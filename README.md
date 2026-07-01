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
  tandrup/snmpsim
```

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

## Notes

- SNMPv3 is not implemented yet (v1/v2c only).
- The device inventory is in-memory; the simulator devices are re-registered on every start.
- To share the lab on a LAN, allow inbound TCP 8080 (UI/API) and UDP 1161 (direct SNMP) in Windows Firewall.
