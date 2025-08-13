-- TimescaleDB Initialization Script for IoT Platform
-- This script sets up the database schema, hypertables, and indexes

\c iot_platform;

-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

-- Create devices table
CREATE TABLE IF NOT EXISTS devices (
    device_id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    device_type VARCHAR(100) NOT NULL,
    location VARCHAR(255),
    manufacturer VARCHAR(255),
    model VARCHAR(255),
    firmware_version VARCHAR(100),
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE', 'DECOMMISSIONED')),
    last_seen TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

-- Create device metadata table
CREATE TABLE IF NOT EXISTS device_metadata (
    device_id VARCHAR(255) REFERENCES devices(device_id) ON DELETE CASCADE,
    metadata_key VARCHAR(255) NOT NULL,
    metadata_value TEXT,
    PRIMARY KEY (device_id, metadata_key)
);

-- Create metric aggregates table (will be converted to hypertable)
CREATE TABLE IF NOT EXISTS metric_aggregates (
    id BIGSERIAL,
    device_id VARCHAR(255) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    metric_name VARCHAR(255) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    min_value DOUBLE PRECISION,
    max_value DOUBLE PRECISION,
    avg_value DOUBLE PRECISION,
    sum_value DOUBLE PRECISION,
    count_value BIGINT,
    std_dev DOUBLE PRECISION,
    aggregation_type VARCHAR(20) DEFAULT 'MINUTE' CHECK (aggregation_type IN ('MINUTE', 'HOUR', 'DAY', 'WEEK', 'MONTH')),
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    PRIMARY KEY (id, timestamp)
);

-- Create metric aggregate tags table
CREATE TABLE IF NOT EXISTS metric_aggregate_tags (
    aggregate_id BIGINT,
    tag_key VARCHAR(255) NOT NULL,
    tag_value VARCHAR(255),
    PRIMARY KEY (aggregate_id, tag_key)
);

-- Create alerts table
CREATE TABLE IF NOT EXISTS alerts (
    id BIGSERIAL,
    device_id VARCHAR(255) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    metric_name VARCHAR(255) NOT NULL,
    alert_type VARCHAR(50) NOT NULL CHECK (alert_type IN ('ANOMALY', 'THRESHOLD_HIGH', 'THRESHOLD_LOW', 'DEVICE_OFFLINE', 'DATA_QUALITY', 'SYSTEM_ERROR')),
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status VARCHAR(20) DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'CLOSED', 'SUPPRESSED')),
    title VARCHAR(500) NOT NULL,
    description TEXT,
    value DOUBLE PRECISION NOT NULL,
    expected_min DOUBLE PRECISION,
    expected_max DOUBLE PRECISION,
    z_score DOUBLE PRECISION,
    threshold_value DOUBLE PRECISION,
    acknowledged_by VARCHAR(255),
    acknowledged_at TIMESTAMPTZ,
    acknowledged_note TEXT,
    resolved_by VARCHAR(255),
    resolved_at TIMESTAMPTZ,
    resolved_note TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    PRIMARY KEY (id, timestamp)
);

-- Create alert metadata table
CREATE TABLE IF NOT EXISTS alert_metadata (
    alert_id BIGINT,
    metadata_key VARCHAR(255) NOT NULL,
    metadata_value TEXT,
    PRIMARY KEY (alert_id, metadata_key)
);

-- Convert metric_aggregates to hypertable
SELECT create_hypertable('metric_aggregates', 'timestamp',
    chunk_time_interval => INTERVAL '1 hour',
    if_not_exists => TRUE
);

-- Convert alerts to hypertable
SELECT create_hypertable('alerts', 'timestamp',
    chunk_time_interval => INTERVAL '1 day',
    if_not_exists => TRUE
);

-- Create indexes for devices table
CREATE INDEX IF NOT EXISTS idx_devices_status ON devices(status);
CREATE INDEX IF NOT EXISTS idx_devices_type ON devices(device_type);
CREATE INDEX IF NOT EXISTS idx_devices_location ON devices(location);
CREATE INDEX IF NOT EXISTS idx_devices_manufacturer ON devices(manufacturer);
CREATE INDEX IF NOT EXISTS idx_devices_last_seen ON devices(last_seen);
CREATE INDEX IF NOT EXISTS idx_devices_created_at ON devices(created_at);
CREATE INDEX IF NOT EXISTS idx_devices_updated_at ON devices(updated_at);

