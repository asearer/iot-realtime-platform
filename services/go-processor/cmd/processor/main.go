package main

import (
	"log"
	"os"
	"os/signal"
	"syscall"

	"go-processor/internal/config"
	"go-processor/internal/database"
	"go-processor/internal/kafka"
	"go-processor/internal/metrics"
	"go-processor/internal/processors"
	"go-processor/internal/websocket"
)

func main() {
	log.Println("Starting Go Processor Service...")

	// Load configuration
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("failed to load config: %v", err)
	}

	log.Printf("Configuration loaded: Kafka=%s, Database=%s", cfg.KafkaBrokers, cfg.DatabaseURL)

	// Initialize database connection
	db, err := database.NewTimescaleDB(cfg.DatabaseURL)
	if err != nil {
		log.Fatalf("failed to connect to database: %v", err)
	}
	defer db.Close()

	// Test database connection
	if err := db.HealthCheck(); err != nil {
		log.Fatalf("database health check failed: %v", err)
	}

	log.Println("Database connection established")

	// Initialize WebSocket server
	wsServer := websocket.NewServer(cfg.WebSocketPort)
	go wsServer.Run()

	log.Printf("WebSocket server started on %s", cfg.WebSocketPort)

	// Start Prometheus metrics server
	go metrics.Serve(cfg.MetricsPort)

	log.Printf("Metrics server started on %s", cfg.MetricsPort)

	// Create Kafka consumer for raw events
	consumer, err := kafka.NewConsumer(cfg)
	if err != nil {
		log.Fatalf("failed to create Kafka consumer: %v", err)
	}
	defer consumer.Close()

	log.Println("Kafka consumer created")

	// Start processing loops
	aggregatorDone := make(chan bool)
	anomalyDone := make(chan bool)

	// Start aggregation processor
	go func() {
		defer func() { aggregatorDone <- true }()
		log.Println("Starting aggregation processor...")

		aggregator, err := processors.NewAggregator(cfg, db)
		if err != nil {
			log.Printf("Failed to create aggregator: %v", err)
			return
		}
		defer aggregator.Stop()

		processors.StartAggregationLoop(consumer, cfg, aggregator, wsServer)
	}()

	// Start anomaly detection processor
	go func() {
		defer func() { anomalyDone <- true }()
		log.Println("Starting anomaly detection processor...")

		detector, err := processors.NewAnomalyDetector(cfg, db)
		if err != nil {
			log.Printf("Failed to create anomaly detector: %v", err)
			return
		}
		defer detector.Stop()

		processors.StartAnomalyDetectionLoop(consumer, cfg, detector, wsServer)
	}()

	log.Println("All processors started successfully")

	// Graceful shutdown handling
	sigs := make(chan os.Signal, 1)
	signal.Notify(sigs, syscall.SIGINT, syscall.SIGTERM)

	// Wait for shutdown signal
	sig := <-sigs
	log.Printf("Received signal %s, initiating graceful shutdown...", sig)

	// Stop WebSocket server
	wsServer.Stop()

	// Close database connection
	db.Close()

	// Wait for processors to finish
	log.Println("Waiting for processors to finish...")
	<-aggregatorDone
	<-anomalyDone

	log.Println("Go Processor Service stopped gracefully")
}
