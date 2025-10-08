# Chat Application Backend

## Email OTP Registration

The registration flow is now protected by an email OTP (6 digits) that expires in 1 minute.

Endpoints:
- POST `/api/auth/register/request-otp` with body `{ username, email, password }`
- POST `/api/auth/register/verify-otp` with body `{ email, otpCode }`

Environment variables for SMTP:
```
SMTP_HOST=
SMTP_PORT=587
SMTP_USER=
SMTP_PASS=
SMTP_SECURE=false
SMTP_FROM="Your App <no-reply@yourapp.com>"
```

Notes:
- Direct POST `/api/auth/register` is disabled and returns 400.
- OTP is valid for 60 seconds; after verification, the user is created and logged in.

Node.js backend for chat application with user authentication, 1:1 chat, and group chat features using WebSocket.

## Technologies Used

- **Express.js** - Web framework for REST API
- **MongoDB** with **Mongoose** - Database and ODM
- **JWT** - Authentication
- **Socket.IO** - Real-time communication
- **bcryptjs** - Password hashing
- **express-validator** - Input validation

## Project Structure

```
Server/
├── config/
│   └── database.js          # MongoDB connection config
├── controllers/
│   ├── authController.js    # Authentication logic
│   ├── userController.js    # User management
│   ├── chatController.js    # Chat management
│   └── messageController.js # Message handling
├── middleware/
│   └── authMiddleware.js    # JWT authentication middleware
├── models/
│   ├── User.js             # User schema
│   ├── Chat.js             # Chat schema
│   └── Message.js          # Message schema
├── routes/
│   ├── auth.js             # Authentication routes
│   ├── users.js            # User routes
│   ├── chats.js            # Chat routes
│   └── messages.js         # Message routes
├── socket/
│   └── signaling.js        # Video call signaling server
├── utils/
│   └── jwt.js              # JWT helper functions
├── server.js               # Entry point
└── package.json            # Dependencies
```

## Installation

1. Clone repository
2. Install dependencies:
   ```bash
   npm install
   ```

3. Create `.env` file in Server directory with environment variables:
   ```env
   # Server Configuration
   PORT=5000
   NODE_ENV=development

   # Database Configuration
   MONGODB_URI=mongodb://localhost:27017/chatapp
   DB_NAME=chatapp

   # JWT Configuration
   JWT_SECRET=your_super_secret_jwt_key_here_change_this_in_production
   JWT_EXPIRES_IN=7d
   JWT_REFRESH_EXPIRES_IN=30d

   # Client Configuration
   CLIENT_URL=http://localhost:3000

   # Socket.IO Configuration
   SOCKET_CORS_ORIGIN=http://localhost:3000

   # Security
   BCRYPT_SALT_ROUNDS=12

   # File Upload (if needed)
   MAX_FILE_SIZE=10mb
   ```

   **Important Notes:** 
   - Ensure MongoDB is running locally or change MONGODB_URI to MongoDB Atlas connection string
   - Change JWT_SECRET to a strong secret string in production

4. Start server:
   ```bash
   # Development mode with nodemon
   npm run dev
   
   # Production mode
   npm start
   ```

## API Endpoints

### Authentication
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login
- `POST /api/auth/logout` - Logout
- `GET /api/auth/me` - Get current user information
- `PUT /api/auth/profile` - Update personal information
- `PUT /api/auth/change-password` - Change password
- `POST /api/auth/refresh` - Refresh token

### Users
- `GET /api/users` - Get list of users
- `GET /api/users/:id` - Get user information by ID
- `PUT /api/users/status` - Update online/offline status
- `GET /api/users/contacts` - Get contact list
- `GET /api/users/search` - Search users
- `GET /api/users/online` - Get list of online users

### Chats
- `GET /api/chats` - Get user's chat list
- `GET /api/chats/:id` - Get detailed chat information
- `POST /api/chats/private` - Create private chat
- `POST /api/chats/group` - Create group chat
- `PUT /api/chats/:id` - Update group chat information
- `POST /api/chats/:id/participants` - Add member to group
- `DELETE /api/chats/:id/participants/:participantId` - Remove member from group
- `POST /api/chats/:id/leave` - Leave group
- `DELETE /api/chats/:id` - Delete chat

### Messages
- `GET /api/messages/:chatId` - Get messages in chat
- `POST /api/messages` - Send message
- `PUT /api/messages/:id` - Edit message
- `DELETE /api/messages/:id` - Delete message
- `POST /api/messages/:id/reactions` - Add reaction to message
- `DELETE /api/messages/:id/reactions` - Remove reaction from message
- `PUT /api/messages/:chatId/read` - Mark messages as read
- `GET /api/messages/:chatId/search` - Search messages in chat

## Socket.IO Events

### Authentication
- `authenticate` - Authenticate socket connection
- `authenticated` - Authentication successful response
- `authentication-error` - Authentication failed response

### User Status
- `user-online` - User online notification
- `user-offline` - User offline notification


## Features

### Authentication & Authorization
- JWT-based authentication
- Password hashing with bcrypt
- Role-based access control
- Token refresh mechanism

### User Management
- User registration and login
- Profile management
- Online status tracking
- User search functionality

### Chat System
- Private (1:1) chat
- Group chat with multiple participants
- Chat creation, update, deletion
- Participant management

### Messaging
- Real-time messaging
- Message types: text, image, file, video, audio
- Message reactions (emoji)
- Message editing and deletion
- Read receipts
- Message search

### Video Calling
- WebRTC signaling server
- Participant management
- Media state control (video/audio on/off)
- Screen sharing support
- In-call messaging

## Database Schema

### User Schema
- username, email, password
- profile (firstName, lastName, bio, phoneNumber)
- avatar, status (online/offline/away)
- lastSeen, isActive

### Chat Schema
- name, description, type (private/group)
- participants with roles (admin/moderator/member)
- avatar, settings, lastMessage, lastActivity

### Message Schema
- content, type, sender, chat
- replyTo, attachments, reactions
- readBy, editHistory, isEdited, isDeleted

## Error Handling

- Validation errors with express-validator
- Authentication errors
- Authorization errors
- Database errors
- Socket connection errors

## Security Features

- Password hashing
- JWT token validation
- Input validation and sanitization
- CORS protection
- Helmet security headers
- Rate limiting support
