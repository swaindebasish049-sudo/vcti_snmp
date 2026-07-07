package com.networking.ems.snmp.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.ScopedPDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.UserTarget;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.AuthMD5;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.PrivAES128;
import org.snmp4j.security.PrivDES;
import org.snmp4j.security.SecurityLevel;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.springframework.stereotype.Component;

import com.networking.ems.snmp.exception.SnmpException;
import com.networking.ems.snmp.exception.SnmpTimeoutException;
import com.networking.ems.snmp.model.SnmpDevice;
import com.networking.ems.snmp.model.SnmpResult;
import com.networking.ems.snmp.model.SnmpV3Credentials;

/**
 * SNMP4J-backed implementation of all operations for SNMP v1, v2c and v3.
 * This is the ONLY class that imports SNMP4J.
 *
 * Version differences handled here:
 *  - v1/v2c: CommunityTarget + plain PDU. The community string is the credential.
 *  - v3:     UserTarget + ScopedPDU. Credentials are a USM user (auth+priv);
 *            the ScopedPDU carries an optional context name (snmpsim uses the
 *            context to select the recording, like the community does in v2c).
 *  - WALK:   v1 has no GETBULK, so it walks with a GETNEXT loop;
 *            v2c and v3 both use GETBULK.
 */
@Component
public class Snmp4jClient implements SnmpClient {

    /** Safety cap so a misbehaving agent can't make a WALK run forever. */
    private static final int MAX_WALK_RESULTS = 10_000;
    /** How many rows to request per GETBULK round-trip (v2c/v3). */
    private static final int BULK_MAX_REPETITIONS = 25;

    private final Snmp snmp;

    /** Tracks which v3 users were already added to the shared USM. */
    private final Set<String> registeredV3Users = ConcurrentHashMap.newKeySet();

    public Snmp4jClient(Snmp snmp) {
        this.snmp = snmp;
    }

    // =========================================================================
    // GET
    // =========================================================================

    @Override
    public SnmpResult get(SnmpDevice device, String oid) {
        PDU request = createPdu(device);
        request.add(new VariableBinding(new OID(oid)));
        request.setType(PDU.GET);

        PDU response = sendRaw(device, request);
        checkErrorStatus(response, "GET " + oid);

        VariableBinding vb = response.get(0);
        requireUsable(vb, oid, device);
        return toResult(vb);
    }

    @Override
    public List<SnmpResult> get(SnmpDevice device, List<String> oids) {
        PDU request = createPdu(device);
        for (String oid : oids) {
            request.add(new VariableBinding(new OID(oid)));
        }
        request.setType(PDU.GET);

        PDU response = sendRaw(device, request);
        checkErrorStatus(response, "GET " + oids);

        List<SnmpResult> results = new ArrayList<>();
        for (VariableBinding vb : response.getVariableBindings()) {
            results.add(toResult(vb)); // keep exception variables as-is in a multi-get
        }
        return results;
    }

    // =========================================================================
    // GETNEXT
    // =========================================================================

    @Override
    public SnmpResult getNext(SnmpDevice device, String oid) {
        PDU request = createPdu(device);
        request.add(new VariableBinding(new OID(oid)));
        request.setType(PDU.GETNEXT);

        PDU response = sendRaw(device, request);
        checkErrorStatus(response, "GETNEXT " + oid);

        VariableBinding vb = response.get(0);
        if (vb == null || vb.isException()) {
            throw new SnmpException("No OID exists after " + oid + " on " + device.host());
        }
        return toResult(vb);
    }

    // =========================================================================
    // WALK -- version-dependent
    // =========================================================================

    @Override
    public List<SnmpResult> walk(SnmpDevice device, String baseOid) {
        return switch (device.version()) {
            case V1       -> walkWithGetNext(device, baseOid); // v1 has no GETBULK
            case V2C, V3  -> walkWithGetBulk(device, baseOid);
        };
    }

    /** v1: repeated GETNEXT until we leave the subtree. */
    private List<SnmpResult> walkWithGetNext(SnmpDevice device, String baseOid) {
        List<SnmpResult> results = new ArrayList<>();
        OID base = new OID(baseOid);
        OID current = base;

        while (results.size() < MAX_WALK_RESULTS) {
            PDU request = createPdu(device);
            request.add(new VariableBinding(current));
            request.setType(PDU.GETNEXT);

            PDU response = sendRaw(device, request);

            // v1 signals "past the end of the MIB" with a noSuchName error.
            if (response.getErrorStatus() == PDU.noSuchName) {
                break;
            }
            checkErrorStatus(response, "WALK " + baseOid);

            VariableBinding vb = response.get(0);
            if (vb == null || vb.isException()) {
                break;
            }
            OID next = vb.getOid();
            if (!isInSubtree(base, next)) {
                break; // walked out of the requested subtree -> done
            }
            results.add(toResult(vb));
            current = next;
        }
        return results;
    }

