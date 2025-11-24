const Call = require('../models/Call');
const Chat = require('../models/Chat');
const Group = require('../models/Group');
const User = require('../models/User');
const Message = require('../models/Message');
const { v4: uuidv4 } = require('uuid');

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

// Helper function to add participant with deduplication and sessionId
const addParticipantUnique = (call, userId, username, avatar, opts = {}) => {
    const idStr = userId.toString();
    const existing = call.participants.find(p => p.userId.toString() === idStr);
    if (existing) {
        // Update status/session if needed
        existing.status = opts.status || existing.status;
        if (opts.sessionId) existing.sessionId = opts.sessionId;
        if (opts.joinedAt) existing.joinedAt = opts.joinedAt;
        return existing;
    }
    const newP = {
        userId,
        username,
        avatar: avatar || '',
        status: opts.status || 'notified',
        isCaller: !!opts.isCaller,
        joinedAt: opts.joinedAt || new Date(),
        sessionId: opts.sessionId || uuidv4()
    };
    call.participants.push(newP);
    return newP;
};

// Generate group-anchored call ID
// Format: GC_[CHAT_ID]_[TIMESTAMP]
// This ensures that simultaneous initiations for the same group get the same Call ID
// The timestamp (using time window) differentiates subsequent calls after previous ones have ended
// Note: The actual implementation uses a time window approach in initiateGroupCall for better race condition handling

// Generate WebRTC room ID
const generateRoomId = () => {
    return `room_${Date.now()}_${uuidv4().substring(0, 12)}`;
};

