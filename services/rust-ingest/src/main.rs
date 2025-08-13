mod config;
mod kafka;
mod server;
mod telemetry_handler;
mod proto;

use anyhow::Result;

#[tokio::main]
async fn main() -> Result<()> {
    let cfg = config::load_config()?;
    let producer = kafka::create_producer(&cfg.kafka_brokers)?;

    println!("Starting Rust ingestion server on {}", cfg.listen_addr);
    server::run_server(cfg, producer).await?;
    Ok(())
}
