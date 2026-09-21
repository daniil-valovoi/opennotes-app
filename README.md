# OpenNotes

A lightweight (<5 MB), fully offline, fully private notes app for Android. Simple and bloat-free alternative to 450+ MB Samsung Notes. Supports .sdocx files import - proprietary Samsung Notes format.

## Features

- **Basic tools only**: Text, checklists, item lists, and doodles. 
- **Bulk `.sdocx` import**: Direct import of Samsung Notes proprietary format. Select your `.sdocx` files and import them all at once.
- **100% Offline & Private**: Zero network permissions requested. No telemetry, cloud sync, trackers, logins, cookies.
- **Private Space compatible**: Fully functional inside Samsung Secure Folder and Android Private Spaces.

## Project Structure

- `app/` — Android application (Kotlin, Jetpack Compose, Room DB).
- `tools/sdocx-bridge/` — Rust bridge wrapping the `sdocx` crate for on-device parsing.
- `tools/build-android-libs.sh` — Script to cross-compile the Rust parser into `app/src/main/jniLibs/`.

## Build

### Prerequisites

- JDK 17
- Android SDK & NDK
- Rust & `cargo-ndk` (`cargo install cargo-ndk`)

### Steps

1. **Build native parser libraries**:
   ```sh
   ./tools/build-android-libs.sh
   ```

2. **Build the APK**:
   ```sh
   # Debug APK
   ./gradlew :app:assembleDebug

   # Release APK
   ./gradlew :app:assembleRelease
   ```

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE) (GPL-3.0) due to static linking with the GPL-3.0 `sdocx` Rust crate.
