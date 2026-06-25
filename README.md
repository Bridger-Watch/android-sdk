# Android-SDK

A drop-in Android (Kotlin) library for sending a ballistic profile from your app
to the **Bridger Android app**, which forwards it to the **Bridger Watch**.

You build a profile (or hand us JSON you already have) and call one method. The
library produces the exact JSON Bridger expects, handles all four payload
encodings, base64, strict URL escaping, pre-flight validation, and the
FileProvider plumbing for the share sheet. You never touch the wire format
yourself.

This is the Android counterpart of the **BridgerBallistics** Swift package; both
emit the identical wire format, so an iOS and an Android partner integrate
against the same Bridger app the same way.

- **Zero third-party dependencies.** JDK + AndroidX core only (`java.util.zip`,
  `java.util.Base64`, `androidx.core` FileProvider).
- **One import, one call.** `import com.bridgerwatch.ballistics.Bridger` →
  `Bridger.open(context, profile)`.
- **Validates before it sends**, so a malformed profile fails in your app with a
  clear message instead of being rejected on the watch.

---

## Requirements

| | |
|---|---|
| minSdk | 26 (Android 8.0) |
| compileSdk | 34+ |
| Kotlin | 1.9+ |
| AGP | 8.x |
| Java | 17 |

The deep-link (Path 1) and share-sheet (Path 2) paths require an Android
`Context`. The model, validation, JSON, and URL builder are pure Kotlin (handy
for unit tests and JVM tooling).

---

