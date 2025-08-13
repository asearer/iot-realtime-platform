-- IoT Real-Time Platform - TimescaleDB Initialization Script
-- This script sets up the initial database schema and hypertables

-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Create raw telemetry data table
CREATE TABLE IF NOT EXISTS raw_telemetry (
    device_id TEXT NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    metric_name TEXT NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    raw_data JSONB,
    ingested_at TIMESTAMPTZ DEFAULT NOW()
);

-- Convert to hypertable (partitioned by time)
SELECT create_hypertable('raw_telemetry', 'timestamp', if_not_exists => TRUE);

-- Create metric aggregates table
CREATE TABLE IF NOT EXISTS metric_aggregates (
    device_id TEXT NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    metric_name TEXT NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    sample_count INTEGER NOT NULL,
    min_value DOUBLE PRECISION,
    max_value DOUBLE PRECISION,
    std_dev DOUBLE PRECISION,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Convert to hypertable
SELECT create_hypertable('metric_aggregates', 'timestamp', if_not_exists => TRUE);

-- Create alerts table
CREATE TABLE IF NOT EXISTS alerts (
    id SERIAL PRIMARY KEY,
    device_id TEXT NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    metric_name TEXT NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    alert_type TEXT NOT NULL,
    severity TEXT NOT NULL CHECK (severity IN ('low', 'medium', 'high', 'critical')),
    z_score DOUBLE PRECISION,
    threshold DOUBLE PRECISION,
    status TEXT DEFAULT 'open' CHECK (status IN ('open', 'acknowledged', 'resolved', 'closed')),
    message TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    acknowledged_at TIMESTAMPTZ,
    acknowledged_by TEXT,
    resolved_at TIMESTAMPTZ,
    resolved_by TEXT
);

-- Convert to hypertable
SELECT create_hypertable('alerts', 'timestamp', if_not_exists => TRUE);

-- Create devices metadata table
CREATE TABLE IF NOT EXISTS devices (
    device_id TEXT PRIMARY KEY,
    device_name TEXT,
    device_type TEXT,
    location TEXT,
    firmware_version TEXT,
    last_seen TIMESTAMPTZ,
    status TEXT DEFAULT 'active' CHECK (status IN ('active', 'inactive', 'maintenance', 'offline')),
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create device groups table
CREATE TABLE IF NOT EXISTS device_groups (
    id SERIAL PRIMARY KEY,
    group_name TEXT UNIQUE NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create device group memberships
CREATE TABLE IF NOT EXISTS device_group_memberships (
    device_id TEXT REFERENCES devices(device_id) ON DELETE CASCADE,
    group_id INTEGER REFERENCES device_groups(id) ON DELETE CASCADE,
    added_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (device_id, group_id)
);

-- Create users table for authentication/authorization
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username TEXT UNIQUE NOT NULL,
    email TEXT UNIQUE NOT NULL,
    password_hash TEXT,
    role TEXT DEFAULT 'viewer' CHECK (role IN ('admin', 'operator', 'viewer')),
    is_active BOOLEAN DEFAULT TRUE,
    last_login TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create audit log table
CREATE TABLE IF NOT EXISTS audit_log (
    id SERIAL PRIMARY KEY,
    timestamp TIMESTAMPTZ DEFAULT NOW(),
    user_id INTEGER REFERENCES users(id),
    action TEXT NOT NULL,
    resource_type TEXT NOT NULL,
    resource_id TEXT,
    details JSONB,
    ip_address INET
);

-- Convert audit log to hypertable
SELECT create_hypertable('audit_log', 'timestamp', if_not_exists => TRUE);

-- Create indexes for better query performance

-- Raw telemetry indexes
CREATE INDEX IF NOT EXISTS idx_raw_telemetry_device_time
ON raw_telemetry (device_id, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_raw_telemetry_metric_time
ON raw_telemetry (metric_name, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_raw_telemetry_ingested
ON raw_telemetry (ingested_at DESC);

-- Metric aggregates indexes
CREATE INDEX IF NOT EXISTS idx_metric_aggregates_device_time
ON metric_aggregates (device_id, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_metric_aggregates_metric_time
ON metric_aggregates (metric_name, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_metric_aggregates_window
ON metric_aggregates (window_start, window_end);

-- Alerts indexes
CREATE INDEX IF NOT EXISTS idx_alerts_device_time
ON alerts (device_id, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_alerts_status_time
ON alerts (status, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_alerts_severity_time
ON alerts (severity, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_alerts_type_time
ON alerts (alert_type, timestamp DESC);

-- Devices indexes
CREATE INDEX IF NOT EXISTS idx_devices_last_seen
ON devices (last_seen DESC);

CREATE INDEX IF NOT EXISTS idx_devices_status
ON devices (status);

CREATE INDEX IF NOT EXISTS idx_devices_type
ON devices (device_type);

CREATE INDEX IF NOT EXISTS idx_devices_location
ON devices (location);

-- Audit log indexes
CREATE INDEX IF NOT EXISTS idx_audit_log_user_time
ON audit_log (user_id, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_audit_log_resource
ON audit_log (resource_type, resource_id, timestamp DESC);

-- Create continuous aggregates for common queries

-- Hourly metric aggregates
CREATE MATERIALIZED VIEW IF NOT EXISTS hourly_metric_aggregates
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', timestamp) AS hour,
    device_id,
    metric_name,
    AVG(metric_value) as avg_value,
    MIN(metric_value) as min_value,
    MAX(metric_value) as max_value,
    COUNT(*) as sample_count,
    STDDEV(metric_value) as std_dev
FROM raw_telemetry
GROUP BY hour, device_id, metric_name
WITH NO DATA;

-- Add refresh policy for continuous aggregate
SELECT add_continuous_aggregate_policy('hourly_metric_aggregates',
    start_offset => INTERVAL '3 hours',
    end_offset => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour',
    if_not_exists => TRUE);

-- Daily metric aggregates
CREATE MATERIALIZED VIEW IF NOT EXISTS daily_metric_aggregates
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 day', timestamp) AS day,
    device_id,
    metric_name,
    AVG(metric_value) as avg_value,
    MIN(metric_value) as min_value,
    MAX(metric_value) as max_value,
    COUNT(*) as sample_count,
    STDDEV(metric_value) as std_dev
FROM raw_telemetry
GROUP BY day, device_id, metric_name
WITH NO DATA;

-- Add refresh policy for daily aggregates
SELECT add_continuous_aggregate_policy('daily_metric_aggregates',
    start_offset => INTERVAL '7 days',
    end_offset => INTERVAL '1 day',
    schedule_interval => INTERVAL '1 day',
    if_not_exists => TRUE);

-- Create data retention policies

-- Keep raw telemetry for 30 days
SELECT add_retention_policy('raw_telemetry',
    INTERVAL '30 days',
    if_not_exists => TRUE);

-- Keep alerts for 90 days
SELECT add_retention_policy('alerts',
    INTERVAL '90 days',
    if_not_exists => TRUE);

-- Keep audit log for 1 year
SELECT add_retention_policy('audit_log',
    INTERVAL '1 year',
    if_not_exists => TRUE);

-- Create some helper functions

-- Function to get device health status
CREATE OR REPLACE FUNCTION get_device_health(device_id_param TEXT)
RETURNS TABLE(
    device_id TEXT,
    last_seen TIMESTAMPTZ,
    status TEXT,
    health_score NUMERIC
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        d.device_id,
        d.last_seen,
        d.status,
        CASE
            WHEN d.last_seen > NOW() - INTERVAL '5 minutes' THEN 100
            WHEN d.last_seen > NOW() - INTERVAL '15 minutes' THEN 75
            WHEN d.last_seen > NOW() - INTERVAL '1 hour' THEN 50
            WHEN d.last_seen > NOW() - INTERVAL '1 day' THEN 25
            ELSE 0
        END::NUMERIC as health_score
    FROM devices d
    WHERE d.device_id = device_id_param;
END;
$$ LANGUAGE plpgsql;

-- Function to get recent anomalies
CREATE OR REPLACE FUNCTION get_recent_anomalies(hours_back INTEGER DEFAULT 24)
RETURNS TABLE(
    device_id TEXT,
    metric_name TEXT,
    alert_count BIGINT,
    latest_alert TIMESTAMPTZ
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        a.device_id,
        a.metric_name,
        COUNT(*) as alert_count,
        MAX(a.timestamp) as latest_alert
    FROM alerts a
    WHERE a.timestamp > NOW() - (hours_back || ' hours')::INTERVAL
      AND a.status = 'open'
    GROUP BY a.device_id, a.metric_name
    ORDER BY alert_count DESC, latest_alert DESC;
END;
$$ LANGUAGE plpgsql;

-- Insert some sample data for testing
INSERT INTO devices (device_id, device_name, device_type, location, status) VALUES
('device-001', 'Temperature Sensor 1', 'temperature_sensor', 'Building A - Floor 1', 'active'),
('device-002', 'Humidity Sensor 1', 'humidity_sensor', 'Building A - Floor 1', 'active'),
('device-003', 'Pressure Sensor 1', 'pressure_sensor', 'Building A - Floor 2', 'active'),
('device-004', 'Multi Sensor 1', 'multi_sensor', 'Building B - Floor 1', 'active'),
('device-005', 'Gateway 1', 'gateway', 'Building A - Entrance', 'active')
ON CONFLICT (device_id) DO NOTHING;

-- Insert sample device groups
INSERT INTO device_groups (group_name, description) VALUES
('Building A Sensors', 'All sensors in Building A'),
('Temperature Sensors', 'All temperature monitoring devices'),
('Critical Infrastructure', 'Mission-critical monitoring devices')
ON CONFLICT (group_name) DO NOTHING;

-- Insert sample user (admin user)
INSERT INTO users (username, email, role, password_hash) VALUES
('admin', 'admin@iot-platform.com', 'admin', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi'), -- password: password
('operator', 'operator@iot-platform.com', 'operator', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi'),
('viewer', 'viewer@iot-platform.com', 'viewer', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi')
ON CONFLICT (username) DO NOTHING;

-- Grant permissions
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO iot_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO iot_user;

-- Print completion message
DO $$
BEGIN
    RAISE NOTICE 'TimescaleDB initialization completed successfully!';
    RAISE NOTICE 'Created tables: raw_telemetry, metric_aggregates, alerts, devices, device_groups, device_group_memberships, users, audit_log';
    RAISE NOTICE 'Created continuous aggregates: hourly_metric_aggregates, daily_metric_aggregates';
    RAISE NOTICE 'Configured data retention policies for automatic cleanup';
    RAISE NOTICE 'Sample data inserted for testing';
END $$;