// Initiate a new group call (Discord-style)
const initiateGroupCall = async (req, res) => {
    try {
        const { chatId, type = 'video' } = req.body;
        const callerId = req.user.id;

        // Validate input
        if (!chatId) {
            return res.status(400).json({
                success: false,
                message: 'Chat ID is required'
            });
        }

        if (!['audio', 'video'].includes(type)) {
            return res.status(400).json({
                success: false,
                message: 'Call type must be audio or video'
            });
        }

        // Check if chat exists and is a group chat
        const chat = await Chat.findById(chatId);
        if (!chat) {
            return res.status(404).json({
                success: false,
                message: 'Chat not found'
            });
        }

        if (chat.type !== 'group') {
            return res.status(400).json({
                success: false,
                message: 'This endpoint is for group calls only. Use /api/calls for 1:1 calls.'
            });
        }

        // Check if user is participant in chat
        const isParticipant = chat.participants.some(p => p.user && p.user.toString() === callerId);
        if (!isParticipant) {
            return res.status(403).json({
                success: false,
                message: 'You are not a participant in this group'
            });
        }

        // Get caller information
        const caller = await User.findById(callerId);
        if (!caller) {
            return res.status(404).json({
                success: false,
                message: 'Caller not found'
            });
        }

        // CRITICAL FIX: Check for existing call FIRST (before generating callId)
        // This ensures we always return the same call for the same chat
        const chatIdStr = chatId.toString();
        
        // STEP 1: Check if there's already an active call for this chat
        let call = await Call.findOne({
            chatId: chatId,
            isGroupCall: true,
            status: { $in: ['initiated', 'notified', 'ringing', 'active'] }
        });
        
        if (call) {
            // Active call exists - add caller if not already participant
            console.log(`[GroupCall] Found existing call for chatId ${chatId}: callId=${call.callId}`);
            const isParticipant = call.participants.some(
                p => p.userId && p.userId.toString() === callerId
            );
            
            if (!isParticipant) {
                addParticipantUnique(call, callerId, caller.username, caller.avatar || '', {
                    status: 'notified',
                    isCaller: false,
                    sessionId: uuidv4()
                });
                await call.save();
            }
            
            // Return existing call
            await call.populate([
                { path: 'chatId', select: 'name type participants' },
                { path: 'participants.userId', select: 'username avatar status' }
            ]);
            
            return res.status(200).json({
                success: true,
                message: 'Group call already active',
                callId: call.callId,
                data: call,
                isExisting: true
            });
        }
        
        // STEP 2: No active call exists - create new one using atomic operation
        // Check if there's an ended call - if so, use timestamp to create new callId
        // Otherwise, use deterministic callId for first call
        const endedCall = await Call.findOne({
            chatId: chatId,
            isGroupCall: true,
            status: 'ended'
        }).sort({ endedAt: -1 }); // Get most recent ended call
        
        // Generate callId: use timestamp if there was a previous call, otherwise use deterministic ID
        const deterministicCallId = endedCall 
            ? `GC_${chatIdStr}_${Date.now()}` // New call after old one ended
            : `GC_${chatIdStr}`; // First call for this chat
        
        console.log(`[GroupCall] Creating new call for chatId ${chatId}: callId=${deterministicCallId}, hadEndedCall=${!!endedCall}`);
        
        // Use findOneAndUpdate with chatId as primary key to ensure atomicity
        // This is atomic - only one request will successfully create the call
        try {
            call = await Call.findOneAndUpdate(
                { 
                    chatId: chatId,
                    isGroupCall: true,
                    status: { $in: ['initiated', 'notified', 'ringing', 'active'] }
                },
                { 
                    $setOnInsert: {
                        // Only set these if creating a new document
                        // CRITICAL: Use deterministic callId for first call, timestamped for subsequent calls
                        callId: deterministicCallId,
                        type: type,
                        chatId: chatId,
                        isGroupCall: true,
                        status: 'notified',
                        webrtcData: {
                            // CRITICAL: Use chatId-based roomId so all participants use same room
                            roomId: `room_${chatIdStr}`,
                            iceServers: [
                                { urls: "turn:103.75.183.125:3478", username: "phu142", credential: "phu142" },
                                { urls: 'stun:stun.l.google.com:19302' },
                                { urls: 'stun:stun1.l.google.com:19302' },
                                { urls: 'stun:stun2.l.google.com:19302' }
                            ],
                            mediaTopology: 'mesh'
                        },
                        participants: [{
                            userId: callerId,
                            username: caller.username,
                            avatar: caller.avatar || '',
                            status: 'connected',
                            isCaller: true,
                            joinedAt: new Date(),
                            sessionId: uuidv4()
                        }],
                        participantMedia: [{
                            userId: callerId,
                            audioMuted: false,
                            videoMuted: true,
                            screenSharing: false,
                            connectionQuality: 'good'
                        }],
                        logs: [{
                            userId: callerId,
                            action: 'call_initiated',
                            details: `Initiated ${type} group call`
                        }]
                    }
                },
                { 
                    upsert: true, 
                    new: true,
                    setDefaultsOnInsert: true
                }
            );
        } catch (error) {
            // Handle unique constraint violation (race condition)
            if (error.code === 11000 || error.message.includes('duplicate key')) {
                console.log(`[GroupCall] Unique constraint violation (race condition), fetching existing call`);
                // Another request created the call - fetch it
                call = await Call.findOne({
                    chatId: chatId,
                    isGroupCall: true,
                    status: { $in: ['initiated', 'notified', 'ringing', 'active'] }
                });
                
                if (!call) {
                    // Still no call found - this shouldn't happen, but handle it
                    throw new Error('Failed to create or find call after race condition');
                }
            } else {
                throw error;
            }
        }
        
        // STEP 3: Check if call was just created by this request or by another simultaneous request
        const wasJustCreatedByThisCaller = call.participants.length === 1 && 
                                          call.participants[0].userId.toString() === callerId &&
                                          call.participants[0].isCaller === true;
        
        if (!wasJustCreatedByThisCaller) {
            // Another request created the call first (race condition) - add this caller as participant
            console.log(`[GroupCall] Call was created by another request (race condition), adding caller as participant. callId=${call.callId}, participants=${call.participants.length}`);
            const isCallerParticipant = call.participants.some(
                p => p.userId && p.userId.toString() === callerId
            );
            
            if (!isCallerParticipant) {
                addParticipantUnique(call, callerId, caller.username, caller.avatar || '', {
                    status: 'notified',
                    isCaller: false,
                    sessionId: uuidv4()
                });
                await call.save();
                console.log(`[GroupCall] Added caller as participant. New participant count: ${call.participants.length}`);
            }
            
            // Return existing call with isExisting flag
            await call.populate([
                { path: 'chatId', select: 'name type participants' },
                { path: 'participants.userId', select: 'username avatar status' }
            ]);
            
            return res.status(200).json({
                success: true,
                message: 'Group call already active',
                callId: call.callId,
                data: call,
                isExisting: true
            });
        }
        
        // Call was just created by this caller - continue with normal flow
        console.log(`[GroupCall] Call created successfully by caller. callId=${call.callId}, roomId=${call.webrtcData?.roomId}`);
        
        // Check if this call was just created or already existed
        const wasJustCreated = call.participants.length === 1 && 
                               call.participants[0].userId.toString() === callerId &&
                               call.participants[0].isCaller === true;
        
        // CRITICAL: Log to verify all users get the same callId
        console.log(`[GroupCall] Call lookup result for chatId ${chatId}: callId=${call.callId}, roomId=${call.webrtcData?.roomId}, wasJustCreated=${wasJustCreated}, participants=${call.participants.length}`);
        
        if (!wasJustCreated) {
            // Call already existed (another request created it first) - add this caller as participant
            const isCallerParticipant = call.participants.some(
                p => p.userId && p.userId.toString() === callerId
            );
            
            if (!isCallerParticipant) {
                addParticipantUnique(call, callerId, caller.username, caller.avatar || '', {
                    status: 'notified',
                    isCaller: false,
                    sessionId: uuidv4()
                });
                await call.save();
            }
            
            // Return existing call with isExisting flag
            await call.populate([
                { path: 'chatId', select: 'name type participants' },
                { path: 'participants.userId', select: 'username avatar status' }
            ]);
            
            return res.status(200).json({
                success: true,
                message: 'Group call already active',
                callId: call.callId,
                data: call,
                isExisting: true
            });
        }
        
        // If call was just created, add other participants
        if (call.participants.length === 1 && call.participants[0].userId.toString() === callerId) {
            // Add other participants as notified
            for (const participant of chat.participants) {
                if (participant.user && participant.user.toString() !== callerId) {
                    const user = await User.findById(participant.user);
                    if (user) {
                        addParticipantUnique(call, participant.user, user.username, user.avatar || '', {
                            status: 'notified',
                            isCaller: false,
                            sessionId: uuidv4()
                        });
                    }
                }
            }
            await call.save();
        } else {
            // Call already existed, check if caller needs to be added
            const isParticipantInCall = call.participants.some(
                p => p.userId && p.userId.toString() === callerId
            );
            
            if (!isParticipantInCall) {
                addParticipantUnique(call, callerId, caller.username, caller.avatar || '', {
                    status: 'connected',
                    isCaller: false,
                    joinedAt: new Date(),
                    sessionId: uuidv4()
                });
                
                const existingMedia = call.participantMedia.find(m => m.userId.toString() === callerId);
                if (!existingMedia) {
                    call.participantMedia.push({
                        userId: callerId,
                        audioMuted: false,
                        videoMuted: true,
                        screenSharing: false,
                        connectionQuality: 'good'
                    });
                }
                
                await call.save();
            }
        }
        
        // Check if this is a newly created call by checking if other participants from the chat are missing
        // This helps determine if we need to add other participants and create system message
        const chatParticipantIds = chat.participants
            .filter(p => p.user && p.user.toString() !== callerId)
            .map(p => p.user.toString());
        const callParticipantIds = call.participants
            .filter(p => p.userId && p.userId.toString() !== callerId)
            .map(p => p.userId.toString());
        
        const isNewCall = chatParticipantIds.some(id => !callParticipantIds.includes(id));
        
        if (isNewCall) {
            // Add other participants as notified (not ringing)
            for (const participant of chat.participants) {
                if (participant.user && participant.user.toString() !== callerId) {
                    const isAlreadyParticipant = call.participants.some(
                        p => p.userId && p.userId.toString() === participant.user.toString()
                    );
                    
                    if (!isAlreadyParticipant) {
                        const user = await User.findById(participant.user);
                        if (user) {
                            addParticipantUnique(call, participant.user, user.username, user.avatar || '', {
                                status: 'notified',
                                isCaller: false,
                                sessionId: uuidv4()
                            });
                        }
                    }
                }
            }
            
            // Add initial log if not already present
            const hasInitLog = call.logs.some(
                log => log.action === 'call_initiated' && log.userId && log.userId.toString() === callerId
            );
            if (!hasInitLog) {
                call.logs.push({
                    userId: callerId,
                    action: 'call_initiated',
                    details: `Initiated ${type} group call`
                });
            }
            
            await call.save();

            // Create system message in group chat (only for new calls, and only once)
            // Check if system message already exists to avoid duplicates
            const existingSystemMessage = await Message.findOne({
                chat: chatId,
                messageType: 'system',
                systemMessageType: 'call_started',
                'metadata.callId': call.callId,
                createdAt: { $gte: new Date(Date.now() - 5000) } // Within last 5 seconds
            });
            
            if (!existingSystemMessage) {
                const systemMessage = new Message({
                    chat: chatId,
                    sender: callerId,
                    content: `${caller.username} started a group ${type} call`,
                    messageType: 'system',
                    systemMessageType: 'call_started',
                    metadata: {
                        callId: call.callId,
                        callType: type,
                        isGroupCall: true
                    }
                });
                await systemMessage.save();

                // Update chat last message
                chat.lastMessage = systemMessage._id;
                chat.lastActivity = new Date();
                await chat.save();
            }
        }

        // Populate call data for response
        await call.populate([
            { path: 'chatId', select: 'name type participants' },
            { path: 'participants.userId', select: 'username avatar status' }
        ]);

        // CRITICAL: Log the callId to ensure all users get the same one
        console.log(`[GroupCall] Returning call for chatId ${chatId}: callId=${call.callId}, roomId=${call.webrtcData?.roomId}, participants=${call.participants.length}`);

        // Send passive notification to other participants via Socket.io
        const io = req.app.get('io');
        if (io) {
            const callerIdStr = callerId.toString();
            
            const notificationData = {
                callId: call.callId, // CRITICAL: Use the actual callId from database
                chatId: chatId,
                chatName: chat.name,
                callerName: caller.username,
                caller: {
                    id: caller._id,
                    username: caller.username,
                    avatar: caller.avatar
                },
                callType: type,
                isGroupCall: true,
                mediaTopology: 'mesh',
                bannerCopy: `Live group ${type} call in progress`,
                timestamp: new Date()
            };
            
            // STRATEGY 1: Emit to individual user rooms (EXCLUDE caller - they don't need notification)
            for (const participant of call.participants) {
                let participantUserId;
                if (participant.userId && typeof participant.userId === 'object' && participant.userId._id) {
                    participantUserId = participant.userId._id.toString();
                } else {
                    participantUserId = participant.userId.toString();
                }
                
                // Skip caller - they initiated the call, they don't need a "join" notification
                if (participantUserId === callerIdStr) {
                    console.log(`Skipping notification for caller: ${participantUserId}`);
                    continue;
                }
                
                // Send to other participants only
                const userRoom = `user_${participantUserId}`;
                io.to(userRoom).emit('group_call_passive_alert', notificationData);
                console.log(`Sent group_call_passive_alert to user room: ${userRoom}`);
            }
            
            // STRATEGY 2: Also emit to chat room (for users actively viewing the chat)
            const chatRoom = `chat_${chatId}`;
            io.to(chatRoom).emit('group_call_passive_alert', notificationData);
            console.log(`Sent group_call_passive_alert to chat room: ${chatRoom}`);
            
            // STRATEGY 3: Emit to group room if available
            if (chat.groupId) {
                io.to(`group_${chat.groupId}`).emit('group_call_passive_alert', notificationData);
                console.log(`Sent group_call_passive_alert to group room: group_${chat.groupId}`);
            }
            
            // STRATEGY 4: Broadcast to all connected sockets (as last resort fallback)
            // This ensures delivery even if room membership is not properly set up
            io.emit('group_call_passive_alert_broadcast', {
                ...notificationData,
                targetChatId: chatId // Clients will filter by this
            });
            console.log(`Broadcast group_call_passive_alert_broadcast to all clients`);
        }

        // CRITICAL: Always return the actual callId from the database (not generated one)
        res.status(201).json({
            success: true,
            message: 'Group call initiated successfully',
            callId: call.callId, // Use actual callId from database
            data: call,
            isExisting: !wasJustCreated // Indicate if this was an existing call
        });

    } catch (error) {
        console.error('Initiate group call error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error',
            error: error.message
        });
    }
};

