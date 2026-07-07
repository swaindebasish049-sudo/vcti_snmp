# VCTI SNMP Lab (EMS)

A learning/lab EMS that manages simulated Cisco devices over SNMP (v1/v2c), built with Spring Boot + SNMP4J, plus an snmpsim-based device lab that the whole team can share.

## Repository layout

| Path | What it is |
|------|-----------|
| `ems/ems/` | Spring Boot EMS application (Java 17, SNMP4J 2.8.15) |
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
