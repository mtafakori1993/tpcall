# TP Call

Internal company VoIP voice calling client for Android.

`101 dials 203 → 203 rings → 203 accepts → both talk over WebRTC → either side hangs up`

## Design

Dark corporate theme based on the TP / Tafakor Pardazan brand palette
(deep-navy aurora backgrounds, `#69B437` green accent), inspired by Adobe
Spectrum design principles. Persian UI with the bundled Vazirmatn typeface.

The main screen is a proper in-app dialer:

```text
TP Call          ● متصل

محمد تفکری
داخلی 101

        203
     حسابداری
    ○ آفلاین

      1   2   3
      4   5   6
      7   8   9
      ⌫   0

        تماس
```

* The numeric keypad is the primary input — the software keyboard never opens.
* Dialing is limited to 3 digits; backspace removes one digit.
* When a complete 3-digit extension is entered, the backend is queried once
  (debounced) and the employee name + online status are shown inline.
* The Call button is enabled only for a valid, connected, non-self extension.

## Backend requirement

The backend (`tpcall-backend`) must be reachable on the company LAN:

```
Backend:   https://tpcallapi.tafakorpardazan.ir
```

Server health check:

```
https://tpcallapi.tafakorpardazan.ir/health
```

Both the phone and the server must be on reachable network segments.

> **Cleartext note:** the MVP uses plain HTTP/WebSocket inside the LAN. The app
> enables `android:usesCleartextTraffic="true"` for this reason. Migrate to
> HTTPS/WSS for production. The server address is centralized in
> `app/build.gradle.kts` (BuildConfig fields) and read by `AppConfig`.

## Build

```bash
./gradlew clean
./gradlew test
./gradlew assembleDebug
```

If Android lint is configured:

```bash
./gradlew lintDebug
```

## APK location

```
app/build/outputs/apk/debug/app-debug.apk
```

Install with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Required Android permissions

The app requests at runtime:

- **Microphone** (`RECORD_AUDIO`) — needed before the first call; without it no
  call can be established.
- **Notifications** (`POST_NOTIFICATIONS`, Android 13+) — needed to see
  incoming calls while the app is in the background.

Other declared permissions: `INTERNET`, `MODIFY_AUDIO_SETTINGS`, `VIBRATE`,
`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` (persistent signaling
service), `USE_FULL_SCREEN_INTENT` (incoming-call full-screen intent), and
`MANAGE_OWN_CALLS` (normal, auto-granted — required by Core-Telecom so TP Call
can register its own VoIP calls with Android Telecom). No Contacts/CallLog
permissions are requested: the corporate directory comes from the backend.

## Android Telecom integration (Core-Telecom)

Every TP Call VoIP call is also registered with Android Telecom through
`androidx.core:core-telecom:1.0.1` (`CallsManager`). WebRTC remains the media
engine; Telecom is used for system-level call integration.

- The app registers itself at startup (`TpCallApplication`) with
  `CallsManager.CAPABILITY_BASELINE` — audio only, no video capability.
- Incoming and outgoing calls are registered with Telecom keyed by the
  authoritative backend `call_id`. The mapping lives in
  `telecom/TpTelecomManager.kt`; per-call control wraps the `CallControlScope`
  in `telecom/TpTelecomCall.kt`.
- Accept / Reject / Hangup from the TP UI, the call-style notification, a
  headset or a watch all converge on the single idempotent controller actions:
  `startCall`, `acceptCurrentCall`, `rejectCurrentCall`, `endCurrentCall`.
- WebRTC connect/fail drives Telecom `setActive` / `disconnect`; backend
  `busy`/`offline`/`rejected`/`no_answer` terminate the Telecom call so no stale
  call is ever left registered.
- Audio routing cooperates with Telecom: the Speaker button requests a Telecom
  audio endpoint when a Telecom call is active, falling back to
  `AudioManager` otherwise. TP Call is **not** the default dialer and does not
  intercept SIM calls.

Notification behavior: the foreground signaling service (specialUse) owns the
call-notification lifecycle. For every ringing/active call the service publishes
the call notification — `Notification.CallStyle` on Android 12+ (API 31), a
high-priority full-screen fallback below that — **through `startForeground()`**
using one stable ID (`NOTIFICATION_ID_CALL = 1003`). The active-call
notification uses Android's built-in chronometer (`setWhen`/`setUsesChronometer`)
so no per-second reposting occurs. On hangup/reject/remote end the service calls
`stopForeground()` and restores the signaling notification. No CallStyle
notification is ever posted through bare `NotificationManager.notify()`, which
Android rejects on API 31+ with `IllegalArgumentException`.

Incoming-call ringtone channels (Xiaomi/MIUI):

- Incoming calls use a dedicated ringtone channel
  `tpcall_incoming_calls_v2` (IMPORTANCE_HIGH) whose sound is the default
  **RINGTONE** uri (never the notification uri) with
  `USAGE_NOTIFICATION_RINGTONE` / `CONTENT_TYPE_SONIFICATION` audio attributes,
  so incoming alerts follow the **Ring** volume, not Notification volume.
- The channel id is versioned because Android persists channel sound config;
  the old notification-sound channel (`calls`) is deleted on startup so a fresh
  ringtone channel is created.
- The active/ongoing call uses a separate silent channel
  (`tpcall_active_calls`) so no ringtone is ever replayed after answer.