// Join an existing group call
const joinGroupCall = async (req, res) => {
    try {
        const { callId } = req.params;
        const userId = req.user.id;

        // Validate callId
        if (!callId) {
            return res.status(400).json({
                success: false,
                message: 'Call ID is required'
            });
        }

        // Find the call
        const call = await Call.findOne({ callId: callId });
        if (!call) {
            console.log(`[GroupCall] Join failed: Call not found for callId=${callId}, userId=${userId}`);
            return res.status(404).json({
                success: false,
                message: 'Call not found'
            });
        }

        if (!call.isGroupCall) {
            console.log(`[GroupCall] Join failed: Not a group call for callId=${callId}, userId=${userId}`);
            return res.status(400).json({
                success: false,
                message: 'This is not a group call'
            });
        }

        // Check if call is still active
        if (!['initiated', 'notified', 'ringing', 'active'].includes(call.status)) {
            console.log(`[GroupCall] Join failed: Call not active (status=${call.status}) for callId=${callId}, userId=${userId}`);
            return res.status(400).json({
                success: false,
                message: `Call is no longer active (status: ${call.status})`
            });
        }

        // Check if user is a participant
        let participant = call.participants.find(p => p.userId.toString() === userId);
        if (!participant) {
            console.log(`[GroupCall] Join failed: User not a participant for callId=${callId}, userId=${userId}`);
            // Try to add user as participant (might have been added to call but not saved properly)
            const user = await User.findById(userId);
            if (!user) {
                return res.status(404).json({
                    success: false,
                    message: 'User not found'
                });
            }
            
            // Use helper to add participant with sessionId
            participant = addParticipantUnique(call, userId, user.username || 'Unknown', user.avatar || '', {
                status: 'notified',
                isCaller: false,
                sessionId: uuidv4()
            });
            await call.save();
            console.log(`[GroupCall] Added user as participant for callId=${callId}, userId=${userId}, sessionId=${participant.sessionId}`);
        }

        // Generate new sessionId for this join (to invalidate old signalling messages)
        const newSessionId = uuidv4();
        participant.sessionId = newSessionId;
        participant.status = 'connected';
        participant.joinedAt = new Date();
        
        console.log(`[GroupCall] User ${userId} joining with new sessionId=${newSessionId}`);

        // Initialize media state for this participant
        const existingMedia = call.participantMedia.find(m => m.userId.toString() === userId);
        if (!existingMedia) {
            call.participantMedia.push({
                userId: userId,
                audioMuted: false,
                videoMuted: true, // Video off by default
                screenSharing: false,
                connectionQuality: 'good'
            });
        }

        // Update call status to active if needed
        if (call.status === 'notified' || call.status === 'initiated') {
            call.status = 'active';
        }

        // Add log
        call.logs.push({
            userId: userId,
            action: 'user_joined',
            details: 'User joined the group call'
        });

        await call.save();

        // Populate call data
        await call.populate([
            { path: 'chatId', select: 'name type' },
            { path: 'participants.userId', select: 'username avatar status' }
        ]);

        // Notify all participants via Socket.io
        const io = req.app.get('io');
        if (io) {
            const user = await User.findById(userId);
            // Extract chatId properly using helper function
            const chatId = extractChatId(call);
            if (!chatId) {
                console.error(`[GroupCall] Cannot extract chatId from call ${call.callId}`);
                return res.status(500).json({
                    success: false,
                    message: 'Invalid call data'
                });
            }
            const socketRoom = `group_call_${chatId}`;
            
            const joinData = {
                callId: call.callId,
                chatId: chatId,
                user: {
                    id: userId,
                    username: user.username,
                    avatar: user.avatar
                },
                timestamp: new Date()
            };
            
            // CRITICAL FIX: Use chatId-based socket room instead of callId
            io.to(socketRoom).emit('group_call_participant_joined', joinData);
            console.log(`User ${userId} joined group call ${call.callId} - notified room ${socketRoom}`);
        }

        // Extract chatId for response
        const chatId = extractChatId(call);
        
        res.json({
            success: true,
            message: 'Joined group call successfully',
            data: call,
            sessionId: participant.sessionId,
            chatId: chatId
        });

    } catch (error) {
        console.error('Join group call error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error',
            error: error.message
        });
    }
};

