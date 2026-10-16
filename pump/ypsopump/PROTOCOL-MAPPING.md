# YpsoPump V05.02.04 — evidence-based GATT mapping

Status: **offline analysis**, 2026-10-16. No insulin-control driver is implemented or enabled.
Sources:
- Real off-body pump inventory provided by the tester from YpsoPump Lab (bonded Android BLE device).
- https://github.com/SandraK82/ypsopump-research/blob/main/docs/02-ble-protocol.md
- https://github.com/SandraK82/ypsopump-research/blob/main/aaps-driver/src/main/kotlin/app/aaps/pump/ypsopump/comm/YpsoCommandCodes.kt
- https://github.com/SandraK82/ypsopump-research/blob/main/docs/04-key-exchange.md

## Observed service inventory

| Service UUID | Observed characteristics | Notes / confidence |
|---|---:|---|
| `00001800-0000-1000-8000-00805f9b34fb` | 4 | Standard GAP service ID; device information in this firmware is also exposed here |
| `00001801-0000-1000-8000-00805f9b34fb` | 1 | Standard GATT service; characteristic 2A05 has INDICATE |
| `fb349b5f-8000-0080-0010-0000adde0000` | 6 | Manufacturer-specific, resembles research base service |
| `fb349b5f-8000-0080-0010-0000dec00000` | 25 | Manufacturer-specific; likely history/operational data; command-to-UUID mapping unverified |
| `fb349b5f-8000-0080-0010-0000efbe0000` | 10 | Manufacturer-specific; likely settings; command-to-UUID mapping unverified |
| `fb349b5f-8000-0080-0010-0000feda0000` | 6 | Manufacturer-specific; status/control candidates; command-to-UUID mapping unverified |
| `fb349b5f-8000-0080-0010-0000feda0002` | 4 | Manufacturer-specific auxiliary service; purpose unverified |

**Total: 7 services, 56 characteristics**, consistent with the actual inventory; the research catalog's **33 indexed commands** does NOT claim there are 33 GATT characteristics.

## Direct observations relevant to protocol

- `669a0c20-0008-969e-e211-fcbeb2147bc5`: authorization-related UUID appears in `adde0000`, advertises WRITE, not READ. **No payload has been sent; no application-layer authentication proven.**
- `669a0c20-0008-969e-e211-fcbee28b7bc5`: READ + NOTIFY advertised under `feda0000`.
- `669a0c20-0008-969e-e211-fcbee58b7bc5`: NOTIFY advertised under `feda0000`, one GATT descriptor.
- `00002a05-0000-1000-8000-00805f9b34fb`: INDICATE advertised in standard 1801 service.
- A characteristic announcing READ, WRITE or NOTIFY does **not** establish that application-layer data are authorized, plaintext, valid, or safe to operate on.
- Bluetooth bonding by Android using a six-digit passkey has been confirmed. This is separate from the research project's server-assisted key exchange and application-layer encryption.

## Research command index catalog (not command implementation)

See `ble/YpsoProtocolCatalog.kt` for the 33 named command indices from the research enum. Each is **RESEARCH_ONLY** unless independently verified.
The original research groups command indices as:
- Base: 0–4
- Settings: 5–9
- History: 10–25
- Control: 26–32

**Do not assign individual command indices to service characteristic UUIDs by order alone.** The research names are a starting hypothesis; many services have extra manufacturer-specific UUIDs.

## Offline next gates before any protected communication

1. Build against AndroidAPS 3.4.2.6 and run complete test suite for the lab module.
2. Create synthetic, deterministic protocol fixture tests that validate message lengths and errors **without using real encryption keys or pump writes**.
3. Obtain an authorized, documented key-exchange mechanism from manufacturer or an integration partner; the research backend appears to use authenticated Play Integrity-based attestation and per-device protected keys.
4. Review safe behavior on BLE loss, restart, timeouts, replay counters and contradictory responses. Fail closed.
5. Review independent pump isolation and explicit approval before *any* insulin-changing tests on bench hardware.

This repository must not use extraction of keys, MITM interception, counterfeit attestation or unaudited insulin-delivery commands in a test marketed as working.
