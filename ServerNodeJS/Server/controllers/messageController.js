const Message = require('../models/Message');
const Chat = require('../models/Chat');
const User = require('../models/User');
const { validationResult } = require('express-validator');

// @desc    Get messages for a chat
// @route   GET /api/messages/:chatId
// @access  Private
const getMessages = async (req, res) => {
  try {
    const { chatId } = req.params;
    const { page = 1, limit = 50 } = req.query;

    // Check if chat exists and user is participant
    const chat = await Chat.findById(chatId);
    if (!chat || !chat.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Chat not found'
      });
    }

    const isParticipant = chat.participants.some(
      p => p.user && p.user.toString() === req.user.id && p.isActive
    );

    // Allow admin/moderator to access any chat
    const isAdmin = req.user && (req.user.role === 'admin' || req.user.role === 'moderator');

    if (!isParticipant && !isAdmin) {
      return res.status(403).json({
        success: false,
        message: 'Access denied to this chat'
      });
    }

    // Get messages
    const messages = await Message.getChatMessages(chatId, parseInt(page), parseInt(limit));

    // Mark messages as read
    await Message.markAsRead(chatId, req.user.id);

    // Format messages with additional info
    const formattedMessages = messages.reverse().map(message => {
      const messageObj = message.toJSON();
      return {
        ...messageObj,
        chatType: chat.type,
        // Ensure sender has avatar info (handle null sender)
        sender: message.sender ? {
          ...messageObj.sender,
          avatar: message.sender.avatar || ''
        } : null,
        senderInfo: message.sender ? {
          id: message.sender._id,
          username: message.sender.username,
          avatar: message.sender.avatar || ''
        } : null
      };
    });

    let isBlockedByMe = false;
    let hasBlockedMe = false;
    if (chat.type === 'private') {
      const otherParticipant = chat.participants.find(p => p.user && p.user.toString() !== req.user.id);
      if (otherParticipant) {
        try {
          const [meDoc, otherDoc] = await Promise.all([
            User.findById(req.user.id).select('blockedUsers'),
            User.findById(otherParticipant.user).select('blockedUsers')
          ]);
          const otherIdStr = otherParticipant.user.toString();
          isBlockedByMe = !!(meDoc && meDoc.blockedUsers && meDoc.blockedUsers.some(id => id.toString() === otherIdStr));
          hasBlockedMe = !!(otherDoc && otherDoc.blockedUsers && otherDoc.blockedUsers.some(id => id.toString() === req.user.id));
        } catch (_) {}
      }
    }

    res.json({
      success: true,
      data: {
        messages: formattedMessages,
        chatInfo: {
          id: chat._id,
          type: chat.type,
          name: chat.name,
          isBlockedByMe,
          hasBlockedMe
        }
      }
    });

  } catch (error) {
    console.error('Get messages error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching messages'
    });
  }
};

// @desc    Send a message
// @route   POST /api/messages
// @access  Private
const sendMessage = async (req, res) => {
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

    const { chatId, content, type = 'text', replyTo, attachments } = req.body;

    // Check if chat exists and user is participant
    const chat = await Chat.findById(chatId);
    if (!chat || !chat.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Chat not found'
      });
    }

    const isParticipant = chat.participants.some(
      p => p.user && p.user.toString() === req.user.id && p.isActive
    );

    if (!isParticipant) {
      return res.status(403).json({
        success: false,
        message: 'Access denied to this chat'
      });
    }

    // If private chat, enforce block status: sender cannot send if either side blocked the other
    if (chat.type === 'private') {
      // Get the other participant id
      const otherParticipant = chat.participants.find(p => p.user && p.user.toString() !== req.user.id);
      const me = await User.findById(req.user.id).select('blockedUsers');
      const other = otherParticipant ? await User.findById(otherParticipant.user).select('blockedUsers') : null;
      const iBlockedThem = me && me.blockedUsers && me.blockedUsers.some(id => id.toString() === otherParticipant.user.toString());
      const theyBlockedMe = other && other.blockedUsers && other.blockedUsers.some(id => id.toString() === req.user.id);
      if (iBlockedThem || theyBlockedMe) {
        return res.status(403).json({ success: false, message: 'Messaging is blocked between these users' });
      }
    }

    // Create message
    const message = new Message({
      content,
      type,
      sender: req.user.id,
      chat: chatId,
      replyTo: replyTo || undefined,
      attachments: attachments || []
    });

    await message.save();

    // Update chat's last message and activity
    chat.lastMessage = message._id;
    await chat.updateLastActivity();

    // Populate message for response
    await message.populate([
      { path: 'sender', select: 'username avatar status' },
      { path: 'replyTo', select: 'content sender type' }
    ]);

    const messageObj = message.toJSON();
    res.status(201).json({
      success: true,
      message: 'Message sent successfully',
      data: {
        message: {
          ...messageObj,
          chatType: chat.type,
          // Ensure sender has avatar info (handle null sender)
          sender: message.sender ? {
            ...messageObj.sender,
            avatar: message.sender.avatar || ''
          } : null,
          senderInfo: message.sender ? {
            id: message.sender._id,
            username: message.sender.username,
            avatar: message.sender.avatar || ''
          } : null
        }
      }
    });

  } catch (error) {
    console.error('Send message error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while sending message'
    });
  }
};

