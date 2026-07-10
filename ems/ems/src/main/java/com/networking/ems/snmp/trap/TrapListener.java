package com.networking.ems.snmp.trap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.PDU;
import org.snmp4j.mp.MessageProcessingModel;
import org.snmp4j.mp.StatusInformation;
import org.snmp4j.security.SecurityLevel;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.springframework.stereotype.Component;

import com.networking.ems.snmp.model.SnmpResult;
import com.networking.ems.snmp.model.TrapEvent;

/**
 * Receives inbound SNMP notifications. SNMP4J invokes {@link #processPdu} on a
 * listener thread for every TRAP or INFORM that arrives on the trap transport.
 *
 * TRAP  = fire-and-forget; nothing to send back.
 * INFORM = must be acknowledged with a RESPONSE PDU, or the sender keeps
 *          retrying. The ack block below mirrors SNMP4J's canonical example.
 */
@Component
public class TrapListener implements CommandResponder {

    private static final Logger log = LoggerFactory.getLogger(TrapListener.class);

    private final TrapStore store;

    public TrapListener(TrapStore store) {
        this.store = store;
    }

    @Override
    public void processPdu(CommandResponderEvent event) {
        PDU pdu = event.getPDU();
        if (pdu == null) {
            return;
        }

        List<SnmpResult> bindings = new ArrayList<>();
        for (VariableBinding vb : pdu.getVariableBindings()) {
            bindings.add(new SnmpResult(vb.getOid().toString(), vb.getVariable().toString()));
        }

        boolean isInform = pdu.getType() == PDU.INFORM;

        // Which SNMP version delivered this, and (for v3) at what USM level / user.
        String version = switch (event.getMessageProcessingModel()) {
            case MessageProcessingModel.MPv1 -> "v1";
            case MessageProcessingModel.MPv2c -> "v2c";
            case MessageProcessingModel.MPv3 -> "v3";
            default -> "v?";
        };
        String securityLevel = null;
        String user = null;
        if (event.getMessageProcessingModel() == MessageProcessingModel.MPv3) {
            securityLevel = switch (event.getSecurityLevel()) {
                case SecurityLevel.NOAUTH_NOPRIV -> "noAuthNoPriv";
                case SecurityLevel.AUTH_NOPRIV -> "authNoPriv";
                case SecurityLevel.AUTH_PRIV -> "authPriv";
                default -> "unknown";
            };
            if (event.getSecurityName() != null) {
                user = new OctetString(event.getSecurityName()).toString();
            }
        }

        TrapEvent trap = new TrapEvent(
                Instant.now().toEpochMilli(),
                String.valueOf(event.getPeerAddress()),
                isInform ? "INFORM" : "TRAP",
                version, securityLevel, user,
                bindings);

        store.add(trap);                       // <-- the single seam a Kafka sink would replace
        log.info("Received {} {} from {}: {}", version, trap.type(), trap.source(), bindings);

        // An INFORM must be acknowledged, otherwise the agent retransmits it.
        if (isInform) {
            pdu.setType(PDU.RESPONSE);
            pdu.setErrorStatus(PDU.noError);
            pdu.setErrorIndex(0);
            try {
                event.getMessageDispatcher().returnResponsePdu(
                        event.getMessageProcessingModel(),
                        event.getSecurityModel(),
                        event.getSecurityName(),
                        event.getSecurityLevel(),
                        pdu,
                        event.getMaxSizeResponsePDU(),
                        event.getStateReference(),
                        new StatusInformation());
            } catch (Exception e) {
                log.warn("Failed to acknowledge INFORM from {}", trap.source(), e);
            }
        }

        event.setProcessed(true);
    }
}
