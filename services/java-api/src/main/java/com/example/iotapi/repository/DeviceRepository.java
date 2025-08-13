package com.example.iotapi.repository;

import com.example.iotapi.model.Device;
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
public interface DeviceRepository extends JpaRepository<Device, String> {
    // Find devices by status
    List<Device> findByStatus(Device.DeviceStatus status);

    // Find devices by type
    List<Device> findByDeviceType(String deviceType);

    // Find devices by location (case insensitive)
    List<Device> findByLocationIgnoreCase(String location);

    // Find devices by name containing (case insensitive)
    List<Device> findByNameContainingIgnoreCase(String name);

    // Find devices by manufacturer
    List<Device> findByManufacturer(String manufacturer);

    // Find devices last seen after a certain time
    List<Device> findByLastSeenAfter(Instant timestamp);

    // Find devices last seen before a certain time (potentially offline)
    List<Device> findByLastSeenBefore(Instant timestamp);

    // Find devices that haven't been seen in a time period
    @Query(
        "SELECT d FROM Device d WHERE d.lastSeen < :cutoffTime OR d.lastSeen IS NULL"
    )
    List<Device> findPotentiallyOfflineDevices(
        @Param("cutoffTime") Instant cutoffTime
    );

    // Get device count by status
    long countByStatus(Device.DeviceStatus status);

    // Get device count by type
    long countByDeviceType(String deviceType);

    // Search devices with multiple criteria
    @Query(
        "SELECT d FROM Device d WHERE " +
        "(:deviceType IS NULL OR d.deviceType = :deviceType) AND " +
        "(:status IS NULL OR d.status = :status) AND " +
        "(:location IS NULL OR LOWER(d.location) LIKE LOWER(CONCAT('%', :location, '%'))) AND " +
        "(:manufacturer IS NULL OR LOWER(d.manufacturer) LIKE LOWER(CONCAT('%', :manufacturer, '%')))"
    )
    Page<Device> findDevicesWithFilters(
        @Param("deviceType") String deviceType,
        @Param("status") Device.DeviceStatus status,
        @Param("location") String location,
        @Param("manufacturer") String manufacturer,
        Pageable pageable
    );

    // Get recently active devices (last seen within specified minutes)
    @Query(
        "SELECT d FROM Device d WHERE d.lastSeen >= :sinceTime ORDER BY d.lastSeen DESC"
    )
    List<Device> findRecentlyActiveDevices(
        @Param("sinceTime") Instant sinceTime
    );

    // Get device statistics grouped by status
    @Query("SELECT d.status, COUNT(d) FROM Device d GROUP BY d.status")
    List<Object[]> getDeviceCountByStatus();

    // Get device statistics grouped by type
    @Query("SELECT d.deviceType, COUNT(d) FROM Device d GROUP BY d.deviceType")
    List<Object[]> getDeviceCountByType();

    // Find devices with specific metadata key-value pair
    @Query(
        "SELECT d FROM Device d JOIN d.metadata m WHERE KEY(m) = :key AND VALUE(m) = :value"
    )
    List<Device> findByMetadata(
        @Param("key") String key,
        @Param("value") String value
    );

    // Check if device exists by ID
    boolean existsByDeviceId(String deviceId);

    // Custom method to find device with all related data
    @Query(
        "SELECT d FROM Device d LEFT JOIN FETCH d.metadata WHERE d.deviceId = :deviceId"
    )
    Optional<Device> findByIdWithMetadata(@Param("deviceId") String deviceId);

    // Get devices created within a time range
    List<Device> findByCreatedAtBetween(Instant startTime, Instant endTime);

    // Get recently updated devices
    List<Device> findByUpdatedAtAfterOrderByUpdatedAtDesc(Instant since);
}
