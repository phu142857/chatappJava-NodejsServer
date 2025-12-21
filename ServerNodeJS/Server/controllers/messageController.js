const Message = require('../models/Message');
const Chat = require('../models/Chat');
const User = require('../models/User');
const { validationResult } = require('express-validator');
const { summarizeMessages } = require('../services/summarizeService');
const { sendChatMessageNotification } = require('../services/fcmService');

// @desc    Get messages for a chat
// @route   GET /api/messages/:chatId
// @access  Private
const getMessages = async (req, res) => {
  try {
    const { chatId } = req.params;
    const { page = 1, limit = 50 } = req.query;

    // Check if chat exists and user is participant
    const chat = await Chat.findById(chatId).populate('participants.user', 'username avatar status');
    if (!chat || !chat.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Chat not found'
      });
    }

    // Check if user is participant (handle both populated and unpopulated user fields)
    const isParticipant = chat.participants.some(p => {
      if (!p.isActive) return false;
      // Handle populated user (object with _id) or unpopulated (just ObjectId)
      const userId = p.user && (p.user._id ? p.user._id.toString() : p.user.toString());
      return userId === req.user.id;
    });

    // Allow admin/moderator to access any chat
    const isAdmin = req.user && (req.user.role === 'admin' || req.user.role === 'moderator');

    if (!isParticipant && !isAdmin) {
      return res.status(403).json({
        success: false,
        message: 'Access denied to this chat'
      });
    }

    // Check if user has leftAt timestamp (deleted chat before and was reactivated)
    // If so, only show messages after they left (new messages only)
    const userParticipant = chat.participants.find(p => {
      if (!p.isActive) return false;
      // Handle populated user (object with _id) or unpopulated (just ObjectId)
      const userId = p.user && (p.user._id ? p.user._id.toString() : p.user.toString());
      return userId === req.user.id;
    });
    const leftAt = userParticipant && userParticipant.leftAt ? userParticipant.leftAt : null;
    
    // Get messages (filter by leftAt if user deleted chat before)
    const messages = await Message.getChatMessages(chatId, parseInt(page), parseInt(limit), leftAt);

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

    // If private chat, enforce block status and reactivate inactive participant
    if (chat.type === 'private') {
      // Get the other participant id
      const otherParticipant = chat.participants.find(p => p.user && p.user.toString() !== req.user.id);
      
      // If other participant is inactive, reactivate them (they deleted the chat but should receive new messages)
      if (otherParticipant && !otherParticipant.isActive) {
        await chat.addParticipant(otherParticipant.user, 'member');
        console.log(`Reactivated participant ${otherParticipant.user} in chat ${chat._id} because they received a new message`);
      }
      
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
      { path: 'sender', select: 'username avatar status profile' },
      { path: 'replyTo', select: 'content sender type' }
    ]);

    // Get message object for notification (before async call)
    const messageObj = message.toJSON();

    // Send push notifications to other participants (async, don't wait)
    (async () => {
      try {
        // Get all active participants except the sender
        const recipientIds = chat.participants
          .filter(p => p.isActive && p.user && p.user.toString() !== req.user.id)
          .map(p => p.user.toString());

        if (recipientIds.length > 0) {
          // Get sender info for notification
          const sender = await User.findById(req.user.id).select('username avatar profile');
          
          // Get recipients with FCM tokens
          const recipients = await User.find({ 
            _id: { $in: recipientIds },
            'fcmTokens.0': { $exists: true } // Only users with FCM tokens
          }).select('fcmTokens username avatar profile');

          console.log(`[FCM] Sending push notification to ${recipients.length} recipient(s) for chat ${chatId}`);

          // Send notification to each recipient
          for (const recipient of recipients) {
            try {
              const result = await sendChatMessageNotification(
                recipient,
                sender,
                {
                  ...messageObj,
                  text: content,
                  type: type,
                  chatName: chat.name || (chat.type === 'private' ? null : 'Group')
                },
                chatId,
                chat.type
              );
              
              if (result.success) {
                console.log(`[FCM] ✓ Notification sent to user ${recipient.username} (${recipient._id})`);
              } else {
                console.error(`[FCM] ✗ Failed to send notification to user ${recipient.username}:`, result.error);
              }
            } catch (notifError) {
              console.error(`[FCM] ✗ Error sending notification to user ${recipient._id}:`, notifError);
            }
          }
        } else {
          console.log(`[FCM] No recipients found for chat ${chatId}`);
        }
      } catch (error) {
        console.error('[FCM] Error sending push notifications:', error);
        // Don't fail the request if notification fails
      }
    })();
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

    // Emit socket event to notify all participants
    const io = req.app.get('io');
    if (io && chat) {
      // Get chat participants
      await chat.populate('participants.user', 'username avatar status');
      const participants = chat.participants.filter(p => p.isActive);
      
      // Emit to all participants
      participants.forEach(participant => {
        const userId = participant.user && (participant.user._id ? participant.user._id.toString() : participant.user.toString());
        if (userId) {
          const userRoom = `user_${userId}`;
          io.to(userRoom).emit('message_deleted', {
            id: message._id,
            chat: chat._id,
            chatType: chat.type,
            deletedBy: req.user.id
          });
        }
      });
    }

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

