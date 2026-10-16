# YpsoPump V05.02.04 — observed GATT vs research (evidence ledger)

Updated 2026-10-16. Scope: **read-only, off-body lab observation and static analysis**.
This document does not authorize or implement actual YpsoPump commands, cryptographic
authentication, subscriptions, or insulin delivery.

## Sources and their evidential status

- **O1 — Direct observation:** Android GATT metadata inventory pasted by the tester
  from YpsoPump Lab for firmware master `V05.02.04`, base `1.2`, configuration
  `1.7`, history `1.4`; Bluetooth six-digit passkey bonding completed.
  Metadata enumerates 7 services / 56 characteristics. Full 56-line per-characteristic
  source dump is **not stored here**; only the observed subset reproduced below.
- **R1 — Third-party research (not a manufacturer's protocol specification):**
  https://github.com/SandraK82/ypsopump-research/blob/main/docs/02-ble-protocol.md
- **R2 — Third-party key exchange analysis (not independently verified on this pump):**
  https://github.com/SandraK82/ypsopump-research/blob/main/docs/04-key-exchange.md
- **R3 — Third-party list of 33 named indexed operations:**
  https://github.com/SandraK82/ypsopump-research/blob/main/aaps-driver/src/main/kotlin/app/aaps/pump/ypsopump/comm/YpsoCommandCodes.kt
- **C1 — This branch's read-only metadata enumerator:**
  `src/main/kotlin/app/aaps/pump/ypsopump/ble/YpsoGattInventory.kt`.

**Evidence labels**: OBSERVED = GATT advertising/discovery metadata collected
from a physical, off-body pump; RESEARCH = assertion in external source;
INFERENCE = plausible mapping without verification; UNKNOWN = no justified role.

## Service-to-research-family comparison

| Observed service UUID (O1) | Characteristics | Confirmed meaning | Candidate research family | Confidence |
|---|---:|---|---|---|
| `00001800-0000-1000-8000-00805f9b34fb` | 4 | GAP UUID, standard Bluetooth SIG service | Not a proprietary command family | OBSERVED identity |
| `00001801-0000-1000-8000-00805f9b34fb` | 1 | GATT UUID, standard Bluetooth SIG service | Not a proprietary command family | OBSERVED identity |
| `fb349b5f-8000-0080-0010-0000adde0000` | 6 | Manufacturer-specific; contains advertised writable `...b214...` | Base/authentication candidate | INFERENCE only |
| `fb349b5f-8000-0080-0010-0000dec00000` | 25 | Manufacturer-specific; individual operations not validated | History candidate (16 research indices) | INFERENCE only |
| `fb349b5f-8000-0080-0010-0000efbe0000` | 10 | Manufacturer-specific; individual operations not validated | Settings candidate (5 research indices) | INFERENCE only |
| `fb349b5f-8000-0080-0010-0000feda0000` | 6 | Manufacturer-specific; includes READ/NOTIFY and NOTIFY characteristics | Control/status candidate (7 research indices) | INFERENCE only |
| `fb349b5f-8000-0080-0010-0000feda0002` | 4 | Manufacturer-specific auxiliary characteristics | UNKNOWN | UNKNOWN |

**Totals:** 7 services; 56 characteristics (4+1+6+25+10+6+4).
R1/R3 enumerate **33 logical research commands**, grouped as base 0–4 (5),
settings 5–9 (5), history 10–25 (16), control 26–32 (7).
Neither service sizes nor iteration order establish a command-to-characteristic
correspondence. In particular **25 characteristics are not 16 commands**, and
**10 characteristics are not 5 commands**.

## Directly observed per-characteristic evidence

UUIDs below have the same prefix `669a0c20-0008-969e-e211-` unless otherwise written.

| Parent service | Characteristic suffix / full UUID | GATT properties directly observed | Research claim vs observation |
|---|---|---|---|
| `adde0000` | `fcbeb0147bc5` | READ | R1 identifies a pump-specific UUID; precise semantic role unknown |
| `adde0000` | `fcbeb1147bc5` | READ | No individually established semantic role |
| `adde0000` | `fcbeb2147bc5` | WRITE **with response** | Authorization **candidate** by research and naming, **not** verified by writing |
| `adde0000` | `fcbed63b7bc5` | READ | Unknown role |
| `adde0000` | `fcbee23b7bc5` | READ | Unknown role |
| `adde0000` | `fcbee23c7bc5` | READ | Unknown role |
| `feda0000` | `fcbee08b7bc5` | READ | Unknown role |
| `feda0000` | `fcbee18b7bc5` | WRITE **with response** | Unknown role; NOT used |
| `feda0000` | `fcbee28b7bc5` | READ + NOTIFY | Status/notification candidate; content unknown |
| `feda0000` | `fcbee38b7bc5` | WRITE **with response** | Unknown role; NOT used |
| `feda0000` | `fcbee48b7bc5` | READ | Unknown role |
| `feda0000` | `fcbee58b7bc5` | NOTIFY; 1 descriptor | Notification candidate; payload unknown |
| `feda0002` | `fcff0000000a` | READ | R1 calls this Data Characteristic A (Read): property consistent, semantics unknown |
| `feda0002` | `fcff0000000b` | **WRITE with response** | **Discrepancy:** R1 calls Data Characteristic B (Read), observed property is WRITE, not READ |
| `feda0002` | `fcff0000000c` | READ | Not listed in R1 table; purpose unknown |
| `feda0002` | `fcff000000ff` | READ | **Discrepancy/ambiguity:** R1 calls it Control Characteristic; this firmware advertises READ only |
| `00001801` | `00002a05-0000-1000-8000-00805f9b34fb` | INDICATE; 1 descriptor | Standard Service Changed characteristic; NOT an insulin event |
 
The other 25 `dec00000` and 10 `efbe0000` characteristic UUIDs are not
reproduced in this ledger. Do **not** fabricate entries or exact command
assignments for them. The physical inventory should be preserved as a separate,
redacted raw text attachment before individual classification.

**Three observed characteristics support asynchronous indications/notifications**:
standard `2A05` (INDICATE), `...e28b...` (NOTIFY), `...e58b...` (NOTIFY).
The latter two are not evidence that authenticated notifications have been
successfully subscribed to or decoded.

## Research contradictions and applicability limits

1. **Advertised service `669a0c20-...-ffffffffffff` / generic service
   `669a0c20-...-eeeeeeeeeeee`** in R1: neither UUID was included among
   O1's *discovered GATT service UUIDs*. Advertisement/scan-filter UUIDs do
   not have to be among discovered services; consequently this is an
   **unresolved distinction**, not proof either source is wrong.
2. **Characteristic `...fcff0000000b`:** R1 describes READ, O1 reports
   WRITE with response. Direct GATT properties for this specific firmware
   take precedence over the generalized research description.
3. **Characteristic `...fcff000000ff`:** R1's label “Control” does not
   establish write capability; O1 reports READ only.
4. **33 indexed commands vs 56 characteristics:** different abstraction
   layers; there is no proven numeric bijection. R3's names do not verify
   access rights, encryption format or payload interpretation.
5. **App-layer authentication:** Android passkey-based bonding is confirmed,
   but no authenticated app session is. R2 asserts a server-assisted key
   exchange and protected device credentials; this has not been executed
   or independently validated for firmware V05.02.04.
6. **Encryption and packet structure:** XChaCha20-Poly1305, nonce layout,
   counters, fragmentation and key persistence are RESEARCH only here;
   there are **no captured/decrypted protected packets** validating them.
   R2's illustrated 116-byte subfield diagram is a *reconstruction*, not
   an observed cryptographic parsing result.

## Command evidence policy

`YpsoProtocolCatalog.kt` contains an **offline research index only**:
all 33 entries remain RESEARCH_ONLY; none may be dispatched.
`canDispatchCommand(...)` must remain false. GATT metadata access does
not prove authorization to read sensitive data, write settings or deliver
insulin. No writes, notifications, impersonation or key retrieval were
performed for this mapping.

## Next evidence-gathering milestones (no physical control)

1. Keep this mapping reviewable and preserve a **redacted complete GATT
   inventory** (UUID / properties / descriptor counts; exclude pump serial,
   MAC, keys and sensitive values) for independent offline comparison.
2. Build a deterministic offline checker for **service totals, read/write
   metadata consistency and conflicts** between O1 and R1; static data only.
3. Request manufacturer-authorized documentation/partner integration for
   an actual authenticated session; do not infer or circumvent app attestation.
4. Require separate safety engineering, clinical oversight and validation
   before a real pump-control driver or any insulin-changing command.

## Non-goals

No PSK extraction, attestation bypass, cryptographic proxying, pump write,
therapy calculation or unvalidated use on a person.
