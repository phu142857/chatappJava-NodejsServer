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
  sharedPostId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Post',
    default: null
  },
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
    parentCommentId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'Post.comments',
      default: null
    },
    mediaUrl: {
      type: String,
      default: null
    },
    reactions: [{
      user: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        required: true
      },
      type: {
        type: String,
        enum: ['like', 'love', 'haha', 'wow', 'sad', 'angry'],
        required: true
      },
      createdAt: {
        type: Date,
        default: Date.now
      }
    }],
    createdAt: {
      type: Date,
      default: Date.now
    },
    updatedAt: {
      type: Date,
      default: Date.now
    },
    isEdited: {
      type: Boolean,
      default: false
    },
    isDeleted: {
      type: Boolean,
      default: false
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

// Full-text search index for content, location, and comments
postSchema.index({ 
  content: 'text', 
  location: 'text',
  'comments.content': 'text'
}, {
  weights: {
    content: 10,
    location: 5,
    'comments.content': 3
  },
  name: 'post_text_search_index'
});

// Virtual for likes count
postSchema.virtual('likesCount').get(function() {
  return this.likes ? this.likes.length : 0;
});

// Virtual for comments count (only top-level comments, not replies)
postSchema.virtual('commentsCount').get(function() {
  if (!this.comments) return 0;
  return this.comments.filter(c => !c.isDeleted && !c.parentCommentId).length;
});

// Virtual for total comments count (including replies)
postSchema.virtual('totalCommentsCount').get(function() {
  if (!this.comments) return 0;
  return this.comments.filter(c => !c.isDeleted).length;
});

// Method to get top-level comments with pagination
postSchema.methods.getTopLevelComments = function(page = 1, limit = 20, sortBy = 'recent') {
  const skip = (page - 1) * limit;
  let topLevelComments = this.comments.filter(c => !c.isDeleted && !c.parentCommentId);
  
  // Sort comments
  if (sortBy === 'recent') {
    topLevelComments.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  } else if (sortBy === 'relevant') {
    // Sort by reactions count + replies count (simple relevance)
    topLevelComments.sort((a, b) => {
      const aScore = (a.reactions?.length || 0) + (this.comments.filter(c => c.parentCommentId?.toString() === a._id.toString()).length || 0);
      const bScore = (b.reactions?.length || 0) + (this.comments.filter(c => c.parentCommentId?.toString() === b._id.toString()).length || 0);
      return bScore - aScore;
});
  }
  
  return topLevelComments.slice(skip, skip + limit);
};

// Method to get replies for a comment
postSchema.methods.getCommentReplies = function(commentId, limit = 10) {
  return this.comments
    .filter(c => !c.isDeleted && c.parentCommentId?.toString() === commentId.toString())
    .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt))
    .slice(0, limit);
};

// Virtual for shares count
postSchema.virtual('sharesCount').get(function() {
  return this.shares ? this.shares.length : 0;
});

// Validate that post has either content or at least one image
postSchema.pre('validate', function(next) {
  if (!this.content && (!this.images || this.images.length === 0)) {
    return next(new Error('Post must contain at least text content OR at least 1 image'));
  }
  if (this.images && this.images.length > 20) {
    return next(new Error('Maximum 20 images allowed per post'));
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
  const mongoose = require('mongoose');
  
  const user = await User.findById(userId);
  const friendIds = user.friends || [];
  const hiddenPostIds = user.hiddenPosts || [];
  
  // Convert hiddenPostIds to ObjectIds to ensure proper comparison
  const hiddenPostObjectIds = hiddenPostIds
    .filter(id => id != null)
    .map(id => {
      if (id instanceof mongoose.Types.ObjectId) {
        return id;
      }
      return new mongoose.Types.ObjectId(id);
    });
  
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
  
  // Only add $nin filter if there are hidden posts
  if (hiddenPostObjectIds.length > 0) {
    query._id = { $nin: hiddenPostObjectIds };
  }

  return await this.find(query)
    .populate('userId', 'username avatar profile.firstName profile.lastName')
    .populate('tags', 'username avatar profile.firstName profile.lastName')
    .populate('likes.user', 'username avatar')
    .populate('comments.user', 'username avatar profile.firstName profile.lastName')
    .populate('shares.user', 'username avatar')
    .populate({
      path: 'sharedPostId',
      select: 'userId content images createdAt',
      populate: {
        path: 'userId',
        select: 'username avatar profile.firstName profile.lastName'
      }
    })
    .sort({ createdAt: -1 })
    .skip(skip)
    .limit(limit);
};


module.exports = mongoose.model('Post', postSchema);

