package com.networking.ems.snmp.web.dto;

import com.networking.ems.snmp.model.ManagedDevice;
import com.networking.ems.snmp.model.SnmpVersion;

/**
 * Safe view of a registered device for API responses.
 *
 * IMPORTANT: this intentionally does NOT include the community string / any
 * credential. We expose only a boolean flag indicating one is configured.
 */
public record DeviceResponse(
        String id,
        String name,
        String host,
        int port,
        SnmpVersion version,
        String vendor,
        String location,
        boolean credentialConfigured
) {
    public static DeviceResponse from(ManagedDevice d) {
        boolean hasCred = (d.community() != null && !d.community().isBlank())
                || (d.v3() != null && d.v3().username() != null);
        return new DeviceResponse(
                d.id(), d.name(), d.host(), d.port(), d.version(),
                d.vendor(), d.location(), hasCred);
    }
}