// @desc    Edit a message
// @route   PUT /api/messages/:id
// @access  Private
const editMessage = async (req, res) => {
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

    const { id } = req.params;
    const { content } = req.body;

    const message = await Message.findById(id);
    if (!message || message.isDeleted) {
      return res.status(404).json({
        success: false,
        message: 'Message not found'
      });
    }

    // Check if user is the sender
    if (!message.sender || message.sender.toString() !== req.user.id) {
      return res.status(403).json({
        success: false,
        message: 'Can only edit your own messages'
      });
    }

    // Check if message type allows editing
    if (message.type !== 'text') {
      return res.status(400).json({
        success: false,
        message: 'Only text messages can be edited'
      });
    }

    // Edit message
    await message.editMessage(content);

    // Populate sender info for response
    await message.populate('sender', 'username avatar status');

    const messageObj = message.toJSON();
    res.json({
      success: true,
      message: 'Message edited successfully',
      data: {
        message: {
          ...messageObj,
          // Ensure sender has avatar info
          sender: {
            ...messageObj.sender,
            avatar: message.sender.avatar || ''
          },
          senderInfo: {
            id: message.sender._id,
            username: message.sender.username,
            avatar: message.sender.avatar || ''
          }
        }
      }
    });

  } catch (error) {
    console.error('Edit message error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while editing message'
    });
  }
};

// @desc    Delete a message
// @route   DELETE /api/messages/:id
// @access  Private
const deleteMessage = async (req, res) => {
  try {
    const { id } = req.params;

    const message = await Message.findById(id);
    if (!message || message.isDeleted) {
      return res.status(404).json({
        success: false,
        message: 'Message not found'
      });
    }

    // Check if user is the sender, chat admin, or global admin
    const chat = await Chat.findById(message.chat);
    const userParticipant = chat.participants.find(
      p => p.user && p.user.toString() === req.user.id && p.isActive
    );

    const isSender = message.sender && message.sender.toString() === req.user.id;
    const isChatAdmin = userParticipant && userParticipant.role === 'admin';
    const isGlobalAdmin = req.user && (req.user.role === 'admin' || req.user.role === 'moderator');

    if (!isSender && !isChatAdmin && !isGlobalAdmin) {
      return res.status(403).json({
        success: false,
        message: 'Permission denied'
      });
    }

    // Soft delete message
    await message.softDelete(req.user.id);

    res.json({
      success: true,
      message: 'Message deleted successfully'
    });

  } catch (error) {
    console.error('Delete message error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while deleting message'
    });
  }
};

// @desc    Add reaction to message
// @route   POST /api/messages/:id/reactions
// @access  Private
const addReaction = async (req, res) => {
  try {
    const { id } = req.params;
    const { emoji } = req.body;

    if (!emoji || emoji.trim() === '') {
      return res.status(400).json({
        success: false,
        message: 'Emoji is required'
      });
    }

    const message = await Message.findById(id);
    if (!message || message.isDeleted) {
      return res.status(404).json({
        success: false,
        message: 'Message not found'
      });
    }

    // Check if user has access to the chat
    const chat = await Chat.findById(message.chat);
    const isParticipant = chat.participants.some(
      p => p.user && p.user.toString() === req.user.id && p.isActive
    );

    if (!isParticipant) {
      return res.status(403).json({
        success: false,
        message: 'Access denied'
      });
    }

    // Add reaction
    await message.addReaction(req.user.id, emoji.trim());

    res.json({
      success: true,
      message: 'Reaction added successfully',
      data: {
        reactions: message.reactions
      }
    });

  } catch (error) {
    console.error('Add reaction error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while adding reaction'
    });
  }
};

// @desc    Remove reaction from message
// @route   DELETE /api/messages/:id/reactions
// @access  Private
const removeReaction = async (req, res) => {
  try {
    const { id } = req.params;
    const { emoji } = req.body;

    if (!emoji || emoji.trim() === '') {
      return res.status(400).json({
        success: false,
        message: 'Emoji is required'
      });
    }

    const message = await Message.findById(id);
    if (!message || message.isDeleted) {
      return res.status(404).json({
        success: false,
        message: 'Message not found'
      });
    }

    // Remove reaction
    await message.removeReaction(req.user.id, emoji.trim());

    res.json({
      success: true,
      message: 'Reaction removed successfully',
      data: {
        reactions: message.reactions
      }
    });

  } catch (error) {
    console.error('Remove reaction error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while removing reaction'
    });
  }
};

