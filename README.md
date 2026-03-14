

# TVKeyboard
--- 
[**English**] | [[简体中文](doc/README_zh.md)]

Use your phone as a remote keyboard for Android TV — type on your phone, see it instantly on the big screen.

| tv                  | phone                     |
|---------------------|---------------------------|
| ![tv](./doc/tv.jpg) | ![phone](./doc/phone.png) |


---

## How It Works

TVKeyboard runs as a system IME (Input Method) on your TV. When any app's text field gains focus, the IME panel appears showing a QR code. Scan it with your phone to open a web page — no app installation needed on the phone. Type in the browser and the text syncs to the TV in real time over your local network.

```
Phone browser  ──WebSocket──►  TV (WebSocket Server)
                                      │
                               HTTP Server (serves the web page)
                                      │
                               IME panel (shows QR code + live text)
```

---

## Features

- Works as a **system-wide IME** — works with any app that has a text field
- Phone uses a **browser** — no app install required on the phone
- **Real-time sync** via WebSocket over LAN
- Supports multiple phone connections simultaneously (last write wins)
- Remote control buttons from phone: backspace, clear, confirm, dismiss, D-pad navigation, back
- Semi-transparent IME panel so content behind remains visible

---

## Requirements

- Android TV device (tested on Huawei TV)
- Android SDK 21+
- Phone and TV on the **same Wi-Fi network**

---

## Build

**Prerequisites:** Android Studio, JDK 11+, Android SDK 34

```bash
# macOS / Linux
./gradlew assembleDebug

# Windows
.\gradlew.bat assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

**Install via ADB (recommended for TV):**
```bash
adb connect <TV_IP>
adb install -r app-debug.apk
```

---

## Setup

1. Install the APK on your TV
2. Go to **Settings → Language & Input → Current Keyboard** and enable **TV Remote Keyboard**
3. Set it as the default input method
4. Open any app with a text field and navigate to it — the IME panel will appear
5. Scan the QR code with your phone browser
6. Start typing

**Remote control:**
- `OK / Enter` — confirm input
- `Back` — dismiss IME

---

## Project Structure

```
app/src/main/java/com/tvkeyboard/
├── MainActivity.java                   # Mode selection (TV / Phone)
├── common/
│   ├── TvWebSocketServer.java          # WebSocket server (TV side)
│   ├── PhoneWebSocketClient.java       # WebSocket client (native phone app)
│   ├── TvHttpServer.java               # HTTP server serving the web input page
│   ├── NetworkUtils.java               # IP detection, QR content builder
│   └── QrCodeGenerator.java           # ZXing QR code bitmap generator
├── tv/
│   ├── TvInputActivity.java            # Standalone TV activity (App mode)
│   └── TvInputMethodService.java       # System IME service
└── phone/
    ├── PhoneInputActivity.java         # Native phone input UI
    └── QrScanActivity.java             # QR code scanner
```

---

## Ports

| Service | Port |
|---------|------|
| WebSocket | 8765 |
| HTTP (web page) | 8766 |

Both TV and phone must be on the same LAN. If ports are occupied, change `DEFAULT_PORT` in `TvWebSocketServer.java` and `HTTP_PORT` in `TvHttpServer.java`.

---

## WebSocket Protocol

**Phone → TV**
```json
{"type": "text",   "value": "hello",     "sessionId": "sess_abc123"}
{"type": "action", "value": "confirm",   "sessionId": "sess_abc123"}
{"type": "action", "value": "backspace", "sessionId": "sess_abc123"}
{"type": "action", "value": "clear",     "sessionId": "sess_abc123"}
{"type": "action", "value": "dismiss",   "sessionId": "sess_abc123"}
{"type": "action", "value": "back",      "sessionId": "sess_abc123"}
{"type": "action", "value": "dpad_up",   "sessionId": "sess_abc123"}
```

**TV → Phone**
```json
{"type": "sync",     "value": "hello", "sessionId": "sess_abc123"}
{"type": "action",   "value": "confirmed"}
{"type": "action",   "value": "cleared"}
{"type": "sessions", "count": 2}
```

---

## Dependencies

```gradle
implementation 'org.java-websocket:Java-WebSocket:1.5.4'
implementation 'com.google.zxing:core:3.5.2'
implementation 'com.journeyapps:zxing-android-embedded:4.3.0'
```

---

---