    /** v2c/v3: GETBULK pulls many rows per round-trip -- far fewer packets. */
    private List<SnmpResult> walkWithGetBulk(SnmpDevice device, String baseOid) {
        List<SnmpResult> results = new ArrayList<>();
        OID base = new OID(baseOid);
        OID current = base;
        boolean done = false;

        while (!done && results.size() < MAX_WALK_RESULTS) {
            PDU request = createPdu(device);
            request.setType(PDU.GETBULK);
            request.setNonRepeaters(0);
            request.setMaxRepetitions(BULK_MAX_REPETITIONS);
            request.add(new VariableBinding(current));

            PDU response = sendRaw(device, request);
            if (response.getErrorStatus() != PDU.noError) {
                break; // some agents report end-of-mib as an error here
            }

            List<? extends VariableBinding> bindings = response.getVariableBindings();
            if (bindings.isEmpty()) {
                break;
            }
            for (VariableBinding vb : bindings) {
                OID oid = vb.getOid();
                if (vb.isException() || !isInSubtree(base, oid)) {
                    done = true; // endOfMibView, or we left the subtree
                    break;
                }
                results.add(toResult(vb));
                current = oid; // continue the next bulk from here
            }
        }
        return results;
    }

    // =========================================================================
    // SET
    // =========================================================================

    @Override
    public SnmpResult set(SnmpDevice device, String oid, String value) {
        PDU request = createPdu(device);
        // NOTE: sent as an OctetString (works for string-typed objects like
        // sysLocation/sysContact). Integer-typed objects (e.g. ifAdminStatus)
        // would need an Integer32 -- a future typed-SET enhancement.
        request.add(new VariableBinding(new OID(oid), new OctetString(value)));
        request.setType(PDU.SET);

        PDU response = sendRaw(device, request);
        checkErrorStatus(response, "SET " + oid);

        VariableBinding vb = response.get(0);
        requireUsable(vb, oid, device);
        return toResult(vb);
    }

    // =========================================================================
    // Version plumbing: target + PDU construction
    // =========================================================================

    /**
     * v1/v2c -> CommunityTarget (community string is the credential).
     * v3     -> UserTarget (USM user; security level derived from which
     *           passwords are present).
     */
    private Target buildTarget(SnmpDevice device) {
        Target target = switch (device.version()) {
            case V1 -> {
                CommunityTarget t = new CommunityTarget();
                t.setCommunity(new OctetString(device.community()));
                t.setVersion(SnmpConstants.version1);
                yield t;
            }
            case V2C -> {
                CommunityTarget t = new CommunityTarget();
                t.setCommunity(new OctetString(device.community()));
                t.setVersion(SnmpConstants.version2c);
                yield t;
            }
            case V3 -> {
                SnmpV3Credentials creds = requireV3(device);
                ensureV3UserRegistered(creds);
                UserTarget t = new UserTarget();
                t.setSecurityName(new OctetString(creds.username()));
                t.setSecurityLevel(securityLevel(creds));
                t.setVersion(SnmpConstants.version3);
                yield t;
            }
        };
        target.setAddress(GenericAddress.parse("udp:" + device.host() + "/" + device.port()));
        target.setRetries(1);
        target.setTimeout(3000); // v3 needs an extra round-trip for engine discovery
        return target;
    }

    /**
     * v1/v2c use a plain PDU; v3 uses a ScopedPDU which can carry a context
     * name (snmpsim selects the recording by context, mirroring the community).
     */
    private PDU createPdu(SnmpDevice device) {
        if (device.version() != com.networking.ems.snmp.model.SnmpVersion.V3) {
            return new PDU();
        }
        ScopedPDU pdu = new ScopedPDU();
        SnmpV3Credentials creds = requireV3(device);
        if (creds.contextName() != null && !creds.contextName().isBlank()) {
            pdu.setContextName(new OctetString(creds.contextName()));
        }
        return pdu;
    }

