package com.example.iotapi.repository;

import com.example.iotapi.model.MetricAggregate;
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
public interface MetricAggregateRepository
    extends JpaRepository<MetricAggregate, Long> {
    // Find aggregates by device ID
    List<MetricAggregate> findByDeviceId(String deviceId);

    // Find aggregates by device ID within time range
    List<MetricAggregate> findByDeviceIdAndTimestampBetween(
        String deviceId,
        Instant startTime,
        Instant endTime
    );

    // Find aggregates by metric name within time range
    List<MetricAggregate> findByMetricNameAndTimestampBetween(
        String metricName,
        Instant startTime,
        Instant endTime
    );

    // Find aggregates by device and metric within time range
    List<MetricAggregate> findByDeviceIdAndMetricNameAndTimestampBetween(
        String deviceId,
        String metricName,
        Instant startTime,
        Instant endTime
    );

    // Find aggregates by aggregation type
    List<MetricAggregate> findByAggregationType(
        MetricAggregate.AggregationType aggregationType
    );

    // Find latest aggregate for a device and metric
    @Query(
        "SELECT m FROM MetricAggregate m WHERE m.deviceId = :deviceId AND m.metricName = :metricName ORDER BY m.timestamp DESC LIMIT 1"
    )
    Optional<MetricAggregate> findLatestByDeviceAndMetric(
        @Param("deviceId") String deviceId,
        @Param("metricName") String metricName
    );

    // Get time series data for a specific device and metric
    @Query(
        "SELECT m FROM MetricAggregate m WHERE " +
        "m.deviceId = :deviceId AND " +
        "m.metricName = :metricName AND " +
        "m.aggregationType = :aggregationType AND " +
        "m.timestamp BETWEEN :startTime AND :endTime " +
        "ORDER BY m.timestamp ASC"
    )
    List<MetricAggregate> getTimeSeriesData(
        @Param("deviceId") String deviceId,
        @Param("metricName") String metricName,
        @Param(
            "aggregationType"
        ) MetricAggregate.AggregationType aggregationType,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    // Get aggregated statistics for a metric across all devices
    @Query(
        "SELECT " +
        "AVG(m.value) as avgValue, " +
        "MIN(m.value) as minValue, " +
        "MAX(m.value) as maxValue, " +
        "SUM(m.value) as sumValue, " +
        "COUNT(m) as countValue " +
        "FROM MetricAggregate m WHERE " +
        "m.metricName = :metricName AND " +
        "m.timestamp BETWEEN :startTime AND :endTime"
    )
    Object[] getMetricStatistics(
        @Param("metricName") String metricName,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    // Get top N devices by average metric value
    @Query(
        "SELECT m.deviceId, AVG(m.value) as avgValue " +
        "FROM MetricAggregate m WHERE " +
        "m.metricName = :metricName AND " +
        "m.timestamp BETWEEN :startTime AND :endTime " +
        "GROUP BY m.deviceId " +
        "ORDER BY avgValue DESC"
    )
    List<Object[]> getTopDevicesByMetric(
        @Param("metricName") String metricName,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime,
        Pageable pageable
    );

    // Find aggregates with values outside normal range (potential anomalies)
    @Query(
        "SELECT m FROM MetricAggregate m WHERE " +
        "m.metricName = :metricName AND " +
        "m.timestamp BETWEEN :startTime AND :endTime AND " +
        "(m.value < :minThreshold OR m.value > :maxThreshold) " +
        "ORDER BY m.timestamp DESC"
    )
    List<MetricAggregate> findAnomalousValues(
        @Param("metricName") String metricName,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime,
        @Param("minThreshold") Double minThreshold,
        @Param("maxThreshold") Double maxThreshold
    );

    // Get hourly aggregations for a device and metric
    @Query(
        "SELECT " +
        "DATE_TRUNC('hour', m.timestamp) as hour, " +
        "AVG(m.value) as avgValue, " +
        "MIN(m.value) as minValue, " +
        "MAX(m.value) as maxValue " +
        "FROM MetricAggregate m WHERE " +
        "m.deviceId = :deviceId AND " +
        "m.metricName = :metricName AND " +
        "m.timestamp BETWEEN :startTime AND :endTime " +
        "GROUP BY DATE_TRUNC('hour', m.timestamp) " +
        "ORDER BY hour ASC"
    )
    List<Object[]> getHourlyAggregations(
        @Param("deviceId") String deviceId,
        @Param("metricName") String metricName,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    // Get daily aggregations for a device and metric
    @Query(
        "SELECT " +
        "DATE_TRUNC('day', m.timestamp) as day, " +
        "AVG(m.value) as avgValue, " +
        "MIN(m.value) as minValue, " +
        "MAX(m.value) as maxValue " +
        "FROM MetricAggregate m WHERE " +
        "m.deviceId = :deviceId AND " +
        "m.metricName = :metricName AND " +
        "m.timestamp BETWEEN :startTime AND :endTime " +
        "GROUP BY DATE_TRUNC('day', m.timestamp) " +
        "ORDER BY day ASC"
    )
    List<Object[]> getDailyAggregations(
        @Param("deviceId") String deviceId,
        @Param("metricName") String metricName,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    // Find all unique metric names for a device
    @Query(
        "SELECT DISTINCT m.metricName FROM MetricAggregate m WHERE m.deviceId = :deviceId"
    )
    List<String> findMetricNamesByDevice(@Param("deviceId") String deviceId);

    // Find all unique device IDs that have a specific metric
    @Query(
        "SELECT DISTINCT m.deviceId FROM MetricAggregate m WHERE m.metricName = :metricName"
    )
    List<String> findDeviceIdsByMetric(@Param("metricName") String metricName);

    // Get recent aggregates for a device (last N hours)
    @Query(
        "SELECT m FROM MetricAggregate m WHERE " +
        "m.deviceId = :deviceId AND " +
        "m.timestamp >= :sinceTime " +
        "ORDER BY m.timestamp DESC"
    )
    List<MetricAggregate> findRecentByDevice(
        @Param("deviceId") String deviceId,
        @Param("sinceTime") Instant sinceTime
    );

    // Delete old aggregates (for data retention)
    void deleteByTimestampBefore(Instant cutoffTime);

    // Count aggregates in time range
    long countByTimestampBetween(Instant startTime, Instant endTime);

    // Count aggregates by device in time range
    long countByDeviceIdAndTimestampBetween(
        String deviceId,
        Instant startTime,
        Instant endTime
    );

    // Get aggregates with pagination and time range
    Page<MetricAggregate> findByTimestampBetween(
        Instant startTime,
        Instant endTime,
        Pageable pageable
    );

    // TimescaleDB specific - get data from last N intervals
    @Query(
        "SELECT m FROM MetricAggregate m WHERE " +
        "m.deviceId = :deviceId AND " +
        "m.metricName = :metricName AND " +
        "m.aggregationType = :aggregationType AND " +
        "m.timestamp >= NOW() - INTERVAL ':intervalCount :intervalType' " +
        "ORDER BY m.timestamp ASC"
    )
    List<MetricAggregate> getLastNIntervals(
        @Param("deviceId") String deviceId,
        @Param("metricName") String metricName,
        @Param(
            "aggregationType"
        ) MetricAggregate.AggregationType aggregationType,
        @Param("intervalCount") int intervalCount,
        @Param("intervalType") String intervalType
    );
}
