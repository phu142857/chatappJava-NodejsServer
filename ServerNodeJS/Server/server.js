const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const path = require('path');
const { createServer } = require('http');
const { Server } = require('socket.io');
require('dotenv').config();

// Import routes
const authRoutes = require('./routes/auth');
const userRoutes = require('./routes/users');
const chatRoutes = require('./routes/chats');
const messageRoutes = require('./routes/messages');
const friendRequestRoutes = require('./routes/friendRequests');
const groupRoutes = require('./routes/groups');
const uploadRoutes = require('./routes/upload');
const callRoutes = require('./routes/call');
const serverRoutes = require('./routes/server');
const statisticsRoutes = require('./routes/statistics');
const securityRoutes = require('./routes/security');
const reportRoutes = require('./routes/reports');

// Import socket handler
const SocketHandler = require('./socket/socketHandler');


// Import database config
const connectDB = require('./config/database');

const app = express();
const server = createServer(app);
const io = new Server(server, {
  cors: {
    origin: function (origin, callback) {
      // Allow requests with no origin (like mobile apps)
      if (!origin) return callback(null, true);
      
      // In development, allow all origins for easier testing
      if (process.env.NODE_ENV === 'development') {
        return callback(null, true);
      }
      
      const allowedOrigins = [
        process.env.CLIENT_URL,
        process.env.WEBADMIN_URL,
        'http://10.197.192.224:5173', // Mobile development server
        'http://10.150.160.224:5173', // Mobile development server
        'http://10.72.206.224:5173', // Mobile development server
        'http://103.75.183.125:5173', // Mobile development server  
        'http://192.168.2.123:5173', // WebAdmin development server
        'http://localhost:5173', // Local WebAdmin development
        'http://127.0.0.1:5173', // Local WebAdmin development
        // Add regex patterns for flexible IP matching
        /^https?:\/\/.*:5173$/, // Allow any IP on port 5173
        /^https?:\/\/.*:3000$/, // Allow any IP on port 3000
        /^https?:\/\/localhost:.*$/, // Allow localhost on any port
        /^https?:\/\/127\.0\.0\.1:.*$/, // Allow 127.0.0.1 on any port
        /^https?:\/\/.*\.local:.*$/ // Allow .local domains
      ].filter(Boolean); // Remove undefined values
      
      const isAllowed = allowedOrigins.some(allowedOrigin => {
        if (typeof allowedOrigin === 'string') {
          return origin === allowedOrigin;
        } else if (allowedOrigin instanceof RegExp) {
          return allowedOrigin.test(origin);
        }
        return false;
      });
      
      if (isAllowed) {
        callback(null, true);
      } else {
        console.log('Socket.IO CORS blocked origin:', origin);
        callback(new Error('Not allowed by CORS'));
      }
    },
    methods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    credentials: true,
    allowedHeaders: [
      "Content-Type", 
      "Authorization", 
      "Accept",
      "Origin",
      "X-Requested-With",
      "Cache-Control",
      "Pragma"
    ]
  }
});

const PORT = process.env.PORT;

// Reduce noisy logs in production
if (process.env.NODE_ENV === 'production') {
  const noop = () => {};
  console.log = noop;
  console.debug = noop;
}

// Make io accessible to routes
app.set('io', io);

// Connect to MongoDB
connectDB();

// Middleware
app.use(helmet());

// Handle preflight requests
app.options('*', (req, res) => {
  res.header('Access-Control-Allow-Origin', req.headers.origin || '*');
  res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization, Accept, Origin, X-Requested-With, Cache-Control, Pragma');
  res.header('Access-Control-Allow-Credentials', 'true');
  res.header('Access-Control-Max-Age', '86400'); // 24 hours
  res.sendStatus(200);
});

app.use(cors({
  origin: function (origin, callback) {
    // Allow requests with no origin (like mobile apps or curl requests)
    if (!origin) return callback(null, true);
    
    // In development, allow all origins for easier testing
    if (process.env.NODE_ENV === 'development') {
      console.log('Development mode: Allowing origin:', origin);
      return callback(null, true);
    }
    
    const allowedOrigins = [
      process.env.CLIENT_URL,
      process.env.WEBADMIN_URL,
      'http://10.197.192.224:5173', // Mobile development server
      'http://10.150.160.224:5173', // Mobile development server
      'http://10.72.206.224:5173', // Mobile development server
      'http://103.75.183.125:5173', // Mobile development server  
      'http://192.168.2.123:5173', // WebAdmin development server
      'http://localhost:5173', // Local WebAdmin development
      'http://127.0.0.1:5173', // Local WebAdmin development
      // Add regex patterns for flexible IP matching
      /^https?:\/\/.*:5173$/, // Allow any IP on port 5173
      /^https?:\/\/.*:3000$/, // Allow any IP on port 3000
      /^https?:\/\/localhost:.*$/, // Allow localhost on any port
      /^https?:\/\/127\.0\.0\.1:.*$/, // Allow 127.0.0.1 on any port
      /^https?:\/\/.*\.local:.*$/ // Allow .local domains
    ].filter(Boolean); // Remove undefined values
    
    // Check if origin matches any allowed pattern
    const isAllowed = allowedOrigins.some(allowedOrigin => {
      if (typeof allowedOrigin === 'string') {
        return origin === allowedOrigin;
      } else if (allowedOrigin instanceof RegExp) {
        return allowedOrigin.test(origin);
      }
      return false;
    });
    
    if (isAllowed) {
      callback(null, true);
    } else {
      console.log('CORS blocked origin:', origin);
      console.log('Allowed origins:', allowedOrigins);
      callback(new Error('Not allowed by CORS'));
    }
  },
  methods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
  credentials: true,
  allowedHeaders: [
    "Content-Type", 
    "Authorization", 
    "Accept",
    "Origin",
    "X-Requested-With",
    "Cache-Control",
    "Pragma"
  ],
  exposedHeaders: ["Content-Length", "X-Foo", "X-Bar"],
  optionsSuccessStatus: 200 // Some legacy browsers (IE11, various SmartTVs) choke on 204
}));
// Use concise logging in non-production; disable in production
if (process.env.NODE_ENV !== 'production') {
  app.use(morgan('dev'));
}
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true }));

