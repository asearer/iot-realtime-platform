package database

import (
	"database/sql"
	"fmt"
	"log"
	"time"

	_ "github.com/lib/pq"
)

type TimescaleDB struct {
	db *sql.DB
}

type AggregateRecord struct {
	DeviceID    string    `json:"device_id"`
	Timestamp   time.Time `json:"timestamp"`
	WindowStart time.Time `json:"window_start"`
	WindowEnd   time.Time `json:"window_end"`
	MetricName  string    `json:"metric_name"`
	MetricValue float64   `json:"metric_value"`
	SampleCount int       `json:"sample_count"`
}

type AlertRecord struct {
	ID          int       `json:"id"`
	DeviceID    string    `json:"device_id"`
	Timestamp   time.Time `json:"timestamp"`
	MetricName  string    `json:"metric_name"`
	MetricValue float64   `json:"metric_value"`
	AlertType   string    `json:"alert_type"`
	Severity    string    `json:"severity"`
	ZScore      float64   `json:"z_score"`
	Threshold   float64   `json:"threshold"`
	Status      string    `json:"status"`
	Message     string    `json:"message"`
}

func NewTimescaleDB(connectionString string) (*TimescaleDB, error) {
	db, err := sql.Open("postgres", connectionString)
	if err != nil {
		return nil, fmt.Errorf("failed to open database connection: %w", err)
	}

	// Test the connection
	if err := db.Ping(); err != nil {
		return nil, fmt.Errorf("failed to ping database: %w", err)
	}

	tsdb := &TimescaleDB{db: db}

	// Initialize database schema
	if err := tsdb.initSchema(); err != nil {
		return nil, fmt.Errorf("failed to initialize database schema: %w", err)
	}

	log.Println("Successfully connected to TimescaleDB")
	return tsdb, nil
}

func (tsdb *TimescaleDB) initSchema() error {
	// Create aggregates table
	aggregatesSchema := `
		CREATE TABLE IF NOT EXISTS metric_aggregates (
			device_id TEXT NOT NULL,
			timestamp TIMESTAMPTZ NOT NULL,
			window_start TIMESTAMPTZ NOT NULL,
			window_end TIMESTAMPTZ NOT NULL,
			metric_name TEXT NOT NULL,
			metric_value DOUBLE PRECISION NOT NULL,
			sample_count INTEGER NOT NULL,
			created_at TIMESTAMPTZ DEFAULT NOW()
		);

		-- Convert to hypertable if not already done
		SELECT create_hypertable('metric_aggregates', 'timestamp', if_not_exists => TRUE);

		-- Create indexes for better query performance
		CREATE INDEX IF NOT EXISTS idx_metric_aggregates_device_time
		ON metric_aggregates (device_id, timestamp DESC);

		CREATE INDEX IF NOT EXISTS idx_metric_aggregates_metric_time
		ON metric_aggregates (metric_name, timestamp DESC);
	`

	if _, err := tsdb.db.Exec(aggregatesSchema); err != nil {
		return fmt.Errorf("failed to create aggregates schema: %w", err)
	}

	// Create alerts table
	alertsSchema := `
		CREATE TABLE IF NOT EXISTS alerts (
			id SERIAL PRIMARY KEY,
			device_id TEXT NOT NULL,
			timestamp TIMESTAMPTZ NOT NULL,
			metric_name TEXT NOT NULL,
			metric_value DOUBLE PRECISION NOT NULL,
			alert_type TEXT NOT NULL,
			severity TEXT NOT NULL,
			z_score DOUBLE PRECISION,
			threshold DOUBLE PRECISION,
			status TEXT DEFAULT 'open',
			message TEXT,
			created_at TIMESTAMPTZ DEFAULT NOW(),
			acknowledged_at TIMESTAMPTZ,
			resolved_at TIMESTAMPTZ
		);

		-- Convert to hypertable
		SELECT create_hypertable('alerts', 'timestamp', if_not_exists => TRUE);

		-- Create indexes
		CREATE INDEX IF NOT EXISTS idx_alerts_device_time
		ON alerts (device_id, timestamp DESC);

		CREATE INDEX IF NOT EXISTS idx_alerts_status_time
		ON alerts (status, timestamp DESC);

		CREATE INDEX IF NOT EXISTS idx_alerts_severity_time
		ON alerts (severity, timestamp DESC);
	`

	if _, err := tsdb.db.Exec(alertsSchema); err != nil {
		return fmt.Errorf("failed to create alerts schema: %w", err)
	}

	// Create devices table for metadata
	devicesSchema := `
		CREATE TABLE IF NOT EXISTS devices (
			device_id TEXT PRIMARY KEY,
			device_name TEXT,
			device_type TEXT,
			location TEXT,
			last_seen TIMESTAMPTZ,
			status TEXT DEFAULT 'active',
			metadata JSONB,
			created_at TIMESTAMPTZ DEFAULT NOW(),
			updated_at TIMESTAMPTZ DEFAULT NOW()
		);

		-- Create indexes
		CREATE INDEX IF NOT EXISTS idx_devices_last_seen
		ON devices (last_seen DESC);

		CREATE INDEX IF NOT EXISTS idx_devices_status
		ON devices (status);
	`

	if _, err := tsdb.db.Exec(devicesSchema); err != nil {
		return fmt.Errorf("failed to create devices schema: %w", err)
	}

	log.Println("Database schema initialized successfully")
	return nil
}

