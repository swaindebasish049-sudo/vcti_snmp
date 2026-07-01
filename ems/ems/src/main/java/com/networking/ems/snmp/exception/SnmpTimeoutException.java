package com.networking.ems.snmp.exception;

/**
 * Thrown when the device does not answer within the configured timeout.
 * Usually means: wrong host/port, wrong community, wrong version, or agent down.
 */
public class SnmpTimeoutException extends SnmpException {

    public SnmpTimeoutException(String message) {
        super(message);
    }
}
