package com.example.iotapi.service;

import com.example.iotapi.model.Alert;
import com.example.iotapi.repository.AlertRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AlertService {

    private final AlertRepository alertRepository;

    /**
     * Get all alerts with pagination
     */
    @Transactional(readOnly = true)
    public Page<Alert> getAllAlerts(Pageable pageable) {
        return alertRepository.findAll(pageable);
    }

    /**
     * Get alert by ID
     */
    @Transactional(readOnly = true)
    public Optional<Alert> getAlertById(Long alertId) {
        return alertRepository.findById(alertId);
    }

    /**
     * Get alert by ID or throw exception
     */
    @Transactional(readOnly = true)
    public Alert getAlertByIdOrThrow(Long alertId) {
        return alertRepository
            .findById(alertId)
            .orElseThrow(() ->
                new EntityNotFoundException("Alert not found with ID: " + alertId)
            );
    }

    /**
     * Create a new alert
     */
    public Alert createAlert(Alert alert) {
        // Set creation timestamp if not set
        if (alert.getCreatedAt() == null) {
            alert.setCreatedAt(Instant.now());
        }

        // Check for similar recent alerts to avoid spam
        if (isDuplicateAlert(alert)) {
            log.debug(
                "Skipping duplicate alert for device {} metric {}",
                alert.getDeviceId(),
                alert.getMetricName()
            );
            return null;
        }

        log.info(
            "Creating new {} alert for device {} metric {} with severity {}",
            alert.getAlertType(),
            alert.getDeviceId(),
            alert.getMetricName(),
            alert.getSeverity()
        );

        return alertRepository.save(alert);
    }

    /**
     * Create multiple alerts
     */
    public List<Alert> createAlerts(List<Alert> alerts) {
        Instant now = Instant.now();

        // Filter out duplicates and set timestamps
        List<Alert> validAlerts = alerts
            .stream()
            .filter(alert -> {
                if (alert.getCreatedAt() == null) {
                    alert.setCreatedAt(now);
                }
                return !isDuplicateAlert(alert);
            })
            .collect(Collectors.toList());

        if (validAlerts.isEmpty()) {
            return List.of();
        }

        log.info("Creating {} new alerts", validAlerts.size());
        return alertRepository.saveAll(validAlerts);
    }

    /**
     * Acknowledge an alert
     */
    public Alert acknowledgeAlert(Long alertId, String acknowledgedBy, String note) {
        Alert alert = getAlertByIdOrThrow(alertId);

        if (alert.isResolved()) {
            throw new IllegalStateException(
                "Cannot acknowledge resolved alert: " + alertId
            );
        }

        alert.acknowledge(acknowledgedBy, note);

        log.info(
            "Alert {} acknowledged by {} for device {}",
            alertId,
            acknowledgedBy,
            alert.getDeviceId()
        );

        return alertRepository.save(alert);
    }

    /**
     * Resolve an alert
     */
    public Alert resolveAlert(Long alertId, String resolvedBy, String note) {
        Alert alert = getAlertByIdOrThrow(alertId);

        alert.resolve(resolvedBy, note);

        log.info(
            "Alert {} resolved by {} for device {}",
            alertId,
            resolvedBy,
            alert.getDeviceId()
        );

        return alertRepository.save(alert);
    }

    /**
     * Close an alert
     */
    public Alert closeAlert(Long alertId, String closedBy, String note) {
        Alert alert = getAlertByIdOrThrow(alertId);

        alert.setStatus(Alert.Status.CLOSED);
        alert.setResolvedBy(closedBy);
        alert.setResolvedAt(Instant.now());
        alert.setResolvedNote(note);

        log.info(
            "Alert {} closed by {} for device {}",
            alertId,
            closedBy,
            alert.getDeviceId()
        );

        return alertRepository.save(alert);
    }

    /**
     * Suppress an alert
     */
    public Alert suppressAlert(Long alertId, String suppressedBy, String reason) {
        Alert alert = getAlertByIdOrThrow(alertId);

        alert.setStatus(Alert.Status.SUPPRESSED);
        alert.setResolvedBy(suppressedBy);
        alert.setResolvedAt(Instant.now());
        alert.setResolvedNote("Suppressed: " + reason);

        log.info(
            "Alert {} suppressed by {} for device {}",
            alertId,
            suppressedBy,
            alert.getDeviceId()
        );

        return alertRepository.save(alert);
    }

    /**
     * Get alerts for a specific device
     */
    @Transactional(readOnly = true)
    public List<Alert> getAlertsForDevice(String deviceId) {
        return alertRepository.findByDeviceId(deviceId);
    }

    /**
     * Get alerts for a specific device with pagination
     */
    @Transactional(readOnly = true)
    public Page<Alert> getAlertsForDevice(String deviceId, Pageable pageable) {
        return alertRepository.findByDeviceId(deviceId, pageable);
    }

    /**
     * Get open alerts
     */
    @Transactional(readOnly = true)
    public List<Alert> getOpenAlerts() {
        return alertRepository.findOpenAlerts();
    }

    /**
     * Get unacknowledged alerts
     */
    @Transactional(readOnly = true)
    public List<Alert> getUnacknowledgedAlerts() {
        return alertRepository.findUnacknowledgedAlerts();
    }

    /**
     * Get alerts by severity
     */
    @Transactional(readOnly = true)
    public List<Alert> getAlertsBySeverity(Alert.Severity severity) {
        return alertRepository.findBySeverity(severity);
    }

    /**
     * Get alerts by status
     */
    @Transactional(readOnly = true)
    public List<Alert> getAlertsByStatus(Alert.Status status) {
        return alertRepository.findByStatus(status);
    }

    /**
     * Get alerts by type
     */
    @Transactional(readOnly = true)
    public List<Alert> getAlertsByType(Alert.AlertType alertType) {
        return alertRepository.findByAlertType(alertType);
    }

    /**
     * Get alerts with filters
     */
    @Transactional(readOnly = true)
    public Page<Alert> getAlertsWithFilters(
        String deviceId,
        Alert.AlertType alertType,
        Alert.Severity severity,
        Alert.Status status,
        String metricName,
        Instant startTime,
        Instant endTime,
        Pageable pageable
    ) {
        return alertRepository.findAlertsWithFilters(
            deviceId,
            alertType,
            severity,
            status,
            metricName,
            startTime,
            endTime,
            pageable
        );
    }

    /**
     * Get alerts within time range
     */
    @Transactional(readOnly = true)
    public List<Alert> getAlertsInTimeRange(Instant startTime, Instant endTime) {
        return alertRepository.findByTimestampBetween(startTime, endTime);
    }

    /**
     * Get recent alerts (last N hours)
     */
    @Transactional(readOnly = true)
    public List<Alert> getRecentAlerts(int hoursAgo) {
        Instant sinceTime = Instant.now().minus(hoursAgo, ChronoUnit.HOURS);
        return alertRepository.findRecentAlerts(sinceTime);
    }

    /**
     * Get stale open alerts (open for more than N minutes)
     */
    @Transactional(readOnly = true)
    public List<Alert> getStaleOpenAlerts(int minutesAgo) {
        Instant thresholdTime = Instant.now().minus(minutesAgo, ChronoUnit.MINUTES);
        return alertRepository.findStaleOpenAlerts(thresholdTime);
    }

    /**
     * Get critical alerts
     */
    @Transactional(readOnly = true)
    public List<Alert> getCriticalAlerts() {
        return alertRepository.findBySeverityAndStatusOrderByTimestampDesc(
            Alert.Severity.CRITICAL,
            Alert.Status.OPEN
        );
    }

    /**
     * Get high Z-score alerts (anomalies)
     */
    @Transactional(readOnly = true)
    public List<Alert> getHighZScoreAlerts(
        Double minZScore,
        Instant startTime,
        Instant endTime
    ) {
        return alertRepository.findHighZScoreAlerts(
            minZScore,
            startTime,
            endTime
        );
    }

    /**
     * Get alert statistics by severity
     */
    @Transactional(readOnly = true)
    public Map<Alert.Severity, Long> getOpenAlertCountsBySeverity() {
        List<Object[]> results = alertRepository.getOpenAlertCountsBySeverity();
        return results
            .stream()
            .collect(
                Collectors.toMap(
                    row -> (Alert.Severity) row[0],
                    row -> (Long) row[1]
                )
            );
    }

    /**
     * Get alert counts by device
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getAlertCountsByDevice(
        Instant startTime,
        Instant endTime
    ) {
        List<Object[]> results = alertRepository.getAlertCountsByDevice(
            startTime,
            endTime
        );
        return results
            .stream()
            .collect(
                Collectors.toMap(
                    row -> (String) row[0],
                    row -> (Long) row[1]
                )
            );
    }

    /**
     * Get alert counts by type
     */
    @Transactional(readOnly = true)
    public Map<Alert.AlertType, Long> getAlertCountsByType(
        Instant startTime,
        Instant endTime
    ) {
        List<Object[]> results = alertRepository.getAlertCountsByType(
            startTime,
            endTime
        );
        return results
            .stream()
            .collect(
                Collectors.toMap(
                    row -> (Alert.AlertType) row[0],
                    row -> (Long) row[1]
                )
            );
    }

    /**
     * Get hourly alert trends
     */
    @Transactional(readOnly = true)
    public List<AlertTrend> getHourlyAlertTrends(
        Instant startTime,
        Instant endTime
    ) {
        List<Object[]> results = alertRepository.getHourlyAlertTrends(
            startTime,
            endTime
        );

        return results
            .stream()
            .map(row ->
                new AlertTrend(
                    (Instant) row[0], // hour
                    (Alert.Severity) row[1], // severity
                    ((Number) row[2]).longValue() // alertCount
                )
            )
            .collect(Collectors.toList());
    }

    /**
     * Get devices with most alerts
     */
    @Transactional(readOnly = true)
    public List<DeviceAlertSummary> getDevicesWithMostAlerts(
        Instant startTime,
        Instant endTime,
        int limit
    ) {
        Pageable pageable = Pageable.ofSize(limit);
        List<Object[]> results = alertRepository.findDevicesWithMostAlerts(
            startTime,
            endTime,
            pageable
        );

        return results
            .stream()
            .map(row ->
                new DeviceAlertSummary(
                    (String) row[0], // deviceId
                    ((Number) row[1]).longValue() // alertCount
                )
            )
            .collect(Collectors.toList());
    }

    /**
     * Get average resolution time for resolved alerts
     */
    @Transactional(readOnly = true)
    public Double getAverageResolutionTimeInMinutes(
        Instant startTime,
        Instant endTime
    ) {
        return alertRepository.getAverageResolutionTimeInMinutes(
            startTime,
            endTime
        );
    }

    /**
     * Bulk acknowledge alerts
     */
    public List<Alert> bulkAcknowledgeAlerts(
        List<Long> alertIds,
        String acknowledgedBy,
        String note
    ) {
        return alertIds
            .stream()
            .map(alertId -> {
                try {
                    return acknowledgeAlert(alertId, acknowledgedBy, note);
                } catch (Exception e) {
                    log.warn(
                        "Failed to acknowledge alert {}: {}",
                        alertId,
                        e.getMessage()
                    );
                    return null;
                }
            })
            .filter(alert -> alert != null)
            .collect(Collectors.toList());
    }

    /**
     * Bulk resolve alerts
     */
    public List<Alert> bulkResolveAlerts(
        List<Long> alertIds,
        String resolvedBy,
        String note
    ) {
        return alertIds
            .stream()
            .map(alertId -> {
                try {
                    return resolveAlert(alertId, resolvedBy, note);
                } catch (Exception e) {
                    log.warn(
                        "Failed to resolve alert {}: {}",
                        alertId,
                        e.getMessage()
                    );
                    return null;
                }
            })
            .filter(alert -> alert != null)
            .collect(Collectors.toList());
    }

    /**
     * Delete old resolved alerts (for cleanup)
     */
    public void deleteOldResolvedAlerts(int daysAgo) {
        Instant cutoffTime = Instant.now().minus(daysAgo, ChronoUnit.DAYS);
        log.info("Deleting resolved alerts older than {}", cutoffTime);
        alertRepository.deleteByStatusAndTimestampBefore(
            Alert.Status.RESOLVED,
            cutoffTime
        );
    }

    /**
     * Count alerts by device and status
     */
    @Transactional(readOnly = true)
    public long countAlertsByDeviceAndStatus(String deviceId, Alert.Status status) {
        return alertRepository.countByDeviceIdAndStatus(deviceId, status);
    }

    /**
     * Count open alerts by severity
     */
    @Transactional(readOnly = true)
    public long countOpenAlertsBySeverity(Alert.Severity severity) {
        return alertRepository.countByStatusAndSeverity(Alert.Status.OPEN, severity);
    }

    /**
     * Get latest alert for a device and metric
     */
    @Transactional(readOnly = true)
    public Optional<Alert> getLatestAlertForDeviceAndMetric(
        String deviceId,
        String metricName
    ) {
        return alertRepository.findLatestByDeviceAndMetric(deviceId, metricName);
    }

    /**
     * Check if a duplicate alert exists
     */
    private boolean isDuplicateAlert(Alert alert) {
        // Check for similar alerts in the last 5 minutes to avoid spam
        Instant recentTime = Instant.now().minus(5, ChronoUnit.MINUTES);

        return alertRepository.existsSimilarRecentAlert(
            alert.getDeviceId(),
            alert.getMetricName(),
            alert.getAlertType(),
            recentTime
        );
    }

    /**
     * Create anomaly alert from telemetry data
     */
    public Alert createAnomalyAlert(
        String deviceId,
        String metricName,
        Double value,
        Double expectedMin,
        Double expectedMax,
        Double zScore
    ) {
        Alert alert = new Alert();
        alert.setDeviceId(deviceId);
        alert.setTimestamp(Instant.now());
        alert.setMetricName(metricName);
        alert.setAlertType(Alert.AlertType.ANOMALY);
        alert.setValue(value);
        alert.setExpectedMin(expectedMin);
        alert.setExpectedMax(expectedMax);
        alert.setZScore(zScore);

        // Determine severity based on Z-score
        if (Math.abs(zScore) >= 5.0) {
            alert.setSeverity(Alert.Severity.CRITICAL);
        } else if (Math.abs(zScore) >= 4.0) {
            alert.setSeverity(Alert.Severity.HIGH);
        } else if (Math.abs(zScore) >= 3.0) {
            alert.setSeverity(Alert.Severity.MEDIUM);
        } else {
            alert.setSeverity(Alert.Severity.LOW);
        }

        alert.setTitle(
            String.format(
                "Anomalous %s value detected for device %s",
                metricName,
                deviceId
            )
        );
        alert.setDescription(
            String.format(
                "Value %.2f is outside expected range [%.2f, %.2f] with Z-score %.2f",
                value,
                expectedMin,
                expectedMax,
                zScore
            )
        );

        return createAlert(alert);
    }

    // Inner classes for DTOs
    public record AlertTrend(
        Instant hour,
        Alert.Severity severity,
        Long alertCount
    ) {}

    public record DeviceAlertSummary(String deviceId, Long alertCount) {}

    public record AlertSummary(
        Long totalAlerts,
        Long openAlerts,
        Long acknowledgedAlerts,
        Long resolvedAlerts,
        Map<Alert.Severity, Long> severityBreakdown
    ) {}

    /**
     * Get alert summary statistics
     */
    @Transactional(readOnly = true)
    public AlertSummary getAlertSummary(Instant startTime, Instant endTime) {
        List<Alert> alerts = getAlertsInTimeRange(startTime, endTime);

        long totalAlerts = alerts.size();
        long openAlerts = alerts.stream()
            .mapToLong(alert -> alert.getStatus() == Alert.Status.OPEN ? 1 : 0)
            .sum();
        long acknowledgedAlerts = alerts.stream()
            .mapToLong(alert -> alert.getStatus() == Alert.Status.ACKNOWLEDGED ? 1 : 0)
            .sum();
        long resolvedAlerts = alerts.stream()
            .mapToLong(alert -> alert.isResolved() ? 1 : 0)
            .sum();

        Map<Alert.Severity, Long> severityBreakdown = alerts.stream()
            .collect(
                Collectors.groupingBy(
                    Alert::getSeverity,
                    Collectors.counting()
                )
            );

        return new AlertSummary(
            totalAlerts,
            openAlerts,
            acknowledgedAlerts,
            resolvedAlerts,
            severityBreakdown
        );
    }
}
