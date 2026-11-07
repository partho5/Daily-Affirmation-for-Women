# Daily Affirmation for Women

A peaceful, science-grounded native Android practice app built with **Kotlin**, **Jetpack Compose**, and **Material 3**. Designed from the ground up for genuine psychological comfort, self-compassion, and absolute privacy.

---

## 🌿 Philosophy & Purpose

Most affirmation apps inadvertently create psychological resistance by pushing unrealistic cheerfulness, streak counters, badges, and noisy notifications. 

**Daily Affirmation for Women** takes a fundamentally different, evidence-based approach:
- **No Streaks or Gamification:** Zero points, zero badges, and zero guilt for missed days.
- **Cognitive Pacing & Respect:** Practice ends with a clear, calming completion message (*"That is enough for today."*).
- **Adaptation Over Pressure:** If an affirmation feels too intense or untrue today, the app automatically adapts downward to a gentler phrasing.
- **100% Offline & Private:** Zero network permissions. No analytics, no tracking, and no accounts. Everything stays strictly on your device.

---

## ✨ Key Features

### 1. Guided Practice Flow (A → B → C → D)
Every session follows a calm 4-step sequence:
- **Screen A (Read):** Read the affirmation at your current comfort level with optional `{name}` personalization.
- **Screen B (Check):** A low-pressure resonance check: *"Does this feel true right now?"*
  - *Feels true:* Keeps the current level.
  - *Not yet:* Respectfully softens the category level (e.g., from Strong to Medium, or Medium to Gentle).
- **Screen C (Do):** A grounding reflection prompt with an optional reflection input box.
- **Screen D (Close):** A quiet wrap-up: *"That is enough for today. Carry it gently."*

### 2. Adaptive Levels (Gentle, Medium, Strong)
Each built-in affirmation provides three carefully calibrated phrasings:
- **Gentle:** Soft, open, unconditional acceptance (ideal when stressed or doubtful).
- **Medium:** Grounded, present-tense affirmations of capability and balance.
- **Strong:** Empowered, assertive affirmations of identity and strength.

### 3. Today's Practice Suggestion Engine
- Evaluates recent practice history, time of day, and category policies.
- Automatically reserves bedtime unload for evening hours.
- Prevents repeating the same sentence within 7 days.
- Rotates dynamically while respecting limited and opt-in category rules.

### 4. Specialized Modes
- **Bedtime Unload (Sleep Timer):**
  - Subdued, low-glare dark theme (`SleepTheme`).
  - Adjustable countdown timer (default 5 minutes).
  - Writing is on physical paper, leaving zero digital trace.
  - **Zero Audio:** Silent ending with a single gentle haptic vibration.
- **Deep Values Reflection:**
  - 10-minute expressive writing practice on core personal values.
  - No timer pressure or countdown anxiety.
  - Reflection answers are strictly ephemeral and discarded on exit.
- **User-Created Sentences:**
  - Full personal customization. Add your own meaningful affirmations.
  - Clean personal badge (`Mine`) with dedicated practice flow.

### 5. Accessibility & Design
- **WCAG 2.1 AA** contrast compliance across light, dark, and sleep color palettes.
- Supports system font scaling up to 200% without clipped text.
- Full screen reader (`TalkBack`) content descriptions on all interactive elements.
- Honors system `Reduced Motion` preferences by disabling long transitions.

---

## 🛡️ Privacy & Security

- **No Internet Access:** The `android.permission.INTERNET` permission is completely omitted from `AndroidManifest.xml`.
- **Zero Third-Party SDKs:** No ad networks, no analytics trackers, no crash reporting services.
- **Data Sovereignty:** All user preferences, history logs, and custom sentences are stored in local private storage.
- **Full Data Reset:** A single-tap *"Delete all data"* option in Settings immediately and permanently erases all local data.

---

## 🏗️ Architecture & Tech Stack

| Layer | Technology |
|---|---|
| **Platform** | Native Android (Min SDK 26, Target SDK 34) |
| **Language** | Kotlin 1.9.24 |
| **UI Toolkit** | Jetpack Compose 1.6.8 + Material 3 (1.2.1) |
| **Architecture** | Clean Architecture / Reactive State (`StateFlow`, `collectAsState`) |
| **Data Parsing** | Zero-overhead native `org.json` parser |
| **Persistence** | Local `SharedPreferences` with atomic JSON serialization |
| **Build System** | Gradle 8.9 + Android Gradle Plugin 8.5.0 |

---

## 🚀 Building & Testing

### Prerequisites
- JDK 17
- Android SDK (API 34)

### Build Debug APK
Build the debug APK using the helper script:
```bash
./build-debug-apk.sh
```
Or directly with Gradle:
```bash
./gradlew assembleDebug --no-daemon
```
The output APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```
*(Any build automatically creates a uniquely suffixed copy in `/home/haku/Dropbox/sync/` for instant device sideloading).*

### Run Unit Tests & Audits
Run the full 139-test compliance and logic audit suite:
```bash
./gradlew testDebugUnitTest --no-daemon
```

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
