<div align="center">
  <br>
  <img src="https://img.shields.io/badge/YouTubeClone-v1.0-FF0000?style=for-the-badge&logo=youtube&logoColor=white" alt="YouTubeClone">
  <br><br>
  <h1>📱 YouTubeClone</h1>
  <p>A feature-rich YouTube client for Android — built with Kotlin, Jetpack Compose, and ExoPlayer</p>
  <br>
  <a href="http://187.127.175.206:8080/app-release.apk">
    <img src="https://img.shields.io/badge/Download_APK-FF0000?style=for-the-badge&logo=android&logoColor=white" alt="Download APK">
  </a>
  &nbsp;
  <a href="https://github.com/codexskills/YouTubeClone">
    <img src="https://img.shields.io/badge/GitHub-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub">
  </a>
  <br><br>
</div>

---

## ✨ Features

| Feature | Status |
|---------|--------|
| 🎬 **Video Playback** with ExoPlayer | ✅ |
| ⏪ **Double-tap Seek** (←10s / +10s) | ✅ |
| 📐 **Playback Speed** (0.25x – 5x) | ✅ |
| 🎚️ **Video Quality** selector (Auto – 1080p) | ✅ |
| 🔁 **Loop Video** toggle | ✅ |
| 💬 **Captions / Subtitles** support | ✅ |
| 📊 **Stats for Nerds** (codec, bitrate, fps, etc.) | ✅ |
| 🖥️ **Fullscreen** with pinch-to-zoom | ✅ |
| 📺 **Mini Player** (drag-down picture-in-picture) | ✅ |
| 🏠 **Home Feed** with category tabs | ✅ |
| 📱 **Shorts** vertical feed | ✅ |
| 🔔 **Notifications** screen | ✅ |
| 👤 **Profile** with stats & history | ✅ |
| ⚙️ **Settings** (11 sub-pages) | ✅ |
| 🔐 **Login** (2-step, guest mode) | ✅ |
| 🌐 **InnerTube API** integration | ✅ |
| ⬇️ **Downloads** manager | ✅ |
| 🎨 **YouTube-style UI** (flat bottom nav, dark theme) | ✅ |

---

## 🖼️ Screenshots

```
📱 Coming Soon — Replace this section with actual screenshots
```

---

## 🚀 Installation

### Prerequisites
- Android 7.0+ (API 24)
- Internet connection (for streaming)

### Download
```bash
# Direct download
curl -O http://187.127.175.206:8080/app-release.apk

# Or install via ADB
adb install app-release.apk
```

### Build from Source
```bash
git clone https://github.com/codexskills/YouTubeClone.git
cd YouTubeClone
./gradlew assembleRelease
```

---

## 🛠️ Tech Stack

<div align="center">

| Technology | Purpose |
|------------|---------|
| **Kotlin** | Primary language |
| **Jetpack Compose** | UI framework |
| **ExoPlayer (Media3)** | Video playback |
| **InnerTube API** | YouTube data |
| **Coroutines + Flow** | Async operations |
| **Hilt** | Dependency injection |
| **DataStore** | Local preferences |
| **Gradle 9.4** | Build system |
| **Material 3** | Design system |

</div>

---

## 📁 Project Structure

```
app/
├── src/main/java/com/darkk/youtube/
│   ├── data/          # Repository, DataStore
│   ├── download/      # Download manager
│   ├── innertube/     # YouTube API client
│   ├── ui/
│   │   ├── components/  # Reusable composables
│   │   ├── screens/     # All screens (20+)
│   │   └── theme/       # Material theme
│   └── viewmodel/      # ViewModels
├── src/main/res/       # Resources, icons
└── build.gradle.kts    # Dependencies
```

---

## 🔧 Configuration

The app uses DataStore for persistent settings. Key preferences:

- Playback speed
- Default video quality
- Data saver mode
- Autoplay toggle
- Download path
- History & privacy settings

---

## 👨‍💻 Developer

<div align="center">
  <br>
  <table>
    <tr>
      <td align="center">
        <img src="https://avatars.githubusercontent.com/u/191073266?v=4" width="100" style="border-radius: 50%;" alt="Codex Skiller"><br>
        <b>Codex Skiller</b><br>
        <a href="https://github.com/codexskills">@codexskills</a><br>
        <a href="https://t.me/codex_update">📢 @codex_update</a><br>
        <a href="https://t.me/codexskills">💬 @codexskills</a>
      </td>
    </tr>
  </table>
  <br>
</div>

---

## 📜 License

```
This project is for educational purposes only.
YouTube is a trademark of Google LLC.
```

<div align="center">
  <br>
  <sub>Built with ❤️ by <a href="https://github.com/codexskills">Codex Skiller</a></sub>
  <br>
  <sub>Join <a href="https://t.me/codex_update">@codex_update</a> for updates</sub>
  <br><br>
</div>
