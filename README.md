# NT118 Final Project - Multi-Platform Chat Application

## 📱 Project Overview

This is a comprehensive multi-platform chat application consisting of three main components:

- **Android Client** - Mobile application for end users
- **Node.js Server** - Backend API and real-time communication server  
- **Web Admin** - Web-based administration panel

## 🏗️ Project Structure

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

## ✨ Features

### Android Client
- User authentication and registration
- Real-time messaging
- Group chat functionality
- Friend request system
- Voice/video calling
- File sharing
- Push notifications

### Node.js Server
- RESTful API endpoints
- WebSocket real-time communication
- JWT authentication
- File upload handling
- Database management (MongoDB)
- Admin user management
- Statistics and analytics

### Web Admin Panel
- User management dashboard
- Chat monitoring
- Group management
- Call statistics
- Friend request management
- System statistics
- Security monitoring

## 🛠️ Technology Stack

### Android Client
- **Language**: Java
- **Framework**: Android SDK
- **Build Tool**: Gradle
- **UI**: Material Design
- **Networking**: Retrofit, OkHttp
- **Real-time**: Socket.IO

### Node.js Server
- **Runtime**: Node.js
- **Framework**: Express.js
- **Database**: MongoDB with Mongoose
- **Authentication**: JWT
- **Real-time**: Socket.IO
- **File Upload**: Multer
- **Validation**: Joi

### Web Admin Panel
- **Framework**: React with TypeScript
- **Build Tool**: Vite
- **UI Library**: Ant Design
- **State Management**: React hooks
- **HTTP Client**: Axios
- **Routing**: React Router

## 🚀 Quick Start

### Prerequisites
- Android Studio (for Android development)
- Node.js (v16 or higher)
- MongoDB
- Git

### 1. Clone Repository
```bash
git clone <repository-url>
cd NT118_FinalProject
```

### 2. Environment Setup

#### Server Configuration
Create `.env` file in `ServerNodeJS/Server/`:
```env
# Server Configuration
PORT=5000
NODE_ENV=development

# Database Configuration
MONGODB_URI=mongodb://localhost:27017/chat-app

# JWT Configuration
JWT_SECRET=your-super-secret-jwt-key-here-make-it-long-and-random
JWT_EXPIRES_IN=7d
JWT_REFRESH_EXPIRES_IN=30d

# File Upload Configuration
UPLOAD_PATH=./uploads
MAX_FILE_SIZE=10485760

# CORS Configuration
CLIENT_URL=http://localhost:3000
WEBADMIN_URL=http://localhost:5173

# Security Configuration
BCRYPT_ROUNDS=12
RATE_LIMIT_WINDOW_MS=900000
RATE_LIMIT_MAX_REQUESTS=100
```

#### WebAdmin Configuration
Create `.env` file in `WebAdmin/`:
```env
# API Configuration
VITE_API_BASE_URL=http://localhost:5000

# Development Configuration
VITE_DEV_MODE=true
```

#### Android Configuration
1. **Update Server IP** in `Client/app/src/main/java/com/example/chatappjava/config/ServerConfig.java`:
```java
private static final String SERVER_IP = "localhost"; // Change to your server IP
private static final int SERVER_PORT = 5000;
```

2. **Add IP to Network Security** in `Client/app/src/main/res/xml/network_security_config.xml`:
```xml
<domain-config cleartextTrafficPermitted="true">
    <!-- Android Emulator localhost -->
    <domain includeSubdomains="true">10.0.2.2</domain>
    <!-- Your server IP address -->
    <domain includeSubdomains="true">localhost</domain>
    <domain includeSubdomains="true">127.0.0.1</domain>
    <!-- Add your server's IP address here -->
    <domain includeSubdomains="true">YOUR_SERVER_IP_HERE</domain>
</domain-config>
```

**IP Configuration Notes:**
- **Android Emulator**: Use `10.0.2.2` to access localhost
- **Physical Device**: Use your computer's actual IP address
- **Find IP**: Windows (`ipconfig`) or Mac/Linux (`ifconfig`)

### 3. Installation & Running

#### Server
```bash
cd ServerNodeJS/Server
npm install
npm start
```

#### WebAdmin
```bash
cd WebAdmin
npm install
npm run dev
```

#### Android
1. Open `Client` folder in Android Studio
2. Sync Gradle files
3. Build and run on device/emulator

## 📚 API Documentation

### Authentication Endpoints
- `POST /api/auth/register` - User registration
- `POST /api/auth/login` - User login
- `POST /api/auth/logout` - User logout
- `GET /api/auth/me` - Get current user

