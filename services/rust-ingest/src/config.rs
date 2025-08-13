use serde::Deserialize;
use anyhow::Result;

#[derive(Debug, Deserialize)]
pub struct Config {
    pub listen_addr: String,
    pub kafka_brokers: String,
    pub kafka_topic: String,
}

pub fn load_config() -> Result<Config> {
    let settings = config::Config::builder()
        .add_source(config::File::with_name("Config").required(false))
        .add_source(config::Environment::default())
        .build()?;
    Ok(settings.try_deserialize::<Config>()?)
}
