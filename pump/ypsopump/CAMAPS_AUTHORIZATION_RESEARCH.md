# CamAPS FX authorization research — non-secret architecture inventory

Status: preliminary static inventory only (18 October 2026). Source is a user-provided, CamDiab-authorized decompilation package of CamAPS FX 1.4(194).101 / versionCode 551. This note contains no proprietary bytecode, credentials, tokens, server addresses, or reverse-engineered authentication messages. No manufacturer-authorized AndroidAPS client enrollment has been established.

## Source quality

- Archive description states 21 APK files, 24,990 classes and 168,008 methods.
- Output consists of **analytical pseudo-Smali**, not original Java/Kotlin source or directly rebuildable Smali. Native components require separate review.
- Obfuscation and generated protobuf/gRPC classes limit what can be concluded from names alone.
- CamDiab's stated authorization to research compatibility is not, by itself, evidence that a ProRegia or Ypsomed server has provisioned AndroidAPS as an authorized client.

## Confirmed class/interface names in the archive index

Within `com.proregia.probluetooth.backend.proto`:
- `KeyExchangeProto`
- `EncryptKeyGrpc`, `EncryptKeyRequest`, `EncryptKeyResponse`
- `EncryptCommanderKeyGrpc`
- `DeviceIdentifier`
- `KeyAttestation`, `KeyAttestationForJwt`, `KeyAttestationResponse`
- `JwtKeyIdentifier`

These identifiers support the existence of backend key and attestation **interfaces**. They do not establish payload semantics, authorization entitlements, production endpoints, key lifetimes, or any right to impersonate an existing app.

## Requirements to unblock legitimate authorization

1. Get CamDiab/Ypsomed/ProRegia to explicitly specify which entity owns the client-onboarding and authorization service.
2. Request a written test-client onboarding contract: dedicated AndroidAPS app identity, development environment, test device enrollment, authorized operations, API/protobuf contract, credential issuance/revocation, and support contact.
3. Request non-secret conformance vectors for pump identity binding, handshake transcripts, X25519/XChaCha20/other primitives actually used, nonce/counter construction, expiry, and error codes.
4. Build a provider implementation from the documented contract without extracting Play Integrity tokens, production session keys, device attestation keys, or CamAPS private application material.
5. Verify identity/authorization separately from transport and cryptographic primitives. Keep pump initialization and dosing disabled until approved on-device testing and an independent safety assessment.

## Security tests

- Wrong pump or app identity, stale authorization, revoked enrollment.
- Modified encrypted payload, altered AAD, wrong direction counter, replay, out-of-order message and nonce reuse.
- Reconnect after power loss, missed acknowledgement, BLE link loss, transport timeout, clock rollback, and any discrepancy between commanded and confirmed delivered insulin.

## Implementation boundary

Current `AuthorizedAuthenticationGateway` is intentionally unconfigured; `YpsoPumpPlugin` remains uninitialized and fail-closed. Do not treat a successful APK build, BLE GATT connection, or a test-only crypto round-trip as physical device authorization.
