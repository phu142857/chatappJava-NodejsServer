const mongoose = require('mongoose');

const postSchema = new mongoose.Schema({
  userId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: [true, 'User ID is required'],
    index: true
  },
  content: {
    type: String,
    required: function() {
      // Content is required if no images provided
      return this.images && this.images.length > 0 ? false : true;
    },
    maxlength: [5000, 'Post content cannot exceed 5000 characters'],
    trim: true
  },
  images: [{
    type: String, // URLs or IDs of uploaded images
    required: true
  }],
  privacySetting: {
    type: String,
    enum: ['public', 'friends', 'only_me'],
    default: 'public',
    required: true
  },
  location: {
    type: String,
    trim: true,
    default: null
  },
  tags: [{
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User'
  }],
  likes: [{
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: true
    },
    createdAt: {
      type: Date,
      default: Date.now
    }
  }],
  comments: [{
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: true
    },
    content: {
      type: String,
      required: true,
      maxlength: [1000, 'Comment cannot exceed 1000 characters'],
      trim: true
    },
    createdAt: {
      type: Date,
      default: Date.now
    },
    updatedAt: {
      type: Date,
      default: Date.now
    }
  }],
  shares: [{
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: true
    },
    createdAt: {
      type: Date,
      default: Date.now
    }
  }],
  isActive: {
    type: Boolean,
    default: true
  },
  isDeleted: {
    type: Boolean,
    default: false
  }
}, {
  timestamps: true,
  toJSON: { virtuals: true },
  toObject: { virtuals: true }
});

// Indexes for better performance
postSchema.index({ userId: 1, createdAt: -1 });
postSchema.index({ privacySetting: 1, createdAt: -1 });
postSchema.index({ tags: 1 });
postSchema.index({ isActive: 1, isDeleted: 1 });
postSchema.index({ 'likes.user': 1 });
postSchema.index({ 'comments.user': 1 });

// Virtual for likes count
postSchema.virtual('likesCount').get(function() {
  return this.likes ? this.likes.length : 0;
});

// Virtual for comments count
postSchema.virtual('commentsCount').get(function() {
  return this.comments ? this.comments.length : 0;
});

// Virtual for shares count
postSchema.virtual('sharesCount').get(function() {
  return this.shares ? this.shares.length : 0;
});

// Validate that post has either content or at least one image
postSchema.pre('validate', function(next) {
  if (!this.content && (!this.images || this.images.length === 0)) {
    return next(new Error('Post must contain at least text content OR at least 1 image'));
  }
  if (this.images && this.images.length > 5) {
    return next(new Error('Maximum 5 images allowed per post'));
  }
  next();
});

// Static method to get posts by user
postSchema.statics.getUserPosts = async function(userId, page = 1, limit = 20, viewerId = null) {
  const skip = (page - 1) * limit;
  const User = mongoose.model('User');
  
  const query = {
    userId: userId,
    isActive: true,
    isDeleted: false
  };

  // If viewer is not the post owner, respect privacy settings
  if (viewerId && viewerId.toString() !== userId.toString()) {
    const postOwner = await User.findById(userId).select('friends');
    const ownerFriends = postOwner ? (postOwner.friends || []) : [];
    const isViewerFriend = ownerFriends.some(friendId => friendId.toString() === viewerId.toString());
    
    // Only show public posts or friends-only posts if viewer is a friend
    if (isViewerFriend) {
      query.$or = [
        { privacySetting: 'public' },
        { privacySetting: 'friends' }
      ];
    } else {
      query.privacySetting = 'public';
    }
  }

  return await this.find(query)
    .populate('userId', 'username avatar profile.firstName profile.lastName')
    .populate('tags', 'username avatar profile.firstName profile.lastName')
    .populate('likes.user', 'username avatar')
    .populate('comments.user', 'username avatar profile.firstName profile.lastName')
    .populate('shares.user', 'username avatar')
    .sort({ createdAt: -1 })
    .skip(skip)
    .limit(limit);
};

// Static method to get feed posts (public and friends' posts)
postSchema.statics.getFeedPosts = async function(userId, page = 1, limit = 20) {
  const skip = (page - 1) * limit;
  const User = mongoose.model('User');
  
  const user = await User.findById(userId);
  const friendIds = user.friends || [];
  
  const query = {
    isActive: true,
    isDeleted: false,
    $or: [
      { privacySetting: 'public' },
      {
        privacySetting: 'friends',
        userId: { $in: [userId, ...friendIds] }
      },
      { userId: userId } // User's own posts regardless of privacy
    ]
  };

  return await this.find(query)
    .populate('userId', 'username avatar profile.firstName profile.lastName')
    .populate('tags', 'username avatar profile.firstName profile.lastName')
    .populate('likes.user', 'username avatar')
    .populate('comments.user', 'username avatar profile.firstName profile.lastName')
    .populate('shares.user', 'username avatar')
    .sort({ createdAt: -1 })
    .skip(skip)
    .limit(limit);
};


module.exports = mongoose.model('Post', postSchema);

