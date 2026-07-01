package com.networking.ems.snmp.web.dto;

import com.networking.ems.snmp.model.SnmpVersion;

/**
 * Request body for registering a device.
 *
 * Example JSON:
 * {
 *   "id": "core-router-01",
 *   "name": "Core Router 1",
 *   "host": "127.0.0.1",
 *   "port": 161,
 *   "version": "V2C",
 *   "community": "public",
 *   "vendor": "Cisco",
 *   "location": "Lab"
 * }
 */
public record RegisterDeviceRequest(
        String id,
        String name,
        String host,
        Integer port,        // boxed so we can default it when omitted
        SnmpVersion version,
        String community,
        String vendor,
        String location
) {}
