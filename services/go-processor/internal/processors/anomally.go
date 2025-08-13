package processors

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"math"
	"sync"
	"time"

	"go-processor/internal/config"
	"go-processor/internal/database"
	"go-processor/internal/kafka"
	"go-processor/internal/metrics"
	pb "go-processor/internal/proto"
	"go-processor/internal/websocket"

	kafkago "github.com/segmentio/kafka-go"
	"google.golang.org/protobuf/proto"
)

type DeviceStats struct {
	DeviceID    string            `json:"device_id"`
	MetricStats map[string]*Stats `json:"metric_stats"`
	LastUpdated int64             `json:"last_updated"`
	SampleCount int               `json:"sample_count"`
	mutex       sync.RWMutex
}

type Stats struct {
	Mean   float64 `json:"mean"`
	StdDev float64 `json:"std_dev"`
	Min    float64 `json:"min"`
	Max    float64 `json:"max"`
	Count  int     `json:"count"`
	Sum    float64 `json:"sum"`
	SumSq  float64 `json:"sum_sq"`
}

type Anomaly struct {
	DeviceID      string     `json:"device_id"`
	Timestamp     int64      `json:"timestamp"`
	MetricName    string     `json:"metric_name"`
	Value         float64    `json:"value"`
	ExpectedRange [2]float64 `json:"expected_range"` // [min, max]
	Severity      string     `json:"severity"`       // "low", "medium", "high"
	ZScore        float64    `json:"z_score"`
}

type AnomalyDetector struct {
	producer       *kafka.Producer
	db             *database.TimescaleDB
	deviceStats    map[string]*DeviceStats
	mutex          sync.RWMutex
	alertThreshold float64 // Z-score threshold for anomalies
	cleanupTicker  *time.Ticker
	stopChannel    chan bool
}

func NewAnomalyDetector(cfg *config.Config, db *database.TimescaleDB) (*AnomalyDetector, error) {
	producer := kafka.NewProducer([]string{cfg.KafkaBrokers}, cfg.AlertsTopic)

	detector := &AnomalyDetector{
		producer:       producer,
		db:             db,
		deviceStats:    make(map[string]*DeviceStats),
		alertThreshold: 3.0, // 3 standard deviations
		cleanupTicker:  time.NewTicker(10 * time.Minute),
		stopChannel:    make(chan bool),
	}

	// Start cleanup routine for stale device stats
	go detector.cleanupLoop()

	return detector, nil
}

func (ad *AnomalyDetector) cleanupLoop() {
	for {
		select {
		case <-ad.cleanupTicker.C:
			ad.cleanupStaleStats()
		case <-ad.stopChannel:
			return
		}
	}
}

func (ad *AnomalyDetector) cleanupStaleStats() {
	ad.mutex.Lock()
	defer ad.mutex.Unlock()

	cutoffTime := time.Now().UnixMilli() - (24 * 60 * 60 * 1000) // 24 hours ago

	for deviceID, stats := range ad.deviceStats {
		if stats.LastUpdated < cutoffTime {
			log.Printf("Cleaning up stale stats for device %s", deviceID)
			delete(ad.deviceStats, deviceID)
		}
	}
}

func (ad *AnomalyDetector) ProcessTelemetry(data []byte) error {
	var telemetry pb.Telemetry
	if err := proto.Unmarshal(data, &telemetry); err != nil {
		log.Printf("Failed to unmarshal telemetry: %v", err)
		return err
	}

	metrics.MessagesProcessed.Inc()

	deviceID := telemetry.DeviceId
	timestamp := telemetry.Ts

	ad.mutex.Lock()
	deviceStats, exists := ad.deviceStats[deviceID]
	if !exists {
		deviceStats = &DeviceStats{
			DeviceID:    deviceID,
			MetricStats: make(map[string]*Stats),
			LastUpdated: timestamp,
			SampleCount: 0,
		}
		ad.deviceStats[deviceID] = deviceStats
	}
	ad.mutex.Unlock()

	deviceStats.mutex.Lock()
	defer deviceStats.mutex.Unlock()

	// Process each metric
	for metricName, value := range telemetry.Metrics {
		stats, exists := deviceStats.MetricStats[metricName]
		if !exists {
			stats = &Stats{
				Mean:  value,
				Min:   value,
				Max:   value,
				Count: 1,
				Sum:   value,
				SumSq: value * value,
			}
			deviceStats.MetricStats[metricName] = stats
		} else {
			// Check for anomaly before updating stats
			if stats.Count >= 10 { // Need at least 10 samples for reliable detection
				zScore := ad.calculateZScore(value, stats)
				if math.Abs(zScore) > ad.alertThreshold {
					anomaly := &Anomaly{
						DeviceID:   deviceID,
						Timestamp:  timestamp,
						MetricName: metricName,
						Value:      value,
						ExpectedRange: [2]float64{
							stats.Mean - ad.alertThreshold*stats.StdDev,
							stats.Mean + ad.alertThreshold*stats.StdDev,
						},
						Severity: ad.calculateSeverity(math.Abs(zScore)),
						ZScore:   zScore,
					}

					if err := ad.sendAnomaly(anomaly); err != nil {
						log.Printf("Failed to send anomaly alert: %v", err)
					}

					if err := ad.saveAnomalyToDatabase(anomaly); err != nil {
						log.Printf("Failed to save anomaly to database: %v", err)
					} else {
						log.Printf("ANOMALY DETECTED: Device %s, Metric %s, Value %.2f, Z-Score %.2f",
							deviceID, metricName, value, zScore)
					}
				}
			}

			// Update statistics
			ad.updateStats(stats, value)
		}
	}

	deviceStats.LastUpdated = timestamp
	deviceStats.SampleCount++

	return nil
}

