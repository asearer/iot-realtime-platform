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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "metric_aggregates",
    indexes = {
        @Index(
            name = "idx_metric_aggregates_device_time",
            columnList = "device_id, timestamp"
        ),
        @Index(
            name = "idx_metric_aggregates_metric_time",
            columnList = "metric_name, timestamp"
        ),
        @Index(
            name = "idx_metric_aggregates_window",
            columnList = "window_start, window_end"
        ),
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class MetricAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Device ID cannot be blank")
    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @NotNull(message = "Timestamp cannot be null")
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @NotNull(message = "Window start cannot be null")
    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @NotNull(message = "Window end cannot be null")
    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @NotBlank(message = "Metric name cannot be blank")
    @Column(name = "metric_name", nullable = false)
    private String metricName;

    @NotNull(message = "Value cannot be null")
    @Column(name = "value", nullable = false)
    private Double value;

    @Column(name = "min_value")
    private Double minValue;

    @Column(name = "max_value")
    private Double maxValue;

    @Column(name = "avg_value")
    private Double avgValue;

    @Column(name = "sum_value")
    private Double sumValue;

    @Column(name = "count_value")
    private Long countValue;

    @Column(name = "std_dev")
    private Double stdDev;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregation_type")
    private AggregationType aggregationType = AggregationType.MINUTE;

    @ElementCollection
    @CollectionTable(
        name = "metric_aggregate_tags",
        joinColumns = @JoinColumn(name = "aggregate_id")
    )
    @MapKeyColumn(name = "tag_key")
    @Column(name = "tag_value")
    private Map<String, String> tags;

    @CreatedDate
    @Column(name = "created_at")
    private Instant createdAt;

    public enum AggregationType {
        MINUTE,
        HOUR,
        DAY,
        WEEK,
        MONTH,
    }

    // Helper method to calculate window duration in milliseconds
    public long getWindowDurationMs() {
        if (windowStart != null && windowEnd != null) {
            return windowEnd.toEpochMilli() - windowStart.toEpochMilli();
        }
        return 0;
    }

    // Helper method to check if this aggregate is within a time range
    public boolean isWithinTimeRange(Instant start, Instant end) {
        return (
            timestamp != null &&
            !timestamp.isBefore(start) &&
            !timestamp.isAfter(end)
        );
    }
}
