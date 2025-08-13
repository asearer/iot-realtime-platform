use crate::{kafka::send_message, proto::telemetry::Telemetry};
use anyhow::Result;
use prost::Message;
use rdkafka::producer::FutureProducer;
use std::collections::HashMap;
use tracing::{info, warn};

pub async fn handle_telemetry(
    telemetry: Telemetry,
    producer: &FutureProducer,
    topic: &str,
) -> Result<()> {
    // Log some basic info about the received telemetry
    let metrics_summary: Vec<String> = telemetry
        .metrics
        .iter()
        .map(|(k, v)| format!("{}={:.2}", k, v))
        .collect();

    info!(
        "Processing telemetry: device_id={} ts={} metrics=[{}]",
        telemetry.device_id,
        telemetry.ts,
        metrics_summary.join(", ")
    );

    // Validate telemetry data
    if telemetry.device_id.is_empty() {
        warn!("Received telemetry with empty device_id");
        return Err(anyhow::anyhow!("Device ID cannot be empty"));
    }

    if telemetry.metrics.is_empty() {
        warn!(
            "Received telemetry with no metrics for device {}",
            telemetry.device_id
        );
        return Err(anyhow::anyhow!("Metrics cannot be empty"));
    }

    // Encode telemetry as protobuf and send to Kafka
    let mut buf = Vec::new();
    telemetry.encode(&mut buf)?;

    send_message(producer, topic, &telemetry.device_id, buf).await?;

    info!(
        "Successfully sent telemetry to Kafka for device {}",
        telemetry.device_id
    );

    Ok(())
}

// Helper function to create telemetry from JSON (for testing/debugging)
pub fn create_telemetry_from_json(json_data: &str, device_id: &str) -> Result<Telemetry> {
    let parsed: serde_json::Value = serde_json::from_str(json_data)?;
    let mut metrics = HashMap::new();

    if let Some(obj) = parsed.as_object() {
        for (key, value) in obj {
            if let Some(num) = value.as_f64() {
                metrics.insert(key.clone(), num);
            }
        }
    }

    Ok(Telemetry {
        device_id: device_id.to_string(),
        ts: chrono::Utc::now().timestamp_millis(),
        metrics,
        raw: json_data.as_bytes().to_vec(),
    })
}

// Helper function to validate metric values
pub fn validate_metrics(metrics: &HashMap<String, f64>) -> Result<()> {
    for (key, value) in metrics {
        if key.is_empty() {
            return Err(anyhow::anyhow!("Metric name cannot be empty"));
        }

        if !value.is_finite() {
            return Err(anyhow::anyhow!(
                "Invalid metric value for {}: {}",
                key,
                value
            ));
        }

        // Add any specific validation rules here
        match key.as_str() {
            "temperature" => {
                if !(-100.0..=200.0).contains(value) {
                    warn!("Temperature value {} seems out of normal range", value);
                }
            }
            "humidity" => {
                if !(0.0..=100.0).contains(value) {
                    warn!("Humidity value {}% seems out of normal range", value);
                }
            }
            "battery_level" => {
                if !(0.0..=100.0).contains(value) {
                    return Err(anyhow::anyhow!("Battery level must be between 0-100%"));
                }
            }
            _ => {} // Other metrics don't have specific validation
        }
    }

    Ok(())
}

// Helper function to enrich telemetry with additional metadata
pub fn enrich_telemetry(mut telemetry: Telemetry, node_id: &str) -> Telemetry {
    // Add ingestion metadata
    telemetry.raw = serde_json::to_vec(&serde_json::json!({
        "ingested_at": chrono::Utc::now().to_rfc3339(),
        "ingestion_node": node_id,
        "original_raw": String::from_utf8_lossy(&telemetry.raw).to_string()
    }))
    .unwrap_or_default();

    telemetry
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_create_telemetry_from_json() {
        let json = r#"{"temperature": 23.5, "humidity": 45.2}"#;
        let telemetry = create_telemetry_from_json(json, "test-device").unwrap();

        assert_eq!(telemetry.device_id, "test-device");
        assert_eq!(telemetry.metrics.len(), 2);
        assert_eq!(telemetry.metrics["temperature"], 23.5);
        assert_eq!(telemetry.metrics["humidity"], 45.2);
    }

    #[test]
    fn test_validate_metrics() {
        let mut metrics = HashMap::new();
        metrics.insert("temperature".to_string(), 25.0);
        metrics.insert("humidity".to_string(), 60.0);

        assert!(validate_metrics(&metrics).is_ok());

        // Test invalid battery level
        metrics.insert("battery_level".to_string(), 150.0);
        assert!(validate_metrics(&metrics).is_err());
    }

    #[test]
    fn test_validate_metrics_with_invalid_values() {
        let mut metrics = HashMap::new();
        metrics.insert("temperature".to_string(), f64::NAN);

        assert!(validate_metrics(&metrics).is_err());
    }
}
