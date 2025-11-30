# Android Client Setup Guide

Complete guide for setting up and building the Android client application.

## Quick Start

1. Open Android Studio
2. Open `Client` folder
3. Edit `ServerConfig.java` → Set `SERVER_IP`
4. Run app → Login/Register

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Server IP Configuration](#server-ip-configuration)
- [Network Security Configuration](#network-security-configuration)
- [Building and Running](#building-and-running)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

- **Android Studio** - [Download](https://developer.android.com/studio)
- **Java JDK 11+** - Included with Android Studio
- **Android SDK Platform 33+**
- **Build Tools**

---

## Installation

### 1. Install Android Studio

1. Download from [developer.android.com/studio](https://developer.android.com/studio)
2. Install and open Android Studio
3. Install Android SDK:
   - Tools → SDK Manager
   - Install Android SDK Platform 33+
   - Install Build Tools
   - Install Android SDK Command-line Tools

### 2. Open Project

1. File → Open
2. Select `Client` folder
3. Wait for Gradle sync to complete

---

## Server IP Configuration

### Method 1: Edit Code (Recommended for Development)

1. Open: `app/src/main/java/com/example/chatappjava/config/ServerConfig.java`

2. Edit:
   ```java
   private static final String SERVER_IP = "192.168.1.100"; // Your server IP
   private static final int SERVER_PORT = 49664;
   ```

3. IP options:
   - Local network: `192.168.1.100` (your server's local IP)
   - Public IP: Your server's public IP
   - Emulator: `10.0.2.2` (alias for localhost)

### Method 2: Runtime Configuration

1. Open app on Android
2. Settings → Server Settings
3. Enter:
   - Server IP: Your server IP
   - Server Port: `49664`
   - Use HTTPS: `false` (development)
   - Use WSS: `false` (development)
4. Restart app

---

## Network Security Configuration

For HTTP connections (development), configure network security:

1. Open: `app/src/main/res/xml/network_security_config.xml`

2. Add server domain:
   ```xml
   <domain includeSubdomains="true">YOUR_SERVER_IP</domain>
   ```

3. Example:
   ```xml
   <domain includeSubdomains="true">192.168.1.100</domain>
   <domain includeSubdomains="true">103.75.183.125</domain>
   ```

**Note:** For production, use HTTPS and remove cleartext traffic permission.

---

## Building and Running

### Build

1. **Sync Gradle:**
   - File → Sync Project with Gradle Files
   - Or wait for auto-sync

2. **Select Device:**
   - Connect Android device via USB (enable USB Debugging)
   - Or create Android Virtual Device (AVD)

3. **Run:**
   - Click Run button (▶️) or `Shift + F10`
   - Or: `./gradlew installDebug` (terminal)

### Build APK

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Release Build

1. Generate keystore:
   ```bash
   keytool -genkey -v -keystore chatapp-release.keystore -alias chatapp -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Configure `app/build.gradle.kts`:
   ```kotlin
   signingConfigs {
       create("release") {
           storeFile = file("chatapp-release.keystore")
           storePassword = "your_password"
           keyAlias = "chatapp"
           keyPassword = "your_password"
       }
   }
   ```

3. Build:
   ```bash
   ./gradlew assembleRelease
   ```

---

## Testing

### On Emulator

- Use `10.0.2.2` instead of `localhost` or `127.0.0.1`
- `10.0.2.2` is alias for host machine's localhost

### On Real Device

- Ensure device and server on same WiFi network
- Use server's local IP address
- Check firewall allows port 49664

### Check Logs

- Android Studio → Logcat
- Filter by package: `com.example.chatappjava`
- Look for connection errors

---

## Troubleshooting

### Connection refused/Timeout

- Verify `SERVER_IP` in `ServerConfig.java`
- Check server is running
- Check firewall allows port 49664
- For emulator: Use `10.0.2.2`
- For real device: Same WiFi network

### Cleartext HTTP not permitted

- Add domain to `network_security_config.xml`
- Verify `AndroidManifest.xml` has:
  ```xml
  android:networkSecurityConfig="@xml/network_security_config"
  ```

### Build errors

- Clean project: Build → Clean Project
- Invalidate caches: File → Invalidate Caches / Restart
- Check Android SDK is installed
- Check Java version: `java -version` (should be 11+)

### App crashes on startup

- Check Logcat for errors
- Verify server is accessible
- Check network permissions in `AndroidManifest.xml`

---

## Features

- **Authentication**: Login, Register (with OTP)
- **Chats**: Private and group chats
- **Messages**: Text, images, files, audio, video
- **Posts**: Create, view, like, comment, share
- **Calls**: Voice and video calls
- **Profile**: View and edit profile
- **Settings**: App and server configuration

---

For more details, see the [main README](../README.md).

