package kafka

import (
	"log"

	"go-processor/internal/config"

	"github.com/segmentio/kafka-go"
)

func NewConsumer(cfg *config.Config) (*kafka.Reader, error) {
	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:  []string{cfg.KafkaBrokers},
		GroupID:  cfg.KafkaGroupID,
		Topic:    cfg.KafkaTopic,
		MinBytes: 10e3,
		MaxBytes: 10e6,
	})
	log.Printf("Kafka consumer connected to %s (topic=%s, group=%s)",
		cfg.KafkaBrokers, cfg.KafkaTopic, cfg.KafkaGroupID)
	return reader, nil
}
