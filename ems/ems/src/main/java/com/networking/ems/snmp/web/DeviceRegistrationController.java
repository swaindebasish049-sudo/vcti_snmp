package com.networking.ems.snmp.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.networking.ems.snmp.inventory.DeviceRegistry;
import com.networking.ems.snmp.model.ManagedDevice;
import com.networking.ems.snmp.model.SnmpResult;
import com.networking.ems.snmp.model.SnmpVersion;
import com.networking.ems.snmp.service.SnmpQueryService;
import com.networking.ems.snmp.web.dto.DeviceResponse;
import com.networking.ems.snmp.web.dto.RegisterDeviceRequest;

/**
 * Device inventory + polling BY registered id.
 *
 *  Register : POST   /devices
 *  List     : GET    /devices
 *  Get one  : GET    /devices/{id}
 *  Delete   : DELETE /devices/{id}
 *  Poll     : GET    /devices/{id}/sysname
 *             GET    /devices/{id}/get?oid=...
 *
 * After registering once, you poll by id and never pass host/community again.
 */
@RestController
@RequestMapping("/devices")
public class DeviceRegistrationController {

    private final DeviceRegistry registry;
    private final SnmpQueryService queryService;

    public DeviceRegistrationController(DeviceRegistry registry, SnmpQueryService queryService) {
        this.registry = registry;
        this.queryService = queryService;
    }

    // ---- Inventory management -------------------------------------------------

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceResponse register(@RequestBody RegisterDeviceRequest req) {
        int port = (req.port() == null) ? 161 : req.port();
        SnmpVersion version = (req.version() == null) ? SnmpVersion.V2C : req.version();

        ManagedDevice device = new ManagedDevice(
                req.id(), req.name(), req.host(), port, version,
                req.community(), req.v3(), req.vendor(), req.location());

        registry.register(device);
        return DeviceResponse.from(device);
    }

    @GetMapping
    public List<DeviceResponse> list() {
        return registry.list().stream().map(DeviceResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DeviceResponse get(@PathVariable String id) {
        return DeviceResponse.from(registry.get(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        registry.remove(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Polling by registered id --------------------------------------------

    @GetMapping("/{id}/sysname")
    public SnmpResult sysName(@PathVariable String id) {
        ManagedDevice device = registry.get(id);
        return queryService.getSystemName(device.toSnmpDevice());
    }

    @GetMapping("/{id}/get")
    public SnmpResult getOid(@PathVariable String id, @RequestParam String oid) {
        ManagedDevice device = registry.get(id);
        return queryService.get(device.toSnmpDevice(), oid);
    }

    @GetMapping("/{id}/get-multi")
    public List<SnmpResult> getMulti(@PathVariable String id, @RequestParam List<String> oid) {
        ManagedDevice device = registry.get(id);
        return queryService.get(device.toSnmpDevice(), oid);
    }

    @GetMapping("/{id}/getnext")
    public SnmpResult getNext(@PathVariable String id, @RequestParam String oid) {
        ManagedDevice device = registry.get(id);
        return queryService.getNext(device.toSnmpDevice(), oid);
    }

    @GetMapping("/{id}/walk")
    public List<SnmpResult> walk(@PathVariable String id, @RequestParam String oid) {
        ManagedDevice device = registry.get(id);
        // version (V1 -> GETNEXT loop, V2C -> GETBULK) is decided by the registered device
        return queryService.walk(device.toSnmpDevice(), oid);
    }

    @PostMapping("/{id}/set")
    public SnmpResult set(@PathVariable String id,
                          @RequestParam String oid,
                          @RequestParam String value) {
        ManagedDevice device = registry.get(id);
        return queryService.set(device.toSnmpDevice(), oid, value);
    }
}
