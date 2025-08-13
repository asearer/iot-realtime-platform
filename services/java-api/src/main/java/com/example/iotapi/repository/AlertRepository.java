package com.example.iotapi.repository;

import com.example.iotapi.model.Alert;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    // Find alerts by device ID
    List<Alert> findByDeviceId(String deviceId);

    // Find alerts by device ID with pagination
    Page<Alert> findByDeviceId(String deviceId, Pageable pageable);

    // Find alerts by status
    List<Alert> findByStatus(Alert.Status status);

    // Find alerts by severity
    List<Alert> findBySeverity(Alert.Severity severity);

    // Find alerts by type
    List<Alert> findByAlertType(Alert.AlertType alertType);

    // Find alerts by device and status
    List<Alert> findByDeviceIdAndStatus(String deviceId, Alert.Status status);

    // Find alerts by severity and status
    List<Alert> findBySeverityAndStatus(Alert.Severity severity, Alert.Status status);

    // Find alerts within time range
    List<Alert> findByTimestampBetween(Instant startTime, Instant endTime);

    // Find alerts by device within time range
    List<Alert> findByDeviceIdAndTimestampBetween(
        String deviceId,
        Instant startTime,
        Instant endTime
    );

    // Find open alerts (status = OPEN)
    @Query("SELECT a FROM Alert a WHERE a.status = 'OPEN' ORDER BY a.severity DESC, a.timestamp DESC")
    List<Alert> findOpenAlerts();

    // Find open alerts for a device
    List<Alert> findByDeviceIdAndStatusOrderByTimestampDesc(String deviceId, Alert.Status status);

    // Find alerts by metric name
    List<Alert> findByMetricNameAndTimestampBetween(
        String metricName,
        Instant startTime,
        Instant endTime
    );

    // Find unacknowledged alerts
    @Query("SELECT a FROM Alert a WHERE a.status = 'OPEN' AND a.acknowledgedAt IS NULL ORDER BY a.severity DESC, a.timestamp DESC")
    List<Alert> findUnacknowledgedAlerts();

    // Find alerts by multiple criteria with pagination
    @Query(
        "SELECT a FROM Alert a WHERE " +
        "(:deviceId IS NULL OR a.deviceId = :deviceId) AND " +
        "(:alertType IS NULL OR a.alertType = :alertType) AND " +
        "(:severity IS NULL OR a.severity = :severity) AND " +
        "(:status IS NULL OR a.status = :status) AND " +
        "(:metricName IS NULL OR a.metricName = :metricName) AND " +
        "a.timestamp BETWEEN :startTime AND :endTime " +
        "ORDER BY a.severity DESC, a.timestamp DESC"
    )
    Page<Alert> findAlertsWithFilters(
        @Param("deviceId") String deviceId,
        @Param("alertType") Alert.AlertType alertType,
        @Param("severity") Alert.Severity severity,
        @Param("status") Alert.Status status,
        @Param("metricName") String metricName,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime,
        Pageable pageable
    );

    // Get alert counts by severity
    @Query("SELECT a.severity, COUNT(a) FROM Alert a WHERE a.status = 'OPEN' GROUP BY a.severity")
    List<Object[]> getOpenAlertCountsBySeverity();

    // Get alert counts by device
    @Query(
        "SELECT a.deviceId, COUNT(a) FROM Alert a WHERE " +
        "a.status = 'OPEN' AND " +
        "a.timestamp BETWEEN :startTime AND :endTime " +
        "GROUP BY a.deviceId " +
        "ORDER BY COUNT(a) DESC"
    )
    List<Object[]> getAlertCountsByDevice(
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    // Get alert counts by alert type
    @Query("SELECT a.alertType, COUNT(a) FROM Alert a WHERE a.timestamp BETWEEN :startTime AND :endTime GROUP BY a.alertType")
    List<Object[]> getAlertCountsByType(
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    // Find alerts that need attention (open for more than X minutes)
    @Query(
        "SELECT a FROM Alert a WHERE " +
        "a.status = 'OPEN' AND " +
        "a.timestamp <= :thresholdTime " +
        "ORDER BY a.severity DESC, a.timestamp ASC"
    )
    List<Alert> findStaleOpenAlerts(@Param("thresholdTime") Instant thresholdTime);

    // Find critical alerts
    List<Alert> findBySeverityAndStatusOrderByTimestampDesc(
        Alert.Severity severity,
        Alert.Status status
    );

    // Find alerts acknowledged by a specific user
    List<Alert> findByAcknowledgedByOrderByAcknowledgedAtDesc(String acknowledgedBy);

    // Find alerts resolved by a specific user
    List<Alert> findByResolvedByOrderByResolvedAtDesc(String resolvedBy);

    // Find recent alerts (last N hours)
    @Query(
        "SELECT a FROM Alert a WHERE " +
        "a.timestamp >= :sinceTime " +
        "ORDER BY a.timestamp DESC"
    )
    List<Alert> findRecentAlerts(@Param("sinceTime") Instant sinceTime);

    // Find alerts for multiple devices
    List<Alert> findByDeviceIdInAndStatusOrderByTimestampDesc(
        List<String> deviceIds,
        Alert.Status status
    );

    // Count open alerts by severity
    long countByStatusAndSeverity(Alert.Status status, Alert.Severity severity);

    // Count alerts by device and status
    long countByDeviceIdAndStatus(String deviceId, Alert.Status status);

    // Find latest alert for a device and metric
    @Query(
        "SELECT a FROM Alert a WHERE " +
        "a.deviceId = :deviceId AND " +
        "a.metricName = :metricName " +
        "ORDER BY a.timestamp DESC LIMIT 1"
    )
    Optional<Alert> findLatestByDeviceAndMetric(
        @Param("deviceId") String deviceId,
        @Param("metricName") String metricName
    );

    // Find alerts with high Z-scores (anomalies)
    @Query(
        "SELECT a FROM Alert a WHERE " +
        "a.zScore IS NOT NULL AND " +
        "ABS(a.zScore) >= :minZScore AND " +
        "a.timestamp BETWEEN :startTime AND :endTime " +
        "ORDER BY ABS(a.zScore) DESC"
    )
    List<Alert> findHighZScoreAlerts(
        @Param("minZScore") Double minZScore,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    // Get alert trend data (counts over time intervals)
    @Query(
        "SELECT " +
        "DATE_TRUNC('hour', a.timestamp) as hour, " +
        "a.severity, " +
        "COUNT(a) as alertCount " +
        "FROM Alert a WHERE " +
        "a.timestamp BETWEEN :startTime AND :endTime " +
        "GROUP BY DATE_TRUNC('hour', a.timestamp), a.severity " +
        "ORDER BY hour ASC"
    )
    List<Object[]> getHourlyAlertTrends(
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    // Find devices with most alerts
    @Query(
        "SELECT a.deviceId, COUNT(a) as alertCount " +
        "FROM Alert a WHERE " +
        "a.timestamp BETWEEN :startTime AND :endTime " +
        "GROUP BY a.deviceId " +
        "ORDER BY alertCount DESC"
    )
    List<Object[]> findDevicesWithMostAlerts(
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime,
        Pageable pageable
    );

    // Delete resolved alerts older than specified time
    void deleteByStatusAndTimestampBefore(Alert.Status status, Instant cutoffTime);

    // Find suppressed alerts
    List<Alert> findByStatusOrderByTimestampDesc(Alert.Status status);

    // Get average resolution time for resolved alerts
    @Query(
        "SELECT AVG(EXTRACT(EPOCH FROM (a.resolvedAt - a.createdAt))/60) " +
        "FROM Alert a WHERE " +
        "a.status = 'RESOLVED' AND " +
        "a.resolvedAt IS NOT NULL AND " +
        "a.createdAt BETWEEN :startTime AND :endTime"
    )
    Double getAverageResolutionTimeInMinutes(
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    // Check if similar alert exists for device and metric
    @Query(
        "SELECT COUNT(a) > 0 FROM Alert a WHERE " +
        "a.deviceId = :deviceId AND " +
        "a.metricName = :metricName AND " +
        "a.alertType = :alertType AND " +
        "a.status IN ('OPEN', 'ACKNOWLEDGED') AND " +
        "a.timestamp >= :recentTime"
    )
    boolean existsSimilarRecentAlert(
        @Param("deviceId") String deviceId,
        @Param("metricName") String metricName,
        @Param("alertType") Alert.AlertType alertType,
        @Param("recentTime") Instant recentTime
    );
}
