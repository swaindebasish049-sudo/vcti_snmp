package com.networking.ems.netconf.web;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.networking.ems.netconf.client.NetconfClient;
import com.networking.ems.netconf.model.NetconfDevice;

/**
 * NETCONF operations by device id (mirrors DeviceRegistrationController's shape).
 *
 *  Get config : GET  /netconf/{id}/get-config?datastore=running
 *  Get (state): GET  /netconf/{id}/get
 *  Edit+commit: POST /netconf/{id}/edit-config?commit=true   (body = <config> fragment)
 *
 * For this first slice a single netopeer2 lab device is hardcoded; a proper
 * inventory (like the SNMP DeviceRegistry) comes next.
 */
@RestController
@RequestMapping("/netconf")
public class NetconfController {

    private final NetconfClient client;

    // Hardcoded lab device for now (netopeer2 in Docker).
    private final NetconfDevice netopeer2 =
            new NetconfDevice("netopeer2", "netopeer2 (lab)", "127.0.0.1", 830, "netconf", "netconf");

    public NetconfController(NetconfClient client) {
        this.client = client;
    }

    private NetconfDevice lookup(String id) {
        if (!"netopeer2".equals(id)) {
            throw new IllegalArgumentException("Unknown NETCONF device: " + id);
        }
        return netopeer2;
    }

    @GetMapping(value = "/{id}/get-config", produces = MediaType.APPLICATION_XML_VALUE)
    public String getConfig(@PathVariable String id,
                            @RequestParam(defaultValue = "running") String datastore) {
        return client.getConfig(lookup(id), datastore);
    }

    @GetMapping(value = "/{id}/get", produces = MediaType.APPLICATION_XML_VALUE)
    public String get(@PathVariable String id) {
        return client.get(lookup(id));
    }

    @PostMapping(value = "/{id}/edit-config", produces = MediaType.APPLICATION_XML_VALUE)
    public String editConfig(@PathVariable String id,
                             @RequestParam(defaultValue = "true") boolean commit,
                             @RequestBody String configXml) {
        return client.editConfig(lookup(id), configXml, commit);
    }

    /** Best-practice flow: lock -> edit-config -> validate -> commit-or-discard -> unlock. */
    @PostMapping(value = "/{id}/edit-config-safe", produces = MediaType.TEXT_PLAIN_VALUE)
    public String editConfigSafe(@PathVariable String id, @RequestBody String configXml) {
        return client.editConfigTransactional(lookup(id), configXml);
    }

    @PostMapping(value = "/{id}/lock", produces = MediaType.APPLICATION_XML_VALUE)
    public String lock(@PathVariable String id, @RequestParam(defaultValue = "candidate") String datastore) {
        return client.lock(lookup(id), datastore);
    }

    @PostMapping(value = "/{id}/unlock", produces = MediaType.APPLICATION_XML_VALUE)
    public String unlock(@PathVariable String id, @RequestParam(defaultValue = "candidate") String datastore) {
        return client.unlock(lookup(id), datastore);
    }

    @PostMapping(value = "/{id}/validate", produces = MediaType.APPLICATION_XML_VALUE)
    public String validate(@PathVariable String id, @RequestParam(defaultValue = "candidate") String datastore) {
        return client.validate(lookup(id), datastore);
    }

    @PostMapping(value = "/{id}/commit", produces = MediaType.APPLICATION_XML_VALUE)
    public String commit(@PathVariable String id) {
        return client.commit(lookup(id));
    }

    @PostMapping(value = "/{id}/discard-changes", produces = MediaType.APPLICATION_XML_VALUE)
    public String discardChanges(@PathVariable String id) {
        return client.discardChanges(lookup(id));
    }

    @PostMapping(value = "/{id}/copy-config", produces = MediaType.APPLICATION_XML_VALUE)
    public String copyConfig(@PathVariable String id,
                             @RequestParam String source, @RequestParam String target) {
        return client.copyConfig(lookup(id), source, target);
    }

    @PostMapping(value = "/{id}/delete-config", produces = MediaType.APPLICATION_XML_VALUE)
    public String deleteConfig(@PathVariable String id, @RequestParam String target) {
        return client.deleteConfig(lookup(id), target);
    }

    @GetMapping("/devices")
    public Map<String, Object> devices() {
        return Map.of("devices", new Object[]{
                Map.of("id", netopeer2.id(), "name", netopeer2.name(),
                        "host", netopeer2.host(), "port", netopeer2.port())});
    }
}
