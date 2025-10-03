## NT118 Final Project - Android Chat Application

### Project Overview

This is a comprehensive multi-platform chat application consisting of three main components:

- **Android Client** – Mobile application for end users
- **Node.js Server** – Backend API and real-time communication server
- **Web Admin** – Web-based administration panel


### Monorepo Structure

```
NT118_FinalProject/
├── Client/                 # Android Application
│   ├── app/src/main/      # Main Android source code
│   └── build.gradle.kts
├── ServerNodeJS/          # Backend Server
│   └── Server/
│       ├── controllers/   # API controllers
│       ├── models/        # Database models
│       ├── routes/        # API routes
│       ├── socket/        # WebSocket handlers
│       └── server.js      # Main server file
└── WebAdmin/              # Web Administration Panel
    ├── src/
    │   ├── components/    # React components
    │   ├── pages/         # Admin pages
    │   └── api/           # API client
    └── package.json
```


## Features

### Android Client
- User authentication and registration
- Real-time messaging (Socket.IO)
- Group chat functionality
- Friend request system
- Voice/video calling (WebRTC activities)
- File/image sharing and avatar upload
- Optional notifications and rich chat UI dialogs

Required Android permissions are declared (Internet, Camera, Record Audio, Media/Storage). Cleartext traffic is enabled via `network_security_config` for development.

### Node.js Server
- RESTful API endpoints (auth, users, chats, messages, friend-requests, groups, upload, calls, server, statistics, security)
- WebSocket real-time communication via Socket.IO
- JWT authentication and role handling (admin/user/moderator)
- File upload handling (Multer), static serving from `/uploads`
- Database management using MongoDB with Mongoose
- Robust CORS configuration for `CLIENT_URL` and `WEBADMIN_URL`
- Helmet, morgan (non-production), centralized error handling

### Web Admin Panel
- User management dashboard
- Chat and group monitoring/management
- Call and statistics pages
- Friend request management
- Security monitoring
- Auth guards and role-based routing (admin-only sections), plus user-facing `Profile` and `My Chats`


## Technology Stack

### Android Client
- Language: Java
- Build Tool: Gradle (Kotlin DSL), Java 11 compatibility
- UI: Material Design
- Networking: OkHttp
- Real-time: Socket.IO client
- Media/UI: ImagePicker, Picasso, PhotoView
- Calls: Google WebRTC AAR

### Node.js Server
- Runtime: Node.js
- Framework: Express.js
- Database: MongoDB with Mongoose
- Authentication: JWT
- Real-time: Socket.IO 4.x
- File Upload: Multer
- Security/Utils: Helmet, CORS, morgan
- Validation: express-validator

### Web Admin Panel
- Framework: React with TypeScript
- Build Tool: Vite
- UI Library: Ant Design
- State Management: React hooks
- HTTP Client: Axios
- Routing: React Router


## Quick Start

### Prerequisites
- Android Studio (for Android development)
- Node.js (v18 or higher recommended)
- MongoDB
- Git

### 1. Clone Repository
```
git clone <repository-url>
cd NT118_FinalProject
```

### 2. Environment Setup

#### Server Configuration
Create `.env` file in `ServerNodeJS/Server/` (example):
```
PORT=49664
NODE_ENV=development
MONGODB_URI=mongodb://localhost:27017/chatapp
DB_NAME=chatapp
JWT_SECRET="WOzMf2fO7jy6hfjxHBXYNcfzcX6/CSySlQr/RjrKhxzOp1edU+4IAZtAQMXb3lYNjjkz+DN1wjZsHhxrQo0WJQ=="
JWT_EXPIRES_IN=7d
JWT_REFRESH_EXPIRES_IN=30d
CLIENT_URL=http://localhost:3000
SOCKET_CORS_ORIGIN=localhost:3000
BCRYPT_SALT_ROUNDS=12
MAX_FILE_SIZE=10mb
ADMIN_EMAIL=admin@example.com
ADMIN_USERNAME=admin
ADMIN_PASSWORD=Admin@123
VITE_API_BASE_URL=http://localhost:49664
WEBADMIN_URL=http://localhost:5173

```

Notes:
- The server reads `PORT` from the environment.
- `CLIENT_URL` and `WEBADMIN_URL` must match your app/browser origins for CORS and Socket.IO.

#### WebAdmin Configuration
Create `.env` file in `WebAdmin/`:
```
VITE_API_BASE_URL=http://localhost:3000
```

#### Android Configuration
- Ensure the app can reach your backend. If using an emulator, `http://10.0.2.2:<PORT>` points to the host machine.
- The project permits cleartext traffic in development via `network_security_config`.
- If there is a configurable API base URL in the client code, set it to your backend URL.