- A controlled looping `Ringtone` fallback (in `AudioRouteManager`, using
  `TYPE_RINGTONE`) guarantees continuous ringing until answer/reject/remote
  end/timeout; it starts once per incoming call and stops idempotently.

## Signaling reconnection

The WebSocket client (`SignalingClient`) owns reconnection — no Activity,
service or controller layer opens sockets. It is production-safe:

- **Single reconnect worker**: a mutex/atomic guard ensures never more than one
  active socket and never more than one reconnect job, even when `onFailure`,
  `onClosed`, a network callback and a manual connect race.
- **Exponential backoff with jitter**: 1s, 2s, 4s, 8s, 15s, then capped at 15s,
  each ±20% jittered (never faster than 1s) so many devices don't reconnect in
  lockstep. Attempt counter resets after a successful reconnect.
- **Network awareness**: a `ConnectivityManager.NetworkCallback` pauses retries
  while offline and triggers an immediate reconnect when the network returns.
- **Heartbeat**: OkHttp protocol-level ping every 25s; a dead/half-open socket is
  detected and reconnected automatically.
- **Auth safety**: every reconnect re-authenticates with the stored JWT. On
  401/403 or an auth close code, reconnection stops and the session is cleared.
  `session_replaced` stops reconnecting immediately and returns to login.
- **State**: one authoritative `SignalingConnectionState` StateFlow
  (Disconnected / Connecting / Connected / Reconnecting(attempt)) drives the UI
  pill (متصل / در حال اتصال / در حال بازیابی ارتباط / قطع ارتباط) and disables
  starting new calls while reconnecting.
- **Call grace period**: if signaling drops during an active call, WebRTC media
  is not killed instantly; the call is given a 15s bounded grace window. If
  signaling reconnects the call continues; otherwise it ends locally and cleanly.
  No call is ever resurrected from local cache after reconnect — the backend
  `call_id` remains authoritative and stale events are ignored.

## Corporate Contacts (مخاطبین)

A new primary destination lists the internal directory from the backend
`GET /api/v1/users` (`[{name, extension, online}]`). No Android Contacts
permission is involved.

- Local search matches name or extension, with Persian/Arabic normalization
  (`ی/ي`, `ک/ك`, Persian digits, whitespace).
- List is sorted by extension; the logged-in employee is marked `شما` and the
  Call button is disabled (no self-call).
- Tapping the call icon routes through the same `startCall(extension)` used by
  the keypad and call history.
- Pull-to-refresh re-fetches the directory.

## Test flow (two devices)

Phone A:

```text
register: name=User 101, extension=101, password=12345678
```

Phone B:

```text
register: name=User 203, extension=203, password=12345678
```

1. Both phones stay logged in (a low-priority foreground notification confirms
   the signaling connection).
2. On phone A enter `203` and press **تماس**, or dial from **مخاطبین**.
3. Phone B rings (**تماس ورودی**, with ringtone + full-screen notification).
   The call is also visible to Android Telecom (system call UI / notification).
4. Phone B presses **پاسخ** — in the app, from the notification, or via a
   headset/watch if supported.
5. Caller creates the WebRTC offer, callee answers, ICE is exchanged, and both
   phones connect. Telecom reports the call as active.
6. Both sides talk (earpiece by default; **بلندگو** toggles the speaker through
   Telecom's audio endpoints).
7. Either side presses **قطع تماس**; the call ends cleanly on both devices and
   no stale Telecom call remains.

Also verify: reject, busy (a third extension calls an already-busy phone),
offline, no-answer timeout, mute, speaker, logout.

### Physical-device verification (required — cannot be automated in CI)

These flows need two real Android phones and must be exercised on-device:

1. **System incoming call**: with phone B in the background, A calls B. B
   receives a system-level incoming VoIP indication; answering from the system
   notification/UI accepts the TP Call call, WebRTC connects, and both apps
   reach the active state.
2. **Screen-off incoming call**: with B's screen off, A calls B. Verify the
   best-supported legitimate call experience (call-style notification) without
   relying on prohibited full-screen-intent tricks.
3. **Bluetooth routing**: during an active call, connect a Bluetooth headset.
   Verify Telecom exposes/routes to the BT endpoint, and that
   Speaker → Bluetooth → Earpiece transitions work.
4. **Headset controls**: headset Answer answers TP Call; headset Hangup ends it.
5. **SIM call conflict**: (a) TP Call active + a SIM call arrives, and (b) a SIM
   call active + TP Call incoming. Verify no audio chaos, microphone routing
   stays sane, TP Call follows Telecom decisions, and no crash.
6. **Speaker / earpiece / wired headset** routing across endpoints.
7. **No stale state**: after any end-of-call path, `dumpsys telecom` should not
   show a lingering TP Call session, and the app returns to Idle.

Process-death limitation: this MVP does not restore a call across complete
process death. After a process kill mid-call, TP Call returns to Idle on the
next launch and registers no phantom call; the backend's own call history
remains the authoritative record.

## WebRTC library

`io.github.webrtc-sdk:android:144.7559.12` (maintained Maven Central fork of
the classic `org.webrtc:google-webrtc` binary). Audio-only; no video, no SIP.

## WebSocket endpoint

```
wss://tpcallapi.tafakorpardazan.ir/api/v1/ws
```

Authenticated with `Authorization: Bearer <JWT>`.
