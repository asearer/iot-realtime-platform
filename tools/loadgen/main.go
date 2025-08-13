package main

import (
	"bytes"
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	"golang.org/x/time/rate"
)

type Config struct {
	TargetURL    string
	Rate         int
	Duration     time.Duration
	DeviceCount  int
	MetricTypes  []string
	OutputFormat string
	Verbose      bool
	HTTPTimeout  time.Duration
	BatchSize    int
}

type TelemetryData struct {
	DeviceID  string             `json:"device_id"`
	Timestamp int64              `json:"ts"`
	Metrics   map[string]float64 `json:"metrics"`
	Raw       []byte             `json:"raw,omitempty"`
}

type Statistics struct {
	TotalRequests   int64
	SuccessRequests int64
	FailedRequests  int64
	TotalLatency    time.Duration
	MinLatency      time.Duration
	MaxLatency      time.Duration
	StartTime       time.Time
	EndTime         time.Time
	BytesSent       int64
	RequestsPerSec  float64
	AvgLatency      time.Duration
	mutex           sync.RWMutex
}

func (s *Statistics) RecordRequest(latency time.Duration, success bool, bytes int64) {
	s.mutex.Lock()
	defer s.mutex.Unlock()

	atomic.AddInt64(&s.TotalRequests, 1)
	atomic.AddInt64(&s.BytesSent, bytes)

	if success {
		atomic.AddInt64(&s.SuccessRequests, 1)
	} else {
		atomic.AddInt64(&s.FailedRequests, 1)
	}

	s.TotalLatency += latency

	if s.MinLatency == 0 || latency < s.MinLatency {
		s.MinLatency = latency
	}

	if latency > s.MaxLatency {
		s.MaxLatency = latency
	}
}

func (s *Statistics) GetStats() Statistics {
	s.mutex.RLock()
	defer s.mutex.RUnlock()

	// Create a new stats struct without copying the mutex
	stats := Statistics{
		TotalRequests:   atomic.LoadInt64(&s.TotalRequests),
		SuccessRequests: atomic.LoadInt64(&s.SuccessRequests),
		FailedRequests:  atomic.LoadInt64(&s.FailedRequests),
		TotalLatency:    s.TotalLatency,
		MinLatency:      s.MinLatency,
		MaxLatency:      s.MaxLatency,
		StartTime:       s.StartTime,
		EndTime:         s.EndTime,
		BytesSent:       atomic.LoadInt64(&s.BytesSent),
	}

	if stats.TotalRequests > 0 {
		stats.AvgLatency = s.TotalLatency / time.Duration(stats.TotalRequests)
	}

	if !stats.EndTime.IsZero() && !stats.StartTime.IsZero() {
		duration := stats.EndTime.Sub(stats.StartTime).Seconds()
		if duration > 0 {
			stats.RequestsPerSec = float64(stats.TotalRequests) / duration
		}
	}

	return stats
}

type LoadGenerator struct {
	config     Config
	httpClient *http.Client
	stats      *Statistics
	limiter    *rate.Limiter
	ctx        context.Context
	cancel     context.CancelFunc
}

func NewLoadGenerator(config Config) *LoadGenerator {
	ctx, cancel := context.WithCancel(context.Background())

	return &LoadGenerator{
		config: config,
		httpClient: &http.Client{
			Timeout: config.HTTPTimeout,
		},
		stats:   &Statistics{StartTime: time.Now()},
		limiter: rate.NewLimiter(rate.Limit(config.Rate), config.BatchSize),
		ctx:     ctx,
		cancel:  cancel,
	}
}

func (lg *LoadGenerator) generateTelemetry(deviceID string) TelemetryData {
	generator := NewTelemetryGenerator(deviceID, lg.config.MetricTypes)
	return generator.GenerateRealisticTelemetry()
}

func (lg *LoadGenerator) sendRequest(telemetry TelemetryData) error {
	jsonData, err := json.Marshal(telemetry)
	if err != nil {
		return fmt.Errorf("failed to marshal telemetry: %w", err)
	}

	req, err := http.NewRequestWithContext(lg.ctx, "POST", lg.config.TargetURL+"/telemetry", bytes.NewBuffer(jsonData))
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("User-Agent", "IoT-LoadGen/1.0")

	start := time.Now()
	resp, err := lg.httpClient.Do(req)
	latency := time.Since(start)

	success := err == nil && resp != nil && resp.StatusCode < 400

	if resp != nil {
		resp.Body.Close()
	}

	lg.stats.RecordRequest(latency, success, int64(len(jsonData)))

	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}

	if resp.StatusCode >= 400 {
		return fmt.Errorf("received error status: %d", resp.StatusCode)
	}

	return nil
}

