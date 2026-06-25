# BridgerAndroidBallistics — Usage Walkthrough

This document walks a partner integration end to end: from your app's own data
model, through building a `BallisticProfile`, to delivering it to the **Bridger
Android app** by each of the three supported paths. It assumes you've added the
library (see `README.md`). There is no manual manifest step — the required
`<queries>` entry and the share-sheet `FileProvider` are contributed by the
library and merged into your app.

---

## 1. Map your data to a `BallisticProfile`

Most apps already have a ballistics result in some internal shape. The only work
is mapping it onto `BallisticProfile`. Suppose your app has:

```kotlin
data class MyTrajectoryPoint(
    val rangeYards: Double,
    val dropInches: Double,
    val elevationMOA: Double,
    val windageInches: Double?,   // optional
)

data class MySolution(
    val title: String,
    val caliber: String,
    val rifleName: String,
    val bulletMaker: String,
    val bulletModel: String,
    val bulletGrains: Double,
    val muzzleVelocity: Double,
    val points: List<MyTrajectoryPoint>,
    val zeroYards: Int,
)
```

Map it once, in a small helper:

```kotlin
import com.bridgerwatch.ballistics.model.*

fun MySolution.toBridgerProfile(): BallisticProfile {
    val rows = points.map { p ->
        BallisticRow(
            range = p.rangeYards,
            comeupDistance = p.dropInches,
            comeupAngle = p.elevationMOA,
            comeupMRADAngle = null,          // include if you compute MRAD
            driftInches = p.windageInches,   // include any wind drift you have
        )
    }

    val settings = RangeSettings.fromRowCount(
        start = points.firstOrNull()?.rangeYards?.toInt() ?: 0,
        end = points.lastOrNull()?.rangeYards?.toInt() ?: 0,
        interval = if (points.size > 1)
            (points[1].rangeYards - points[0].rangeYards).toInt() else 1,
        rowCount = rows.size,                // becomes interval_count
        zeroRange = zeroYards,
        tableDisplayRange = zeroYards,
    )

    return BallisticProfile(
        chartID = title,
        units = Units(RangeUnit.YARDS, LinearUnit.INCHES, AngleUnit.MOA),
        rangeSettings = settings,
        rifle = Rifle(caliber = caliber, name = rifleName),
        bullet = Bullet(
            manufacturer = bulletMaker,
            model = bulletModel,
            weightGrains = bulletGrains,
            muzzleVelocityFPS = muzzleVelocity,
        ),
        table = rows,
    )
}
```

> The mixed-case wire keys (`Comeup_linear`, `Name`, `weight_grains`, …) are
> handled internally — you only ever see clean Kotlin names.

---

## 2. Validate (optional but recommended for your own UX)

Sends validate automatically, but validating up front lets you show problems in
your own UI before the user taps anything:

```kotlin
val profile = solution.toBridgerProfile()
val result = Bridger.validate(profile)

if (!result.isValid) {
    showAlert("Profile not ready", result.errors.joinToString("\n") { it.message })
    return
}
if (result.warnings.isNotEmpty()) {
    Log.i("Bridger", "Heads up:\n" + result.warnings.joinToString("\n") { it.toString() })
}
```

---

## 3a. Deliver via the deep link (Path 1)

```kotlin
import com.bridgerwatch.ballistics.Bridger
import com.bridgerwatch.ballistics.support.BridgerError
import com.bridgerwatch.ballistics.transfer.BridgerSlot

if (!Bridger.isInstalled(context)) {
    presentShareSheet(context, profile)      // fall back, or prompt to install Bridger
    return
}

try {
    Bridger.open(context, profile, slot = BridgerSlot.SLOT0)
    toast("Sent to Bridger")
} catch (e: BridgerError) {
    toast("Couldn't send: ${e.message}")
}
```

If you'd rather control the launch yourself:

```kotlin
try {
    val url = Bridger.importUrl(profile, slot = BridgerSlot.SLOT0,
                                encoding = PayloadEncoding.BASE64_JSON)
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
} catch (e: BridgerError.PayloadTooLarge) {
    presentShareSheet(context, profile)      // too big for a URL — use a file
} catch (e: BridgerError) {
    showError(e)
}
```

---

## 3b. Deliver via the share sheet (Path 2)

```kotlin
fun presentShareSheet(context: Context, profile: BallisticProfile) {
    try {
        context.startActivity(Bridger.shareIntent(context, profile))
    } catch (e: BridgerError) {
        showError(e)
    }
}
```

The user picks **Bridger** from the chooser; Bridger receives the `.json` and
imports it.

---

## 3c. Deliver via a file (Path 3)

```kotlin
val dir = context.getExternalFilesDir(null)!!
val file = Bridger.exportFile(profile, directory = dir, filename = "bdata0.json")
// Then instruct the user: open Bridger → Import → pick this file.
```

---

## 4. Inspecting the JSON

Useful while integrating:

```kotlin
val pretty = Bridger.makeJson(profile, pretty = true)
Log.d("Bridger", String(pretty, Charsets.UTF_8))
```

---

## 5. If you already have Bridger-format JSON

```kotlin
val jsonBytes: ByteArray = api.fetchBallisticJson()   // your bytes

if (!Bridger.validate(jsonBytes).isValid) return

Bridger.open(context, jsonBytes, slot = BridgerSlot.SLOT1)
// or build a URL / share intent from the same bytes.
```

---

## Checklist before you ship

- [ ] The library is on your classpath (its manifest `<queries>` + FileProvider merged).
- [ ] You handle `Bridger.isInstalled(context) == false` with a fallback.
- [ ] You surface `BridgerError.ValidationFailed` issues to the user.
- [ ] You've tested with a real profile on a device with the Bridger Android app installed.

Questions: **Pat Murphy — pmurphyjam@gmail.com**.
