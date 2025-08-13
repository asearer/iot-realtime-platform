package main

import (
	"encoding/json"
	"fmt"
	"math/rand"
	"strconv"
	"time"
)

// TelemetryGenerator handles the generation of realistic IoT telemetry data
type TelemetryGenerator struct {
	DeviceID    string
	MetricTypes []string
}

// NewTelemetryGenerator creates a new telemetry generator for a device
func NewTelemetryGenerator(deviceID string, metricTypes []string) *TelemetryGenerator {
	return &TelemetryGenerator{
		DeviceID:    deviceID,
		MetricTypes: metricTypes,
	}
}

// GenerateRealisticTelemetry generates realistic telemetry data for the device
func (tg *TelemetryGenerator) GenerateRealisticTelemetry() TelemetryData {
	metrics := make(map[string]float64)

	// Generate different types of realistic IoT metrics
	for _, metricType := range tg.MetricTypes {
		switch metricType {
		case "temperature":
			// Simulate temperature readings between 18-28°C with some variation
			metrics["temperature"] = 18.0 + rand.Float64()*10.0 + (rand.Float64()-0.5)*2.0
		case "humidity":
			// Simulate humidity readings between 30-80%
			metrics["humidity"] = 30.0 + rand.Float64()*50.0 + (rand.Float64()-0.5)*5.0
		case "pressure":
			// Simulate atmospheric pressure around 1013 hPa ±50
			metrics["pressure"] = 1013.0 + (rand.Float64()-0.5)*100.0
		case "cpu_usage":
			// Simulate CPU usage 0-100%
			metrics["cpu_usage"] = rand.Float64() * 100.0
		case "memory_usage":
			// Simulate memory usage 20-90%
			metrics["memory_usage"] = 20.0 + rand.Float64()*70.0
		case "battery_level":
			// Simulate battery level 0-100%
			metrics["battery_level"] = rand.Float64() * 100.0
		case "signal_strength":
			// Simulate signal strength -120 to -30 dBm
			metrics["signal_strength"] = -120.0 + rand.Float64()*90.0
		case "vibration":
			// Simulate vibration sensor 0-10
			metrics["vibration"] = rand.Float64() * 10.0
		case "light_level":
			// Simulate light sensor 0-1000 lux
			metrics["light_level"] = rand.Float64() * 1000.0
		case "noise_level":
			// Simulate noise level 30-120 dB
			metrics["noise_level"] = 30.0 + rand.Float64()*90.0
		}
	}

	// Add some random additional metrics occasionally
	if rand.Float64() < 0.3 {
		metrics["power_consumption"] = rand.Float64() * 100.0 // Watts
	}

	if rand.Float64() < 0.2 {
		metrics["uptime"] = rand.Float64() * 86400.0 // Seconds
	}

	telemetry := TelemetryData{
		DeviceID:  tg.DeviceID,
		Timestamp: time.Now().UnixMilli(),
		Metrics:   metrics,
	}

	// Add raw data occasionally for testing
	if rand.Float64() < 0.1 {
		rawData, _ := json.Marshal(map[string]interface{}{
			"sensor_id": tg.DeviceID,
			"readings":  metrics,
			"metadata": map[string]string{
				"location": fmt.Sprintf("Building-%d", rand.Intn(10)+1),
				"floor":    strconv.Itoa(rand.Intn(5) + 1),
			},
		})
		telemetry.Raw = rawData
	}

	return telemetry
}

// GenerateAnomalyTelemetry generates telemetry data with anomalies for testing
func (tg *TelemetryGenerator) GenerateAnomalyTelemetry() TelemetryData {
	telemetry := tg.GenerateRealisticTelemetry()

	// Introduce anomalies randomly
	for metric := range telemetry.Metrics {
		if rand.Float64() < 0.1 { // 10% chance of anomaly
			switch metric {
			case "temperature":
				// Extreme temperature readings
				if rand.Float64() < 0.5 {
					telemetry.Metrics[metric] = -10.0 + rand.Float64()*5.0 // Very cold
				} else {
					telemetry.Metrics[metric] = 45.0 + rand.Float64()*20.0 // Very hot
				}
			case "humidity":
				// Extreme humidity readings
				if rand.Float64() < 0.5 {
					telemetry.Metrics[metric] = rand.Float64() * 10.0 // Very dry
				} else {
					telemetry.Metrics[metric] = 95.0 + rand.Float64()*5.0 // Very humid
				}
			case "cpu_usage":
				telemetry.Metrics[metric] = 95.0 + rand.Float64()*5.0 // High CPU
			case "memory_usage":
				telemetry.Metrics[metric] = 90.0 + rand.Float64()*10.0 // High memory
			case "battery_level":
				telemetry.Metrics[metric] = rand.Float64() * 5.0 // Low battery
			}
		}
	}

	return telemetry
}
