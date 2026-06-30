# Chat Application

A full-stack chat system including:

* Node.js Server (Express + Socket.IO + MongoDB)
* React Web Admin
* Android Client

---

## Quick Start

### 1. Requirements

* Node.js (v16+)
* MongoDB (local or Atlas)
* Android Studio

---

### 2. Start Server

```bash
cd ServerNodeJS/Server
npm install
cp .env.example .env
npm run dev
```

---

### 3. Start Web Admin

```bash
cd WebAdmin
npm install
cp .env.example .env
npm run dev
```

---

### 4. Run Android Client

* Open `Client` in Android Studio
* Set `SERVER_IP` in `ServerConfig.java`
* Run app

---

## Architecture

* REST API: `/api/*`
* Realtime: Socket.IO
* Database: MongoDB
* Optional: SMTP (OTP email), AI summarization

---

## Configuration

Update server IP in:

* `ServerNodeJS/Server/.env`
* `WebAdmin/.env`
* `Client/.../ServerConfig.java`

---

## Testing

* Server: `/api/server/health`
* WebAdmin: `http://localhost:5173`
* Client: login/register

---

## Common Issues

* MongoDB not running
* Wrong IP or port
* Firewall blocking
* Android emulator: use `10.0.2.2`

---

## Documentation

* Server: https://github.com/phu142857/chatappJava-NodejsServer/tree/main/NodejsServer/Server/README.md
* WebAdmin: https://github.com/phu142857/chatappJava-NodejsServer/tree/main/WebAdmin/README.md
* Client: https://github.com/phu142857/chatappJava-NodejsServer/tree/main/Client/README.md

---

## Project Structure

```
ServerNodeJS/Server   # Backend
WebAdmin              # Admin panel
Client                # Android app
```

---

## Notes

* All components must use the same server IP
* Optional services can be added later (email, AI)

---
