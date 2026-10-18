# YpsoPump authorization and cryptographic integration contract

Status: research/engineering design only. No authorized physical pump session exists in this branch. Do not use for insulin treatment.

## Trust boundaries and evidence

- A BLE bond proves neither app-layer identity nor authorization to administer insulin.
- An X25519 shared secret alone does not authenticate the pump; an authenticated binding to the intended pump and app is necessary.
- Play Integrity attestation by itself is not a reusable credential for AndroidAPS. A legitimate provider must independently authorize this application and the intended pump.
- The published community integrations describe ProRegia involvement and time-bounded key material. Treat reported validity periods as unverified for a particular device, firmware, and session until independently confirmed.
- Do not extract third-party application keys/tokens, impersonate CamAPS or mylife, or bypass app/device integrity checks.

## Required manufacturer-authorized integration

1. Officially documented onboarding and authorization of the AndroidAPS client, including explicit allowed operations and pump identity binding.
2. Trusted identity material: authenticated pump identity, verified authorization response, origin/purpose binding, expiration, and revocation semantics. Any provider network transport requires standard certificate validation and an approved API contract.
3. A manufacturer-approved key agreement transcript with validated test vectors; reject missing or mismatched transcript bindings. Do not accept externally supplied raw keys as proof of authorization.
4. Separate keys and nonces per direction. Nonces must not repeat under the same encryption key. Authenticated counters and message types must be part of the authenticated transcript / AAD where the protocol requires them.
5. Fail closed on integrity error, replay, lost connection, authorization expiry, counter desynchronization, or unexpected device identity. Invalidate sessions, and never infer successful administration from a BLE acknowledgement alone.
6. Independent validation on a disconnected off-body test pump before any consideration of therapy, plus safety review of bolus and basal command semantics and failure recovery.

## Implemented laboratory components

- HChaCha20Lab: public-vector tested primitive.
- XChaCha20Poly1305Lab: offline-only AEAD combining HChaCha20 with the runtime JCA ChaCha20-Poly1305 provider. Provider support on target Android versions is unverified. This does **not** validate interoperability with the pump.
- YpsoOfflineChallenge: parsing of a research-described handshake fixture only.
- YpsoAuthenticatedSequenceGuard / YpsoVerifiedReceivePipeline: offline monotonic counter and verifier-first interface; no configured device authenticator.
- AuthorizedAuthenticationGateway: unconfigured and intentionally incapable of granting pump trust.
- YpsoPumpPlugin: authentication and delivery remain unavailable.

## Acceptance criteria before enabling read-only authenticated status

- Manufacturer-authorized credentials and authorization contract available.
- Peer identity and completed transcript independently verified, including negative tests and session expiry/revocation.
- Crypto implementation matches published and independently captured non-secret fixture vectors, including AEAD tampering tests and deterministic protocol transcript tests.
- BLE framing, nonce construction, counter handling and key lifecycle match verified pump firmware behavior.
- No synthetic status or safety-critical telemetry is surfaced as live pump data.

This document is not a security certification or medical device validation.
