package com.networking.ems.snmp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.networking.ems.snmp.inventory.DeviceRegistry;
import com.networking.ems.snmp.model.ManagedDevice;
import com.networking.ems.snmp.model.SnmpVersion;

/**
 * Pre-registers the snmpsim lab devices at startup so the inventory (and the
 * UI) is populated out of the box. Runs after the Spring context is ready.
 *
 * snmpsim serves one recording per community string: querying community
 * "cisco-2621-router" returns the data in cisco-2621-router.snmprec.
 *
 * NOTE: host is 127.0.0.1 because the snmpsim container runs on the SAME
 * machine as this app. Colleagues use the app's HTTP API/UI -- the app does
 * the SNMP locally on their behalf.
 */
@Component
public class SimulatorDeviceInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulatorDeviceInitializer.class);

    private static final String SNMPSIM_HOST = "127.0.0.1";
    private static final int SNMPSIM_PORT = 1161;

    private final DeviceRegistry registry;

    public SimulatorDeviceInitializer(DeviceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run(ApplicationArguments args) {
        register("cisco-2621",  "Cisco 2621 Edge Router",     "cisco-2621-router",  "Cisco", "EMS Lab - Rack 1");
        register("cisco-3640",  "Cisco 3640 Core Router",     "cisco-3640-router",  "Cisco", "EMS Lab - Rack 1");
        register("cisco-7204",  "Cisco 7204 Backbone Router", "cisco-7204-router",  "Cisco", "EMS Lab - Rack 2");
        register("cisco-c6506", "Cisco Catalyst 6506 Switch", "cisco-c6506-switch", "Cisco", "EMS Lab - DC Row A");
        register("cisco-3750",  "Cisco Catalyst 3750 Switch (recorded)", "cisco_16_switch", "Cisco", "snmpsim sample");
        log.info("Registered {} simulator devices (snmpsim @ {}:{})",
                registry.list().size(), SNMPSIM_HOST, SNMPSIM_PORT);
    }

    private void register(String id, String name, String community, String vendor, String location) {
        if (!registry.exists(id)) {
            registry.register(new ManagedDevice(
                    id, name, SNMPSIM_HOST, SNMPSIM_PORT, SnmpVersion.V2C,
                    community, vendor, location));
        }
    }
}
