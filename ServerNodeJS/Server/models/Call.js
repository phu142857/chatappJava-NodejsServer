const mongoose = require('mongoose');

const callSchema = new mongoose.Schema({
    // Call identification
    callId: {
        type: String,
        required: true,
        unique: true,
        index: true
    },
    
    // Call type: 'audio' or 'video'
    type: {
        type: String,
        required: true,
        enum: ['audio', 'video'],
        default: 'video'
    },
    
    // Chat information
    chatId: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'Chat',
        required: true,
        index: true
    },
    
    // Call participants
    participants: [{
        userId: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User',
            required: true
        },
        username: {
            type: String,
            required: true
        },
        avatar: {
            type: String,
            default: ''
        },
        status: {
            type: String,
            enum: ['invited', 'ringing', 'connected', 'declined', 'missed', 'left'],
            default: 'invited'
        },
        joinedAt: {
            type: Date,
            default: null
        },
        leftAt: {
            type: Date,
            default: null
        },
        isCaller: {
            type: Boolean,
            default: false
        }
    }],
    
    // Call status
    status: {
        type: String,
        enum: ['initiated', 'ringing', 'active', 'ended', 'declined', 'missed', 'cancelled'],
        default: 'initiated',
        index: true
    },
    
    // Call timing
    startedAt: {
        type: Date,
        default: Date.now,
        index: true
    },
    
    endedAt: {
        type: Date,
        default: null
    },
    
    duration: {
        type: Number, // in seconds
        default: 0
    },
    
    // Call metadata
    isGroupCall: {
        type: Boolean,
        default: false
    },
    
    // WebRTC information
    webrtcData: {
        roomId: {
            type: String,
            required: true
        },
        iceServers: [{
            urls: String,
            username: String,
            credential: String
        }],
        sdpOffer: String,
        sdpAnswer: String
    },
    
    // Call quality metrics
    quality: {
        averageLatency: Number,
        packetLoss: Number,
        jitter: Number,
        bitrate: Number
    },
    
    // Call settings
    settings: {
        muteAudio: {
            type: Boolean,
            default: false
        },
        muteVideo: {
            type: Boolean,
            default: false
        },
        screenShare: {
            type: Boolean,
            default: false
        },
        recording: {
            type: Boolean,
            default: false
        }
    },
    
    // Call history and logs
    logs: [{
        timestamp: {
            type: Date,
            default: Date.now
        },
        userId: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User'
        },
        action: {
            type: String,
            enum: ['call_initiated', 'call_answered', 'call_declined', 'call_ended', 'user_joined', 'user_left', 'mute_toggled', 'video_toggled', 'screen_shared', 'recording_started', 'recording_stopped']
        },
        details: String
    }],
    
    // Call recording information
    recording: {
        isRecording: {
            type: Boolean,
            default: false
        },
        recordingUrl: String,
        recordingStartedAt: Date,
        recordingEndedAt: Date
    }
}, {
    timestamps: true, // Adds createdAt and updatedAt
    collection: 'calls'
});

// Indexes for better performance
callSchema.index({ chatId: 1, status: 1 });
callSchema.index({ 'participants.userId': 1, status: 1 });
callSchema.index({ startedAt: -1 });
callSchema.index({ callId: 1 }, { unique: true });

// Virtual for call duration calculation
callSchema.virtual('calculatedDuration').get(function() {
    if (this.endedAt && this.startedAt) {
        return Math.floor((this.endedAt - this.startedAt) / 1000);
    }
    return 0;
});

// Pre-save middleware to calculate duration
callSchema.pre('save', function(next) {
    if (this.endedAt && this.startedAt && this.status === 'ended') {
        this.duration = Math.floor((this.endedAt - this.startedAt) / 1000);
    }
    next();
});

// Instance methods
callSchema.methods.addParticipant = function(userId, username, avatar, isCaller = false) {
    const participant = {
        userId: userId,
        username: username,
        avatar: avatar || '',
        status: 'invited',
        isCaller: isCaller
    };
    
    // Check if participant already exists
    const existingParticipant = this.participants.find(p => p.userId.toString() === userId.toString());
    if (!existingParticipant) {
        this.participants.push(participant);
    }
    
    return this.save();
};

callSchema.methods.updateParticipantStatus = function(userId, status) {
    const participant = this.participants.find(p => p.userId.toString() === userId.toString());
    if (participant) {
        participant.status = status;
        if (status === 'connected') {
            participant.joinedAt = new Date();
        } else if (status === 'left') {
            participant.leftAt = new Date();
        }
    }
    return this.save();
};

callSchema.methods.addLog = function(userId, action, details = '') {
    this.logs.push({
        userId: userId,
        action: action,
        details: details
    });
    return this.save();
};

callSchema.methods.endCall = function() {
    this.status = 'ended';
    this.endedAt = new Date();
    this.duration = this.calculatedDuration;
    
    // Update all participants status to 'left'
    this.participants.forEach(participant => {
        if (participant.status === 'connected') {
            participant.status = 'left';
            participant.leftAt = new Date();
        }
    });
    
    return this.save();
};

callSchema.methods.getActiveParticipants = function() {
    return this.participants.filter(p => p.status === 'connected');
};

callSchema.methods.getCaller = function() {
    return this.participants.find(p => p.isCaller === true);
};

// Static methods
callSchema.statics.findActiveCalls = function() {
    return this.find({ status: { $in: ['initiated', 'ringing', 'active'] } });
};

callSchema.statics.findCallsByUser = function(userId) {
    return this.find({ 'participants.userId': userId });
};

callSchema.statics.findActiveCallByChat = function(chatId) {
    return this.findOne({ 
        chatId: chatId, 
        status: { $in: ['initiated', 'ringing', 'active'] } 
    });
};

callSchema.statics.findCallHistory = function(userId, limit = 50) {
    return this.find({ 'participants.userId': userId })
        .sort({ startedAt: -1 })
        .limit(limit)
        .populate('chatId', 'name type')
        .populate('participants.userId', 'username avatar');
};

// Transform JSON output
callSchema.set('toJSON', {
    virtuals: true,
    transform: function(doc, ret) {
        delete ret._id;
        delete ret.__v;
        return ret;
    }
});

module.exports = mongoose.model('Call', callSchema);
