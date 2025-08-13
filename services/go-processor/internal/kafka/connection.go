package kafka

import (
	"log"

	"github.com/segmentio/kafka-go"
)

func NewReader(brokers []string, groupID, topic string) *kafka.Reader {
	log.Printf("Connecting Kafka reader: brokers=%v topic=%s group=%s", brokers, topic, groupID)
	return kafka.NewReader(kafka.ReaderConfig{
		Brokers:  brokers,
		GroupID:  groupID,
		Topic:    topic,
		MinBytes: 10e3,
		MaxBytes: 10e6,
	})
}

func NewWriter(brokers []string, topic string) *kafka.Writer {
	log.Printf("Connecting Kafka writer: brokers=%v topic=%s", brokers, topic)
	return &kafka.Writer{
		Addr:     kafka.TCP(brokers...),
		Topic:    topic,
		Balancer: &kafka.LeastBytes{},
	}
}
