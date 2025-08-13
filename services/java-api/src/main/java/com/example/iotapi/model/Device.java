package com.example.iotapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "devices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Device {

    @Id
    @Column(name = "device_id")
    @NotBlank(message = "Device ID cannot be blank")
    private String deviceId;

    @NotBlank(message = "Device name cannot be blank")
    private String name;

    private String description;

    @NotBlank(message = "Device type cannot be blank")
    private String deviceType;

    private String location;

    private String manufacturer;

    private String model;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Enumerated(EnumType.STRING)
    private DeviceStatus status = DeviceStatus.ACTIVE;

    @ElementCollection
    @CollectionTable(
        name = "device_metadata",
        joinColumns = @JoinColumn(name = "device_id")
    )
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @CreatedDate
    @Column(name = "created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum DeviceStatus {
        ACTIVE,
        INACTIVE,
        MAINTENANCE,
        DECOMMISSIONED,
    }
}
