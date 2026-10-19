# Follow-up on-device QA: M34 v1.0.9

No production app changes were necessary in this pass. The installed M34 version
remains v1.0.9; new artifacts are the reproducible device-test harness and report.

## Collection membership (ADB UI interaction)

- Assigned artwork to a uniquely named temporary collection.
- Confirmed it appeared in that collection.
- Renamed the collection and confirmed membership survived.
- Deleted the temporary collection and confirmed the artwork remained saved.
- Restored the artwork's original saved state; no preexisting save was removed.
- Removed the temporary collection and returned to Explore.

## Gesture and background-transfer instrumentation

A separately installed package, `com.m34.viewer.qa`, was signed with the M34
identity so Android could run its instrumentation against the owned target app.
The working package, `com.m34.viewer`, was not replaced. No credential values
were read or exported. The QA package is removed after testing.

Ten on-device assertions passed:

1. Fullscreen viewer is laid out.
2. Initial zoom is exactly fit-to-screen (1.0).
3. Double-tap changes zoom to 2.5x.
4. Zoomed artwork pans in both axes.
5. A second double-tap resets zoom and pan.
6. Two-finger pinch changes zoom and respects the 1–5x limits (observed 3.3158x).
7. Live post metadata is available for the transfer test.
8. M34's task moves to the background immediately after starting a transfer.
9. The small preview transfer completes with the task backgrounded.
10. The uniquely named QA output is deleted through MediaStore.

Gesture checks used a synthetic 400×800 bitmap, not an account asset. The transfer
used a live preview URL only on the phone, with a unique QA filename. The test
also issued duplicate download calls; it does not separately assert the number
of transient pending rows, so exhaustive duplicate-download coverage is not
claimed. Background completion verifies Activity backgrounding, not survival of
process termination or Android's extended background restrictions.

An initial synthetic pinch failed because its events mixed input-source types;
Android canceled the stream. Consistent touchscreen sources fixed the harness,
and the existing production pinch code passed. This was not an app defect.

## Reproduce

With the local signing bundle present:

```sh
python3 scripts/build_device_tests.py
adb -s SERIAL install app/build/device-qa/M34-device-qa.apk
adb -s SERIAL shell am instrument -w com.m34.viewer.qa/com.m34.viewer.qa.PhoneInstrumentation
adb -s SERIAL uninstall com.m34.viewer.qa
adb -s SERIAL shell am start -W -n com.m34.viewer/.MainActivity
```

The phone must be manually unlocked. Temporary USB keep-awake configuration is
restored after the pass. Synthetic fixture and QA file cleanup run at test exit.
The API must be reachable with already configured in-app credentials for the
transfer assertions; credentials are never required on the host or in the test
APK. The harness changes no theme, rating, download-quality, or account settings.
