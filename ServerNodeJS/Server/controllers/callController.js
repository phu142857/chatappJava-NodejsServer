const Call = require('../models/Call');
const Chat = require('../models/Chat');
const User = require('../models/User');
const { v4: uuidv4 } = require('uuid');

// Generate unique call ID
const generateCallId = () => {
    return `call_${Date.now()}_${uuidv4().substring(0, 8)}`;
};

// Generate WebRTC room ID
const generateRoomId = () => {
    return `room_${Date.now()}_${uuidv4().substring(0, 12)}`;
};

// Initiate a new call
const initiateCall = async (req, res) => {
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

        // Check if chat exists
        const chat = await Chat.findById(chatId);
        if (!chat) {
            return res.status(404).json({
                success: false,
                message: 'Chat not found'
            });
        }

        // Check if user is participant in chat
        const isParticipant = chat.participants.some(p => p.user && p.user.toString() === callerId);
        if (!isParticipant) {
            return res.status(403).json({
                success: false,
                message: 'You are not a participant in this chat'
            });
        }

        // Check if there's already an active call in this chat
        const existingCall = await Call.findActiveCallByChat(chatId);
        if (existingCall) {
            // Force end the existing call to allow new call
            existingCall.status = 'ended';
            existingCall.endedAt = new Date();
            existingCall.duration = Math.floor((new Date() - existingCall.startedAt) / 1000);
            await existingCall.save();
            
            console.log(`Force-ended old call ${existingCall.callId} to allow new call`);
        }

        // Get caller information
        const caller = await User.findById(callerId);
        if (!caller) {
            return res.status(404).json({
                success: false,
                message: 'Caller not found'
            });
        }

        // Create new call
        const callId = generateCallId();
        const roomId = generateRoomId();

        // Build ICE servers (include TURN if configured)
        const iceServers = [
            { urls: "turn:103.75.183.125:3478", username: "phu142", credential: "phu142" },
            { urls: "turn:103.75.183.125:5349", username: "phu142", credential: "phu142" },
            { urls: "turn:192.168.2.36:3478", username: "phu142", credential: "phu142" },
            { urls: 'stun:stun.l.google.com:19302' },
            { urls: 'stun:stun1.l.google.com:19302' },
            { urls: 'stun:stun2.l.google.com:19302' }
        ];
        if (process.env.TURN_URL && process.env.TURN_USERNAME && process.env.TURN_CREDENTIAL) {
            iceServers.push({
                urls: process.env.TURN_URL,
                username: process.env.TURN_USERNAME,
                credential: process.env.TURN_CREDENTIAL
            });
        }

        const call = new Call({
            callId: callId,
            type: type,
            chatId: chatId,
            isGroupCall: chat.type === 'group',
            status: 'initiated',
            webrtcData: {
                roomId: roomId,
                iceServers: iceServers
            },
            participants: [{
                userId: callerId,
                username: caller.username,
                avatar: caller.avatar || '',
                status: 'connected',
                isCaller: true,
                joinedAt: new Date()
            }]
        });

        // Add other participants as invited
        for (const participant of chat.participants) {
            if (participant.user && participant.user.toString() !== callerId) {
                const user = await User.findById(participant.user);
                if (user) {
                    call.participants.push({
                        userId: participant.user,
                        username: user.username,
                        avatar: user.avatar || '',
                        status: 'invited',
                        isCaller: false
                    });
                }
            }
        }

        // Add initial log
        call.logs.push({
            userId: callerId,
            action: 'call_initiated',
            details: `Initiated ${type} call in ${chat.type} chat`
        });

        await call.save();

        // Populate call data for response
        await call.populate([
            { path: 'chatId', select: 'name type participants' },
            { path: 'participants.userId', select: 'username avatar status' }
        ]);

        // Send incoming call notification to other participants via Socket.io
        const io = req.app.get('io');
        if (io) {
            // Convert callerId to string for comparison
            const callerIdStr = callerId.toString();
            
            for (const participant of call.participants) {
                // Handle both ObjectId and populated object cases
                let participantUserId;
                if (participant.userId && typeof participant.userId === 'object' && participant.userId._id) {
                    // Populated object case
                    participantUserId = participant.userId._id.toString();
                } else {
                    // ObjectId case
                    participantUserId = participant.userId.toString();
                }
                
                // Only send to participants who are NOT the caller
                if (participantUserId !== callerIdStr) {
                    const userRoom = `user_${participantUserId}`;
                    const callData = {
                        callId: call.callId,
                        caller: {
                            id: caller._id,
                            username: caller.username,
                            avatar: caller.avatar
                        },
                        chatId: chatId,
                        callType: type,
                        timestamp: new Date()
                    };
                    
                    // Only emit to specific user room (removed fallback emit to all)
                    io.to(userRoom).emit('incoming_call', callData);
                    
                    console.log(`Sent incoming call notification to user ${participantUserId} in room ${userRoom} (caller: ${callerIdStr})`);
                } else {
                    console.log(`Skipped sending notification to caller: ${participantUserId}`);
                }
            }
        }

        res.status(201).json({
            success: true,
            message: 'Call initiated successfully',
            callId: call.callId,
            data: call
        });

    } catch (error) {
        console.error('Initiate call error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error',
            error: error.message
        });
    }
};

