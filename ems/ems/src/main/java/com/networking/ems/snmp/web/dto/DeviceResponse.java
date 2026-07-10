package com.networking.ems.snmp.web.dto;

import com.networking.ems.snmp.model.ManagedDevice;
import com.networking.ems.snmp.model.SnmpV3Credentials;
import com.networking.ems.snmp.model.SnmpVersion;

/**
 * Safe view of a registered device for API responses.
 *
 * IMPORTANT: this intentionally does NOT include the community string / any
 * password. It DOES expose the v3 USM security LEVEL and the auth/priv protocol
 * NAMES (e.g. "authPriv", "SHA", "AES128") -- those are not secrets and make the
 * three USM levels visible in the UI.
 */
public record DeviceResponse(
        String id,
        String name,
        String host,
        int port,
        SnmpVersion version,
        String vendor,
        String location,
        boolean credentialConfigured,
        String securityLevel,   // v3 only: noAuthNoPriv | authNoPriv | authPriv  (null for v1/v2c)
        String authProtocol,    // v3 only: MD5 / SHA        (null if noAuth)
        String privProtocol     // v3 only: DES / AES128      (null if noPriv)
) {
    public static DeviceResponse from(ManagedDevice d) {
        boolean hasCred = (d.community() != null && !d.community().isBlank())
                || (d.v3() != null && d.v3().username() != null);

        String level = null, auth = null, priv = null;
        if (d.version() == SnmpVersion.V3 && d.v3() != null) {
            level = securityLevel(d.v3());
            if (d.v3().authPassword() != null) {
                auth = d.v3().authProtocol() != null ? d.v3().authProtocol() : "MD5";
            }
            if (d.v3().privPassword() != null) {
                priv = d.v3().privProtocol() != null ? d.v3().privProtocol() : "DES";
            }
        }

        return new DeviceResponse(
                d.id(), d.name(), d.host(), d.port(), d.version(),
                d.vendor(), d.location(), hasCred, level, auth, priv);
    }

    /** Same derivation the SNMP layer uses: presence of passwords decides the level. */
    private static String securityLevel(SnmpV3Credentials v3) {
        if (v3.authPassword() == null) return "noAuthNoPriv";
        if (v3.privPassword() == null) return "authNoPriv";
        return "authPriv";
    }
}
