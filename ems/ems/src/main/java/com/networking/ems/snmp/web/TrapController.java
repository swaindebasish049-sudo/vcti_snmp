package com.networking.ems.snmp.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.ScopedPDU;
import org.snmp4j.Snmp;
import org.snmp4j.UserTarget;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.SecurityLevel;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.VariableBinding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.networking.ems.snmp.model.TrapEvent;
import com.networking.ems.snmp.trap.TrapStore;

/**
 * Read side of the trap receiver, plus self-contained test senders so the
 * feature can be demoed without installing Net-SNMP.
 *
 *  List     : GET    /traps
 *  Clear    : DELETE /traps
 *  Test v2c : POST   /traps/test       (v2c linkDown trap)
 *  Test v3  : POST   /traps/test-v3    (v3 authPriv linkUp INFORM)
 */
@RestController
@RequestMapping("/traps")
public class TrapController {

    private final TrapStore store;
    private final Snmp snmp;         // @Primary query session; also has the v3 trap user in its USM
    private final int trapPort;
    private final String v3User;
    private final String v3AuthPass;
    private final String v3PrivPass;

    public TrapController(TrapStore store, Snmp snmp,
                          @Value("${ems.trap.port:1162}") int trapPort,
                          @Value("${ems.trap.v3.username:}") String v3User,
                          @Value("${ems.trap.v3.auth-password:}") String v3AuthPass,
                          @Value("${ems.trap.v3.priv-password:}") String v3PrivPass) {
        this.store = store;
        this.snmp = snmp;
        this.trapPort = trapPort;
        this.v3User = v3User;
        this.v3AuthPass = v3AuthPass;
        this.v3PrivPass = v3PrivPass;
    }

    @GetMapping
    public List<TrapEvent> list() {
        return store.list();
    }

    @DeleteMapping
    public Map<String, Integer> clear() {
        return Map.of("cleared", store.clear());
    }

    /**
     * Emit a synthetic v2c linkDown trap at our own receiver on 127.0.0.1.
     * A v2c notification's first two bindings are, by convention, sysUpTime and
     * snmpTrapOID (which identifies the trap), followed by any payload varbinds.
     */
    @PostMapping("/test")
    public Map<String, Object> sendTest() throws IOException {
        PDU trap = new PDU();
        trap.setType(PDU.NOTIFICATION); // v2c TRAP
        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(System.nanoTime() / 10_000_000L)));
        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, new OID("1.3.6.1.6.3.1.1.5.3"))); // linkDown
        trap.add(new VariableBinding(new OID("1.3.6.1.2.1.2.2.1.1.2"), new Integer32(2)));         // ifIndex = 2
        trap.add(new VariableBinding(new OID("1.3.6.1.2.1.2.2.1.2.2"), new OctetString("GigabitEthernet0/2")));

        CommunityTarget target = new CommunityTarget();
        target.setCommunity(new OctetString("public"));
        target.setVersion(SnmpConstants.version2c);
        target.setAddress(GenericAddress.parse("udp:127.0.0.1/" + trapPort));

        snmp.send(trap, target);
        return Map.of("sent", true, "version", "v2c", "to", "127.0.0.1:" + trapPort,
                "trap", "linkDown (ifIndex=2)");
    }

    /**
     * v3 authPriv INFORM (acked). SNMP4J discovers the receiver's engine id
     * automatically -- exercises v3 auth/priv + the ack path.
     */
    @PostMapping("/test-v3")
    public Map<String, Object> sendTestV3() throws IOException {
        return sendV3Notification(false);
    }

    /**
     * v3 authPriv TRAP (fire-and-forget). For a trap the SENDER is the
     * authoritative engine, so we stamp our own engine id on the target; the
     * receiver localizes the user's keys to it.
     */
    @PostMapping("/test-v3-trap")
    public Map<String, Object> sendTestV3Trap() throws IOException {
        return sendV3Notification(true);
    }

    private Map<String, Object> sendV3Notification(boolean asTrap) throws IOException {
        if (v3User == null || v3User.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "v3 trap user is not configured (set ems.trap.v3.username)");
        }

        UserTarget target = new UserTarget();
        target.setAddress(GenericAddress.parse("udp:127.0.0.1/" + trapPort));
        target.setVersion(SnmpConstants.version3);
        target.setSecurityName(new OctetString(v3User));
        target.setSecurityLevel(securityLevel());
        target.setRetries(2);
        target.setTimeout(5000);
        if (asTrap) {
            target.setAuthoritativeEngineID(snmp.getLocalEngineID());
        }

        String trapOid = asTrap ? "1.3.6.1.6.3.1.1.5.3" : "1.3.6.1.6.3.1.1.5.4"; // linkDown / linkUp
        int ifIndex = asTrap ? 4 : 3;
        String label = "GigabitEthernet0/" + ifIndex + (asTrap ? " (v3 authPriv trap)" : " (v3 authPriv inform)");

        ScopedPDU pdu = new ScopedPDU();
        pdu.setType(asTrap ? PDU.NOTIFICATION : PDU.INFORM);
        pdu.add(new VariableBinding(SnmpConstants.sysUpTime,
                new TimeTicks((System.nanoTime() / 10_000_000L) & 0xFFFFFFFFL)));
        pdu.add(new VariableBinding(SnmpConstants.snmpTrapOID, new OID(trapOid)));
        pdu.add(new VariableBinding(new OID("1.3.6.1.2.1.2.2.1.1." + ifIndex), new Integer32(ifIndex)));
        pdu.add(new VariableBinding(new OID("1.3.6.1.2.1.2.2.1.2." + ifIndex), new OctetString(label)));

        ResponseEvent resp = snmp.send(pdu, target);
        boolean acked = !asTrap && resp != null && resp.getResponse() != null;
        return Map.of("sent", true, "version", "v3", "kind", asTrap ? "trap" : "inform",
                "level", levelName(), "user", v3User, "acked", acked,
                "trap", asTrap ? "linkDown (ifIndex=4)" : "linkUp (ifIndex=3)");
    }

    /** Derive the security level from which passwords are configured (same rule as the SNMP layer). */
    private int securityLevel() {
        if (v3AuthPass == null || v3AuthPass.isBlank()) return SecurityLevel.NOAUTH_NOPRIV;
        if (v3PrivPass == null || v3PrivPass.isBlank()) return SecurityLevel.AUTH_NOPRIV;
        return SecurityLevel.AUTH_PRIV;
    }

    private String levelName() {
        return switch (securityLevel()) {
            case SecurityLevel.NOAUTH_NOPRIV -> "noAuthNoPriv";
            case SecurityLevel.AUTH_NOPRIV -> "authNoPriv";
            default -> "authPriv";
        };
    }
}
