# Chat Application - Complete Setup Guide

Complete installation and setup guide for the Chat Application, including Server (Node.js), WebAdmin (React), and Client (Android).

---

## 📋 Table of Contents

- [System Requirements](#system-requirements)
- [Part 1: Server Setup](#part-1-server-setup)
- [Part 2: WebAdmin Setup](#part-2-webadmin-setup)
- [Part 3: Client Setup](#part-3-client-setup)
- [Connection Testing](#connection-testing)
- [Troubleshooting](#troubleshooting)

---

## System Requirements

### Required Tools:

- **Node.js** (v16 or higher) - [Download](https://nodejs.org/)
- **MongoDB** (v5.0 or higher) - [Download](https://www.mongodb.com/try/download/community) or [MongoDB Atlas](https://www.mongodb.com/cloud/atlas)
- **npm** or **yarn** (comes with Node.js)
- **Android Studio** (for Client) - [Download](https://developer.android.com/studio)
- **Java JDK 11+** (for Android development)
- **Git** - [Download](https://git-scm.com/)

---

## Part 1: Server Setup

### 1.1. Install MongoDB

#### Option A: MongoDB Local (Windows/Linux/Mac)

**Windows:**
1. Download MongoDB Community Server from [mongodb.com](https://www.mongodb.com/try/download/community)
2. Run the installer and select "Complete" installation
3. MongoDB will run as a Windows Service

**Linux (Ubuntu/Debian):**
```bash
# Import MongoDB public GPG key
wget -qO - https://www.mongodb.org/static/pgp/server-7.0.asc | sudo apt-key add -

# Add MongoDB repository
echo "deb [ arch=amd64,arm64 ] https://repo.mongodb.org/apt/ubuntu jammy/mongodb-org/7.0 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-7.0.list

# Update and install
sudo apt-get update
sudo apt-get install -y mongodb-org

# Start MongoDB
sudo systemctl start mongod
sudo systemctl enable mongod

# Check status
sudo systemctl status mongod
```

**Mac (Homebrew):**
```bash
brew tap mongodb/brew
brew install mongodb-community
brew services start mongodb-community
```

#### Option B: MongoDB Atlas (Cloud - Recommended)

1. Sign up for an account at [MongoDB Atlas](https://www.mongodb.com/cloud/atlas)
2. Create a free cluster
3. Create a database user and whitelist IP (0.0.0.0/0 for development)
4. Get the connection string from "Connect" → "Connect your application"

### 1.2. Install Node.js

**Windows/Mac:**
- Download and install from [nodejs.org](https://nodejs.org/)
- Choose LTS version

**Linux:**
```bash
# Using NodeSource repository
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# Check version
node --version
npm --version
```

### 1.3. Configure Server

1. **Navigate to Server directory:**
```bash
cd ServerNodeJS/Server
```

2. **Install dependencies:**
```bash
npm install
```

3. **Create `.env` file in `ServerNodeJS/Server/` directory:**

```env
# ===== Server Configuration =====
PORT=49664
NODE_ENV=development

# ===== Database Configuration =====
# Local MongoDB
MONGODB_URI=mongodb://localhost:27017/chatapp

# Or MongoDB Atlas (replace with your connection string)
# MONGODB_URI=mongodb+srv://username:password@cluster0.xxxxx.mongodb.net/chatapp?retryWrites=true&w=majority

DB_NAME=chatapp

# ===== JWT Configuration =====
JWT_SECRET=your_super_secret_jwt_key_here_change_this_in_production_min_32_chars
JWT_EXPIRES_IN=7d
JWT_REFRESH_EXPIRES_IN=30d

# ===== Client URLs =====
# WebAdmin URL (will configure later)
WEBADMIN_URL=http://localhost:5173

# Android Client URL (IP of the machine running the server)
CLIENT_URL=http://YOUR_SERVER_IP:49664

# ===== Socket.IO Configuration =====
SOCKET_CORS_ORIGIN=*

# ===== Security =====
BCRYPT_SALT_ROUNDS=12

# ===== File Upload =====
MAX_FILE_SIZE=10mb
UPLOAD_DIR=./uploads

# ===== Email Configuration (For OTP Registration) =====
# Gmail example:
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=your-email@gmail.com
SMTP_PASS=your-app-password
SMTP_SECURE=false
SMTP_FROM="Chat App <no-reply@chatapp.com>"

# Or use other services:
# SMTP_HOST=smtp.mailtrap.io
# SMTP_PORT=2525
# SMTP_USER=your-mailtrap-user
# SMTP_PASS=your-mailtrap-password

# ===== AI Summarization (Optional) =====
# Get free API key from: https://makersuite.google.com/app/apikey
GEMINI_API_KEY=your_gemini_api_key_here

# ===== MediaSoup Configuration (For video calls) =====
MEDIASOUP_LISTEN_IP=0.0.0.0
MEDIASOUP_ANNOUNCED_IP=YOUR_PUBLIC_IP_OR_DOMAIN

# ===== TURN Server Configuration (Optional - For WebRTC calls) =====
# TURN server is needed when direct peer-to-peer connection fails (NAT/firewall issues)
# Leave empty if not using TURN server (will use STUN only)
TURN_URL=turn:YOUR_TURN_SERVER_IP:3478
TURN_USERNAME=your_turn_username
TURN_CREDENTIAL=your_turn_password

# ===== Admin User (For seedAdmin script) =====
ADMIN_EMAIL=admin@example.com
ADMIN_USERNAME=admin
ADMIN_PASSWORD=your_admin_password
```

**Important Notes:**
- Replace `YOUR_SERVER_IP` with the actual IP of the machine running the server (e.g., `192.168.1.100` or public IP if deployed)
- To get local IP: 
  - Windows: `ipconfig` → find IPv4 Address
  - Linux/Mac: `ifconfig` or `ip addr show`
  - Or use: `hostname -I` (Linux) or `ipconfig getifaddr en0` (Mac)
- Replace `JWT_SECRET` with a strong random string (at least 32 characters)
  - Generate one using: `node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"`
- If not using email OTP, you can skip the SMTP section (but registration won't work)
- `MEDIASOUP_ANNOUNCED_IP`: If server runs behind NAT/router, set public IP or domain. For local, can use local IP.
- `TURN_URL`, `TURN_USERNAME`, `TURN_CREDENTIAL`: Optional TURN server for WebRTC calls. See TURN Server Setup section below.

4. **Configure TURN Server (Optional):**

TURN (Traversal Using Relays around NAT) server is used for WebRTC video/audio calls when direct peer-to-peer connections fail due to NAT or firewall restrictions.

#### Option A: Self-hosted TURN Server (coturn)

**Install coturn:**

**Linux (Ubuntu/Debian):**
```bash
sudo apt-get update
sudo apt-get install coturn
```

**Linux (CentOS/RHEL):**
```bash
sudo yum install coturn
```

**Mac (Homebrew):**
```bash
brew install coturn
```

**Configure coturn:**

1. Edit `/etc/turnserver.conf` (Linux) or `/opt/homebrew/etc/turnserver.conf` (Mac):
```conf
# Listening IP and port
listening-ip=0.0.0.0
listening-port=3478

# Relay IP (your server's public IP)
external-ip=YOUR_PUBLIC_IP

# Realm (domain name)
realm=yourdomain.com

# User credentials (username:password)
user=your_turn_username:your_turn_password

# Enable STUN
stun-only=no

# Log file
log-file=/var/log/turn.log

# No authentication for local network (optional, for development)
no-cli
no-tls
no-dtls
```

2. Start coturn:
```bash
# Linux
sudo systemctl start coturn
sudo systemctl enable coturn

# Mac
brew services start coturn

# Or run manually
turnserver -c /etc/turnserver.conf
```

3. Add to Server `.env`:
```env
TURN_URL=turn:YOUR_PUBLIC_IP:3478
TURN_USERNAME=your_turn_username
TURN_CREDENTIAL=your_turn_password
```

**Firewall Configuration:**
- Open UDP ports: 3478 (TURN), 49152-65535 (RTP/RTCP range)
- Open TCP port: 3478 (TURN)

```bash
# Linux (ufw)
sudo ufw allow 3478/udp
sudo ufw allow 3478/tcp
sudo ufw allow 49152:65535/udp

# Linux (iptables)
sudo iptables -A INPUT -p udp --dport 3478 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 3478 -j ACCEPT
sudo iptables -A INPUT -p udp --dport 49152:65535 -j ACCEPT
```

#### Option B: Cloud TURN Services

**Twilio STUN/TURN:**
1. Sign up at [Twilio](https://www.twilio.com/)
2. Get credentials from Twilio Console → Network Traversal Service
3. Add to `.env`:
```env
TURN_URL=turn:global.turn.twilio.com:3478?transport=udp
TURN_USERNAME=your_twilio_username
TURN_CREDENTIAL=your_twilio_credential
```

**Xirsys:**
1. Sign up at [Xirsys](https://xirsys.com/)
2. Get credentials from dashboard
3. Add to `.env`:
```env
TURN_URL=turn:YOUR_XIRSYS_URL:3478
TURN_USERNAME=your_xirsys_username
TURN_CREDENTIAL=your_xirsys_password
```

**Metered.ca (Free tier available):**
1. Sign up at [Metered.ca](https://www.metered.ca/)
2. Get free TURN server credentials
3. Add to `.env`:
```env
TURN_URL=turn:a.relay.metered.ca:80
TURN_USERNAME=your_metered_username
TURN_CREDENTIAL=your_metered_password
```

**Note:** If TURN server is not configured, the app will use STUN servers (Google's public STUN) which may not work in all network environments (especially behind strict NAT/firewalls).

5. **Create Admin user (optional):**

Add to `.env` file:
```env
ADMIN_EMAIL=admin@example.com
ADMIN_USERNAME=admin
ADMIN_PASSWORD=your_admin_password
```

Then run:
```bash
npm run seed:admin
```

Note: Ensure MongoDB is connected before running this script.

5. **Start the server:**
```bash
# Development mode (with auto-reload)
npm run dev

# Production mode
npm start
```

Server will run at: `http://0.0.0.0:49664`

**Test the server:**
- Open browser: `http://localhost:49664/api/server/health`
- Or: `http://YOUR_SERVER_IP:49664/api/server/health`

**Test TURN server (if configured):**
```bash
# Using stunclient (install: sudo apt-get install stun-client)
stunclient YOUR_TURN_SERVER_IP 3478

# Or use online tools:
# https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/
# Add your TURN server credentials to test connectivity
```

---

## Part 2: WebAdmin Setup

### 2.1. Install Node.js (if not already installed)

See instructions in [Part 1.2](#12-install-nodejs)

### 2.2. Configure WebAdmin

1. **Navigate to WebAdmin directory:**
```bash
cd WebAdmin
```

2. **Install dependencies:**
```bash
npm install
```

3. **Create `.env` file in `WebAdmin/` directory:**

```env
# API Base URL - Replace YOUR_SERVER_IP with the IP of the machine running the server
VITE_API_BASE_URL=http://YOUR_SERVER_IP:49664
```

**Notes:**
- Replace `YOUR_SERVER_IP` with the IP of the machine running the server (same as in Server `.env`)
- If WebAdmin and Server run on the same machine: `http://localhost:49664`
- If running on different machines: `http://192.168.1.100:49664` (replace with actual IP)

4. **Start WebAdmin:**
```bash
# Development mode
npm run dev

# Development mode with IP binding (allows access from local network)
npm run dev:ip

# Build for production
npm run build
```

WebAdmin will run at: `http://localhost:5173`

**Access from other machines:**
- If using `npm run dev:ip`, WebAdmin will be accessible from: `http://YOUR_MACHINE_IP:5173`
- Ensure firewall allows port 5173

### 2.3. Login to WebAdmin

1. Open browser: `http://localhost:5173` (or `http://YOUR_MACHINE_IP:5173` if using `dev:ip`)
2. Login with admin account:
   - If you ran `npm run seed:admin` on Server, use credentials from `.env`:
     - Email: value of `ADMIN_EMAIL`
     - Password: value of `ADMIN_PASSWORD`
   - Or create admin user manually via API or MongoDB

---

## Part 3: Client Setup

### 3.1. Install Android Studio

1. Download Android Studio from [developer.android.com/studio](https://developer.android.com/studio)
2. Install and open Android Studio
3. Install Android SDK:
   - Open "SDK Manager" (Tools → SDK Manager)
   - Install Android SDK Platform 33+ and Build Tools
   - Install Android SDK Command-line Tools

### 3.2. Configure Server IP in Client

There are 2 ways to configure server IP:

#### Method 1: Edit directly in code (Recommended for development)

1. Open file: `Client/app/src/main/java/com/example/chatappjava/config/ServerConfig.java`

2. Find and edit the line:
```java
private static final String SERVER_IP = "103.75.183.125"; // Replace with your server IP
private static final int SERVER_PORT = 49664; // Server port
```

3. Replace `SERVER_IP` with the IP of the machine running the server:
   - Local network: `192.168.1.100` (replace with actual IP)
   - Public IP: Public IP of the server
   - Localhost (emulator): `10.0.2.2`

#### Method 2: Configure in app (Runtime)

1. Open the app on Android
2. Go to Settings → Server Settings
3. Enter:
   - Server IP: IP of the machine running the server
   - Server Port: `49664`
   - Use HTTPS: `false` (for development)
   - Use WSS: `false` (for development)
4. Restart app to apply changes

### 3.3. Configure Network Security (For HTTP)

1. Open file: `Client/app/src/main/res/xml/network_security_config.xml`

2. Add server domain (if not already present):
```xml
<domain includeSubdomains="true">YOUR_SERVER_IP</domain>
```

Example:
```xml
<domain includeSubdomains="true">192.168.1.100</domain>
<domain includeSubdomains="true">103.75.183.125</domain>
```

### 3.4. Build and Run Client

1. **Open project in Android Studio:**
   - File → Open → Select `Client` folder

2. **Sync Gradle:**
   - Android Studio will automatically sync
   - Or: File → Sync Project with Gradle Files

3. **Select device:**
   - Connect Android device via USB (enable USB Debugging)
   - Or create Android Virtual Device (AVD)

4. **Build and Run:**
   - Click "Run" button (▶️) or press `Shift + F10`
   - Or: `./gradlew installDebug` (from terminal)

### 3.5. Test Connection

1. Open the app on Android
2. Try to register/login
3. Check Logcat in Android Studio for errors (if any)

**Note when testing on Emulator:**
- Use `10.0.2.2` instead of `localhost` or `127.0.0.1`
- IP `10.0.2.2` is an alias for `localhost` in Android Emulator

---

## Connection Testing

### Test Server

```bash
# Health check
curl http://YOUR_SERVER_IP:49664/api/server/health

# Test API
curl http://YOUR_SERVER_IP:49664/api/auth/me
```

### Test WebAdmin

1. Open browser: `http://localhost:5173`
2. Check console (F12) for API errors (if any)

### Test Client

1. Open the app on Android
2. Try to register/login
3. Check Logcat in Android Studio

---

## Troubleshooting

### Server won't start

**Error: MongoDB connection failed**
- Check if MongoDB is running: `sudo systemctl status mongod` (Linux) or Services (Windows)
- Check if `MONGODB_URI` in `.env` is correct
- Check if firewall is blocking port 27017

**Error: Port already in use**
- Change PORT in `.env` or kill the process using that port
- Linux: `sudo lsof -i :49664` → `kill -9 PID`
- Windows: `netstat -ano | findstr :49664` → Task Manager → End Process

### WebAdmin can't connect to Server

**Error: Network Error or CORS**
- Check if `VITE_API_BASE_URL` in `.env` is correct
- Check if server is running
- Check if firewall is blocking port 49664
- Try accessing API directly: `http://YOUR_SERVER_IP:49664/api/server/health`

### Client can't connect to Server

**Error: Connection refused or Timeout**
- Check if `SERVER_IP` in `ServerConfig.java` is correct
- Check if server is running and accessible from network
- Check if firewall on server allows port 49664
- If using emulator: use `10.0.2.2` instead of `localhost`
- If using real device: ensure device and server are on the same WiFi network

**Error: Cleartext HTTP traffic not permitted**
- Check if `network_security_config.xml` has added the domain
- Ensure `AndroidManifest.xml` has: `android:networkSecurityConfig="@xml/network_security_config"`

### Email OTP not sending

**Error: SMTP authentication failed**
- Check if `SMTP_USER` and `SMTP_PASS` are correct
- For Gmail: need to create "App Password" instead of regular password
  - Go to Google Account → Security → 2-Step Verification → App passwords
- Check if firewall is blocking port 587/465

### MongoDB Atlas connection issues

**Error: Authentication failed**
- Check username/password in connection string
- Check if IP whitelist has added `0.0.0.0/0` (for development)
- Check if database user has read/write permissions

### Video/Audio calls not connecting

**Error: ICE connection failed or calls not working**
- Check if TURN server is configured correctly in `.env`
- Verify TURN server is accessible: `stunclient YOUR_TURN_SERVER_IP 3478`
- Check firewall allows UDP port 3478 and RTP range (49152-65535)
- For self-hosted TURN: Ensure `external-ip` in coturn config matches your public IP
- Test TURN server using [Trickle ICE tool](https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/)
- If using cloud TURN service, verify credentials are correct
- Check server logs for TURN connection errors
- Ensure `TURN_URL` format is correct: `turn:IP:PORT` or `turn:domain.com:PORT`

**Note:** Without TURN server, calls may fail when users are behind symmetric NAT or strict firewalls. TURN server relays traffic when direct connection is not possible.

---

## Project Structure

```
chatappJava-NodejsServer/
├── ServerNodeJS/
│   └── Server/              # Node.js Backend
│       ├── config/          # Database config
│       ├── controllers/     # API controllers
│       ├── models/          # MongoDB models
│       ├── routes/          # API routes
│       ├── socket/          # Socket.IO handlers
│       ├── utils/           # Utilities
│       ├── server.js        # Entry point
│       ├── package.json
│       └── .env              # Environment variables
│
├── WebAdmin/                # React Admin Panel
│   ├── src/
│   │   ├── pages/          # Admin pages
│   │   ├── api/            # API client
│   │   └── config.ts       # Config
│   ├── package.json
│   └── .env                # Environment variables
│
└── Client/                  # Android App
    └── app/
        ├── src/main/
        │   ├── java/       # Java source code
        │   └── res/        # Resources
        └── build.gradle.kts
```

---

## Summary of IPs to Configure

| Component | File | Variable | Example |
|-----------|------|----------|---------|
| **Server** | `ServerNodeJS/Server/.env` | `CLIENT_URL` | `http://192.168.1.100:49664` |
| **WebAdmin** | `WebAdmin/.env` | `VITE_API_BASE_URL` | `http://192.168.1.100:49664` |
| **Client** | `Client/app/.../ServerConfig.java` | `SERVER_IP` | `192.168.1.100` |

**Note:** Replace `192.168.1.100` with the actual IP of the machine running the server.

---

## Support & Help

If you encounter issues, please:
1. Check the Troubleshooting section
2. Check logs from Server, WebAdmin, and Client
3. Check network connectivity between components

---

**Happy Setup! 🎉**
