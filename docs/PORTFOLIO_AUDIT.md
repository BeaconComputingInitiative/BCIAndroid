<!-- BEACON COMPUTING INITIATIVE | Developer Seal: CJB-DEV-2026 | Chance Joaquin Boone -->
<!-- SHA-256: see docs/HYGIENE_SHA256SUMS from the repository root -->

# Commit and portfolio audit: BCIAndroid

Reviewed October 26, 2026. Scope: all commits reachable from the available branches at the audit snapshot. GitHub commit/file-diff responses and complete default-branch trees were retrieved.

- Default branch: `main` at `57291e40c0f56925913b502c4d422edea5eb715c`.
- Default history: **40 commits**; all available branch histories: **183 distinct commits within this repository**.
- Available branches: **12**. Shared ancestry across repositories is counted separately.

## Consolidation decision

Apply the presentation and source-boundary cleanup as one squash commit. Preserve published ancestry and release links. Matching commit messages do not prove redundant implementation; matching trees can be normal merge snapshots. Historical fixes and useful feature changes remain available in Git.

## Identical whole-tree snapshots

These commits expose identical tracked trees. They are not duplicate runtime files and do not warrant deleting published history.

- [`57291e40c0`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/57291e40c0f56925913b502c4d422edea5eb715c), [`c8da76f096`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/c8da76f096d23fa35c7d6718365e72da72754a3d)
- [`be02712d4c`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/be02712d4c2995047d74143038eb1292449fb8a9), [`4f33773924`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/4f33773924b83905a9dec26bf988c8059fc8628c)
- [`31eb9f527e`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/31eb9f527e0def49c428145b49f0b46725376e92), [`334276bf43`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/334276bf4300f09eeaef380b73f54318b2a6e76e)
- [`ec1d557476`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/ec1d55747651745e77c8e033793bb0bc765e001f), [`6eea9a9341`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/6eea9a9341f565e79311d220700fe339e98f3035)
- [`cf4dad3e0b`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/cf4dad3e0b5e95c6ad82a164a8fe271b34008123), [`34135dec31`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/34135dec313def30b1d85caaf8596503d235419d)
- [`3bf084a16c`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/3bf084a16c814a172a7ad8d928ddf7bee66b4833), [`5da5579401`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/5da55794016bc8056178dbc460c467cba7ddb016)
- [`11608f3bba`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/11608f3bba52254a137795a36969d360c078d202), [`857e190e5b`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/857e190e5b25a9b7ed2c291c7113581e6f5a1537)
- [`a0afcc2ce6`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/a0afcc2ce6f1a59f44051d50be17172e690c92ed), [`5a5a04e276`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/5a5a04e27648d2db9d9fd73afcaec665f87e71c4)
- [`94ef6bfb60`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/94ef6bfb603448af8d069a9215961cdc08a9cd8b), [`ad00f8d677`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/ad00f8d677e7b517b88769c3cb96685d988f258a)

## Branch snapshot

| Branch | Reviewed head |
| --- | --- |
| `bci/alpha12-live-integration` | `5a5a04e27648` |
| `bci/alpha12-modular-responsiveness` | `a3d2b6a68016` |
| `bci/alpha13-spectral-lock` | `857e190e5b25` |
| `bci/alpha14-transcription-speed` | `5da55794016b` |
| `bci/alpha15-native-engine` | `34135dec313d` |
| `bci/alpha17-oboe-opengles` | `6eea9a9341f5` |
| `bci/alpha18-live-transcription` | `334276bf4300` |
| `bci/alpha19-module-contracts` | `4f33773924b8` |
| `bci/alpha20-verbose-core-contract` | `c8da76f096d2` |
| `bci/render-live-deploy` | `ad00f8d677e7` |
| `bci/repository-hygiene-2026-10-26` | `88e2c044d6fb` |
| `main` | `57291e40c0f5` |

## Commit index

The ledger records every reviewed commit, its message, parent count and tree fingerprint. File-change inventories appear in the GitHub commit links. This is a structural redundancy and presentation audit, not a certification of every historical algorithm, dependency, dataset, or hardware behavior.

- [Commits 1](history/commits-01.md)
- [Commits 2](history/commits-02.md)
- [Commits 3](history/commits-03.md)

## Validation boundary

Text source snapshots used locally were checked against their remote Git blob IDs. Binary assets, submodule contents and datasets were inventoried by Git object identity rather than executed or semantically audited. Historical commit patches were retrieved for redundancy analysis; no claim is made that every line has passed a fresh runtime review. Device tests, production deployment and trained-model quality are outside this cleanup.

Release if and only if the exact source commit passes its documented acceptance gates. Existing releases and tags are retained; README status tables distinguish stable references, previews and incomplete recovered source.

## Portfolio outcome

README now describes Alpha 20 Oboe/OpenGL/live-caption ownership. Alpha 16 AudioRecord/Canvas notes moved to a clearly historical guide. Tags and published APK links remain intact.