func (lg *LoadGenerator) worker(deviceID string, wg *sync.WaitGroup) {
	defer wg.Done()

	for {
		select {
		case <-lg.ctx.Done():
			return
		default:
			// Wait for rate limiter
			if err := lg.limiter.Wait(lg.ctx); err != nil {
				if err == context.Canceled {
					return
				}
				log.Printf("Rate limiter error: %v", err)
				continue
			}

			telemetry := lg.generateTelemetry(deviceID)

			if err := lg.sendRequest(telemetry); err != nil {
				if lg.config.Verbose {
					log.Printf("Request failed for device %s: %v", deviceID, err)
				}
			} else if lg.config.Verbose {
				log.Printf("✓ Sent telemetry for device %s", deviceID)
			}
		}
	}
}

func (lg *LoadGenerator) Run() error {
	log.Printf("Starting load generator...")
	log.Printf("Target: %s", lg.config.TargetURL)
	log.Printf("Rate: %d requests/second", lg.config.Rate)
	log.Printf("Duration: %v", lg.config.Duration)
	log.Printf("Devices: %d", lg.config.DeviceCount)
	log.Printf("Metrics: %v", lg.config.MetricTypes)

	var wg sync.WaitGroup

	// Start workers for each device
	for i := 0; i < lg.config.DeviceCount; i++ {
		deviceID := fmt.Sprintf("loadgen-device-%04d", i+1)
		wg.Add(1)
		go lg.worker(deviceID, &wg)
	}

	// Start statistics reporter
	statsTicker := time.NewTicker(5 * time.Second)
	defer statsTicker.Stop()

	go func() {
		for {
			select {
			case <-lg.ctx.Done():
				return
			case <-statsTicker.C:
				lg.printStats()
			}
		}
	}()

	// Wait for duration or cancellation
	if lg.config.Duration > 0 {
		timer := time.NewTimer(lg.config.Duration)
		defer timer.Stop()

		select {
		case <-timer.C:
			log.Printf("Duration reached, stopping...")
		case <-lg.ctx.Done():
			log.Printf("Received cancellation signal, stopping...")
		}
	} else {
		<-lg.ctx.Done()
		log.Printf("Received cancellation signal, stopping...")
	}

	// Cancel all workers
	lg.cancel()

	// Wait for all workers to finish
	done := make(chan struct{})
	go func() {
		wg.Wait()
		close(done)
	}()

	select {
	case <-done:
		log.Printf("All workers stopped")
	case <-time.After(5 * time.Second):
		log.Printf("Timeout waiting for workers to stop")
	}

	lg.stats.EndTime = time.Now()
	lg.printFinalStats()

	return nil
}

func (lg *LoadGenerator) printStats() {
	stats := lg.stats.GetStats()

	log.Printf("Stats: Total=%d, Success=%d, Failed=%d, Rate=%.2f req/s, Avg Latency=%v",
		stats.TotalRequests,
		stats.SuccessRequests,
		stats.FailedRequests,
		stats.RequestsPerSec,
		stats.AvgLatency,
	)
}

func (lg *LoadGenerator) printFinalStats() {
	stats := lg.stats.GetStats()

	fmt.Printf("\n" + strings.Repeat("=", 60) + "\n")
	fmt.Printf("FINAL LOAD TEST RESULTS\n")
	fmt.Printf(strings.Repeat("=", 60) + "\n")
	fmt.Printf("Duration:              %v\n", stats.EndTime.Sub(stats.StartTime))
	fmt.Printf("Total Requests:        %d\n", stats.TotalRequests)
	fmt.Printf("Successful Requests:   %d\n", stats.SuccessRequests)
	fmt.Printf("Failed Requests:       %d\n", stats.FailedRequests)
	fmt.Printf("Success Rate:          %.2f%%\n", float64(stats.SuccessRequests)/float64(stats.TotalRequests)*100)
	fmt.Printf("Requests per Second:   %.2f\n", stats.RequestsPerSec)
	fmt.Printf("Average Latency:       %v\n", stats.AvgLatency)
	fmt.Printf("Min Latency:           %v\n", stats.MinLatency)
	fmt.Printf("Max Latency:           %v\n", stats.MaxLatency)
	fmt.Printf("Total Bytes Sent:      %d (%.2f MB)\n", stats.BytesSent, float64(stats.BytesSent)/(1024*1024))
	fmt.Printf(strings.Repeat("=", 60) + "\n")

	// Output in JSON format if requested
	if lg.config.OutputFormat == "json" {
		jsonStats := map[string]interface{}{
			"duration_seconds":     stats.EndTime.Sub(stats.StartTime).Seconds(),
			"total_requests":       stats.TotalRequests,
			"successful_requests":  stats.SuccessRequests,
			"failed_requests":      stats.FailedRequests,
			"success_rate_percent": float64(stats.SuccessRequests) / float64(stats.TotalRequests) * 100,
			"requests_per_second":  stats.RequestsPerSec,
			"average_latency_ms":   float64(stats.AvgLatency.Nanoseconds()) / 1e6,
			"min_latency_ms":       float64(stats.MinLatency.Nanoseconds()) / 1e6,
			"max_latency_ms":       float64(stats.MaxLatency.Nanoseconds()) / 1e6,
			"total_bytes_sent":     stats.BytesSent,
		}

		if jsonData, err := json.MarshalIndent(jsonStats, "", "  "); err == nil {
			fmt.Printf("\nJSON Output:\n%s\n", string(jsonData))
		}
	}
}