// Update participant media state
const updateParticipantMedia = async (req, res) => {
    try {
        const { callId } = req.params;
        const userId = req.user.id;
        const { audioMuted, videoMuted, screenSharing } = req.body;

        const call = await Call.findOne({ callId: callId });
        if (!call) {
            return res.status(404).json({
                success: false,
                message: 'Call not found'
            });
        }

        const participant = call.participants.find(p => p.userId.toString() === userId);
        if (!participant) {
            return res.status(403).json({
                success: false,
                message: 'You are not a participant in this call'
            });
        }

        // Update media state
        let mediaState = call.participantMedia.find(m => m.userId.toString() === userId);
        if (!mediaState) {
            mediaState = {
                userId: userId,
                audioMuted: false,
                videoMuted: true,
                screenSharing: false
            };
            call.participantMedia.push(mediaState);
        }

        if (audioMuted !== undefined) mediaState.audioMuted = audioMuted;
        if (videoMuted !== undefined) mediaState.videoMuted = videoMuted;
        if (screenSharing !== undefined) mediaState.screenSharing = screenSharing;

        // Add log
        if (audioMuted !== undefined) {
            call.logs.push({
                userId: userId,
                action: 'mute_toggled',
                details: `Audio ${audioMuted ? 'muted' : 'unmuted'}`
            });
        }

        if (videoMuted !== undefined) {
            call.logs.push({
                userId: userId,
                action: 'video_toggled',
                details: `Video ${videoMuted ? 'off' : 'on'}`
            });
        }

        await call.save();

        // Broadcast media update to all participants
        const io = req.app.get('io');
        if (io) {
            // Extract chatId properly using helper function
            const chatId = extractChatId(call);
            if (!chatId) {
                console.error(`[GroupCall] Cannot extract chatId from call ${call.callId}`);
                return res.status(500).json({
                    success: false,
                    message: 'Invalid call data'
                });
            }
            const socketRoom = `group_call_${chatId}`;
            
            const updateData = {
                callId: call.callId,
                chatId: chatId,
                userId: userId,
                audioMuted: mediaState.audioMuted,
                videoMuted: mediaState.videoMuted,
                screenSharing: mediaState.screenSharing,
                timestamp: new Date()
            };
            
            // CRITICAL FIX: Use chatId-based socket room instead of callId
            io.to(socketRoom).emit('participant_media_updated', updateData);
        }

        res.json({
            success: true,
            message: 'Media state updated successfully',
            data: mediaState
        });

    } catch (error) {
        console.error('Update participant media error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error',
            error: error.message
        });
    }
};

