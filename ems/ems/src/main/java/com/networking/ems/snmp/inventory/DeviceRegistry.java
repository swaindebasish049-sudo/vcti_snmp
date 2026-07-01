package com.networking.ems.snmp.inventory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.networking.ems.snmp.exception.DeviceNotFoundException;
import com.networking.ems.snmp.model.ManagedDevice;

/**
 * In-memory device inventory. Thread-safe via ConcurrentHashMap.
 *
 * NOTE: state is lost on restart. When you move to a database later, you can
 * replace this class's internals (or introduce a repository) WITHOUT changing
 * any caller -- that's the benefit of keeping the registry behind a service.
 */
@Service
public class DeviceRegistry {

    private final Map<String, ManagedDevice> devices = new ConcurrentHashMap<>();

    /** Add or replace a device in the inventory. */
    public ManagedDevice register(ManagedDevice device) {
        if (device.id() == null || device.id().isBlank()) {
            throw new IllegalArgumentException("Device id must not be blank");
        }
        devices.put(device.id(), device);
        return device;
    }

    /** Look up a device, or fail with 404-mapped exception. */
    public ManagedDevice get(String id) {
        ManagedDevice device = devices.get(id);
        if (device == null) {
            throw new DeviceNotFoundException(id);
        }
        return device;
    }

    /** All registered devices. */
    public Collection<ManagedDevice> list() {
        return List.copyOf(devices.values());
    }

    /** Remove a device; returns true if it existed. */
    public boolean remove(String id) {
        return devices.remove(id) != null;
    }

    public boolean exists(String id) {
        return devices.containsKey(id);
    }
}
