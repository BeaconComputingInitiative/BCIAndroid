<!-- BEACON COMPUTING INITIATIVE | Developer Seal: CJB-DEV-2026 | Chance Joaquin Boone -->
<!-- SHA-256: see docs/HYGIENE_SHA256SUMS from the repository root -->

# BCIAndroid: reviewed commits 3

Each row links to the retrieved file-level diff. A repeated title is not sufficient evidence for removing a change. Binary changes and dataset content were inventoried, not executed or semantically reviewed.

| Commit | Change | Files | + / − lines | Tree |
| --- | --- | ---: | --- | --- |
| [`78a7071fc3`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/78a7071fc3fe98ce8ac5158268b2cc6231bc5eef) | Separate first-frame timeout from runtime capture stall | 1 | 31 / 1 | `05adc821b6a7` |
| [`b414ffe734`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/b414ffe7341e14f0006097b05f0be39a75f02326) | Document baseline priority and lazy Whisper warmup | 1 | 8 / 0 | `c632950d0fa1` |
| [`ce79f28dca`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/ce79f28dcaed82eb992e97654b3239ff712bc449) | Define baseline priority over Whisper warmup | 1 | 7 / 2 | `bbe5637ba7d5` |
| [`bc5526366f`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/bc5526366f9a07a29d9ebe9a17ef97cee9446e3c) | Defer Whisper warmup away from baseline startup | 1 | 19 / 1 | `8376d34c1502` |
| [`3afc563118`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/3afc563118540cfd1a927c40050d1d66544ae07d) | Make Alpha 20 latest and retire prior three releases | 1 | 14 / 0 | `f4501f622aab` |
| [`0529a83426`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/0529a834262e58a0bd87dae0a06127b471a45353) | Validate core contract in gateway CI | 1 | 5 / 1 | `e889620058e0` |
| [`5e628d1ddb`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/5e628d1ddbfd46603caecbe876af6d5a06fbd14a) | Gate Alpha 20 on verbose contracts and robust error handling | 1 | 19 / 8 | `72bcad3b6037` |
| [`fbd45ed870`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/fbd45ed8704ade4bcd5b2c0001eccd0bd11015f8) | Enforce verbose core contract and robust error handling in CI | 1 | 97 / 5 | `a7bcfac4d986` |
| [`2637f5f19e`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/2637f5f19e3f818728a33ee76eaf19930566cce3) | Surface Alpha 20 robust root-cause diagnostics | 1 | 10 / 1 | `54887918c7a5` |
| [`f9a5537236`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/f9a5537236d112c4105eba4760539c4405823ea0) | Bump BCIAndroid to Alpha 20 | 1 | 2 / 2 | `976e10445916` |
| [`233beaa350`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/233beaa350ec25da1951635d004e45c2ecaa343b) | Test robust error root-cause preservation and coalescing | 1 | 161 / 0 | `b447e80558e6` |
| [`0c9a0b033d`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/0c9a0b033dd5788c41aab1927e1ac32b2b05ae2f) | Document verbose BCI Core Contract v1 | 1 | 379 / 0 | `9935207874d6` |
| [`8f464393b4`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/8f464393b404dcd49fc45cb8a757d1dd350e3612) | Add authoritative machine-readable BCI Core Contract | 1 | 333 / 0 | `6f2b278ce4de` |
| [`57f8346bbd`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/57f8346bbd15927383407139821e0a7156c43ebe) | Add robust error handling loop across runtime modules | 1 | 832 / 18 | `46d51d81c87b` |
| [`e20a2fb56d`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/e20a2fb56d6fb84716167d4e2024e96ad0ba5bb5) | Remove superseded runtime health supervisor name | 1 | 0 / 303 | `aa26b7058e9a` |
| [`33c1cda543`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/33c1cda5430c1de55bebe7458e0dc2303dce99fd) | Rename runtime supervisor to robust error handler | 1 | 303 / 0 | `204ed376c32c` |
| [`29ebe22d62`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/29ebe22d62b7159be1bd5a0538baf87eeae52a97) | Add centralized runtime health supervisor | 1 | 303 / 0 | `d796d3874c68` |
| [`ad00f8d677`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/ad00f8d677e7b517b88769c3cb96685d988f258a) | Keep Bun shebang ahead of BCI source header | 1 | 1 / 1 | `a0f0df853a16` |
| [`e584da955b`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/e584da955be39c3941ccfacefde48f7a189dbd08) | Run gateway validation on deployment pull requests | 1 | 6 / 0 | `305217ce32c4` |
| [`792fddc2bd`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/792fddc2bdc4215698f7498e5712f7cb2a3ac8e5) | Add Render blueprint for BCIAndroid live gateway | 1 | 29 / 0 | `0c18d8310a65` |
| [`a1d966c9df`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/a1d966c9df70a99af26298d5df2a249e8ad5e24b) | Add gateway source integrity verification | 1 | 12 / 0 | `d3e18b70d8db` |
| [`a7c3ea71b2`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/a7c3ea71b27f773bed1fdeceee167cf72661270a) | Prepare live gateway for Render deployment | 1 | 16 / 9 | `72069c883f6c` |
| [`88e2c044d6`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/88e2c044d6fbc576f0f4a69576f56a0b3377da38) | chore: document repository cleanup findings and prevent new source clutter | 6 | 155 / 1 | `e4fe81957458` |
