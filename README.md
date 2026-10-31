<p align="center">
  <a href="https://github.com/sakhanamit/OnFire-Leanback-Launcher/releases/download/FiretvOS7/OnFire-v1.0-FireTV-Stick-Lite-4K-FireOS7.apk">
    <img src="https://img.shields.io/badge/Download-Fire%20TV%20Stick%20APK-FF9900?style=for-the-badge&logo=amazon" alt="Download Fire TV APK" />
  </a>
  <a href="https://github.com/sakhanamit/OnFire-Leanback-Launcher/releases/download/GoogleTV/OnFire-v1.0-Universal-GoogleTV-AndroidTV.apk">
    <img src="https://img.shields.io/badge/Download-Google%20TV%20%26%20Android%20TV-3DDC84?style=for-the-badge&logo=android" alt="Download Google TV APK" />
  </a>
</p>
# 🔥 On Fire — Premium Leanback TV Launcher

<p align="center">
  <img src="logo.png" alt="On Fire Launcher Logo" width="160" height="160" style="border-radius: 36px; box-shadow: 0 10px 30px rgba(255, 75, 43, 0.4);" />
</p>

<p align="center">
  <strong>A modern, ultra-fast, liquid glass launcher engineered specifically for Amazon Fire TV Stick, Google TV, and Android TV devices.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Fire%20OS%205%20%7C%206%20%7C%207%20%7C%208-FF9900?style=flat-square&logo=amazon" alt="Fire OS" />
  <img src="https://img.shields.io/badge/Platform-Google%20TV%20%7C%20Android%20TV-3DDC84?style=flat-square&logo=android" alt="Android TV" />
  <img src="https://img.shields.io/badge/Architecture-Universal%20(ARMv7%20%2F%20ARM64)-blue?style=flat-square" alt="Architecture" />
  <img src="https://img.shields.io/badge/Resolution-1080p%20%2F%204K%20UHD-purple?style=flat-square" alt="Resolution" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=flat-square" alt="License" />
</p>

---

## ✨ Features

- 🎯 **5-Card Ergonomic Grid**: Exactly 5 large, high-contrast application cards per row (`310px × 170px`) optimized for comfortable 10-foot viewing from across the room.
- 🌫️ **50% Translucent Side Settings Drawer**: Sleek Apple TV-inspired side panel (`580px`) with real-time backdrop blur, large 26px bold typography, and direct system actions.
- ⚙️ **One-Click Native Fire TV Settings**: Opens the full Fire TV root settings grid (`MainSettingsActivity`) showing all 13 categories (Network, Display & Audio, Applications, Controllers & Bluetooth, My Fire TV, Accessibility, and more).
- 🕒 **Center-Stage Clock & Live Weather**: Dead-center typographic clock paired with live temperature, weather condition icons, and humidity powered by Google Weather API.
- 🎬 **Upward Fade Transition**: Application cards seamlessly fade out before reaching the clock and weather widget without obstructive blurs or stutter.
- ⚡ **Zero-Flicker Hardware Compositing**: Accelerated layer rendering with zero microsecond refresh flickers or dropped frames on low-power TV processors (Fire TV Stick Lite / Gen 1 / 4K).
- 🔊 **Native Remote D-Pad Sounds**: Integrated audio feedback matching the native Fire OS navigation clicks for every directional move and selection.
- 🧹 **Built-in Memory Booster**: One-click RAM cleaner that terminates background cached tasks and frees up memory instantly.

---

## 📸 Screenshots

| 01. Clean Centered Home Screen | 02. 5-Card Application Drawer |
|:---:|:---:|
| ![Home Screen Clean](screenshots/01_home_screen_clean.png) | ![Apps Drawer](screenshots/02_apps_drawer_5_cards.png) |

| 03. Smooth Upward Fade Behind Header | 04. 50% Translucent Settings Drawer |
|:---:|:---:|
| ![Fade Transition](screenshots/03_apps_fade_before_weather.png) | ![Settings Menu](screenshots/04_settings_menu_50pct_transparent.png) |

<p align="center">
  <strong>05. Direct Root Fire TV System Settings</strong><br/>
  <img src="screenshots/05_all_firetv_settings_opened.png" alt="All Fire TV Settings Opened" width="85%" />
</p>

---

## 📥 Download & Installation

Pre-compiled, cryptographically signed APKs are available in the [`releases/`](releases/) directory:

| Build Target | File | Minimum OS |
|:---|:---|:---|
| **Fire TV Stick (Lite, 4K, 4K Max)** | [`OnFire-v1.0-FireTV-Stick-Lite-4K-FireOS7.apk`](releases/OnFire-v1.0-FireTV-Stick-Lite-4K-FireOS7.apk) | Fire OS 7 / 8 (API 22–28) |
| **Google TV & Universal Android TV** | [`OnFire-v1.0-Universal-GoogleTV-AndroidTV.apk`](releases/OnFire-v1.0-Universal-GoogleTV-AndroidTV.apk) | Android 5.0 to Android 14+ |
| **Legacy Devices (Older Fire TV 1/2)** | [`OnFire-v1.0-Legacy-AndroidTV-FireOS5.apk`](releases/OnFire-v1.0-Legacy-AndroidTV-FireOS5.apk) | Fire OS 5 / Android 5.1 |

### Sideload via ADB:

```bash
# Connect to your Fire TV Stick / Android TV
adb connect <YOUR_DEVICE_IP>:5555

# Install the optimized APK
adb install -r releases/OnFire-v1.0-FireTV-Stick-Lite-4K-FireOS7.apk

# Launch On Fire Launcher
adb shell am start -n com.onfire.launcher/.MainActivity
```

---

## 🎮 Remote Control Navigation

| Key / Button | Action |
|:---|:---|
| **D-Pad Down** | Opens the application drawer and scrolls through app rows |
| **D-Pad Up** | Returns focus to the top or hides the apps drawer |
| **D-Pad Left / Right** | Navigates between application cards and settings items |
| **Center / Select** | Launches selected app or toggles settings |
| **Back / Return** | Closes the settings drawer or returns to clean home clock |

---

## 🛠️ Building from Source

### Prerequisites:
- Java JDK 8 or higher
- Android SDK build-tools (28.0.3)
- Windows PowerShell

### Automated Build:
Run the automated build script to compile and sign both universal and Fire OS APKs:

```powershell
powershell -ExecutionPolicy Bypass -File compile_both.ps1
```

The script will automatically compile Java sources, package assets, convert to DEX via D8, zip-align to 4-byte boundaries, and apply dual v1 + v2 APK signatures.

---

## 📂 Repository Structure

```
├── android_project/           # Full Android Studio source code
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── assets/        # index.html (Liquid Glass TV UI)
│   │   │   ├── java/          # MainActivity.java (Native bridges & intents)
│   │   │   └── res/           # Leanback banner & launcher icons
│   │   ├── build.gradle       # Gradle build configuration
│   │   └── release-key.jks    # Signing keystore
│   └── settings.gradle
├── releases/                  # Pre-compiled, dual-signed production APKs
├── screenshots/               # Verified 1080p screenshots from Fire TV
├── compile_both.ps1           # Fast independent command-line build pipeline
├── logo.png                   # Centered vector flame launcher logo
├── .gitignore                 # Standard Android/Gradle ignore rules
├── LICENSE                    # MIT License
└── README.md                  # Project documentation & visual guide
```

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
