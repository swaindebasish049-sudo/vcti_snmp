package com.networking.ems.snmp.web.dto;

import com.networking.ems.snmp.model.SnmpV3Credentials;
import com.networking.ems.snmp.model.SnmpVersion;

/**
 * Request body for registering a device.
 *
 * v1/v2c example:
 * {
 *   "id": "core-router-01", "name": "Core Router 1",
 *   "host": "127.0.0.1", "port": 1161,
 *   "version": "V2C", "community": "public"
 * }
 *
 * v3 example (snmpsim defaults; context selects the recording):
 * {
 *   "id": "core-router-01-v3", "name": "Core Router 1 (v3)",
 *   "host": "127.0.0.1", "port": 1161, "version": "V3",
 *   "v3": {
 *     "username": "simulator",
 *     "authPassword": "auctoritas", "privPassword": "privatus",
 *     "authProtocol": "MD5", "privProtocol": "DES",
 *     "contextName": "real-cisco-c6500"
 *   }
 * }
 */
public record RegisterDeviceRequest(
        String id,
        String name,
        String host,
        Integer port,          // boxed so we can default it when omitted
        SnmpVersion version,
        String community,      // v1/v2c
        SnmpV3Credentials v3,  // v3
        String vendor,
        String location
) {}