// Special CORS handling for static files before main CORS middleware
app.use('/uploads', (req, res, next) => {
  // Set comprehensive CORS headers for static files
  const origin = req.headers.origin;
  
  // In development, allow all origins for easier testing
  if (process.env.NODE_ENV === 'development') {
    res.header('Access-Control-Allow-Origin', origin || '*');
    res.header('Access-Control-Allow-Methods', 'GET, OPTIONS');
    res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization, Accept, Origin, X-Requested-With, Cache-Control, Pragma');
    res.header('Access-Control-Allow-Credentials', 'true');
    res.header('Cross-Origin-Resource-Policy', 'cross-origin');
    return next();
  }
  
  const allowedOrigins = [
    process.env.CLIENT_URL,
    process.env.WEBADMIN_URL,
    'http://10.197.192.224:5173', // Mobile development server
    'http://10.150.160.224:5173', // Mobile development server
    'http://10.72.206.224:5173', // Mobile development server
    'http://103.75.183.125:5173', // Mobile development server  
    'http://192.168.2.123:5173', // WebAdmin development server
    'http://localhost:5173', // Local WebAdmin development
    'http://127.0.0.1:5173', // Local WebAdmin development
    // Add regex patterns for flexible IP matching
    /^https?:\/\/.*:5173$/, // Allow any IP on port 5173
    /^https?:\/\/.*:3000$/, // Allow any IP on port 3000
    /^https?:\/\/localhost:.*$/, // Allow localhost on any port
    /^https?:\/\/127\.0\.0\.1:.*$/, // Allow 127.0.0.1 on any port
    /^https?:\/\/.*\.local:.*$/ // Allow .local domains
  ].filter(Boolean); // Remove undefined values
  
  const isAllowed = !origin || allowedOrigins.some(allowedOrigin => {
    if (typeof allowedOrigin === 'string') {
      return origin === allowedOrigin;
    } else if (allowedOrigin instanceof RegExp) {
      return allowedOrigin.test(origin);
    }
    return false;
  });
  
  if (isAllowed) {
    res.header('Access-Control-Allow-Origin', origin || '*');
  } else {
    res.header('Access-Control-Allow-Origin', '*');
  }
  
  res.header('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization, Accept, Origin, X-Requested-With, Cache-Control, Pragma');
  res.header('Access-Control-Allow-Credentials', 'true');
  res.header('Cross-Origin-Resource-Policy', 'cross-origin');
  res.header('Cross-Origin-Embedder-Policy', 'unsafe-none');
  res.header('Cache-Control', 'public, max-age=31536000');
  
  if (req.method === 'OPTIONS') {
    return res.sendStatus(200);
  }
  
  next();
});

// Serve static files (uploaded avatars)
app.use('/uploads', express.static('uploads'));

// Routes
app.use('/api/auth', authRoutes);
app.use('/api/users', userRoutes);
app.use('/api/chats', chatRoutes);
app.use('/api/messages', messageRoutes);
app.use('/api/friend-requests', friendRequestRoutes);
app.use('/api/groups', groupRoutes);
app.use('/api/upload', uploadRoutes);
app.use('/api/calls', callRoutes);
app.use('/api/server', serverRoutes);
app.use('/api/statistics', statisticsRoutes);
app.use('/api/security', securityRoutes);
app.use('/api/reports', reportRoutes);

// Health check endpoint
app.get('/api/health', (req, res) => {
  res.status(200).json({ 
    status: 'OK', 
    message: 'Chat App Backend Server is running',
    timestamp: new Date().toISOString()
  });
});

// Initialize socket handler
const socketHandler = new SocketHandler(io);

// Error handling middleware
app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(500).json({ 
    message: 'Something went wrong!',
    error: process.env.NODE_ENV === 'production' ? {} : err
  });
});

// 404 handler
app.use('*', (req, res) => {
  res.status(404).json({ message: 'Route not found' });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Server is running on port ${PORT}`);
  console.log(`Environment: ${process.env.NODE_ENV || 'development'}`);
  console.log(`Server accessible from: http://0.0.0.0:${PORT}`);
  console.log(`WebAdmin URL: ${process.env.WEBADMIN_URL}`);
  console.log(`Client URL: ${process.env.CLIENT_URL}`);
});

module.exports = { app, server, io, socketHandler };
