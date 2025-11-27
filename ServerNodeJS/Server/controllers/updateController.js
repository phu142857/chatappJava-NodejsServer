const Message = require('../models/Message');
const Post = require('../models/Post');
const Chat = require('../models/Chat');
const { validationResult } = require('express-validator');

/**
 * Get messages updated/created after a timestamp
 * @route GET /api/updates/messages?since=timestamp
 * @access Private
 */
const getMessagesUpdates = async (req, res) => {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({
        success: false,
        message: 'Validation failed',
        errors: errors.array()
      });
    }

    const userId = req.user.id;
    const since = parseInt(req.query.since) || 0;

    // Find messages that:
    // 1. Belong to chats where user is a participant
    // 2. Were created or updated after the 'since' timestamp
    // 3. Are not deleted
    const chats = await Chat.find({
      $or: [
        { 'participants.user': userId },
        { creator: userId }
      ]
    }).select('_id');

    const chatIds = chats.map(chat => chat._id);

    const messages = await Message.find({
      chatId: { $in: chatIds },
      $or: [
        { createdAt: { $gt: new Date(since) } },
        { updatedAt: { $gt: new Date(since) } }
      ],
      isDeleted: false
    })
      .populate('senderId', 'username avatar profile.firstName profile.lastName')
      .populate('chatId', 'name type participants')
      .sort({ createdAt: -1 })
      .limit(100); // Limit to prevent huge responses

    // If no updates, return 304 Not Modified
    if (messages.length === 0) {
      return res.status(304).json({
        success: true,
        message: 'No updates available'
      });
    }

    // Get the latest updated_at timestamp from the messages
    const latestTimestamp = messages.length > 0 
      ? Math.max(...messages.map(m => m.updatedAt?.getTime() || m.createdAt?.getTime() || 0))
      : since;

    res.json({
      success: true,
      data: {
        messages: messages.map(msg => ({
          _id: msg._id,
          chatId: msg.chatId._id || msg.chatId,
          senderId: msg.senderId._id || msg.senderId,
          sender: {
            _id: msg.senderId._id || msg.senderId,
            username: msg.senderId.username,
            avatar: msg.senderId.avatar,
            profile: msg.senderId.profile
          },
          content: msg.content,
          type: msg.type,
          attachments: msg.attachments,
          timestamp: msg.createdAt?.getTime() || 0,
          updatedAt: msg.updatedAt?.getTime() || msg.createdAt?.getTime() || 0,
          isRead: msg.readBy?.some(r => r.user.toString() === userId.toString()) || false,
          isDeleted: msg.isDeleted,
          replyTo: msg.replyTo,
          reactions: msg.reactions,
          edited: msg.edited,
          editedAt: msg.editedAt?.getTime() || null
        })),
        updated_at: latestTimestamp
      }
    });

  } catch (error) {
    console.error('Get messages updates error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching message updates'
    });
  }
};

/**
 * Get posts updated/created after a timestamp
 * @route GET /api/updates/posts?since=timestamp
 * @access Private
 */
