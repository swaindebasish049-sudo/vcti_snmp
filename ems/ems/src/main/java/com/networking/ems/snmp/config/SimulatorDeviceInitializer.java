package com.networking.ems.snmp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.networking.ems.snmp.inventory.DeviceRegistry;
import com.networking.ems.snmp.model.ManagedDevice;
import com.networking.ems.snmp.model.SnmpV3Credentials;
import com.networking.ems.snmp.model.SnmpVersion;

/**
 * Pre-registers the snmpsim lab devices at startup so the inventory (and the
 * UI) is populated out of the box.
 *
 * snmpsim serves one recording per credential:
 *  - v1/v2c: the COMMUNITY string selects the recording file
 *  - v3:     the CONTEXT NAME selects it; the USM user is snmpsim's default
 *            (simulator / auctoritas (MD5) / privatus (DES))
 *
 * The "version practice" block registers the SAME recording via v1, v2c and
 * v3 so the three protocol versions can be compared against identical data.
 */
@Component
public class SimulatorDeviceInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulatorDeviceInitializer.class);

    private static final String SNMPSIM_HOST = "127.0.0.1";
    private static final int SNMPSIM_PORT = 1161;

    /** snmpsim's built-in default v3 user. */
    private static final String V3_USER = "simulator";
    private static final String V3_AUTH_PASS = "auctoritas";  // MD5
    private static final String V3_PRIV_PASS = "privatus";    // DES

    private final DeviceRegistry registry;

    public SimulatorDeviceInitializer(DeviceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run(ApplicationArguments args) {
        // -- hand-authored recordings (synthetic but Cisco-realistic), v2c --
        register("cisco-2621",  "Cisco 2621 Edge Router",     "cisco-2621-router",  "Cisco", "EMS Lab - Rack 1");
        register("cisco-3640",  "Cisco 3640 Core Router",     "cisco-3640-router",  "Cisco", "EMS Lab - Rack 1");
        register("cisco-7204",  "Cisco 7204 Backbone Router", "cisco-7204-router",  "Cisco", "EMS Lab - Rack 2");
        register("cisco-c6506", "Cisco Catalyst 6506 Switch", "cisco-c6506-switch", "Cisco", "EMS Lab - DC Row A");

        // -- REAL device captures (snmpsim sample + LibreNMS test data), v2c --
        register("cisco-3750",     "Cisco Catalyst 3750 (real capture)",     "cisco_16_switch",      "Cisco",   "recorded device");
        register("cisco-c6500",    "Cisco Catalyst 6500 (real capture)",     "real-cisco-c6500",     "Cisco",   "recorded device");
        register("cisco-2960x",    "Cisco Catalyst 2960X (real capture)",    "real-cisco-2960x",     "Cisco",   "recorded device");
        register("cisco-asr1000",  "Cisco ASR1000 IOS-XE (real capture)",    "real-cisco-c9400",     "Cisco",   "recorded device");
        register("cisco-nexus3064","Cisco Nexus 3064PQ NX-OS (real capture)","real-cisco-nexus3064", "Cisco",   "recorded device");
        register("juniper-mx80",   "Juniper MX80 Router (real capture)",     "real-juniper-mx",      "Juniper", "recorded device");
        register("arista-7280r",   "Arista 7280SR Switch (real capture)",    "real-arista-7280r",    "Arista",  "recorded device");
        register("huawei-s5720",   "Huawei S5720 Switch (real capture)",     "real-huawei-s5720",    "Huawei",  "recorded device");

        // -- version practice: SAME device (real Catalyst 6500) via all 3 versions --
        if (!registry.exists("c6500-v1")) {
            registry.register(new ManagedDevice(
                    "c6500-v1", "Catalyst 6500 via SNMPv1", SNMPSIM_HOST, SNMPSIM_PORT,
                    SnmpVersion.V1, "real-cisco-c6500", null, "Cisco", "version practice"));
        }
        if (!registry.exists("c6500-v2c")) {
            registry.register(new ManagedDevice(
                    "c6500-v2c", "Catalyst 6500 via SNMPv2c", SNMPSIM_HOST, SNMPSIM_PORT,
                    SnmpVersion.V2C, "real-cisco-c6500", null, "Cisco", "version practice"));
        }
        if (!registry.exists("c6500-v3")) {
            registry.register(new ManagedDevice(
                    "c6500-v3", "Catalyst 6500 via SNMPv3 (authPriv)", SNMPSIM_HOST, SNMPSIM_PORT,
                    SnmpVersion.V3, null,
                    new SnmpV3Credentials(V3_USER, V3_AUTH_PASS, V3_PRIV_PASS,
                            "MD5", "DES", "real-cisco-c6500"),
                    "Cisco", "version practice"));
        }

        log.info("Registered {} simulator devices (snmpsim @ {}:{})",
                registry.list().size(), SNMPSIM_HOST, SNMPSIM_PORT);
    }

    private void register(String id, String name, String community, String vendor, String location) {
        if (!registry.exists(id)) {
            registry.register(new ManagedDevice(
                    id, name, SNMPSIM_HOST, SNMPSIM_PORT, SnmpVersion.V2C,
                    community, null, vendor, location));
        }
    }
}
