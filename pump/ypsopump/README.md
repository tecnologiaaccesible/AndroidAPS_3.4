# YpsoPump Lab — AndroidAPS 3.4.2.6 (experimental)

## Implemented in this branch

The module is registered in `settings.gradle` and linked to the `:app` build.
It contributes a **GENERAL** plugin, **YpsoPump Lab (solo lectura)**, visible as a separate AAPS tab.
It is **not** registered as an AAPS Pump driver.

The first integration provides:
- Bluetooth LE scanning for devices advertising `YpsoPump_` or containing `YpsoPump`.
- Manual selection of a discovered device, GATT connection and service discovery.
- Read-only GATT characteristic reads: manufacturer, model, serial, firmware, software and up to four software-version characteristics.
- BLE permission handling (Android 12+), disconnect button, automatic scan timeout and descriptive, accessible text/buttons.
- Unit tests for device-name filtering and read-only UUID mappings.
- CI validation of the module and Kotlin compilation of the AAPS app.

BLE characteristic UUIDs were sourced from the research project's `ypsopump-test` module:
https://github.com/SandraK82/ypsopump-research
Compatibility with other pump firmware has **not** yet been verified on a real device.

## Not implemented / not verified

- Full pump pairing and key exchange, encrypted BLE sessions and counter synchronisation.
- Reading authenticated pump status/history, settings or basal profiles.
- Any pump-write operations: boluses, temporary basal changes, basal profile changes or setting changes.
- Interoperation with CamAPS; do not connect this experimental build to a therapeutic pump.
- End-to-end AAPS pump-driver interface, pump synchronisation or autonomous insulin dosing.
- Safe independently installable test APK; this branch currently uses the standard AAPS application identity.

The source is integrated, but no completed bench validation or application build has yet been performed.
To check the current integration, launch `AAPS CI` from the branch `ypsopump-lab-3.4.2.6`
with AAPS version `3.4.2.6` and build variant `fullDebug`. 
CI runs the module tests and `:app:compileFullDebugKotlin`; it does not publish/install the APK.

## Safety and next validation gate

Only connect a confirmed off-body test pump and start with read-only diagnostics.
Do not use this experimental branch for diabetes treatment.
Adding actual pump-control commands requires independently verified encodings,
authenticated communications, counter persistence, idempotency and safety interlocks.
