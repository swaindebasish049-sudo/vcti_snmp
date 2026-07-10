package com.networking.ems.snmp.model;

import java.util.List;

/**
 * A single inbound SNMP notification (TRAP or INFORM) received on the trap port.
 *
 *  - receivedAt:    epoch millis when we received it.
 *  - source:        the sending agent's address (host:port).
 *  - type:          "TRAP" (fire-and-forget) or "INFORM" (acknowledged).
 *  - version:       "v1" / "v2c" / "v3" -- which SNMP version delivered it.
 *  - securityLevel: v3 only -- noAuthNoPriv / authNoPriv / authPriv (null for v1/v2c).
 *  - user:          v3 only -- the USM security name (null for v1/v2c).
 *  - bindings:      the variable bindings carried by the notification.
 */
public record TrapEvent(
        long receivedAt,
        String source,
        String type,
        String version,
        String securityLevel,
        String user,
        List<SnmpResult> bindings
) {}
