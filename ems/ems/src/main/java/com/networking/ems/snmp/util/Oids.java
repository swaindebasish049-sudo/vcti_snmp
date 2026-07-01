package com.networking.ems.snmp.util;

/**
 * Well-known SNMP OIDs, so callers use names instead of magic number strings.
 * These all live under the standard SNMPv2-MIB / IF-MIB trees.
 */
public final class Oids {

    private Oids() {} // no instances

    // --- System group (1.3.6.1.2.1.1) ---
    public static final String SYS_DESCR     = "1.3.6.1.2.1.1.1.0"; // vendor/model/OS string
    public static final String SYS_OBJECT_ID = "1.3.6.1.2.1.1.2.0";
    public static final String SYS_UPTIME    = "1.3.6.1.2.1.1.3.0";
    public static final String SYS_CONTACT   = "1.3.6.1.2.1.1.4.0";
    public static final String SYS_NAME      = "1.3.6.1.2.1.1.5.0"; // hostname
    public static final String SYS_LOCATION  = "1.3.6.1.2.1.1.6.0";

    // --- Interfaces table (1.3.6.1.2.1.2.2.1) -- useful later for WALK ---
    public static final String IF_DESCR       = "1.3.6.1.2.1.2.2.1.2";
    public static final String IF_OPER_STATUS = "1.3.6.1.2.1.2.2.1.8";
}
