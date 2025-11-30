# Chat Application - Complete Setup Guide

Complete installation and setup guide for the Chat Application, including Server (Node.js), WebAdmin (React), and Client (Android).

---

## 🚀 Quick Start

Get the system running in 1-2 minutes:

1. **Install Node.js & MongoDB**
   ```bash
   # Node.js: Download from https://nodejs.org/
   # MongoDB: Follow installation guide below or use MongoDB Atlas
   ```

2. **Setup Server**
   ```bash
   cd ServerNodeJS/Server
   npm install
   cp .env.example .env  # Edit .env with your configuration
   npm run dev
   ```

3. **Setup WebAdmin**
   ```bash
   cd WebAdmin
   npm install
   cp .env.example .env  # Edit .env with server IP
   npm run dev
   ```

4. **Setup Android Client**
   - Open Android Studio
   - Open `Client` folder
   - Edit `ServerConfig.java` → Set `SERVER_IP` to your server IP
   - Run app → Login/Register

**For detailed instructions, see:**
- [Server Setup Guide](ServerNodeJS/Server/README.md)
- [WebAdmin Setup Guide](WebAdmin/README.md)
- [Client Setup Guide](Client/README.md)

---

## 📋 Table of Contents

- [System Architecture](#system-architecture)
- [System Requirements](#system-requirements)
- [Quick Start](#-quick-start)
- [Connection Testing](#connection-testing)
- [Troubleshooting](#troubleshooting)
- [Project Structure](#project-structure)

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Chat Application                         │
└─────────────────────────────────────────────────────────────────┘

┌──────────────┐      REST API (/api/*)    ┌──────────────┐
│   Android    │ ─────────────────────────▶ │   Node.js    │
│   Client     │                             │   Server    │
│              │ ◀───────────────────────── │  (Express)   │
└──────────────┘      WebSocket/Socket.IO    └──────┬───────┘
                                                    │
┌──────────────┐      REST API (/api/*)             │
│   WebAdmin   │ ────────────────────────────────▶ │
│   (React)    │                                    │
└──────────────┘                                    │
                                                    │
                                                    ▼
                                            ┌──────────────┐
                                            │   MongoDB    │
                                            │  Database    │
                                            └──────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    Optional Components                          │
├─────────────────────────────────────────────────────────────────┤
│  • TURN Server (coturn/Cloud) - For WebRTC video calls        │
│  • MediaSoup SFU - For group video calls                       │
│  • SMTP Server - For email OTP registration                    │
│  • Gemini AI - For chat summarization                          │
└─────────────────────────────────────────────────────────────────┘
```

**Data Flow:**
- **Authentication**: Client/WebAdmin → REST API (`/api/auth/*`) → MongoDB
- **Real-time Messages**: Client ↔ Socket.IO ↔ Server → MongoDB
- **Video Calls**: Client ↔ WebRTC (STUN/TURN) ↔ MediaSoup SFU
- **File Uploads**: Client → REST API (`/api/upload/*`) → Server Storage → MongoDB

**API Structure:**
- All REST endpoints use `/api` prefix (e.g., `/api/auth/login`, `/api/chats`, `/api/messages`)
- No API Gateway pattern - direct Express.js routes
- Socket.IO for real-time communication (separate from REST API)

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
│       ├── .env.example     # Environment template
│       └── README.md        # Detailed server guide
│
├── WebAdmin/                # React Admin Panel
│   ├── src/
│   │   ├── pages/          # Admin pages
│   │   ├── api/            # API client
│   │   └── config.ts       # Config
│   ├── package.json
│   ├── .env.example        # Environment template
│   └── README.md           # Detailed WebAdmin guide
│
└── Client/                  # Android App
    ├── app/
    │   ├── src/main/
    │   │   ├── java/       # Java source code
    │   │   └── res/        # Resources
    │   └── build.gradle.kts
    └── README.md            # Detailed client guide
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
1. Check the detailed README files in each component directory
2. Check the Troubleshooting section
3. Check logs from Server, WebAdmin, and Client
4. Check network connectivity between components

---

**Happy Setup! 🎉**
