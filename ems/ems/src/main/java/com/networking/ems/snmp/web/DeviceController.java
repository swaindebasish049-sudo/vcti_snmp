package com.networking.ems.snmp.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.networking.ems.snmp.model.SnmpDevice;
import com.networking.ems.snmp.model.SnmpResult;
import com.networking.ems.snmp.model.SnmpVersion;
import com.networking.ems.snmp.service.SnmpQueryService;

/**
 * Ad-hoc testing endpoints: pass connection details as query params, including
 * {@code version} (V1 or V2C) so you can exercise both protocol versions.
 *
 *   GET  /device/sysname?host=127.0.0.1&version=V2C
 *   GET  /device/get?host=127.0.0.1&oid=1.3.6.1.2.1.1.1.0
 *   GET  /device/getnext?host=127.0.0.1&oid=1.3.6.1.2.1.1
 *   GET  /device/walk?host=127.0.0.1&oid=1.3.6.1.2.1.2.2.1.2&version=V1
 *   GET  /device/get-multi?host=127.0.0.1&oid=...&oid=...
 *   POST /device/set?host=127.0.0.1&community=private&oid=...&value=...
 */
@RestController
@RequestMapping("/device")
public class DeviceController {

    private final SnmpQueryService queryService;

    public DeviceController(SnmpQueryService queryService) {
        this.queryService = queryService;
    }

    private SnmpDevice device(String host, int port, String community, SnmpVersion version) {
        // ad-hoc endpoints support v1/v2c only; use registered devices for v3
        return new SnmpDevice(host, port, version, community, null);
    }

    @GetMapping("/sysname")
    public SnmpResult sysName(
            @RequestParam String host,
            @RequestParam(defaultValue = "161") int port,
            @RequestParam(defaultValue = "public") String community,
            @RequestParam(defaultValue = "V2C") SnmpVersion version) {
        return queryService.getSystemName(device(host, port, community, version));
    }

    @GetMapping("/get")
    public SnmpResult get(
            @RequestParam String host,
            @RequestParam(defaultValue = "161") int port,
            @RequestParam(defaultValue = "public") String community,
            @RequestParam(defaultValue = "V2C") SnmpVersion version,
            @RequestParam String oid) {
        return queryService.get(device(host, port, community, version), oid);
    }

    @GetMapping("/get-multi")
    public List<SnmpResult> getMulti(
            @RequestParam String host,
            @RequestParam(defaultValue = "161") int port,
            @RequestParam(defaultValue = "public") String community,
            @RequestParam(defaultValue = "V2C") SnmpVersion version,
            @RequestParam List<String> oid) {
        return queryService.get(device(host, port, community, version), oid);
    }

    @GetMapping("/getnext")
    public SnmpResult getNext(
            @RequestParam String host,
            @RequestParam(defaultValue = "161") int port,
            @RequestParam(defaultValue = "public") String community,
            @RequestParam(defaultValue = "V2C") SnmpVersion version,
            @RequestParam String oid) {
        return queryService.getNext(device(host, port, community, version), oid);
    }

    @GetMapping("/walk")
    public List<SnmpResult> walk(
            @RequestParam String host,
            @RequestParam(defaultValue = "161") int port,
            @RequestParam(defaultValue = "public") String community,
            @RequestParam(defaultValue = "V2C") SnmpVersion version,
            @RequestParam String oid) {
        return queryService.walk(device(host, port, community, version), oid);
    }

    @PostMapping("/set")
    public SnmpResult set(
            @RequestParam String host,
            @RequestParam(defaultValue = "161") int port,
            @RequestParam(defaultValue = "private") String community,
            @RequestParam(defaultValue = "V2C") SnmpVersion version,
            @RequestParam String oid,
            @RequestParam String value) {
        return queryService.set(device(host, port, community, version), oid, value);
    }
}