-- Create indexes for device_metadata table
CREATE INDEX IF NOT EXISTS idx_device_metadata_key ON device_metadata(metadata_key);
CREATE INDEX IF NOT EXISTS idx_device_metadata_value ON device_metadata(metadata_value);

-- Create indexes for metric_aggregates table
CREATE INDEX IF NOT EXISTS idx_metric_aggregates_device_id ON metric_aggregates(device_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_metric_aggregates_metric_name ON metric_aggregates(metric_name, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_metric_aggregates_device_metric ON metric_aggregates(device_id, metric_name, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_metric_aggregates_aggregation_type ON metric_aggregates(aggregation_type, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_metric_aggregates_window ON metric_aggregates(window_start, window_end);
CREATE INDEX IF NOT EXISTS idx_metric_aggregates_value ON metric_aggregates(value);

-- Create indexes for alerts table
CREATE INDEX IF NOT EXISTS idx_alerts_device_id ON alerts(device_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_status ON alerts(status, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_severity ON alerts(severity, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_alert_type ON alerts(alert_type, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_metric_name ON alerts(metric_name, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_device_metric ON alerts(device_id, metric_name, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_status_severity ON alerts(status, severity, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_z_score ON alerts(z_score) WHERE z_score IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_alerts_acknowledged_by ON alerts(acknowledged_by) WHERE acknowledged_by IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_alerts_resolved_by ON alerts(resolved_by) WHERE resolved_by IS NOT NULL;

-- Create composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_metric_aggregates_device_time_metric ON metric_aggregates(device_id, timestamp DESC, metric_name);
CREATE INDEX IF NOT EXISTS idx_alerts_device_status_time ON alerts(device_id, status, timestamp DESC);

-- Set up continuous aggregates for common time-based queries
-- Hourly aggregates
CREATE MATERIALIZED VIEW IF NOT EXISTS metric_aggregates_hourly
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', timestamp) AS bucket,
    device_id,
    metric_name,
    aggregation_type,
    AVG(value) as avg_value,
    MIN(value) as min_value,
    MAX(value) as max_value,
    SUM(value) as sum_value,
    COUNT(*) as count_value,
    STDDEV(value) as std_dev
FROM metric_aggregates
WHERE timestamp > NOW() - INTERVAL '7 days'
GROUP BY bucket, device_id, metric_name, aggregation_type;

-- Daily aggregates
CREATE MATERIALIZED VIEW IF NOT EXISTS metric_aggregates_daily
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 day', timestamp) AS bucket,
    device_id,
    metric_name,
    aggregation_type,
    AVG(value) as avg_value,
    MIN(value) as min_value,
    MAX(value) as max_value,
    SUM(value) as sum_value,
    COUNT(*) as count_value,
    STDDEV(value) as std_dev
FROM metric_aggregates
WHERE timestamp > NOW() - INTERVAL '90 days'
GROUP BY bucket, device_id, metric_name, aggregation_type;

-- Alert summary view
CREATE MATERIALIZED VIEW IF NOT EXISTS alert_summary_hourly
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', timestamp) AS bucket,
    device_id,
    alert_type,
    severity,
    COUNT(*) as alert_count,
    COUNT(CASE WHEN status = 'OPEN' THEN 1 END) as open_count,
    COUNT(CASE WHEN status = 'RESOLVED' THEN 1 END) as resolved_count
FROM alerts
WHERE timestamp > NOW() - INTERVAL '30 days'
GROUP BY bucket, device_id, alert_type, severity;

-- Set up refresh policies for continuous aggregates
SELECT add_continuous_aggregate_policy('metric_aggregates_hourly',
    start_offset => INTERVAL '3 hours',
    end_offset => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour',
    if_not_exists => TRUE);

SELECT add_continuous_aggregate_policy('metric_aggregates_daily',
    start_offset => INTERVAL '2 days',
    end_offset => INTERVAL '1 day',
    schedule_interval => INTERVAL '1 day',
    if_not_exists => TRUE);

SELECT add_continuous_aggregate_policy('alert_summary_hourly',
    start_offset => INTERVAL '3 hours',
    end_offset => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour',
    if_not_exists => TRUE);

-- Set up data retention policies
-- Keep raw metric data for 90 days
SELECT add_retention_policy('metric_aggregates', INTERVAL '90 days', if_not_exists => TRUE);

-- Keep alerts for 1 year
SELECT add_retention_policy('alerts', INTERVAL '1 year', if_not_exists => TRUE);

-- Create functions for common operations
-- Function to update device last_seen timestamp
CREATE OR REPLACE FUNCTION update_device_last_seen(p_device_id VARCHAR(255))
RETURNS VOID AS $$
BEGIN
    UPDATE devices
    SET last_seen = NOW(), updated_at = NOW()
    WHERE device_id = p_device_id;
END;
$$ LANGUAGE plpgsql;

-- Function to get device health status
CREATE OR REPLACE FUNCTION get_device_health_status(p_device_id VARCHAR(255), p_threshold_minutes INTEGER DEFAULT 60)
RETURNS VARCHAR(20) AS $$
DECLARE
    last_seen_time TIMESTAMPTZ;
    health_status VARCHAR(20);
BEGIN
    SELECT last_seen INTO last_seen_time FROM devices WHERE device_id = p_device_id;

    IF last_seen_time IS NULL THEN
        health_status := 'UNKNOWN';
    ELSIF last_seen_time < NOW() - INTERVAL '1 minute' * p_threshold_minutes THEN
        health_status := 'OFFLINE';
    ELSE
        health_status := 'ONLINE';
    END IF;

    RETURN health_status;
END;
$$ LANGUAGE plpgsql;

-- Function to calculate metric statistics
CREATE OR REPLACE FUNCTION get_metric_statistics(
    p_device_id VARCHAR(255),
    p_metric_name VARCHAR(255),
    p_start_time TIMESTAMPTZ,
    p_end_time TIMESTAMPTZ
)
RETURNS TABLE(
    avg_value DOUBLE PRECISION,
    min_value DOUBLE PRECISION,
    max_value DOUBLE PRECISION,
    count_value BIGINT,
    std_dev DOUBLE PRECISION
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        AVG(ma.value) as avg_value,
        MIN(ma.value) as min_value,
        MAX(ma.value) as max_value,
        COUNT(*) as count_value,
        STDDEV(ma.value) as std_dev
    FROM metric_aggregates ma
    WHERE ma.device_id = p_device_id
        AND ma.metric_name = p_metric_name
        AND ma.timestamp BETWEEN p_start_time AND p_end_time;
END;
$$ LANGUAGE plpgsql;

-- Insert sample data for development/testing
INSERT INTO devices (device_id, name, device_type, location, manufacturer, model, status)
VALUES
    ('sensor-001', 'Temperature Sensor 1', 'temperature_sensor', 'Building A - Floor 1', 'SensorCorp', 'TC-100', 'ACTIVE'),
    ('sensor-002', 'Humidity Sensor 1', 'humidity_sensor', 'Building A - Floor 1', 'SensorCorp', 'HC-200', 'ACTIVE'),
    ('sensor-003', 'Pressure Sensor 1', 'pressure_sensor', 'Building B - Floor 2', 'PressureTech', 'PT-300', 'ACTIVE'),
    ('gateway-001', 'IoT Gateway 1', 'gateway', 'Building A - Network Closet', 'GatewayInc', 'GW-500', 'ACTIVE'),
    ('camera-001', 'Security Camera 1', 'camera', 'Building A - Entrance', 'SecureCam', 'SC-800', 'MAINTENANCE')
ON CONFLICT (device_id) DO NOTHING;

-- Insert sample metadata
INSERT INTO device_metadata (device_id, metadata_key, metadata_value)
VALUES
    ('sensor-001', 'calibration_date', '2023-10-01'),
    ('sensor-001', 'firmware_auto_update', 'enabled'),
    ('sensor-002', 'calibration_date', '2023-09-15'),
    ('gateway-001', 'network_interface', 'ethernet'),
    ('gateway-001', 'max_devices', '100')
ON CONFLICT (device_id, metadata_key) DO NOTHING;

-- Create triggers for updating timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to devices table
DROP TRIGGER IF EXISTS update_devices_updated_at ON devices;
CREATE TRIGGER update_devices_updated_at
    BEFORE UPDATE ON devices
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Apply trigger to alerts table
DROP TRIGGER IF EXISTS update_alerts_updated_at ON alerts;
CREATE TRIGGER update_alerts_updated_at
    BEFORE UPDATE ON alerts
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Grant permissions to application user
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO iot_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO iot_user;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO iot_user;

-- Vacuum and analyze tables for optimal performance
VACUUM ANALYZE devices;
VACUUM ANALYZE device_metadata;
VACUUM ANALYZE metric_aggregates;
VACUUM ANALYZE alerts;

COMMIT;
