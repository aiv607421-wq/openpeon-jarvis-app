# J.A.R.V.I.S. — Mk VII (Android) · v1.2

A Tony Stark-style voice assistant app for Android (built for Helio G100 / Android 15).
Built around the **openpeon-jarvis** voice pack (45+5 authentic Paul Bettany-style
JARVIS lines, CC-BY-NC-4.0 by FlynnCruse).

## What it is

- **Wake-word voice core** — a foreground service listens continuously; say **"Jarvis"**
  and the rest of the sentence becomes the command.
- **Fully offline wake word** — Vosk (40 MB model + the word "jarvis" in a tight grammar)
  bundled in the APK; no internet needed to be woken. Falls back to the platform
  recognizer loop automatically if anything fails.
- **Voicepack personality** — his canonical lines ("Welcome home, sir.", "For you, sir,
  always.", "That's broken…") fire on start-up/ack/complete/error/low-battery events;
  dynamic replies use TTS (British-leaning when available).
- **40+ real device commands** — flashlight, apps, calls/SMS, volume/brightness,
  alarms/timers, reminders, notes, weather, diagnostics, math, web & YouTube.
- **Optional AI brain** — any OpenAI-compatible endpoint (default: Freebuff's
  `https://freebuff.com/api/v1/paid`) so JARVIS can answer literally anything, spoken.
- **Voiceprint lock (beta)** — say "Jarvis" 3× to enroll; other voices get politely ignored.
- **Car / headset mode** — Bluetooth SCO routing for mic + replies.
- **"Jarvis, any messages?"** — reads recent notifications aloud on request.
- **Widget + Quick Settings tile** + diagnostics report generator.

## Install

1. Grab `JARVIS-v1.2.apk` from Releases (or copy it to the phone directly).
2. Tap it; allow installing from unknown sources (self-signed).
3. Grant microphone + notifications; contacts/phone optional for calling.
4. Set JARVIS to **unrestricted battery** — MediaTek kills background apps aggressively.
5. Press the arc reactor once. Then: "Jarvis, what time is it?"

After a reboot, tap his notification (or open the app once) to re-arm the mic —
Android 15 requires one foreground interaction per boot.

## Command manual (highlights)

| Say | Does |
|---|---|
| Jarvis, flashlight on / off | Torch |
| Jarvis, open Telegram / WhatsApp / Settings | Launches any installed app |
| Jarvis, call Sarah / call +1… | Contacts → direct call |
| Jarvis, text Sarah saying I'm on my way | Drafts SMS |
| Jarvis, volume up/down/max/mute | Media volume |
| Jarvis, brightness 40% / up / down | Screen brightness |
| Jarvis, set an alarm at 7:30 am | Clock app |
| Jarvis, set a timer for 5 minutes | Clock app |
| Jarvis, remind me to water plants in 2 hours | Personal reminder alerts |
| Jarvis, take a note that… / read my notes | Private notes |
| Jarvis, what's the weather | Weather |
| Jarvis, battery status / phone status | Diagnostics |
| Jarvis, any messages? | Reads recent notifications aloud |
| Jarvis, calculate 34 times 12 | Math |
| Jarvis, search for… / open youtube / play X | Web & YouTube |
| Jarvis, how are you / who are you | The butler answers |
| Jarvis, stop listening / that will be all | Shuts the core down |
| Jarvis, <anything else> | AI brain (if configured) or web search |

## Building from source

```bash
bash scripts/install-toolchain.sh   # once per machine: JDK 17, Android SDK 35, vosk/jna jars
bash scripts/fetch-models.sh        # vosk models + voice pack into assets/ (not stored in git)
bash build.sh                       # -> build/JARVIS.apk
```

The signing keystore `jarvis.keystore` is intentionally not in git (see .gitignore):
APK *updates* must share one signature, so keep a copy of it somewhere safe and drop
it into the repo root before building. Its passphrase lives in build.sh.

## Permissions & privacy

All voice, notes and credentials live on the phone. Notifications are only read on
request. The AI key never leaves the device. The voice pack (CC-BY-NC-4.0) is
personal, non-commercial use. JARVIS/Marvel/Disney are not affiliated with this
fan project.
