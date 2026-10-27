<!-- BEACON COMPUTING INITIATIVE | Developer Seal: CJB-DEV-2026 | Chance Joaquin Boone -->
<!-- SHA-256: see docs/HYGIENE_SHA256SUMS -->

# Repository review: BCIAndroid

Reviewed: October 26, 2026. Default branch: `main`. Audited commit: `57291e40c0f56925913b502c4d422edea5eb715c`.

## Verified findings and follow-up

- Published release `android-v6.0-alpha20` and the default branch show Alpha 20, but the README opens with Alpha 16 and retains older AudioRecord/Canvas descriptions.
- Source paths include the native Oboe engine and live-transcription modules. Refresh the top-level overview against those modules instead of presenting old implementation notes as current behavior.
- No final/latest/copy filename pattern was found in the inspected default tree. APKs are release assets rather than tracked binaries, which is appropriate.
- Eleven branch names were returned, including prior alpha integration branches. Review merge ancestry before deleting any branch; this audit preserves them.
- Physical-device behavior, signing-key custody, and production deployment were not tested.

## Repository rules

- Maintain one documented current source tree and one supported starting point per product/platform. Give genuinely distinct tools purpose-based names.
- Use Git commits, branches and tags for revisions. Avoid final, latest, new, old, backup and copy suffixes on source files.
- Put downloadable builds and checksums in GitHub Releases. Keep recordings, telemetry, local environments and generated dependencies outside tracked source.
- Keep version-qualified protocol/API names and regression-test names when they describe an actual compatibility contract.
- Write commits as a concrete action and affected component, followed by the reason and relevant validation when needed. A late-night timestamp alone is not a quality defect.
- Release if and only if the documented acceptance gates pass on the exact release commit. Clearly label previews, recovered source, device validation gaps and historical releases.
- Keep the BCI header and developer seal on new authored files. Record their exact bytes in a SHA-256 manifest; do not attempt a self-referential file hash.

## Automated prevention

`Repository hygiene` runs on pushes and pull requests with read-only repository permissions. It inspects tracked files without executing project/application code. It rejects new confusing copy-style filenames, generated/runtime artifacts, recovery snapshot directories and byte-identical nonempty source copies. Existing paths are grandfathered against the audited commit, so this is prevention rather than a claim that historical issues are resolved. Empty package initializers are excluded from duplicate detection. Dataset duplicates are reported separately and left untouched.

Run locally: `python3 tools/repository_hygiene.py --baseline 57291e40c0f56925913b502c4d422edea5eb715c` and `sha256sum -c docs/HYGIENE_SHA256SUMS`.

This workflow checks pushes and pull requests after integration, and it does not become a mandatory branch-protection rule automatically. It cannot prove semantic equivalence, runtime correctness, README accuracy or release readiness. Intentional shared code should be extracted into a common module; deliberate compatibility exceptions require a reviewed policy adjustment.

## Extended history audit

See [PORTFOLIO_AUDIT.md](PORTFOLIO_AUDIT.md) for the complete branch-history inventory, consolidation decisions and current review limits. The original findings above describe the pre-cleanup snapshot; the portfolio audit and README record the resulting state.
