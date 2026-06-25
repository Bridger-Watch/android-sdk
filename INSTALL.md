# BridgerAndroidBallistics

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

## Installation (Gradle)

**Option A — as a Maven artifact (e.g. JitPack):**

`settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") // if distributed via JitPack
    }
}
```

App `build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.bridgerwatch:bridgerballistics:1.0.0")
}
```

**Option B — as a local module:** copy the `bridgerballistics/` module into your
project, add `include(":bridgerballistics")` to `settings.gradle.kts`, and depend
on `implementation(project(":bridgerballistics"))`.

---

## ✅ No manual manifest step for the deep link

To launch the Bridger app and to query whether it's installed, Android 11+
requires your app to declare *package visibility* for the `bridgerwatch` scheme.
**This library contributes that `<queries>` entry via manifest merge**, so
`Bridger.isInstalled(context)` and `Bridger.open(...)` work out of the box. (This
is the Android analog of the iOS `LSApplicationQueriesSchemes` Info.plist entry.)

The library also bundles a uniquely-named `FileProvider`
(`${applicationId}.bridgerballistics.fileprovider`) for the share-sheet path, so
there's nothing to wire up there either.

---

## Quick start

```kotlin
import com.bridgerwatch.ballistics.Bridger
import com.bridgerwatch.ballistics.model.*

val profile = BallisticProfile(
    chartID = "Weatherby 307 · 300 PRC · 225 ELD-M",
    units = Units(RangeUnit.YARDS, LinearUnit.INCHES, AngleUnit.MOA),
    rangeSettings = RangeSettings.fromRowCount(
        start = 100, end = 500, interval = 5, rowCount = myRows.size,
    ),
    rifle = Rifle(caliber = "300 PRC", name = "Weatherby 307"),
    bullet = Bullet(
        manufacturer = "Hornady", model = "ELD-M",
        weightGrains = 225.0, muzzleVelocityFPS = 2810.0,
    ),
    table = myRows.map { row ->
        BallisticRow(
            range = row.yards,
            comeupDistance = row.dropInches,
            comeupAngle = row.moa,
        )
    },
)

// Send it to Bridger (validated first; base64-encoded; launches the app).
try {
    Bridger.open(context, profile)            // Path 1
} catch (e: BridgerError) {
    // e.message is partner-friendly. e.g. BridgerError.BridgerNotInstalled.
}
```

That's the whole integration. Everything below is detail for when you need it.

---

## The three delivery paths

