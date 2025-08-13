package com.example.iotapi.controller;

import com.example.iotapi.model.Alert;
import com.example.iotapi.service.AlertService;
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
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Alert Management",
    description = "APIs for managing IoT platform alerts and notifications"
)
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    @Operation(
        summary = "Get alerts with filters",
        description = "Retrieve alerts with optional filtering and pagination"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved alerts"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<Page<Alert>> getAlerts(
        @Parameter(description = "Filter by device ID") @RequestParam(
            required = false
        ) String deviceId,
        @Parameter(description = "Filter by alert type") @RequestParam(
            required = false
        ) Alert.AlertType alertType,
        @Parameter(description = "Filter by severity") @RequestParam(
            required = false
        ) Alert.Severity severity,
        @Parameter(description = "Filter by status") @RequestParam(
            required = false
        ) Alert.Status status,
        @Parameter(description = "Filter by metric name") @RequestParam(
            required = false
        ) String metricName,
        @Parameter(description = "Start time (ISO 8601)") @RequestParam(
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
        @Parameter(description = "End time (ISO 8601)") @RequestParam(
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug(
            "Getting alerts with filters - deviceId: {}, type: {}, severity: {}, status: {}, metric: {}",
            deviceId,
            alertType,
            severity,
            status,
            metricName
        );

        // Default to last 24 hours if no time range specified
        if (startTime == null || endTime == null) {
            endTime = Instant.now();
            startTime = endTime.minus(24, ChronoUnit.HOURS);
        }

        Page<Alert> alerts = alertService.getAlertsWithFilters(
            deviceId,
            alertType,
            severity,
            status,
            metricName,
            startTime,
            endTime,
            pageable
        );

        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/{alertId}")
    @Operation(
        summary = "Get alert by ID",
        description = "Retrieve a specific alert by its ID"
    )
    @ApiResponses(
        value = {
            @ApiResponse(responseCode = "200", description = "Alert found"),
            @ApiResponse(responseCode = "404", description = "Alert not found"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<Alert> getAlertById(
        @Parameter(description = "Alert ID") @PathVariable Long alertId
    ) {
        log.debug("Getting alert by ID: {}", alertId);

        Optional<Alert> alert = alertService.getAlertById(alertId);
        return alert
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create alert", description = "Create a new alert")
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "201",
                description = "Alert created successfully"
            ),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid alert data"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Alert> createAlert(@Valid @RequestBody Alert alert) {
        log.info(
            "Creating alert for device {} with severity {}",
            alert.getDeviceId(),
            alert.getSeverity()
        );

        Alert createdAlert = alertService.createAlert(alert);
        if (createdAlert == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(createdAlert);
    }

    @PostMapping("/batch")
    @Operation(
        summary = "Batch create alerts",
        description = "Create multiple alerts in batch"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "201",
                description = "Alerts created successfully"
            ),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid alert data"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")
    public ResponseEntity<List<Alert>> createAlerts(
        @Valid @RequestBody List<Alert> alerts
    ) {
        log.info("Batch creating {} alerts", alerts.size());

        List<Alert> createdAlerts = alertService.createAlerts(alerts);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAlerts);
    }

    @PatchMapping("/{alertId}/acknowledge")
    @Operation(
        summary = "Acknowledge alert",
        description = "Acknowledge an alert"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Alert acknowledged successfully"
            ),
            @ApiResponse(responseCode = "404", description = "Alert not found"),
            @ApiResponse(
                responseCode = "400",
                description = "Alert cannot be acknowledged"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Alert> acknowledgeAlert(
        @Parameter(description = "Alert ID") @PathVariable Long alertId,
        @RequestBody AcknowledgeRequest request
    ) {
        log.info(
            "Acknowledging alert {} by {}",
            alertId,
            request.acknowledgedBy()
        );

        try {
            Alert acknowledgedAlert = alertService.acknowledgeAlert(
                alertId,
                request.acknowledgedBy(),
                request.note()
            );
            return ResponseEntity.ok(acknowledgedAlert);
        } catch (IllegalStateException e) {
            log.warn(
                "Cannot acknowledge alert {}: {}",
                alertId,
                e.getMessage()
            );
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.warn("Alert not found for acknowledgment: {}", alertId);
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{alertId}/resolve")
    @Operation(summary = "Resolve alert", description = "Resolve an alert")
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Alert resolved successfully"
            ),
            @ApiResponse(responseCode = "404", description = "Alert not found"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Alert> resolveAlert(
        @Parameter(description = "Alert ID") @PathVariable Long alertId,
        @RequestBody ResolveRequest request
    ) {
        log.info("Resolving alert {} by {}", alertId, request.resolvedBy());

        try {
            Alert resolvedAlert = alertService.resolveAlert(
                alertId,
                request.resolvedBy(),
                request.note()
            );
            return ResponseEntity.ok(resolvedAlert);
        } catch (Exception e) {
            log.warn("Alert not found for resolution: {}", alertId);
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{alertId}/close")
    @Operation(summary = "Close alert", description = "Close an alert")
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Alert closed successfully"
            ),
            @ApiResponse(responseCode = "404", description = "Alert not found"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Alert> closeAlert(
        @Parameter(description = "Alert ID") @PathVariable Long alertId,
        @RequestBody CloseRequest request
    ) {
        log.info("Closing alert {} by {}", alertId, request.closedBy());

        try {
            Alert closedAlert = alertService.closeAlert(
                alertId,
                request.closedBy(),
                request.note()
            );
            return ResponseEntity.ok(closedAlert);
        } catch (Exception e) {
            log.warn("Alert not found for closing: {}", alertId);
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{alertId}/suppress")
    @Operation(summary = "Suppress alert", description = "Suppress an alert")
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Alert suppressed successfully"
            ),
            @ApiResponse(responseCode = "404", description = "Alert not found"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Alert> suppressAlert(
        @Parameter(description = "Alert ID") @PathVariable Long alertId,
        @RequestBody SuppressRequest request
    ) {
        log.info("Suppressing alert {} by {}", alertId, request.suppressedBy());

        try {
            Alert suppressedAlert = alertService.suppressAlert(
                alertId,
                request.suppressedBy(),
                request.reason()
            );
            return ResponseEntity.ok(suppressedAlert);
        } catch (Exception e) {
            log.warn("Alert not found for suppression: {}", alertId);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/devices/{deviceId}")
    @Operation(
        summary = "Get alerts for device",
        description = "Retrieve alerts for a specific device"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved device alerts"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<Page<Alert>> getAlertsForDevice(
        @Parameter(description = "Device ID") @PathVariable String deviceId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        log.debug("Getting alerts for device: {}", deviceId);

        Page<Alert> alerts = alertService.getAlertsForDevice(
            deviceId,
            pageable
        );
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/open")
    @Operation(
        summary = "Get open alerts",
        description = "Retrieve all open alerts ordered by severity and timestamp"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved open alerts"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<List<Alert>> getOpenAlerts() {
        log.debug("Getting all open alerts");

        List<Alert> openAlerts = alertService.getOpenAlerts();
        return ResponseEntity.ok(openAlerts);
    }

    @GetMapping("/unacknowledged")
    @Operation(
        summary = "Get unacknowledged alerts",
        description = "Retrieve all unacknowledged alerts"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved unacknowledged alerts"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<List<Alert>> getUnacknowledgedAlerts() {
        log.debug("Getting all unacknowledged alerts");

        List<Alert> unacknowledgedAlerts =
            alertService.getUnacknowledgedAlerts();
        return ResponseEntity.ok(unacknowledgedAlerts);
    }

    @GetMapping("/critical")
    @Operation(
        summary = "Get critical alerts",
        description = "Retrieve all open critical alerts"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved critical alerts"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<List<Alert>> getCriticalAlerts() {
        log.debug("Getting all critical alerts");

        List<Alert> criticalAlerts = alertService.getCriticalAlerts();
        return ResponseEntity.ok(criticalAlerts);
    }

    @GetMapping("/stale")
    @Operation(
        summary = "Get stale open alerts",
        description = "Get alerts that have been open for more than specified minutes"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved stale alerts"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<List<Alert>> getStaleOpenAlerts(
        @Parameter(description = "Minutes threshold") @RequestParam(
            defaultValue = "60"
        ) int minutesAgo
    ) {
        log.debug("Getting stale alerts older than {} minutes", minutesAgo);

        List<Alert> staleAlerts = alertService.getStaleOpenAlerts(minutesAgo);
        return ResponseEntity.ok(staleAlerts);
    }

    @GetMapping("/recent")
    @Operation(
        summary = "Get recent alerts",
        description = "Get alerts from the last N hours"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved recent alerts"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<List<Alert>> getRecentAlerts(
        @Parameter(description = "Hours ago threshold") @RequestParam(
            defaultValue = "24"
        ) int hoursAgo
    ) {
        log.debug("Getting alerts from last {} hours", hoursAgo);

        List<Alert> recentAlerts = alertService.getRecentAlerts(hoursAgo);
        return ResponseEntity.ok(recentAlerts);
    }

    @GetMapping("/anomalies")
    @Operation(
        summary = "Get high Z-score alerts",
        description = "Get alerts with high Z-scores indicating anomalies"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved anomaly alerts"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<List<Alert>> getHighZScoreAlerts(
        @Parameter(description = "Minimum Z-score threshold") @RequestParam(
            defaultValue = "3.0"
        ) Double minZScore,
        @Parameter(description = "Start time (ISO 8601)") @RequestParam(
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
        @Parameter(description = "End time (ISO 8601)") @RequestParam(
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime
    ) {
        // Default to last 24 hours if no time range specified
        if (startTime == null || endTime == null) {
            endTime = Instant.now();
            startTime = endTime.minus(24, ChronoUnit.HOURS);
        }

        log.debug(
            "Getting high Z-score alerts with minZScore: {}, timeRange: {} to {}",
            minZScore,
            startTime,
            endTime
        );

        List<Alert> anomalyAlerts = alertService.getHighZScoreAlerts(
            minZScore,
            startTime,
            endTime
        );
        return ResponseEntity.ok(anomalyAlerts);
    }

    @GetMapping("/statistics")
    @Operation(
        summary = "Get alert statistics",
        description = "Get comprehensive alert statistics and counts"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved alert statistics"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<AlertService.AlertSummary> getAlertStatistics(
        @Parameter(description = "Start time (ISO 8601)") @RequestParam(
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
        @Parameter(description = "End time (ISO 8601)") @RequestParam(
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime
    ) {
        // Default to last 24 hours if no time range specified
        if (startTime == null || endTime == null) {
            endTime = Instant.now();
            startTime = endTime.minus(24, ChronoUnit.HOURS);
        }

        log.debug(
            "Getting alert statistics for timeRange: {} to {}",
            startTime,
            endTime
        );

        AlertService.AlertSummary summary = alertService.getAlertSummary(
            startTime,
            endTime
        );
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/counts/severity")
    @Operation(
        summary = "Get alert counts by severity",
        description = "Get count of open alerts grouped by severity"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved severity counts"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<
        Map<Alert.Severity, Long>
    > getAlertCountsBySeverity() {
        log.debug("Getting alert counts by severity");

        Map<Alert.Severity, Long> severityCounts =
            alertService.getOpenAlertCountsBySeverity();
        return ResponseEntity.ok(severityCounts);
    }

    @GetMapping("/counts/devices")
    @Operation(
        summary = "Get alert counts by device",
        description = "Get count of alerts grouped by device"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved device alert counts"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<Map<String, Long>> getAlertCountsByDevice(
        @Parameter(description = "Start time (ISO 8601)") @RequestParam(
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
        @Parameter(description = "End time (ISO 8601)") @RequestParam(
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime
    ) {
        // Default to last 24 hours if no time range specified
        if (startTime == null || endTime == null) {
            endTime = Instant.now();
            startTime = endTime.minus(24, ChronoUnit.HOURS);
        }

        log.debug(
            "Getting alert counts by device for timeRange: {} to {}",
            startTime,
            endTime
        );

        Map<String, Long> deviceCounts = alertService.getAlertCountsByDevice(
            startTime,
            endTime
        );
        return ResponseEntity.ok(deviceCounts);
    }

    @GetMapping("/trends")
    @Operation(
        summary = "Get alert trends",
        description = "Get hourly alert trends over time"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved alert trends"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<List<AlertService.AlertTrend>> getHourlyAlertTrends(
        @Parameter(description = "Start time (ISO 8601)") @RequestParam(
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
        @Parameter(description = "End time (ISO 8601)") @RequestParam(
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime
    ) {
        // Default to last 24 hours if no time range specified
        if (startTime == null || endTime == null) {
            endTime = Instant.now();
            startTime = endTime.minus(24, ChronoUnit.HOURS);
        }

        log.debug(
            "Getting hourly alert trends for timeRange: {} to {}",
            startTime,
            endTime
        );

        List<AlertService.AlertTrend> trends =
            alertService.getHourlyAlertTrends(startTime, endTime);
        return ResponseEntity.ok(trends);
    }

    @GetMapping("/devices/top-alerting")
    @Operation(
        summary = "Get devices with most alerts",
        description = "Get devices that have generated the most alerts"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved top alerting devices"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<
        List<AlertService.DeviceAlertSummary>
    > getDevicesWithMostAlerts(
        @Parameter(description = "Start time (ISO 8601)") @RequestParam(
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
        @Parameter(description = "End time (ISO 8601)") @RequestParam(
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
        @Parameter(description = "Number of top devices") @RequestParam(
            defaultValue = "10"
        ) int limit
    ) {
        // Default to last 24 hours if no time range specified
        if (startTime == null || endTime == null) {
            endTime = Instant.now();
            startTime = endTime.minus(24, ChronoUnit.HOURS);
        }

        log.debug(
            "Getting top {} alerting devices for timeRange: {} to {}",
            limit,
            startTime,
            endTime
        );

        List<AlertService.DeviceAlertSummary> topDevices =
            alertService.getDevicesWithMostAlerts(startTime, endTime, limit);
        return ResponseEntity.ok(topDevices);
    }

    @PostMapping("/bulk/acknowledge")
    @Operation(
        summary = "Bulk acknowledge alerts",
        description = "Acknowledge multiple alerts in batch"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Alerts acknowledged successfully"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")
    public ResponseEntity<List<Alert>> bulkAcknowledgeAlerts(
        @RequestBody BulkActionRequest request
    ) {
        log.info(
            "Bulk acknowledging {} alerts by {}",
            request.alertIds().size(),
            request.actionBy()
        );

        List<Alert> acknowledgedAlerts = alertService.bulkAcknowledgeAlerts(
            request.alertIds(),
            request.actionBy(),
            request.note()
        );

        return ResponseEntity.ok(acknowledgedAlerts);
    }

    @PostMapping("/bulk/resolve")
    @Operation(
        summary = "Bulk resolve alerts",
        description = "Resolve multiple alerts in batch"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Alerts resolved successfully"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")
    public ResponseEntity<List<Alert>> bulkResolveAlerts(
        @RequestBody BulkActionRequest request
    ) {
        log.info(
            "Bulk resolving {} alerts by {}",
            request.alertIds().size(),
            request.actionBy()
        );

        List<Alert> resolvedAlerts = alertService.bulkResolveAlerts(
            request.alertIds(),
            request.actionBy(),
            request.note()
        );

        return ResponseEntity.ok(resolvedAlerts);
    }

    @GetMapping("/resolution-time")
    @Operation(
        summary = "Get average resolution time",
        description = "Get average time to resolve alerts"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved resolution time"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<Map<String, Double>> getAverageResolutionTime(
        @Parameter(description = "Start time (ISO 8601)") @RequestParam(
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
        @Parameter(description = "End time (ISO 8601)") @RequestParam(
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime
    ) {
        // Default to last 24 hours if no time range specified
        if (startTime == null || endTime == null) {
            endTime = Instant.now();
            startTime = endTime.minus(24, ChronoUnit.HOURS);
        }

        log.debug(
            "Getting average resolution time for timeRange: {} to {}",
            startTime,
            endTime
        );

        Double avgResolutionTime =
            alertService.getAverageResolutionTimeInMinutes(startTime, endTime);
        return ResponseEntity.ok(
            Map.of(
                "averageResolutionTimeMinutes",
                avgResolutionTime != null ? avgResolutionTime : 0.0
            )
        );
    }

    @DeleteMapping("/cleanup")
    @Operation(
        summary = "Delete old resolved alerts",
        description = "Delete resolved alerts older than specified days"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Old alerts deleted successfully"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteOldResolvedAlerts(
        @Parameter(description = "Days threshold") @RequestParam(
            defaultValue = "90"
        ) int daysAgo
    ) {
        log.info("Deleting resolved alerts older than {} days", daysAgo);

        alertService.deleteOldResolvedAlerts(daysAgo);
        return ResponseEntity.ok("Old resolved alerts deleted successfully");
    }

    @PostMapping("/anomaly")
    @Operation(
        summary = "Create anomaly alert",
        description = "Create an anomaly alert from telemetry data"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "201",
                description = "Anomaly alert created successfully"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Alert> createAnomalyAlert(
        @RequestBody AnomalyAlertRequest request
    ) {
        log.info(
            "Creating anomaly alert for device {} metric {}",
            request.deviceId(),
            request.metricName()
        );

        Alert anomalyAlert = alertService.createAnomalyAlert(
            request.deviceId(),
            request.metricName(),
            request.value(),
            request.threshold(),
            request.description()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(anomalyAlert);
    }

    // Inner class for anomaly alert request
    public record AnomalyAlertRequest(
        String deviceId,
        String metricName,
        double value,
        double threshold,
        String description
    ) {}
}
