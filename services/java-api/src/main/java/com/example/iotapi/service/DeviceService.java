package com.example.iotapi.service;

import com.example.iotapi.model.Device;
import com.example.iotapi.repository.DeviceRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
public class DeviceService {

    private final DeviceRepository deviceRepository;

    /**
     * Get all devices with pagination
     */
    @Transactional(readOnly = true)
    public Page<Device> getAllDevices(Pageable pageable) {
        return deviceRepository.findAll(pageable);
    }

    /**
     * Get all devices as a list
     */
    @Transactional(readOnly = true)
    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }

    /**
     * Get device by ID
     */
    @Transactional(readOnly = true)
    public Optional<Device> getDeviceById(String deviceId) {
        return deviceRepository.findById(deviceId);
    }

    /**
     * Get device by ID with metadata
     */
    @Transactional(readOnly = true)
    public Optional<Device> getDeviceByIdWithMetadata(String deviceId) {
        return deviceRepository.findByIdWithMetadata(deviceId);
    }

    /**
     * Get device by ID or throw exception
     */
    @Transactional(readOnly = true)
    public Device getDeviceByIdOrThrow(String deviceId) {
        return deviceRepository
            .findById(deviceId)
            .orElseThrow(() ->
                new EntityNotFoundException(
                    "Device not found with ID: " + deviceId
                )
            );
    }

    /**
     * Create a new device
     */
    public Device createDevice(Device device) {
        // Validate device ID is unique
        if (deviceRepository.existsByDeviceId(device.getDeviceId())) {
            throw new IllegalArgumentException(
                "Device with ID " + device.getDeviceId() + " already exists"
            );
        }

        // Set creation timestamp if not set
        if (device.getCreatedAt() == null) {
            device.setCreatedAt(Instant.now());
        }

        log.info("Creating new device: {}", device.getDeviceId());
        return deviceRepository.save(device);
    }

    /**
     * Update an existing device
     */
    public Device updateDevice(String deviceId, Device updatedDevice) {
        Device existingDevice = getDeviceByIdOrThrow(deviceId);

        // Update fields
        if (updatedDevice.getName() != null) {
            existingDevice.setName(updatedDevice.getName());
        }
        if (updatedDevice.getDescription() != null) {
            existingDevice.setDescription(updatedDevice.getDescription());
        }
        if (updatedDevice.getDeviceType() != null) {
            existingDevice.setDeviceType(updatedDevice.getDeviceType());
        }
        if (updatedDevice.getLocation() != null) {
            existingDevice.setLocation(updatedDevice.getLocation());
        }
        if (updatedDevice.getManufacturer() != null) {
            existingDevice.setManufacturer(updatedDevice.getManufacturer());
        }
        if (updatedDevice.getModel() != null) {
            existingDevice.setModel(updatedDevice.getModel());
        }
        if (updatedDevice.getFirmwareVersion() != null) {
            existingDevice.setFirmwareVersion(
                updatedDevice.getFirmwareVersion()
            );
        }
        if (updatedDevice.getStatus() != null) {
            existingDevice.setStatus(updatedDevice.getStatus());
        }
        if (updatedDevice.getMetadata() != null) {
            existingDevice.setMetadata(updatedDevice.getMetadata());
        }

        log.info("Updating device: {}", deviceId);
        return deviceRepository.save(existingDevice);
    }

    /**
     * Update device metadata
     */
    public Device updateDeviceMetadata(
        String deviceId,
        Map<String, String> metadata
    ) {
        Device device = getDeviceByIdOrThrow(deviceId);
        device.setMetadata(metadata);

        log.info("Updating metadata for device: {}", deviceId);
        return deviceRepository.save(device);
    }

    /**
     * Add or update a single metadata entry
     */
    public Device addDeviceMetadata(String deviceId, String key, String value) {
        Device device = getDeviceByIdOrThrow(deviceId);

        if (device.getMetadata() == null) {
            device.setMetadata(Map.of(key, value));
        } else {
            device.getMetadata().put(key, value);
        }

        log.info("Adding metadata {}={} for device: {}", key, value, deviceId);
        return deviceRepository.save(device);
    }

    /**
     * Remove a metadata entry
     */
    public Device removeDeviceMetadata(String deviceId, String key) {
        Device device = getDeviceByIdOrThrow(deviceId);

        if (device.getMetadata() != null) {
            device.getMetadata().remove(key);
        }

        log.info("Removing metadata key {} for device: {}", key, deviceId);
        return deviceRepository.save(device);
    }

    /**
     * Update device status
     */
    public Device updateDeviceStatus(
        String deviceId,
        Device.DeviceStatus status
    ) {
        Device device = getDeviceByIdOrThrow(deviceId);
        Device.DeviceStatus oldStatus = device.getStatus();
        device.setStatus(status);

        log.info(
            "Updating device {} status from {} to {}",
            deviceId,
            oldStatus,
            status
        );
        return deviceRepository.save(device);
    }

    /**
     * Update device last seen timestamp
     */
    public Device updateDeviceLastSeen(String deviceId, Instant lastSeen) {
        Device device = getDeviceByIdOrThrow(deviceId);
        device.setLastSeen(lastSeen);

        return deviceRepository.save(device);
    }

    /**
     * Mark device as seen now
     */
    public Device markDeviceAsSeen(String deviceId) {
        return updateDeviceLastSeen(deviceId, Instant.now());
    }

    /**
     * Delete a device
     */
    public void deleteDevice(String deviceId) {
        if (!deviceRepository.existsByDeviceId(deviceId)) {
            throw new EntityNotFoundException(
                "Device not found with ID: " + deviceId
            );
        }

        log.info("Deleting device: {}", deviceId);
        deviceRepository.deleteById(deviceId);
    }

    /**
     * Get devices by status
     */
    @Transactional(readOnly = true)
    public List<Device> getDevicesByStatus(Device.DeviceStatus status) {
        return deviceRepository.findByStatus(status);
    }

    /**
     * Get devices by type
     */
    @Transactional(readOnly = true)
    public List<Device> getDevicesByType(String deviceType) {
        return deviceRepository.findByDeviceType(deviceType);
    }

    /**
     * Get devices by location
     */
    @Transactional(readOnly = true)
    public List<Device> getDevicesByLocation(String location) {
        return deviceRepository.findByLocationIgnoreCase(location);
    }

    /**
     * Search devices by name
     */
    @Transactional(readOnly = true)
    public List<Device> searchDevicesByName(String name) {
        return deviceRepository.findByNameContainingIgnoreCase(name);
    }

    /**
     * Get devices with filters and pagination
     */
    @Transactional(readOnly = true)
    public Page<Device> getDevicesWithFilters(
        String deviceType,
        Device.DeviceStatus status,
        String location,
        String manufacturer,
        Pageable pageable
    ) {
        return deviceRepository.findDevicesWithFilters(
            deviceType,
            status,
            location,
            manufacturer,
            pageable
        );
    }

    /**
     * Get recently active devices (last N minutes)
     */
    @Transactional(readOnly = true)
    public List<Device> getRecentlyActiveDevices(int minutesAgo) {
        Instant sinceTime = Instant.now().minus(minutesAgo, ChronoUnit.MINUTES);
        return deviceRepository.findRecentlyActiveDevices(sinceTime);
    }

    /**
     * Get potentially offline devices (not seen for N minutes)
     */
    @Transactional(readOnly = true)
    public List<Device> getPotentiallyOfflineDevices(int minutesAgo) {
        Instant cutoffTime = Instant.now().minus(
            minutesAgo,
            ChronoUnit.MINUTES
        );
        return deviceRepository.findPotentiallyOfflineDevices(cutoffTime);
    }

    /**
     * Get devices by metadata
     */
    @Transactional(readOnly = true)
    public List<Device> getDevicesByMetadata(String key, String value) {
        return deviceRepository.findByMetadata(key, value);
    }

    /**
     * Get device statistics by status
     */
    @Transactional(readOnly = true)
    public Map<Device.DeviceStatus, Long> getDeviceCountByStatus() {
        List<Object[]> results = deviceRepository.getDeviceCountByStatus();
        return results
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    row -> (Device.DeviceStatus) row[0],
                    row -> (Long) row[1]
                )
            );
    }

    /**
     * Get device statistics by type
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getDeviceCountByType() {
        List<Object[]> results = deviceRepository.getDeviceCountByType();
        return results
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    row -> (String) row[0],
                    row -> (Long) row[1]
                )
            );
    }

    /**
     * Get total device count
     */
    @Transactional(readOnly = true)
    public long getTotalDeviceCount() {
        return deviceRepository.count();
    }

    /**
     * Get active device count
     */
    @Transactional(readOnly = true)
    public long getActiveDeviceCount() {
        return deviceRepository.countByStatus(Device.DeviceStatus.ACTIVE);
    }

    /**
     * Check if device exists
     */
    @Transactional(readOnly = true)
    public boolean deviceExists(String deviceId) {
        return deviceRepository.existsByDeviceId(deviceId);
    }

    /**
     * Get recently created devices
     */
    @Transactional(readOnly = true)
    public List<Device> getRecentlyCreatedDevices(int hoursAgo) {
        Instant since = Instant.now().minus(hoursAgo, ChronoUnit.HOURS);
        return deviceRepository.findByCreatedAtBetween(since, Instant.now());
    }

    /**
     * Get recently updated devices
     */
    @Transactional(readOnly = true)
    public List<Device> getRecentlyUpdatedDevices(int hoursAgo) {
        Instant since = Instant.now().minus(hoursAgo, ChronoUnit.HOURS);
        return deviceRepository.findByUpdatedAtAfterOrderByUpdatedAtDesc(since);
    }

    /**
     * Bulk update device status
     */
    public void bulkUpdateDeviceStatus(
        List<String> deviceIds,
        Device.DeviceStatus status
    ) {
        deviceIds.forEach(deviceId -> {
            try {
                updateDeviceStatus(deviceId, status);
            } catch (EntityNotFoundException e) {
                log.warn(
                    "Device not found during bulk status update: {}",
                    deviceId
                );
            }
        });

        log.info(
            "Bulk updated status to {} for {} devices",
            status,
            deviceIds.size()
        );
    }

    /**
     * Mark multiple devices as seen
     */
    public void bulkMarkDevicesAsSeen(List<String> deviceIds) {
        Instant now = Instant.now();
        deviceIds.forEach(deviceId -> {
            try {
                updateDeviceLastSeen(deviceId, now);
            } catch (EntityNotFoundException e) {
                log.warn(
                    "Device not found during bulk last seen update: {}",
                    deviceId
                );
            }
        });

        log.info("Bulk updated last seen for {} devices", deviceIds.size());
    }
}
