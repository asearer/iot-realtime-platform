package kafka

import (
	"context"
	"log"

	"github.com/segmentio/kafka-go"
)

type Producer struct {
	writer *kafka.Writer
}

func NewProducer(brokers []string, topic string) *Producer {
	w := &kafka.Writer{
		Addr:     kafka.TCP(brokers...),
		Topic:    topic,
		Balancer: &kafka.LeastBytes{},
	}
	log.Printf("Kafka producer ready for topic %s", topic)
	return &Producer{writer: w}
}

func (p *Producer) SendMessage(key, value []byte) error {
	msg := kafka.Message{
		Key:   key,
		Value: value,
	}
	return p.writer.WriteMessages(context.Background(), msg)
}

func (p *Producer) Close() error {
	return p.writer.Close()
}
