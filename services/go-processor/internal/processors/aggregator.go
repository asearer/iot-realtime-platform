package processors

import (
	"context"
	"encoding/json"
	"log"
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

type AggregateData struct {
	DeviceID    string             `json:"device_id"`
	Timestamp   int64              `json:"timestamp"`
	WindowStart int64              `json:"window_start"`
	WindowEnd   int64              `json:"window_end"`
	Metrics     map[string]float64 `json:"metrics"`
	Count       int                `json:"count"`
}

type Aggregator struct {
	producer    *kafka.Producer
	db          *database.TimescaleDB
	data        map[string]map[string]*AggregateData
	mutex       sync.RWMutex
	windowSize  time.Duration
	ticker      *time.Ticker
	stopChannel chan bool
}

func NewAggregator(cfg *config.Config, db *database.TimescaleDB) (*Aggregator, error) {
	producer := kafka.NewProducer([]string{cfg.KafkaBrokers}, cfg.AggregatesTopic)

	aggregator := &Aggregator{
		producer:    producer,
		db:          db,
		data:        make(map[string]map[string]*AggregateData),
		windowSize:  time.Minute,
		ticker:      time.NewTicker(time.Minute),
		stopChannel: make(chan bool),
	}

	// Start background aggregation flush
	go aggregator.flushLoop()

	return aggregator, nil
}

func (a *Aggregator) flushLoop() {
	for {
		select {
		case <-a.ticker.C:
			a.flushAggregates()
		case <-a.stopChannel:
			return
		}
	}
}

func (a *Aggregator) ProcessTelemetry(data []byte) error {
	var telemetry pb.Telemetry
	if err := proto.Unmarshal(data, &telemetry); err != nil {
		log.Printf("Failed to unmarshal telemetry: %v", err)
		return err
	}

	metrics.MessagesProcessed.Inc()

	// Calculate window boundaries
	windowStart := (telemetry.Ts / 60000) * 60000 // Round down to minute
	windowEnd := windowStart + 60000

	windowKey := generateWindowKey(windowStart, windowEnd)
	deviceID := telemetry.DeviceId

	a.mutex.Lock()
	defer a.mutex.Unlock()

	// Initialize device aggregates if not exists
	if a.data[deviceID] == nil {
		a.data[deviceID] = make(map[string]*AggregateData)
	}

	// Get or create aggregate for this window
	aggregate, exists := a.data[deviceID][windowKey]
	if !exists {
		aggregate = &AggregateData{
			DeviceID:    deviceID,
			Timestamp:   time.Now().UnixMilli(),
			WindowStart: windowStart,
			WindowEnd:   windowEnd,
			Metrics:     make(map[string]float64),
			Count:       0,
		}
		a.data[deviceID][windowKey] = aggregate
	}

	// Aggregate metrics (simple average for now)
	aggregate.Count++
	for metricName, metricValue := range telemetry.Metrics {
		if existing, exists := aggregate.Metrics[metricName]; exists {
			// Running average
			aggregate.Metrics[metricName] = (existing*float64(aggregate.Count-1) + metricValue) / float64(aggregate.Count)
		} else {
			aggregate.Metrics[metricName] = metricValue
		}
	}

	log.Printf("Aggregated telemetry for device %s, window %s, count %d",
		deviceID, windowKey, aggregate.Count)

	return nil
}

func (a *Aggregator) flushAggregates() {
	a.mutex.Lock()
	defer a.mutex.Unlock()

	currentTime := time.Now().UnixMilli()
	cutoffTime := currentTime - 120000 // 2 minutes ago

	for deviceID, windows := range a.data {
		for windowKey, aggregate := range windows {
			// Flush windows that are at least 2 minutes old
			if aggregate.WindowEnd < cutoffTime {
				// Send to Kafka
				if err := a.sendAggregate(aggregate); err != nil {
					log.Printf("Failed to send aggregate to Kafka: %v", err)
				}

				// Save to database
				if err := a.saveAggregateToDatabase(aggregate); err != nil {
					log.Printf("Failed to save aggregate to database: %v", err)
				} else {
					log.Printf("Flushed aggregate for device %s, window %s", deviceID, windowKey)
				}

				delete(windows, windowKey)
			}
		}

		// Clean up empty device maps
		if len(windows) == 0 {
			delete(a.data, deviceID)
		}
	}
}

func (a *Aggregator) sendAggregate(aggregate *AggregateData) error {
	jsonData, err := json.Marshal(aggregate)
	if err != nil {
		return err
	}

	return a.producer.SendMessage([]byte(aggregate.DeviceID), jsonData)
}

func (a *Aggregator) saveAggregateToDatabase(aggregate *AggregateData) error {
	// Convert to database records - one record per metric
	var dbRecords []database.AggregateRecord

	for metricName, metricValue := range aggregate.Metrics {
		record := database.AggregateRecord{
			DeviceID:    aggregate.DeviceID,
			Timestamp:   time.UnixMilli(aggregate.Timestamp),
			WindowStart: time.UnixMilli(aggregate.WindowStart),
			WindowEnd:   time.UnixMilli(aggregate.WindowEnd),
			MetricName:  metricName,
			MetricValue: metricValue,
			SampleCount: aggregate.Count,
		}
		dbRecords = append(dbRecords, record)
	}

	return a.db.InsertAggregates(dbRecords)
}

func (a *Aggregator) Stop() {
	a.stopChannel <- true
	a.ticker.Stop()
	a.producer.Close()
}

func generateWindowKey(start, end int64) string {
	return time.UnixMilli(start).Format("2006-01-02T15:04:05Z")
}

func StartAggregationLoop(reader *kafkago.Reader, cfg *config.Config, aggregator *Aggregator, wsServer *websocket.Server) {
	log.Println("Starting aggregation loop...")

	for {
		msg, err := reader.ReadMessage(context.Background())
		if err != nil {
			log.Printf("Error reading message: %v", err)
			continue
		}

		if err := aggregator.ProcessTelemetry(msg.Value); err != nil {
			log.Printf("Error processing telemetry: %v", err)
		}

		// Update device last seen in database
		var telemetry pb.Telemetry
		if err := proto.Unmarshal(msg.Value, &telemetry); err == nil {
			if err := aggregator.db.UpdateDeviceLastSeen(telemetry.DeviceId); err != nil {
				log.Printf("Failed to update device last seen: %v", err)
			}
		}

		log.Printf("Processed aggregation message from partition %d @ offset %d", msg.Partition, msg.Offset)
	}
}
