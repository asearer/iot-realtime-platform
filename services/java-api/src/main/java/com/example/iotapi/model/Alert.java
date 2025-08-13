package com.example.iotapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "alerts",
    indexes = {
        @Index(
            name = "idx_alerts_device_time",
            columnList = "device_id, timestamp"
        ),
        @Index(
            name = "idx_alerts_status_severity",
            columnList = "status, severity"
        ),
        @Index(
            name = "idx_alerts_metric_type",
            columnList = "metric_name, alert_type"
        ),
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Device ID cannot be blank")
    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @NotNull(message = "Timestamp cannot be null")
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @NotBlank(message = "Metric name cannot be blank")
    @Column(name = "metric_name", nullable = false)
    private String metricName;

    @NotNull(message = "Alert type cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false)
    private AlertType alertType;

    @NotNull(message = "Severity cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity;

    @NotNull(message = "Status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.OPEN;

    @NotBlank(message = "Title cannot be blank")
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @NotNull(message = "Value cannot be null")
    @Column(name = "value", nullable = false)
    private Double value;

    @Column(name = "expected_min")
    private Double expectedMin;

    @Column(name = "expected_max")
    private Double expectedMax;

    @Column(name = "z_score")
    private Double zScore;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    @ElementCollection
    @CollectionTable(
        name = "alert_metadata",
        joinColumns = @JoinColumn(name = "alert_id")
    )
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;

    @Column(name = "acknowledged_by")
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_note")
    private String acknowledgedNote;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_note")
    private String resolvedNote;

    @CreatedDate
    @Column(name = "created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum AlertType {
        ANOMALY,
        THRESHOLD_HIGH,
        THRESHOLD_LOW,
        DEVICE_OFFLINE,
        DATA_QUALITY,
        SYSTEM_ERROR,
    }

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL,
    }

    public enum Status {
        OPEN,
        ACKNOWLEDGED,
        RESOLVED,
        CLOSED,
        SUPPRESSED,
    }

    // Helper methods
    public boolean isOpen() {
        return status == Status.OPEN;
    }

    public boolean isAcknowledged() {
        return status == Status.ACKNOWLEDGED;
    }

    public boolean isResolved() {
        return status == Status.RESOLVED || status == Status.CLOSED;
    }

    public void acknowledge(String acknowledgedBy, String note) {
        this.status = Status.ACKNOWLEDGED;
        this.acknowledgedBy = acknowledgedBy;
        this.acknowledgedAt = Instant.now();
        this.acknowledgedNote = note;
    }

    public void resolve(String resolvedBy, String note) {
        this.status = Status.RESOLVED;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = Instant.now();
        this.resolvedNote = note;
    }

    public long getAgeInMinutes() {
        return (Instant.now().toEpochMilli() - createdAt.toEpochMilli()) / (1000 * 60);
    }
}
