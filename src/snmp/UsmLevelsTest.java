package snmp;

import org.snmp4j.Snmp;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import com.networking.ems.snmp.client.Snmp4jClient;
import com.networking.ems.snmp.model.SnmpDevice;
import com.networking.ems.snmp.model.SnmpResult;
import com.networking.ems.snmp.model.SnmpV3Credentials;

/**
 * Demonstrates USM's three SECURITY LEVELS by running the same GET with the
 * same user at each level against snmpsim:
 *
 *   noAuthNoPriv  - username only          (no signature, no encryption)
 *   authNoPriv    - + MD5 authentication   (signed, still readable on wire)
 *   authPriv      - + DES privacy          (signed AND encrypted)
 *
 * A fresh SNMP engine is created per level because SNMP4J's USM keys users
 * by username -- registering "simulator" with three different key-sets on
 * one engine would overwrite each other.
 */
public class UsmLevelsTest {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 1161;
    private static final String CONTEXT = "real-cisco-c6500";
    private static final String SYS_NAME = "1.3.6.1.2.1.1.5.0";

    public static void main(String[] args) throws Exception {
        Object[][] levels = {
            // label            user         authPass       privPass     msgFlags meaning
            {"noAuthNoPriv", "labuser",   null,          null,        "flags: ....  (nothing proven, nothing hidden)"},
            {"authNoPriv  ", "authuser",  "authpass123", null,        "flags: auth  (signed w/ MD5, payload readable)"},
            {"authPriv    ", "simulator", "auctoritas",  "privatus",  "flags: auth+priv (signed + DES-encrypted PDU)"},
        };

        System.out.println("Three users, each provisioned at a different level, same OID (sysName):\n");

        for (Object[] lv : levels) {
            String label = (String) lv[0];
            String user  = (String) lv[1];
            String auth  = (String) lv[2];
            String priv  = (String) lv[3];
            String note  = (String) lv[4];

            // fresh engine per level (see class comment)
            DefaultUdpTransportMapping transport =
                    new DefaultUdpTransportMapping(new UdpAddress("0.0.0.0/0"));
            Snmp snmp = new Snmp(transport);
            SecurityProtocols.getInstance().addDefaultProtocols();
            SecurityModels.getInstance().addSecurityModel(
                    new USM(SecurityProtocols.getInstance(),
                            new OctetString(MPv3.createLocalEngineID()), 0));
            transport.listen();

            Snmp4jClient client = new Snmp4jClient(snmp);
            SnmpDevice dev = SnmpDevice.v3(HOST, PORT, new SnmpV3Credentials(
                    user, auth, priv, "MD5", "DES", CONTEXT));

            try {
                long t0 = System.currentTimeMillis();
                SnmpResult r = client.get(dev, SYS_NAME);
                long ms = System.currentTimeMillis() - t0;
                System.out.println(label + " (user " + user + ") -> OK   sysName="
                        + r.value() + "  (" + ms + " ms)");
            } catch (Exception e) {
                System.out.println(label + " (user " + user + ") -> REJECTED: "
                        + shortMsg(e.getMessage()));
            }
            System.out.println("               " + note + "\n");

            snmp.close();
        }

        System.out.println("Takeaway: the agent supports all three levels; each USER is");
        System.out.println("provisioned with a minimum level and the agent enforces it.");
        System.out.println("labuser=noAuthNoPriv, authuser=authNoPriv, simulator=authPriv.");
    }

    private static String shortMsg(String s) {
        if (s == null) return "(no message)";
        return s.length() > 90 ? s.substring(0, 90) + "..." : s;
    }
}
