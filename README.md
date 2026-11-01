# Summit

Summit is an Android app that records your route, distance, time and speed while jogging or riding a bicycle.

## Essential requirements

| ID    | Requirement |
|-------|-------------|
| REQ-1 | **Activity type & session control** — The user picks *Jogging* or *Cycling* with the runner and bicycle icons at the bottom left (locked while a session is active), and can start, pause, resume and stop a tracking session. Invalid transitions (e.g. pausing a session that has not started) are rejected. |
| REQ-2 | **GPS recording** — While a session is running the app records GPS positions. Positions received while idle, paused or stopped are ignored. To keep the track clean, a position is discarded when its accuracy is worse than 20 m, when it is less than 3 m from the previous recorded position (jitter), or when reaching it would require an implausible speed for the activity (jogging > 12 m/s, cycling > 30 m/s). |
| REQ-3 | **Distance** — The total distance is the sum of the great-circle (haversine) distances between consecutive recorded positions. The gap between the position before a pause and the first position after resuming is not counted. |
| REQ-4 | **Elapsed time** — The session shows the active time, excluding time spent paused, formatted as `MM:SS` (or `H:MM:SS` from one hour). |
| REQ-5 | **Speed / pace** — The session shows the average speed over the active time: pace in `min:ss /km` for jogging, `km/h` for cycling. Distance is shown in km with two decimals. |
| REQ-6 | **Background tracking & permissions** — Tracking keeps running with the screen off through a foreground service with a persistent notification. Before starting, the app requests precise location permission (plus notification permission on Android 13+); tracking only starts when location permission is granted. |
| REQ-7 | **Route map** — The map view (map icon, REQ-16) shows a map (OpenStreetMap data, served free by OpenFreeMap and drawn with MapLibre) with a recorded path, its last position marked and the gap between pausing and resuming not drawn. While a session is running or paused, the map shows that session once a position has been recorded: the path taken so far, which keeps growing while the map is open. When no session is active, it shows a finished run instead (REQ-17). |
| REQ-8 | **Run export (GPX)** — When a session is stopped, the run is saved as a GPX 1.1 file (one `<trkseg>` per segment between pauses, UTC timestamps, activity type `running`/`cycling`) so it can be imported into other sports apps such as Strava, Garmin Connect or Komoot. Runs without recorded positions are not saved. An *Export GPX* button shares the saved file through the Android share sheet. |
| REQ-9 | **Run history** — Every stopped run with recorded positions is added to a history that survives app restarts. The history view (history icon, REQ-16) lists the runs, newest on top, one line per run with its start date and time (local, `yyyy-MM-dd HH:mm`), activity type icon, distance (REQ-5), active duration (REQ-4) and the name of the place where it started (town, else district, region or country). The place name is looked up with the phone's geocoder, which needs an internet connection; runs that cannot be named yet (offline, or recorded before place names were stored — their start is then read from their GPX file) are named the next time the app starts or a run is stopped. A run can be selected from the history to show it on the map (REQ-17). |
| REQ-10 | **Look & icon** — Every screen has a diagonal gradient background running from red (top left) through purple to blue (bottom right), with white text and controls. The launcher icon uses the same gradient as its background with a white, abstract Matterhorn-like mountain in front. |
| REQ-11 | **Battery efficiency** — GPS tracking accuracy comes first: while running, the GPS reports a position every second at the highest accuracy, with the screen on or off, and no position is dropped to save power. Within that limit, battery use is kept low: with the screen off, positions are collected by the GPS chip and delivered in batches (at most 30 s late, Android 12+) so the phone can sleep in between, and are delivered at once when the screen turns on; while paused, the GPS stays on for 1 minute (so a short stop resumes without waiting for a fix) and is then switched off until resuming; the notification lets the system count the elapsed time and is only refreshed when the distance or state changes; the elapsed time on screen is refreshed every second only while the screen is on. |
| REQ-12 | **Kilometre pace** — The front view shows the pace of the last completed kilometre and of the three kilometres before it (newest first, in `min:ss /km`), or `--:-- /km` before the first kilometre. A kilometre's time is measured between recorded positions (the moment it is completed is interpolated between the positions either side of it), so time and distance spent paused are not counted. |
| REQ-13 | **Settings** — The burger icon at the bottom right opens a settings view, the last view when swiping right (REQ-16). Settings are kept across app restarts. |
| REQ-14 | **Kilometre voice note** — An option, off by default, turned on and off either in the settings or with the tone icon at the top right of the front view (a speaker, crossed out while off), speaks a note each time a kilometre is completed with the total distance, the total active time and the last kilometre's pace in minutes and seconds per kilometre (e.g. *"3 kilometres. Time 16 minutes 5 seconds. Last kilometre 5 minutes 12 seconds per kilometre."*). Other audio such as music is lowered while it speaks. With the option on and the screen off, positions are batched for at most 5 s instead of 30 s (REQ-11) so the note comes on time. |
| REQ-15 | **Power saving warning** — Power saving can switch the GPS off while the screen is off (e.g. Samsung), which leaves gaps in the track that are drawn and counted as straight lines. When *Start* is pressed while power saving is on and limits location (or, before Android 9, whenever it is on), a note asks the user to turn power saving off, with *Open settings* (the power saving settings) or *Start anyway*. |
| REQ-16 | **Navigation** — A bar of icons at the bottom of every view holds, from the left, the activity types (REQ-1), then the map, the history and, at the bottom right, the settings burger icon; the icon of the open view is highlighted. The views are swiped through in the order of their icons, rotating: swiping right from the front view shows the map, then the history, then the settings, and the next swipe right returns to the front view; swiping left goes round the other way (from the front view to the settings). Picking an icon goes to its view the shortest way round. *Back* returns to the front view. On the map, a swipe pans the map, so the icons are used to leave it. |
| REQ-17 | **Run on the map** — Tapping a run in the history (REQ-9) selects it, highlights it in the list and moves to the map view. When no session is running or paused, the map shows the selected run, read from its saved GPX file (REQ-8), or the newest run when none is selected; starting a new session clears the selection. At the bottom of the map, over it, the shown run is summarised on one line with the same information as in the history: start date and time, activity type icon, distance, duration and place (for an active session the distance and duration so far, without a place). |

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