// Leave group call
const leaveGroupCall = async (req, res) => {
    try {
        const { callId } = req.params;
        const userId = req.user.id;

        const call = await Call.findOne({ callId: callId });
        if (!call) {
            return res.status(404).json({
                success: false,
                message: 'Call not found'
            });
        }

        const participant = call.participants.find(p => p.userId.toString() === userId);
        if (!participant) {
            return res.status(403).json({
                success: false,
                message: 'You are not a participant in this call'
            });
        }

        // Update participant status
        participant.status = 'left';
        participant.leftAt = new Date();
        
        // CRITICAL FIX: Remove participantMedia entry for user
        call.participantMedia = call.participantMedia.filter(pm => pm.userId.toString() !== userId);
        
        // CRITICAL FIX: Remove participant from participants array to prevent ghost peers
        // This ensures stale ICE candidates/offers don't get forwarded to old sessions
        call.participants = call.participants.filter(p => p.userId.toString() !== userId);
        
        // CRITICAL FIX: Deduplicate remaining participants (safety check)
        const unique = {};
        call.participants = call.participants.filter(p => {
            const id = p.userId.toString();
            if (unique[id]) {
                console.log(`[GroupCall] Removing duplicate participant: ${id}`);
                return false;
            }
            unique[id] = true;
            return true;
        });

        // Add log
        call.logs.push({
            userId: userId,
            action: 'user_left',
            details: 'User left the group call'
        });

        // Check if call should end (if only 1 or 0 participants remain)
        // CRITICAL: Only end if there are 0 active participants (everyone left)
        // If 1 remains, that's the last person - they should be able to stay in the call
        const activeParticipants = call.participants.filter(p => p.status === 'connected');
        const callEnded = activeParticipants.length === 0;
        
        console.log(`[GroupCall] Leave - userId=${userId}, remainingParticipants=${call.participants.length}, activeParticipants=${activeParticipants.length}, callEnded=${callEnded}`);
        
        if (callEnded) {
            console.log(`[GroupCall] Ending group call ${call.callId} - no active participants remaining`);
            await call.endCall();
        }

        await call.save();

        // Notify all participants
        const io = req.app.get('io');
        if (io) {
            const user = await User.findById(userId);
            // Extract chatId properly using helper function
            const chatId = extractChatId(call);
            if (!chatId) {
                console.error(`[GroupCall] Cannot extract chatId from call ${call.callId}`);
                return res.status(500).json({
                    success: false,
                    message: 'Invalid call data'
                });
            }
            const socketRoom = `group_call_${chatId}`;
            
            const leaveData = {
                callId: call.callId,
                chatId: chatId,
                userId: userId,
                username: user.username,
                reason: 'user_left',
                timestamp: new Date()
            };
            
            // CRITICAL FIX: Use chatId-based socket room instead of callId
            io.to(socketRoom).emit('group_call_participant_left', leaveData);
            console.log(`Sent participant_left to socket room: ${socketRoom} (callId: ${call.callId})`);
            
            // If call ended, emit call_ended event to all participants
            if (callEnded) {
                const callEndedData = {
                    callId: call.callId,
                    chatId: chatId,
                    endedBy: {
                        id: userId,
                        username: user.username
                    },
                    timestamp: new Date()
                };
                
                // Emit to chat-based socket room
                io.to(socketRoom).emit('call_ended', callEndedData);
                
                // Also emit to individual user rooms as fallback
                for (const participant of call.participants) {
                    let participantUserId;
                    if (participant.userId && typeof participant.userId === 'object' && participant.userId._id) {
                        participantUserId = participant.userId._id.toString();
                    } else {
                        participantUserId = participant.userId.toString();
                    }
                    
                    const userRoom = `user_${participantUserId}`;
                    io.to(userRoom).emit('call_ended', callEndedData);
                }
                
                // Emit to chat room
                const chatRoom = `chat_${chatId}`;
                io.to(chatRoom).emit('call_ended', callEndedData);
                
                console.log(`Sent call_ended notification for group call ${call.callId} to room ${socketRoom}`);
            }
        }

        res.json({
            success: true,
            message: 'Left group call successfully',
            data: call
        });

    } catch (error) {
        console.error('Leave group call error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error',
            error: error.message
        });
    }
};

