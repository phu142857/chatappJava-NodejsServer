const Post = require('../models/Post');
const User = require('../models/User');
const { validationResult } = require('express-validator');

// @desc    Create a new post
// @route   POST /api/posts
// @access  Private
const createPost = async (req, res) => {
  try {
    // Check for validation errors
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({
        success: false,
        message: 'Validation failed',
        errors: errors.array()
      });
    }

    const { content, images = [], privacySetting = 'public', location = null, tags = [], sharedPostId = null } = req.body;
    const userId = req.user.id;

    // If sharing a post, validate that the original post exists
    if (sharedPostId) {
      const originalPost = await Post.findById(sharedPostId);
      if (!originalPost || !originalPost.isActive || originalPost.isDeleted) {
        return res.status(404).json({
          success: false,
          message: 'Original post not found'
        });
      }
      
      // Check if user already shared this post (prevent duplicate shares)
      const existingShare = originalPost.shares.find(share => share.user.toString() === userId);
      if (!existingShare) {
        // Increment share count on original post
        originalPost.shares.push({ user: userId });
        await originalPost.save();
      }
    }

    // Validate business rules (relaxed for shared posts - they can have no content)
    if (!sharedPostId && !content && (!images || images.length === 0)) {
      return res.status(400).json({
        success: false,
        message: 'Post must contain at least text content OR at least 1 image'
      });
    }
    
    // For shared posts, allow empty content (user might just want to share without comment)
    // But ensure we have at least the sharedPostId reference

    if (images && images.length > 5) {
      return res.status(400).json({
        success: false,
        message: 'Maximum 5 images allowed per post'
      });
    }

    // Validate that tagged users exist
    if (tags && tags.length > 0) {
      const taggedUsers = await User.find({ _id: { $in: tags } });
      if (taggedUsers.length !== tags.length) {
        return res.status(400).json({
          success: false,
          message: 'One or more tagged users do not exist'
        });
      }
    }

    // Create post
    const post = new Post({
      userId,
      content: content || '',
      images,
      privacySetting,
      location,
      tags,
      sharedPostId: sharedPostId || null
    });

    await post.save();
    await post.populate('userId', 'username avatar profile.firstName profile.lastName');
    await post.populate('tags', 'username avatar profile.firstName profile.lastName');

    res.status(201).json({
      success: true,
      message: 'Post created successfully',
      data: {
        postId: post._id,
        post: post
      }
    });

  } catch (error) {
    console.error('Create post error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while creating post',
      error: process.env.NODE_ENV === 'development' ? error.message : undefined
    });
  }
};

// @desc    Get user's posts
// @route   GET /api/posts/user/:userId
// @access  Private
const getUserPosts = async (req, res) => {
  try {
    const { userId } = req.params;
    const { page = 1, limit = 20 } = req.query;
    const viewerId = req.user.id;

    // Check if user exists
    const user = await User.findById(userId);
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    // Get posts
    const posts = await Post.getUserPosts(userId, parseInt(page), parseInt(limit), viewerId);

    res.json({
      success: true,
      data: {
        posts,
        pagination: {
          page: parseInt(page),
          limit: parseInt(limit),
          total: posts.length
        }
      }
    });

  } catch (error) {
    console.error('Get user posts error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching posts'
    });
  }
};

// @desc    Get feed posts (public and friends' posts)
// @route   GET /api/posts/feed
// @access  Private
const getFeedPosts = async (req, res) => {
  try {
    const { page = 1, limit = 20 } = req.query;
    const userId = req.user.id;

    const posts = await Post.getFeedPosts(userId, parseInt(page), parseInt(limit));

    res.json({
      success: true,
      data: {
        posts,
        pagination: {
          page: parseInt(page),
          limit: parseInt(limit),
          total: posts.length
        }
      }
    });

  } catch (error) {
    console.error('Get feed posts error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching feed'
    });
  }
};