### 3. Installation & Running

#### Server
```
cd ServerNodeJS/Server
npm install
npm run dev   # or: npm start
```

The server exposes REST endpoints under `/api`. Health check: `/api/health`.

#### WebAdmin
```
cd WebAdmin
npm install
npm run dev
```

Open `http://localhost:5173`.

#### Android
1) Open `Client/` in Android Studio
2) Sync Gradle files (targetSdk 36, Java 11)
3) Build and run on device/emulator


## API Overview (High-Level)

Mounted under `/api` in `server.js`:
- `/api/auth` – register, login, logout, me, profile, change-password, refresh, upload-avatar, delete account
- `/api/users` – list, search, friends, contacts, online, status, CRUD/admin actions, role updates, reports, friendship admin
- `/api/chats` – chat listing and details
- `/api/messages` – message CRUD/retrieval
- `/api/friend-requests` – send/accept/reject/list
- `/api/groups` – groups and members management
- `/api/upload` – media upload endpoints
- `/api/calls` – call signaling endpoints
- `/api/server` – server info
- `/api/statistics` – statistics endpoints
- `/api/security` – security endpoints

Static files are served from `/uploads` with appropriate CORS headers.


## WebSocket Events (Typical)

Client → Server:
- `join_chat`, `leave_chat`, `send_message`, `typing`, `stop_typing`

Server → Client:
- `new_message`, `user_typing`, `user_stopped_typing`, `user_joined`, `user_left`


## Database Schema (Simplified Examples)

### User Model
```javascript
{
  username: String,
  email: String,
  password: String,
  avatar: String,
  isOnline: Boolean,
  lastSeen: Date,
  role: String // 'user' | 'admin' | 'moderator'
}
```

### Chat Model
```javascript
{
  participants: [ObjectId],
  type: String, // 'private' | 'group'
  name: String, // group name
  lastMessage: ObjectId,
  createdAt: Date
}
```

### Message Model
```javascript
{
  chat: ObjectId,
  sender: ObjectId,
  content: String,
  type: String, // 'text' | 'image' | 'file'
  timestamp: Date,
  readBy: [ObjectId]
}
```


## Realtime Messaging & Calls

- Socket.IO server is initialized in `ServerNodeJS/Server/server.js` and shared via `app.set('io', io)`.
- CORS origins are restricted by `CLIENT_URL` and `WEBADMIN_URL`.
- Android Client uses `io.socket:socket.io-client`.
- Ensure client and server Socket.IO versions are compatible.


## Security Features

- JWT-based authentication
- Password hashing (bcryptjs)
- Input validation and sanitization (express-validator)
- CORS configuration
- Helmet hardening and structured logging
- File upload checks and static file CORS
- Admin-only routes and role checks


## Seeding Admin User

```
cd ServerNodeJS/Server
npm run seed:admin
```


## Production Notes

- Set `NODE_ENV=production` to reduce logs
- Use a strong `JWT_SECRET` and managed MongoDB (e.g., Atlas)
- Serve the WebAdmin production build behind a reverse proxy
- Use HTTPS in production; disable cleartext traffic on Android


## Scripts

### Backend (from `ServerNodeJS/Server`)
- `npm run dev` – start server with nodemon
- `npm start` – start server
- `npm run seed:admin` – seed an admin account

### WebAdmin
- `npm run dev` – start Vite dev server
- `npm run build` – type-check and build production bundle
- `npm run preview` – preview production build


## Testing (High-Level)

- Android: unit/UI tests under `src/test` and `src/androidTest`
- Server: add API/socket tests (e.g., Jest) as needed
- WebAdmin: component tests (RTL) and E2E (Cypress) as desired


## Monitoring & Analytics (Optional)

- User activity tracking, message stats, call duration, error logging, performance metrics


## Version History

- v1.0.0 – Initial release with basic chat functionality
- v1.1.0 – Added group chat and file sharing
- v1.2.0 – Added voice/video calling
- v1.3.0 – Added web admin panel


## Contributing

1) Fork the repository
2) Create a feature branch
3) Make changes and add tests when applicable
4) Submit a pull request


## License

This is a final project for the NT118 course at University of Information Technology (UIT), Vietnam. This project is developed for educational purposes only and is not intended for commercial use.


## Team

- Coder: Nguyen Tai Phu
- Course: NT118 - Final Project
- Institution: UIT - VN


## Support

For support and questions:
- Create an issue in the repository
- Contact: nguyentaiphu980@gmail.com


