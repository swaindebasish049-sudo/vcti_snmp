package com.networking.ems.snmp.model;

/**
 * A single SNMP result: one OID mapped to its returned value.
 */
public record SnmpResult(
        String oid,
        String value
) {}