// Join an existing call
const joinCall = async (req, res) => {
    try {
        const { callId } = req.params;
        const userId = req.user.id;

        // Find the call
        const call = await Call.findOne({ callId: callId });
        if (!call) {
            return res.status(404).json({
                success: false,
                message: 'Call not found'
            });
        }

        // Check if call is still active
        if (!['initiated', 'ringing', 'active'].includes(call.status)) {
            return res.status(400).json({
                success: false,
                message: 'Call is no longer active'
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

        // Idempotency: if already connected, return current state without emitting again
        if (participant.status === 'connected') {
            // Populate call data for response
            await call.populate([
                { path: 'chatId', select: 'name type' },
                { path: 'participants.userId', select: 'username avatar status' }
            ]);

            return res.json({
                success: true,
                message: 'Already joined',
                data: call
            });
        }

        // Update participant status
        participant.status = 'connected';
        participant.joinedAt = new Date();

        // Update call status if needed
        if (call.status === 'initiated' || call.status === 'ringing') {
            call.status = 'active';
        }

        // Add log
        call.logs.push({
            userId: userId,
            action: 'user_joined',
            details: 'User joined the call'
        });

        await call.save();

        // Populate call data
        await call.populate([
            { path: 'chatId', select: 'name type' },
            { path: 'participants.userId', select: 'username avatar status' }
        ]);

        // Send call_accepted notification to other participants via Socket.io (idempotent by status guard above)
        const io = req.app.get('io');
        if (io) {
            const userIdStr = userId.toString();
            
            for (const participant of call.participants) {
                // Handle both ObjectId and populated object cases
                let participantUserId;
                if (participant.userId && typeof participant.userId === 'object' && participant.userId._id) {
                    participantUserId = participant.userId._id.toString();
                } else {
                    participantUserId = participant.userId.toString();
                }
                
                // Send to other participants (not the one who just joined)
                if (participantUserId !== userIdStr) {
                    const userRoom = `user_${participantUserId}`;
                    const callData = {
                        callId: call.callId,
                        acceptedBy: {
                            id: userId,
                            username: participant.username
                        },
                        timestamp: new Date()
                    };
                    
                    io.to(userRoom).emit('call_accepted', callData);
                    console.log(`Sent call_accepted notification to user ${participantUserId} in room ${userRoom}`);
                }
            }
        }

        res.json({
            success: true,
            message: 'Joined call successfully',
            data: call
        });

    } catch (error) {
        console.error('Join call error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error',
            error: error.message
        });
    }
};

// Decline a call
const declineCall = async (req, res) => {
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

        // Idempotency: if already declined, return current state without emitting again
        if (participant.status === 'declined') {
            return res.json({
                success: true,
                message: 'Already declined',
                data: call
            });
        }

        // Update participant status
        participant.status = 'declined';

        // Add log
        call.logs.push({
            userId: userId,
            action: 'call_declined',
            details: 'User declined the call'
        });

        // Check if all participants declined
        const allDeclined = call.participants.every(p => p.status === 'declined' || p.status === 'left');
        if (allDeclined) {
            call.status = 'declined';
            call.endedAt = new Date();
        }

        await call.save();

        // Send call_declined notification to other participants via Socket.io
        const io = req.app.get('io');
        if (io) {
            const userIdStr = userId.toString();
            
            for (const participant of call.participants) {
                // Handle both ObjectId and populated object cases
                let participantUserId;
                if (participant.userId && typeof participant.userId === 'object' && participant.userId._id) {
                    participantUserId = participant.userId._id.toString();
                } else {
                    participantUserId = participant.userId.toString();
                }
                
                // Send to other participants (not the one who declined)
                if (participantUserId !== userIdStr) {
                    const userRoom = `user_${participantUserId}`;
                    const callData = {
                        callId: call.callId,
                        declinedBy: {
                            id: userId,
                            username: participant.username
                        },
                        timestamp: new Date()
                    };
                    
                    io.to(userRoom).emit('call_declined', callData);
                    console.log(`Sent call_declined notification to user ${participantUserId} in room ${userRoom}`);
                }
            }
        }

        res.json({
            success: true,
            message: 'Call declined successfully',
            data: call
        });

    } catch (error) {
        console.error('Decline call error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error',
            error: error.message
        });
    }
};

