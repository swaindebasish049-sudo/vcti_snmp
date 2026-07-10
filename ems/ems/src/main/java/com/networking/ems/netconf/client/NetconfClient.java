package com.networking.ems.netconf.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelSubsystem;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.networking.ems.netconf.model.NetconfDevice;

/**
 * NETCONF-over-SSH client (RFC 6241), built on Apache MINA SSHD. The NETCONF
 * counterpart of Snmp4jClient -- the only class that speaks the wire protocol.
 *
 * Flow for every exchange:
 *   1. SSH connect + auth (username/password)
 *   2. open the "netconf" SSH subsystem channel
 *   3. read the server &lt;hello&gt; (capabilities), send our &lt;hello&gt;
 *   4. send one or more &lt;rpc&gt; requests, each framed by the NETCONF 1.0
 *      end-of-message marker "]]&gt;]]&gt;", and read the reply
 *
 * A {@link Session} is opened per operation for simplicity. Transactional
 * writes (edit-config on the candidate + commit) share one session.
 */
@Component
public class NetconfClient {

    private static final Logger log = LoggerFactory.getLogger(NetconfClient.class);

    /** NETCONF 1.0 end-of-message framing marker. */
    private static final String DELIM = "]]>]]>";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private static final String CLIENT_HELLO =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">"
            + "<capabilities><capability>urn:ietf:params:netconf:base:1.0</capability></capabilities>"
            + "</hello>";

    // ---- public operations ----------------------------------------------------

    /** get-config from a datastore ("running", "candidate", "startup"). */
    public String getConfig(NetconfDevice device, String datastore) {
        String ds = (datastore == null || datastore.isBlank()) ? "running" : datastore.trim();
        try (Session s = open(device)) {
            return s.rpc("<get-config><source><" + ds + "/></source></get-config>");
        } catch (IOException e) {
            throw new RuntimeException("NETCONF get-config failed on " + device.host() + ": " + e.getMessage(), e);
        }
    }