func parseEnvConfig() Config {
	config := Config{
		TargetURL:    getEnv("TARGET_URL", "http://localhost:8090"),
		Rate:         getEnvInt("RATE", 100),
		DeviceCount:  getEnvInt("DEVICE_COUNT", 10),
		MetricTypes:  []string{"temperature", "humidity", "pressure"},
		OutputFormat: getEnv("OUTPUT_FORMAT", "text"),
		Verbose:      getEnvBool("VERBOSE", false),
		HTTPTimeout:  time.Duration(getEnvInt("HTTP_TIMEOUT", 30)) * time.Second,
		BatchSize:    getEnvInt("BATCH_SIZE", 10),
	}

	if durationStr := getEnv("DURATION", "60s"); durationStr != "" {
		if duration, err := time.ParseDuration(durationStr); err == nil {
			config.Duration = duration
		}
	}

	if metricsEnv := getEnv("METRICS", ""); metricsEnv != "" {
		config.MetricTypes = []string{}
		for _, metric := range []string{"temperature", "humidity", "pressure", "cpu_usage", "memory_usage", "battery_level", "signal_strength", "vibration", "light_level", "noise_level"} {
			config.MetricTypes = append(config.MetricTypes, metric)
		}
	}

	return config
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

func getEnvInt(key string, defaultValue int) int {
	if value := os.Getenv(key); value != "" {
		if intValue, err := strconv.Atoi(value); err == nil {
			return intValue
		}
	}
	return defaultValue
}

func getEnvBool(key string, defaultValue bool) bool {
	if value := os.Getenv(key); value != "" {
		if boolValue, err := strconv.ParseBool(value); err == nil {
			return boolValue
		}
	}
	return defaultValue
}

func main() {
	// Start with environment configuration
	config := parseEnvConfig()

	// Command line flags override environment variables
	flag.StringVar(&config.TargetURL, "url", config.TargetURL, "Target URL for load testing")
	flag.IntVar(&config.Rate, "rate", config.Rate, "Requests per second")
	flag.DurationVar(&config.Duration, "duration", config.Duration, "Test duration (0 for infinite)")
	flag.IntVar(&config.DeviceCount, "devices", config.DeviceCount, "Number of devices to simulate")
	flag.StringVar(&config.OutputFormat, "output", config.OutputFormat, "Output format (text|json)")
	flag.BoolVar(&config.Verbose, "verbose", config.Verbose, "Verbose logging")
	flag.DurationVar(&config.HTTPTimeout, "timeout", config.HTTPTimeout, "HTTP request timeout")
	flag.IntVar(&config.BatchSize, "batch", config.BatchSize, "Batch size for rate limiting")

	var metricsFlag string
	flag.StringVar(&metricsFlag, "metrics", "temperature,humidity,pressure", "Comma-separated list of metrics to generate")

	flag.Parse()

	// Parse metrics
	if metricsFlag != "" {
		config.MetricTypes = []string{}
		for _, metric := range []string{"temperature", "humidity", "pressure", "cpu_usage", "memory_usage", "battery_level", "signal_strength", "vibration", "light_level", "noise_level"} {
			config.MetricTypes = append(config.MetricTypes, metric)
		}
	}

	// Validate configuration
	if config.Rate <= 0 {
		log.Fatal("Rate must be positive")
	}
	if config.DeviceCount <= 0 {
		log.Fatal("Device count must be positive")
	}
	if config.TargetURL == "" {
		log.Fatal("Target URL must be specified")
	}

	// Create load generator
	loadGen := NewLoadGenerator(config)

	// Handle graceful shutdown
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		<-sigChan
		log.Printf("Received shutdown signal...")
		loadGen.cancel()
	}()

	// Run load test
	if err := loadGen.Run(); err != nil {
		log.Fatalf("Load test failed: %v", err)
	}
}