// Leave a call
const leaveCall = async (req, res) => {
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

        // Add log
        call.logs.push({
            userId: userId,
            action: 'user_left',
            details: 'User left the call'
        });

        // Check if call should end
        const activeParticipants = call.getActiveParticipants();
        if (activeParticipants.length <= 1) {
            await call.endCall();
        }

        await call.save();

        res.json({
            success: true,
            message: 'Left call successfully',
            data: call
        });

    } catch (error) {
        console.error('Leave call error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error',
            error: error.message
        });
    }
};

// End a call
const endCall = async (req, res) => {
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

        // Check if user is the caller or has permission to end call
        const participant = call.participants.find(p => p.userId.toString() === userId);
        if (!participant) {
            return res.status(403).json({
                success: false,
                message: 'You are not a participant in this call'
            });
        }

        // Idempotency: if already ended, return current state without emitting again
        if (call.status === 'ended') {
            return res.json({
                success: true,
                message: 'Call already ended',
                data: call
            });
        }

        // End the call
        await call.endCall();

        // Add log
        call.logs.push({
            userId: userId,
            action: 'call_ended',
            details: 'Call ended by user'
        });

        await call.save();

        // Send call_ended notification to all participants via Socket.io (idempotent by status guard above)
        const io = req.app.get('io');
        if (io) {
            const callData = {
                callId: call.callId,
                endedBy: {
                    id: userId,
                    username: participant.username
                },
                timestamp: new Date()
            };
            
            // Emit to call room
            io.to(`call_${call.callId}`).emit('call_ended', callData);
            
            // Also emit to individual user rooms as fallback
            for (const participant of call.participants) {
                let participantUserId;
                if (participant.userId && typeof participant.userId === 'object' && participant.userId._id) {
                    participantUserId = participant.userId._id.toString();
                } else {
                    participantUserId = participant.userId.toString();
                }
                
                const userRoom = `user_${participantUserId}`;
                io.to(userRoom).emit('call_ended', callData);
            }
            
            console.log(`Sent call_ended notification for call ${call.callId}`);
        }

        res.json({
            success: true,
            message: 'Call ended successfully',
            data: call
        });

    } catch (error) {
        console.error('End call error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error',
            error: error.message
        });
    }
};

// Get call details
const getCallDetails = async (req, res) => {
    try {
        const { callId } = req.params;
        const userId = req.user.id;

        const call = await Call.findOne({ callId: callId })
            .populate('chatId', 'name type participants')
            .populate('participants.userId', 'username avatar status');

        if (!call) {
            return res.status(404).json({
                success: false,
                message: 'Call not found'
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
        console.error('Get call details error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error',
            error: error.message
        });
    }
};

// Get call history
const getCallHistory = async (req, res) => {
    try {
        const userId = req.user.id;
        const { limit = 50, page = 1 } = req.query;

        const calls = await Call.findCallsByUser(userId)
            .sort({ startedAt: -1 })
            .limit(parseInt(limit))
            .skip((parseInt(page) - 1) * parseInt(limit))
            .populate('chatId', 'name type')
            .populate('participants.userId', 'username avatar');

        const totalCalls = await Call.countDocuments({ 'participants.userId': userId });

        res.json({
            success: true,
            data: {
                calls: calls,
                pagination: {
                    currentPage: parseInt(page),
                    totalPages: Math.ceil(totalCalls / parseInt(limit)),
                    totalCalls: totalCalls,
                    hasNext: (parseInt(page) * parseInt(limit)) < totalCalls,
                    hasPrev: parseInt(page) > 1
                }
            }
        });

    } catch (error) {
        console.error('Get call history error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error',
            error: error.message
        });
    }
};

// Get active calls
const getActiveCalls = async (req, res) => {
    try {
        const userId = req.user.id;

        const activeCalls = await Call.findActiveCalls()
            .populate('chatId', 'name type')
            .populate('participants.userId', 'username avatar status');

        // Filter calls where user is a participant
        const userActiveCalls = activeCalls.filter(call => 
            call.participants.some(p => p.userId.toString() === userId)
        );

        res.json({
            success: true,
            data: userActiveCalls
        });

    } catch (error) {
        console.error('Get active calls error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error',
            error: error.message
        });
    }
};