// @desc    Get post by ID
// @route   GET /api/posts/:id
// @access  Private
const getPostById = async (req, res) => {
  try {
    const { id } = req.params;
    const { page = 1, limit = 20, sortBy = 'recent' } = req.query;
    const userId = req.user.id;

    const post = await Post.findOne({
      _id: id,
      isActive: true,
      isDeleted: false
    })
      .populate('userId', 'username avatar profile.firstName profile.lastName')
      .populate('tags', 'username avatar profile.firstName profile.lastName')
      .populate('likes.user', 'username avatar')
      .populate('comments.user', 'username avatar profile.firstName profile.lastName')
      .populate('comments.reactions.user', 'username avatar')
      .populate('shares.user', 'username avatar');

    if (!post) {
      return res.status(404).json({
        success: false,
        message: 'Post not found'
      });
    }

    // Check privacy settings
    if (post.privacySetting === 'only_me' && post.userId._id.toString() !== userId) {
      return res.status(403).json({
        success: false,
        message: 'Access denied to this post'
      });
    }

    if (post.privacySetting === 'friends') {
      const user = await User.findById(userId);
      const isFriend = user.friends && user.friends.some(friendId => friendId.toString() === post.userId._id.toString());
      if (!isFriend && post.userId._id.toString() !== userId) {
        return res.status(403).json({
          success: false,
          message: 'Access denied to this post'
        });
      }
    }

    // Populate all comments first
    await post.populate('comments.user', 'username avatar profile.firstName profile.lastName');
    await post.populate('comments.reactions.user', 'username avatar');
    
    // Get top-level comments with pagination and sorting
    const topLevelComments = post.getTopLevelComments(parseInt(page), parseInt(limit), sortBy);
    
    // For each top-level comment, get its replies (limit to 3 most recent)
    const commentsWithReplies = topLevelComments.map(comment => {
      const commentObj = comment.toObject();
      const replies = post.getCommentReplies(comment._id, 3);
      commentObj.replies = replies.map(r => r.toObject());
      commentObj.repliesCount = post.comments.filter(c => 
        !c.isDeleted && c.parentCommentId?.toString() === comment._id.toString()
      ).length;
      return commentObj;
    });

    // Calculate total comments count
    const totalComments = post.comments.filter(c => !c.isDeleted && !c.parentCommentId).length;
    const totalPages = Math.ceil(totalComments / parseInt(limit));

    res.json({
      success: true,
      data: {
        post: {
          ...post.toObject(),
          comments: commentsWithReplies
        },
        pagination: {
          page: parseInt(page),
          limit: parseInt(limit),
          total: totalComments,
          totalPages: totalPages,
          hasMore: parseInt(page) < totalPages
        }
      }
    });

  } catch (error) {
    console.error('Get post by ID error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching post'
    });
  }
};

// @desc    Update post
// @route   PUT /api/posts/:id
// @access  Private
const updatePost = async (req, res) => {
  try {
    const { id } = req.params;
    const userId = req.user.id;
    const { content, images, privacySetting, location, tags } = req.body;

    const post = await Post.findById(id);
    if (!post || !post.isActive || post.isDeleted) {
      return res.status(404).json({
        success: false,
        message: 'Post not found'
      });
    }

    // Check if user owns the post
    if (post.userId.toString() !== userId) {
      return res.status(403).json({
        success: false,
        message: 'You can only update your own posts'
      });
    }

    // Validate business rules
    const newContent = content !== undefined ? content : post.content;
    const newImages = images !== undefined ? images : post.images;
    
    if (!newContent && (!newImages || newImages.length === 0)) {
      return res.status(400).json({
        success: false,
        message: 'Post must contain at least text content OR at least 1 image'
      });
    }

    if (newImages && newImages.length > 5) {
      return res.status(400).json({
        success: false,
        message: 'Maximum 5 images allowed per post'
      });
    }

    // Update post
    if (content !== undefined) post.content = content;
    if (images !== undefined) post.images = images;
    if (privacySetting !== undefined) post.privacySetting = privacySetting;
    if (location !== undefined) post.location = location;
    if (tags !== undefined) {
      // Validate tagged users
      if (tags.length > 0) {
        const taggedUsers = await User.find({ _id: { $in: tags } });
        if (taggedUsers.length !== tags.length) {
          return res.status(400).json({
            success: false,
            message: 'One or more tagged users do not exist'
          });
        }
      }
      post.tags = tags;
    }

    await post.save();
    await post.populate('userId', 'username avatar profile.firstName profile.lastName');
    await post.populate('tags', 'username avatar profile.firstName profile.lastName');

    res.json({
      success: true,
      message: 'Post updated successfully',
      data: {
        post
      }
    });

  } catch (error) {
    console.error('Update post error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while updating post'
    });
  }
};