func (ad *AnomalyDetector) calculateZScore(value float64, stats *Stats) float64 {
	if stats.StdDev == 0 {
		return 0
	}
	return (value - stats.Mean) / stats.StdDev
}

func (ad *AnomalyDetector) calculateSeverity(absZScore float64) string {
	if absZScore >= 5.0 {
		return "high"
	} else if absZScore >= 4.0 {
		return "medium"
	}
	return "low"
}

func (ad *AnomalyDetector) updateStats(stats *Stats, newValue float64) {
	stats.Count++
	stats.Sum += newValue
	stats.SumSq += newValue * newValue

	// Update min/max
	if newValue < stats.Min {
		stats.Min = newValue
	}
	if newValue > stats.Max {
		stats.Max = newValue
	}

	// Update running mean and standard deviation
	stats.Mean = stats.Sum / float64(stats.Count)

	// Calculate standard deviation using the formula: sqrt((sum_sq - n*mean^2) / (n-1))
	if stats.Count > 1 {
		variance := (stats.SumSq - float64(stats.Count)*stats.Mean*stats.Mean) / float64(stats.Count-1)
		if variance > 0 {
			stats.StdDev = math.Sqrt(variance)
		} else {
			stats.StdDev = 0
		}
	} else {
		stats.StdDev = 0
	}
}

func (ad *AnomalyDetector) sendAnomaly(anomaly *Anomaly) error {
	jsonData, err := json.Marshal(anomaly)
	if err != nil {
		return err
	}

	return ad.producer.SendMessage([]byte(anomaly.DeviceID), jsonData)
}

func (ad *AnomalyDetector) saveAnomalyToDatabase(anomaly *Anomaly) error {
	dbAlert := database.AlertRecord{
		DeviceID:    anomaly.DeviceID,
		Timestamp:   time.UnixMilli(anomaly.Timestamp),
		MetricName:  anomaly.MetricName,
		MetricValue: anomaly.Value,
		AlertType:   "anomaly",
		Severity:    anomaly.Severity,
		ZScore:      anomaly.ZScore,
		Threshold:   ad.alertThreshold,
		Status:      "open",
		Message:     fmt.Sprintf("Anomalous %s value detected: %.2f (Z-score: %.2f)", anomaly.MetricName, anomaly.Value, anomaly.ZScore),
	}

	return ad.db.InsertAlert(dbAlert)
}

func (ad *AnomalyDetector) Stop() {
	ad.stopChannel <- true
	ad.cleanupTicker.Stop()
	ad.producer.Close()
}

func StartAnomalyDetectionLoop(reader *kafkago.Reader, cfg *config.Config, detector *AnomalyDetector, wsServer *websocket.Server) {
	log.Println("Starting anomaly detection loop...")

	for {
		msg, err := reader.ReadMessage(context.Background())
		if err != nil {
			log.Printf("Error reading message: %v", err)
			continue
		}

		if err := detector.ProcessTelemetry(msg.Value); err != nil {
			log.Printf("Error processing telemetry for anomaly detection: %v", err)
		}

		// Broadcast anomaly alerts to WebSocket clients if any were detected
		var telemetry pb.Telemetry
		if err := proto.Unmarshal(msg.Value, &telemetry); err == nil {
			// Check if this processing resulted in any new alerts
			alerts, err := detector.db.GetActiveAlerts(telemetry.DeviceId, 1)
			if err == nil && len(alerts) > 0 {
				// Broadcast the most recent alert
				wsServer.BroadcastAlert(alerts[0])
			}
		}

		log.Printf("Processed anomaly detection for offset %d", msg.Offset)
	}
}
