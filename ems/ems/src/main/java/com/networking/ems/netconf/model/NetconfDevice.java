package com.networking.ems.netconf.model;

/**
 * A device managed over NETCONF (SSH, port 830). The NETCONF counterpart of the
 * SNMP inventory's device -- connection details plus credentials.
 *
 * Unlike SNMP (community/USM over UDP), NETCONF authenticates over SSH, so the
 * credential is simply an SSH username + password (key-based auth is a possible
 * future addition).
 */
public record NetconfDevice(
        String id,        // friendly unique id, e.g. "netopeer2-lab"
        String name,      // display name
        String host,      // management IP / hostname
        int port,         // NETCONF-over-SSH port (usually 830)
        String username,  // SSH username (sensitive pairing)
        String password   // SSH password (sensitive!)
) {}
