# M34 for Android

Releases use plain integer numbering: **1**, **2**, **3**, and so on.
`VERSION` is the public release/app version. `VERSION_CODE` is the internal,
monotonically increasing Android installation code; release 1 uses code 11 to
remain installable over the earlier QA builds.

[Download release 1](https://github.com/mattzilla30/M34/releases/tag/1).

Native Android 17+ (API 37), ARM64-only artwork viewer. The release uses the
original signing key, so install it over your existing M34 app to retain data.

## Install release 1

Use `dist/M34-1-android17-arm64.apk`, or:

```sh
adb install -r dist/M34-1-android17-arm64.apk
```

## QA updates through v1.0.9

Three ADB install/test iterations added collection renaming, persistent validation
errors, an always-accessible collection button, complete tag browsing, tag copying,
clear-search controls, and gallery position restoration. The typing test caught
a missing clear button; v1.0.9 updates it as the field changes. Image-cache files
are published atomically to avoid concurrent readers seeing partial downloads.
Download work has a separate queue and duplicate taps are guarded. Autoplay runs
once per artwork opening rather than again after each local like/save update.

## Search and browsing changes

- Removed the offline demo, Online/Offline buttons, and content-source setting.
  Explore and Search always use the Rule34 API. Previously saved local artwork
  remains accessible in Saved; it is not offered as a demo feed.
- Search runs after a 700 ms typing pause, the Go button, or keyboard search.
  Active tags appear above results. Comma-separated tags become space-separated
  tags; multiword booru tags use underscores (`blue_hair`). Multiple tags mean
  AND; exclusions use `-tag`; simple wildcards are supported.
- Results are tied to the query, sort and rating selection. Late responses from
  earlier searches cannot replace newer results. Metadata requests have their
  own thread pool so old thumbnail downloads do not delay a new search. HTTP
  cache reuse is disabled. Simple tag searches reject unrelated API posts and
  report the mismatch instead of displaying the front page as search results.
- Discover tags are fetched from fresh `sort:random` website post metadata on
  each Search refresh. Tags are shuffled and deduplicated, with newly fetched
  tags preferred over the last set. No fixed or history-based fallback. Refresh
  controls reload the feed or just the tag chips; current rating preferences
  apply to the random post sample too.
- Artwork opens a fit-to-screen immersive viewer from its detail page. Double
  tap zooms to 2.5× around the tap position; another double tap resets to fit.
  Pinch zoom and bounded panning are supported. Tap once to toggle Close; Android
  Back closes the viewer. System bars are temporarily revealed by an edge swipe.
- Settings panels retain separators between rows and omit the trailing separator
  after each section's last option.
- Safe only includes both General (`g`, `general`) and Safe (`s`, `safe`) ratings.
  The server query excludes Questionable and Explicit; a separate local check
  still rejects those and unknown ratings. The setting is not silently disabled.
- Empty states distinguish zero server posts, rating-filtered posts, unrelated
  posts and unusable media. Connection testing reports these counts without
  exposing credentials. Null/blank responses are errors rather than empty lists.

[Rule34 rating documentation](https://rule34.xxx/index.php?page=help&topic=rating)
[Rule34 search syntax](https://rule34.xxx/index.php?page=help&topic=cheatsheet)

## Connect

Open **Settings → API credentials**. Enter your numeric user ID and API key,
using **Save & connect**, or paste the complete `&api_key=…&user_id=…` string from
your account options into the key field. Blank key input preserves the saved
key. Existing credentials are retained on upgrade. Do not share your key in chat.
**Settings → Test connection** requests a small safe-rated post list independently
of your current search.

Keys are encrypted with Android Keystore AES-GCM. The app uses HTTPS directly,
with no proxy, ads, analytics, account syncing or remote collection storage.
API availability depends on the service and your credentials. Error reporting
separates authentication, browser verification, rate limits, DNS, TLS and timeout
failures. Raw server bodies and credential-bearing URLs are not displayed.

## Features

Explore and tag search; pagination; saved artwork and named collections; local
likes; full-screen pinch zoom; supported MP4/WebM playback; original/preview
file downloads to Downloads/M34 using MediaStore; system sharing; dark/light
appearance; three accents; 1–3 grid columns; image-cache controls; device-screen-
lock authentication; adaptive and themed launcher icons (M over 34).

Saves retain metadata. Remote images may require network access after cache
clearing; download files you want to retain offline. The grid keeps at most 90
posts to bound memory. Downloads have a 256 MB limit. Live Trending uses score
sorting. Some animated images preview as still images.

## Build and verify

Requires Python 3, JDK 17+ (21 recommended), Android SDK Platform 37, Build Tools,
and LLVM clang/lld capable of producing AArch64. Set `ANDROID_HOME` or pass `--sdk`.

```sh
bash scripts/test.sh
python3 scripts/build.py --sdk /absolute/path/to/android-sdk
python3 scripts/verify.py --sdk /absolute/path/to/android-sdk
```

The direct SDK build has no Maven dependencies. It recognizes `android-37` and
`android-37.0`. Use Build Tools 37.x when available. This workstation has Build
Tools 36.0.0; D8 warns about API 37 but successfully produces the verified APK.
The application uses established platform APIs without Java library desugaring.

Release signing reads private `signing-bundle/` files or these environment
variables: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
No signing secrets are packaged or included in the source archive. `--debug`
uses a separate debug identity.

Android Studio/Gradle files are included (AGP 9.4.1, Gradle 9.8, JDK 21). No
Gradle wrapper is bundled. The direct SDK script is the verified release build
path; Gradle release signing is not configured with secrets. The manual GitHub
Actions workflow builds with the same four repository secrets. Source/workflow
changes are local and have not been pushed or run on GitHub.

## Follow-up device QA

The next pass verified collection membership across rename/deletion and ten
on-device gesture/background-transfer assertions. Production code required no
changes. See [follow-up QA report](docs/QA-followup-1.0.9.md) for observations,
limits and the temporary instrumentation build/run commands.

## Validation limits

Host regression tests cover credentials/request encoding, General/Safe ratings,
post/media mapping, tag matching and recommendations, different-query request
URLs, stale-response rejection, sort/filter changes, and deferred system-bar
startup. Package verification checks release signatures, API 37 minimum/target,
ARM64-only ELF, 16 KB page alignment, APK alignment and secret-file exclusion.

ADB testing is now available on the connected Android phone. Real UI checks
covered artwork/fullscreen/Back navigation, collection create/rename/delete,
invalid collection input, typed search, clearing search, theme switching/restoring,
complete tag lists and a successful preview download. The device retained its
existing API credentials; no key values were read into this session. A temporary
QA collection was removed, and the original theme/download quality restored.
One preview image was created in Downloads/M34 by the download test.

App-lock credential verification, every media codec, and loss of network during
large/background downloads have not been exhaustively tested. Tests establish
specific behaviors, not that the app or external API is free of all bugs.

Design reference images in `design/` are concept artwork, not runtime screenshots.

## Transfer the local library to MBooru

Settings → Library transfer → Export creates a versioned `M34-library.json` through Android's document picker. In MBooru, open Saved → Import, select the file, review the counts, and confirm. Saved Rule34 metadata, local likes and collection memberships transfer; API credentials never transfer. Reconnect Rule34 in MBooru separately. Legacy demo artwork remains in M34. Unsaved likes whose metadata is unavailable import as unresolved references and can be repaired when that artwork is encountered again. Exporting leaves M34's library unchanged.
