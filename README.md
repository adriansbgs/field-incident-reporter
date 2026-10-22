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
- **One pending change per incident, not an outbox table.** Two offline edits collapse into one, and a delete replaces a pending edit. Deleting a report that never reached the server is a local-only delete with no network call. The server only ever needs an incident's latest state. Keeping one change per record removes a whole class of ordering bugs that a general outbox would have to solve.
- **Client-generated UUIDs.** A report created offline has its permanent id from the first moment. There's no temporary id to rewrite when it reaches the server, which accepts the id the app sends.
- **Photos sync as metadata only; the bytes stay on the phone.** json-server accepts no multipart uploads, and base64 blobs in `db.json` would model the problem badly. `fileName` is the contract. Real upload is additive: an upload step before the incident `POST`, plus a `remoteUrl` on each photo.
- **Every photo records its source, `CAMERA` or `GALLERY`.** An in-app capture was taken at the scene, at a known time. A picked image could be any file from any day. Both code paths exist anyway, so recording which one ran is free, and it lets an operator weigh the evidence instead of assuming it.
- **Room is the single source of truth.** Screens observe the database, never the network. A pull writes server rows into Room and the list updates from there, so with no signal the app shows its last-known list instead of an error.
- **A pull never overwrites what the phone still owes the server.** Drafts and rows with a pending change are left alone. Everything else mirrors the server, deletions included. A row that was pending when the pull started stays protected even if its upload lands mid-pull, so a report that just synced can't be deleted along with its photos, which exist only on the phone.
- **Only abnormal sync states get a badge.** A synced row shows nothing. Pending, Failed and Changed on server each get an outlined badge, and the banner counts exactly the rows wearing Pending. When everything has uploaded, the list is quiet.
- **Only open incidents can be edited or deleted.** In progress and Resolved belong to the operator. The detail screen keeps Edit and Delete visible but disabled, with one line saying why, so nobody hunts for a button that seems to have vanished.
- **A new report starts as a hidden draft, and there's only ever one.** The draft row exists from the first photo, so a shot is never held only in memory. It never appears in the list and is never sent. Submitting turns it into a pending report in one transaction, and a second tap finds nothing left to submit. Starting another report discards the abandoned draft and any photo file no row points at.
- **The app owns photo bytes from the moment they exist.** The camera writes straight into app storage under the photo's final id, and a gallery pick is copied in, so the original can be deleted without breaking the report. Database changes commit first and files are deleted only afterwards, because a rolled-back transaction can't bring a deleted file back.
- **Photos are encouraged, never required.** Validation has no photo rule. A device with the camera disabled by policy, a fault with nothing to see, or a hazard too dangerous to approach must still produce a report. The list marks a report with "no photos" so triage can see the evidence is missing.
- **Light theme only, applied unconditionally.** Dark mode is a non-goal. Rather than ship a half-built dark palette, a phone in dark mode gets the light app with dark status-bar icons. Every colour comes from one theme file, so adding a dark scheme later means changing one palette, not every screen.

## Non-goals

- Authentication and users. A single hard-coded technician identity, `tech-001`.
- Map rendering. Locations show as coordinates or a place label.
- Uploading photo bytes. The API stores photo metadata only.
- Push notifications, analytics, crash reporting.
- Dark mode, and pixel-level visual polish.
- Resolving conflicts across devices beyond detection plus last write wins.
- The operator web dashboard.
