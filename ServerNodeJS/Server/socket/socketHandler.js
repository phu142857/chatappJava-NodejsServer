const jwt = require('jsonwebtoken');
const User = require('../models/User');
const Group = require('../models/Group');
const Chat = require('../models/Chat');
const Message = require('../models/Message');
const Call = require('../models/Call');
const GroupSocketHandler = require('./groupSocket');

class SocketHandler {
  constructor(io) {
    this.io = io;
    this.connectedUsers = new Map(); // Map to track connected users
    this.activeCalls = new Map(); // Map to track active calls
    this.groupHandler = new GroupSocketHandler(io);
    this.setupMiddleware();
    this.setupConnectionHandling();
  }

  // Setup socket middleware for authentication
  setupMiddleware() {
    this.io.use(async (socket, next) => {
      try {
        const token = socket.handshake.auth.token || socket.handshake.headers.authorization?.split(' ')[1];
        
        if (!token) {
          return next(new Error('Authentication error: No token provided'));
        }

        const decoded = jwt.verify(token, process.env.JWT_SECRET);
        const user = await User.findById(decoded.id).select('-password');
        
        if (!user || !user.isActive) {
          return next(new Error('Authentication error: User not found or inactive'));
        }

        socket.userId = user._id.toString();
        socket.user = user;
        next();

      } catch (error) {
        console.error('Socket authentication error:', error);
        next(new Error('Authentication error: Invalid token'));
      }
    });
  }

  // Setup connection handling
  setupConnectionHandling() {
    this.io.on('connection', (socket) => {
      console.log(`User connected: ${socket.user.username} (${socket.userId})`);
      
      // Join user-specific room for targeted messaging
      socket.join(`user_${socket.userId}`);
      
      // Store user connection
      this.connectedUsers.set(socket.userId, {
        socketId: socket.id,
        user: socket.user,
        connectedAt: new Date()
      });

      // Handle user-specific events
      this.handleUserEvents(socket);

      // Handle call-specific events
      this.handleCallEvents(socket);

      // Handle group-specific events
      this.groupHandler.handleConnection(socket);

      // Handle disconnect
      socket.on('disconnect', () => {
        this.handleDisconnect(socket);
      });

      // Send connection confirmation
      socket.emit('connected', {
        message: 'Connected successfully',
        user: socket.user,
        timestamp: new Date()
      });
    });
  }

  // Handle user-specific socket events
  handleUserEvents(socket) {
    // Join user room for private messages
    socket.on('join_user_room', () => {
      const userRoom = `user_${socket.userId}`;
      socket.join(userRoom);
      console.log(`User ${socket.userId} joined user room: ${userRoom}`);
    });

    // Leave user room
    socket.on('leave_user_room', () => {
      const userRoom = `user_${socket.userId}`;
      socket.leave(userRoom);
      console.log(`User ${socket.userId} left user room: ${userRoom}`);
    });

    // Send private message
    socket.on('send_private_message', async (data) => {
      try {
        const { receiverId, content, messageType = 'text' } = data;
        
        if (!receiverId || !content) {
          socket.emit('error', { message: 'Receiver ID and content are required' });
          return;
        }

        // Verify receiver exists
        const receiver = await User.findById(receiverId);
        if (!receiver || !receiver.isActive) {
          socket.emit('error', { message: 'Receiver not found' });
          return;
        }

        // Find or create private chat
        let chat = await Chat.findOne({
          type: 'private',
          'participants.user': { $all: [socket.userId, receiverId] },
          'participants.isActive': true,
          isActive: true
        });

        if (!chat) {
          // Create new private chat
          chat = new Chat({
            type: 'private',
            participants: [
              { user: socket.userId, role: 'member' },
              { user: receiverId, role: 'member' }
            ],
            createdBy: socket.userId
          });
          await chat.save();
        }

        // Create message
        const message = new Message({
          chat: chat._id,
          sender: socket.userId,
          content,
          messageType,
          readBy: [{ user: socket.userId, readAt: new Date() }]
        });

        await message.save();

        // Update chat last message and activity
        chat.lastMessage = message._id;
        chat.lastActivity = new Date();
        await chat.save();

        // Populate message with sender info
        await message.populate('sender', 'username email avatar');

        const messageObj = message.toJSON();
        const senderInfo = {
          id: message.sender._id,
          username: message.sender.username,
          avatar: message.sender.avatar || ''
        };

        // Send message to receiver if online
        const receiverRoom = `user_${receiverId}`;
        this.io.to(receiverRoom).emit('private_message', {
          message: {
            ...messageObj,
            // Ensure sender has avatar info
            sender: {
              ...messageObj.sender,
              avatar: message.sender.avatar || ''
            }
          },
          chatId: chat._id,
          chatType: 'private',
          senderInfo
        });

        // Send confirmation to sender
        socket.emit('message_sent', {
          message: {
            ...messageObj,
            // Ensure sender has avatar info
            sender: {
              ...messageObj.sender,
              avatar: message.sender.avatar || ''
            }
          },
          chatId: chat._id,
          chatType: 'private',
          senderInfo
        });

        console.log(`Private message sent from ${socket.userId} to ${receiverId}`);

      } catch (error) {
        console.error('Send private message error:', error);
        socket.emit('error', { message: 'Failed to send message' });
      }
    });

    // Handle typing indicators
    socket.on('typing', (data) => {
      try {
        const { receiverId, isTyping } = data;
        
        if (!receiverId) return;

        const receiverRoom = `user_${receiverId}`;
        socket.to(receiverRoom).emit('typing', {
          senderId: socket.userId,
          isTyping,
          timestamp: new Date()
        });

      } catch (error) {
        console.error('Typing error:', error);
      }
    });
  }

