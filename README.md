# Ring-R (Android)

Native on-device version: paste a YouTube link → yt-dlp extracts audio locally
→ ffmpeg trims it → save the MP3 straight to the device. No backend, no
bandwidth caps, no cookies file to manage.

Built with `youtubedl-android` (https://github.com/yausername/youtubedl-android),
which bundles `yt-dlp` + `ffmpeg` for Android via Chaquopy.

## Before you open this in Android Studio

**I could not compile/run this in my environment** — no Android SDK or
emulator available here. Everything is written carefully against the
documented library API, but treat this as a first-pass scaffold that needs a
real build to shake out. Specific things to check first:

1. **`youtubedl-android` version** — `app/build.gradle.kts` pins
   `0.17.1`. Check https://github.com/yausername/youtubedl-android/releases
   for the current latest tag and bump it if newer.
2. **`FFmpeg.getInstance().execute(...)` signature** — I called it with a
   `String` array (`FFmpeg.getInstance().execute(command)` in
   `YtDlpManager.kt`). Some versions of the library instead expect a single
   space-joined command string. If this doesn't compile, switch to
   `FFmpeg.getInstance().execute(command.joinToString(" "))`.
3. **Gradle/AGP/Kotlin versions** — pinned to reasonably current versions as
   of early 2026 knowledge (AGP 8.5.2, Kotlin 1.9.24, compileSdk 34). Android
   Studio will prompt you to update if newer stable versions exist — safe to
   accept those prompts.

## Project structure

```
app/src/main/java/com/hexcorp/ringr/
├── RingRApp.kt              Application class — initializes yt-dlp + ffmpeg on startup
├── MainActivity.kt          Hosts Compose UI, switches between the 3 screens
├── ytdlp/YtDlpManager.kt    Wraps yt-dlp info/extract + ffmpeg trim calls
├── viewmodel/RingRViewModel.kt   State machine: LANDING → TRIM → FINALIZE
└── ui/
    ├── theme/               Colors + type matching the Ring-R mockups
    └── screens/              LandingScreen, TrimScreen, FinalizeScreen, EditableTitle
```

## Known simplification vs. the web version

`TrimScreen` currently uses a **slider to pick where a fixed-length clip
starts**, rather than a true draggable waveform region like the web app's
wavesurfer.js implementation. This gets full trim functionality working
without depending on a native waveform-rendering library. A real waveform
view (e.g. rendering amplitude data on a `Canvas`) is a reasonable follow-up
once the core flow is confirmed working.

## Things that matter once you're actually building/running this

- **yt-dlp goes stale** — YouTube changes break extractors periodically.
  Call `YoutubeDL.getInstance().updateYoutubeDL(context)` occasionally (e.g.
  on app start, off the main thread) to keep it current — same idea as
  updating the yt-dlp binary in the old backend's Dockerfile.
- **App size** — bundling a Python runtime (Chaquopy) + yt-dlp + ffmpeg is
  not small. Expect the APK to land somewhere in the 60-100+ MB range.
  Nothing to fix, just don't be surprised.
- **Distribution** — Play Store has previously pulled apps that facilitate
  downloading audio/video from platforms like YouTube, under their policies
  on facilitating infringement. Sideloading (direct APK) or an alternative
  store like F-Droid avoids that review risk entirely — same as how apps
  like "Seal" distribute primarily via F-Droid.
- **Legal scope stays the same as the web version** — this is for content
  you own or otherwise have the rights to use, same as everything we built
  for the backend.

## Build

Open the `RingR-android/` folder in Android Studio, let Gradle sync (it'll
pull `youtubedl-android` from JitPack — already added in
`settings.gradle.kts`), then Run on a device or emulator.
