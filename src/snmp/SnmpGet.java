package snmp;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

/**
 * SNMP MANAGER: asks one question (an SNMP GET) and prints the answer.
 *
 * Usage:
 *   java -cp out;lib/snmp4j-2.8.15.jar snmp.SnmpGet <host> <port> <community> <oid>
 *
 * Example (against our local stand-in device):
 *   java -cp out;lib/snmp4j-2.8.15.jar snmp.SnmpGet 127.0.0.1 1161 public 1.3.6.1.2.1.1.5.0
 */
public class SnmpGet {

    public static void main(String[] args) throws Exception {
        // ---- 1. Read inputs (with sensible defaults) -------------------------
        String host      = args.length > 0 ? args[0] : "127.0.0.1";
        String port      = args.length > 1 ? args[1] : "1161";
        String community = args.length > 2 ? args[2] : "public";
        String oid       = args.length > 3 ? args[3] : "1.3.6.1.2.1.1.5.0"; // sysName

        System.out.println("Manager -> asking " + host + ":" + port
                + " (community=" + community + ") for OID " + oid);

        // ---- 2. Open a UDP transport and start the SNMP engine --------------
        DefaultUdpTransportMapping transport = new DefaultUdpTransportMapping();
        Snmp snmp = new Snmp(transport);
        transport.listen();

        // ---- 3. Describe WHO we're talking to (the agent/device) ------------
        Address targetAddress = GenericAddress.parse("udp:" + host + "/" + port);
        CommunityTarget target = new CommunityTarget();
        target.setCommunity(new OctetString(community)); // the "password" for v2c
        target.setAddress(targetAddress);
        target.setRetries(1);
        target.setTimeout(2000);                         // 2 seconds
        target.setVersion(SnmpConstants.version2c);      // SNMP v2c

        // ---- 4. Build the request: a GET for one OID -----------------------
        PDU pdu = new PDU();
        pdu.add(new VariableBinding(new OID(oid)));
        pdu.setType(PDU.GET);

        // ---- 5. Send it and wait for the reply -----------------------------
        ResponseEvent event = snmp.send(pdu, target);
        snmp.close();

        // ---- 6. Interpret the response -------------------------------------
        if (event == null || event.getResponse() == null) {
            System.out.println("Manager <- NO RESPONSE (timeout). "
                    + "Is the device/agent running and reachable on that port?");
            return;
        }

        for (VariableBinding vb : event.getResponse().getVariableBindings()) {
            System.out.println("Manager <- " + vb.getOid() + " = " + vb.getVariable());
        }
    }
}