  // Handle user disconnect
  handleDisconnect(socket) {
    console.log(`User disconnected: ${socket.user.username} (${socket.userId})`);
    
    // Remove from connected users
    this.connectedUsers.delete(socket.userId);
    
    // Handle group disconnection
    this.groupHandler.handleUserDisconnect(socket.userId);
  }

  // Send message to specific user
  sendToUser(userId, event, data) {
    const userRoom = `user_${userId}`;
    this.io.to(userRoom).emit(event, data);
  }

  // Send message to specific group
  sendToGroup(groupId, event, data) {
    this.groupHandler.broadcastToGroup(groupId, event, data);
  }

  // Helper: emit to call participants excluding sender, and also to user rooms for those not in the call room yet
  emitToCallParticipantsExceptSender(callId, event, payload, senderSocket) {
    try {
      const roomName = `call_${callId}`;
      const roomSockets = this.io.sockets.adapter.rooms.get(roomName) || new Set();

      // Emit to everyone currently in the room except the sender
      senderSocket.to(roomName).emit(event, payload);

      // Additionally, emit directly to user rooms for participants not currently in the call room
      const callInfo = this.activeCalls.get(callId);
      if (!callInfo || !Array.isArray(callInfo.participants)) {
        return;
      }

      for (const participantUserId of callInfo.participants) {
        if (participantUserId === senderSocket.userId) continue;

        const connected = this.connectedUsers.get(participantUserId);
        if (!connected) continue;

        const participantSocketId = connected.socketId;
        // If participant socket is not yet in the call room, deliver via their user room to avoid loss
        if (!roomSockets.has(participantSocketId)) {
          this.sendToUser(participantUserId, event, payload);
        }
      }
    } catch (error) {
      console.error('emitToCallParticipantsExceptSender error:', error);
    }
  }

