package com.networking.ems.snmp.model;

/**
 * Connection details for one device's SNMP agent.
 *
 * v1/v2c use {@code community}; v3 uses {@code v3} credentials instead.
 * Exactly one of the two should be populated, matching {@code version}.
 */
public record SnmpDevice(
        String host,
        int port,
        SnmpVersion version,
        String community,       // v1/v2c only
        SnmpV3Credentials v3    // v3 only
) {
    /** Convenience factory for v1. */
    public static SnmpDevice v1(String host, int port, String community) {
        return new SnmpDevice(host, port, SnmpVersion.V1, community, null);
    }

    /** Convenience factory for the common v2c case. */
    public static SnmpDevice v2c(String host, int port, String community) {
        return new SnmpDevice(host, port, SnmpVersion.V2C, community, null);
    }

    /** Convenience factory for v3. */
    public static SnmpDevice v3(String host, int port, SnmpV3Credentials creds) {
        return new SnmpDevice(host, port, SnmpVersion.V3, null, creds);
    }
}
