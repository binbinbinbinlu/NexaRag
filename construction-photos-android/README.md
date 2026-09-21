# SiteLens — construction photos for Android

Native Kotlin / Jetpack Compose app for Android 10 and newer, with Google Play services.

## What it does

1. On first launch, connect Google Drive and choose an existing writable folder from My Drive or Shared with me. The account and folder are saved across launches; **Change** selects a different destination.
2. Allow all photos or selected-photo access. Startup scans only last week (the previous Monday–Sunday). The MediaStore query excludes other dates before opening originals, hashing, or image analysis.
3. ML Kit suggests construction-related photos. **Amber** means selected for upload. Check or uncheck any unuploaded photo, including photos the detector missed. Tap a thumbnail to inspect it. Choices survive rescans and app restarts.
4. **Green / Uploaded** means the photo is already in the chosen account and folder. **Check Drive** refreshes that status; Upload also checks before sending anything. While connected, scans refresh Drive status automatically. Offline or after restarting, saved tags are explicitly labeled as saved status until refreshed.
5. Tap **Scan more** to scan **Yesterday**, **Last week**, **Last month**, explicitly **All dates**, or **Choose date range** for custom start/end dates (both included). Tap **Upload** to send checked photos in the scanned range. Successful photos turn green; failed photos show an error and can be retried. Green photos can be explicitly checked again for replacement.

Photos are never uploaded merely because the app was opened or a scan finished. Original files are not changed; tags are local app metadata. A scan includes images only, not videos or cloud-only Google Photos items unavailable through Android MediaStore.

## Date ranges and replacement (1.0.2)

- Yesterday is the previous local calendar day; last week is the previous Monday–Sunday week; last month is the previous calendar month. Exact dates and the phone's time zone are shown. Daylight-saving transitions are handled with local midnight boundaries. Dates use MediaStore's date taken, falling back to date added and then date modified when unavailable. Undated photos appear only under All dates.
- Gallery counts and the upload button apply to the chosen date range. Checked photos outside it remain checked but are not uploaded. The batch is frozen when Upload is tapped, before Google authorization or Drive refresh.
- Before each upload, the destination is checked for an exact filename match or matching content checksum. A **File already exists** dialog offers **Replace**, **Skip this photo**, and **Cancel remaining uploads**. Closing the dialog cancels the remaining batch. Nothing is overwritten until Replace is chosen for that photo.
- If several files match, the user chooses exactly one target. Replacements update that Drive file's contents, keeping its filename, folder, and link. Its version and folder membership are checked again immediately before starting the replacement. Only image files can be replaced. Original GPS/EXIF bytes are preserved and the uploaded checksum is verified.
- Uploaded photos stay green and unselected by default. Explicitly checking a green photo requests replacement, followed by the same confirmation. These replacement selections and approvals last for the current session; they do not grant permission to replace files after restarting the app. No “replace all” default is stored.

## Preserve photo location

Version 1.0.1 requests Android's **photo-location metadata** permission (`ACCESS_MEDIA_LOCATION`) along with photo access. Scanning checksums and upload streams use `MediaStore.setRequireOriginal`, so Android must supply the original bytes including embedded EXIF GPS coordinates, altitude, timestamps, orientation, and other existing metadata. Images are not resized or re-encoded. This does not request the phone's current location or add coordinates to photos that have none.

If permission is denied/revoked or the original cannot be opened, uploads stop rather than fall back to a GPS-redacted copy. Only thumbnails and image analysis may use ordinary display copies. On upgrading from 1.0.0, cached checksums are invalidated while manual selections remain, so previous redacted uploads will not be mistaken for matching GPS-bearing originals. Old Drive copies are not changed automatically; select the original again and confirm replacement when prompted.

Regression tests stream a synthetic JPEG containing GPS EXIF metadata through the production request body and mock Drive endpoint and compare every byte, plus check failure when original access is denied. Device acceptance: upload a geotagged photo, download the original file from Drive, and compare its checksum and EXIF GPS tags against the phone original. Also deny/revoke photo-location access and confirm no upload proceeds. Android permission/redaction behavior still needs verification on a physical phone.

## Build and install

Open this directory in Android Studio, use JDK 17, and install Android SDK 35. Gradle 8.11.1 is pinned by the wrapper.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:signingReport
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On macOS/Linux use `bash gradlew`. Android Studio can also install the app using **Run**. Use a physical Android phone or an emulator image with Google Play services for Drive authorization.

For the smaller personal-testing APK, build the optimized release variant:

