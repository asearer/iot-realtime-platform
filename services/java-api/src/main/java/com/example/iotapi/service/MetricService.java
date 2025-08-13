package com.example.iotapi.service;

import com.example.iotapi.model.MetricAggregate;
import com.example.iotapi.repository.MetricAggregateRepository;
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
public class MetricService {

    private final MetricAggregateRepository metricAggregateRepository;

    /**
     * Get all metric aggregates for a device
     */
    @Transactional(readOnly = true)
    public List<MetricAggregate> getMetricsForDevice(String deviceId) {
        return metricAggregateRepository.findByDeviceId(deviceId);
    }

    /**
     * Get metric aggregates for a device within time range
     */
    @Transactional(readOnly = true)
    public List<MetricAggregate> getMetricsForDevice(
        String deviceId,
        Instant startTime,
        Instant endTime
    ) {
        return metricAggregateRepository.findByDeviceIdAndTimestampBetween(
            deviceId,
            startTime,
            endTime
        );
    }

    /**
     * Get metric aggregates for a specific metric across all devices
     */
    @Transactional(readOnly = true)
    public List<MetricAggregate> getMetricData(
        String metricName,
        Instant startTime,
        Instant endTime
    ) {
        return metricAggregateRepository.findByMetricNameAndTimestampBetween(
            metricName,
            startTime,
            endTime
        );
    }

    /**
     * Get metric aggregates for a specific device and metric
     */
    @Transactional(readOnly = true)
    public List<MetricAggregate> getDeviceMetricData(
        String deviceId,
        String metricName,
        Instant startTime,
        Instant endTime
    ) {
        return metricAggregateRepository.findByDeviceIdAndMetricNameAndTimestampBetween(
            deviceId,
            metricName,
            startTime,
            endTime
        );
    }

    /**
     * Get time series data for a specific device and metric with aggregation type
     */
    @Transactional(readOnly = true)
    public List<MetricAggregate> getTimeSeriesData(
        String deviceId,
        String metricName,
        MetricAggregate.AggregationType aggregationType,
        Instant startTime,
        Instant endTime
    ) {
        return metricAggregateRepository.getTimeSeriesData(
            deviceId,
            metricName,
            aggregationType,
            startTime,
            endTime
        );
    }

    /**
     * Get latest metric value for a device and metric
     */
    @Transactional(readOnly = true)
    public Optional<MetricAggregate> getLatestMetric(
        String deviceId,
        String metricName
    ) {
        return metricAggregateRepository.findLatestByDeviceAndMetric(
            deviceId,
            metricName
        );
    }

    /**
     * Get metric statistics for a specific metric
     */
    @Transactional(readOnly = true)
    public MetricStatistics getMetricStatistics(
        String metricName,
        Instant startTime,
        Instant endTime
    ) {
        Object[] result = metricAggregateRepository.getMetricStatistics(
            metricName,
            startTime,
            endTime
        );

        if (result != null && result.length == 5) {
            return new MetricStatistics(
                (Double) result[0], // avgValue
                (Double) result[1], // minValue
                (Double) result[2], // maxValue
                (Double) result[3], // sumValue
                ((Number) result[4]).longValue() // countValue
            );
        }

        return new MetricStatistics(0.0, 0.0, 0.0, 0.0, 0L);
    }

    /**
     * Get top devices by metric value
     */
    @Transactional(readOnly = true)
    public List<DeviceMetricSummary> getTopDevicesByMetric(
        String metricName,
        Instant startTime,
        Instant endTime,
        int limit
    ) {
        Pageable pageable = Pageable.ofSize(limit);
        List<Object[]> results =
            metricAggregateRepository.getTopDevicesByMetric(
                metricName,
                startTime,
                endTime,
                pageable
            );

        return results
            .stream()
            .map(row ->
                new DeviceMetricSummary(
                    (String) row[0], // deviceId
                    (Double) row[1] // avgValue
                )
            )
            .collect(Collectors.toList());
    }

    /**
     * Find anomalous metric values
     */
    @Transactional(readOnly = true)
    public List<MetricAggregate> findAnomalousValues(
        String metricName,
        Instant startTime,
        Instant endTime,
        Double minThreshold,
        Double maxThreshold
    ) {
        return metricAggregateRepository.findAnomalousValues(
            metricName,
            startTime,
            endTime,
            minThreshold,
            maxThreshold
        );
    }

    /**
     * Get hourly aggregations for a device metric
     */
    @Transactional(readOnly = true)
    public List<HourlyAggregation> getHourlyAggregations(
        String deviceId,
        String metricName,
        Instant startTime,
        Instant endTime
    ) {
        List<Object[]> results =
            metricAggregateRepository.getHourlyAggregations(
                deviceId,
                metricName,
                startTime,
                endTime
            );

        return results
            .stream()
            .map(row ->
                new HourlyAggregation(
                    (Instant) row[0], // hour
                    (Double) row[1], // avgValue
                    (Double) row[2], // minValue
                    (Double) row[3] // maxValue
                )
            )
            .collect(Collectors.toList());
    }

    /**
     * Get daily aggregations for a device metric
     */
    @Transactional(readOnly = true)
    public List<DailyAggregation> getDailyAggregations(
        String deviceId,
        String metricName,
        Instant startTime,
        Instant endTime
    ) {
        List<Object[]> results = metricAggregateRepository.getDailyAggregations(
            deviceId,
            metricName,
            startTime,
            endTime
        );

        return results
            .stream()
            .map(row ->
                new DailyAggregation(
                    (Instant) row[0], // day
                    (Double) row[1], // avgValue
                    (Double) row[2], // minValue
                    (Double) row[3] // maxValue
                )
            )
            .collect(Collectors.toList());
    }