// @desc    Delete post
// @route   DELETE /api/posts/:id
// @access  Private
const deletePost = async (req, res) => {
  try {
    const { id } = req.params;
    const userId = req.user.id;

    const post = await Post.findById(id);
    if (!post || post.isDeleted) {
      return res.status(404).json({
        success: false,
        message: 'Post not found'
      });
    }

    // Check if user owns the post or is admin
    if (post.userId.toString() !== userId && req.user.role !== 'admin') {
      return res.status(403).json({
        success: false,
        message: 'You can only delete your own posts'
      });
    }

    // Soft delete
    post.isDeleted = true;
    post.isActive = false;
    await post.save();

    res.json({
      success: true,
      message: 'Post deleted successfully'
    });

  } catch (error) {
    console.error('Delete post error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while deleting post'
    });
  }
};

// @desc    Hide post from user's feed
// @route   POST /api/posts/:id/hide
// @access  Private
const hidePost = async (req, res) => {
  try {
    const { id } = req.params;
    const userId = req.user.id;

    const post = await Post.findById(id);
    if (!post || !post.isActive || post.isDeleted) {
      return res.status(404).json({
        success: false,
        message: 'Post not found'
      });
    }

    // Cannot hide your own post
    if (post.userId.toString() === userId) {
      return res.status(400).json({
        success: false,
        message: 'You cannot hide your own post. Use delete instead.'
      });
    }

    // Add post to user's hiddenPosts list
    const user = await User.findById(userId);
    if (!user.hiddenPosts) {
      user.hiddenPosts = [];
    }
    
    // Check if already hidden
    if (user.hiddenPosts.some(postId => postId.toString() === id)) {
      return res.status(400).json({
        success: false,
        message: 'Post is already hidden'
      });
    }

    user.hiddenPosts.push(id);
    await user.save();

    res.json({
      success: true,
      message: 'Post hidden successfully'
    });

  } catch (error) {
    console.error('Hide post error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while hiding post'
    });
  }
};

// @desc    Like/Unlike post
// @route   POST /api/posts/:id/like
// @access  Private
const toggleLike = async (req, res) => {
  try {
    const { id } = req.params;
    const userId = req.user.id;

    const post = await Post.findById(id);
    if (!post || !post.isActive || post.isDeleted) {
      return res.status(404).json({
        success: false,
        message: 'Post not found'
      });
    }

    // Check if user already liked
    const likeIndex = post.likes.findIndex(like => like.user.toString() === userId);
    
    if (likeIndex > -1) {
      // Unlike
      post.likes.splice(likeIndex, 1);
      await post.save();
      res.json({
        success: true,
        message: 'Post unliked',
        data: {
          liked: false,
          likesCount: post.likes.length
        }
      });
    } else {
      // Like
      post.likes.push({ user: userId });
      await post.save();
      await post.populate('likes.user', 'username avatar');
      res.json({
        success: true,
        message: 'Post liked',
        data: {
          liked: true,
          likesCount: post.likes.length
        }
      });
    }

  } catch (error) {
    console.error('Toggle like error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while toggling like'
    });
  }
};