Bridger accepts a profile by any of three mechanisms. **You only need one.**
Path 1 is the smoothest; Paths 2 and 3 are good fallbacks (e.g. very large
profiles, or when you'd rather use the system share sheet).

### Path 1 — Deep link / intent (recommended)

```kotlin
// Fire-and-forget (validates, then launches Bridger):
Bridger.open(context, profile, slot = BridgerSlot.SLOT0)

// Or build the URL yourself and start the intent however you like:
val url = Bridger.importUrl(profile, slot = BridgerSlot.SLOT0,
                            encoding = PayloadEncoding.BASE64_JSON)
context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
```

Check availability first if you want to offer a fallback:

```kotlin
if (Bridger.isInstalled(context)) {
    Bridger.open(context, profile)
} else {
    // Offer the share sheet, or point the user to install Bridger.
}
```

### Path 2 — Share sheet

```kotlin
val chooser = Bridger.shareIntent(context, profile)
context.startActivity(chooser)
```

The library writes the profile to your app's cache and shares it via its bundled
FileProvider — the user picks **Bridger** from the share sheet to import it.

### Path 3 — Write a file the user imports manually

```kotlin
val file = Bridger.exportFile(profile, directory = context.getExternalFilesDir(null)!!,
                              filename = "bdata0.json")
// Tell the user to open Bridger and import this .json file.
```

---

## Bringing your own JSON

If your app already produces Bridger-format JSON (from a server, say), skip the
model and pass the bytes. They're used verbatim, so your exact JSON is preserved:

```kotlin
val jsonBytes: ByteArray = ...   // your Bridger-format JSON

// Validate the way Bridger will:
val result = Bridger.validate(jsonBytes)
if (!result.isValid) { Log.w("Bridger", result.toString()); return }

// Send it:
Bridger.open(context, jsonBytes)
// or: Bridger.importUrl(jsonBytes)
// or: context.startActivity(Bridger.shareIntent(context, jsonBytes, "bdata0.json"))
```

---

## Validation

Validation runs automatically before any send (pass `validate = false` to skip
it). It mirrors the rules Bridger applies on the watch side:

- **Errors (block the transfer):** not valid JSON; missing `chart_id`, `units`,
  `range_settings`, or `ballistic_table`; a non-numeric value in a numeric field.
- **Warnings (accepted, but flagged):** `ballistic_table` length ≠
  `interval_count`; empty table; empty `chart_id`.

`weapon_system` and `ammunition` are optional and never cause a rejection.

```kotlin
val result = Bridger.validate(profile)
result.isValid     // false if any errors
result.errors      // blocking issues
result.warnings    // non-blocking issues
result.toString()  // human-readable summary of everything
```

When a send throws `BridgerError.ValidationFailed`, its `result` lists every
issue so you can surface them all at once.

---

## Payload encodings (Path 1)

The `data` value in the URL can be packed four ways. Bridger auto-detects which
one you used, so pick whichever you like — `BASE64_JSON` is the default and
recommended.

| `PayloadEncoding` | What it sends | Notes |
|---|---|---|
| `RAW_JSON` | the JSON text | Longest URL; still escaped correctly. |
| `BASE64_JSON` | base64 of the JSON | **Default.** Simple and robust. |
| `GZIP_BASE64` | base64 of a gzip archive | Smaller URL. |
| `ZIP_BASE64` | base64 of a one-entry zip | Smaller URL. |

```kotlin
val url = Bridger.importUrl(profile, encoding = PayloadEncoding.GZIP_BASE64)
```

A typical single-weapon profile fits comfortably in a URL as base64. For
unusually large tables, `GZIP_BASE64` / `ZIP_BASE64` shrink it; if it still
exceeds the limit you'll get `BridgerError.PayloadTooLarge` — use the share sheet
instead.

> The gzip/zip writers emit sizes in the local header (no streaming data
> descriptor), which is exactly what the Bridger receiver requires.

---

## Slots

The watch holds up to four profiles. Target one explicitly, or let Bridger
decide:

```kotlin
Bridger.open(context, profile, slot = BridgerSlot.SLOT2)  // overwrite slot 2
Bridger.open(context, profile, slot = BridgerSlot.AUTO)   // Bridger chooses (defaults to slot 0)
```

A pinned slot adds `slot=<n>&filename=bdata<n>.json` to the URL; `AUTO` omits both.

---

## Error handling

Everything throws `BridgerError` (a sealed `Exception`):

```kotlin
try {
    val url = Bridger.importUrl(profile)
    // ...
} catch (e: BridgerError) {
    when (e) {
        is BridgerError.ValidationFailed -> showIssues(e.result)
        is BridgerError.PayloadTooLarge  -> offerShareSheet(e.urlLength, e.limit)
        BridgerError.BridgerNotInstalled -> promptToInstallBridger()
        else -> Log.e("Bridger", e.message, e)
    }
}
```

---

## How the URL is escaped (and why it's safe)

A standard base64 string can contain `+`, `/`, and `=`. The library
percent-escapes every query value down to the RFC 3986 *unreserved* set, so those
become `%2B`, `%2F`, `%3D`. This decodes back to the identical standard-base64
string under both ordinary percent-decoding and
`application/x-www-form-urlencoded` decoding — so the bytes Bridger ultimately
base64-decodes are byte-for-byte what the encoder produced. (A naive
`Uri.Builder` query could leave `+` unescaped, and a form-decoder would turn it
into a space and corrupt the payload.)

---

## FAQ

**Which app receives the profile?**
The **Bridger Android app** (`com.bridgerwatch.app`). It registers an
intent-filter for `bridgerwatch://import-ballistics`, decodes the `data` payload,
writes `bdata<slot>.json`, and syncs it to the watch over BLE.

**Does Bridger call my app back when the import finishes?**
No. The handoff is one-way.

**My `isInstalled` always returns false.**
That should not happen — the library contributes the `<queries>` entry. Confirm
the library's manifest actually merged (check the merged manifest) and that the
Bridger app is installed.

**Does JSON key order or number formatting matter?**
No. Key order is not significant, and the library emits canonical JSON numbers
(e.g. `0` rather than `0.0`).

**Can I see the JSON the library will send?**
Yes: `String(Bridger.makeJson(profile, pretty = true))`.

**Do I have to use the `BallisticProfile` model?**
No — see *Bringing your own JSON*.

---

## Support

Integration spec and questions: **Pat Murphy — pmurphyjam@gmail.com**.

See `USAGE.md` for a longer, end-to-end worked example, and `Examples/` for a
reference integration.

## License

Provided to integration partners under your integration agreement with Bridger.
See `LICENSE`.
