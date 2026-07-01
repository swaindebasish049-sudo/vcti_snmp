package com.networking.ems.snmp.client;

import java.util.List;

import com.networking.ems.snmp.model.SnmpDevice;
import com.networking.ems.snmp.model.SnmpResult;

/**
 * Abstraction over the SNMP library. The rest of the app depends on THIS,
 * never on SNMP4J directly -- so the library can be swapped or mocked in tests.
 *
 * All operations work for SNMP v1 and v2c; the version is taken from
 * {@link SnmpDevice#version()}. (v3 is not implemented yet.)
 */
public interface SnmpClient {

    /** GET a single OID. */
    SnmpResult get(SnmpDevice device, String oid);

    /** GET several OIDs in one request. */
    List<SnmpResult> get(SnmpDevice device, List<String> oids);

    /** GETNEXT: the next OID lexicographically after the given one. */
    SnmpResult getNext(SnmpDevice device, String oid);

    /**
     * WALK an entire subtree under {@code baseOid}.
     * Uses GETBULK for v2c, and a GETNEXT loop for v1 (which has no GETBULK).
     */
    List<SnmpResult> walk(SnmpDevice device, String baseOid);

    /** SET a value (sent as an OctetString). Requires a read-write community. */
    SnmpResult set(SnmpDevice device, String oid, String value);
}
