package com.networking.ems.snmp.model;

/**
 * Connection details for one device's SNMP agent.
 *
 * For now this carries v2c settings (community). When you add v3 later,
 * extend this with username / auth / priv credentials.
 */
public record SnmpDevice(
        String host,
        int port,
        SnmpVersion version,
        String community
) {
    /** Convenience factory for the common v2c case. */
    public static SnmpDevice v2c(String host, int port, String community) {
        return new SnmpDevice(host, port, SnmpVersion.V2C, community);
    }
}
