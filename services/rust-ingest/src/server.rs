use crate::{telemetry_handler::handle_telemetry, Config};
use anyhow::Result;
use axum::{
    extract::State,
    http::StatusCode,
    response::Json,
    routing::{get, post},
    Router,
};
use rdkafka::producer::FutureProducer;
use serde::{Deserialize, Serialize};
use std::{collections::HashMap, sync::Arc};
use tokio::net::TcpListener;
use tower::ServiceBuilder;
use tower_http::{cors::CorsLayer, trace::TraceLayer};
use tracing::{info, warn};

#[derive(Debug, Deserialize)]
pub struct TelemetryRequest {
    pub device_id: String,
    pub ts: Option<i64>,
    pub metrics: HashMap<String, f64>,
    pub raw: Option<Vec<u8>>,
}

#[derive(Debug, Serialize)]
pub struct HealthResponse {
    status: String,
    timestamp: i64,
    version: String,
}

#[derive(Debug, Serialize)]
pub struct TelemetryResponse {
    success: bool,
    message: String,
    device_id: String,
}

#[derive(Debug, Serialize)]
pub struct ErrorResponse {
    error: String,
    details: Option<String>,
}

#[derive(Clone)]
pub struct AppState {
    producer: FutureProducer,
    topic: String,
}

pub async fn run_server(cfg: Config, producer: FutureProducer) -> Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(tracing_subscriber::EnvFilter::from_default_env())
        .init();

    let state = AppState {
        producer,
        topic: cfg.kafka_topic,
    };

    let app = Router::new()
        .route("/health", get(health_check))
        .route("/telemetry", post(ingest_telemetry))
        .route("/metrics", get(metrics_handler))
        .layer(
            ServiceBuilder::new()
                .layer(TraceLayer::new_for_http())
                .layer(CorsLayer::permissive()),
        )
        .with_state(Arc::new(state));

    let listener = TcpListener::bind(&cfg.listen_addr).await?;
    info!("Rust ingestion server listening on {}", cfg.listen_addr);

    axum::serve(listener, app).await?;
    Ok(())
}

async fn health_check() -> Json<HealthResponse> {
    Json(HealthResponse {
        status: "healthy".to_string(),
        timestamp: chrono::Utc::now().timestamp(),
        version: env!("CARGO_PKG_VERSION").to_string(),
    })
}

async fn ingest_telemetry(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<TelemetryRequest>,
) -> Result<Json<TelemetryResponse>, (StatusCode, Json<ErrorResponse>)> {
    if payload.device_id.is_empty() {
        return Err((
            StatusCode::BAD_REQUEST,
            Json(ErrorResponse {
                error: "device_id is required".to_string(),
                details: None,
            }),
        ));
    }

    if payload.metrics.is_empty() {
        return Err((
            StatusCode::BAD_REQUEST,
            Json(ErrorResponse {
                error: "metrics cannot be empty".to_string(),
                details: None,
            }),
        ));
    }

    // Convert HTTP request to telemetry and process
    let telemetry_data = crate::proto::telemetry::Telemetry {
        device_id: payload.device_id.clone(),
        ts: payload
            .ts
            .unwrap_or_else(|| chrono::Utc::now().timestamp_millis()),
        metrics: payload.metrics,
        raw: payload.raw.unwrap_or_default(),
    };

    match handle_telemetry(telemetry_data, &state.producer, &state.topic).await {
        Ok(_) => Ok(Json(TelemetryResponse {
            success: true,
            message: "Telemetry received successfully".to_string(),
            device_id: payload.device_id,
        })),
        Err(e) => {
            warn!("Failed to process telemetry: {:?}", e);
            Err((
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(ErrorResponse {
                    error: "Failed to process telemetry".to_string(),
                    details: Some(e.to_string()),
                }),
            ))
        }
    }
}

async fn metrics_handler() -> &'static str {
    // Basic prometheus metrics endpoint
    // In a real implementation, you'd use the prometheus crate properly
    "# HELP rust_ingest_requests_total Total number of telemetry requests\n# TYPE rust_ingest_requests_total counter\nrust_ingest_requests_total 0\n"
}
