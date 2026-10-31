# Monero wallet native library

Radar uses `im.molly:monero-wallet-sdk:1.0.0`. The arm64 `libmonero_wallet.so`
in `../jniLibs/arm64-v8a/` is built from that SDK's v1.0.0 source with
`monero-wallet-sdk-1.0.0-wallet-balance.patch` applied. The patch initializes
the native wallet's balance-change flag before its first refresh.

SHA-256 of the checked-in arm64 library:

```
0df920c6256628041d475a390772eaa1169a47db0284020df662f654143558e8
```

Only arm64 has the patched binary. Radar hides Monero on other Android ABIs
until equivalent binaries are built and tested. The unmodified SDK AAR still
provides the rest of its Java and native dependencies. Release builds also
require Radar-managed Monero node endpoints from Remote Config.

The SDK source and GPL-3.0 license are at
https://github.com/mollyim/monero-wallet-sdk/tree/v1.0.0.
