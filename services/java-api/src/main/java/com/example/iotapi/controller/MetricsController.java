package com.example.iotapi.controller;

import com.example.iotapi.model.MetricAggregate;
import com.example.iotapi.service.MetricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Metrics Management",
    description = "APIs for managing and querying IoT device metrics"
)
public class MetricsController {

    private final MetricService metricService;

    @GetMapping("/devices/{deviceId}")
    @Operation(
        summary = "Get metrics for device",
        description = "Retrieve all metric aggregates for a specific device"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved metrics"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Device not found"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<List<MetricAggregate>> getMetricsForDevice(
        @Parameter(description = "Device ID") @PathVariable String deviceId,
        @Parameter(description = "Start time (ISO 8601)") @RequestParam(
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
        @Parameter(description = "End time (ISO 8601)") @RequestParam(
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime
    ) {
        log.debug(
            "Getting metrics for device: {}, timeRange: {} to {}",
            deviceId,
            startTime,
            endTime
        );

        List<MetricAggregate> metrics;

        if (startTime != null && endTime != null) {
            metrics = metricService.getMetricsForDevice(
                deviceId,
                startTime,
                endTime
            );
        } else {
            // Default to last 24 hours if no time range specified
            Instant defaultEndTime = Instant.now();
            Instant defaultStartTime = defaultEndTime.minus(
                24,
                ChronoUnit.HOURS
            );
            metrics = metricService.getMetricsForDevice(
                deviceId,
                defaultStartTime,
                defaultEndTime
            );
        }

        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/devices/{deviceId}/metrics/{metricName}")
    @Operation(
        summary = "Get specific metric for device",
        description = "Retrieve specific metric data for a device within a time range"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved metric data"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Device or metric not found"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<List<MetricAggregate>> getDeviceMetricData(
        @Parameter(description = "Device ID") @PathVariable String deviceId,
        @Parameter(description = "Metric name") @PathVariable String metricName,
        @Parameter(description = "Start time (ISO 8601)") @RequestParam(
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
        @Parameter(description = "End time (ISO 8601)") @RequestParam(
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime
    ) {
        log.debug(
            "Getting metric {} for device: {}, timeRange: {} to {}",
            metricName,
            deviceId,
            startTime,
            endTime
        );

        // Default to last 24 hours if no time range specified
        if (startTime == null || endTime == null) {
            endTime = Instant.now();
            startTime = endTime.minus(24, ChronoUnit.HOURS);
        }

        List<MetricAggregate> metrics = metricService.getDeviceMetricData(
            deviceId,
            metricName,
            startTime,
            endTime
        );

        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/timeseries/{deviceId}/{metricName}")
    @Operation(
        summary = "Get time series data",
        description = "Retrieve time series data for a specific device and metric with aggregation type"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved time series data"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Device or metric not found"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<List<MetricAggregate>> getTimeSeriesData(
        @Parameter(description = "Device ID") @PathVariable String deviceId,
        @Parameter(description = "Metric name") @PathVariable String metricName,
        @Parameter(description = "Aggregation type") @RequestParam(
            defaultValue = "MINUTE"
        ) MetricAggregate.AggregationType aggregationType,
        @Parameter(
            description = "Start time (ISO 8601)"
        ) @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        ) Instant startTime,
        @Parameter(
            description = "End time (ISO 8601)"
        ) @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        ) Instant endTime
    ) {
        log.debug(
            "Getting time series data for device: {}, metric: {}, aggregation: {}, timeRange: {} to {}",
            deviceId,
            metricName,
            aggregationType,
            startTime,
            endTime
        );

        List<MetricAggregate> timeSeries = metricService.getTimeSeriesData(
            deviceId,
            metricName,
            aggregationType,
            startTime,
            endTime
        );

        return ResponseEntity.ok(timeSeries);
    }

    @GetMapping("/latest/{deviceId}/{metricName}")
    @Operation(
        summary = "Get latest metric value",
        description = "Retrieve the latest metric value for a specific device and metric"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved latest metric"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Device, metric, or data not found"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<MetricAggregate> getLatestMetric(
        @Parameter(description = "Device ID") @PathVariable String deviceId,
        @Parameter(description = "Metric name") @PathVariable String metricName
    ) {
        log.debug(
            "Getting latest metric {} for device: {}",
            metricName,
            deviceId
        );

        Optional<MetricAggregate> latestMetric = metricService.getLatestMetric(
            deviceId,
            metricName
        );

        return latestMetric
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{metricName}/statistics")
    @Operation(
        summary = "Get metric statistics",
        description = "Get statistical summary for a metric across all devices"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved statistics"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<MetricService.MetricStatistics> getMetricStatistics(
        @Parameter(description = "Metric name") @PathVariable String metricName,
        @Parameter(
            description = "Start time (ISO 8601)"
        ) @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        ) Instant startTime,
        @Parameter(
            description = "End time (ISO 8601)"
        ) @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        ) Instant endTime
    ) {
        log.debug(
            "Getting statistics for metric: {}, timeRange: {} to {}",
            metricName,
            startTime,
            endTime
        );

        MetricService.MetricStatistics statistics =
            metricService.getMetricStatistics(metricName, startTime, endTime);

        return ResponseEntity.ok(statistics);
    }

    @GetMapping("/{metricName}/top-devices")
    @Operation(
        summary = "Get top devices by metric",
        description = "Get devices with highest average values for a specific metric"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved top devices"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<
        List<MetricService.DeviceMetricSummary>
    > getTopDevicesByMetric(
        @Parameter(description = "Metric name") @PathVariable String metricName,
        @Parameter(
            description = "Start time (ISO 8601)"
        ) @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        ) Instant startTime,
        @Parameter(
            description = "End time (ISO 8601)"
        ) @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        ) Instant endTime,
        @Parameter(
            description = "Number of top devices to return"
        ) @RequestParam(defaultValue = "10") int limit
    ) {
        log.debug(
            "Getting top {} devices for metric: {}, timeRange: {} to {}",
            limit,
            metricName,
            startTime,
            endTime
        );

        List<MetricService.DeviceMetricSummary> topDevices =
            metricService.getTopDevicesByMetric(
                metricName,
                startTime,
                endTime,
                limit
            );

        return ResponseEntity.ok(topDevices);
    }

    @GetMapping("/{metricName}/anomalies")
    @Operation(
        summary = "Find anomalous metric values",
        description = "Find metric values outside specified threshold range"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved anomalous values"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<List<MetricAggregate>> findAnomalousValues(
        @Parameter(description = "Metric name") @PathVariable String metricName,
        @Parameter(
            description = "Start time (ISO 8601)"
        ) @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        ) Instant startTime,
        @Parameter(
            description = "End time (ISO 8601)"
        ) @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        ) Instant endTime,
        @Parameter(
            description = "Minimum threshold"
        ) @RequestParam Double minThreshold,
        @Parameter(
            description = "Maximum threshold"
        ) @RequestParam Double maxThreshold
    ) {
        log.debug(
            "Finding anomalous values for metric: {}, range: [{}, {}], timeRange: {} to {}",
            metricName,
            minThreshold,
            maxThreshold,
            startTime,
            endTime
        );

        List<MetricAggregate> anomalies = metricService.findAnomalousValues(
            metricName,
            startTime,
            endTime,
            minThreshold,
            maxThreshold
        );

        return ResponseEntity.ok(anomalies);
    }

    @GetMapping("/devices/{deviceId}/aggregations/hourly")
    @Operation(
        summary = "Get hourly aggregations",
        description = "Get hourly aggregated data for a device and metric"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved hourly aggregations"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Device or metric not found"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<
        List<MetricService.HourlyAggregation>
    > getHourlyAggregations(
        @Parameter(description = "Device ID") @PathVariable String deviceId,
        @Parameter(description = "Metric name") @RequestParam String metricName,
        @Parameter(
            description = "Start time (ISO 8601)"
        ) @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        ) Instant startTime,
        @Parameter(
            description = "End time (ISO 8601)"
        ) @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        ) Instant endTime
    ) {
        log.debug(
            "Getting hourly aggregations for device: {}, metric: {}, timeRange: {} to {}",
            deviceId,
            metricName,
            startTime,
            endTime
        );

        List<MetricService.HourlyAggregation> hourlyData =
            metricService.getHourlyAggregations(
                deviceId,
                metricName,
                startTime,
                endTime
            );

        return ResponseEntity.ok(hourlyData);
    }

    @GetMapping("/devices/{deviceId}/aggregations/daily")
    @Operation(
        summary = "Get daily aggregations",
        description = "Get daily aggregated data for a device and metric"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved daily aggregations"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Device or metric not found"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<
        List<MetricService.DailyAggregation>
    > getDailyAggregations(
        @Parameter(description = "Device ID") @PathVariable String deviceId,
        @Parameter(description = "Metric name") @RequestParam String metricName,
        @Parameter(
            description = "Start time (ISO 8601)"
        ) @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        ) Instant startTime,
        @Parameter(
            description = "End time (ISO 8601)"
        ) @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        ) Instant endTime
    ) {
        log.debug(
            "Getting daily aggregations for device: {}, metric: {}, timeRange: {} to {}",
            deviceId,
            metricName,
            startTime,
            endTime
        );

        List<MetricService.DailyAggregation> dailyData =
            metricService.getDailyAggregations(
                deviceId,
                metricName,
                startTime,
                endTime
            );

        return ResponseEntity.ok(dailyData);
    }

    @GetMapping("/devices/{deviceId}/metric-names")
    @Operation(
        summary = "Get metric names for device",
        description = "Get all unique metric names available for a device"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved metric names"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Device not found"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<List<String>> getMetricNamesForDevice(
        @Parameter(description = "Device ID") @PathVariable String deviceId
    ) {
        log.debug("Getting metric names for device: {}", deviceId);

        List<String> metricNames = metricService.getMetricNamesForDevice(
            deviceId
        );

        return ResponseEntity.ok(metricNames);
    }

    @GetMapping("/{metricName}/device-ids")
    @Operation(
        summary = "Get device IDs for metric",
        description = "Get all device IDs that have data for a specific metric"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved device IDs"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<List<String>> getDeviceIdsForMetric(
        @Parameter(description = "Metric name") @PathVariable String metricName
    ) {
        log.debug("Getting device IDs for metric: {}", metricName);

        List<String> deviceIds = metricService.getDeviceIdsForMetric(
            metricName
        );

        return ResponseEntity.ok(deviceIds);
    }

    @GetMapping("/devices/{deviceId}/recent")
    @Operation(
        summary = "Get recent metrics",
        description = "Get recent metric data for a device (last N hours)"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved recent metrics"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Device not found"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<List<MetricAggregate>> getRecentMetricsForDevice(
        @Parameter(description = "Device ID") @PathVariable String deviceId,
        @Parameter(description = "Hours ago threshold") @RequestParam(
            defaultValue = "24"
        ) int hoursAgo
    ) {
        log.debug(
            "Getting recent metrics for device: {}, hoursAgo: {}",
            deviceId,
            hoursAgo
        );

        List<MetricAggregate> recentMetrics =
            metricService.getRecentMetricsForDevice(deviceId, hoursAgo);

        return ResponseEntity.ok(recentMetrics);
    }

    @PostMapping
    @Operation(
        summary = "Create metric aggregate",
        description = "Create a new metric aggregate entry"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "201",
                description = "Metric aggregate created successfully"
            ),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid metric data"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")
    public ResponseEntity<MetricAggregate> createMetricAggregate(
        @Valid @RequestBody MetricAggregate metricAggregate
    ) {
        log.info(
            "Creating metric aggregate for device {} metric {}",
            metricAggregate.getDeviceId(),
            metricAggregate.getMetricName()
        );

        MetricAggregate createdMetric = metricService.createMetricAggregate(
            metricAggregate
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(createdMetric);
    }

    @PostMapping("/batch")
    @Operation(
        summary = "Batch create metric aggregates",
        description = "Create multiple metric aggregate entries in batch"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "201",
                description = "Metric aggregates created successfully"
            ),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid metric data"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")
    public ResponseEntity<List<MetricAggregate>> createMetricAggregates(
        @Valid @RequestBody List<MetricAggregate> metricAggregates
    ) {
        log.info(
            "Batch creating {} metric aggregates",
            metricAggregates.size()
        );

        List<MetricAggregate> createdMetrics =
            metricService.createMetricAggregates(metricAggregates);

        return ResponseEntity.status(HttpStatus.CREATED).body(createdMetrics);
    }

    @GetMapping
    @Operation(
        summary = "Get metrics with pagination",
        description = "Get metric aggregates with pagination and time filtering"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved metrics"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<Page<MetricAggregate>> getMetrics(
        @Parameter(
            description = "Start time (ISO 8601)"
        ) @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        ) Instant startTime,
        @Parameter(
            description = "End time (ISO 8601)"
        ) @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        ) Instant endTime,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug(
            "Getting metrics with pagination, timeRange: {} to {}, page: {}",
            startTime,
            endTime,
            pageable
        );

        Page<MetricAggregate> metrics = metricService.getMetrics(
            startTime,
            endTime,
            pageable
        );

        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/devices/{deviceId}/trends/{metricName}")
    @Operation(
        summary = "Get metric trend",
        description = "Get trend analysis for a specific device and metric"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved metric trend"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Device or metric not found"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<MetricService.MetricTrend> getMetricTrend(
        @Parameter(description = "Device ID") @PathVariable String deviceId,
        @Parameter(description = "Metric name") @PathVariable String metricName,
        @Parameter(description = "Hours to look back") @RequestParam(
            defaultValue = "24"
        ) int hoursAgo
    ) {
        log.debug(
            "Getting trend for device: {}, metric: {}, hoursAgo: {}",
            deviceId,
            metricName,
            hoursAgo
        );

        MetricService.MetricTrend trend = metricService.getMetricTrend(
            deviceId,
            metricName,
            hoursAgo
        );

        return ResponseEntity.ok(trend);
    }

    @DeleteMapping("/cleanup")
    @Operation(
        summary = "Delete old metrics",
        description = "Delete metric aggregates older than specified time"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Old metrics deleted successfully"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteOldMetrics(
        @Parameter(
            description = "Cutoff time (ISO 8601)"
        ) @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        ) Instant cutoffTime
    ) {
        log.info("Deleting metrics older than: {}", cutoffTime);

        metricService.deleteOldMetrics(cutoffTime);

        return ResponseEntity.ok("Old metrics deleted successfully");
    }

    @GetMapping("/count")
    @Operation(
        summary = "Count metrics",
        description = "Count metric aggregates in specified time range"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved metric count"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<Map<String, Long>> countMetrics(
        @Parameter(
            description = "Start time (ISO 8601)"
        ) @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        ) Instant startTime,
        @Parameter(
            description = "End time (ISO 8601)"
        ) @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        ) Instant endTime,
        @Parameter(description = "Device ID (optional)") @RequestParam(
            required = false
        ) String deviceId
    ) {
        log.debug(
            "Counting metrics, timeRange: {} to {}, deviceId: {}",
            startTime,
            endTime,
            deviceId
        );

        long count;
        if (deviceId != null) {
            count = metricService.countMetricsForDevice(
                deviceId,
                startTime,
                endTime
            );
        } else {
            count = metricService.countMetrics(startTime, endTime);
        }

        return ResponseEntity.ok(Map.of("count", count));
    }
}