### Chat Endpoints
- `GET /api/chats` - Get user chats
- `POST /api/chats` - Create new chat
- `GET /api/chats/:id/messages` - Get chat messages
- `POST /api/chats/:id/messages` - Send message

### Group Endpoints
- `GET /api/groups` - Get user groups
- `POST /api/groups` - Create new group
- `PUT /api/groups/:id` - Update group
- `DELETE /api/groups/:id` - Delete group

### User Management
- `GET /api/users` - Get all users (admin)
- `PUT /api/users/:id` - Update user
- `DELETE /api/users/:id` - Delete user

## 🔌 WebSocket Events

### Client to Server
- `join_chat` - Join a chat room
- `leave_chat` - Leave a chat room
- `send_message` - Send a message
- `typing` - User typing indicator
- `stop_typing` - Stop typing indicator

### Server to Client
- `new_message` - New message received
- `user_typing` - User typing notification
- `user_stopped_typing` - User stopped typing
- `user_joined` - User joined chat
- `user_left` - User left chat

## 🗄️ Database Schema

### User Model
```javascript
{
  username: String,
  email: String,
  password: String,
  avatar: String,
  isOnline: Boolean,
  lastSeen: Date,
  role: String // 'user' or 'admin'
}
```

### Chat Model
```javascript
{
  participants: [ObjectId],
  type: String, // 'private' or 'group'
  name: String, // for group chats
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
  type: String, // 'text', 'image', 'file'
  timestamp: Date,
  readBy: [ObjectId]
}
```

## 🔒 Security Features

- JWT-based authentication
- Password hashing with bcrypt
- Input validation and sanitization
- CORS configuration
- Rate limiting
- File upload security
- Audit logging
- IP blocking capabilities

## 🚀 Deployment

### Android App
1. Generate signed APK in Android Studio
2. Upload to Google Play Store or distribute directly

### Server
1. Deploy to cloud platform (Heroku, AWS, DigitalOcean)
2. Set up MongoDB Atlas for production database
3. Configure environment variables
4. Set up SSL certificate

### Web Admin
1. Build production version: `npm run build`
2. Deploy to static hosting (Netlify, Vercel, AWS S3)

## 🧪 Testing

### Android
- Unit tests in `src/test/`
- UI tests in `src/androidTest/`

### Server
- API tests using Jest
- WebSocket tests

### Web Admin
- Component tests using React Testing Library
- E2E tests using Cypress

## 📊 Monitoring & Analytics

- User activity tracking
- Message statistics
- Call duration metrics
- Error logging and monitoring
- Performance metrics

## 🔧 Environment Variables Reference

### Server (.env)
- `PORT`: Server port (default: 5000)
- `NODE_ENV`: Environment mode (development/production)
- `MONGODB_URI`: MongoDB connection string
- `JWT_SECRET`: Secret key for JWT token signing
- `JWT_EXPIRES_IN`: JWT token expiration time
- `JWT_REFRESH_EXPIRES_IN`: Refresh token expiration time
- `UPLOAD_PATH`: Directory for file uploads
- `MAX_FILE_SIZE`: Maximum file size in bytes (10MB default)
- `CLIENT_URL`: Android client URL for CORS
- `WEBADMIN_URL`: WebAdmin URL for CORS
- `BCRYPT_ROUNDS`: Password hashing rounds
- `RATE_LIMIT_WINDOW_MS`: Rate limiting window in milliseconds
- `RATE_LIMIT_MAX_REQUESTS`: Maximum requests per window

### WebAdmin (.env)
- `VITE_API_BASE_URL`: Backend API base URL
- `VITE_DEV_MODE`: Development mode flag

### Security Notes
1. **Never commit .env files** to version control
2. **Use strong, unique JWT secrets** in production
3. **Change default passwords** for database and services
4. **Use HTTPS** in production environments
5. **Regularly rotate secrets** and API keys

## 📝 Version History

- **v1.0.0** - Initial release with basic chat functionality
- **v1.1.0** - Added group chat and file sharing
- **v1.2.0** - Added voice/video calling
- **v1.3.0** - Added web admin panel

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 👥 Team

- **Developer**: [Your Name]
- **Course**: NT118 - Final Project
- **Institution**: [Your Institution]

## 📞 Support

For support and questions:
- Create an issue in the repository
- Contact: nguyentaiphu980@gmail.com

---

**Note**: This is a final project for NT118 course. Please ensure all components are properly configured before running the application.