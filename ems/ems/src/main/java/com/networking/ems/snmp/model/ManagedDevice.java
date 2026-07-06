package com.networking.ems.snmp.model;

/**
 * A device REGISTERED in the EMS inventory, keyed by {@code id}.
 *
 * Carries connection details + credentials + light metadata. Credentials
 * (community / v3 passwords) are sensitive -- never expose them in API
 * responses (see DeviceResponse, which omits them).
 */
public record ManagedDevice(
        String id,           // friendly unique id, e.g. "core-router-01"
        String name,         // display name
        String host,         // management IP or hostname
        int port,            // usually 161
        SnmpVersion version, // V1 / V2C / V3
        String community,    // v1/v2c credential (sensitive!)
        SnmpV3Credentials v3,// v3 credentials (sensitive!)
        String vendor,       // optional metadata
        String location      // optional metadata
) {
    /** Convert the inventory record into the connection-only object the SNMP layer needs. */
    public SnmpDevice toSnmpDevice() {
        return new SnmpDevice(host, port, version, community, v3);
    }
}
