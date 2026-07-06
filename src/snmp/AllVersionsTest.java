package snmp;

import java.util.List;

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
 * Standalone harness that exercises the PRODUCTION Snmp4jClient (compiled in
 * ems/ems/target/classes) against snmpsim for all three SNMP versions --
 * no Spring/Tomcat needed. Mirrors what SnmpSessionConfig does for setup.
 */
public class AllVersionsTest {

    public static void main(String[] args) throws Exception {
        // Same setup as SnmpSessionConfig
        DefaultUdpTransportMapping transport =
                new DefaultUdpTransportMapping(new UdpAddress("0.0.0.0/0"));
        Snmp snmp = new Snmp(transport);
        SecurityProtocols.getInstance().addDefaultProtocols();
        SecurityModels.getInstance().addSecurityModel(
                new USM(SecurityProtocols.getInstance(),
                        new OctetString(MPv3.createLocalEngineID()), 0));
        transport.listen();

        Snmp4jClient client = new Snmp4jClient(snmp);

        String host = "127.0.0.1";
        int port = 1161;
        String recording = "real-cisco-c6500"; // same data via all 3 versions

        SnmpDevice v1  = SnmpDevice.v1(host, port, recording);
        SnmpDevice v2c = SnmpDevice.v2c(host, port, recording);
        SnmpDevice v3  = SnmpDevice.v3(host, port, new SnmpV3Credentials(
                "simulator", "auctoritas", "privatus", "MD5", "DES", recording));

        String sysDescr = "1.3.6.1.2.1.1.1.0";
        String systemSubtree = "1.3.6.1.2.1.1";
        String ifDescr = "1.3.6.1.2.1.2.2.1.2";

        for (Object[] tc : new Object[][] {
                {"SNMPv1 ", v1}, {"SNMPv2c", v2c}, {"SNMPv3 ", v3}}) {
            String label = (String) tc[0];
            SnmpDevice dev = (SnmpDevice) tc[1];
            try {
                SnmpResult get = client.get(dev, sysDescr);
                System.out.println(label + " GET     : " + short60(get.value()));

                SnmpResult next = client.getNext(dev, systemSubtree);
                System.out.println(label + " GETNEXT : " + next.oid() + " = " + short60(next.value()));

                List<SnmpResult> multi = client.get(dev, List.of(
                        "1.3.6.1.2.1.1.3.0", "1.3.6.1.2.1.1.5.0"));
                System.out.println(label + " MULTIGET: uptime=" + multi.get(0).value()
                        + " sysName=" + multi.get(1).value());

                List<SnmpResult> sys = client.walk(dev, systemSubtree);
                List<SnmpResult> ifs = client.walk(dev, ifDescr);
                System.out.println(label + " WALK    : system=" + sys.size()
                        + " rows, ifDescr=" + ifs.size() + " rows"
                        + (dev.version().name().equals("V1") ? " (GETNEXT loop)" : " (GETBULK)"));
            } catch (Exception e) {
                System.out.println(label + " FAILED  : " + e.getMessage());
            }
            System.out.println();
        }

        // SET demo: snmpsim's "variation/writecache" recording accepts SETs.
        // Its sysDescr (1.3.6.1.2.1.1.1.0) is a writable string.
        try {
            SnmpDevice writable = SnmpDevice.v2c(host, port, "variation/writecache");
            SnmpResult before = client.get(writable, sysDescr);
            SnmpResult set = client.set(writable, sysDescr, "Modified-by-EMS-SET");
            SnmpResult after = client.get(writable, sysDescr);
            System.out.println("SET (v2c, writecache) : before='" + short60(before.value())
                    + "' -> after='" + short60(after.value()) + "'");
        } catch (Exception e) {
            System.out.println("SET (v2c, writecache) : " + e.getMessage());
        }

        // Negative test: v3 with an UNKNOWN username -> the agent must reject it
        // with a usmStats REPORT (this is also how v3 support is detectable:
        // even a failed v3 exchange gets a reply, unlike v1/v2c silence).
        try {
            SnmpDevice bad = SnmpDevice.v3(host, port, new SnmpV3Credentials(
                    "hacker", "WRONG-PASSWORD", "WRONG-TOO", "MD5", "DES", recording));
            client.get(bad, sysDescr);
            System.out.println("v3 wrong-credentials  : UNEXPECTEDLY SUCCEEDED");
        } catch (Exception e) {
            System.out.println("v3 wrong-credentials  : rejected as expected -> " + short60(e.getMessage()));
        }

        snmp.close();
    }

    private static String short60(String s) {
        if (s == null) return "null";
        s = s.replace("\r", " ").replace("\n", " ");
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }
}
