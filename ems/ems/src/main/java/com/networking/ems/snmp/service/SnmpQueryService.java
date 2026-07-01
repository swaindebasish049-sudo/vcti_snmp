package com.networking.ems.snmp.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.networking.ems.snmp.client.SnmpClient;
import com.networking.ems.snmp.model.SnmpDevice;
import com.networking.ems.snmp.model.SnmpResult;
import com.networking.ems.snmp.util.Oids;

/**
 * Business layer. Controllers call this; it orchestrates SNMP calls via the
 * SnmpClient abstraction. Works for v1 and v2c (version comes from the device).
 */
@Service
public class SnmpQueryService {

    private final SnmpClient snmpClient;

    public SnmpQueryService(SnmpClient snmpClient) {
        this.snmpClient = snmpClient;
    }

    /** Convenience: fetch the device's system name (hostname). */
    public SnmpResult getSystemName(SnmpDevice device) {
        return snmpClient.get(device, Oids.SYS_NAME);
    }

    public SnmpResult get(SnmpDevice device, String oid) {
        return snmpClient.get(device, oid);
    }

    public List<SnmpResult> get(SnmpDevice device, List<String> oids) {
        return snmpClient.get(device, oids);
    }

    public SnmpResult getNext(SnmpDevice device, String oid) {
        return snmpClient.getNext(device, oid);
    }

    public List<SnmpResult> walk(SnmpDevice device, String baseOid) {
        return snmpClient.walk(device, baseOid);
    }

    public SnmpResult set(SnmpDevice device, String oid, String value) {
        return snmpClient.set(device, oid, value);
    }
}
