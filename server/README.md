# Mock API

[json-server](https://github.com/typicode/json-server) **0.17.4**, serving `db.json` on port 3000.

```bash
npm install
npm start        # http://localhost:3000
npm run slow     # same, with an 800 ms delay on every response (to show loading states)
npm run reset    # restore db.json from db.seed.json
```

`db.json` is committed and seeded with 7 incidents. They cover every severity, status and category, some with photos and some without. Requests that change data rewrite `db.json`. Run `npm run reset` to get back to a clean seed.

## Why 0.17.4, not v1

v1 is still labelled beta. It also changes behavior this project depends on:
- `_sort=-createdAt` replaces `_sort=&_order=`.
- `_page`/`_per_page` replace `_limit`.
- **`--delay` was removed.** Without `--delay`, loading states and skeleton rows can't be shown on a phone, because browser throttling doesn't apply to an Android client.

The exact version is pinned in `package.json`.

## Endpoints

| Method | Path | Purpose | Notes |
| --- | --- | --- | --- |
| GET | `/incidents?_sort=createdAt&_order=desc` | List | Filtering happens on the device, against the local database, so it also works offline |
| GET | `/incidents/:id` | Fetch one | Read before every edit, to detect conflicts |
| POST | `/incidents` | Create | The body carries the client-generated UUID as `id` |
| PATCH | `/incidents/:id` | Edit | Partial body: the user-editable fields plus `updatedAt` |
| DELETE | `/incidents/:id` | Remove | The app treats 404 as success (already gone) |

## Resource

```json
{
  "id": "24f2d67b-518a-405b-afd8-8e75becaa72a",
  "title": "Hydraulic press leaking fluid",
  "description": "Puddle forming under the press at bay 3.",
  "category": "EQUIPMENT_FAILURE",
  "severity": "HIGH",
  "status": "OPEN",
  "location": { "latitude": -0.9471, "longitude": 100.4172, "label": "Bay 3, South Workshop", "source": "GPS" },
  "photos": [
    { "id": "53943fcb-…", "fileName": "53943fcb-….jpg", "source": "CAMERA",
      "capturedAt": "2026-10-20T08:14:02Z", "widthPx": 1080, "heightPx": 1920 }
  ],
  "reportedBy": "tech-001",
  "createdAt": "2026-10-20T08:15:30Z",
  "updatedAt": "2026-10-20T08:15:30Z"
}
```

- **Enums:** `category` is `EQUIPMENT_FAILURE | ROAD_HAZARD | ELECTRICAL | PLUMBING | STRUCTURAL | OTHER`. `severity` is `LOW | MEDIUM | HIGH | CRITICAL`. `status` is `OPEN | IN_PROGRESS | RESOLVED`.
  - The status is owned by the server. The app only ever creates `OPEN`.
  - When you hand-edit unknown values into `db.json`, the app falls back instead of crashing.
- **Timestamps:** ISO-8601 UTC strings.
- **Photo metadata only.** `fileName` is the contract; the image bytes stay on the reporting device. json-server can't accept multipart uploads, and base64 blobs inside `db.json` would model the problem badly. Adding real upload means one new field (`remoteUrl`) plus an upload step before the `POST`.
- **No sync or draft fields.** Those are local concerns of the app and never appear in the API.

## Errors, as the app handles them

| Condition | App behavior |
| --- | --- |
| No connectivity, or a timeout | The record stays `Pending`; WorkManager retries when the network is back. Not shown as an error |
| 4xx (except 404 on DELETE) | The record is marked `Failed` with the message; the detail screen offers Retry |
| 5xx | Exponential backoff, at most 5 attempts, then `Failed` |
| Malformed body | `Failed`, and logged. Never a crash |

## Concurrency: read before write

json-server has no ETags and no conditional requests. Before each `PATCH`, the app `GET`s the record. It compares the server's `updatedAt` with the last value it saw. If the server copy is newer, the app marks the local edit **Changed on server** instead of overwriting it, and lets the technician take the server version.

With a real backend, this would be `If-Match` with an ETag, and the server would answer `412 Precondition Failed`.
