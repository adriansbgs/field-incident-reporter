# Field Incident Reporter

An Android app for field technicians who photograph and report physical incidents at a facility: broken equipment, road hazards, leaks. A local json-server mock API stands in for the backend.

The app is **offline-first**. Every report is committed to the phone first and uploads in the background, so it works with no signal and with the server down. It is **camera-first**: the photo is the perishable part of a report (the puddle gets mopped, the hazard gets cleared), so the camera opens before the form.

**Demo video** (1–2 minutes, recorded on a physical phone): [watch on Google Drive](https://drive.google.com/file/d/1NHRJLGsLNrrt-OA9fNhwEV4Rvor63GVD/view?usp=sharing)

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

The build needs JDK 17 or newer, and the Android SDK. Opening `android/` once in Android Studio finds both; on the command line, set `JAVA_HOME` and `ANDROID_HOME`.

## Tests

```bash
cd android
./gradlew testDebugUnitTest            # on the JVM: ViewModels, validation, sync rules, mappers
./gradlew connectedDebugAndroidTest    # on a phone: Room, the repository, and the sync worker's logic
```

`SyncDecisionTest` covers every pairing of a pending change with a new one, plus the success, error, conflict and pull rules as tables. Fakes stand in for the network and the clock. There are no mocking libraries.

## Code layout

```
android/app/src/main/java/com/incidentreporter/field/
├── core/      database (Room), di (Hilt), designsystem (theme and shared components), result (errors)
├── domain/    model, repository interfaces, usecase (validation and sync rules, plain Kotlin)
├── data/      local and remote mapping, photo files, the sync worker, location, the repository
└── feature/   list, detail, form, capture: a ViewModel and a screen each
```

## Decisions & trade-offs

### Offline-first and sync

- **Room is the single source of truth.** Screens observe the database, never the network. A pull writes server rows into Room and the list updates from there, so with no signal the app shows its last-known list instead of an error.
- **A pull never overwrites what the phone still owes the server.** Drafts and rows with a pending change are left alone. Everything else mirrors the server, deletions included. A row that was pending when the pull started stays protected even if its upload lands mid-pull, so a report that just synced can't be deleted along with its photos, which exist only on the phone.
- **One pending change per incident, not an outbox table.** Two offline edits collapse into one, and a delete replaces a pending edit. Deleting a report that never reached the server is a local-only delete with no network call. The server only ever needs an incident's latest state. Keeping one change per record removes a whole class of ordering bugs that a general outbox would have to solve.
- **Client-generated UUIDs.** A report created offline has its permanent id from the first moment. There's no temporary id to rewrite when it reaches the server, which accepts the id the app sends.
- **One WorkManager job uploads every queued change, one record at a time.**
  - A record moves from Pending to Syncing to Synced.
  - Offline, it stays Pending. WorkManager's network constraint resumes the job when the signal returns, so there's no connectivity listener.
  - A server error retries with exponential backoff from 10 seconds, and the record becomes Failed after five tries. A rejected request fails at once.
  - Sync is requested after every local change and on every pull, including at launch.
  - The worker sends a snapshot and records the result against the live row, so an edit made during an upload is queued, not lost.
- **A create is safe to retry.** If the server stored a report but the response was lost, the retry finds the record with a GET and finishes with a PATCH. A second POST would hit json-server's duplicate-id 500 and mark a successful upload Failed.
- **Conflicts are caught by reading before writing.** json-server has no ETags, so an update first GETs the record and compares its `updatedAt` with the last value the phone saw from the server. If someone else changed it, the record is marked Changed on server and nothing is overwritten. The technician's edit stays on the phone until they decide. "Use server version" takes the server's copy. Editing again is a deliberate overwrite, so last write wins, but only by choice. A real API would send `If-Match` with an ETag and get a 412 back: one round trip, and no gap between the read and the write.
- **A new sync request replaces a waiting one (`ExistingWorkPolicy.REPLACE`).** Pull-to-refresh uploads now instead of sitting out a backoff delay. Cancelling a run mid-upload is safe: the create is idempotent, and an interrupted record is picked up again.
- **The worker gets its dependencies from a Hilt `@EntryPoint`, not `@HiltWorker`.** WorkManager's default factory builds it, so one worker needs no custom `WorkerFactory`, no manifest override of WorkManager's startup and no extra dependency.
- **Only abnormal sync states get a badge.** A synced row shows nothing. Pending, Failed and Changed on server each get an outlined badge, and the banner counts exactly the rows wearing Pending. When everything has uploaded, the list is quiet.

### Capturing evidence

- **Camera first, because the photo is the perishable part.** A leak spreads and a hazard gets cleared, but a title can be typed later. So "Report incident" opens the camera, and the form comes after the shots. Skip, confirmed once, still allows a report with no photo.
- **No dialog between shots.**
  - Each shot saves straight to the draft and lands in the strip. A tap on a thumbnail opens a review with Retake and Delete.
  - The draft is created by the first shot, so opening the camera and backing out leaves nothing behind.
  - Back with photos asks once before discarding them.
- **A new report starts as a hidden draft, and there's only ever one.** The draft row exists from the first photo, so a shot is never held only in memory. It never appears in the list and is never sent. Submitting turns it into a pending report in one transaction, and a second tap finds nothing left to submit. Starting another report discards the abandoned draft and any photo file no row points at.
- **The app owns photo bytes from the moment they exist.** The camera writes straight into app storage under the photo's final id, and a gallery pick is copied in, so the original can be deleted without breaking the report. Database changes commit first and files are deleted only afterwards, because a rolled-back transaction can't bring a deleted file back.
- **Photos are encouraged, never required.** Validation has no photo rule. A device with the camera disabled by policy, a fault with nothing to see, or a hazard too dangerous to approach must still produce a report. The list marks a report with "no photos" so triage can see the evidence is missing.
- **Every photo records its source, `CAMERA` or `GALLERY`.** An in-app capture was taken at the scene, at a known time. A picked image could be any file from any day. Both code paths exist anyway, so recording which one ran is free, and it lets an operator weigh the evidence instead of assuming it.
- **Photos sync as metadata only; the bytes stay on the phone.** json-server accepts no multipart uploads, and base64 blobs in `db.json` would model the problem badly. `fileName` is the contract. Real upload is additive: an upload step before the incident `POST`, plus a `remoteUrl` on each photo.
- **Photos are taken in the app with CameraX, not handed to the system camera app, and the choice is contained.**
  - The technician never leaves the report. Each shot is written straight into app storage under its final id, and behaviour doesn't depend on whichever camera app the phone ships with.
  - The scope is deliberately small: a viewfinder and still capture at about Full HD, with no image analysis, zoom or tap-to-focus.
  - Capture touches one screen and one class, and `PhotoStore` takes a camera shot and a gallery pick the same way. Swapping to `ActivityResultContracts.TakePicture` would change nothing downstream.
- **The camera permission is asked in three stages.**
  - The first time, the system prompt appears straight away, because the technician has just asked for the camera.
  - After one denial, the camera screen explains why a photo matters and offers the prompt again.
  - After a final denial, it says camera access is off and links to the app's Settings page. Coming back with access switched on shows the viewfinder without another prompt.
  - At every stage the gallery button still works, so a denied camera never blocks a report.
- **The camera screen is locked to portrait, but its photos aren't.** The controls never jump around, and the rest of the app still rotates. CameraX's `RotationProvider` follows how the phone is physically held, so a sideways shot is saved upright.
- **Gallery photos come from the system photo picker (`PickVisualMedia`).** It needs no storage permission, shows only images, and falls back to `ACTION_GET_CONTENT` on older phones. The picked file is copied in at once, so the report never depends on a link that can expire.
- **Thumbnails have no delete button.** A 48dp × on a 76dp thumbnail would delete evidence with one gloved tap. A tap opens a full-screen review instead, and Delete is there.
- **Photos are fixed once a report is submitted.** Editing covers text, category, severity and location. Evidence can't be swapped after the fact, the PATCH body carries no photos, and edits need no second draft. Edit mode shows the photos read-only.

### The form and location

- **Every control is at least 48dp, for gloved hands.**
  - Category is six chips, all visible, one tap each, instead of a dropdown.
  - Severity is a four-way segmented row.
  - Submit is a 54dp button pinned above the keyboard, so it never takes a scroll while typing.
- **A half-filled form survives the app being killed.** Android may kill a backgrounded app to free memory. The typed fields are saved to `SavedStateHandle` on every change, and the photos are already in Room. A technician who switches to a call and comes back loses nothing.
- **Only open incidents can be edited or deleted.** In progress and Resolved belong to the operator. The detail screen keeps Edit and Delete visible but disabled, with one line saying why, so nobody hunts for a button that seems to have vanished.
- **Location comes from Play Services, because it's the least code.** One `getCurrentLocation` call picks GPS, Wi-Fi or the mobile network by itself. With a 10-second cap, it returns "no fix" instead of leaving the form waiting.
- **The location permission is asked once, in context, and then never again.**
  - The form doesn't prompt when it opens, because the dialog would land on top of the keyboard.
  - "Use my location" asks. Once granted, every new report fills in its position without a tap.
  - Approximate location counts as a yes.
- **Location never blocks a report.** A denial, no fix, or Location switched off all lead to the same place: a short note, and a Place field that satisfies the form on its own. An edit keeps the location the report was made with.

### Build and platform

- **json-server pinned to 0.17.4.** v1 removed `--delay`, which is the only way to make loading states visible on a phone. Browser throttling doesn't apply to an Android client.
- **One Gradle module (`:app`), organised as `core` / `domain` / `data` / `feature` packages.** Splitting into multiple modules buys build isolation a five-screen prototype can't use. The package boundaries make a later split mechanical.
- **KSP, not kapt.** AGP 9 compiles Kotlin itself and has no place for kapt. KSP also runs Hilt and Room's code generation faster.
- **compileSdk 37, targetSdk 36.** The current AndroidX releases compile against API 37. Runtime behaviour stays opted in at 36.
- **Cleartext HTTP in debug builds only.** json-server speaks plain HTTP, so the exception lives in `src/debug/AndroidManifest.xml`. Release builds stay HTTPS-only.
- **Light theme only, applied unconditionally.** Dark mode is a non-goal. Rather than ship a half-built dark palette, a phone in dark mode gets the light app with dark status-bar icons. Every colour comes from one theme file, so adding a dark scheme later means changing one palette, not every screen.

## Known limitations

Cut on purpose to fit the time box, or limits of the prototype:

- **No connectivity listener.** Uploads resume through WorkManager's network constraint, but the list itself only refreshes on launch and on pull-to-refresh, not the moment the signal returns.
- **No "pending for over an hour" warning.** The banner counts waiting reports, but it doesn't escalate when one has been stuck for a long time.
- **Photo bytes never leave the phone.** Another device, or the operator, sees each photo's file name and metadata, not the image.
- **Conflicts are detected, not merged.** After "Changed on server", the technician either takes the server's copy or edits again. Editing again overwrites the server's change.
- **No authentication.** Every report comes from one hard-coded technician, `tech-001`.
- **The first location fix right after allowing approximate location can come back empty.** The next request succeeds, and tapping "Use my location" again recovers.

## Non-goals

- Authentication and users. A single hard-coded technician identity, `tech-001`.
- Map rendering. Locations show as coordinates or a place label.
- Uploading photo bytes. The API stores photo metadata only.
- Push notifications, analytics, crash reporting.
- Dark mode, and pixel-level visual polish.
- Resolving conflicts across devices beyond detection plus last write wins.
- The operator web dashboard.
