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
