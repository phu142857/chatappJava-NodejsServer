const jwt = require('jsonwebtoken');
const User = require('../models/User');
const Group = require('../models/Group');
const Chat = require('../models/Chat');
const Message = require('../models/Message');
const Call = require('../models/Call');

// Helper function to extract chatId string from call object (handles both ObjectId and populated object)
const extractChatId = (call) => {
    if (!call || !call.chatId) {
        return null;
    }
    if (typeof call.chatId === 'string') {
        return call.chatId;
    }
    if (typeof call.chatId === 'object') {
        // Populated object - extract _id or id
        if (call.chatId._id) {
            return call.chatId._id.toString();
        }
        if (call.chatId.id) {
            return call.chatId.id.toString();
        }
        // Fallback to toString()
        return call.chatId.toString();
    }
    // ObjectId
    return call.chatId.toString();
};
const GroupSocketHandler = require('./groupSocket');
const sfuService = require('../services/sfuService');

class SocketHandler {
  constructor(io) {
    this.io = io;
    this.connectedUsers = new Map(); // Map to track connected users: { userId: { socketId, user, connectedAt } }
    this.socketToUserMap = new Map(); // Map to track socketId -> userId: { socketId: userId }
    this.activeCalls = new Map(); // Map to track active calls
    this.groupHandler = new GroupSocketHandler(io);
    this.setupMiddleware();
    this.setupConnectionHandling();
    
    // Initialize SFU service
    sfuService.initialize().catch(err => {
      console.error('[SFU] Failed to initialize SFU service:', err);
    });
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
      
      // Store socketId -> userId mapping for quick lookup
      this.socketToUserMap.set(socket.id, socket.userId);

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
    
    // Remove socketId -> userId mapping
    this.socketToUserMap.delete(socket.id);
    
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

      // Get call info to know all participants
      const callInfo = this.activeCalls.get(callId);
      if (!callInfo || !Array.isArray(callInfo.participants)) {
        console.warn(`emitToCallParticipantsExceptSender: No callInfo for ${callId}`);
        // Fallback: emit to room anyway
        senderSocket.to(roomName).emit(event, payload);
        return;
      }

      console.log(`emitToCallParticipantsExceptSender: Forwarding ${event} for call ${callId} to ${callInfo.participants.length} participants (excluding ${senderSocket.userId})`);

      // Emit to everyone currently in the room except the sender
      senderSocket.to(roomName).emit(event, payload);

      // CRITICAL: Also emit directly to user rooms for ALL participants to ensure delivery
      // This handles the case where a participant joins after frames start being sent
      for (const participantUserId of callInfo.participants) {
        if (participantUserId === senderSocket.userId) continue;

        const connected = this.connectedUsers.get(participantUserId);
        if (!connected) {
          console.log(`emitToCallParticipantsExceptSender: Participant ${participantUserId} not connected`);
          continue;
        }

        const participantSocketId = connected.socketId;
        // CRITICAL: Always send to user room as well to ensure delivery
        // This ensures frames are delivered even if participant hasn't joined the call room yet
        this.sendToUser(participantUserId, event, payload);
        
        // Also check if they're in the room - if not, log it
        if (!roomSockets.has(participantSocketId)) {
          console.log(`emitToCallParticipantsExceptSender: Participant ${participantUserId} not in room ${roomName}, delivered via user room`);
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
        
        // CRITICAL: Update activeCalls to include all current participants from database
        // This ensures video frames are forwarded to all participants, even if they join later
        const allParticipantIds = call.participants.map(p => {
          if (p.userId && typeof p.userId === 'object' && p.userId._id) {
            return p.userId._id.toString();
          }
          return p.userId.toString();
        });
        
        // Store call info - always use latest participants from database
        this.activeCalls.set(callId, {
          callId: callId,
          participants: allParticipantIds,
          roomId: call.webrtcData ? call.webrtcData.roomId : `room_${callId}`
        });
        
        console.log(`join_call_room: Updated activeCalls for ${callId} with ${allParticipantIds.length} participants:`, allParticipantIds);

        // Notify other participants
        socket.to(`call_${callId}`).emit('user_joined_call', {
          callId: callId,
          userId: socket.userId,
          username: socket.user.username,
          avatar: socket.user.avatar
        });

        // CRITICAL: Chỉ gửi những participants đã thực sự join call room (có trong socket room)
        // Lấy danh sách sockets đang trong room
        const roomName = `call_${callId}`;
        const roomSockets = this.io.sockets.adapter.rooms.get(roomName) || new Set();
        
        // Lấy danh sách userIds đã join room
        const joinedUserIds = new Set();
        for (const socketId of roomSockets) {
          const userId = this.socketToUserMap.get(socketId);
          if (userId) {
            joinedUserIds.add(userId);
          }
        }
        
        // Filter participants để chỉ lấy những người đã join room
        const activeParticipants = call.participants.filter(p => {
          let participantUserId;
          if (p.userId && typeof p.userId === 'object' && p.userId._id) {
            participantUserId = p.userId._id.toString();
          } else {
            participantUserId = p.userId.toString();
          }
          return joinedUserIds.has(participantUserId);
        });
        
        console.log(`join_call_room: Sending ${activeParticipants.length} active participants (out of ${call.participants.length} total) to user ${socket.userId}`);

        // Send call info to user với chỉ những participants đã join
        socket.emit('call_room_joined', {
          callId: callId,
          roomId: call.webrtcData.roomId,
          participants: activeParticipants,
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

    // Gérer les frames vidéo personnalisées (sans WebRTC)
    socket.on('video_frame', async (data) => {
      try {
        const { callId, frame, timestamp } = data;
        
        if (!callId || !frame) {
          console.error('video_frame: callId ou frame manquant');
          return;
        }

        // Vérifier que l'utilisateur est dans la salle d'appel
        const callInfo = this.activeCalls.get(callId);
        if (!callInfo) {
          console.error(`video_frame: Appel ${callId} non trouvé dans activeCalls`);
          // Try to find call in database and add to activeCalls
          Call.findOne({ callId: callId }).then(call => {
            if (call) {
              this.activeCalls.set(callId, {
                callId: callId,
                participants: call.participants.map(p => p.userId.toString()),
                roomId: call.webrtcData ? call.webrtcData.roomId : `room_${callId}`
              });
              console.log(`video_frame: Call ${callId} ajouté à activeCalls`);
              
              // Retry sending the frame
              const payload = {
                callId: callId,
                userId: socket.userId,
                frame: frame,
                timestamp: timestamp || Date.now()
              };
              this.emitToCallParticipantsExceptSender(callId, 'video_frame', payload, socket);
            }
          }).catch(err => {
            console.error(`video_frame: Erreur lors de la recherche de l'appel ${callId}:`, err);
          });
          return;
        }

        // Créer le payload avec les informations de l'expéditeur
        const payload = {
          callId: callId,
          userId: socket.userId,
          frame: frame,
          timestamp: timestamp || Date.now()
        };

        // CRITICAL: Refresh participants from database synchronously before forwarding
        // This ensures new participants receive frames immediately
        try {
          const call = await Call.findOne({ callId: callId });
          if (call) {
            const latestParticipantIds = call.participants.map(p => {
              if (p.userId && typeof p.userId === 'object' && p.userId._id) {
                return p.userId._id.toString();
              }
              return p.userId.toString();
            });
            
            // Update activeCalls with latest participants immediately
            this.activeCalls.set(callId, {
              callId: callId,
              participants: latestParticipantIds,
              roomId: call.webrtcData ? call.webrtcData.roomId : `room_${callId}`
            });
            
            console.log(`video_frame: Forwarding frame from user ${socket.userId} for call ${callId} to ${latestParticipantIds.length} participants:`, latestParticipantIds);
            
            // Forward frame with updated participant list
            this.emitToCallParticipantsExceptSender(callId, 'video_frame', payload, socket);
          } else {
            // Fallback: use existing callInfo
            console.log(`video_frame: Call not found in DB, using existing callInfo`);
            this.emitToCallParticipantsExceptSender(callId, 'video_frame', payload, socket);
          }
        } catch (err) {
          console.error(`video_frame: Error refreshing participants:`, err);
          // Fallback: use existing callInfo
          this.emitToCallParticipantsExceptSender(callId, 'video_frame', payload, socket);
        }

      } catch (error) {
        console.error('Erreur lors du traitement de video_frame:', error);
        socket.emit('call_error', { message: 'Erreur lors de l\'envoi de la frame vidéo' });
      }
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

    // Group call specific events - simplified join/leave handlers
    socket.on('join_group_call', async (data) => {
      try {
        let { chatId, callId, sessionId } = data;
        
        if (!chatId) {
          socket.emit('group_call_error', { message: 'chatId is required' });
          return;
        }
        
        // Normalize chatId - handle both string and object formats
        if (typeof chatId === 'object') {
          chatId = chatId._id || chatId.id || JSON.stringify(chatId);
        } else if (typeof chatId === 'string' && chatId.startsWith('{')) {
          try {
            const chatObj = JSON.parse(chatId);
            chatId = chatObj._id || chatObj.id || chatId;
          } catch (e) {
            console.warn(`[Socket] Could not parse chatId: ${chatId}`);
          }
        }
        
        const room = `group_call_${chatId}`;
        socket.join(room);
        console.log(`[Socket] User ${socket.userId} joined room ${room} (callId: ${callId}, sessionId: ${sessionId || 'none'})`);
        
        socket.emit('group_call_joined', { chatId, callId, sessionId });
      } catch (error) {
        console.error('Join group call error:', error);
        socket.emit('group_call_error', { message: 'Failed to join group call' });
      }
    });

    socket.on('leave_group_call', async (data) => {
      try {
        const { chatId } = data;
        
        if (!chatId) {
          return;
        }
        
        const room = `group_call_${chatId}`;
        socket.leave(room);
        console.log(`[Socket] User ${socket.userId} left room ${room}`);
      } catch (error) {
        console.error('Leave group call error:', error);
      }
    });

    socket.on('join_group_call_room', async (data) => {
      try {
        const { callId } = data;
        
        // Verify user is participant in group call
        const call = await Call.findOne({ callId: callId, isGroupCall: true });
        if (!call) {
          socket.emit('group_call_error', { message: 'Group call not found' });
          return;
        }

        const participant = call.participants.find(p => p.userId.toString() === socket.userId);
        if (!participant) {
          socket.emit('group_call_error', { message: 'You are not a participant in this call' });
          return;
        }

        // CRITICAL FIX: Use chatId for socket room instead of callId
        // This ensures all users in the same chat join the same socket room
        // even if they have different callIds (due to race conditions)
        const chatId = extractChatId(call);
        if (!chatId) {
          console.error(`[Socket] Cannot extract chatId from call ${callId}`);
          socket.emit('group_call_error', { message: 'Invalid call data' });
          return;
        }
        const socketRoom = `group_call_${chatId}`;
        
        // Join socket room based on chatId (not callId)
        socket.join(socketRoom);
        
        console.log(`User ${socket.userId} joined group call socket room: ${socketRoom} (callId: ${callId}, chatId: ${chatId})`);

        // Notify other participants in the same chat room
        socket.to(socketRoom).emit('group_call_user_joined_room', {
          callId: callId,
          chatId: chatId,
          userId: socket.userId,
          username: socket.user.username,
          avatar: socket.user.avatar,
          timestamp: new Date()
        });

        // Send current participants list to joining user
        const activeParticipants = call.participants.filter(p => p.status === 'connected');
        
        // Get SFU router capabilities if using SFU
        let rtpCapabilities = null;
        if (call.webrtcData.mediaTopology === 'sfu') {
          try {
            const roomId = call.webrtcData.roomId || `room_${chatId}`;
            rtpCapabilities = sfuService.getRouterCapabilities(roomId);
          } catch (error) {
            console.error(`[Socket] Failed to get SFU router capabilities: ${error.message}`);
          }
        }
        
        socket.emit('group_call_room_state', {
          callId: callId,
          chatId: chatId,
          participants: activeParticipants,
          participantMedia: call.participantMedia,
          roomId: call.webrtcData.roomId,
          iceServers: call.webrtcData.iceServers,
          topology: call.webrtcData.mediaTopology,
          sfu: rtpCapabilities ? { rtpCapabilities } : null
        });

      } catch (error) {
        console.error('Join group call room error:', error);
        socket.emit('group_call_error', { message: 'Failed to join group call room' });
      }
    });

    // Mesh WebRTC signaling removed - SFU only
    // All offer/answer/ice candidate handlers removed
    // SFU handles all signaling via sfu-* events
    
    // Placeholder to prevent errors - will be removed
    socket.on('group_call_webrtc_offer', async (data) => {
      try {
      const { callId, offer, toUserId, sessionId, chatId: providedChatId } = data;

        // Get call to find chatId for socket room and validate sessionId
        const call = await Call.findOne({ callId: callId, isGroupCall: true });
        if (!call) {
          console.error(`[signal] Call not found for offer: ${callId}`);
          return;
        }

        // Extract chatId properly using helper function
        const chatId = providedChatId || extractChatId(call);
        if (!chatId) {
          console.error(`[signal] Cannot extract chatId from call ${callId}`);
          return;
        }
        const socketRoom = `group_call_${chatId}`;

        // CRITICAL: Validate sessionId if provided
        if (sessionId) {
          const fromParticipant = call.participants.find(p => p.userId.toString() === socket.userId);
          if (fromParticipant) {
            if (fromParticipant.sessionId && fromParticipant.sessionId !== sessionId) {
              console.log(`[signal] DROPPED offer - sessionId mismatch: from=${socket.userId}, expected=${fromParticipant.sessionId}, received=${sessionId}, callId=${callId}`);
              return; // Drop stale offer
            } else if (!fromParticipant.sessionId) {
              // Participant doesn't have sessionId yet - update it (first message from this session)
              fromParticipant.sessionId = sessionId;
              call.save().catch(err => console.error(`[signal] Error saving sessionId: ${err.message}`));
              console.log(`[signal] Updated sessionId for ${socket.userId} to ${sessionId}`);
            }
          } else {
            console.warn(`[signal] Participant ${socket.userId} not found in call ${callId} - allowing offer (may be race condition)`);
          }
        } else {
          console.log(`[signal] No sessionId provided for offer from ${socket.userId} - allowing (backward compatibility)`);
        }

      const payload = {
        callId: callId,
          chatId: chatId,
        offer: offer,
        fromUserId: socket.userId,
        fromUsername: socket.user.username,
        sessionId: sessionId
      };

      if (toUserId) {
        // Send to specific participant (for mesh)
        // Find socketId for target user
        let targetSocketId = null;
        for (const [sid, uid] of this.socketToUserMap.entries()) {
          if (uid === toUserId) {
            targetSocketId = sid;
            break;
          }
        }
        
        if (targetSocketId) {
          this.io.to(targetSocketId).emit('group_call_webrtc_offer', payload);
          console.log(`[signal] forward offer call=${callId} from=${socket.userId} to=${toUserId} (direct socket) session=${sessionId || 'none'} timestamp=${Date.now()}`);
        } else {
          // CRITICAL FIX: Try multiple delivery methods
          // 1. Try user room
          this.io.to(`user_${toUserId}`).emit('group_call_webrtc_offer', payload);
          // 2. Also try socket room (group_call_${chatId}) as fallback
          // This ensures users in the same group call room receive the offer
          socket.to(socketRoom).emit('group_call_webrtc_offer', payload);
          console.log(`[signal] forward offer call=${callId} from=${socket.userId} to=${toUserId} (user room + socket room: ${socketRoom}) session=${sessionId || 'none'} timestamp=${Date.now()}`);
        }
      } else {
          // Broadcast to all in chat room except sender
          socket.to(socketRoom).emit('group_call_webrtc_offer', payload);
          console.log(`[signal] forward offer call=${callId} from=${socket.userId} to=room:${chatId} session=${sessionId || 'none'} timestamp=${Date.now()}`);
        }
      } catch (error) {
        console.error('Error handling group call offer:', error);
      }
    });

    socket.on('group_call_webrtc_answer', async (data) => {
      try {
      const { callId, answer, toUserId, sessionId, chatId: providedChatId } = data;

        // Get call to find chatId for socket room and validate sessionId
        const call = await Call.findOne({ callId: callId, isGroupCall: true });
        if (!call) {
          console.error(`[signal] Call not found for answer: ${callId}`);
          return;
        }

        // Extract chatId properly using helper function
        const chatId = providedChatId || extractChatId(call);
        if (!chatId) {
          console.error(`[signal] Cannot extract chatId from call ${callId}`);
          return;
        }
        const socketRoom = `group_call_${chatId}`;

        // CRITICAL: Validate sessionId if provided
        if (sessionId) {
          const fromParticipant = call.participants.find(p => p.userId.toString() === socket.userId);
          if (fromParticipant) {
            if (fromParticipant.sessionId && fromParticipant.sessionId !== sessionId) {
              console.log(`[signal] DROPPED answer - sessionId mismatch: from=${socket.userId}, expected=${fromParticipant.sessionId}, received=${sessionId}, callId=${callId}`);
              return; // Drop stale answer
            } else if (!fromParticipant.sessionId) {
              // Participant doesn't have sessionId yet - update it (first message from this session)
              fromParticipant.sessionId = sessionId;
              call.save().catch(err => console.error(`[signal] Error saving sessionId: ${err.message}`));
              console.log(`[signal] Updated sessionId for ${socket.userId} to ${sessionId}`);
            }
          } else {
            console.warn(`[signal] Participant ${socket.userId} not found in call ${callId} - allowing answer (may be race condition)`);
          }
        } else {
          console.log(`[signal] No sessionId provided for answer from ${socket.userId} - allowing (backward compatibility)`);
        }

      const payload = {
        callId: callId,
          chatId: chatId,
        answer: answer,
        fromUserId: socket.userId,
        fromUsername: socket.user.username,
        sessionId: sessionId
      };

      if (toUserId) {
        // Send to specific participant (for mesh)
        // Find socketId for target user
        let targetSocketId = null;
        for (const [sid, uid] of this.socketToUserMap.entries()) {
          if (uid === toUserId) {
            targetSocketId = sid;
            break;
          }
        }
        
        if (targetSocketId) {
          this.io.to(targetSocketId).emit('group_call_webrtc_answer', payload);
        } else {
          this.io.to(`user_${toUserId}`).emit('group_call_webrtc_answer', payload);
        }
          console.log(`[signal] forward answer call=${callId} from=${socket.userId} to=${toUserId} session=${sessionId || 'none'} timestamp=${Date.now()}`);
      } else {
          // Broadcast to all in chat room except sender
          socket.to(socketRoom).emit('group_call_webrtc_answer', payload);
          console.log(`[signal] forward answer call=${callId} from=${socket.userId} to=room:${chatId} session=${sessionId || 'none'} timestamp=${Date.now()}`);
        }
      } catch (error) {
        console.error('Error handling group call answer:', error);
      }
    });

    socket.on('group_call_ice_candidate', async (data) => {
      try {
      const { callId, candidate, toUserId, sessionId, chatId: providedChatId } = data;

        // Get call to find chatId for socket room and validate sessionId
        const call = await Call.findOne({ callId: callId, isGroupCall: true });
        if (!call) {
          console.error(`[signal] Call not found for ICE candidate: ${callId}`);
          return;
        }

        // Extract chatId properly using helper function
        const chatId = providedChatId || extractChatId(call);
        if (!chatId) {
          console.error(`[signal] Cannot extract chatId from call ${callId}`);
          return;
        }
        const socketRoom = `group_call_${chatId}`;

        // CRITICAL: Validate sessionId if provided
        if (sessionId) {
          const fromParticipant = call.participants.find(p => p.userId.toString() === socket.userId);
          if (fromParticipant) {
            if (fromParticipant.sessionId && fromParticipant.sessionId !== sessionId) {
              console.log(`[signal] DROPPED ice_candidate - sessionId mismatch: from=${socket.userId}, expected=${fromParticipant.sessionId}, received=${sessionId}, callId=${callId}`);
              return; // Drop stale candidate
            } else if (!fromParticipant.sessionId) {
              // Participant doesn't have sessionId yet - update it (first message from this session)
              fromParticipant.sessionId = sessionId;
              call.save().catch(err => console.error(`[signal] Error saving sessionId: ${err.message}`));
              console.log(`[signal] Updated sessionId for ${socket.userId} to ${sessionId}`);
            }
          } else {
            console.warn(`[signal] Participant ${socket.userId} not found in call ${callId} - allowing ice_candidate (may be race condition)`);
          }
        } else {
          console.log(`[signal] No sessionId provided for ice_candidate from ${socket.userId} - allowing (backward compatibility)`);
        }

      const payload = {
        callId: callId,
          chatId: chatId,
        candidate: candidate,
        fromUserId: socket.userId,
        sessionId: sessionId
      };

      if (toUserId) {
        // Send to specific participant (for mesh)
        // Find socketId for target user
        let targetSocketId = null;
        for (const [sid, uid] of this.socketToUserMap.entries()) {
          if (uid === toUserId) {
            targetSocketId = sid;
            break;
          }
        }
        
        if (targetSocketId) {
          this.io.to(targetSocketId).emit('group_call_ice_candidate', payload);
          console.log(`[signal] forward ice_candidate call=${callId} from=${socket.userId} to=${toUserId} (direct socket) session=${sessionId || 'none'} timestamp=${Date.now()}`);
        } else {
          // CRITICAL FIX: Try multiple delivery methods
          // 1. Try user room
          this.io.to(`user_${toUserId}`).emit('group_call_ice_candidate', payload);
          // 2. Also try socket room (group_call_${chatId}) as fallback
          // This ensures users in the same group call room receive the ICE candidate
          socket.to(socketRoom).emit('group_call_ice_candidate', payload);
          console.log(`[signal] forward ice_candidate call=${callId} from=${socket.userId} to=${toUserId} (user room + socket room: ${socketRoom}) session=${sessionId || 'none'} timestamp=${Date.now()}`);
        }
      } else {
          // Broadcast to all in chat room except sender
          socket.to(socketRoom).emit('group_call_ice_candidate', payload);
          console.log(`[signal] forward ice_candidate call=${callId} from=${socket.userId} to=room:${chatId} session=${sessionId || 'none'} timestamp=${Date.now()}`);
        }
      } catch (error) {
        console.error('Error handling group call ICE candidate:', error);
      }
    });
    
    // All mesh handlers above are deprecated - SFU only now

    // Dismiss group call passive notification
    socket.on('dismiss_group_call_alert', async (data) => {
      try {
        const { callId } = data;
        
        // Just acknowledge dismissal, no database change needed
        socket.emit('group_call_alert_dismissed', {
          callId: callId,
          timestamp: new Date()
        });

        console.log(`User ${socket.userId} dismissed group call alert for ${callId}`);
      } catch (error) {
        console.error('Dismiss group call alert error:', error);
      }
    });

    // ========== SFU (Selective Forwarding Unit) Handlers ==========
    
    // Create SFU room
    socket.on('sfu-create-room', async (data) => {
      try {
        const { roomId } = data;
        if (!roomId) {
          socket.emit('sfu-error', { message: 'roomId is required' });
          return;
        }
        
        const room = await sfuService.createRoom(roomId);
        const routerCapabilities = sfuService.getRouterCapabilities(roomId);
        
        socket.emit('sfu-room-created', { 
          roomId, 
          rtpCapabilities: routerCapabilities 
        });
        console.log(`[SFU] Room ${roomId} created for user ${socket.userId}`);
      } catch (error) {
        console.error('[SFU] Create room error:', error);
        socket.emit('sfu-error', { message: error.message });
      }
    });

    // Get router capabilities
    socket.on('sfu-get-router-capabilities', async (data) => {
      try {
        const { roomId } = data;
        if (!roomId) {
          socket.emit('sfu-error', { message: 'roomId is required' });
          return;
        }
        
        const rtpCapabilities = sfuService.getRouterCapabilities(roomId);
        socket.emit('sfu-router-capabilities', { roomId, rtpCapabilities });
      } catch (error) {
        console.error('[SFU] Get router capabilities error:', error);
        socket.emit('sfu-error', { message: error.message });
      }
    });

    // Create transport (send or receive)
    socket.on('sfu-create-transport', async (data) => {
      try {
        const { roomId, peerId, direction } = data;
        if (!roomId || !peerId || !direction) {
          socket.emit('sfu-error', { message: 'roomId, peerId, and direction are required' });
          return;
        }
        
        if (direction !== 'send' && direction !== 'receive') {
          socket.emit('sfu-error', { message: 'direction must be "send" or "receive"' });
          return;
        }
        
        const transport = await sfuService.createTransport(roomId, peerId, direction, this.io);
        socket.emit('sfu-transport-created', { transport, direction });
        console.log(`[SFU] Created ${direction} transport ${transport.id} for peer ${peerId} in room ${roomId}`);
        
        // CRITICAL: When receive transport is created, send existing producers to this peer
        if (direction === 'receive') {
          try {
            const existingProducers = sfuService.getExistingProducers(roomId, peerId);
            console.log(`[SFU] Sending ${existingProducers.length} existing producers to peer ${peerId}`);
            for (const producer of existingProducers) {
              // Extract chatId from roomId for socket room
              let chatId = roomId.replace('room_', '');
              if (chatId.startsWith('{')) {
                try {
                  const chatObj = JSON.parse(chatId);
                  chatId = chatObj._id || chatObj.id || chatId;
                } catch (e) {
                  console.warn(`[SFU] Could not parse chatId from roomId: ${roomId}`);
                }
              }
              
              // Emit to this specific socket (the new peer)
              socket.emit('sfu-new-producer', {
                roomId,
                producerPeerId: producer.producerPeerId,
                producerId: producer.producerId,
                kind: producer.kind
              });
              console.log(`[SFU] Sent existing producer ${producer.producerId} (${producer.kind}) from peer ${producer.producerPeerId} to new peer ${peerId}`);
            }
          } catch (error) {
            console.error('[SFU] Error sending existing producers:', error);
          }
        }
      } catch (error) {
        console.error('[SFU] Create transport error:', error);
        socket.emit('sfu-error', { message: error.message });
      }
    });

    // Connect transport
    socket.on('sfu-connect-transport', async (data) => {
      try {
        const { roomId, peerId, transportId, dtlsParameters } = data;
        if (!roomId || !peerId || !transportId || !dtlsParameters) {
          socket.emit('sfu-error', { message: 'roomId, peerId, transportId, and dtlsParameters are required' });
          return;
        }
        
        await sfuService.connectTransport(roomId, peerId, transportId, dtlsParameters);
        socket.emit('sfu-transport-connected', { transportId });
        console.log(`[SFU] Transport ${transportId} connected for peer ${peerId}`);
      } catch (error) {
        console.error('[SFU] Connect transport error:', error);
        socket.emit('sfu-error', { message: error.message });
      }
    });

    // Create producer (send media)
    socket.on('sfu-produce', async (data) => {
      try {
        const { roomId, peerId, transportId, rtpParameters, kind } = data;
        if (!roomId || !peerId || !transportId || !rtpParameters || !kind) {
          socket.emit('sfu-error', { message: 'roomId, peerId, transportId, rtpParameters, and kind are required' });
          return;
        }
        
        const producer = await sfuService.createProducer(roomId, peerId, transportId, rtpParameters, kind);
        
        // Notify other peers about new producer
        // Extract chatId from roomId - handle both "room_xxx" and "room_{...}" formats
        let chatId = roomId.replace('room_', '');
        // If chatId is a JSON string, parse it to get the actual ID
        if (chatId.startsWith('{')) {
          try {
            const chatObj = JSON.parse(chatId);
            chatId = chatObj._id || chatObj.id || chatId;
          } catch (e) {
            console.warn(`[SFU] Could not parse chatId from roomId: ${roomId}`);
          }
        }
        const socketRoom = `group_call_${chatId}`;
        console.log(`[SFU] Emitting sfu-new-producer to room: ${socketRoom}, producerId: ${producer.id}, kind: ${producer.kind}`);
        socket.to(socketRoom).emit('sfu-new-producer', {
          roomId,
          producerPeerId: peerId,
          producerId: producer.id,
          kind: producer.kind
        });
        
        socket.emit('sfu-producer-created', { producer });
        console.log(`[SFU] Created producer ${producer.id} (${kind}) for peer ${peerId}`);
      } catch (error) {
        console.error('[SFU] Create producer error:', error);
        socket.emit('sfu-error', { message: error.message });
      }
    });

    // Create consumer (receive media)
    socket.on('sfu-consume', async (data) => {
      try {
        const { roomId, peerId, transportId, producerId, rtpCapabilities } = data;
        if (!roomId || !peerId || !transportId || !producerId || !rtpCapabilities) {
          socket.emit('sfu-error', { message: 'roomId, peerId, transportId, producerId, and rtpCapabilities are required' });
          return;
        }
        
        const consumer = await sfuService.createConsumer(roomId, peerId, transportId, producerId, rtpCapabilities);
        socket.emit('sfu-consumer-created', { consumer });
        console.log(`[SFU] Created consumer ${consumer.id} for peer ${peerId} consuming producer ${producerId}`);
      } catch (error) {
        console.error('[SFU] Create consumer error:', error);
        socket.emit('sfu-error', { message: error.message });
      }
    });

    // Close producer
    socket.on('sfu-close-producer', async (data) => {
      try {
        const { roomId, peerId, producerId } = data;
        if (!roomId || !peerId || !producerId) {
          socket.emit('sfu-error', { message: 'roomId, peerId, and producerId are required' });
          return;
        }
        
        await sfuService.closeProducer(roomId, peerId, producerId);
        socket.emit('sfu-producer-closed', { producerId });
      } catch (error) {
        console.error('[SFU] Close producer error:', error);
        socket.emit('sfu-error', { message: error.message });
      }
    });

    // Resume consumer (start receiving media)
    socket.on('sfu-resume-consumer', async (data) => {
      try {
        const { roomId, peerId, consumerId } = data;
        if (!roomId || !peerId || !consumerId) {
          socket.emit('sfu-error', { message: 'roomId, peerId, and consumerId are required' });
          return;
        }
        
        await sfuService.resumeConsumer(roomId, peerId, consumerId);
        socket.emit('sfu-consumer-resumed', { consumerId });
        console.log(`[SFU] Resumed consumer ${consumerId} for peer ${peerId}`);
      } catch (error) {
        console.error('[SFU] Resume consumer error:', error);
        socket.emit('sfu-error', { message: error.message });
      }
    });

    // Close consumer
    socket.on('sfu-close-consumer', async (data) => {
      try {
        const { roomId, peerId, consumerId } = data;
        if (!roomId || !peerId || !consumerId) {
          socket.emit('sfu-error', { message: 'roomId, peerId, and consumerId are required' });
          return;
        }
        
        await sfuService.closeConsumer(roomId, peerId, consumerId);
        socket.emit('sfu-consumer-closed', { consumerId });
      } catch (error) {
        console.error('[SFU] Close consumer error:', error);
        socket.emit('sfu-error', { message: error.message });
      }
    });

    // Remove peer from room
    socket.on('sfu-remove-peer', async (data) => {
      try {
        const { roomId, peerId } = data;
        if (!roomId || !peerId) {
          socket.emit('sfu-error', { message: 'roomId and peerId are required' });
          return;
        }
        
        await sfuService.removePeer(roomId, peerId);
        socket.emit('sfu-peer-removed', { peerId });
      } catch (error) {
        console.error('[SFU] Remove peer error:', error);
        socket.emit('sfu-error', { message: error.message });
      }
    });

    // Handle disconnect - cleanup SFU resources
    socket.on('disconnect', async () => {
      // Cleanup will be handled in handleDisconnect
    });
  }

  // Broadcast to all connected users
  broadcast(event, data) {
    this.io.emit(event, data);
  }
}

module.exports = SocketHandler;
