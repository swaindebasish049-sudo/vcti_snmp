package com.networking.ems.snmp.exception;

/**
 * Thrown when a caller references a device id that isn't registered.
 */
public class DeviceNotFoundException extends RuntimeException {

    public DeviceNotFoundException(String id) {
        super("No device registered with id '" + id + "'");
    }
}
