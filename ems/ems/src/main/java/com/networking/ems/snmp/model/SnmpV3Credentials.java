package com.networking.ems.snmp.model;

/**
 * SNMPv3 USM credentials. v3 replaces the community string with a user +
 * two optional passwords; which ones are present decides the security level:
 *
 *   username only                    -> noAuthNoPriv
 *   username + authPassword          -> authNoPriv   (signed, not encrypted)
 *   username + auth + privPassword   -> authPriv     (signed AND encrypted)
 *
 * contextName: v3 has no community, so simulators like snmpsim use the SNMPv3
 * "context name" to select which recording to serve (same role the community
 * string plays for v1/v2c). On a real device this is usually left empty.
 */
public record SnmpV3Credentials(
        String username,
        String authPassword,   // null => noAuthNoPriv
        String privPassword,   // null => authNoPriv
        String authProtocol,   // "MD5" or "SHA"    (default MD5)
        String privProtocol,   // "DES" or "AES128" (default DES)
        String contextName     // null/"" for real devices; recording name for snmpsim
) {}
