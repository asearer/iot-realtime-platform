package config

import (
	"github.com/kelseyhightower/envconfig"
)

type Config struct {
	KafkaBrokers string `envconfig:"KAFKA_BROKERS" default:"localhost:9092"`
	KafkaGroupID string `envconfig:"KAFKA_GROUP_ID" default:"go-processor"`
	KafkaTopic   string `envconfig:"KAFKA_TOPIC" default:"raw.events"`

	AggregatesTopic string `envconfig:"AGGREGATES_TOPIC" default:"aggregates.minute"`
	AlertsTopic     string `envconfig:"ALERTS_TOPIC" default:"alerts"`

	DatabaseURL string `envconfig:"DATABASE_URL" required:"true"`

	MetricsPort   string `envconfig:"METRICS_PORT" default:":9090"`
	WebSocketPort string `envconfig:"WEBSOCKET_PORT" default:":8080"`
}

func Load() (*Config, error) {
	var cfg Config
	err := envconfig.Process("", &cfg)
	if err != nil {
		return nil, err
	}
	return &cfg, nil
}
