package com.example.iotapi.controller;

import com.example.iotapi.model.Device;
import com.example.iotapi.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Device Management", description = "APIs for managing IoT devices")
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    @Operation(
        summary = "Get all devices",
        description = "Retrieve all devices with pagination and filtering"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved devices"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<Page<Device>> getAllDevices(
        @PageableDefault(size = 20) Pageable pageable,
        @Parameter(description = "Filter by device type") @RequestParam(
            required = false
        ) String deviceType,
        @Parameter(description = "Filter by device status") @RequestParam(
            required = false
        ) Device.DeviceStatus status,
        @Parameter(description = "Filter by location") @RequestParam(
            required = false
        ) String location,
        @Parameter(description = "Filter by manufacturer") @RequestParam(
            required = false
        ) String manufacturer
    ) {
        log.debug(
            "Getting devices with filters - type: {}, status: {}, location: {}, manufacturer: {}",
            deviceType,
            status,
            location,
            manufacturer
        );

        Page<Device> devices = deviceService.getDevicesWithFilters(
            deviceType,
            status,
            location,
            manufacturer,
            pageable
        );

        return ResponseEntity.ok(devices);
    }

    @GetMapping("/{deviceId}")
    @Operation(
        summary = "Get device by ID",
        description = "Retrieve a specific device by its ID"
    )
    @ApiResponses(
        value = {
            @ApiResponse(responseCode = "200", description = "Device found"),
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
    public ResponseEntity<Device> getDeviceById(
        @Parameter(description = "Device ID") @PathVariable String deviceId,
        @Parameter(description = "Include metadata") @RequestParam(
            defaultValue = "false"
        ) boolean includeMetadata
    ) {
        log.debug(
            "Getting device by ID: {}, includeMetadata: {}",
            deviceId,
            includeMetadata
        );

        try {
            Device device = includeMetadata
                ? deviceService
                    .getDeviceByIdWithMetadata(deviceId)
                    .orElseThrow(() ->
                        new EntityNotFoundException(
                            "Device not found: " + deviceId
                        )
                    )
                : deviceService.getDeviceByIdOrThrow(deviceId);

            return ResponseEntity.ok(device);
        } catch (EntityNotFoundException e) {
            log.warn("Device not found: {}", deviceId);
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @Operation(
        summary = "Create device",
        description = "Create a new IoT device"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "201",
                description = "Device created successfully"
            ),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid device data"
            ),
            @ApiResponse(
                responseCode = "409",
                description = "Device already exists"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Device> createDevice(
        @Valid @RequestBody Device device
    ) {
        log.info("Creating new device: {}", device.getDeviceId());

        try {
            Device createdDevice = deviceService.createDevice(device);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                createdDevice
            );
        } catch (IllegalArgumentException e) {
            log.warn("Failed to create device: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PutMapping("/{deviceId}")
    @Operation(
        summary = "Update device",
        description = "Update an existing device"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Device updated successfully"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Device not found"
            ),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid device data"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Device> updateDevice(
        @Parameter(description = "Device ID") @PathVariable String deviceId,
        @Valid @RequestBody Device device
    ) {
        log.info("Updating device: {}", deviceId);

        try {
            Device updatedDevice = deviceService.updateDevice(deviceId, device);
            return ResponseEntity.ok(updatedDevice);
        } catch (EntityNotFoundException e) {
            log.warn("Device not found for update: {}", deviceId);
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{deviceId}/status")
    @Operation(
        summary = "Update device status",
        description = "Update the status of a device"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Status updated successfully"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Device not found"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Device> updateDeviceStatus(
        @Parameter(description = "Device ID") @PathVariable String deviceId,
        @Parameter(description = "New status") @RequestBody Map<
            String,
            String
        > statusUpdate
    ) {
        log.info("Updating status for device: {}", deviceId);

        try {
            Device.DeviceStatus status = Device.DeviceStatus.valueOf(
                statusUpdate.get("status")
            );
            Device updatedDevice = deviceService.updateDeviceStatus(
                deviceId,
                status
            );
            return ResponseEntity.ok(updatedDevice);
        } catch (EntityNotFoundException e) {
            log.warn("Device not found for status update: {}", deviceId);
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid status value: {}", statusUpdate.get("status"));
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/{deviceId}/metadata")
    @Operation(
        summary = "Update device metadata",
        description = "Update metadata for a device"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Metadata updated successfully"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Device not found"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Device> updateDeviceMetadata(
        @Parameter(description = "Device ID") @PathVariable String deviceId,
        @RequestBody Map<String, String> metadata
    ) {
        log.info("Updating metadata for device: {}", deviceId);

        try {
            Device updatedDevice = deviceService.updateDeviceMetadata(
                deviceId,
                metadata
            );
            return ResponseEntity.ok(updatedDevice);
        } catch (EntityNotFoundException e) {
            log.warn("Device not found for metadata update: {}", deviceId);
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{deviceId}/last-seen")
    @Operation(
        summary = "Mark device as seen",
        description = "Update the last seen timestamp for a device"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Last seen updated successfully"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Device not found"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Device> markDeviceAsSeen(
        @Parameter(description = "Device ID") @PathVariable String deviceId
    ) {
        log.debug("Marking device as seen: {}", deviceId);

        try {
            Device updatedDevice = deviceService.markDeviceAsSeen(deviceId);
            return ResponseEntity.ok(updatedDevice);
        } catch (EntityNotFoundException e) {
            log.warn("Device not found for last seen update: {}", deviceId);
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{deviceId}")
    @Operation(summary = "Delete device", description = "Delete a device")
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "204",
                description = "Device deleted successfully"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Device not found"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDevice(
        @Parameter(description = "Device ID") @PathVariable String deviceId
    ) {
        log.info("Deleting device: {}", deviceId);

        try {
            deviceService.deleteDevice(deviceId);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            log.warn("Device not found for deletion: {}", deviceId);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/search")
    @Operation(
        summary = "Search devices",
        description = "Search devices by name"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Search completed successfully"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<List<Device>> searchDevices(
        @Parameter(
            description = "Search term for device name"
        ) @RequestParam String name
    ) {
        log.debug("Searching devices by name: {}", name);

        List<Device> devices = deviceService.searchDevicesByName(name);
        return ResponseEntity.ok(devices);
    }

    @GetMapping("/by-status/{status}")
    @Operation(
        summary = "Get devices by status",
        description = "Retrieve devices filtered by status"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Devices retrieved successfully"
            ),
            @ApiResponse(responseCode = "400", description = "Invalid status"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<List<Device>> getDevicesByStatus(
        @Parameter(
            description = "Device status"
        ) @PathVariable Device.DeviceStatus status
    ) {
        log.debug("Getting devices by status: {}", status);

        List<Device> devices = deviceService.getDevicesByStatus(status);
        return ResponseEntity.ok(devices);
    }

    @GetMapping("/by-type/{deviceType}")
    @Operation(
        summary = "Get devices by type",
        description = "Retrieve devices filtered by device type"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Devices retrieved successfully"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<List<Device>> getDevicesByType(
        @Parameter(description = "Device type") @PathVariable String deviceType
    ) {
        log.debug("Getting devices by type: {}", deviceType);

        List<Device> devices = deviceService.getDevicesByType(deviceType);
        return ResponseEntity.ok(devices);
    }

    @GetMapping("/recently-active")
    @Operation(
        summary = "Get recently active devices",
        description = "Get devices that were active within specified minutes"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Recently active devices retrieved successfully"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<List<Device>> getRecentlyActiveDevices(
        @Parameter(description = "Minutes ago threshold") @RequestParam(
            defaultValue = "60"
        ) int minutesAgo
    ) {
        log.debug("Getting devices active within {} minutes", minutesAgo);

        List<Device> devices = deviceService.getRecentlyActiveDevices(
            minutesAgo
        );
        return ResponseEntity.ok(devices);
    }

    @GetMapping("/potentially-offline")
    @Operation(
        summary = "Get potentially offline devices",
        description = "Get devices that haven't been seen for specified minutes"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Potentially offline devices retrieved successfully"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<List<Device>> getPotentiallyOfflineDevices(
        @Parameter(description = "Minutes ago threshold") @RequestParam(
            defaultValue = "120"
        ) int minutesAgo
    ) {
        log.debug("Getting devices not seen for {} minutes", minutesAgo);

        List<Device> devices = deviceService.getPotentiallyOfflineDevices(
            minutesAgo
        );
        return ResponseEntity.ok(devices);
    }

    @GetMapping("/statistics")
    @Operation(
        summary = "Get device statistics",
        description = "Get device count statistics"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Statistics retrieved successfully"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<DeviceStatistics> getDeviceStatistics() {
        log.debug("Getting device statistics");

        Map<Device.DeviceStatus, Long> statusCounts =
            deviceService.getDeviceCountByStatus();
        Map<String, Long> typeCounts = deviceService.getDeviceCountByType();
        long totalCount = deviceService.getTotalDeviceCount();
        long activeCount = deviceService.getActiveDeviceCount();

        DeviceStatistics stats = new DeviceStatistics(
            totalCount,
            activeCount,
            statusCounts,
            typeCounts
        );

        return ResponseEntity.ok(stats);
    }

    @PostMapping("/bulk/status")
    @Operation(
        summary = "Bulk update device status",
        description = "Update status for multiple devices"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Bulk status update completed"
            ),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid request data"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")
    public ResponseEntity<String> bulkUpdateDeviceStatus(
        @RequestBody BulkStatusUpdateRequest request
    ) {
        log.info(
            "Bulk updating status for {} devices to {}",
            request.deviceIds().size(),
            request.status()
        );

        try {
            deviceService.bulkUpdateDeviceStatus(
                request.deviceIds(),
                request.status()
            );
            return ResponseEntity.ok("Bulk status update completed");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                "Invalid status: " + request.status()
            );
        }
    }

    @PostMapping("/bulk/mark-seen")
    @Operation(
        summary = "Bulk mark devices as seen",
        description = "Mark multiple devices as recently seen"
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Bulk mark as seen completed"
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")
    public ResponseEntity<String> bulkMarkDevicesAsSeen(
        @RequestBody List<String> deviceIds
    ) {
        log.info("Bulk marking {} devices as seen", deviceIds.size());

        deviceService.bulkMarkDevicesAsSeen(deviceIds);
        return ResponseEntity.ok("Bulk mark as seen completed");
    }

    @GetMapping("/{deviceId}/exists")
    @Operation(
        summary = "Check if device exists",
        description = "Check if a device exists by ID"
    )
    @ApiResponses(
        value = {
            @ApiResponse(responseCode = "200", description = "Check completed"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
        }
    )
    @PreAuthorize(
        "hasRole('VIEWER') or hasRole('OPERATOR') or hasRole('ADMIN')"
    )
    public ResponseEntity<Map<String, Boolean>> checkDeviceExists(
        @Parameter(description = "Device ID") @PathVariable String deviceId
    ) {
        boolean exists = deviceService.deviceExists(deviceId);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    // DTOs for request/response
    public record DeviceStatistics(
        Long totalDevices,
        Long activeDevices,
        Map<Device.DeviceStatus, Long> statusBreakdown,
        Map<String, Long> typeBreakdown
    ) {}

    public record BulkStatusUpdateRequest(
        List<String> deviceIds,
        Device.DeviceStatus status
    ) {}
}
