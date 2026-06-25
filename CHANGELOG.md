# Changelog

All notable changes to BridgerAndroidBallistics are documented here. This project
adheres to [Semantic Versioning](https://semver.org).

## [1.0.0] — 2026-06

Initial release. Android (Kotlin) counterpart of the **BridgerBallistics** Swift
package — emits the identical wire format consumed by the Bridger Android app
(`com.bridgerwatch.app`) and the Bridger Watch.

### Added
- `Bridger` facade — one entry point for all delivery paths.
- `BallisticProfile` model with type-safe Kotlin names mapping to Bridger's exact
  (mixed-case) wire keys; optional `rifle` / `bullet`; unit-aware row range key
  (`range_yards` / `range_meters`).
- Delivery **Path 1** (deep link `bridgerwatch://import-ballistics`):
  `open(...)`, `importUrl(...)`, and `isInstalled(...)`. The required Android 11+
  `<queries>` package-visibility entry is contributed by the library manifest.
- Delivery **Path 2** (share sheet): `shareIntent(...)`, backed by a
  uniquely-named bundled `FileProvider`.
- Delivery **Path 3** (manual file): `exportFile(...)`.
- Four payload encodings — `RAW_JSON`, `BASE64_JSON`, `GZIP_BASE64`,
  `ZIP_BASE64` — with self-contained gzip and zip writers (JDK `java.util.zip`,
  no third-party dependencies). The zip writer keeps sizes in the local header
  (no streaming data descriptor) so the Bridger receiver accepts it.
- Pre-flight validation (`validate(...)`, `ValidationResult`) mirroring Bridger's
  accept / reject / warn rules.
- Strict RFC 3986 percent-escaping of URL query values so base64 payloads survive
  both percent- and form-decoding intact.
- "Bring your own JSON" variants of every send method, plus `BallisticProfile.fromJson`.
- JVM unit-test suite (`EncodingTests`, `ValidationTests`, `ImportUrlBuilderTests`,
  `PayloadCodecTests`) and documentation (`README.md`, `USAGE.md`, `Examples/`).

### Verified
- Output cross-checked against the Bridger iOS import pipeline (decoder + parser):
  base64, gzip, and zip payloads all decode and validate identically to the Swift
  package's output.

[1.0.0]: https://github.com/your-org/BridgerAndroidBallistics/releases/tag/1.0.0
