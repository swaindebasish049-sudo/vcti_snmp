package com.networking.ems.snmp.exception;

/**
 * Base unchecked exception for any SNMP-layer failure.
 * Keeps SNMP4J's checked IOExceptions etc. from leaking into the web layer.
 */
public class SnmpException extends RuntimeException {

    public SnmpException(String message) {
        super(message);
    }

    public SnmpException(String message, Throwable cause) {
        super(message, cause);
    }
}
