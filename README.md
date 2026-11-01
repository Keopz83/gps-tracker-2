# GPS Tracker

An Android app that records your route, distance, time and speed while jogging or riding a bicycle.

## Essential requirements

| ID    | Requirement |
|-------|-------------|
| REQ-1 | **Activity type & session control** — The user picks *Jogging* or *Cycling* and can start, pause, resume and stop a tracking session. Invalid transitions (e.g. pausing a session that has not started) are rejected. |
| REQ-2 | **GPS recording** — While a session is running the app records GPS positions. Positions received while idle, paused or stopped are ignored. To keep the track clean, a position is discarded when its accuracy is worse than 20 m, when it is less than 3 m from the previous recorded position (jitter), or when reaching it would require an implausible speed for the activity (jogging > 12 m/s, cycling > 30 m/s). |
| REQ-3 | **Distance** — The total distance is the sum of the great-circle (haversine) distances between consecutive recorded positions. The gap between the position before a pause and the first position after resuming is not counted. |
| REQ-4 | **Elapsed time** — The session shows the active time, excluding time spent paused, formatted as `MM:SS` (or `H:MM:SS` from one hour). |
| REQ-5 | **Speed / pace** — The session shows the average speed over the active time: pace in `min:ss /km` for jogging, `km/h` for cycling. Distance is shown in km with two decimals. |
| REQ-6 | **Background tracking & permissions** — Tracking keeps running with the screen off through a foreground service with a persistent notification. Before starting, the app requests precise location permission (plus notification permission on Android 13+); tracking only starts when location permission is granted. |
| REQ-7 | **Route map** — A *Map* button, enabled once a position has been recorded, opens a map (OpenStreetMap) showing the path taken so far, with the latest position marked. The path keeps growing while the map is open, and the gap between pausing and resuming is not drawn. |
| REQ-8 | **Run export (GPX)** — When a session is stopped, the run is saved as a GPX 1.1 file (one `<trkseg>` per segment between pauses, UTC timestamps, activity type `running`/`cycling`) so it can be imported into other sports apps such as Strava, Garmin Connect or Komoot. Runs without recorded positions are not saved. An *Export GPX* button shares the saved file through the Android share sheet. |
| REQ-9 | **Run history** — Every stopped run with recorded positions is added to a history that survives app restarts. A *History* button opens the list of runs, newest first, showing for each run its start date and time (local, `yyyy-MM-dd HH:mm`), activity type, distance (REQ-5) and active duration (REQ-4). |

## Project layout

- `app/src/main/java/com/example/gpstracker/domain` — platform-independent tracking logic (session, filtering, distance, formatting, permissions).
- `app/src/main/java/com/example/gpstracker` — Android layer: `TrackingService` (foreground location service), `TrackingRepository` (shared state) and `MainActivity` (Compose UI).
- `app/src/test` — JVM unit tests; each test name starts with the requirement it covers (e.g. `REQ-3 ...`).

## Build & test

Requires JDK 17 and the Android SDK (platform 35).

```sh
./gradlew testDebugUnitTest   # run the requirement tests
./gradlew assembleDebug       # build the APK
```

If `java` is not on your path, point Gradle at a JDK 17 first, e.g. `export JAVA_HOME=/opt/homebrew/opt/openjdk@17` (Homebrew `openjdk@17`). The SDK location is read from `local.properties` (`sdk.dir=...`), which is not committed.

## Deploy to a phone

1. **Enable developer mode** — tap *Build number* seven times (Samsung: *Settings › About phone › Software information*; Pixel: *Settings › About phone*).
2. **Connect the phone** with `adb` (in `<sdk>/platform-tools`), either:
   - **USB** — enable *Developer options › USB debugging*, plug in the cable and accept the prompt on the phone; or
   - **Wi‑Fi** (Android 11+, phone and computer on the same network) — enable *Developer options › Wireless debugging*, tap *Pair device with pairing code*, then:
     ```sh
     adb pair <ip>:<pairing-port>     # enter the pairing code shown on the phone
     adb connect <ip>:<port>          # the IP address & port shown on the Wireless debugging screen
     ```
3. **Check** the phone is listed as `device` (not `unauthorized`):
   ```sh
   adb devices -l
   ```
4. **Build, install and launch** (`-r` replaces an existing install and keeps its data):
   ```sh
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.example.gpstracker/.MainActivity
   ```
   `./gradlew installDebug` builds and installs in one step. With several devices connected, add `-s <serial>` to the `adb` commands (or set `ANDROID_SERIAL`).

Reinstalling stops a running tracking session. On first start, grant precise location (and notifications) when asked. The route map needs an internet connection for its map tiles.
