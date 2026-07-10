# VCTI SNMP Lab (EMS)

A learning/lab EMS that manages simulated Cisco devices over SNMP (v1/v2c/v3) and
NETCONF, built with Spring Boot + SNMP4J + Apache MINA SSHD, plus Docker-based
device labs (snmpsim, netopeer2) that the whole team can share.

## Repository layout

| Path | What it is |
|------|-----------|
| `ems/ems/` | Spring Boot EMS application (Java 17, SNMP4J 2.8.15, MINA SSHD 2.13.2) |
| `ems/ems/.../snmp/` | SNMP client, trap receiver, device inventory, web endpoints |
| `ems/ems/.../netconf/` | NETCONF client (SSH transport), web endpoints |
| `snmpsim-data/` | `.snmprec` device recordings served by the snmpsim Docker container (filename = SNMP community) |
| `src/snmp/` | Standalone SNMP4J learning examples (`SnmpGet`, `MiniAgent`) — no Spring needed |
| `lib/` | SNMP4J jar for the standalone examples |

## Quick start

### 1. Start the device simulator (Docker)

```bash
docker run -d --name snmpsim --restart unless-stopped \
  -p 1161:161/udp \
  -v "<repo>/snmpsim-data:/usr/local/snmpsim/data" \
  -e EXTRA_FLAGS="--v3-user=simulator --v3-auth-key=auctoritas --v3-priv-key=privatus --v3-user=authuser --v3-auth-key=authpass123 --v3-user=labuser" \
  tandrup/snmpsim
```

The `EXTRA_FLAGS` provision three SNMPv3 users, one per USM security level:

| User | Auth | Priv | Security level |
|------|------|------|----------------|
| `labuser` | — | — | noAuthNoPriv |
| `authuser` | MD5 `authpass123` | — | authNoPriv |
| `simulator` | MD5 `auctoritas` | DES `privatus` | authPriv |

Simulated devices (SNMP v2c, UDP port **1161**, community selects the device):

| Community | Device |
|-----------|--------|
| `cisco-2621-router` | Cisco 2621 edge router |
| `cisco-3640-router` | Cisco 3640 core router |
| `cisco-7204-router` | Cisco 7204 backbone router |
| `cisco-c6506-switch` | Cisco Catalyst 6506 switch |
| `cisco_16_switch` | Real recorded Catalyst 3750 |

### 2. Run the EMS

```bash
cd ems/ems
./mvnw spring-boot:run        # needs JDK 17
```

Then open **http://localhost:8080** — the simulator devices are auto-registered at startup, and the web UI lets you run System Info / Interface walks / custom OID GET+WALK against any device.

## REST API (used by the UI)

```
POST   /devices                     register a device
GET    /devices                     list inventory
GET    /devices/{id}/sysname        GET sysName
GET    /devices/{id}/get?oid=       GET one OID
GET    /devices/{id}/get-multi?oid=&oid=   multi-OID GET
GET    /devices/{id}/getnext?oid=   GETNEXT
GET    /devices/{id}/walk?oid=      WALK (GETBULK for v2c, GETNEXT loop for v1)
POST   /devices/{id}/set?oid=&value=  SET (needs RW community)
```

## Adding a new simulated device

1. Create `snmpsim-data/<community-name>.snmprec` — one line per OID:
   `OID|type|value` (types: 2=Integer, 4=OctetString, `4x|hex` for MAC, 6=OID, 65=Counter32, 66=Gauge32, 67=TimeTicks). Lines must be in ascending OID order; one bad line stops the daemon.
2. `docker restart snmpsim`
3. Register it in the EMS (UI/API), or add it to `SimulatorDeviceInitializer`.

## SNMP versions

All operations work over SNMP **v1, v2c and v3**:

- v1/v2c: the community string is the credential (and selects the snmpsim recording)
- v3: USM credentials; snmpsim's built-in user is `simulator` / auth `auctoritas` (MD5)
  / priv `privatus` (DES), and the **context name** selects the recording.
  The startup inventory includes `c6500-v1`, `c6500-v2c` and `c6500-v3` — the same
  real Catalyst 6500 capture via all three versions, for side-by-side practice.

v3 registration example:

```json
POST /devices
{
  "id": "my-v3-device", "name": "My v3 Device",
  "host": "127.0.0.1", "port": 1161, "version": "V3",
  "v3": {
    "username": "simulator",
    "authPassword": "auctoritas", "privPassword": "privatus",
    "authProtocol": "MD5", "privProtocol": "DES",
    "contextName": "real-cisco-c6500"
  }
}
```

`src/snmp/AllVersionsTest.java` is a standalone harness that runs every operation
across all three versions against snmpsim (no Spring needed).

## Notes
- The device inventory is in-memory; the simulator devices are re-registered on every start.
- To share the lab on a LAN, allow inbound TCP 8080 (UI/API) and UDP 1161 (direct SNMP) in Windows Firewall.

## NETCONF (RFC 6241) lab