const getPostsUpdates = async (req, res) => {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({
        success: false,
        message: 'Validation failed',
        errors: errors.array()
      });
    }

    const userId = req.user.id;
    const since = parseInt(req.query.since) || 0;

    // Find posts that:
    // 1. Are active and not deleted
    // 2. Were created or updated after the 'since' timestamp
    // 3. Are public, or friends-only and user is a friend, or user is the author
    const user = await require('../models/User').findById(userId).select('friends');
    const userFriends = user?.friends || [];

    const posts = await Post.find({
      isActive: true,
      isDeleted: false,
      $or: [
        { createdAt: { $gt: new Date(since) } },
        { updatedAt: { $gt: new Date(since) } }
      ],
      $or: [
        { privacySetting: 'public' },
        { 
          privacySetting: 'friends',
          userId: { $in: [userId, ...userFriends] }
        },
        { userId: userId }
      ]
    })
      .populate('userId', 'username avatar profile.firstName profile.lastName')
      .populate('tags', 'username avatar profile.firstName profile.lastName')
      .populate('likes.user', 'username avatar')
      .populate('shares.user', 'username avatar')
      .sort({ createdAt: -1 })
      .limit(50); // Limit to prevent huge responses

    // If no updates, return 304 Not Modified
    if (posts.length === 0) {
      return res.status(304).json({
        success: true,
        message: 'No updates available'
      });
    }

    // Get the latest updated_at timestamp from the posts
    const latestTimestamp = posts.length > 0 
      ? Math.max(...posts.map(p => p.updatedAt?.getTime() || p.createdAt?.getTime() || 0))
      : since;

    res.json({
      success: true,
      data: {
        posts: posts.map(post => ({
          _id: post._id,
          userId: post.userId._id || post.userId,
          author: {
            _id: post.userId._id || post.userId,
            username: post.userId.username,
            avatar: post.userId.avatar,
            profile: post.userId.profile
          },
          content: post.content,
          images: post.images || [],
          mediaType: post.images && post.images.length > 0 ? 'gallery' : 'none',
          createdAt: post.createdAt?.getTime() || 0,
          updatedAt: post.updatedAt?.getTime() || post.createdAt?.getTime() || 0,
          likesCount: post.likes?.length || 0,
          commentsCount: post.commentsCount || 0,
          sharesCount: post.shares?.length || 0,
          isLiked: post.likes?.some(l => l.user.toString() === userId.toString()) || false,
          tags: post.tags || [],
          privacySetting: post.privacySetting,
          sharedPostId: post.sharedPostId
        })),
        updated_at: latestTimestamp
      }
    });

  } catch (error) {
    console.error('Get posts updates error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching post updates'
    });
  }
};

/**
 * Get conversations updated after a timestamp
 * @route GET /api/updates/conversations?since=timestamp
 * @access Private
 */
const getConversationsUpdates = async (req, res) => {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({
        success: false,
        message: 'Validation failed',
        errors: errors.array()
      });
    }

    const userId = req.user.id;
    const since = parseInt(req.query.since) || 0;

    // Find conversations that:
    // 1. User is a participant
    // 2. Were created or updated after the 'since' timestamp
    // 3. Are active
    const conversations = await Chat.find({
      $or: [
        { 'participants.user': userId },
        { creator: userId }
      ],
      $or: [
        { createdAt: { $gt: new Date(since) } },
        { updatedAt: { $gt: new Date(since) } }
      ],
      isActive: true
    })
      .populate('participants.user', 'username avatar profile.firstName profile.lastName')
      .populate('creator', 'username avatar')
      .sort({ updatedAt: -1 })
      .limit(50); // Limit to prevent huge responses

    // If no updates, return 304 Not Modified
    if (conversations.length === 0) {
      return res.status(304).json({
        success: true,
        message: 'No updates available'
      });
    }

    // Get the latest updated_at timestamp from the conversations
    const latestTimestamp = conversations.length > 0 
      ? Math.max(...conversations.map(c => c.updatedAt?.getTime() || c.createdAt?.getTime() || 0))
      : since;

    res.json({
      success: true,
      data: {
        conversations: conversations.map(chat => ({
          _id: chat._id,
          type: chat.type,
          name: chat.name,
          description: chat.description,
          avatar: chat.avatar,
          creator: chat.creator?._id || chat.creator,
          participants: chat.participants || [],
          createdAt: chat.createdAt?.getTime() || 0,
          updatedAt: chat.updatedAt?.getTime() || chat.createdAt?.getTime() || 0,
          isActive: chat.isActive,
          isPublic: chat.isPublic,
          visibility: chat.visibility
        })),
        updated_at: latestTimestamp
      }
    });

  } catch (error) {
    console.error('Get conversations updates error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching conversation updates'
    });
  }
};

module.exports = {
  getMessagesUpdates,
  getPostsUpdates,
  getConversationsUpdates
};