// Update call settings
const updateCallSettings = async (req, res) => {
    try {
        const { callId } = req.params;
        const userId = req.user.id;
        const { muteAudio, muteVideo, screenShare, recording } = req.body;

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

        // Update settings
        if (muteAudio !== undefined) {
            call.settings.muteAudio = muteAudio;
            call.logs.push({
                userId: userId,
                action: 'mute_toggled',
                details: `Audio ${muteAudio ? 'muted' : 'unmuted'}`
            });
        }

        if (muteVideo !== undefined) {
            call.settings.muteVideo = muteVideo;
            call.logs.push({
                userId: userId,
                action: 'video_toggled',
                details: `Video ${muteVideo ? 'muted' : 'unmuted'}`
            });
        }

        if (screenShare !== undefined) {
            call.settings.screenShare = screenShare;
            call.logs.push({
                userId: userId,
                action: 'screen_shared',
                details: `Screen share ${screenShare ? 'started' : 'stopped'}`
            });
        }

        if (recording !== undefined) {
            call.settings.recording = recording;
            call.recording.isRecording = recording;
            if (recording) {
                call.recording.recordingStartedAt = new Date();
            } else {
                call.recording.recordingEndedAt = new Date();
            }
            call.logs.push({
                userId: userId,
                action: recording ? 'recording_started' : 'recording_stopped',
                details: `Recording ${recording ? 'started' : 'stopped'}`
            });
        }

        await call.save();

        res.json({
            success: true,
            message: 'Call settings updated successfully',
            data: call
        });

    } catch (error) {
        console.error('Update call settings error:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error',
            error: error.message
        });
    }
};

// @desc    Get all calls (admin only)
// @route   GET /api/calls/admin
// @access  Private (Admin)
const getAllCalls = async (req, res) => {
    try {
        const { page = 1, limit = 50, status, type, dateFrom, dateTo } = req.query;
        const skip = (page - 1) * limit;
        
        let query = {};
        
        // Add filters
        if (status) query.status = status;
        if (type) query.type = type;
        if (dateFrom || dateTo) {
            query.startedAt = {};
            if (dateFrom) query.startedAt.$gte = new Date(dateFrom);
            if (dateTo) query.startedAt.$lte = new Date(dateTo);
        }

        const calls = await Call.find(query)
            .populate('participants.userId', 'username email avatar')
            .sort({ startedAt: -1 })
            .skip(skip)
            .limit(parseInt(limit));

        const total = await Call.countDocuments(query);

        // Format participants data
        const formattedCalls = calls.map(call => {
            const callObj = call.toJSON();
            return {
                ...callObj,
                participants: callObj.participants.map(p => ({
                    userId: p.userId ? (p.userId._id || p.userId) : null,
                    username: p.userId ? (p.userId.username || 'Unknown') : 'Unknown',
                    avatar: p.userId ? (p.userId.avatar || '') : '',
                    joinedAt: p.joinedAt,
                    leftAt: p.leftAt,
                    status: p.status
                }))
            };
        });

        res.json({
            success: true,
            data: {
                calls: formattedCalls,
                pagination: {
                    page: parseInt(page),
                    limit: parseInt(limit),
                    total,
                    pages: Math.ceil(total / limit)
                }
            }
        });

    } catch (error) {
        console.error('Get all calls error:', error);
        res.status(500).json({
            success: false,
            message: 'Server error while fetching calls'
        });
    }
};

module.exports = {
    initiateCall,
    joinCall,
    declineCall,
    leaveCall,
    endCall,
    getCallDetails,
    getCallHistory,
    getActiveCalls,
    updateCallSettings,
    getAllCalls
};
