const mongoose = require('mongoose');

const groupSchema = new mongoose.Schema({
  name: {
    type: String,
    required: [true, 'Group name is required'],
    trim: true,
    minlength: [3, 'Group name must be at least 3 characters'],
    maxlength: [50, 'Group name cannot exceed 50 characters']
  },
  description: {
    type: String,
    trim: true,
    maxlength: [500, 'Description cannot exceed 500 characters']
  },
  avatar: {
    type: String,
    default: ''
  },
  status: {
    type: String,
    enum: ['active', 'inactive', 'archived'],
    default: 'active'
  },
  lastActivity: {
    type: Date,
    default: Date.now
  },
  isActive: {
    type: Boolean,
    default: true
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
  members: [{
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
  createdBy: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true
  }
}, {
  timestamps: true,
  toJSON: { virtuals: true },
  toObject: { virtuals: true }
});

// Indexes for better performance
groupSchema.index({ name: 1 });
groupSchema.index({ status: 1 });
groupSchema.index({ 'members.user': 1 });
groupSchema.index({ createdBy: 1 });
groupSchema.index({ lastActivity: -1 });

// Virtual for active members count
groupSchema.virtual('activeMembersCount').get(function() {
  return this.members.filter(m => m.isActive).length;
});

// Virtual for member user IDs
groupSchema.virtual('memberIds').get(function() {
  return this.members
    .filter(m => m.isActive)
    .map(m => m.user);
});

// Update last activity
groupSchema.methods.updateLastActivity = function() {
  this.lastActivity = new Date();
  return this.save();
};

// Add member method
groupSchema.methods.addMember = async function(userId, role = 'member') {
  const existingMember = this.members.find(
    m => m.user.toString() === userId.toString()
  );
  
  if (existingMember) {
    if (!existingMember.isActive) {
      existingMember.isActive = true;
      existingMember.joinedAt = new Date();
      existingMember.leftAt = undefined;
    }
  } else {
    this.members.push({
      user: userId,
      role: role,
      joinedAt: new Date()
    });
  }
  
  this.lastActivity = new Date();
  return this.save();
};

// Remove member method
groupSchema.methods.removeMember = function(userId) {
  console.log('Group.removeMember called for user:', userId, 'in group:', this._id);
  console.log('Current members before removal:', this.members.map(m => ({
    user: m.user.toString(),
    isActive: m.isActive,
    role: m.role
  })));
  
  const member = this.members.find(
    m => m.user.toString() === userId.toString()
  );
  
  if (member) {
    console.log('Found member to remove:', {
      user: member.user.toString(),
      isActive: member.isActive,
      role: member.role
    });
    member.isActive = false;
    member.leftAt = new Date();
    console.log('Set member isActive to false');
  } else {
    console.log('Member not found for user:', userId);
  }
  
  this.lastActivity = new Date();
  return this.save().then(savedGroup => {
    console.log('Group saved after member removal. Members after:', savedGroup.members.map(m => ({
      user: m.user.toString(),
      isActive: m.isActive,
      role: m.role
    })));
    return savedGroup;
  });
};

// Check if user is member
groupSchema.methods.isMember = function(userId) {
  return this.members.some(m => m.user.equals(userId) && m.isActive);
};

// Get membership status with user
groupSchema.methods.getMembershipStatus = function(userId) {
  const member = this.members.find(m => m.user.equals(userId));
  return {
    isMember: member ? member.isActive : false,
    role: member ? member.role : null,
    joinedAt: member ? member.joinedAt : null,
    status: member ? (member.isActive ? 'active' : 'inactive') : 'not_member'
  };
};

// Check if user has permission (admin or moderator)
groupSchema.methods.hasPermission = function(userId) {
  const member = this.members.find(m => m.user.equals(userId) && m.isActive);
  return member && ['admin', 'moderator'].includes(member.role);
};

// Check if user is admin
groupSchema.methods.isAdmin = function(userId) {
  const member = this.members.find(m => m.user.equals(userId) && m.isActive);
  return member && member.role === 'admin';
};

// Static method to find user's groups
groupSchema.statics.findUserGroups = function(userId) {
  return this.find({
    'members.user': userId,
    'members.isActive': true,
    isActive: true
  })
  .populate('members.user', 'username email avatar status')
  .sort({ lastActivity: -1 });
};

// Static method to find public groups
groupSchema.statics.findPublicGroups = function() {
  return this.find({
    'settings.isPublic': true,
    isActive: true,
    status: 'active'
  })
  .populate('members.user', 'username email avatar status')
  .sort({ lastActivity: -1 });
};

// Pre-save middleware to validate members
groupSchema.pre('save', function(next) {
  // Group must have at least 1 member (creator)
  const activeMembers = this.members.filter(m => m.isActive);
  if (activeMembers.length < 1) {
    return next(new Error('Group must have at least 1 member'));
  }
  
  // Ensure creator is admin
  const creatorMember = this.members.find(m => m.user.equals(this.createdBy));
  if (creatorMember) {
    creatorMember.role = 'admin';
  }
  
  next();
});

module.exports = mongoose.model('Group', groupSchema);
