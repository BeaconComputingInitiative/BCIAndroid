<!-- BEACON COMPUTING INITIATIVE | Developer Seal: CJB-DEV-2026 | Chance Joaquin Boone -->
<!-- SHA-256: see docs/HYGIENE_SHA256SUMS from the repository root -->

# BCIAndroid: reviewed commits 2

Each row links to the retrieved file-level diff. A repeated title is not sufficient evidence for removing a change. Binary changes and dataset content were inventoried, not executed or semantically reviewed.

| Commit | Change | Files | + / − lines | Tree |
| --- | --- | ---: | --- | --- |
| [`52e7cd60bd`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/52e7cd60bda1b3c700fb231166ca6fb0d6950fe5) | Protect quiet locked voice from dead-air classification | 1 | 16 / 2 | `d3510e233912` |
| [`6d21cd9b45`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/6d21cd9b45bf5d78134eeff2fba1625da8de5e6d) | Teach transcription to skip recorded dead air | 1 | 115 / 14 | `3c6a4bce04b0` |
| [`f9c35439fa`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/f9c35439fa90e3b0d02ab7289050d4b2577b4df4) | Make event recording voice-aware and activity-indexed | 1 | 37 / 3 | `e23e28f93e34` |
| [`ae10fa5c01`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/ae10fa5c015088b6d54a5be5f92fd476bef76fee) | Add voice-aware spectral isolation processor | 1 | 652 / 0 | `5c2558ebc0da` |
| [`6df7039c51`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/6df7039c511ecfa4d07f4661697a4eb54301f64b) | Add dead-air-aware transcription planner | 1 | 603 / 0 | `0721e7f8d099` |
| [`d8409b504e`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/d8409b504e2d3933ef25a5b3224a796c82656a5c) | Add intelligent recording activity timeline | 1 | 366 / 0 | `589a5569003f` |
| [`335c0b3832`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/335c0b38327bfda33e3690b6fa09cde1c36f07dd) | Align Android spectral lock continuity with BCIWhisper | 1 | 161 / 28 | `276423bf1968` |
| [`5da5579401`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/5da55794016bc8056178dbc460c467cba7ddb016) | Fix quality-gate constant scope | 1 | 16 / 18 | `982094454191` |
| [`691d275532`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/691d2755324d92a52211599ecc3c3bf5b424ae80) | Fix warm-context Kotlin return expression | 1 | 1 / 2 | `c6844b175883` |
| [`aa062f8bbd`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/aa062f8bbd74cd8b0f02ad6090ee17b9eabd9454) | Build and hash Alpha 14 transcription speed path | 1 | 4 / 3 | `260ed71bfae5` |
| [`f355f5b90f`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/f355f5b90f17410d346e61d3c23af0a181861ff6) | Bump BCIAndroid to alpha 14 | 1 | 2 / 2 | `cbfcb27dc378` |
| [`7df4c2aa51`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/7df4c2aa51dc24ec44b275c0b6e5435677c6c0a4) | Test adaptive transcription fast path | 1 | 133 / 0 | `af6d86df9963` |
| [`0d00d1825e`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/0d00d1825ed1970fb235eb971018559118930a1d) | Prewarm Whisper and prioritize exclusive transcription | 1 | 32 / 6 | `122b927e7de8` |
| [`1fad5f360a`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/1fad5f360a1057f139f925343d5dad3c2a6b47f3) | Optimize LPC and noise estimation for mobile transcription | 1 | 177 / 48 | `57056e646b1e` |
| [`9afe127ef7`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/9afe127ef734befa3c2734ea03f880813d7d6bb9) | Keep Whisper warm and bypass redundant reconstruction | 1 | 704 / 174 | `b01a8ecbb61f` |
| [`f87f7928fa`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/f87f7928fa0673569d95adeacd2fd37db8268981) | Add adaptive quality gate for fast transcription | 1 | 240 / 0 | `61d2976d1b25` |
| [`34135dec31`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/34135dec313def30b1d85caaf8596503d235419d) | Validate Alpha 15 native engine architecture | 1 | 16 / 5 | `58858be40775` |
| [`120650cc20`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/120650cc2062eff55b30886a79b3ab03de6f6595) | Surface Alpha 15 native transcription engine | 1 | 1 / 1 | `c388ce766501` |
| [`f55c995e79`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/f55c995e79eaed60068e3afe9733dd5bea519d35) | Bump BCIAndroid to alpha 15 native engine | 1 | 2 / 2 | `c3ea29d458be` |
| [`b136d6398b`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/b136d6398be67f47f52bf822284a9148b9d54c5a) | Remove legacy C Whisper JNI bridge | 1 | 0 / 97 | `18a492c633f3` |
| [`db14cd24ec`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/db14cd24ec966fd5e38c676da6dc10e917a47577) | Remove legacy Kotlin Whisper JNI surface | 1 | 0 / 22 | `5968d3c6a46a` |
| [`9853f23c59`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/9853f23c59d801f2e34a8683ae55ce79493b7495) | Make native engine the sole Whisper JNI implementation | 1 | 0 / 1 | `afcf1c6a56f5` |
| [`d19368b90c`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/d19368b90c39bffae65695cb838a9d6566173e0e) | Move Whisper ownership and resampling behind C++ engine | 1 | 37 / 176 | `d59ecc18b8bb` |
| [`43143605a6`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/43143605a6c5d78a0c5f4e33ec664bcd290b22e4) | Compile native transcription engine into Android library | 1 | 1 / 0 | `bff8d65106a5` |
| [`66dde71c62`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/66dde71c62bb09dc5835ab7e8769ceb23b374d40) | Add single Kotlin JNI transcription boundary | 1 | 100 / 0 | `d15717fd74f1` |
| [`fb4cbb1bae`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/fb4cbb1bae7bae67e546a16424b3b396a746d403) | Add native C++ transcription engine | 1 | 642 / 0 | `fe58944a1a66` |
| [`6eea9a9341`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/6eea9a9341f565e79311d220700fe339e98f3035) | Harden Oboe SPSC overflow ownership | 1 | 4 / 11 | `d01000fd501b` |
| [`f66d7540db`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/f66d7540dba21c6b60171a3fade3765c7213ae7c) | Tie OpenGL renderer to Android lifecycle | 1 | 24 / 0 | `36322a22ab79` |
| [`44e9445cc4`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/44e9445cc4498fdbadd5c2724b0dbc831dcf0573) | Validate and package Alpha 17 native audio GPU stack | 1 | 17 / 8 | `dc02bdfee380` |
| [`74c9882d84`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/74c9882d84a14e5311ad67567bccf024a0602ef7) | Declare OpenGL ES 2 requirement | 1 | 4 / 0 | `4c4043202924` |
| [`b7d5dce182`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/b7d5dce1820f03899f33a80822bbed064c62d91c) | Surface Alpha 17 Oboe and OpenGL architecture | 1 | 3 / 2 | `d6b476708a95` |
| [`a51e2dc647`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/a51e2dc647d2a3807fc6cf5f66e5eec795e43afb) | Batch OpenGL spectrum rendering without frame allocations | 1 | 279 / 177 | `9f69d2040387` |
| [`bbc711cfbd`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/bbc711cfbd8c3b481465f2ac8bb9d3fd347f6495) | Move spectrum rendering to OpenGL ES | 1 | 926 / 804 | `c93c316e845e` |
| [`8c07147241`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/8c071472419e1d619ec7bdd0015b55a996884bd1) | Move BCIAndroid microphone transport to Oboe | 1 | 118 / 138 | `2bd06db8d231` |
| [`1c924d80ed`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/1c924d80ed6f942366fc3c93c0d02fee27df946d) | Report Oboe stream activity to Android service | 1 | 15 / 0 | `8573ad8c8fba` |
| [`2d207d824b`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/2d207d824bfb114ab2710a26818b5b9890dbe29a) | Expose native Oboe stream activity | 1 | 1 / 0 | `1a9246e87bb4` |
| [`2c4b2f2f3c`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/2c4b2f2f3ce931f38fc350e28bb2734b65fad9e4) | Allow raw-processing control for Oboe session | 1 | 71 / 0 | `21aa34073e9e` |
| [`c0a4300f92`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/c0a4300f92b767f223af9d5091c7cc29c3c8e25d) | Link Oboe into native Android engine | 1 | 4 / 0 | `c75d374447e1` |
| [`d0d52f95c7`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/d0d52f95c7d9811a56bec36dc9b2a2591cb50237) | Enable Oboe Prefab for Alpha 17 | 1 | 8 / 3 | `b46d7006e068` |
| [`0ab9e1fd92`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/0ab9e1fd92b19bc2e2b7f96e3b4250ca750b4e01) | Add Oboe low-latency native input engine | 1 | 444 / 0 | `0a7eb9b7ba68` |
| [`43666dc356`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/43666dc356edc957eb5c2761bd7f78cef7f7ea46) | Add Kotlin surface for native Oboe capture | 1 | 34 / 0 | `c689d256dd02` |
| [`334276bf43`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/334276bf4300f09eeaef380b73f54318b2a6e76e) | Include live-caption probes in gateway integrity check | 1 | 2 / 0 | `486090384efb` |
| [`91cc2d4a5f`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/91cc2d4a5f47af70de057a46116263096c564823) | Verify authenticated caption publish and SSE fanout | 1 | 9 / 0 | `9f5b40acafba` |
| [`0a89b7a91f`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/0a89b7a91fa337fc45916c3ad7c84a959f99a906) | Add authenticated live-caption integration probe | 1 | 177 / 0 | `5d5c110c6dd6` |
| [`9b354994a1`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/9b354994a15b1dfe541e336a0b0801d9f055bf9d) | Serialize and atomically finalize Whisper model copy | 1 | 79 / 36 | `23f0ee5f5e32` |
| [`9cd930dc06`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/9cd930dc0687400bca7e618d82f71dbb6ec9e96d) | Expose live-caption health without masking audio state | 1 | 30 / 10 | `f2f7f4606b41` |
| [`a151faf2f9`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/a151faf2f9804463145119479ace17bbd16073fa) | Bound and keep alive public caption streams | 1 | 38 / 0 | `503fb037f425` |
| [`82712884c3`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/82712884c3d8db88ff1173eafd5a39f7fa1b39c1) | Keep live-caption failures isolated from audio | 1 | 0 / 6 | `8f0b66d263f6` |
| [`98c5972f4f`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/98c5972f4f4d477563cc2fc13c6f39f83efcf727) | Prevent stale live-caption workers after restart | 1 | 45 / 4 | `5c2d7b9d8a03` |
| [`8e9fb75748`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/8e9fb75748c9a67fc50c9263935b33dd2793ffae) | Document public audio and live caption paths | 1 | 22 / 8 | `d8598b472b01` |
| [`d00de3b0c3`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/d00de3b0c3861f0832df4909c06553408bd0804b) | Smoke-test public live caption endpoints | 1 | 11 / 0 | `188635b8fbb5` |
| [`957cbf2f93`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/957cbf2f93a1a9a3da6b273b98b416bc41a54b38) | Bump live gateway to 1.1.0 | 1 | 1 / 1 | `eb002e602180` |
| [`bc22c4f808`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/bc22c4f808994eefd9da0d6ed49b7fb08d5345a4) | Validate and package Alpha 18 live captions | 1 | 13 / 8 | `fde7dde425b1` |
| [`03880a3a20`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/03880a3a204032f442bff4a9e536858eeda7be12) | Bump BCIAndroid to Alpha 18 | 1 | 2 / 2 | `53d61baeac2b` |
| [`cc4fd728fb`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/cc4fd728fb6d9eace0ff7e66b3feeeaa8509aecc) | Surface Alpha 18 live caption telemetry | 1 | 6 / 3 | `6edc963a2985` |
| [`6070128e51`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/6070128e51270a83c69893871d244f8d86cdcb96) | Build public livestream player with live captions | 1 | 170 / 21 | `8b2df498927d` |
| [`a37e42dbe5`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/a37e42dbe5f6bcaf94a144fcb2dce8eb4d5b2dbd) | Expose live caption history and SSE endpoint | 1 | 196 / 0 | `5e9db40ab634` |
| [`1ef65a0c35`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/1ef65a0c352f0d6076f0bd9c6b9c631a0c51379d) | Add gateway transcript state and SSE fanout | 1 | 113 / 1 | `e8bce465fe1e` |
| [`b3da119ac8`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/b3da119ac8312a44a972052513a7124c0ee0ad59) | Wire rolling live captions into broadcast service | 1 | 95 / 4 | `d6afe07dc68c` |
| [`f2ab17af71`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/f2ab17af719477aea636b4597940ad812ab612cc) | Publish live transcript messages beside audio | 1 | 118 / 1 | `e548bcc1702c` |
| [`eec032e439`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/eec032e439ec8b7b06ee14741388597a4a7a41e4) | Harden rolling caption window scheduling | 1 | 25 / 44 | `f492d7b87956` |
| [`a57cae8bd4`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/a57cae8bd4fa18455faa5b0fd9653b6f357bb670) | Add low-latency PCM path for live captions | 1 | 239 / 0 | `8f7f8987478a` |
| [`fed4396e6c`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/fed4396e6cf4257a7cfbed522ef7008186e27830) | Test rolling transcript overlap de-duplication | 1 | 51 / 0 | `68e52176d762` |
| [`7567216ee4`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/7567216ee41efaf0c5d1ee08b7f21d0b327efe20) | Add bounded rolling live transcription worker | 1 | 604 / 0 | `4dd05819a462` |
| [`852318212f`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/852318212fcf954f54dd7b73bd7a59ab3861ff27) | Add overlap de-duplication for live captions | 1 | 145 / 0 | `57bfc28a198f` |
| [`4f33773924`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/4f33773924b83905a9dec26bf988c8059fc8628c) | Cancel stale Android compatibility builds | 1 | 4 / 0 | `e55350fc83df` |
| [`3e2e92b3c4`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/3e2e92b3c47e648281b14b5bf1c1a539b5f611a2) | Remove unnecessary gateway host ffmpeg install | 1 | 4 / 5 | `9b5fbf80d1ff` |
| [`ea7129e899`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/ea7129e89908a54e46083404d32d9789ce52ecd0) | Check live resampling at the correct module boundary | 1 | 9 / 2 | `e85279316881` |
| [`512e58fba2`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/512e58fba2ca56336b35aa823417e4cce455ced4) | Run cross-module contracts in gateway CI | 1 | 7 / 1 | `ee96bf02291b` |
| [`3178a51a15`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/3178a51a15c75196042eb2150333f9870187342e) | Gate Alpha 19 on source and APK module compatibility | 1 | 52 / 9 | `12858f936e52` |
| [`c6340293ef`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/c6340293eff3abb77170dac7ca93337d4409029f) | Surface Alpha 19 compatibility build | 1 | 1 / 1 | `c7851adc2bda` |
| [`110fd4a0f7`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/110fd4a0f7feba0c84175cc161034c83907bd7da) | Bump BCIAndroid to Alpha 19 compatibility build | 1 | 2 / 2 | `05872867d526` |
| [`a59c4e00c0`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/a59c4e00c0eda44c2291237f04c67d8bf759d21e) | Validate Android gateway handshake contract | 1 | 76 / 6 | `6464b9f46148` |
| [`29cf92c19d`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/29cf92c19de242e71e27f7a23b74b33037a2de9f) | Make broadcast stream and output codec explicit | 1 | 6 / 0 | `c0452de61c89` |
| [`4ea4644486`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/4ea464448687b581295984fe707c7b5d88fac074) | Add cross-language module compatibility verifier | 1 | 422 / 0 | `25691fed2c90` |
| [`c8da76f096`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/c8da76f096d23fa35c7d6718365e72da72754a3d) | Fix core-contract invariant shell quoting | 1 | 1 / 1 | `2dbec1d9ac0a` |
| [`e6d3dd5f76`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/e6d3dd5f7629ff922c9bfe9a70c659cf3ac8e69a) | Normalize baseline stall constant verification | 1 | 1 / 1 | `3f519b6b805c` |
| [`a186fc96b5`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/a186fc96b5739511dc5557da777775fc8f400c91) | Verify lazy Whisper warmup and first-frame timeout | 1 | 27 / 1 | `280569d213b4` |
| [`4d3ef05bff`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/4d3ef05bffa3126fb5880346eb9b947df4c5c88d) | Document first-frame versus runtime stall handling | 1 | 4 / 3 | `30948d4f626e` |
| [`ac13f8a65c`](https://github.com/BeaconComputingInitiative/BCIAndroid/commit/ac13f8a65c57ccd87b2fc2c72e16835a0bf6b00f) | Define first PCM frame timeout | 1 | 2 / 1 | `2ead73e43659` |