    /**
     * Get all unique metric names for a device
     */
    @Transactional(readOnly = true)
    public List<String> getMetricNamesForDevice(String deviceId) {
        return metricAggregateRepository.findMetricNamesByDevice(deviceId);
    }

    /**
     * Get all device IDs that have a specific metric
     */
    @Transactional(readOnly = true)
    public List<String> getDeviceIdsForMetric(String metricName) {
        return metricAggregateRepository.findDeviceIdsByMetric(metricName);
    }

    /**
     * Get recent metrics for a device (last N hours)
     */
    @Transactional(readOnly = true)
    public List<MetricAggregate> getRecentMetricsForDevice(
        String deviceId,
        int hoursAgo
    ) {
        Instant sinceTime = Instant.now().minus(hoursAgo, ChronoUnit.HOURS);
        return metricAggregateRepository.findRecentByDevice(
            deviceId,
            sinceTime
        );
    }

    /**
     * Get metrics with pagination
     */
    @Transactional(readOnly = true)
    public Page<MetricAggregate> getMetrics(
        Instant startTime,
        Instant endTime,
        Pageable pageable
    ) {
        return metricAggregateRepository.findByTimestampBetween(
            startTime,
            endTime,
            pageable
        );
    }

    /**
     * Create a new metric aggregate
     */
    public MetricAggregate createMetricAggregate(
        MetricAggregate metricAggregate
    ) {
        if (metricAggregate.getCreatedAt() == null) {
            metricAggregate.setCreatedAt(Instant.now());
        }

        log.debug(
            "Creating metric aggregate for device {} metric {}",
            metricAggregate.getDeviceId(),
            metricAggregate.getMetricName()
        );

        return metricAggregateRepository.save(metricAggregate);
    }

    /**
     * Batch create metric aggregates
     */
    public List<MetricAggregate> createMetricAggregates(
        List<MetricAggregate> metricAggregates
    ) {
        Instant now = Instant.now();
        metricAggregates.forEach(aggregate -> {
            if (aggregate.getCreatedAt() == null) {
                aggregate.setCreatedAt(now);
            }
        });

        log.debug(
            "Batch creating {} metric aggregates",
            metricAggregates.size()
        );
        return metricAggregateRepository.saveAll(metricAggregates);
    }

    /**
     * Delete old metric aggregates (for data retention)
     */
    public void deleteOldMetrics(Instant cutoffTime) {
        log.info("Deleting metric aggregates older than {}", cutoffTime);
        metricAggregateRepository.deleteByTimestampBefore(cutoffTime);
    }

    /**
     * Count metrics in time range
     */
    @Transactional(readOnly = true)
    public long countMetrics(Instant startTime, Instant endTime) {
        return metricAggregateRepository.countByTimestampBetween(
            startTime,
            endTime
        );
    }

    /**
     * Count metrics for a device in time range
     */
    @Transactional(readOnly = true)
    public long countMetricsForDevice(
        String deviceId,
        Instant startTime,
        Instant endTime
    ) {
        return metricAggregateRepository.countByDeviceIdAndTimestampBetween(
            deviceId,
            startTime,
            endTime
        );
    }

    /**
     * Get metric trend data (percentage change over time periods)
     */
    @Transactional(readOnly = true)
    public MetricTrend getMetricTrend(
        String deviceId,
        String metricName,
        int hoursAgo
    ) {
        Instant now = Instant.now();
        Instant periodStart = now.minus(hoursAgo, ChronoUnit.HOURS);
        Instant halfPeriod = now.minus(hoursAgo / 2, ChronoUnit.HOURS);

        // Get recent period average
        List<MetricAggregate> recentPeriod = getDeviceMetricData(
            deviceId,
            metricName,
            halfPeriod,
            now
        );

        // Get earlier period average
        List<MetricAggregate> earlierPeriod = getDeviceMetricData(
            deviceId,
            metricName,
            periodStart,
            halfPeriod
        );

        double recentAvg = recentPeriod
            .stream()
            .mapToDouble(MetricAggregate::getValue)
            .average()
            .orElse(0.0);

        double earlierAvg = earlierPeriod
            .stream()
            .mapToDouble(MetricAggregate::getValue)
            .average()
            .orElse(0.0);

        double percentChange = 0.0;
        if (earlierAvg != 0.0) {
            percentChange = ((recentAvg - earlierAvg) / earlierAvg) * 100;
        }

        return new MetricTrend(
            deviceId,
            metricName,
            recentAvg,
            earlierAvg,
            percentChange,
            recentPeriod.size(),
            earlierPeriod.size()
        );
    }

    // Inner classes for DTOs
    public record MetricStatistics(
        Double avgValue,
        Double minValue,
        Double maxValue,
        Double sumValue,
        Long countValue
    ) {}

    public record DeviceMetricSummary(String deviceId, Double avgValue) {}

    public record HourlyAggregation(
        Instant hour,
        Double avgValue,
        Double minValue,
        Double maxValue
    ) {}

    public record DailyAggregation(
        Instant day,
        Double avgValue,
        Double minValue,
        Double maxValue
    ) {}

    public record MetricTrend(
        String deviceId,
        String metricName,
        Double recentAverage,
        Double earlierAverage,
        Double percentChange,
        Integer recentDataPoints,
        Integer earlierDataPoints
    ) {}
}
