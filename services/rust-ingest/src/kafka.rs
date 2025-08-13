use anyhow::Result;
use rdkafka::producer::{FutureProducer, FutureRecord};
use rdkafka::ClientConfig;
use std::time::Duration;

pub fn create_producer(brokers: &str) -> Result<FutureProducer> {
    let producer: FutureProducer = ClientConfig::new()
        .set("bootstrap.servers", brokers)
        .set("message.timeout.ms", "5000")
        .create()?;
    Ok(producer)
}

pub async fn send_message(
    producer: &FutureProducer,
    topic: &str,
    key: &str,
    payload: Vec<u8>,
) -> Result<()> {
    producer
        .send(
            FutureRecord::to(topic)
                .key(key)
                .payload(&payload),
            Duration::from_secs(0),
        )
        .await?;
    Ok(())
}
