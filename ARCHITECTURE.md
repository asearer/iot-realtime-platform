# IoT Real-Time Analytics Platform — Architecture

## Overview

This platform ingests, processes, and serves analytics from millions of IoT devices in near real-time.
It uses a polyglot approach to leverage the strengths of three languages:

* **Rust**: Low-latency, memory-safe ingestion of high-throughput IoT telemetry.
* **Go**: Distributed, concurrent stream processing and aggregation.
* **Java (Spring Boot)**: Secure, enterprise-grade API and dashboard integration.

## High-Level Data Flow

```
[IoT Devices] 
   ↓ (MQTT/CoAP/WebSocket)
[Rust Ingestion Service]
   ↓ (Kafka/NATS/Redis Streams)
[Go Processing Cluster]
   ↓ (Aggregated Metrics)
[Java Spring Boot API]
   ↔ [TimescaleDB/PostgreSQL]
   ↔ [Auth Server / Keycloak]
```

## Component Responsibilities

### 1. Rust — Ingestion Layer

* Handles millions of messages per second.
* Uses `tokio` for async IO, `rumqttc` for MQTT, `serde` for serialization, and `rdkafka` for Kafka publishing.
* Validates and batches data, compresses payloads, and enforces device rate limits.
* Outputs to Kafka topic `raw.events`.

### 2. Go — Processing & Aggregation

* Kafka consumers perform rolling window aggregations, anomaly detection, and data enrichment.
* Uses goroutines and channels for high concurrency.
* Writes results to TimescaleDB and sends real-time updates via WebSockets.
* Exposes internal metrics to Prometheus.

### 3. Java — API & Enterprise Integration

* Spring Boot REST API with role-based access via Keycloak.
* Endpoints for querying time-series data and triggering reprocessing.
* Uses Spring Data JPA for TimescaleDB integration.
* Provides Swagger/OpenAPI documentation.

## Inter-Service Communication

* **Rust → Go**: Kafka or NATS message queues.
* **Go → Java**: Writes aggregated data to TimescaleDB.
* **Java → Clients**: HTTPS REST APIs + WebSocket streams.

## Message Formats

### Device to Rust

Protobuf schema (`telemetry.proto`):

```proto
syntax = "proto3";
message Telemetry {
  string device_id = 1;
  int64 ts = 2; // epoch ms
  map<string, double> metrics = 3;
  bytes raw = 4;
}
```

### Rust to Kafka

* Topic: `raw.events`
* Headers: `device-id`, `schema-version`, `ingest-node-id`
* Compression: Zstd

### Kafka to Go

* Go consumers parse protobuf messages.
* Aggregations output to topic `aggregates.minute`.

## Storage

* **Hot data**: TimescaleDB hypertables for fast time-series queries.
* **Cold data**: S3-compatible storage for archived raw telemetry.

## Security

* TLS for all connections.
* mTLS between internal services.
* OAuth2/OIDC via Keycloak for API authentication.
* Role-based authorization: Operator, Viewer, Admin.

## Observability

* **Metrics**: Prometheus + Grafana dashboards.
* **Tracing**: Jaeger for distributed tracing.
* **Logging**: Loki or ELK stack.

## Scaling & Resilience

* Ingestion nodes scale horizontally behind a load balancer.
* Go processors use Kafka consumer groups for partitioned processing.
* TimescaleDB multi-node deployment for high ingest rates.
* Kafka replication factor ≥ 3 for durability.

## Deployment

* Dockerized services deployed on Kubernetes.
* Helm charts for service orchestration.
* GitHub Actions for CI/CD with unit, integration, and load testing.


