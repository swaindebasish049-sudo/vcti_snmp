package com.networking.ems.snmp.config;

import java.io.IOException;

import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Creates ONE shared SNMP session for the whole application.
 *
 * Why a single bean instead of {@code new Snmp(...)} per request?
 *  - Opening a UDP transport per request leaks sockets and is slow.
 *  - SNMP4J's {@link Snmp} object is thread-safe for sending, so one instance
 *    can serve every concurrent request.
 *
 * v3 support: SNMPv3 needs a local security engine. We register:
 *  - the default security protocols (MD5/SHA auth, DES/AES privacy), and
 *  - a USM (User-based Security Model) with a locally generated engine ID.
 * Per-device v3 USERS are added later by Snmp4jClient as devices are queried.
 * SNMP4J then handles v3 engine discovery + time sync automatically per agent.
 *
 * {@code destroyMethod = "close"} shuts the transport down cleanly on exit.
 */
@Configuration
public class SnmpSessionConfig {

    @Bean(destroyMethod = "close")
    @Primary // the query session is the default Snmp bean; the trap receiver has its own
    public Snmp snmp() throws IOException {
        // Bind to an ephemeral local UDP port for sending/receiving manager traffic.
        TransportMapping<UdpAddress> transport =
                new DefaultUdpTransportMapping(new UdpAddress("0.0.0.0/0"));
        Snmp snmp = new Snmp(transport);

        // --- SNMPv3 machinery -------------------------------------------------
        SecurityProtocols.getInstance().addDefaultProtocols(); // MD5, SHA, DES, AES...
        USM usm = new USM(SecurityProtocols.getInstance(),
                new OctetString(MPv3.createLocalEngineID()), 0);
        SecurityModels.getInstance().addSecurityModel(usm);

        transport.listen(); // start the listener thread
        return snmp;
    }
}