// Get group call details
const getGroupCallDetails = async (req, res) => {
    try {
        const { callId } = req.params;
        const userId = req.user.id;

        const call = await Call.findOne({ callId: callId })
            .populate('chatId', 'name type participants')
            .populate('participants.userId', 'username avatar status')
            .populate('participantMedia.userId', 'username avatar');

        if (!call) {
            return res.status(404).json({
                success: false,
                message: 'Call not found'
            });
        }

        if (!call.isGroupCall) {
            return res.status(400).json({
                success: false,
                message: 'This is not a group call'
            });
        }

        // Check if user is a participant
        const participant = call.participants.find(p => p.userId.toString() === userId);
        if (!participant) {
            return res.status(403).json({
                success: false,
                message: 'You are not a participant in this call'
            });
        }

        res.json({
            success: true,
            data: call
        });

    } catch (error) {
        console.error('Get group call details error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error',
            error: error.message
        });
    }
};

// Get active group call for a chat
const getActiveGroupCall = async (req, res) => {
    try {
        const { chatId } = req.params;
        const userId = req.user.id;

        // Check if chat exists
        const chat = await Chat.findById(chatId);
        if (!chat) {
            return res.status(404).json({
                success: false,
                message: 'Chat not found'
            });
        }

        // Check if user is participant
        const isParticipant = chat.participants.some(p => p.user && p.user.toString() === userId);
        if (!isParticipant) {
            return res.status(403).json({
                success: false,
                message: 'You are not a participant in this chat'
            });
        }

        // Find active call
        const call = await Call.findActiveCallByChat(chatId)
            .populate('chatId', 'name type')
            .populate('participants.userId', 'username avatar status');

        if (!call) {
            return res.json({
                success: true,
                data: null,
                message: 'No active call'
            });
        }

        res.json({
            success: true,
            data: call
        });

    } catch (error) {
        console.error('Get active group call error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error',
            error: error.message
        });
    }
};

module.exports = {
    initiateGroupCall,
    joinGroupCall,
    updateParticipantMedia,
    leaveGroupCall,
    getGroupCallDetails,
    getActiveGroupCall
};