// @desc    Add comment to post
// @route   POST /api/posts/:id/comments
// @access  Private
const addComment = async (req, res) => {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({
        success: false,
        message: 'Validation failed',
        errors: errors.array()
      });
    }

    const { id } = req.params;
    const { content, parentCommentId = null, mediaUrl = null } = req.body;
    const userId = req.user.id;

    const post = await Post.findById(id);
    if (!post || !post.isActive || post.isDeleted) {
      return res.status(404).json({
        success: false,
        message: 'Post not found'
      });
    }

    // Validate parentCommentId if provided
    if (parentCommentId) {
      const parentComment = post.comments.id(parentCommentId);
      if (!parentComment || parentComment.isDeleted) {
        return res.status(404).json({
          success: false,
          message: 'Parent comment not found'
        });
      }
    }

    const newComment = {
      user: userId,
      content,
      parentCommentId: parentCommentId || null,
      mediaUrl: mediaUrl || null,
      reactions: [],
      isEdited: false,
      isDeleted: false
    };

    post.comments.push(newComment);
    await post.save();
    
    // Populate user info for the new comment
    const savedComment = post.comments[post.comments.length - 1];
    await post.populate('comments.user', 'username avatar profile.firstName profile.lastName');

    res.status(201).json({
      success: true,
      message: 'Comment added successfully',
      data: {
        comment: savedComment,
        commentsCount: post.commentsCount
      }
    });

  } catch (error) {
    console.error('Add comment error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while adding comment'
    });
  }
};

// @desc    Edit comment
// @route   PUT /api/posts/:id/comments/:commentId
// @access  Private
const editComment = async (req, res) => {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({
        success: false,
        message: 'Validation failed',
        errors: errors.array()
      });
    }

    const { id, commentId } = req.params;
    const { content } = req.body;
    const userId = req.user.id;

    const post = await Post.findById(id);
    if (!post || !post.isActive || post.isDeleted) {
      return res.status(404).json({
        success: false,
        message: 'Post not found'
      });
    }

    const comment = post.comments.id(commentId);
    if (!comment || comment.isDeleted) {
      return res.status(404).json({
        success: false,
        message: 'Comment not found'
      });
    }

    // Check if user owns the comment
    if (comment.user.toString() !== userId) {
      return res.status(403).json({
        success: false,
        message: 'You can only edit your own comments'
      });
    }

    comment.content = content;
    comment.updatedAt = new Date();
    comment.isEdited = true;

    await post.save();
    await post.populate('comments.user', 'username avatar profile.firstName profile.lastName');
    await post.populate('comments.reactions.user', 'username avatar');

    // Get updated comment
    const updatedComment = post.comments.id(commentId);

    res.json({
      success: true,
      message: 'Comment updated successfully',
      data: {
        comment: updatedComment
      }
    });

  } catch (error) {
    console.error('Edit comment error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while editing comment'
    });
  }
};

// @desc    Delete comment from post
// @route   DELETE /api/posts/:id/comments/:commentId
// @access  Private
const deleteComment = async (req, res) => {
  try {
    const { id, commentId } = req.params;
    const userId = req.user.id;

    const post = await Post.findById(id);
    if (!post || !post.isActive || post.isDeleted) {
      return res.status(404).json({
        success: false,
        message: 'Post not found'
      });
    }

    const comment = post.comments.id(commentId);
    if (!comment || comment.isDeleted) {
      return res.status(404).json({
        success: false,
        message: 'Comment not found'
      });
    }

    // Check if user owns the comment or is admin
    if (comment.user.toString() !== userId && req.user.role !== 'admin') {
      return res.status(403).json({
        success: false,
        message: 'You can only delete your own comments'
      });
    }

    // Soft delete: mark as deleted instead of removing
    comment.isDeleted = true;
    comment.content = '[Deleted]';
    await post.save();

    res.json({
      success: true,
      message: 'Comment deleted successfully',
      data: {
        commentsCount: post.commentsCount
      }
    });

  } catch (error) {
    console.error('Delete comment error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while deleting comment'
    });
  }
};