  // Handle call-specific events
  handleCallEvents(socket) {
    // Join call room
    socket.on('join_call_room', async (data) => {
      try {
        const { callId } = data;
        
        // Verify user is participant in call
        const call = await Call.findOne({ callId: callId });
        if (!call) {
          socket.emit('call_error', { message: 'Call not found' });
          return;
        }

        const participant = call.participants.find(p => p.userId.toString() === socket.userId);
        if (!participant) {
          socket.emit('call_error', { message: 'You are not a participant in this call' });
          return;
        }

        // Join socket room for call
        socket.join(`call_${callId}`);
        
        // Store call info
        this.activeCalls.set(callId, {
          callId: callId,
          participants: call.participants.map(p => p.userId.toString()),
          roomId: call.webrtcData.roomId
        });

        // Notify other participants
        socket.to(`call_${callId}`).emit('user_joined_call', {
          callId: callId,
          userId: socket.userId,
          username: socket.user.username,
          avatar: socket.user.avatar
        });

        // Send call info to user
        socket.emit('call_room_joined', {
          callId: callId,
          roomId: call.webrtcData.roomId,
          participants: call.participants,
          iceServers: call.webrtcData.iceServers
        });

      } catch (error) {
        console.error('Join call room error:', error);
        socket.emit('call_error', { message: 'Failed to join call room' });
      }
    });

    // Leave call room
    socket.on('leave_call_room', async (data) => {
      try {
        const { callId } = data;
        
        // Leave socket room
        socket.leave(`call_${callId}`);
        
        // Notify other participants
        socket.to(`call_${callId}`).emit('user_left_call', {
          callId: callId,
          userId: socket.userId,
          username: socket.user.username
        });

        // Remove from active calls if no participants
        const callInfo = this.activeCalls.get(callId);
        if (callInfo) {
          const remainingParticipants = callInfo.participants.filter(id => id !== socket.userId);
          if (remainingParticipants.length === 0) {
            this.activeCalls.delete(callId);
          }
        }

      } catch (error) {
        console.error('Leave call room error:', error);
        socket.emit('call_error', { message: 'Failed to leave call room' });
      }
    });

    // WebRTC signaling
    socket.on('webrtc_offer', (data) => {
      const { callId, offer } = data;

      const payload = {
        callId: callId,
        offer: offer,
        fromUserId: socket.userId,
        fromUsername: socket.user.username
      };

      this.emitToCallParticipantsExceptSender(callId, 'webrtc_offer', payload, socket);

      console.log(`Forwarded WebRTC offer for call ${callId} from user ${socket.userId}`);
    });

    socket.on('webrtc_answer', (data) => {
      const { callId, answer } = data;

      const payload = {
        callId: callId,
        answer: answer,
        fromUserId: socket.userId,
        fromUsername: socket.user.username
      };

      this.emitToCallParticipantsExceptSender(callId, 'webrtc_answer', payload, socket);

      console.log(`Forwarded WebRTC answer for call ${callId} from user ${socket.userId}`);
    });

    socket.on('webrtc_ice_candidate', (data) => {
      const { callId, candidate } = data;

      const payload = {
        callId: callId,
        candidate: candidate,
        fromUserId: socket.userId
      };

      this.emitToCallParticipantsExceptSender(callId, 'webrtc_ice_candidate', payload, socket);

      console.log(`Forwarded ICE candidate for call ${callId} from user ${socket.userId}`);
    });

    // Call status updates
    socket.on('call_status_update', async (data) => {
      try {
        const { callId, status, details } = data;
        
        // Update call in database
        const call = await Call.findOne({ callId: callId });
        if (call) {
          await call.addLog(socket.userId, status, details || '');
          
          // Broadcast status update to all participants
          this.io.to(`call_${callId}`).emit('call_status_updated', {
            callId: callId,
            status: status,
            userId: socket.userId,
            username: socket.user.username,
            details: details,
            timestamp: new Date()
          });
        }
      } catch (error) {
        console.error('Call status update error:', error);
        socket.emit('call_error', { message: 'Failed to update call status' });
      }
    });

    // Call settings updates
    socket.on('call_settings_update', async (data) => {
      try {
        const { callId, settings } = data;
        
        // Update call settings in database
        const call = await Call.findOne({ callId: callId });
        if (call) {
          Object.assign(call.settings, settings);
          await call.save();
          
          // Broadcast settings update to all participants
          this.io.to(`call_${callId}`).emit('call_settings_updated', {
            callId: callId,
            settings: call.settings,
            userId: socket.userId,
            username: socket.user.username,
            timestamp: new Date()
          });
        }
      } catch (error) {
        console.error('Call settings update error:', error);
        socket.emit('call_error', { message: 'Failed to update call settings' });
      }
    });

    // Handle member removed from group
    socket.on('member_removed', (data) => {
      console.log(`User ${socket.userId} was removed from group:`, data);
      // Client should handle this event to refresh chat list
    });

    // Handle member removed from group (for other members)
    socket.on('member_removed_from_group', (data) => {
      console.log(`User ${socket.userId} received member removal notification:`, data);
      // Client should handle this event to refresh group members list
    });
  }

  // Broadcast to all connected users
  broadcast(event, data) {
    this.io.emit(event, data);
  }
}

module.exports = SocketHandler;
