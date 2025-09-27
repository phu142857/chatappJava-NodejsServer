const mongoose = require('mongoose');

const messageSchema = new mongoose.Schema({
  content: {
    type: String,
    required: function() {
      return this.type === 'text' || this.type === 'system';
    },
    maxlength: [5000, 'Message cannot exceed 5000 characters']
  },
  type: {
    type: String,
    enum: ['text', 'image', 'file', 'video', 'audio', 'system'],
    default: 'text',
    required: true
  },
  sender: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: function() {
      return this.type !== 'system';
    }
  },
  chat: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Chat',
    required: true
  },
  replyTo: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Message'
  },
  attachments: [{
    filename: {
      type: String,
      required: true
    },
    originalName: {
      type: String,
      required: true
    },
    mimeType: {
      type: String,
      required: true
    },
    size: {
      type: Number,
      required: true
    },
    url: {
      type: String,
      required: true
    },
    thumbnail: {
      type: String // For images and videos
    }
  }],
  reactions: [{
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: true
    },
    emoji: {
      type: String,
      required: true,
      maxlength: 10
    },
    createdAt: {
      type: Date,
      default: Date.now
    }
  }],
  readBy: [{
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: true
    },
    readAt: {
      type: Date,
      default: Date.now
    }
  }],
  editHistory: [{
    content: {
      type: String,
      required: true
    },
    editedAt: {
      type: Date,
      default: Date.now
    }
  }],
  isEdited: {
    type: Boolean,
    default: false
  },
  isDeleted: {
    type: Boolean,
    default: false
  },
  deletedAt: {
    type: Date
  },
  deletedBy: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User'
  },
  metadata: {
    deliveredAt: {
      type: Date
    },
    failedDelivery: {
      type: Boolean,
      default: false
    },
    retryCount: {
      type: Number,
      default: 0
    }
  }
}, {
  timestamps: true,
  toJSON: { virtuals: true },
  toObject: { virtuals: true }
});

// Indexes for better performance
messageSchema.index({ chat: 1, createdAt: -1 });
messageSchema.index({ sender: 1 });
messageSchema.index({ type: 1 });
messageSchema.index({ isDeleted: 1 });
messageSchema.index({ 'readBy.user': 1 });

// Virtual for unread status (computed per user)
messageSchema.virtual('isRead').get(function() {
  // This will be computed in the application logic
  return false;
});

// Virtual for reaction summary
messageSchema.virtual('reactionSummary').get(function() {
  const summary = {};
  this.reactions.forEach(reaction => {
    if (summary[reaction.emoji]) {
      summary[reaction.emoji]++;
    } else {
      summary[reaction.emoji] = 1;
    }
  });
  return summary;
});

// Pre-save middleware
messageSchema.pre('save', function(next) {
  // Set delivered timestamp for new messages
  if (this.isNew && this.type !== 'system') {
    this.metadata.deliveredAt = new Date();
  }
  
  // Validate attachments based on message type
  if (this.type === 'text' && this.attachments.length > 0) {
    return next(new Error('Text messages cannot have attachments'));
  }
  
  if (['image', 'file', 'video', 'audio'].includes(this.type) && this.attachments.length === 0) {
    return next(new Error(`${this.type} messages must have attachments`));
  }
  
  next();
});

// Static method to get chat messages
messageSchema.statics.getChatMessages = function(chatId, page = 1, limit = 50) {
  const skip = (page - 1) * limit;
  
  return this.find({
    chat: chatId,
    isDeleted: false
  })
  .populate('sender', 'username avatar status')
  .populate('replyTo', 'content sender type')
  .populate('reactions.user', 'username')
  .sort({ createdAt: -1 })
  .skip(skip)
  .limit(limit);
};

// Static method to mark messages as read
messageSchema.statics.markAsRead = function(chatId, userId, messageIds = []) {
  const query = {
    chat: chatId,
    'readBy.user': { $ne: userId },
    isDeleted: false
  };
  
  if (messageIds.length > 0) {
    query._id = { $in: messageIds };
  }
  
  return this.updateMany(query, {
    $push: {
      readBy: {
        user: userId,
        readAt: new Date()
      }
    }
  });
};

// Method to add reaction
messageSchema.methods.addReaction = function(userId, emoji) {
  // Remove existing reaction from this user
  this.reactions = this.reactions.filter(
    r => r.user.toString() !== userId.toString()
  );
  
  // Add new reaction
  this.reactions.push({
    user: userId,
    emoji: emoji
  });
  
  return this.save();
};

// Method to remove reaction
messageSchema.methods.removeReaction = function(userId, emoji) {
  this.reactions = this.reactions.filter(
    r => !(r.user.toString() === userId.toString() && r.emoji === emoji)
  );
  
  return this.save();
};

// Method to edit message
messageSchema.methods.editMessage = function(newContent) {
  if (this.content !== newContent) {
    // Save to edit history
    this.editHistory.push({
      content: this.content
    });
    
    this.content = newContent;
    this.isEdited = true;
  }
  
  return this.save();
};

// Method to soft delete message
messageSchema.methods.softDelete = function(deletedBy) {
  this.isDeleted = true;
  this.deletedAt = new Date();
  this.deletedBy = deletedBy;
  
  return this.save();
};

module.exports = mongoose.model('Message', messageSchema);
