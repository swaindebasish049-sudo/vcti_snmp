package com.networking.ems.snmp.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.security.AuthMD5;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.PrivAES128;
import org.snmp4j.security.PrivDES;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.networking.ems.snmp.trap.TrapListener;

/**
 * Stands up a SECOND, dedicated SNMP session that only LISTENS for inbound
 * notifications (traps/informs).
 *
 * Why separate from {@link SnmpSessionConfig}'s session?
 *  - That one binds an ephemeral port for OUTBOUND queries (GET/WALK/SET).
 *  - Traps arrive INBOUND on a well-known port. The real trap port is UDP/162,
 *    which is privileged (<1024); we default to 1162 so the app can bind it
 *    without elevated rights. Override with ems.trap.port.
 *
 * v1/v2c traps work out of the box. For v3 (authenticated/encrypted) traps and
 * informs, the sender's USM USER must be known to the receiver so its messages
 * can be verified and decrypted. SNMP4J keeps the USM in a process-wide
 * SecurityModels registry; {@link SnmpSessionConfig} already created the USM, so
 * here we just add the trap user to it. We inject the primary query {@link Snmp}
 * bean to guarantee that USM exists before we touch it.
 *
 * v3 engine-id note:
 *  - INFORM: the receiver is authoritative -> the sender discovers our engine id
 *    automatically (no extra flags on snmpinform).
 *  - TRAP:   the SENDER is authoritative -> snmptrap must set its engine id with
 *    -e; SNMP4J localizes the user's keys to that engine id from the trap header.
 */
@Configuration
public class TrapReceiverConfig {

    private static final Logger log = LoggerFactory.getLogger(TrapReceiverConfig.class);

    @Bean(name = "trapSnmp", destroyMethod = "close")
    public Snmp trapSnmp(TrapListener listener,
                         Snmp querySnmp, // @Primary bean; forces USM to be created first
                         @Value("${ems.trap.port:1162}") int trapPort,
                         @Value("${ems.trap.v3.username:}") String v3User,
                         @Value("${ems.trap.v3.auth-protocol:SHA}") String authProto,
                         @Value("${ems.trap.v3.auth-password:}") String authPass,
                         @Value("${ems.trap.v3.priv-protocol:AES128}") String privProto,
                         @Value("${ems.trap.v3.priv-password:}") String privPass) throws IOException {

        TransportMapping<UdpAddress> transport =
                new DefaultUdpTransportMapping(new UdpAddress("0.0.0.0/" + trapPort));
        Snmp snmp = new Snmp(transport);

        if (v3User != null && !v3User.isBlank()) {
            registerV3TrapUser(querySnmp, v3User, authProto, authPass, privProto, privPass);
            log.info("v3 trap user '{}' registered (auth={}, priv={}); receiver engineId=0x{}",
                    v3User,
                    isBlank(authPass) ? "none" : authProto,
                    isBlank(privPass) ? "none" : privProto,
                    new OctetString(snmp.getLocalEngineID()).toHexString());
        }

        snmp.addCommandResponder(listener);
        transport.listen();
        log.info("SNMP trap receiver listening on UDP/{} (v1/v2c always; v3 {})",
                trapPort, (v3User == null || v3User.isBlank()) ? "disabled" : "enabled for user " + v3User);
        return snmp;
    }

    /**
     * Add the trap sender's user to the shared USM. A user added without a
     * specific engine id is a template SNMP4J localizes per-engine, which is what
     * inbound traps/informs need.
     */
    private void registerV3TrapUser(Snmp querySnmp, String user, String authProto, String authPass,
                                    String privProto, String privPass) {
        OID auth = null;
        OctetString authKey = null;
        if (!isBlank(authPass)) {
            auth = "SHA".equalsIgnoreCase(authProto) ? AuthSHA.ID : AuthMD5.ID;
            authKey = new OctetString(authPass);
        }
        OID priv = null;
        OctetString privKey = null;
        if (!isBlank(privPass)) {
            priv = (privProto != null && privProto.toUpperCase().startsWith("AES"))
                    ? PrivAES128.ID : PrivDES.ID;
            privKey = new OctetString(privPass);
        }
        querySnmp.getUSM().addUser(new OctetString(user),
                new UsmUser(new OctetString(user), auth, authKey, priv, privKey));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