func (tsdb *TimescaleDB) InsertAggregate(aggregate AggregateRecord) error {
	query := `
		INSERT INTO metric_aggregates
		(device_id, timestamp, window_start, window_end, metric_name, metric_value, sample_count)
		VALUES ($1, $2, $3, $4, $5, $6, $7)
	`

	_, err := tsdb.db.Exec(query,
		aggregate.DeviceID,
		aggregate.Timestamp,
		aggregate.WindowStart,
		aggregate.WindowEnd,
		aggregate.MetricName,
		aggregate.MetricValue,
		aggregate.SampleCount,
	)

	if err != nil {
		return fmt.Errorf("failed to insert aggregate: %w", err)
	}

	return nil
}

func (tsdb *TimescaleDB) InsertAggregates(aggregates []AggregateRecord) error {
	if len(aggregates) == 0 {
		return nil
	}

	tx, err := tsdb.db.Begin()
	if err != nil {
		return fmt.Errorf("failed to begin transaction: %w", err)
	}
	defer tx.Rollback()

	stmt, err := tx.Prepare(`
		INSERT INTO metric_aggregates
		(device_id, timestamp, window_start, window_end, metric_name, metric_value, sample_count)
		VALUES ($1, $2, $3, $4, $5, $6, $7)
	`)
	if err != nil {
		return fmt.Errorf("failed to prepare statement: %w", err)
	}
	defer stmt.Close()

	for _, aggregate := range aggregates {
		_, err := stmt.Exec(
			aggregate.DeviceID,
			aggregate.Timestamp,
			aggregate.WindowStart,
			aggregate.WindowEnd,
			aggregate.MetricName,
			aggregate.MetricValue,
			aggregate.SampleCount,
		)
		if err != nil {
			return fmt.Errorf("failed to insert aggregate: %w", err)
		}
	}

	if err := tx.Commit(); err != nil {
		return fmt.Errorf("failed to commit transaction: %w", err)
	}

	return nil
}

func (tsdb *TimescaleDB) InsertAlert(alert AlertRecord) error {
	query := `
		INSERT INTO alerts
		(device_id, timestamp, metric_name, metric_value, alert_type, severity, z_score, threshold, status, message)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
		RETURNING id
	`

	var id int
	err := tsdb.db.QueryRow(query,
		alert.DeviceID,
		alert.Timestamp,
		alert.MetricName,
		alert.MetricValue,
		alert.AlertType,
		alert.Severity,
		alert.ZScore,
		alert.Threshold,
		alert.Status,
		alert.Message,
	).Scan(&id)

	if err != nil {
		return fmt.Errorf("failed to insert alert: %w", err)
	}

	log.Printf("Inserted alert with ID %d for device %s", id, alert.DeviceID)
	return nil
}

func (tsdb *TimescaleDB) UpdateDeviceLastSeen(deviceID string) error {
	query := `
		INSERT INTO devices (device_id, last_seen, updated_at)
		VALUES ($1, NOW(), NOW())
		ON CONFLICT (device_id)
		DO UPDATE SET last_seen = NOW(), updated_at = NOW()
	`

	_, err := tsdb.db.Exec(query, deviceID)
	if err != nil {
		return fmt.Errorf("failed to update device last seen: %w", err)
	}

	return nil
}

func (tsdb *TimescaleDB) GetRecentAggregates(deviceID string, hours int, limit int) ([]AggregateRecord, error) {
	query := `
		SELECT device_id, timestamp, window_start, window_end, metric_name, metric_value, sample_count
		FROM metric_aggregates
		WHERE device_id = $1 AND timestamp >= NOW() - INTERVAL '%d hours'
		ORDER BY timestamp DESC
		LIMIT $2
	`

	rows, err := tsdb.db.Query(fmt.Sprintf(query, hours), deviceID, limit)
	if err != nil {
		return nil, fmt.Errorf("failed to query aggregates: %w", err)
	}
	defer rows.Close()

	var aggregates []AggregateRecord
	for rows.Next() {
		var agg AggregateRecord
		err := rows.Scan(
			&agg.DeviceID,
			&agg.Timestamp,
			&agg.WindowStart,
			&agg.WindowEnd,
			&agg.MetricName,
			&agg.MetricValue,
			&agg.SampleCount,
		)
		if err != nil {
			return nil, fmt.Errorf("failed to scan aggregate: %w", err)
		}
		aggregates = append(aggregates, agg)
	}

	return aggregates, nil
}

func (tsdb *TimescaleDB) GetActiveAlerts(deviceID string, limit int) ([]AlertRecord, error) {
	query := `
		SELECT id, device_id, timestamp, metric_name, metric_value, alert_type,
		       severity, z_score, threshold, status, message
		FROM alerts
		WHERE device_id = $1 AND status = 'open'
		ORDER BY timestamp DESC
		LIMIT $2
	`

	rows, err := tsdb.db.Query(query, deviceID, limit)
	if err != nil {
		return nil, fmt.Errorf("failed to query alerts: %w", err)
	}
	defer rows.Close()

	var alerts []AlertRecord
	for rows.Next() {
		var alert AlertRecord
		err := rows.Scan(
			&alert.ID,
			&alert.DeviceID,
			&alert.Timestamp,
			&alert.MetricName,
			&alert.MetricValue,
			&alert.AlertType,
			&alert.Severity,
			&alert.ZScore,
			&alert.Threshold,
			&alert.Status,
			&alert.Message,
		)
		if err != nil {
			return nil, fmt.Errorf("failed to scan alert: %w", err)
		}
		alerts = append(alerts, alert)
	}

	return alerts, nil
}

func (tsdb *TimescaleDB) Close() error {
	if tsdb.db != nil {
		return tsdb.db.Close()
	}
	return nil
}

func (tsdb *TimescaleDB) HealthCheck() error {
	return tsdb.db.Ping()
}
