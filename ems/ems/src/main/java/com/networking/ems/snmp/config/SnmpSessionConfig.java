package com.networking.ems.snmp.config;

import java.io.IOException;

import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates ONE shared SNMP session for the whole application.
 *
 * Why a single bean instead of {@code new Snmp(...)} per request?
 *  - Opening a UDP transport per request leaks sockets and is slow.
 *  - SNMP4J's {@link Snmp} object is thread-safe for sending, so one instance
 *    can serve every concurrent request.
 *
 * {@code destroyMethod = "close"} ensures the transport is shut down cleanly
 * when the Spring context stops (Snmp.close() also closes the transport).
 *
 * NOTE: This is enough for v1/v2c. When you add v3, you'll also register the
 * USM (User-based Security Model) and users on this same Snmp instance here.
 */
@Configuration
public class SnmpSessionConfig {

    @Bean(destroyMethod = "close")
    public Snmp snmp() throws IOException {
        // Bind to an ephemeral local UDP port for sending/receiving manager traffic.
        TransportMapping<UdpAddress> transport =
                new DefaultUdpTransportMapping(new UdpAddress("0.0.0.0/0"));
        Snmp snmp = new Snmp(transport);
        transport.listen(); // start the listener thread
        return snmp;
    }
}
