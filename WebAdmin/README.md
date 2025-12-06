# WebAdmin Setup Guide

Complete guide for setting up the React admin panel.

## Quick Start

```bash
cd WebAdmin
npm install
cp .env.example .env  # Edit with server IP
npm run dev
```

## Table of Contents

- [Installation](#installation)
- [Environment Configuration](#environment-configuration)
- [Running WebAdmin](#running-webadmin)
- [Production Build](#production-build)
- [Troubleshooting](#troubleshooting)

---

## Installation

### 1. Install Node.js

See [Server README](../ServerNodeJS/Server/README.md) for Node.js installation.

### 2. Install Dependencies

```bash
cd WebAdmin
npm install
```

---

## Environment Configuration

1. **Copy `.env.example` to `.env`:**
   ```bash
   cp .env.example .env
   ```

2. **Edit `.env` with server IP:**
   ```env
   VITE_API_BASE_URL=http://YOUR_SERVER_IP:49664
   ```

   - Same machine: `http://localhost:49664`
   - Different machine: `http://192.168.1.100:49664` (replace with actual IP)

---

## Running WebAdmin

### Development

```bash
npm run dev
```

Access at: `http://localhost:5173`

### Development with Network Access

```bash
npm run dev:ip
```

Access from other machines: `http://YOUR_MACHINE_IP:5173`

### Login

1. Open `http://localhost:5173`
2. Login with admin credentials:
   - Email: From `ADMIN_EMAIL` in Server `.env`
   - Password: From `ADMIN_PASSWORD` in Server `.env`

---

## Production Build

### Build

```bash
npm run build
```

Output: `dist/` directory

### Serve with Nginx

**Nginx config:**
```nginx
server {
    listen 80;
    server_name admin.yourdomain.com;
    
    root /path/to/WebAdmin/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

### Serve with Node.js

```bash
npm install -g serve
serve -s dist -l 5173
```

### Serve with PM2 (Production - Recommended)

**Prerequisites:**
```bash
npm install -g pm2 serve
```

**Build the application first:**
```bash
npm run build
```

**Start with PM2:**
```bash
# From WebAdmin directory
pm2 start ecosystem.config.js --env production

# Or from project root
pm2 start WebAdmin/ecosystem.config.js --env production
```

**PM2 Commands:**
```bash
# View status
pm2 status

# View logs
pm2 logs webadmin

# Stop
pm2 stop webadmin

# Restart
pm2 restart webadmin

# Delete from PM2
pm2 delete webadmin

# Save PM2 process list
pm2 save

# Setup PM2 to start on system boot
pm2 startup
```

**Alternative: Development mode with PM2:**
```bash
# Run Vite dev server with PM2 (not recommended for production)
pm2 start ecosystem.config.js --only webadmin-dev --env development
```

**Note:** For production, always use the `webadmin` app (serves built files). The `webadmin-dev` app is only for development purposes.

---

## Features

- **Dashboard**: System statistics and charts
- **User Management**: View, edit, delete users
- **Chat Management**: View and moderate chats
- **Post Management**: View, delete, hide posts
- **Reports**: Handle user reports
- **Statistics**: Analytics and insights
- **Security**: Monitor security events

---

## Troubleshooting

### Can't connect to server

- Verify `VITE_API_BASE_URL` in `.env` is correct
- Check server is running
- Check firewall allows port 49664
- Test: `curl http://YOUR_SERVER_IP:49664/api/server/health`

### CORS errors

- Check server CORS configuration
- Verify `WEBADMIN_URL` in Server `.env` matches WebAdmin URL

### Build errors

- Clear cache: `rm -rf node_modules package-lock.json && npm install`
- Check Node.js version: `node --version` (should be v16+)

---

For more details, see the [main README](../README.md).

