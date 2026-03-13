# TV 远程输入法 (TVKeyboard)

用手机为电视端远程输入文字，通过局域网 WebSocket 实时同步。

---

## 功能概述

| 设备 | 功能 |
|------|------|
| 📺 电视端 | 显示二维码、实时展示输入内容、确认/清空/收起按钮 |
| 📱 手机端 | 扫描二维码、在输入框中打字实时同步、发送确认 |

### 通信协议
- **传输层**：局域网 WebSocket（端口 8765）
- **电视端**作为 WebSocket **服务端**
- **手机端**作为 WebSocket **客户端**
- 消息格式：JSON  `{"type":"text","value":"..."}` / `{"type":"action","value":"confirm"}`

---

## 架构说明

```
app/src/main/java/com/tvkeyboard/
├── MainActivity.java              # 启动页，选择TV模式/手机模式
├── common/
│   ├── TvWebSocketServer.java     # WebSocket 服务端（电视侧）
│   ├── PhoneWebSocketClient.java  # WebSocket 客户端（手机侧）
│   ├── NetworkUtils.java          # 获取本机IP、二维码内容构建/解析
│   └── QrCodeGenerator.java      # 使用ZXing生成二维码Bitmap
├── tv/
│   ├── TvInputActivity.java       # 电视端独立Activity（独立App模式）
│   └── TvInputMethodService.java  # 电视端输入法Service（IME模式）
└── phone/
    ├── PhoneInputActivity.java    # 手机端输入界面
    └── QrScanActivity.java        # 手机端二维码扫描页
```

---

## 构建方法

### 前置条件
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 11+
- Android SDK 34
- 设备/模拟器 API 21+

### 步骤
1. 用 Android Studio 打开项目根目录 `TVKeyboard/`
2. 等待 Gradle 同步完成（会自动下载依赖）
3. 选择构建变体：Debug
4. 点击 **Run** 或执行 `./gradlew assembleDebug`
5. APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

### 依赖库
```gradle
// WebSocket 通信
implementation 'org.java-websocket:Java-WebSocket:1.5.4'
// 二维码生成
implementation 'com.google.zxing:core:3.5.2'
// 二维码扫描（封装了ZXing）
implementation 'com.journeyapps:zxing-android-embedded:4.3.0'
```

---

## 使用方式

### 方式一：独立App模式（推荐调试）

1. 在**电视**上安装并打开 App → 选择「电视端」
   - 屏幕显示二维码 + IP地址
   - 等待手机连接

2. 在**手机**上安装并打开同一个 App → 选择「手机端」
   - 点击「扫描二维码」
   - 对准电视屏幕扫描

3. 连接成功后，在手机输入框中打字
   - 内容**实时同步**显示在电视大屏上
   - 手机或电视均可点击「确认」或「清空」

### 方式二：IME 输入法模式

1. 安装后，在系统「设置 → 语言与输入法」中启用 **"TV 远程键盘"**
2. 在电视任意文本框中选择该输入法
3. 输入法面板自动显示二维码，手机扫码后即可输入
4. 点击「确认」会触发 `IME_ACTION_DONE`（相当于回车/完成）

---

## 二维码协议

二维码内容格式：`tvkb://192.168.1.100:8765`

手机扫描后解析出IP和端口，直接建立 WebSocket 连接。

---

## WebSocket 消息格式

### 手机 → 电视
```json
// 发送当前完整文本（实时同步）
{"type": "text", "value": "你好世界"}

// 发送操作
{"type": "action", "value": "confirm"}   // 确认
{"type": "action", "value": "backspace"} // 退格
{"type": "action", "value": "clear"}     // 清空
{"type": "action", "value": "dismiss"}   // 关闭
```

### 电视 → 手机
```json
// 同步当前文本（电视端操作后广播）
{"type": "sync", "value": "你好世界"}

// 通知操作结果
{"type": "action", "value": "confirmed"} // 已确认
{"type": "action", "value": "dismissed"} // 已关闭
```

---

## 注意事项

- 电视和手机必须在**同一局域网/WiFi**下
- 默认端口 **8765**，如有冲突可在 `TvWebSocketServer.DEFAULT_PORT` 修改
- Android 9+ 需要确保 WiFi 已连接（`ACCESS_WIFI_STATE` 权限已声明）
- 电视端需要允许网络监听，部分安全软件可能阻止（如提示防火墙请允许）

---

## 扩展建议

- [ ] 支持手动输入IP（无摄像头设备）
- [ ] 历史输入记录
- [ ] 多手机同时连接（已支持，服务端广播给所有客户端）
- [ ] 支持发送 Enter/Tab 等特殊键
- [ ] 支持语音识别输入
- [ ] 加密通信（TLS WebSocket）
