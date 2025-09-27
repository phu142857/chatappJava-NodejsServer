const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
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

// Import socket handler
const SocketHandler = require('./socket/socketHandler');


// Import database config
const connectDB = require('./config/database');

const app = express();
const server = createServer(app);
const io = new Server(server, {
  cors: {
    origin: process.env.CLIENT_URL || "http://localhost:3000",
    methods: ["GET", "POST"]
  }
});

const PORT = process.env.PORT || 5000;

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
app.use(cors());
// Use concise logging in non-production; disable in production
if (process.env.NODE_ENV !== 'production') {
  app.use(morgan('dev'));
}
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true }));

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

server.listen(PORT, () => {
  console.log(`Server is running on port ${PORT}`);
  console.log(`Environment: ${process.env.NODE_ENV || 'development'}`);
});

module.exports = { app, server, io, socketHandler };
