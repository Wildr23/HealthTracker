# HealthTracker (GymTracker OS)

Personal Android gym & biometrics tracker built for **Samsung Galaxy S25 Ultra + OnePlus Watch 3 (OHealth + Health Connect) + Xiaomi Smart Scale (`XMTZC05HM` BLE)** with **Jefit CSV history migration**, **PR / 1RM analytics**, **Smart Hybrid Calories**, **Muscle Stimulus & Recovery**, **Supabase cloud sync**, and **bilingual Ukrainian / English** support.

---

## Local Build Setup

Copy `local.properties.example` to `local.properties` and set your Android SDK path and Supabase project credentials (never commit `local.properties`):

```properties
sdk.dir=B\:\\android-sdk
supabase.url=https://your-project-ref.supabase.co
supabase.publishableKey=sb_publishable_your_key_here
```

If your Windows user path contains non-ASCII characters (e.g. Cyrillic), set `GRADLE_USER_HOME` to an ASCII directory:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:ANDROID_HOME = "B:\android-sdk"
$env:GRADLE_USER_HOME = "B:\gradle-home"

.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Output APK: `app\build\outputs\apk\debug\app-debug.apk`.

---

## Sideloading to Samsung S25 Ultra

1. Enable **Developer Options → USB Debugging** on your phone and connect via USB or Wireless Debugging.
2. Install the APK:
   ```powershell
   & "B:\android-sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
   ```

---

## First-Time Setup on Phone

1. **Import Jefit History:**
   - Open **Settings → Import from Jefit CSV** and pick your exported `.csv` file (e.g., `jefit_export.csv`).
   - Review the preview report and tap **Import Into App**.
2. **Connect Supabase Cloud Sync:**
   - Open **Settings → Cloud Database (Supabase)**, enter your email + password (or tap **Create Account**), then tap **Sync All to Cloud**.
3. **Grant Health Connect Permissions:**
   - Open **Settings → Health Connect** (or tap **Grant Access** on the Today screen).
   - Enable read permissions including **History (>30 days)** and **Background Read**.
   - Tap **Scan Watch Sources** and verify OHealth (`com.heytap.health.*` / `oplus`) is selected.
4. **Disable Jefit Health Connect Access:**
   - In Android Settings → Health Connect → App permissions → **JEFIT**, revoke write & read access so Jefit stops creating duplicate sessions.
5. **During Gym Sessions:**
   - Start **Strength training** on OnePlus Watch 3.
   - Start your Routine Day in HealthTracker (toggle **Took Pre-Workout ⚡** if applicable).
   - Tap **✓** after each set. When you finish, the app automatically merges OHealth HR + calories in the background without writing duplicate sessions back to Health Connect.
