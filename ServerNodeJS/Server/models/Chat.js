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
  },
  lastSummarizedTimestamp: {
    type: Date,
    default: null
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

// Pre-save middleware to validate participants and clean up duplicates
chatSchema.pre('save', function(next) {
  // Clean up duplicate participants (same user ID appears multiple times)
  if (this.participants && this.participants.length > 0) {
    const seenUserIds = new Map();
    const cleanedParticipants = [];
    
    for (const participant of this.participants) {
      if (!participant.user) continue;
      
      const userId = participant.user.toString();
      if (seenUserIds.has(userId)) {
        // Duplicate found - keep the first active one, or first one if none are active
        const existing = seenUserIds.get(userId);
        if (participant.isActive && !existing.isActive) {
          // Replace inactive with active
          const index = cleanedParticipants.indexOf(existing);
          if (index >= 0) {
            cleanedParticipants[index] = participant;
            seenUserIds.set(userId, participant);
          }
        }
        // Otherwise keep the existing one, skip this duplicate
        console.log(`Removed duplicate participant for user ${userId}`);
      } else {
        // First occurrence of this user
        cleanedParticipants.push(participant);
        seenUserIds.set(userId, participant);
      }
    }
    
    if (cleanedParticipants.length !== this.participants.length) {
      console.log(`Cleaned up ${this.participants.length - cleanedParticipants.length} duplicate participants`);
      this.participants = cleanedParticipants;
    }
  }
  
  // Private chats must have at least 1 active participant (can have 1 when one user deleted)
  // When creating, must have exactly 2 participants
  if (this.type === 'private') {
    const activeParticipants = this.participants ? this.participants.filter(p => p.isActive) : [];
    // Check if this is a deletion scenario (someone has leftAt set)
    const hasLeftAt = this.participants && this.participants.some(p => {
      return !p.isActive && p.leftAt;
    });
    
    // Allow 0 active participants if someone has left (deletion scenario)
    // This allows the chat to be saved when a user deletes it
    // Otherwise require 1-2 active participants
    if (!hasLeftAt && (activeParticipants.length < 1 || activeParticipants.length > 2)) {
      return next(new Error('Private chat must have 1-2 active participants'));
    }
    // Private chats don't need names (use participants' names)
    this.name = undefined;
  }
  
  // Group chats must have at least 1 participant and a name
  if (this.type === 'group') {
    const activeParticipants = this.participants ? this.participants.filter(p => p.isActive) : [];
    // Allow 0 active participants if someone has left (similar to private chat)
    const hasLeftAt = this.participants && this.participants.some(p => {
      return !p.isActive && p.leftAt;
    });
    
    if (!hasLeftAt && activeParticipants.length < 1) {
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
  
  // First, clean up any duplicate participants for this user
  const userParticipants = this.participants.filter(
    p => p.user && p.user.toString() === userId.toString()
  );
  
  if (userParticipants.length > 1) {
    console.log(`Found ${userParticipants.length} duplicate participants for user ${userId} in addParticipant, cleaning up...`);
    // Keep the first active participant, or first one if none are active
    const toKeep = userParticipants.find(p => p.isActive) || userParticipants[0];
    // Remove all duplicates except the one to keep
    this.participants = this.participants.filter((p) => {
      const pUserId = p.user && p.user.toString();
      if (pUserId === userId.toString()) {
        // Keep only the one we want to keep
        return p === toKeep;
      }
      return true;
    });
    console.log('Cleaned up duplicate participants in addParticipant');
  }
  
  const existingParticipant = this.participants.find(
    p => p.user && p.user.toString() === userId.toString()
  );
  
  if (existingParticipant) {
    if (!existingParticipant.isActive) {
      existingParticipant.isActive = true;
      existingParticipant.joinedAt = new Date();
      // If leftAt is being reset (set to undefined before calling addParticipant),
      // it means user explicitly opened/created the chat - show all messages
      // Otherwise, keep leftAt to only show new messages after automatic reactivation
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
chatSchema.methods.removeParticipant = async function(userId) {
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
  
  // First, clean up any duplicate participants for this user
  // Keep only the first active one, or the first one if none are active
  const userParticipants = this.participants.filter(
    p => p.user && p.user.toString() === userId.toString()
  );
  
  if (userParticipants.length > 1) {
    console.log(`Found ${userParticipants.length} duplicate participants for user ${userId}, cleaning up...`);
    // Keep the first active participant, or first one if none are active
    const toKeep = userParticipants.find(p => p.isActive) || userParticipants[0];
    // Remove all duplicates except the one to keep
    this.participants = this.participants.filter((p, index) => {
      const pUserId = p.user && p.user.toString();
      if (pUserId === userId.toString()) {
        // Keep only the one we want to keep
        return p === toKeep;
      }
      return true;
    });
    console.log('Cleaned up duplicate participants');
  }
  
  const participant = this.participants.find(
    p => p.user && p.user.toString() === userId.toString()
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
    
    // Mark all current messages as read for this user when they delete the chat
    // This ensures they only see new messages when they're reactivated
    const Message = mongoose.model('Message');
    await Message.updateMany(
      {
        chat: this._id,
        'readBy.user': { $ne: userId },
        isDeleted: false
      },
      {
        $push: {
          readBy: {
            user: userId,
            readAt: new Date()
          }
        }
      }
    );
    console.log('Marked all current messages as read for user:', userId);
  } else {
    console.log('Participant not found for user:', userId);
  }
  
  this.lastActivity = new Date();
  const savedChat = await this.save();
  console.log('Chat saved after participant removal. Participants after:', savedChat.participants.map(p => ({
    user: p.user.toString(),
    isActive: p.isActive,
    role: p.role
  })));
  return savedChat;
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