// @desc    Add reaction to comment
// @route   POST /api/posts/:id/comments/:commentId/reactions
// @access  Private
const addReactionToComment = async (req, res) => {
  try {
    const { id, commentId } = req.params;
    const { type } = req.body;
    const userId = req.user.id;

    if (!type || !['like', 'love', 'haha', 'wow', 'sad', 'angry'].includes(type)) {
      return res.status(400).json({
        success: false,
        message: 'Invalid reaction type. Must be one of: like, love, haha, wow, sad, angry'
      });
    }

    const post = await Post.findById(id);
    if (!post || !post.isActive || post.isDeleted) {
      return res.status(404).json({
        success: false,
        message: 'Post not found'
      });
    }

    const comment = post.comments.id(commentId);
    if (!comment || comment.isDeleted) {
      return res.status(404).json({
        success: false,
        message: 'Comment not found'
      });
    }

    // Remove existing reaction from this user
    comment.reactions = comment.reactions.filter(
      r => r.user.toString() !== userId.toString()
    );

    // Add new reaction
    comment.reactions.push({
      user: userId,
      type: type
    });

    await post.save();
    await post.populate('comments.reactions.user', 'username avatar');

    res.json({
      success: true,
      message: 'Reaction added successfully',
      data: {
        reaction: comment.reactions[comment.reactions.length - 1],
        reactionsCount: comment.reactions.length,
        reactions: comment.reactions
      }
    });

  } catch (error) {
    console.error('Add reaction to comment error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while adding reaction'
    });
  }
};

// @desc    Remove reaction from comment
// @route   DELETE /api/posts/:id/comments/:commentId/reactions
// @access  Private
const removeReactionFromComment = async (req, res) => {
  try {
    const { id, commentId } = req.params;
    const userId = req.user.id;

    const post = await Post.findById(id);
    if (!post || !post.isActive || post.isDeleted) {
      return res.status(404).json({
        success: false,
        message: 'Post not found'
      });
    }

    const comment = post.comments.id(commentId);
    if (!comment || comment.isDeleted) {
      return res.status(404).json({
        success: false,
        message: 'Comment not found'
      });
    }

    // Remove reaction from this user
    const initialLength = comment.reactions.length;
    comment.reactions = comment.reactions.filter(
      r => r.user.toString() !== userId.toString()
    );

    if (comment.reactions.length === initialLength) {
      return res.status(404).json({
        success: false,
        message: 'Reaction not found'
      });
    }

    await post.save();

    res.json({
      success: true,
      message: 'Reaction removed successfully',
      data: {
        reactionsCount: comment.reactions.length,
        reactions: comment.reactions
      }
    });

  } catch (error) {
    console.error('Remove reaction from comment error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while removing reaction'
    });
  }
};

// @desc    Share post
// @route   POST /api/posts/:id/share
// @access  Private
const sharePost = async (req, res) => {
  try {
    const { id } = req.params;
    const userId = req.user.id;

    const post = await Post.findById(id);
    if (!post || !post.isActive || post.isDeleted) {
      return res.status(404).json({
        success: false,
        message: 'Post not found'
      });
    }

    // Check if user already shared
    const shareIndex = post.shares.findIndex(share => share.user.toString() === userId);
    
    if (shareIndex > -1) {
      return res.status(400).json({
        success: false,
        message: 'Post already shared'
      });
    }

    post.shares.push({ user: userId });
    await post.save();

    res.json({
      success: true,
      message: 'Post shared successfully',
      data: {
        sharesCount: post.shares.length
      }
    });

  } catch (error) {
    console.error('Share post error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while sharing post'
    });
  }
};

module.exports = {
  createPost,
  getUserPosts,
  getFeedPosts,
  getPostById,
  updatePost,
  deletePost,
  hidePost,
  toggleLike,
  addComment,
  editComment,
  deleteComment,
  addReactionToComment,
  removeReactionFromComment,
  sharePost
};