    /** Add the v3 user to the shared USM once; SNMP4J localizes it per engine. */
    private void ensureV3UserRegistered(SnmpV3Credentials creds) {
        String key = creds.username() + "|" + creds.authPassword() + "|" + creds.privPassword()
                + "|" + creds.authProtocol() + "|" + creds.privProtocol();
        if (!registeredV3Users.add(key)) {
            return; // already registered
        }
        OID authProto = null;
        OctetString authPass = null;
        if (creds.authPassword() != null) {
            authProto = "SHA".equalsIgnoreCase(creds.authProtocol()) ? AuthSHA.ID : AuthMD5.ID;
            authPass = new OctetString(creds.authPassword());
        }
        OID privProto = null;
        OctetString privPass = null;
        if (creds.privPassword() != null) {
            privProto = (creds.privProtocol() != null
                    && creds.privProtocol().toUpperCase().startsWith("AES"))
                    ? PrivAES128.ID : PrivDES.ID;
            privPass = new OctetString(creds.privPassword());
        }
        snmp.getUSM().addUser(new OctetString(creds.username()),
                new UsmUser(new OctetString(creds.username()),
                        authProto, authPass, privProto, privPass));
    }

    /** Which passwords are present decides the v3 security level. */
    private int securityLevel(SnmpV3Credentials creds) {
        if (creds.authPassword() == null) {
            return SecurityLevel.NOAUTH_NOPRIV;
        }
        if (creds.privPassword() == null) {
            return SecurityLevel.AUTH_NOPRIV;
        }
        return SecurityLevel.AUTH_PRIV;
    }

    private SnmpV3Credentials requireV3(SnmpDevice device) {
        if (device.v3() == null || device.v3().username() == null) {
            throw new SnmpException("Device is V3 but has no v3 credentials (username required)");
        }
        return device.v3();
    }

    // =========================================================================
    // Shared helpers
    // =========================================================================

    /** Send a request over the shared session; throws on transport failure/timeout. */
    private PDU sendRaw(SnmpDevice device, PDU request) {
        Target target = buildTarget(device);
        ResponseEvent event;
        try {
            event = snmp.send(request, target);
        } catch (IOException e) {
            throw new SnmpException(
                    "Failed to send SNMP request to " + device.host() + ":" + device.port(), e);
        }
        if (event == null || event.getResponse() == null) {
            throw new SnmpTimeoutException(
                    "No response from " + device.host() + ":" + device.port()
                    + " (check host/port/credentials/version and that the agent is running)");
        }
        PDU response = event.getResponse();

        // v3: security failures come back as REPORT PDUs (usmStats counters),
        // not as timeouts -- decode the counter OID into a precise diagnosis.
        if (response.getType() == PDU.REPORT) {
            String oid = response.getVariableBindings().isEmpty()
                    ? "unknown" : response.get(0).getOid().toString();
            throw new SnmpException("SNMPv3 REPORT from " + device.host()
                    + ": " + usmReportReason(oid) + " (" + oid + ")");
        }
        return response;
    }

    /** Translate RFC 3414 usmStats REPORT counters into actionable messages. */
    private String usmReportReason(String oid) {
        return switch (oid) {
            case "1.3.6.1.6.3.15.1.1.1.0" ->
                "security level not allowed for this user (agent requires a higher level, e.g. authPriv)";
            case "1.3.6.1.6.3.15.1.1.2.0" ->
                "not in time window (engine clock resync; retry usually fixes this)";
            case "1.3.6.1.6.3.15.1.1.3.0" ->
                "unknown username";
            case "1.3.6.1.6.3.15.1.1.4.0" ->
                "unknown engine id (v3 discovery mismatch)";
            case "1.3.6.1.6.3.15.1.1.5.0" ->
                "wrong digest -- auth password or auth protocol (MD5/SHA) is incorrect";
            case "1.3.6.1.6.3.15.1.1.6.0" ->
                "decryption error -- priv password or priv protocol (DES/AES) is incorrect";
            default -> "security failure";
        };
    }

    private void checkErrorStatus(PDU response, String context) {
        if (response.getErrorStatus() != PDU.noError) {
            throw new SnmpException("SNMP error during " + context + ": "
                    + response.getErrorStatusText());
        }
    }

    private void requireUsable(VariableBinding vb, String oid, SnmpDevice device) {
        if (vb == null || vb.isException()) {
            throw new SnmpException("OID " + oid + " is not available on " + device.host()
                    + " (" + (vb == null ? "no binding" : vb.getVariable()) + ")");
        }
    }

    private SnmpResult toResult(VariableBinding vb) {
        return new SnmpResult(vb.getOid().toString(), vb.getVariable().toString());
    }

    /** True if {@code candidate} lies under the {@code base} OID subtree. */
    private boolean isInSubtree(OID base, OID candidate) {
        if (candidate.size() < base.size()) {
            return false;
        }
        for (int i = 0; i < base.size(); i++) {
            if (candidate.get(i) != base.get(i)) {
                return false;
            }
        }
        return true;
    }
}
