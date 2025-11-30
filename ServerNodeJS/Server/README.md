# Server Setup Guide

Complete guide for setting up the Node.js backend server.

## Quick Start

```bash
cd ServerNodeJS/Server
npm install
cp .env.example .env  # Edit with your configuration
npm run dev
```

## Table of Contents

- [Installation](#installation)
- [Environment Configuration](#environment-configuration)
- [MongoDB Setup](#mongodb-setup)
- [TURN Server Configuration](#turn-server-configuration)
- [Running the Server](#running-the-server)
- [API Endpoints](#api-endpoints)
- [Troubleshooting](#troubleshooting)

---

## Installation

### 1. Install Node.js

**Windows/Mac:**
- Download and install from [nodejs.org](https://nodejs.org/)
- Choose LTS version

**Linux:**
```bash
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs
node --version
npm --version
```

### 2. Install MongoDB

#### Option A: MongoDB Local

**Windows:**
1. Download from [mongodb.com](https://www.mongodb.com/try/download/community)
2. Run installer → "Complete" installation
3. MongoDB runs as Windows Service

**Linux (Ubuntu/Debian):**
```bash
wget -qO - https://www.mongodb.org/static/pgp/server-7.0.asc | sudo apt-key add -
echo "deb [ arch=amd64,arm64 ] https://repo.mongodb.org/apt/ubuntu jammy/mongodb-org/7.0 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-7.0.list
sudo apt-get update
sudo apt-get install -y mongodb-org
sudo systemctl start mongod
sudo systemctl enable mongod
```

**Mac:**
```bash
brew tap mongodb/brew
brew install mongodb-community
brew services start mongodb-community
```

#### Option B: MongoDB Atlas (Cloud)

1. Sign up at [MongoDB Atlas](https://www.mongodb.com/cloud/atlas)
2. Create free cluster
3. Create database user and whitelist IP (0.0.0.0/0 for development)
4. Get connection string from "Connect" → "Connect your application"

### 3. Install Dependencies

```bash
cd ServerNodeJS/Server
npm install
```

---

## Environment Configuration

1. **Copy `.env.example` to `.env`:**
   ```bash
   cp .env.example .env
   ```

2. **Edit `.env` with your configuration:**

See `.env.example` for all available options. Key variables:

- `MONGODB_URI`: MongoDB connection string
- `JWT_SECRET`: Generate with: `node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"`
- `CLIENT_URL`: IP of machine running server
- `SMTP_*`: Email configuration for OTP registration
- `TURN_*`: TURN server for WebRTC calls (optional)

---

## MongoDB Setup

### Local MongoDB

Default connection: `mongodb://localhost:27017/chatapp`

### MongoDB Atlas

1. Get connection string: `mongodb+srv://username:password@cluster0.xxxxx.mongodb.net/chatapp`
2. Update `MONGODB_URI` in `.env`

### Create Admin User

Add to `.env`:
```env
ADMIN_EMAIL=admin@example.com
ADMIN_USERNAME=admin
ADMIN_PASSWORD=your_admin_password
```

Run:
```bash
npm run seed:admin
```

---

## TURN Server Configuration

TURN server is needed for WebRTC video/audio calls when direct peer-to-peer connections fail.

### Option A: Self-hosted (coturn)

**Install:**
```bash
# Ubuntu/Debian
sudo apt-get install coturn

# Mac
brew install coturn
```

**Configure `/etc/turnserver.conf`:**
```conf
listening-ip=0.0.0.0
listening-port=3478
external-ip=YOUR_PUBLIC_IP
realm=yourdomain.com
user=your_turn_username:your_turn_password
stun-only=no
```

**Start:**
```bash
sudo systemctl start coturn
sudo systemctl enable coturn
```

**Firewall:**
```bash
sudo ufw allow 3478/udp
sudo ufw allow 3478/tcp
sudo ufw allow 49152:65535/udp
```

**Add to `.env`:**
```env
TURN_URL=turn:YOUR_PUBLIC_IP:3478
TURN_USERNAME=your_turn_username
TURN_CREDENTIAL=your_turn_password
```

### Option B: Cloud TURN Services

**Metered.ca (Free tier):**
1. Sign up at [Metered.ca](https://www.metered.ca/)
2. Get credentials
3. Add to `.env`:
```env
TURN_URL=turn:a.relay.metered.ca:80
TURN_USERNAME=your_metered_username
TURN_CREDENTIAL=your_metered_password
```

**Twilio/Xirsys:**
- See main README for configuration

---

## Running the Server

### Development

```bash
npm run dev  # Auto-reload with nodemon
```

### Production

```bash
npm start
```

### With PM2

```bash
npm install -g pm2
pm2 start server.js --name chatapp-server
pm2 save
pm2 startup
```

Server runs at: `http://0.0.0.0:49664`

**Test:**
```bash
curl http://localhost:49664/api/server/health
```

---

## API Endpoints

### Authentication
- `POST /api/auth/register/request-otp` - Request registration OTP
- `POST /api/auth/register/verify-otp` - Verify OTP and register
- `POST /api/auth/login` - Login
- `GET /api/auth/me` - Get current user
- `PUT /api/auth/profile` - Update profile
- `PUT /api/auth/change-password` - Change password

### Chats
- `GET /api/chats` - Get user's chats
- `POST /api/chats/private` - Create private chat
- `POST /api/chats/group` - Create group chat

### Messages
- `GET /api/messages/:chatId` - Get messages
- `POST /api/messages` - Send message

### Users
- `GET /api/users` - Search users
- `GET /api/users/:id` - Get user profile

### Posts
- `GET /api/posts` - Get posts
- `POST /api/posts` - Create post
- `GET /api/posts/search` - Search posts

See API documentation or Postman collection for complete list.

---

## Troubleshooting

### MongoDB connection failed
- Check MongoDB is running: `sudo systemctl status mongod`
- Verify `MONGODB_URI` in `.env`
- Check firewall allows port 27017

### Port already in use
- Change `PORT` in `.env`
- Or kill process: `sudo lsof -i :49664` → `kill -9 PID`

### Email OTP not sending
- Verify SMTP credentials
- For Gmail: Use App Password (not regular password)
- Check firewall allows port 587/465

### TURN server issues
- Test: `stunclient YOUR_TURN_SERVER_IP 3478`
- Verify firewall allows UDP 3478 and RTP range
- Check `external-ip` matches public IP

---

For more details, see the [main README](../../README.md).