NETCONF is SNMP's configuration-focused counterpart: SSH transport (port 830,
encrypted by default), XML `<rpc>` messages, and a **transactional** config model
(edit a `candidate` datastore, `validate` it, then `commit` it to `running`
atomically — with `discard-changes` as the undo button). SNMP `SET` has none of
this; it writes straight to the live device with no draft and no rollback.

### 1. Start the NETCONF simulator (Docker)

```bash
docker run -d --name netopeer2 --restart unless-stopped -p 830:830 \
  sysrepo/sysrepo-netopeer2:latest
```

Default credentials: user `netconf`, password `netconf` (baked into the image;
key-based auth is also provisioned — see `/home/netconf/.ssh/authorized_keys`
inside the container — but the client here uses the simpler password auth).

### 2. One-time lab setup: install `iana-if-type` and open NACM

The image ships `ietf-interfaces` but not the `iana-if-type` identity module it
depends on for the `type` leaf, and NACM's `write-default` is `deny` (correct,
secure-by-default behavior — but it blocks every edit until you grant a rule).
Both are one-time fixes, done directly against the datastore (bypassing NETCONF,
since local `sysrepo` tools run with full authority):

```bash
# install the missing identity module (it ships inside the image's own test fixtures)
docker exec netopeer2 sh -c "sysrepoctl -i /opt/dev/sysrepo/tests/files/iana-if-type.yang"

# open NACM so the netconf user can read/write/exec (lab only -- don't do this on a
# real device; a production NACM config should grant narrow, specific rules instead)
cat > /tmp/nacm-open.xml <<'EOF'
<nacm xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-acm">
  <enable-nacm>true</enable-nacm>
  <read-default>permit</read-default>
  <write-default>permit</write-default>
  <exec-default>permit</exec-default>
</nacm>
EOF
docker cp /tmp/nacm-open.xml netopeer2:/tmp/nacm-open.xml
docker exec netopeer2 sh -c "sysrepocfg --edit=/tmp/nacm-open.xml -d running -m ietf-netconf-acm"
docker exec netopeer2 sh -c "sysrepocfg --edit=/tmp/nacm-open.xml -d startup -m ietf-netconf-acm"
```

After this, `ietf-interfaces` is genuinely readable/writable — e.g. adding a
loopback interface and committing it works end-to-end (see the UI's NETCONF
panel presets, or the curl examples below).

### 3. REST API

```
GET  /netconf/devices                              list NETCONF-managed devices
GET  /netconf/{id}/get-config?datastore=running    read a datastore (running/candidate/startup)
GET  /netconf/{id}/get                              read config + operational state
POST /netconf/{id}/edit-config?commit=true          apply <config> body to candidate, optionally commit
POST /netconf/{id}/edit-config-safe                 lock -> edit-config -> validate -> commit/discard -> unlock
POST /netconf/{id}/lock?datastore=candidate          reserve a datastore
POST /netconf/{id}/unlock?datastore=candidate        release a lock
POST /netconf/{id}/validate?datastore=candidate      check a datastore without applying it
POST /netconf/{id}/commit                            apply candidate -> running
POST /netconf/{id}/discard-changes                   throw away uncommitted candidate edits
POST /netconf/{id}/copy-config?source=&target=       replace target's contents with source's
POST /netconf/{id}/delete-config?target=             wipe a (non-running) datastore
```

The web UI's **NETCONF** panel drives all of these against the seeded `netopeer2`
device, with config-XML presets (add/describe/delete a loopback interface) and
pretty-printed XML replies.

### 4. Example: the transactional safety net

```bash
# edit the CANDIDATE only (commit=false) -- running is untouched
curl -X POST "http://localhost:8080/netconf/netopeer2/edit-config?commit=false" \
  -H "Content-Type: application/xml" \
  --data-raw '<interfaces xmlns="urn:ietf:params:xml:ns:yang:ietf-interfaces">
    <interface><name>loopback99</name>
      <type xmlns:ianaift="urn:ietf:params:xml:ns:yang:iana-if-type">ianaift:softwareLoopback</type>
      <enabled>true</enabled></interface></interfaces>'

curl "http://localhost:8080/netconf/netopeer2/get-config?datastore=candidate"  # loopback99: present
curl "http://localhost:8080/netconf/netopeer2/get-config?datastore=running"   # loopback99: absent

# undo the draft -- running was never affected
curl -X POST "http://localhost:8080/netconf/netopeer2/discard-changes"
```

### Notes
- Only one NETCONF device (the netopeer2 lab) is wired up today, hardcoded in
  `NetconfController` — no inventory/registration yet (unlike the SNMP side's
  `DeviceRegistry`). A real device is added the same way: point a `NetconfDevice`
  at its host/port/credentials; the client code is transport-only and doesn't
  change per vendor.
- The lab uses `AcceptAllServerKeyVerifier` (trust any SSH host key) and a
  password. Neither is appropriate for a real device: verify the actual host key,
  and prefer SSH key-based auth for unattended automation.
- To enable NETCONF on a **real** device, it must be turned on via the device's
  own CLI first (e.g. Cisco IOS-XE: `netconf-yang` / `netconf-yang ssh`; Juniper:
  `set system services netconf ssh`) — port 830 won't be listening otherwise.
