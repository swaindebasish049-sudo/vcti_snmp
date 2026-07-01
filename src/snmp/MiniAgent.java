package snmp;

import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.MessageException;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.mp.StateReference;
import org.snmp4j.mp.StatusInformation;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

/**
 * A minimal SNMP AGENT used ONLY as a local stand-in for a real device,
 * so you can watch the Manager <-> Agent conversation without any hardware.
 *
 * It listens on 127.0.0.1:1161 and answers GET requests:
 *   - sysName (1.3.6.1.2.1.1.5.0)  -> "RouterA-LAB"
 *   - sysDescr (1.3.6.1.2.1.1.1.0) -> a fake description
 *   - anything else                -> "n/a"
 *
 * Run this FIRST, then run SnmpGet in another window.
 *
 * Usage:
 *   java -cp out;lib/snmp4j-2.8.15.jar snmp.MiniAgent
 */
public class MiniAgent implements CommandResponder {

    private static final OID SYS_NAME  = new OID("1.3.6.1.2.1.1.5.0");
    private static final OID SYS_DESCR = new OID("1.3.6.1.2.1.1.1.0");

    public static void main(String[] args) throws Exception {
        DefaultUdpTransportMapping transport =
                new DefaultUdpTransportMapping(new UdpAddress("127.0.0.1/1161"));
        Snmp snmp = new Snmp(transport);          // registers v1/v2c/v3 processing
        snmp.addCommandResponder(new MiniAgent()); // who handles incoming requests
        transport.listen();

        System.out.println("MiniAgent (stand-in device) listening on 127.0.0.1:1161 ...");
        System.out.println("Press Ctrl+C to stop.");
        Thread.sleep(Long.MAX_VALUE);             // keep running
    }

    @Override
    public void processPdu(CommandResponderEvent event) {
        PDU request = event.getPDU();
        if (request == null) {
            return;
        }

        System.out.println("Agent <- received request for: "
                + request.getVariableBindings());

        // Fill in a value for each requested OID, then turn this into a RESPONSE.
        for (VariableBinding vb : request.getVariableBindings()) {
            OID oid = vb.getOid();
            if (SYS_NAME.equals(oid)) {
                vb.setVariable(new OctetString("RouterA-LAB"));
            } else if (SYS_DESCR.equals(oid)) {
                vb.setVariable(new OctetString("Lab Virtual Router, SNMP4J stand-in v1.0"));
            } else {
                vb.setVariable(new OctetString("n/a"));
            }
        }
        request.setType(PDU.RESPONSE);
        request.setErrorStatus(0);
        request.setErrorIndex(0);

        // Send the response back to the Manager.
        StateReference ref = event.getStateReference();
        try {
            event.getMessageDispatcher().returnResponsePdu(
                    event.getMessageProcessingModel(),
                    event.getSecurityModel(),
                    event.getSecurityName(),
                    event.getSecurityLevel(),
                    request,
                    event.getMaxSizeResponsePDU(),
                    ref,
                    new StatusInformation());
            System.out.println("Agent -> replied: " + request.getVariableBindings());
        } catch (MessageException e) {
            e.printStackTrace();
        }
    }
}
