# YpsoPump laboratory module for AndroidAPS 3.4.2.6

**Current state: scaffolding only. Not a functional pump driver.**

This module is reserved for experimental, off-body bench testing of a mylife YpsoPump. It is not registered as an AAPS pump plugin and contains no BLE command dispatch or insulin administration code yet.

Development milestones:
1. Compile and validate module structure using AAPS 3.4.2.6 dependencies.
2. Review research references (SandraK82/ypsopump-research), including the more complete standalone ypsopump-test implementation.
3. Port BLE framing, encryption, counter synchronization and response validation with deterministic tests.
4. Implement all supported pump command categories with explicit status for each command: unimplemented, simulated, verified in tests, and verified on an off-body pump.
5. Implement interlocks and audit logs before any pump-writing bench tests.
6. Create a separately identifiable Android app and test without touching a therapeutic AAPS installation.

A command enum listing all 33 known BLE characteristic indices does not mean all commands are implemented or verified. No code from this repository is approved for clinical insulin delivery.
