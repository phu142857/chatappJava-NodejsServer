const jwt = require('jsonwebtoken');
const User = require('../models/User');
const Chat = require('../models/Chat');
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

class SocketHandler {
  constructor(io) {
    this.io = io;
    this.connectedUsers = new Map(); // Map to track connected users: { userId: { socketId, user, connectedAt } }
    this.socketToUserMap = new Map(); // Map to track socketId -> userId: { socketId: userId }
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
    const broadcastTypingToChat = async (chatId, eventName) => {
      const chat = await Chat.findById(chatId).select('participants isActive');
      if (!chat || !chat.isActive) {
        return;
      }

      const payload = {
        chatId: chat._id.toString(),
        userId: socket.userId,
        username: socket.user.username
      };

      for (const participant of chat.participants) {
        if (!participant.isActive) continue;
        const participantUserId = participant.user?.toString();
        if (!participantUserId || participantUserId === socket.userId) continue;
        this.io.to(`user_${participantUserId}`).emit(eventName, payload);
      }
    };

    socket.on('typing', async (data) => {
      try {
        const { chatId } = data || {};
        if (!chatId) return;
        await broadcastTypingToChat(chatId, 'user_typing');
      } catch (error) {
        console.error('Typing error:', error);
      }
    });

    socket.on('stop_typing', async (data) => {
      try {
        const { chatId } = data || {};
        if (!chatId) return;
        await broadcastTypingToChat(chatId, 'user_stop_typing');
      } catch (error) {
        console.error('Stop typing error:', error);
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

      // Emit to everyone currently in the room except the sender
      senderSocket.to(roomName).emit(event, payload);

      // CRITICAL: Also emit directly to user rooms for ALL participants to ensure delivery
      // This handles the case where a participant joins after frames start being sent
      for (const participantUserId of callInfo.participants) {
        if (participantUserId === senderSocket.userId) continue;

        const connected = this.connectedUsers.get(participantUserId);
        if (!connected) {
          continue;
        }

        const participantSocketId = connected.socketId;
        // CRITICAL: Always send to user room as well to ensure delivery
        // This ensures frames are delivered even if participant hasn't joined the call room yet
        this.sendToUser(participantUserId, event, payload);
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
        
        // CRITICAL FIX: For group calls, check if this is first user joining and send notification
        if (call.isGroupCall) {
          // Store io reference for use in nested functions
          const io = this.io;
          
          // Check number of connected participants BEFORE user joins
          const connectedParticipantsBeforeJoin = call.participants.filter(p => p.status === 'connected');
          const connectedCountBeforeJoin = connectedParticipantsBeforeJoin.length;
          
          // Check if caller has already joined
          const callerParticipant = call.participants.find(p => p.isCaller === true);
          const callerHasJoined = callerParticipant && callerParticipant.status === 'connected';
          const callerUserId = callerParticipant?.userId?.toString() || '';
          
          console.log(`[Socket] [GroupCall] join_call_room: connectedCountBeforeJoin=${connectedCountBeforeJoin}, callerHasJoined=${callerHasJoined}, callerUserId=${callerUserId}, currentUserId=${socket.userId}`);
          
          // Check if this is first user to join or first non-caller joining
          // CRITICAL FIX: Also send notification if caller is joining and they are the only connected participant
          // (when caller initiates call, notification should be sent when they join call room)
          const isFirstUserToJoin = connectedCountBeforeJoin === 0;
          const isFirstNonCallerJoining = connectedCountBeforeJoin === 1 && callerHasJoined && socket.userId !== callerUserId;
          // If caller is joining and they are the only connected participant, send notification
          const isCallerJoiningAsOnlyParticipant = socket.userId === callerUserId && connectedCountBeforeJoin === 1 && callerHasJoined;
          
          if (isFirstUserToJoin || isFirstNonCallerJoining || isCallerJoiningAsOnlyParticipant) {
            const reason = isCallerJoiningAsOnlyParticipant ? 'Caller joining as only participant' : (isFirstUserToJoin ? 'First user to join' : 'First non-caller joining');
            console.log(`[Socket] [GroupCall] ✓ Sending notification. Reason: ${reason}`);
            
            // Get chat information
            const chatId = extractChatId(call);
            if (chatId) {
              Chat.findById(chatId).populate('participants.user', 'username avatar').then(chat => {
                if (chat) {
                  // CRITICAL FIX: Determine the actual caller for notification
                  // BEST PRACTICE: Check if current user just initiated the call (within last 5 seconds)
                  // This ensures that when user B starts call, notification shows "user B started call"
                  let caller = null;
                  
                  // CRITICAL FIX: Prioritize lastNotificationCallerId from metadata (most reliable)
                  // This ensures we use the caller who most recently started the call, not the original caller
                  const lastNotificationCallerId = call.metadata?.lastNotificationCallerId;
                  const lastNotificationTimestamp = call.metadata?.lastNotificationTimestamp;
                  const notificationWasRecent = lastNotificationTimestamp && 
                    (Date.now() - new Date(lastNotificationTimestamp).getTime()) < 30000; // Within 30 seconds
                  
                  // Check if current user just initiated the call
                  const isLastNotificationCaller = lastNotificationCallerId === socket.userId;
                  const userJustInitiatedCall = isLastNotificationCaller && notificationWasRecent;
                  
                  // Method 2: Check recent logs (if available)
                  const recentInitLog = call.logs && call.logs.length > 0 ? 
                    call.logs.slice().reverse().find(log => 
                      log.action === 'call_initiated' && 
                      log.userId && 
                      log.userId.toString() === socket.userId &&
                      log.timestamp && 
                      (Date.now() - new Date(log.timestamp).getTime()) < 10000
                    ) : null;
                  
                  // Method 3: Check if user has status='notified' (just added, not yet joined)
                  const currentUserParticipant = call.participants.find(p => p.userId.toString() === socket.userId);
                  const hasNotifiedStatus = currentUserParticipant && currentUserParticipant.status === 'notified';
                  
                  const userJustStartedCall = userJustInitiatedCall || !!recentInitLog || hasNotifiedStatus;
                  
                  console.log(`[Socket] [GroupCall] Caller determination: lastNotificationCallerId=${lastNotificationCallerId}, userJustStartedCall=${userJustStartedCall} (recentInitLog: ${!!recentInitLog}, hasNotifiedStatus: ${hasNotifiedStatus}), isFirstUserToJoin=${isFirstUserToJoin}, isCallerJoiningAsOnlyParticipant=${isCallerJoiningAsOnlyParticipant}`);
                  
                  // CRITICAL FIX: Priority order:
                  // 1. If user just started call OR is first to join, use them as caller
                  // 2. If metadata has recent lastNotificationCallerId, use that caller (not original caller)
                  // 3. Otherwise, use original caller or current user
                  if (userJustStartedCall || isFirstUserToJoin || isCallerJoiningAsOnlyParticipant) {
                    // Current user is the caller (they just started/joined first)
                    console.log(`[Socket] [GroupCall] ✓ Using current user as caller (they just started call)`);
                    caller = socket.user;
                    sendGroupCallNotification();
                  } else if (lastNotificationCallerId && notificationWasRecent) {
                    // CRITICAL: Use the last notification caller from metadata (most recent caller)
                    // This ensures "user B started call" when user B starts call, even if user A was original caller
                    User.findById(lastNotificationCallerId).then(callerUser => {
                      if (callerUser) {
                        console.log(`[Socket] [GroupCall] ✓ Using last notification caller from metadata: ${callerUser.username} (${lastNotificationCallerId})`);
                        caller = callerUser;
                        sendGroupCallNotification();
                      } else {
                        // Fallback: use current user
                        console.log(`[Socket] [GroupCall] Fallback: Last notification caller not found, using current user`);
                        caller = socket.user;
                        sendGroupCallNotification();
                      }
                    });
                  } else if (callerParticipant && callerParticipant.userId.toString() === socket.userId) {
                    // Current user IS the original caller, use them
                    console.log(`[Socket] [GroupCall] ✓ Using current user as caller (they are the original caller)`);
                    caller = socket.user;
                    sendGroupCallNotification();
                  } else if (callerParticipant) {
                    // Use the original caller from call (fallback if no metadata)
                    User.findById(callerParticipant.userId).then(callerUser => {
                      if (callerUser) {
                        console.log(`[Socket] [GroupCall] ✓ Using original caller (fallback): ${callerUser.username}`);
                        caller = callerUser;
                        sendGroupCallNotification();
                      } else {
                        // Fallback: use current user
                        console.log(`[Socket] [GroupCall] Fallback: Using current user as caller`);
                        caller = socket.user;
                        sendGroupCallNotification();
                      }
                    });
                  } else {
                    // No caller found, use current user
                    console.log(`[Socket] [GroupCall] No caller found, using current user as caller`);
                    caller = socket.user;
                    sendGroupCallNotification();
                  }
                  
                  function sendGroupCallNotification() {
                    if (!caller) return;
                    
                    // CRITICAL FIX: Update metadata to track the caller who sent this notification
                    if (!call.metadata) {
                      call.metadata = {};
                    }
                    const callerIdStr = caller._id.toString();
                    call.metadata.lastNotificationCallerId = callerIdStr;
                    call.metadata.lastNotificationTimestamp = new Date();
                    
                    // Save metadata immediately
                    call.save().catch(err => {
                      console.error(`[Socket] [GroupCall] Failed to save call metadata: ${err.message}`);
                    });
                    
                    const notificationData = {
                      callId: call.callId,
                      chatId: chatId,
                      chatName: chat.name,
                      callerName: caller.username,
                      caller: {
                        id: caller._id,
                        username: caller.username,
                        avatar: caller.avatar
                      },
                      callType: call.type,
                      isGroupCall: true,
                      mediaTopology: 'sfu',
                      bannerCopy: `Live group ${call.type} call in progress`,
                      timestamp: new Date()
                    };
                    
                    console.log(`[Socket] [GroupCall] Sending notification with caller: ${caller.username} (${callerIdStr}), metadata.lastNotificationCallerId=${call.metadata.lastNotificationCallerId}`);
                    
                    // STRATEGY 1: Emit to individual user rooms (SEND TO ALL - no exceptions)
                    for (const participant of chat.participants) {
                      if (participant.user) {
                        let participantUserId;
                        if (typeof participant.user === 'object' && participant.user._id) {
                          participantUserId = participant.user._id.toString();
                        } else {
                          participantUserId = participant.user.toString();
                        }
                        
                        // Send to ALL participants (no skipping)
                        const userRoom = `user_${participantUserId}`;
                        io.to(userRoom).emit('group_call_passive_alert', notificationData);
                        console.log(`[Socket] [GroupCall] ✓ Sent group_call_passive_alert to user room: ${userRoom} (userId: ${participantUserId})`);
                      }
                    }
                    
                    // STRATEGY 2: Emit to chat room
                    const chatRoom = `chat_${chatId}`;
                    io.to(chatRoom).emit('group_call_passive_alert', notificationData);
                    console.log(`[Socket] [GroupCall] ✓ Sent group_call_passive_alert to chat room: ${chatRoom}`);
                    
                    // STRATEGY 3: Emit to group room if available
                    if (chat.groupId) {
                      io.to(`group_${chat.groupId}`).emit('group_call_passive_alert', notificationData);
                      console.log(`[Socket] [GroupCall] ✓ Sent group_call_passive_alert to group room: group_${chat.groupId}`);
                    }
                    
                    // STRATEGY 4: Broadcast
                    io.emit('group_call_passive_alert_broadcast', {
                      ...notificationData,
                      targetChatId: chatId
                    });
                    console.log(`[Socket] [GroupCall] ✓ Broadcast group_call_passive_alert_broadcast to all clients (targetChatId: ${chatId})`);
                  }
                }
              }).catch(err => {
                console.error(`[Socket] [GroupCall] Error getting chat:`, err);
              });
            }
          } else {
            console.log(`[Socket] [GroupCall] ✗ Skipping notification: not first user to join and not first non-caller joining`);
          }
        }
        
        // CRITICAL FIX: Update activeCalls to include only connected participants from database
        // This ensures video frames are only forwarded to participants who are actually in the call
        const connectedParticipants = call.participants.filter(p => p.status === 'connected');
        const connectedParticipantIds = connectedParticipants.map(p => {
          if (p.userId && typeof p.userId === 'object' && p.userId._id) {
            return p.userId._id.toString();
          }
          return p.userId.toString();
        });
        
        // Store call info - only include connected participants
        this.activeCalls.set(callId, {
          callId: callId,
          participants: connectedParticipantIds,
          roomId: call.getRoomId(callId)
        });
        
        console.log(`join_call_room: Updated activeCalls for ${callId} with ${connectedParticipantIds.length} connected participants (out of ${call.participants.length} total):`, connectedParticipantIds);

        // Notify other participants
        socket.to(`call_${callId}`).emit('user_joined_call', {
          callId: callId,
          userId: socket.userId,
          username: socket.user.username,
          avatar: socket.user.avatar
        });

        // CRITICAL: Only send participants who have actually joined call room (are in socket room)
        // Get list of sockets currently in room
        const roomName = `call_${callId}`;
        const roomSockets = this.io.sockets.adapter.rooms.get(roomName) || new Set();
        
        // Get list of userIds that have joined room
        const joinedUserIds = new Set();
        for (const socketId of roomSockets) {
          const userId = this.socketToUserMap.get(socketId);
          if (userId) {
            joinedUserIds.add(userId);
          }
        }
        
        // Filter participants to only get those who have joined room
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

        // Send call info to user with only participants who have joined
        socket.emit('call_room_joined', {
          callId: callId,
          roomId: call.getRoomId(callId),
          participants: activeParticipants
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

    socket.on('video_frame', async (data) => {
      try {
        const { callId, frame, timestamp } = data;
        
        if (!callId || !frame) {
          console.error('video_frame: callId or frame missing');
          return;
        }

        // Verify that user is in call room
        const callInfo = this.activeCalls.get(callId);
        if (!callInfo) {
          console.error(`video_frame: Call ${callId} not found in activeCalls`);
          // Try to find call in database and add to activeCalls
          Call.findOne({ callId: callId }).then(call => {
            if (call) {
              // CRITICAL FIX: Only include participants with status = 'connected'
              const connectedParticipants = call.participants.filter(p => p.status === 'connected');
              const connectedParticipantIds = connectedParticipants.map(p => {
                if (p.userId && typeof p.userId === 'object' && p.userId._id) {
                  return p.userId._id.toString();
                }
                return p.userId.toString();
              });
              
              this.activeCalls.set(callId, {
                callId: callId,
                participants: connectedParticipantIds,
                roomId: call.getRoomId(callId)
              });
              
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
            console.error(`video_frame: Error searching for call ${callId}:`, err);
          });
          return;
        }

        // Create payload with sender information
        const payload = {
          callId: callId,
          userId: socket.userId,
          frame: frame,
          timestamp: timestamp || Date.now()
        };

        // CRITICAL: Refresh participants from database synchronously before forwarding
        // This ensures new participants receive frames immediately
        // CRITICAL FIX: Only include participants with status = 'connected' (actually in the call)
        try {
          const call = await Call.findOne({ callId: callId });
          if (call) {
            // Filter to only include participants with status = 'connected'
            const connectedParticipants = call.participants.filter(p => p.status === 'connected');
            const latestParticipantIds = connectedParticipants.map(p => {
              if (p.userId && typeof p.userId === 'object' && p.userId._id) {
                return p.userId._id.toString();
              }
              return p.userId.toString();
            });
            
            // Update activeCalls with only connected participants
            this.activeCalls.set(callId, {
              callId: callId,
              participants: latestParticipantIds,
              roomId: call.getRoomId(callId)
            });
            
            // Forward frame with updated participant list (only connected participants)
            this.emitToCallParticipantsExceptSender(callId, 'video_frame', payload, socket);
          } else {
            // Fallback: use existing callInfo
            this.emitToCallParticipantsExceptSender(callId, 'video_frame', payload, socket);
          }
        } catch (err) {
          console.error(`video_frame: Error refreshing participants:`, err);
          // Fallback: use existing callInfo
          this.emitToCallParticipantsExceptSender(callId, 'video_frame', payload, socket);
        }

      } catch (error) {
        console.error('Error processing video_frame:', error);
        socket.emit('call_error', { message: 'Error sending video frame' });
      }
    });

    // Handle custom audio frames relayed via Socket.IO
    socket.on('audio_frame', async (data) => {
      try {
        const { callId, audio, timestamp } = data;
        
        if (!callId || !audio) {
          console.error('audio_frame: callId or audio missing');
          return;
        }

        // Verify that user is in call room
        const callInfo = this.activeCalls.get(callId);
        if (!callInfo) {
          console.error(`audio_frame: Call ${callId} not found in activeCalls`);
          // Try to find call in database and add to activeCalls
          Call.findOne({ callId: callId }).then(call => {
            if (call) {
              // CRITICAL FIX: Only include participants with status = 'connected'
              const connectedParticipants = call.participants.filter(p => p.status === 'connected');
              const connectedParticipantIds = connectedParticipants.map(p => {
                if (p.userId && typeof p.userId === 'object' && p.userId._id) {
                  return p.userId._id.toString();
                }
                return p.userId.toString();
              });
              
              this.activeCalls.set(callId, {
                callId: callId,
                participants: connectedParticipantIds,
                roomId: call.getRoomId(callId)
              });
              
              // Retry sending the audio frame
              const payload = {
                callId: callId,
                userId: socket.userId,
                audio: audio,
                timestamp: timestamp || Date.now()
              };
              this.emitToCallParticipantsExceptSender(callId, 'audio_frame', payload, socket);
            }
          }).catch(err => {
            console.error(`audio_frame: Error searching for call ${callId}:`, err);
          });
          return;
        }

        // Create payload with sender information
        const payload = {
          callId: callId,
          userId: socket.userId,
          audio: audio,
          timestamp: timestamp || Date.now()
        };

        // CRITICAL: Refresh participants from database synchronously before forwarding
        // This ensures new participants receive audio immediately
        // CRITICAL FIX: Only include participants with status = 'connected' (actually in the call)
        try {
          const call = await Call.findOne({ callId: callId });
          if (call) {
            // Filter to only include participants with status = 'connected'
            const connectedParticipants = call.participants.filter(p => p.status === 'connected');
            const latestParticipantIds = connectedParticipants.map(p => {
              if (p.userId && typeof p.userId === 'object' && p.userId._id) {
                return p.userId._id.toString();
              }
              return p.userId.toString();
            });
            
            // Update activeCalls with only connected participants
            this.activeCalls.set(callId, {
              callId: callId,
              participants: latestParticipantIds,
              roomId: call.getRoomId(callId)
            });
            
            // Forward audio frame with updated participant list (only connected participants)
            this.emitToCallParticipantsExceptSender(callId, 'audio_frame', payload, socket);
          } else {
            // Fallback: use existing callInfo
            this.emitToCallParticipantsExceptSender(callId, 'audio_frame', payload, socket);
          }
        } catch (err) {
          console.error(`audio_frame: Error refreshing participants:`, err);
          // Fallback: use existing callInfo
          this.emitToCallParticipantsExceptSender(callId, 'audio_frame', payload, socket);
        }

      } catch (error) {
        console.error('Error processing audio_frame:', error);
        socket.emit('call_error', { message: 'Error sending audio frame' });
      }
    });

  }

  // Broadcast to all connected users
  broadcast(event, data) {
    this.io.emit(event, data);
  }
}

module.exports = SocketHandler;
