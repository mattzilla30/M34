# M34 QA pass: v1.0.6 → v1.0.9

Three builds were installed over the existing app through ADB. The original
release signing identity and application data were retained. Device SDK: 37.
Phone interaction was performed through ADB input and UI Automator hierarchy
dumps. Screenshots used for the double-tap check were compared in memory only.
No API key values or account preference files were retrieved from the phone.

## Changes

- Collection editors keep input and display inline validation errors.
- Collection renaming preserves membership and ordering; names are validated
  case-insensitively and reserved/overlong names rejected.
- Collection creation is visible next to the Saved heading rather than hidden
  beyond the initial horizontal tab viewport.
- Complete artwork tag lists and an explicit copy-tags action are available.
- Gallery scroll position is restored after returning from detail.
- Clear-query controls were added. The phone test found the control missing
  when typing into an initially empty field; v1.0.9 fixes live visibility.
- Image-cache writes are atomic to avoid partial-file reads during concurrent
  preview requests.
- Downloads use a separate executor and duplicate taps are guarded.
- Video autoplay is restricted to once per artwork opening.
- Fullscreen artwork uses the secure-window flag when app lock is enabled and
  is dismissed when the locked app leaves the foreground.

## On-phone observations

| Check | Result |
| --- | --- |
| Open artwork details | Passed |
| Open fullscreen viewer | Passed |
| Double-tap changes the displayed image | Passed; screenshot hashes differ |
| Back from fullscreen to detail, then gallery | Passed |
| Saved and Settings navigation | Passed |
| Blank collection input remains in editor | Passed on updated build |
| Temporary collection create, rename, delete | Passed; QA collection removed |
| Typing a tag submits the search | Passed |
| Clear control appears while typing and clears query | Passed in v1.0.9 |
| Switch appearance and restore original theme | Passed |
| Open full artwork tag list | Passed |
| Refresh website tags changes visible suggestions | Passed |
| Preview download reaches Downloads/M34 | Passed; one preview file created |
| Restore original download quality | Passed |
| Gallery position retained on detail return | Passed |
| Fatal exception in current M34 process crash buffer | None found |

123 host checks passed (28 catalog, 8 startup lifecycle, 36 API contracts, 51
search/rating/tag/collection checks). The signed APK passed API-level, ARM64-only,
16 KB ELF page-alignment, ZIP-alignment and secret-file-exclusion verification.

The phone lock screen required manual unlocking. The original USB keep-awake
setting was saved for restoration after QA; lock authentication was not bypassed.

## Limits

Double-tap verification establishes a visual response, not pixel-perfect zoom
geometry. App-lock credential flow, clipboard transfer, collection membership
under rename, every video codec, interrupted downloads and extended background
execution were not exhaustively exercised on the phone. No claim of exhaustive
bug coverage or external API availability is made.
