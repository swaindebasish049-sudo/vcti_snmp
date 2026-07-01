package com.networking.ems.snmp.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.springframework.stereotype.Component;

import com.networking.ems.snmp.exception.SnmpException;
import com.networking.ems.snmp.exception.SnmpTimeoutException;
import com.networking.ems.snmp.model.SnmpDevice;
import com.networking.ems.snmp.model.SnmpResult;

/**
 * SNMP4J-backed implementation of all v1/v2c operations.
 * This is the ONLY class that imports SNMP4J.
 *
 * Reuses the single shared {@link Snmp} session (from SnmpSessionConfig).
 */
@Component
public class Snmp4jClient implements SnmpClient {

    /** Safety cap so a misbehaving agent can't make a WALK run forever. */
    private static final int MAX_WALK_RESULTS = 10_000;
    /** How many rows to request per GETBULK round-trip (v2c). */
    private static final int BULK_MAX_REPETITIONS = 25;

    private final Snmp snmp;

    public Snmp4jClient(Snmp snmp) {
        this.snmp = snmp;
    }

    // =========================================================================
    // GET
    // =========================================================================

    @Override
    public SnmpResult get(SnmpDevice device, String oid) {
        PDU request = new PDU();
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
        PDU request = new PDU();
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
        PDU request = new PDU();
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
    // WALK -- the version-dependent one
    // =========================================================================

    @Override
    public List<SnmpResult> walk(SnmpDevice device, String baseOid) {
        return switch (device.version()) {
            case V1  -> walkWithGetNext(device, baseOid); // v1 has no GETBULK
            case V2C -> walkWithGetBulk(device, baseOid);
            case V3  -> throw new SnmpException(
                    "SNMPv3 is not supported yet; register the device as V1 or V2C");
        };
    }

    /** v1 (and a valid fallback for v2c): repeated GETNEXT until we leave the subtree. */
    private List<SnmpResult> walkWithGetNext(SnmpDevice device, String baseOid) {
        List<SnmpResult> results = new ArrayList<>();
        OID base = new OID(baseOid);
        OID current = base;

        while (results.size() < MAX_WALK_RESULTS) {
            PDU request = new PDU();
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
                break; // v2c endOfMibView etc.
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

    /** v2c: GETBULK pulls many rows per round-trip -- far fewer packets. */
    private List<SnmpResult> walkWithGetBulk(SnmpDevice device, String baseOid) {
        List<SnmpResult> results = new ArrayList<>();
        OID base = new OID(baseOid);
        OID current = base;
        boolean done = false;

        while (!done && results.size() < MAX_WALK_RESULTS) {
            PDU request = new PDU();
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
        PDU request = new PDU();
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
    // Shared helpers
    // =========================================================================

    /** Build a v1/v2c CommunityTarget from the device's version + community. */
    private Target buildTarget(SnmpDevice device) {
        int version = switch (device.version()) {
            case V1  -> SnmpConstants.version1;
            case V2C -> SnmpConstants.version2c;
            case V3  -> throw new SnmpException(
                    "SNMPv3 is not supported yet; register the device as V1 or V2C");
        };
        CommunityTarget target = new CommunityTarget();
        target.setCommunity(new OctetString(device.community()));
        target.setAddress(GenericAddress.parse("udp:" + device.host() + "/" + device.port()));
        target.setVersion(version);
        target.setRetries(1);
        target.setTimeout(2000);
        return target;
    }

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
                    + " (check host/port/community/version and that the agent is running)");
        }
        return event.getResponse();
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
