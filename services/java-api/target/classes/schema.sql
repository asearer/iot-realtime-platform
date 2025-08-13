CREATE TABLE IF NOT EXISTS device (
    id VARCHAR PRIMARY KEY,
    name VARCHAR(255),
    location VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS metric_aggregate (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR REFERENCES device(id),
    timestamp TIMESTAMP,
    metric_name VARCHAR(255),
    value DOUBLE PRECISION
);
