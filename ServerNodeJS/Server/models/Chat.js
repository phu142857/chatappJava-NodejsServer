const mongoose = require('mongoose');

const chatSchema = new mongoose.Schema({
  groupId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Group'
  },
  name: {
    type: String,
    trim: true,
    maxlength: [100, 'Chat name cannot exceed 100 characters']
  },
  description: {
    type: String,
    trim: true,
    maxlength: [500, 'Description cannot exceed 500 characters']
  },
  type: {
    type: String,
    enum: ['private', 'group'],
    required: true,
    default: 'private'
  },
  participants: [{
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: true
    },
    role: {
      type: String,
      enum: ['admin', 'moderator', 'member'],
      default: 'member'
    },
    joinedAt: {
      type: Date,
      default: Date.now
    },
    leftAt: {
      type: Date
    },
    isActive: {
      type: Boolean,
      default: true
    }
  }],
  avatar: {
    type: String,
    default: ''
  },
  settings: {
    isPublic: {
      type: Boolean,
      default: false
    },
    allowInvites: {
      type: Boolean,
      default: true
    },
    muteNotifications: {
      type: Boolean,
      default: false
    }
  },
  lastMessage: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Message'
  },
  lastActivity: {
    type: Date,
    default: Date.now
  },
  createdBy: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true
  },
  isActive: {
    type: Boolean,
    default: true
  }
}, {
  timestamps: true,
  toJSON: { virtuals: true },
  toObject: { virtuals: true }
});

// Indexes for better performance
chatSchema.index({ type: 1 });
chatSchema.index({ 'participants.user': 1 });
chatSchema.index({ lastActivity: -1 });
chatSchema.index({ createdBy: 1 });

// Virtual for active participants count
chatSchema.virtual('activeParticipantsCount').get(function() {
  return this.participants ? this.participants.filter(p => p.isActive).length : 0;
});

// Virtual for participant user IDs
chatSchema.virtual('participantIds').get(function() {
  return this.participants ? this.participants
    .filter(p => p.isActive)
    .map(p => p.user) : [];
});

// Pre-save middleware to validate participants
chatSchema.pre('save', function(next) {
  // Private chats must have exactly 2 participants
  if (this.type === 'private') {
    const activeParticipants = this.participants ? this.participants.filter(p => p.isActive) : [];
    if (activeParticipants.length !== 2) {
      return next(new Error('Private chat must have exactly 2 participants'));
    }
    // Private chats don't need names (use participants' names)
    this.name = undefined;
  }
  
  // Group chats must have at least 1 participant and a name
  if (this.type === 'group') {
    const activeParticipants = this.participants ? this.participants.filter(p => p.isActive) : [];
    if (activeParticipants.length < 1) {
      return next(new Error('Group chat must have at least 1 participant'));
    }
    if (!this.groupId && (!this.name || this.name.trim() === '')) {
      return next(new Error('Group chat must have a name'));
    }
  }
  
  next();
});

// Static method to find chat between two users
chatSchema.statics.findPrivateChat = function(userId1, userId2) {
  return this.findOne({
    type: 'private',
    'participants.user': { $all: [userId1, userId2] },
    'participants.isActive': true,
    isActive: true
  }).populate('participants.user', 'username email avatar status');
};

// Static method to find user's chats
chatSchema.statics.findUserChats = function(userId) {
  return this.find({
    'participants.user': userId,
    'participants.isActive': true,
    isActive: true
  })
  .populate('participants.user', 'username email avatar status')
  .populate('lastMessage')
  .sort({ lastActivity: -1 });
};

// Method to add participant
chatSchema.methods.addParticipant = function(userId, role = 'member') {
  if (!this.participants) {
    this.participants = [];
  }
  
  const existingParticipant = this.participants.find(
    p => p.user.toString() === userId.toString()
  );
  
  if (existingParticipant) {
    if (!existingParticipant.isActive) {
      existingParticipant.isActive = true;
      existingParticipant.joinedAt = new Date();
      existingParticipant.leftAt = undefined;
      // When reactivating, set role to 'member' if explicitly provided (for new additions)
      // This ensures users added as members always get 'member' role, even if they had a different role before
      if (role !== undefined) {
        existingParticipant.role = role;
      }
    }
  } else {
    this.participants.push({
      user: userId,
      role: role,
      joinedAt: new Date()
    });
  }
  
  this.lastActivity = new Date();
  return this.save();
};

// Method to remove participant
chatSchema.methods.removeParticipant = function(userId) {
  if (!this.participants) {
    this.participants = [];
    return this.save();
  }
  
  console.log('Chat.removeParticipant called for user:', userId, 'in chat:', this._id);
  console.log('Current participants before removal:', this.participants.map(p => ({
    user: p.user.toString(),
    isActive: p.isActive,
    role: p.role
  })));
  
  const participant = this.participants.find(
    p => p.user.toString() === userId.toString()
  );
  
  if (participant) {
    console.log('Found participant to remove:', {
      user: participant.user.toString(),
      isActive: participant.isActive,
      role: participant.role
    });
    participant.isActive = false;
    participant.leftAt = new Date();
    console.log('Set participant isActive to false');
  } else {
    console.log('Participant not found for user:', userId);
  }
  
  this.lastActivity = new Date();
  return this.save().then(savedChat => {
    console.log('Chat saved after participant removal. Participants after:', savedChat.participants.map(p => ({
      user: p.user.toString(),
      isActive: p.isActive,
      role: p.role
    })));
    return savedChat;
  });
};

// Method to update last activity
chatSchema.methods.updateLastActivity = function() {
  this.lastActivity = new Date();
  return this.save();
};

// Virtual for display name
chatSchema.virtual('displayName').get(function() {
  if (this.type === 'private') {
    // For private chats, return the other participant's name
    // This will be set by the controller
    return this.name || 'Private Chat';
  }
  return this.name || 'Group Chat';
});

// Method to get other participant in private chat
chatSchema.methods.getOtherParticipant = function(currentUserId) {
  if (this.type === 'private' && this.participants) {
    return this.participants.find(
      p => p.user.toString() !== currentUserId.toString()
    );
  }
  return null;
};

module.exports = mongoose.model('Chat', chatSchema);
