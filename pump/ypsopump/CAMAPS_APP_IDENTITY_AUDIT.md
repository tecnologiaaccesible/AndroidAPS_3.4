# CamAPS application identity / attestation — static evidence audit

Research only; no production credentials, tokens, private keys or proprietary code included.
Source reviewed: user-supplied CamAPS FX 1.4(194).101 disassembly archive, supplied for interoperability research.

## What the archive confirms

- Package ID shown by archive metadata: `com.camdiab.fx_alert.mmoll` (the mmol/L variant, not evidence of every supported CamAPS variant).
- The `com.proregia.probluetooth.backend.proto` tree contains generated-looking protobuf/gRPC interfaces including:
  - `NonceForKeyAttestationGrpc`
  - `NonceForKeyAttestationJwtGrpc`
  - `ValidateKeyAttestationGrpc`
  - `ValidateKeyAttestationJwtGrpc`
  - `KeyAttestation`, `KeyAttestationForJwt`, `KeyAttestationResponse`
  - `DeviceIdentifier`, `EncryptKeyRequest`, `EncryptKeyGrpc`, `EncryptCommanderKeyGrpc`
- These names suggest that nonce issuance and key-attestation validation are represented in the client package. They do **not** prove which RPCs the running app invokes, which attestation mechanism validates them, what the server's policy checks, or whether an independent AndroidAPS app could enroll.
- This archive is **pseudo-Smali analytical disassembly**, not rebuildable Smali or original source. File names do not establish verified request/response semantics.
- Searching archive class paths for literal `IntegrityManager` and `IntegrityToken` yielded no matching *.smali file names. This is *not evidence* that Play Integrity is absent; usage may be obfuscated, delegated to native code or reached indirectly.

## Client authentication hypotheses (unverified)

- Play Integrity integrity-token binding to application package name and signing certificate.
- A separate provider-issued enrollment or signed identity authorization.
- Device key attestation or challenge-response tied to an enrolled client.
- Multiple mechanisms combined in a server-side policy.

## Required evidence before concluding what the server accepts

1. Map concrete call sites that invoke the identified gRPC stubs, without extracting actual tokens or request secrets.
2. Match non-sensitive protobuf field types and error-code paths to the corresponding transport states.
3. Establish whether attestation covers package/signature identity, server nonce, request body, pump identifier, or a combination; do not assume one from identifiers alone.
4. Obtain a developer/test-client contract from the authorizing service owner, or a designated authorized interoperability environment.
5. Use independent AndroidAPS identity; reject strategies that reuse CamAPS signatures, tamper with Play Integrity, relay tokens, or impersonate production app metadata.

## Security decision / acceptance boundary

- `AuthorizedAuthenticationGateway` remains intentionally unconfigured and the YpsoPump pump adapter uninitialized.
- Do not map an app integrity verdict, bare successful gRPC HTTP status, or BLE connection into an authenticated pump session.
- No delivery commands are enabled until manufacturer/authorized-service-recognized pump/app identity and an authenticated encrypted session are independently demonstrated and safety tested.

**Current result:** Identified concrete generated interfaces and hypotheses for targeted static call-graph analysis; **no confirmed server app-identity policy** and **no real YpsoPump authorization**.
