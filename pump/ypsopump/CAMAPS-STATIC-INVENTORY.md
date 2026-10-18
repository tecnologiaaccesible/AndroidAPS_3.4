# CamAPS FX offline static component inventory

Source: user-provided APKMirror .apkm package, CamAPS FX mmol/L 1.4(194).101, version code 551. This inventory is based on static printable DEX string extraction and APK entry names; no executable disassembly, login, key extraction, server calls, credential recovery, Bluetooth writes or physical pump commands.

## Confirmed presence in supplied artifact

- `base.apk` embeds `classes.dex`, `classes2.dex`, and `classes3.dex`.
- `split_config.arm64_v8a.apk` contains `libnative-lib.so`, `libsodium.so`, and `libsqlcipher.so`.
- DEX descriptor `Luk/ac/cam/ap/ypsomed_15x/YpsoAPI;`.
- DEX descriptor `Luk/ac/cam/ap/ypsomed_15x/ypsomed/YpsoCommandChars;`.
- Embedded string `YpsoPump Library Version 1.0.14`.
- DEX descriptors `Lcom/proregia/probluetooth/pro/ProBluetoothAdapter;` and `Lcom/proregia/probluetooth/pro/ProBluetoothGatt;`.
- DEX descriptors `Lcom/proregia/probluetooth/backend/proto/EncryptKeyGrpc;` and `Lcom/proregia/probluetooth/backend/proto/EncryptCommanderKeyGrpc;`.
- Embedded string `Backend Host: 'connect.cam.pr.sec01.proregia.io', Port: '443'`.
- DEX reference to `Lcom/goterl/lazysodium/Sodium;` and Google Tink `XChaCha20Poly1305Key`.

## Limits of inference

Presence of classes and symbols does not demonstrate a particular runtime call graph, protocol encoding, authentication token, or legitimate access to server-side secrets. No finding in this inventory supports bypassing authentication or sending real insulin-delivery commands.

## Development implications

1. Keep Android BLE bonding distinct from authenticated application sessions.
2. Confirm crypto algorithm interoperability offline using public vectors.
3. Keep physical dispatch disabled until authenticated sessions and therapy safety have been independently validated.
4. For remaining physical test time, capture read-only GATT properties, permitted firmware identifiers and diagnostic failure codes with identifiers redacted.
