# AD&HD

A gamified, ADHD-friendly productivity app with a D&D tavern theme. Turn chores into quests,
build streaks, ride out urges, and level a hero — all offline, on-device.

> Package id: `com.focuspet.app` · minSdk 26 · targetSdk 35 · Jetpack Compose + Room

## Features

- **Quests (Tasks)** — log tasks, complete them for XP, gear loot drops, and combat flavor.
  Break any quest into **micro-steps**, or use **Roll for Priority** to let fate pick one.
- **Campaigns & bosses** — chapter progression with typed boss encounters.
- **Rituals (Habits)** — daily habit tracking with streaks and a backfill calendar.
- **Focus timer** — Pomodoro-style sessions with optional **Ambient Companions**
  (🔥 Hearth · 🌲 Wildwood · 🌧️ Rain) and a "Summon Bard's Song" music hook.
- **Status & Mana** — quick check-in on your condition and energy.
- **The Sanctuary** — an urge-surfing / reset flow ("Invoke a Blessing", "Consult the Oracle").
- **Streaks & protection** — clean-streak tracking with non-punitive resets and Chrono-Anchor
  Streak-Freezes.
- **Economy** — a Stronghold, Adventurer's Shop, gold, and artifacts.
- **Feel** — tactile haptics + sound on wins, loot, and level-ups; a global quick-capture FAB.

## Tech stack

- Kotlin, Jetpack Compose (Material 3), Navigation Compose
- Room (SQLite) for all persistence — **fully offline, no network/analytics**
- MVVM with a single `AppViewModel`
- Synthesized + sampled audio (`SoundPool`, `MediaPlayer`, `AudioTrack`), `Vibrator` haptics

## Build & run

Requirements: JDK 17, Android SDK (platform 35), an emulator or device on API 26+.

```bash
# Debug APK
./gradlew :app:assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk

# Install on a connected device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` (with your `sdk.dir`) is generated locally and is git-ignored.

## Sideloading

The debug APK is signed with the local debug key and installs on any phone with
"Install unknown apps" enabled for your file manager/browser. Copy the APK to the phone and
tap it, or run the `adb install` command above.

## Audio credits

The ambient Focus soundscapes are external recordings placed in `app/src/main/res/raw/`
(`amb_hearth.mp3`, `amb_nature.mp3`, `amb_rain.mp3`). **Verify each source's license permits
redistribution before committing them to a public repo** — if a license disallows it, remove
the files (the app auto-falls back to a built-in synthesized ambience) and list download links here.

- 🔥 Hearth — fire crackling — _source / license: TODO_
- 🌲 Wildwood — calm nature — _source / license: TODO_
- 🌧️ Rain — rain — _source / license: TODO_

## License

TODO — add a license (e.g. MIT) if you intend others to reuse the code.