// @desc    Summarize chat messages using summary cursor (like Facebook Messenger)
// @route   GET /api/messages/:chatId/summarize
// @access  Private
const summarizeChat = async (req, res) => {
  try {
    const { chatId } = req.params;

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

    // Get last summarized timestamp from chat (summary cursor)
    const lastSummarizedTimestamp = chat.lastSummarizedTimestamp || null;

    // Build query: messages after lastSummarizedTimestamp
    // IMPORTANT: Only summarize messages from OTHER users (not from current user)
    const query = {
      chat: chatId,
      isDeleted: false,
      sender: { $ne: req.user.id } // Only messages from other users
    };

    if (lastSummarizedTimestamp) {
      query.createdAt = { $gt: lastSummarizedTimestamp };
    }

    // Fetch all pending messages (messages after cursor) from other users only
    // Sort by timestamp descending to get most recent first
    let pendingMessages = await Message.find(query)
      .populate('sender', 'username')
      .sort({ createdAt: -1 }); // Descending order (newest first)

    // Limit to 5-50 most recent messages
    const MIN_MESSAGES = 5;
    const MAX_MESSAGES = 50;
    
    if (pendingMessages.length > MAX_MESSAGES) {
      // Take only the 50 most recent messages
      pendingMessages = pendingMessages.slice(0, MAX_MESSAGES);
    }
    
    // Reverse to chronological order for summarization
    pendingMessages = pendingMessages.reverse();

    // If no new messages or less than minimum, return early
    if (pendingMessages.length === 0) {
      return res.json({
        success: true,
        data: {
          summary: 'No new messages to summarize.',
          message_count: 0,
          new_last_summarized_timestamp: lastSummarizedTimestamp
        }
      });
    }
    
    // If less than minimum messages, still summarize but note it
    if (pendingMessages.length < MIN_MESSAGES) {
      // Still summarize, but this is a small batch
    }

    // Prepare chat context for AI
    const chatContext = {
      type: chat.type,
      participantCount: chat.participants ? chat.participants.filter(p => p.isActive).length : 0
    };

    // Generate summary using AI (with fallback to rule-based)
    const summary = await summarizeMessages(pendingMessages, chatContext);
    const messageCount = pendingMessages.length;

    // Get the newest message timestamp to update cursor
    const newestMessage = pendingMessages[pendingMessages.length - 1];
    const newLastSummarizedTimestamp = newestMessage.createdAt;

    // Update chat's lastSummarizedTimestamp
    chat.lastSummarizedTimestamp = newLastSummarizedTimestamp;
    await chat.save();

    res.json({
      success: true,
      data: {
        summary: summary.trim(),
        message_count: messageCount,
        new_last_summarized_timestamp: newLastSummarizedTimestamp
      }
    });

  } catch (error) {
    console.error('Summarize chat error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while summarizing chat'
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
  searchMessages,
  summarizeChat
};
