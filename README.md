# Field Incident Reporter

An Android app for field technicians who photograph and report physical incidents at a facility: broken equipment, road hazards, leaks. A local json-server mock API stands in for the backend.

The app is **offline-first**. Every report is committed to the phone first and uploads in the background, so it works with no signal and with the server down. It is **camera-first**: the photo is the perishable part of a report (the puddle gets mopped, the hazard gets cleared), so the camera opens before the form.

```
.
├── android/   Android app (Kotlin, Jetpack Compose)
└── server/    json-server 0.17.4 mock API (see server/README.md for the API contract)
```

## Run

**1. Start the mock API** (Node 18+):

```bash
cd server
npm install
npm start          # or: npm run slow   (800 ms delay, makes loading states visible)
```

**2. Run the app** on a phone with USB debugging enabled:

```bash
adb reverse tcp:3000 tcp:3000
cd android
./gradlew installDebug
```

`adb reverse` lets the phone reach the server at `localhost:3000` over USB, with no Wi-Fi setup. On an emulator, either run the same `adb reverse`, or change `BASE_URL` in `android/app/build.gradle.kts` to `http://10.0.2.2:3000/`.

## Decisions & trade-offs

- **json-server pinned to 0.17.4.** v1 removed `--delay`, which is the only way to make loading states visible on a phone. Browser throttling doesn't apply to an Android client.
- **One Gradle module (`:app`), organised as `core` / `domain` / `data` / `feature` packages.** Splitting into multiple modules buys build isolation a five-screen prototype can't use. The package boundaries make a later split mechanical.
- **KSP, not kapt.** AGP 9 compiles Kotlin itself and has no place for kapt. KSP also runs Hilt and Room's code generation faster.
- **compileSdk 37, targetSdk 36.** The current AndroidX releases compile against API 37. Runtime behaviour stays opted in at 36.
- **Cleartext HTTP in debug builds only.** json-server speaks plain HTTP, so the exception lives in `src/debug/AndroidManifest.xml`. Release builds stay HTTPS-only.
- **Light theme only, applied unconditionally.** Dark mode is a non-goal. Rather than ship a half-built dark palette, a phone in dark mode gets the light app with dark status-bar icons. Every colour comes from one theme file, so adding a dark scheme later means changing one palette, not every screen.

## Non-goals

- Authentication and users. A single hard-coded technician identity, `tech-001`.
- Map rendering. Locations show as coordinates or a place label.
- Uploading photo bytes. The API stores photo metadata only.
- Push notifications, analytics, crash reporting.
- Dark mode, and pixel-level visual polish.
- Resolving conflicts across devices beyond detection plus last write wins.
- The operator web dashboard.