// @desc    Mark messages as read
// @route   PUT /api/messages/:chatId/read
// @access  Private
const markAsRead = async (req, res) => {
  try {
    const { chatId } = req.params;
    const { messageIds } = req.body;

    // Check if user has access to the chat
    const chat = await Chat.findById(chatId);
    if (!chat || !chat.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Chat not found'
      });
    }

    const isParticipant = chat.participants.some(
      p => p.user && p.user.toString() === req.user.id && p.isActive
    );

    if (!isParticipant) {
      return res.status(403).json({
        success: false,
        message: 'Access denied'
      });
    }

    // Mark messages as read
    await Message.markAsRead(chatId, req.user.id, messageIds);

    res.json({
      success: true,
      message: 'Messages marked as read'
    });

  } catch (error) {
    console.error('Mark as read error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while marking messages as read'
    });
  }
};

// @desc    Search messages in chat
// @route   GET /api/messages/:chatId/search
// @access  Private
const searchMessages = async (req, res) => {
  try {
    const { chatId } = req.params;
    const { q, page = 1, limit = 20 } = req.query;

    if (!q || q.trim().length < 2) {
      return res.status(400).json({
        success: false,
        message: 'Search query must be at least 2 characters'
      });
    }

    // Check if user has access to the chat
    const chat = await Chat.findById(chatId);
    if (!chat || !chat.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Chat not found'
      });
    }

    const isParticipant = chat.participants.some(
      p => p.user && p.user.toString() === req.user.id && p.isActive
    );

    if (!isParticipant) {
      return res.status(403).json({
        success: false,
        message: 'Access denied'
      });
    }

    const skip = (page - 1) * limit;

    // Search messages
    const messages = await Message.find({
      chat: chatId,
      content: { $regex: q.trim(), $options: 'i' },
      type: 'text',
      isDeleted: false
    })
    .populate('sender', 'username avatar status')
    .sort({ createdAt: -1 })
    .skip(skip)
    .limit(parseInt(limit));

    // Format messages with additional info
    const formattedMessages = messages.map(message => {
      const messageObj = message.toJSON();
      return {
        ...messageObj,
        chatType: chat.type,
        // Ensure sender has avatar info (handle null sender)
        sender: message.sender ? {
          ...messageObj.sender,
          avatar: message.sender.avatar || ''
        } : null,
        senderInfo: message.sender ? {
          id: message.sender._id,
          username: message.sender.username,
          avatar: message.sender.avatar || ''
        } : null
      };
    });

    res.json({
      success: true,
      data: {
        messages: formattedMessages,
        searchQuery: q.trim(),
        chatInfo: {
          id: chat._id,
          type: chat.type,
          name: chat.name
        }
      }
    });

  } catch (error) {
    console.error('Search messages error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while searching messages'
    });
  }
};

// @desc    Get all messages (admin only)
// @route   GET /api/messages/admin
// @access  Private (Admin)
const getAllMessages = async (req, res) => {
  try {
    const { 
      page = 1, 
      limit = 50, 
      chatId, 
      userId, 
      messageType, 
      search, 
      dateFrom, 
      dateTo 
    } = req.query;
    
    const skip = (page - 1) * limit;
    let query = { isDeleted: { $ne: true } };

    // Add filters
    if (chatId) query.chat = chatId;
    if (userId) query.sender = userId;
    if (messageType) query.messageType = messageType;
    if (search) {
      query.content = { $regex: search, $options: 'i' };
    }
    if (dateFrom || dateTo) {
      query.createdAt = {};
      if (dateFrom) query.createdAt.$gte = new Date(dateFrom);
      if (dateTo) query.createdAt.$lte = new Date(dateTo);
    }

    const messages = await Message.find(query)
      .populate('sender', 'username email avatar')
      .populate('chat', 'name type')
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(parseInt(limit));

    const total = await Message.countDocuments(query);

    res.json({
      success: true,
      data: {
        messages,
        pagination: {
          page: parseInt(page),
          limit: parseInt(limit),
          total,
          pages: Math.ceil(total / limit)
        }
      }
    });

  } catch (error) {
    console.error('Get all messages error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching messages'
    });
  }
};

module.exports = {
  getMessages,
  getAllMessages,
  sendMessage,
  editMessage,
  deleteMessage,
  addReaction,
  removeReaction,
  markAsRead,
  searchMessages
};