    /** get -- returns config AND operational state. */
    public String get(NetconfDevice device) {
        try (Session s = open(device)) {
            return s.rpc("<get/>");
        } catch (IOException e) {
            throw new RuntimeException("NETCONF get failed on " + device.host() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Transactional edit: apply {@code configXml} to the candidate datastore and,
     * if the edit succeeds and {@code commit} is true, commit it to running.
     * Returns the combined RPC replies.
     */
    public String editConfig(NetconfDevice device, String configXml, boolean commit) {
        try (Session s = open(device)) {
            String editReply = s.rpc("<edit-config><target><candidate/></target><config>"
                    + configXml + "</config></edit-config>");
            if (commit && editReply.contains("<ok/>")) {
                String commitReply = s.rpc("<commit/>");
                return editReply + "\n" + commitReply;
            }
            return editReply;
        } catch (IOException e) {
            throw new RuntimeException("NETCONF edit-config failed on " + device.host() + ": " + e.getMessage(), e);
        }
    }

    /**
     * The full, best-practice transactional workflow in ONE session:
     * lock candidate -> edit-config -> validate -> commit -> unlock.
     * If edit-config or validate fails, the candidate is discarded and unlocked
     * instead of committed -- so a bad edit never reaches running.
     * Returns every step's reply, one per line, so the whole transaction is visible.
     */
    public String editConfigTransactional(NetconfDevice device, String configXml) {
        try (Session s = open(device)) {
            StringBuilder log = new StringBuilder();
            log.append("lock: ").append(s.rpc("<lock><target><candidate/></target></lock>")).append('\n');

            String editReply = s.rpc("<edit-config><target><candidate/></target><config>"
                    + configXml + "</config></edit-config>");
            log.append("edit-config: ").append(editReply).append('\n');

            String validateReply = editReply.contains("<ok/>")
                    ? s.rpc("<validate><source><candidate/></source></validate>") : null;
            if (validateReply != null) log.append("validate: ").append(validateReply).append('\n');

            boolean proceed = editReply.contains("<ok/>")
                    && (validateReply == null || validateReply.contains("<ok/>"));
            if (proceed) {
                log.append("commit: ").append(s.rpc("<commit/>")).append('\n');
            } else {
                log.append("discard-changes: ").append(s.rpc("<discard-changes/>")).append('\n');
            }
            log.append("unlock: ").append(s.rpc("<unlock><target><candidate/></target></unlock>"));
            return log.toString();
        } catch (IOException e) {
            throw new RuntimeException("NETCONF transactional edit failed on " + device.host() + ": " + e.getMessage(), e);
        }
    }

    /** Reserve a datastore so no one else can edit it while you hold the lock. */
    public String lock(NetconfDevice device, String datastore) {
        return oneShot(device, "<lock><target><" + ds(datastore) + "/></target></lock>");
    }

    /** Release a lock taken with {@link #lock}. */
    public String unlock(NetconfDevice device, String datastore) {
        return oneShot(device, "<unlock><target><" + ds(datastore) + "/></target></unlock>");
    }

    /** Check a datastore's contents are valid without applying/committing anything. */
    public String validate(NetconfDevice device, String datastore) {
        return oneShot(device, "<validate><source><" + ds(datastore) + "/></source></validate>");
    }

    /** Apply the candidate datastore to running (standalone, outside editConfig). */
    public String commit(NetconfDevice device) {
        return oneShot(device, "<commit/>");
    }

    /** Throw away un-committed edits in the candidate, reverting it to match running. */
    public String discardChanges(NetconfDevice device) {
        return oneShot(device, "<discard-changes/>");
    }

    /** Replace an entire target datastore's contents with another datastore's. */
    public String copyConfig(NetconfDevice device, String source, String target) {
        return oneShot(device, "<copy-config><target><" + ds(target) + "/></target>"
                + "<source><" + ds(source) + "/></source></copy-config>");
    }

    /** Delete an entire (non-running) datastore's contents, e.g. reset candidate/startup. */
    public String deleteConfig(NetconfDevice device, String target) {
        return oneShot(device, "<delete-config><target><" + ds(target) + "/></target></delete-config>");
    }

    private String ds(String datastore) {
        return (datastore == null || datastore.isBlank()) ? "candidate" : datastore.trim();
    }

    private String oneShot(NetconfDevice device, String innerRpcXml) {
        try (Session s = open(device)) {
            return s.rpc(innerRpcXml);
        } catch (IOException e) {
            throw new RuntimeException("NETCONF operation failed on " + device.host() + ": " + e.getMessage(), e);
        }
    }

    // ---- session plumbing -----------------------------------------------------

    private Session open(NetconfDevice device) throws IOException {
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE); // lab: trust any host key
        client.start();
        try {
            ClientSession ssh = client.connect(device.username(), device.host(), device.port())
                    .verify(TIMEOUT).getSession();
            ssh.addPasswordIdentity(device.password());
            ssh.auth().verify(TIMEOUT);

            ChannelSubsystem channel = ssh.createSubsystemChannel("netconf");
            channel.open().verify(TIMEOUT);

            Session session = new Session(client, channel);
            session.readFrame();          // consume the server <hello>
            session.writeFrame(CLIENT_HELLO); // send our <hello>
            return session;
        } catch (IOException e) {
            client.stop();
            throw e;
        }
    }

    /** One open NETCONF session; closing it tears down the channel + SSH client. */
    private static final class Session implements AutoCloseable {
        private final SshClient client;
        private final ChannelSubsystem channel;
        private final OutputStream toServer;
        private final InputStream fromServer;
        private final AtomicInteger messageId = new AtomicInteger(1);

        Session(SshClient client, ChannelSubsystem channel) {
            this.client = client;
            this.channel = channel;
            this.toServer = channel.getInvertedIn();
            this.fromServer = channel.getInvertedOut();
        }

        /** Wrap the inner XML in an &lt;rpc&gt;, send it, and return the reply body. */
        String rpc(String innerXml) throws IOException {
            String rpc = "<rpc message-id=\"" + messageId.getAndIncrement()
                    + "\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">" + innerXml + "</rpc>";
            writeFrame(rpc);
            return readFrame();
        }

        void writeFrame(String xml) throws IOException {
            toServer.write((xml + "\n" + DELIM).getBytes());
            toServer.flush();
        }

        /** Read bytes until the "]]>]]>" end-of-message marker. */
        String readFrame() throws IOException {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = fromServer.read()) != -1) {
                sb.append((char) c);
                int n = sb.length();
                if (n >= DELIM.length() && sb.substring(n - DELIM.length()).equals(DELIM)) {
                    return sb.substring(0, n - DELIM.length()).trim();
                }
            }
            return sb.toString().trim();
        }

        @Override
        public void close() {
            try {
                channel.close(false);
            } catch (Exception e) {
                log.debug("channel close: {}", e.getMessage());
            }
            client.stop();
        }
    }
}