```powershell
.\gradlew.bat :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

This release variant disables debugging and enables R8 code optimization, obfuscation, and resource shrinking. It includes the on-device image labeling model and all supported CPU architectures. For convenient in-place phone testing it uses this computer's existing **debug signing certificate**, so the OAuth fingerprint above still applies and it can update the prior APK without uninstalling it. It is not configured for Play Store distribution: use a dedicated private release key or Play App Signing before publishing. Keep `app/build/outputs/mapping/release/mapping.txt` with the APK for crash diagnosis.

## Required Google setup

Version 1.0.3 fixes misleading cancellation errors: every returned authorization intent is parsed, including unsuccessful Android activity results. Google status codes now distinguish configuration, network, unavailable services, and actual cancellation. A missing result no longer claims that the user canceled. This diagnostic correction does not replace the required server-side OAuth registration below.

The APK builds without secrets, but Drive sign-in cannot work until the app is registered in a Google Cloud project you control.

1. Enable **Google Drive API** in that project.
2. Configure the Google Auth Platform consent screen, audience, support contact, and test users. For a Workspace-only rollout you can use an internal audience where supported.
3. Create an **Android OAuth client**, package name **`com.sitelens.photos`**, with the SHA-1 from `:app:signingReport` for the exact APK you install. Register the release / Play signing certificate separately when distributing a release.
4. Configure the scope **`https://www.googleapis.com/auth/drive`**. This implementation uses a native folder browser and compares files already present in arbitrary user-chosen folders, including files not created by this app. It therefore uses the restricted Drive scope. External production distribution needs the applicable Google OAuth verification and policy review. The app lists metadata, creates photo files, and replaces individual images after confirmation; it does not delete Drive files.
5. Add your testing Google account as a test user, install the app, and choose a folder. Android authorization is associated with the package and signing certificate: no client secret or API key belongs in the APK.

If Google shows a developer configuration error, check the package, signing SHA-1, project API enablement, consent scope, and test-user account. An account mismatch with the saved folder asks the user to select a destination again instead of mixing upload records.

For the debug APK built in this workspace, the Android OAuth registration values are:

```text
Package: com.sitelens.photos
SHA-1:   07:C0:35:DC:EE:63:C3:8C:3D:1D:6D:D8:4C:1F:BF:EE:BB:59:42:68
```

A build on another computer or CI uses a different debug certificate; get its fingerprint with `signingReport` instead. The debug key is for testing, not production distribution.

Google documentation: [Android authorization](https://developer.android.com/identity/authorization), [Drive scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth), [Drive uploads and retry IDs](https://developers.google.com/workspace/drive/api/guides/manage-uploads), [partial photo access](https://developer.android.com/about/versions/14/changes/partial-photo-video-access).

## Detection and duplicate behavior

- The bundled [ML Kit image labeler](https://developers.google.com/ml-kit/vision/image-labeling/android) runs locally and works without a model download. Version 1.0.4 starts with last week only; **Scan more** explicitly chooses another range. Only accessible photos in that range are hashed and analyzed; unchanged photos reuse cached results.
- This is an initial heuristic over general labels such as Construction, Building, and Wood, not a construction-specific trained model. It will have false positives and false negatives. The All and Unselected filters let users correct misses. A representative labeled jobsite dataset is needed to measure accuracy and train a specialized model.
- The chosen folder's direct children are checked by **MD5 checksum**, across all response pages. Same contents with a different filename are recognized; recompressed or edited copies count as different photos. Subfolders are separate destinations and are not searched recursively.
- Upload history is scoped to the Google account's stable Drive permission ID plus the folder ID, not a folder name. A completed refresh removes stale green status when remote photos were deleted or moved.
- Before uploading, content is rehashed to detect local edits. New file IDs are reserved and saved in SQLite before requests begin. Retries reuse them, and a lost successful response is reconciled without creating another copy. Remote checksum and parent are verified before marking success.
- Uploads stream originals using Drive resumable sessions without loading the whole photo into RAM. Interrupted transfers restart that file on retry using the same reserved ID; byte-range resume is not implemented. An existing session URL is not stored.
- Scans and uploads run off the UI thread and survive screen rotation. They are foreground app operations, not scheduled background jobs. Keep the app open for a batch. If Android terminates it, reopen, scan, and tap Upload to retry; committed receipts and choices survive.
- Permission denial, partial access, inaccessible photos, canceled authorization, read-only folders, token expiry, network failures, and per-photo upload errors are surfaced in the UI. No access token is persisted. App backup is disabled to avoid restoring misleading upload receipts on another device.

## Verification

Unit tests exercise confidence thresholds, manual selection, destination isolation, paginated duplicate detection, reserved-ID upload requests, lost-response retries, conflicts, checksum mismatch, moved/trashed files, incomplete listing, and denied permissions. Android lint and a debug APK build are part of the validation command above.

Device acceptance checks with a configured Google project:

- Fresh install → folder choice → deny photo access → grant selected or full access → scan.
- Select a missed construction photo, deselect a false positive, restart, and rescan; both choices remain.
- Put an identical image in the destination under another name; Check Drive makes it green. Check it again and tap Upload; verify that Skip leaves the existing file untouched and Replace updates that same Drive ID.
- Create a different image with the same filename in Drive; confirm the replacement prompt appears. With multiple matches, choose one and verify only that target changes. Cancel during a batch and verify remaining files are untouched.
- Select a date range and verify that checked photos outside it are excluded from both the upload count and uploaded files. Test yesterday across midnight and daylight-saving changes.
- Upload a batch; interrupt connectivity during a transfer and retry. Confirm only one remote file exists per image.
- Delete or move a remote image and refresh; it becomes eligible again.
- Change to another folder; upload status is recomputed for that destination.
- Rotate during scanning/uploading; revoke or reduce media access in Settings and return to the app.

Real-device ML accuracy and live OAuth / Drive upload behavior require device testing; JVM tests use a local mock HTTP server.
