const Chat = require('../models/Chat');
const Message = require('../models/Message');
const User = require('../models/User');
const Group = require('../models/Group');
const { validationResult } = require('express-validator');

// @desc    Get user's chats
// @route   GET /api/chats
// @access  Private
const getChats = async (req, res) => {
  try {
    const { page = 1, limit = 20 } = req.query;
    const skip = (page - 1) * limit;

    console.log('Getting chats for user:', req.user.id);
    const chats = await Chat.findUserChats(req.user.id)
      .skip(skip)
      .limit(parseInt(limit));
    
    console.log('Found chats:', chats.length, 'for user:', req.user.id);

    // Filter out chats where user is not active participant
    const validChats = chats.filter(chat => {
      // Check if current user is an active participant
      const userParticipant = chat.participants.find(p => 
        p.user && p.user._id && p.user._id.toString() === req.user.id && p.isActive
      );
      
      if (!userParticipant) {
        return false; // User is not an active participant
      }
      
      if (chat.type === 'private') {
        // For private chats, ensure both participants exist (but one may be inactive if deleted)
        // Only check that both participants have valid user objects
        return chat.participants.every(p => p.user && p.user._id);
      }
      
      return true; // Group chats - user is active participant
    });

    console.log('Valid chats after filtering:', validChats.length, 'for user:', req.user.id);

    // Get unread message counts for each chat and format chat data
    const chatsWithUnreadCount = await Promise.all(
      validChats.map(async (chat) => {
        const unreadCount = await Message.countDocuments({
          chat: chat._id,
          'readBy.user': { $ne: req.user.id },
          sender: { $ne: req.user.id },
          isDeleted: false
        });

        // Format chat data for private c
        let chatData = { ...chat.toJSON(), unreadCount };
        
        if (chat.type === 'private') {
          // Find the other participant (not the current user)
          const otherParticipant = chat.participants.find(
            p => p.user && p.user._id && p.user._id.toString() !== req.user.id
          );
          
          if (otherParticipant && otherParticipant.user) {
            // Set chat name to the other participant's name
            chatData.name = otherParticipant.user.username;
            chatData.otherParticipant = {
              id: otherParticipant.user._id,
              username: otherParticipant.user.username,
              email: otherParticipant.user.email,
              avatar: otherParticipant.user.avatar
            };
          } else {
            // Handle case where other participant user is null/deleted
            chatData.name = 'Unknown User';
            chatData.otherParticipant = {
              id: null,
              username: 'Unknown User',
              email: '',
              avatar: ''
            };
          }
        }

        return chatData;
      })
    );

    res.json({
      success: true,
      data: {
        chats: chatsWithUnreadCount
      }
    });

  } catch (error) {
    console.error('Get chats error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching chats'
    });
  }
};

// @desc    Get all chats (admin only)
// @route   GET /api/chats/admin
// @access  Private (Admin)
const getAllChats = async (req, res) => {
  try {
    const { page = 1, limit = 50 } = req.query;
    const skip = (page - 1) * limit;

    const chats = await Chat.find({ isActive: true })
      .populate({
        path: 'participants.user',
        select: 'username email avatar status',
        model: 'User'
      })
      .populate('lastMessage')
      .sort({ lastActivity: -1 })
      .skip(skip)
      .limit(parseInt(limit));

    const total = await Chat.countDocuments({ isActive: true });

    res.json({
      success: true,
      data: {
        chats,
        pagination: {
          page: parseInt(page),
          limit: parseInt(limit),
          total,
          pages: Math.ceil(total / limit)
        }
      }
    });

  } catch (error) {
    console.error('Get all chats error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching chats'
    });
  }
};

// @desc    Get chat by ID
// @route   GET /api/chats/:id
// @access  Private
const getChatById = async (req, res) => {
  try {
    const { id } = req.params;

    const chat = await Chat.findById(id)
      .populate('participants.user', 'username email avatar status lastSeen')
      .populate('lastMessage')
      .populate('createdBy', 'username');

    if (!chat || !chat.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Chat not found'
      });
    }

    // Check if user is a participant
    const isParticipant = chat.participants.some(
      p => p.user && p.user._id && p.user._id.toString() === req.user.id && p.isActive
    );

    if (!isParticipant) {
      return res.status(403).json({
        success: false,
        message: 'Access denied to this chat'
      });
    }

    res.json({
      success: true,
      data: {
        chat
      }
    });

  } catch (error) {
    console.error('Get chat by ID error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching chat'
    });
  }
};

// @desc    Create private chat
// @route   POST /api/chats/private
// @access  Private
const createPrivateChat = async (req, res) => {
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

    const { participantId } = req.body;

    // Prevent creating a chat with yourself
    if (req.user.id === participantId) {
      return res.status(400).json({
        success: false,
        message: 'Cannot create a private chat with yourself'
      });
    }

    // Check if participant exists
    const participant = await User.findById(participantId);
    if (!participant || !participant.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Participant not found'
      });
    }

    // Check if private chat already exists (including inactive participants)
    // First try to find with both active
    let existingChat = await Chat.findPrivateChat(req.user.id, participantId);
    
    // If not found, try to find with any participant status (one might be inactive)
    if (!existingChat) {
      existingChat = await Chat.findOne({
        type: 'private',
        'participants.user': { $all: [req.user.id, participantId] },
        isActive: true
      }).populate('participants.user', 'username email avatar status');
    }
    
    if (existingChat) {
      // Reactivate current user if they were inactive
      const currentUserParticipant = existingChat.participants.find(
        p => {
          const userId = p.user && (p.user._id ? p.user._id.toString() : p.user.toString());
          return userId === req.user.id;
        }
      );
      if (currentUserParticipant && !currentUserParticipant.isActive) {
        // When user explicitly creates/opens chat, reset leftAt so they see all messages
        // This is different from automatic reactivation when receiving a new message
        currentUserParticipant.leftAt = undefined;
        await existingChat.addParticipant(req.user.id, 'member');
        // Reload chat from database to ensure changes are saved and reflected
        existingChat = await Chat.findById(existingChat._id).populate('participants.user', 'username email avatar status');
      } else if (!currentUserParticipant) {
        // User not found in participants - this shouldn't happen, but handle it
        console.warn(`User ${req.user.id} not found in chat ${existingChat._id} participants`);
        // Try to add them
        await existingChat.addParticipant(req.user.id, 'member');
        existingChat = await Chat.findById(existingChat._id).populate('participants.user', 'username email avatar status');
      } else {
        // User is already active, just ensure populated
        await existingChat.populate('participants.user', 'username email avatar status');
      }
      
      // Format chat data for private chat (set name to other participant's username)
      const chatData = existingChat.toJSON();
      if (existingChat.type === 'private') {
        // Find the other participant (not the current user)
        const otherParticipant = existingChat.participants.find(
          p => p.user && p.user._id && p.user._id.toString() !== req.user.id
        );
        
        if (otherParticipant && otherParticipant.user) {
          // Set chat name to the other participant's name
          chatData.name = otherParticipant.user.username;
          chatData.otherParticipant = {
            id: otherParticipant.user._id,
            username: otherParticipant.user.username,
            email: otherParticipant.user.email,
            avatar: otherParticipant.user.avatar
          };
        } else {
          // Handle case where other participant user is null/deleted
          chatData.name = 'Unknown User';
          chatData.otherParticipant = {
            id: null,
            username: 'Unknown User',
            email: '',
            avatar: ''
          };
        }
      }
      
      return res.status(200).json({
        success: true,
        message: 'Private chat already exists',
        data: {
          chat: chatData
        }
      });
    }

    // Create new private chat
    const chat = new Chat({
      type: 'private',
      participants: [
        { user: req.user.id, role: 'member', isActive: true },
        { user: participantId, role: 'member', isActive: true }
      ],
      createdBy: req.user.id
    });

    await chat.save();

    // Populate participants
    await chat.populate('participants.user', 'username email avatar status');

    // Format chat data for private chat (set name to other participant's username)
    const chatData = chat.toJSON();
    if (chat.type === 'private') {
      // Find the other participant (not the current user)
      const otherParticipant = chat.participants.find(
        p => p.user && p.user._id && p.user._id.toString() !== req.user.id
      );
      
      if (otherParticipant && otherParticipant.user) {
        // Set chat name to the other participant's name
        chatData.name = otherParticipant.user.username;
        chatData.otherParticipant = {
          id: otherParticipant.user._id,
          username: otherParticipant.user.username,
          email: otherParticipant.user.email,
          avatar: otherParticipant.user.avatar
        };
      } else {
        // Handle case where other participant user is null/deleted
        chatData.name = 'Unknown User';
        chatData.otherParticipant = {
          id: null,
          username: 'Unknown User',
          email: '',
          avatar: ''
        };
      }
    }

    res.status(201).json({
      success: true,
      message: 'Private chat created successfully',
      data: {
        chat: chatData
      }
    });

  } catch (error) {
    console.error('Create private chat error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while creating private chat'
    });
  }
};

// @desc    Create group chat
// @route   POST /api/chats/group
// @access  Private
const createGroupChat = async (req, res) => {
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

    const { name, description, participantIds, avatar, settings } = req.body;

    // Validate participants
    if (!participantIds || participantIds.length < 2) {
      return res.status(400).json({
        success: false,
        message: 'Group chat must include at least 2 other participants'
      });
    }

    // Check if all participants exist
    const participants = await User.find({
      _id: { $in: participantIds },
      isActive: true
    });

    if (participants.length !== participantIds.length) {
      return res.status(400).json({
        success: false,
        message: 'Some participants not found'
      });
    }

    // Allow duplicate group names; no unique check on name

    // Create Group first
    const groupMembers = [
      { user: req.user.id, role: 'admin' }, // Creator is admin
      ...participantIds.map(id => ({ user: id, role: 'member' }))
    ];

    const group = new Group({
      name,
      description,
      avatar: avatar || '',
      members: groupMembers,
      createdBy: req.user.id,
      settings: {
        ...(settings || {}),
        isPublic: settings && typeof settings.isPublic === 'boolean' ? settings.isPublic : false
      }
    });

    await group.save();

    // Create associated Chat
    const chatParticipants = [
      { user: req.user.id, role: 'admin' }, // Creator is admin
      ...participantIds.map(id => ({ user: id, role: 'member' }))
    ];

    const chat = new Chat({
      name,
      description,
      type: 'group',
      groupId: group._id, // Link chat to group by ID
      participants: chatParticipants,
      avatar: avatar || '',
      createdBy: req.user.id,
      isActive: true
    });

    await chat.save();

    // Populate participants
    await chat.populate('participants.user', 'username email avatar status');
    await group.populate('members.user', 'username email avatar status');

    res.status(201).json({
      success: true,
      message: 'Group chat created successfully',
      data: {
        chat,
        group
      }
    });

  } catch (error) {
    console.error('Create group chat error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while creating group chat'
    });
  }
};

// @desc    Update group chat
// @route   PUT /api/chats/:id
// @access  Private
const updateGroupChat = async (req, res) => {
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
    const { name, description, avatar } = req.body;

    const chat = await Chat.findById(id);
    if (!chat || !chat.isActive || chat.type !== 'group') {
      return res.status(404).json({
        success: false,
        message: 'Group chat not found'
      });
    }

    // Check if user has permission (admin or moderator)
    const userParticipant = chat.participants.find(
      p => p.user && p.user.toString() === req.user.id && p.isActive
    );

    if (!userParticipant || !['admin', 'moderator'].includes(userParticipant.role)) {
      return res.status(403).json({
        success: false,
        message: 'Only admins and moderators can update group chat'
      });
    }

    // Find associated group
    const group = await Group.findOne({ name: chat.name, isActive: true });
    if (!group) {
      return res.status(404).json({
        success: false,
        message: 'Associated group not found'
      });
    }

    // Check if user has permission in group (admin or moderator)
    if (!group.hasPermission(req.user.id) && group.createdBy.toString() !== req.user.id) {
      return res.status(403).json({
        success: false,
        message: 'Only group admins, moderators, or owner can update group chat'
      });
    }

    // Update chat fields
    if (name) {
      chat.name = name;
      group.name = name; // Update group name as well
    }
    if (description !== undefined) {
      chat.description = description;
      group.description = description; // Update group description as well
    }
    if (avatar !== undefined) {
      chat.avatar = avatar;
      group.avatar = avatar; // Update group avatar as well
    }

    await chat.updateLastActivity();
    await group.updateLastActivity();

    res.json({
      success: true,
      message: 'Group chat updated successfully',
      data: {
        chat,
        group
      }
    });

  } catch (error) {
    console.error('Update group chat error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while updating group chat'
    });
  }
};

// @desc    Add participant to group chat
// @route   POST /api/chats/:id/participants
// @access  Private
const addParticipant = async (req, res) => {
  try {
    const { id } = req.params;
    const { participantId } = req.body;

    const chat = await Chat.findById(id);
    if (!chat || !chat.isActive || chat.type !== 'group') {
      return res.status(404).json({
        success: false,
        message: 'Group chat not found'
      });
    }

    // Check if user has permission to add participants
    const userParticipant = chat.participants.find(
      p => p.user && p.user.toString() === req.user.id && p.isActive
    );

    if (!userParticipant || !['admin', 'moderator'].includes(userParticipant.role)) {
      return res.status(403).json({
        success: false,
        message: 'Permission denied'
      });
    }

    // Check if participant exists
    const participant = await User.findById(participantId);
    if (!participant || !participant.isActive) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    // Find associated group
    const group = await Group.findOne({ name: chat.name, isActive: true });
    if (!group) {
      return res.status(404).json({
        success: false,
        message: 'Associated group not found'
      });
    }

    // Check if user has permission in group
    if (!group.hasPermission(req.user.id)) {
      return res.status(403).json({
        success: false,
        message: 'Permission denied in group'
      });
    }

    // Add participant to both chat and group with 'member' role
    await chat.addParticipant(participantId, 'member');
    await group.addMember(participantId, 'member');

    res.json({
      success: true,
      message: 'Participant added successfully'
    });

  } catch (error) {
    console.error('Add participant error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while adding participant'
    });
  }
};

// @desc    Remove participant from group chat
// @route   DELETE /api/chats/:id/participants/:participantId
// @access  Private
const removeParticipant = async (req, res) => {
  try {
    const { id, participantId } = req.params;

    const chat = await Chat.findById(id);
    if (!chat || !chat.isActive || chat.type !== 'group') {
      return res.status(404).json({
        success: false,
        message: 'Group chat not found'
      });
    }

    // Check permissions: allow if requester is chat admin/mod OR group admin/owner OR removing self
    const userParticipant = chat.participants.find(
      p => p.user && p.user.toString() === req.user.id && p.isActive
    );
    const isRemovingSelf = participantId === req.user.id;
    let hasPermission = !!(userParticipant && ['admin', 'moderator'].includes(userParticipant.role));

    // Find associated group
    const group = await Group.findOne({ name: chat.name, isActive: true });
    if (!group) {
      return res.status(404).json({
        success: false,
        message: 'Associated group not found'
      });
    }

    // Also allow if requester has admin/mod role in group OR is group owner
    const isRemovingSelfFromGroup = participantId === req.user.id;
    const isGroupOwner = group.createdBy && group.createdBy.toString() === req.user.id;
    const hasGroupPermission = group.hasPermission(req.user.id) || isGroupOwner;
    hasPermission = hasPermission || hasGroupPermission;

    if (!isRemovingSelfFromGroup && !hasPermission) {
      return res.status(403).json({ success: false, message: 'Permission denied' });
    }

    // Remove participant from both chat and group
    await chat.removeParticipant(participantId);
    await group.removeMember(participantId);

    res.json({
      success: true,
      message: 'Participant removed successfully'
    });

  } catch (error) {
    console.error('Remove participant error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while removing participant'
    });
  }
};

// @desc    Leave group chat
// @route   POST /api/chats/:id/leave
// @access  Private
const leaveChat = async (req, res) => {
  try {
    const { id } = req.params;

    const chat = await Chat.findById(id);
    if (!chat || !chat.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Chat not found'
      });
    }

    if (chat.type === 'private') {
      return res.status(400).json({
        success: false,
        message: 'Cannot leave private chat'
      });
    }

    // Find associated group
    const group = await Group.findOne({ name: chat.name, isActive: true });
    if (!group) {
      return res.status(404).json({
        success: false,
        message: 'Associated group not found'
      });
    }

    // Check if user is the creator
    const isCreator = group.createdBy.toString() === req.user.id;
    
    // Check current member count
    const currentMemberCount = group.activeMembersCount;
    
    // If user is creator or group will have less than 1 member after leaving, delete the group
    if (isCreator || currentMemberCount <= 1) {
      // Delete the group and associated chat and all messages
      await Message.deleteMany({ chat: chat._id });
      await Chat.findByIdAndDelete(chat._id);
      await Group.findByIdAndDelete(group._id);
      
      res.json({
        success: true,
        message: isCreator ? 'Group deleted successfully (creator left)' : 'Group deleted successfully (insufficient members)',
        deleted: true
      });
    } else {
      // Normal leave - remove from both chat and group
      console.log('Removing user from chat and group:', req.user.id);
      await chat.removeParticipant(req.user.id);
      await group.removeMember(req.user.id);
      
      console.log('User successfully removed from chat and group');
      res.json({
        success: true,
        message: 'Left chat successfully',
        deleted: false
      });
    }

  } catch (error) {
    console.error('Leave chat error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while leaving chat'
    });
  }
};

// @desc    Delete chat
// @route   DELETE /api/chats/:id
// @access  Private
const deleteChat = async (req, res) => {
  try {
    const { id } = req.params;

    const chat = await Chat.findById(id);
    if (!chat || !chat.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Chat not found'
      });
    }

    // Only chat creator or admin can delete
    const userParticipant = chat.participants.find(
      p => p.user && p.user.toString() === req.user.id && p.isActive
    );

    const isCreator = chat.createdBy.toString() === req.user.id;
    const isAdmin = userParticipant;// && userParticipant.role === 'admin';

    if (!isCreator && !isAdmin) {
      return res.status(403).json({
        success: false,
        message: 'Permission denied'
      });
    }

    // For private chats, only remove the user's participation (don't delete the whole chat)
    if (chat.type === 'private') {
      // Remove current user from participants (set isActive = false)
      await chat.removeParticipant(req.user.id);
      
      res.json({
        success: true,
        message: 'Chat deleted successfully'
      });
      return;
    }

    // For group chats, handle the associated group
    if (chat.type === 'group') {
      const group = await Group.findOne({ name: chat.name, isActive: true });
      if (group) {
        // Check if user is group creator or admin (moderators cannot delete groups)
        const isGroupCreator = group.createdBy.toString() === req.user.id;
        const isGroupAdmin = group.isAdmin(req.user.id);

        if (!isGroupCreator && !isGroupAdmin) {
          return res.status(403).json({
            success: false,
            message: 'Only group creator and admins can delete group'
          });
        }

        // Soft delete group
        group.isActive = false;
        await group.save();
      }
      
      // For group chats, also remove the user from participants
      await chat.removeParticipant(req.user.id);
      
      // If no active participants left, soft delete the chat
      const activeParticipants = chat.participants.filter(p => p.isActive);
      if (activeParticipants.length === 0) {
        chat.isActive = false;
      }
      
      await chat.save();
    }

    res.json({
      success: true,
      message: 'Chat deleted successfully'
    });

  } catch (error) {
    console.error('Delete chat error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while deleting chat'
    });
  }
};


const addMember = async (req, res) => {
  try {
    const { id } = req.params;
    const { userIds } = req.body;

    if (!userIds || !Array.isArray(userIds) || userIds.length === 0) {
      return res.status(400).json({
        success: false,
        message: 'User IDs are required'
      });
    }

    const chat = await Chat.findById(id);
    if (!chat || !chat.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Chat not found'
      });
    }

    if (chat.type === 'private') {
      return res.status(400).json({
        success: false,
        message: 'Cannot add members to private chat'
      });
    }

    // Check permissions: allow if chat owner OR chat admin/mod OR group owner/admin/mod
    // First, try to find user in chat participants
    // Note: p.user is an ObjectId, so we compare as strings
    const userParticipant = chat.participants.find(p => {
      if (!p.isActive) return false;
      if (!p.user) return false;
      const userId = p.user.toString();
      return userId === req.user.id;
    });
    
    const isChatOwner = chat.createdBy && chat.createdBy.toString() === req.user.id;
    const userRole = userParticipant ? (userParticipant.role || 'member') : null;
    let hasPermission = isChatOwner || !!(userParticipant && userRole && ['admin', 'moderator'].includes(userRole));
    
    console.log('Add member permission check (chat level):', {
      requesterId: req.user.id,
      chatId: id,
      isChatOwner,
      userParticipantFound: !!userParticipant,
      userRole,
      hasPermissionFromChat: hasPermission,
      allParticipants: chat.participants.map(p => ({
        userId: p.user ? p.user.toString() : null,
        role: p.role || 'member',
        isActive: p.isActive
      }))
    });

    // If not permitted by chat roles, check group permissions
    if (!hasPermission) {
      let group = null;
      try {
        if (chat.groupId) {
          group = await Group.findById(chat.groupId);
        }
        if (!group) {
          group = await Group.findOne({ name: chat.name, isActive: true });
        }
      } catch (e) {
        console.error('Error finding group:', e);
      }

      if (group) {
        const isGroupOwner = group.createdBy && group.createdBy.toString() === req.user.id;
        const hasGroupPermission = group.hasPermission(req.user.id) || isGroupOwner;
        hasPermission = hasPermission || hasGroupPermission;
        
        console.log('Add member permission check (group level):', {
          requesterId: req.user.id,
          groupId: group._id,
          isGroupOwner,
          hasGroupPermission,
          hasPermission: hasPermission
        });
      } else {
        console.log('Group not found for chat:', { chatId: id, chatName: chat.name, groupId: chat.groupId });
      }
    }

    if (!hasPermission) {
      return res.status(403).json({
        success: false,
        message: 'Only group creator, admins, and moderators can add members'
      });
    }

    // Validate that all users exist
    const users = await User.find({ _id: { $in: userIds } });
    if (users.length !== userIds.length) {
      return res.status(400).json({
        success: false,
        message: 'Some users not found'
      });
    }

    // Find associated group
    const group = await Group.findOne({ name: chat.name, isActive: true });
    if (!group) {
      return res.status(404).json({
        success: false,
        message: 'Associated group not found'
      });
    }

    console.log('Adding members to chat:', chat.name, 'and group:', group.name);
    console.log('User IDs to add:', userIds);

    // Add/reactivate users in both chat and group
    const addedUsers = [];
    for (const userId of userIds) {
      const existingParticipant = chat.participants.find(p => p.user && p.user.toString() === userId);
      const wasActive = !!(existingParticipant && existingParticipant.isActive);

      // Always delegate to model method to add/reactivate
      await chat.addParticipant(userId, 'member');
      await group.addMember(userId, 'member');

      if (!wasActive) {
        addedUsers.push(userId);
      } else {
        console.log('User already active in chat participants:', userId);
      }
    }

    // Chat.addParticipant persists; ensure latest chat fetched in memory reflects DB, but response doesn't require it

    res.json({
      success: true,
      message: `Added ${addedUsers.length} members successfully`,
      data: {
        addedUsers,
        totalMembers: chat.participants.length
      }
    });

  } catch (error) {
    console.error('Add member error:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error'
    });
  }
};

const getGroupMembers = async (req, res) => {
  try {
    const { id } = req.params;

    const chat = await Chat.findById(id).populate('participants.user', 'username email displayName avatar isOnline lastSeen');
    if (!chat || !chat.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Chat not found'
      });
    }

    if (chat.type === 'private') {
      return res.status(400).json({
        success: false,
        message: 'Cannot get members from private chat'
      });
    }

    // Check if user is a participant
    const userParticipant = chat.participants.find(p => p.user && p.user._id && p.user._id.toString() === req.user.id && p.isActive);
    if (!userParticipant) {
      return res.status(403).json({
        success: false,
        message: 'Access denied'
      });
    }

    // Get active members only (filter out null users)
    const ownerId = chat.createdBy ? chat.createdBy.toString() : null;
    const activeMembers = chat.participants
      .filter(p => p.isActive && p.user && p.user._id)
      .map(p => ({
        id: p.user._id,
        username: p.user.username,
        email: p.user.email,
        displayName: p.user.displayName,
        avatar: p.user.avatar,
        isOnline: p.user.isOnline,
        lastSeen: p.user.lastSeen,
        role: p.role,
        joinedAt: p.joinedAt,
        isOwner: ownerId ? (p.user._id && p.user._id.toString() === ownerId) : false
      }));

    res.json({
      success: true,
      data: {
        members: activeMembers,
        totalMembers: activeMembers.length
      }
    });

  } catch (error) {
    console.error('Get group members error:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error'
    });
  }
};

const removeMember = async (req, res) => {
  try {
    const { id } = req.params;
    const { userId } = req.body;

    console.log('Remove member request:', { chatId: id, userId, requesterId: req.user.id });

    if (!userId) {
      return res.status(400).json({
        success: false,
        message: 'User ID is required'
      });
    }

    const chat = await Chat.findById(id);
    if (!chat || !chat.isActive) {
      console.log('Chat not found or inactive:', { chatId: id, chatExists: !!chat, isActive: chat?.isActive });
      return res.status(404).json({
        success: false,
        message: 'Chat not found'
      });
    }

    console.log('Chat found:', { 
      id: chat._id, 
      type: chat.type, 
      createdBy: chat.createdBy, 
      participants: chat.participants.map(p => ({ user: p.user, isActive: p.isActive }))
    });

    if (chat.type === 'private') {
      return res.status(400).json({
        success: false,
        message: 'Cannot remove members from private chat'
      });
    }

    // Check permissions: allow if chat owner OR chat admin/mod OR group owner/admin/mod
    const userParticipant = chat.participants.find(p => p.user && p.user.toString() === req.user.id && p.isActive);
    const isChatOwner = chat.createdBy && chat.createdBy.toString() === req.user.id;
    let hasPermission = isChatOwner || !!(userParticipant && ['admin', 'moderator'].includes(userParticipant.role));

    // If not permitted by chat roles, check group permissions
    if (!hasPermission) {
      let group = null;
      try {
        if (chat.groupId) {
          group = await Group.findById(chat.groupId);
        }
        if (!group) {
          group = await Group.findOne({ name: chat.name, isActive: true });
        }
      } catch (e) {}

      if (group) {
        const isGroupOwner = group.createdBy && group.createdBy.toString() === req.user.id;
        const hasGroupPermission = group.hasPermission(req.user.id) || isGroupOwner;
        hasPermission = hasPermission || hasGroupPermission;
      }
    }

    if (!hasPermission) {
      console.log('Permission denied:', { 
        chatCreatedBy: chat.createdBy ? chat.createdBy.toString() : null, 
        requesterId: req.user.id,
        userParticipant: userParticipant ? { role: userParticipant.role, isActive: userParticipant.isActive } : null
      });
      return res.status(403).json({ success: false, message: 'Permission denied' });
    }

    // Cannot remove creator
    if (userId === chat.createdBy.toString()) {
      return res.status(400).json({
        success: false,
        message: 'Cannot remove group creator'
      });
    }

    // Find and deactivate user in chat
    const participantToRemove = chat.participants.find(p => p.user && p.user.toString() === userId && p.isActive);
    
    if (!participantToRemove) {
      console.log('User not found in group:', { userId, participants: chat.participants.map(p => p.user ? p.user.toString() : 'null') });
      return res.status(400).json({
        success: false,
        message: 'User is not a member of this group'
      });
    }
    
    // Deactivate user instead of removing completely
    participantToRemove.isActive = false;
    participantToRemove.leftAt = new Date();
    
    console.log('User deactivated from group:', { 
      userId, 
      participant: participantToRemove,
      activeParticipants: chat.participants.filter(p => p.isActive).length
    });

    await chat.save();

    // Also reflect removal in associated Group to keep membership logic consistent
    try {
      let group = null;
      if (chat.groupId) {
        group = await Group.findById(chat.groupId);
      }
      // Fallback by name if groupId is not linked in Chat (legacy data)
      if (!group) {
        group = await Group.findOne({ name: chat.name, isActive: true });
      }
      if (group) {
        await group.removeMember(userId);
      } else {
        console.warn('Associated group not found when removing member from chat', { chatId: chat._id, name: chat.name });
      }
    } catch (e) {
      console.error('Failed to sync Group membership on removeMember:', e);
      // Do not fail the whole operation; chat state is already updated
    }

    // Get io instance and emit events
    const io = req.app.get('io');
    if (io) {
      // Notify the removed user that they were removed
      io.to(`user_${userId}`).emit('member_removed', {
        chatId: chat._id,
        chatName: chat.name,
        message: 'You have been removed from the group'
      });

      // Notify remaining active members about the removal
      const activeParticipants = chat.participants.filter(p => p.isActive);
      activeParticipants.forEach(participant => {
        if (participant.user && participant.user._id) {
          io.to(`user_${participant.user._id}`).emit('member_removed_from_group', {
            chatId: chat._id,
            chatName: chat.name,
            removedUserId: userId,
            totalMembers: activeParticipants.length
          });
        }
      });
    }

    res.json({
      success: true,
      message: 'Member removed successfully',
      data: {
        removedUser: userId,
        totalMembers: chat.participants.filter(p => p.isActive).length
      }
    });

  } catch (error) {
    console.error('Remove member error:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error'
    });
  }
};

const updateMemberRole = async (req, res) => {
  try {
    const { id } = req.params;
    const { userId, role } = req.body;

    console.log('Update member role request:', { chatId: id, userId, role, requesterId: req.user.id });

    if (!userId) {
      return res.status(400).json({
        success: false,
        message: 'User ID is required'
      });
    }

    if (!role || !['admin', 'moderator', 'member'].includes(role)) {
      return res.status(400).json({
        success: false,
        message: 'Valid role is required (admin, moderator, or member)'
      });
    }

    const chat = await Chat.findById(id);
    if (!chat || !chat.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Chat not found'
      });
    }

    if (chat.type === 'private') {
      return res.status(400).json({
        success: false,
        message: 'Cannot change roles in private chat'
      });
    }

    // Check permissions: only chat owner can change roles
    const isChatOwner = chat.createdBy && chat.createdBy.toString() === req.user.id;
    
    // Also check group owner
    let isGroupOwner = false;
    let group = null;
    try {
      if (chat.groupId) {
        group = await Group.findById(chat.groupId);
      }
      if (!group) {
        group = await Group.findOne({ name: chat.name, isActive: true });
      }
      if (group) {
        isGroupOwner = group.createdBy && group.createdBy.toString() === req.user.id;
      }
    } catch (e) {
      console.error('Error finding group:', e);
    }

    if (!isChatOwner && !isGroupOwner) {
      return res.status(403).json({
        success: false,
        message: 'Only group owner can change member roles'
      });
    }

    // Cannot change owner's role
    if (userId === chat.createdBy.toString()) {
      return res.status(400).json({
        success: false,
        message: 'Cannot change owner role'
      });
    }

    // Find participant and update role
    const participant = chat.participants.find(p => p.user && p.user.toString() === userId && p.isActive);
    
    if (!participant) {
      return res.status(404).json({
        success: false,
        message: 'User is not a member of this group'
      });
    }

    const oldRole = participant.role;
    participant.role = role;
    await chat.save();

    // Also update role in associated Group
    if (group) {
      const groupMember = group.members.find(m => m.user && m.user.toString() === userId && m.isActive);
      if (groupMember) {
        groupMember.role = role;
        await group.save();
      }
    }

    // Get io instance and emit events
    const io = req.app.get('io');
    if (io) {
      // Notify the user whose role was changed
      io.to(`user_${userId}`).emit('member_role_changed', {
        chatId: chat._id,
        chatName: chat.name,
        newRole: role,
        oldRole: oldRole
      });

      // Notify all members about the role change
      const activeParticipants = chat.participants.filter(p => p.isActive);
      activeParticipants.forEach(p => {
        if (p.user && p.user._id) {
          io.to(`user_${p.user._id}`).emit('member_role_updated', {
            chatId: chat._id,
            userId: userId,
            newRole: role,
            oldRole: oldRole
          });
        }
      });
    }

    res.json({
      success: true,
      message: 'Member role updated successfully',
      data: {
        userId: userId,
        oldRole: oldRole,
        newRole: role
      }
    });

  } catch (error) {
    console.error('Update member role error:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error'
    });
  }
};

const uploadGroupAvatar = async (req, res) => {
  try {
    const { id } = req.params;
    console.log('Upload group avatar request:', { id, userId: req.user.id });

    if (!req.file) {
      return res.status(400).json({
        success: false,
        message: 'No image file provided'
      });
    }

    const chat = await Chat.findById(id);
    if (!chat || !chat.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Chat not found'
      });
    }

    console.log('Chat found:', { 
      id: chat._id, 
      type: chat.type, 
      createdBy: chat.createdBy,
      isActive: chat.isActive,
      participantCount: chat.participants.length
    });

    if (chat.type === 'private') {
      return res.status(400).json({
        success: false,
        message: 'Cannot upload avatar for private chat'
      });
    }

    // Check if user is a participant in the group
    const isParticipant = chat.participants.some(participant => 
      participant.user && participant.user.toString() === req.user.id
    );
    
    if (!isParticipant) {
      console.log('Permission denied - not a participant:', { 
        chatId: chat._id,
        userId: req.user.id,
        participants: chat.participants.map(p => p.user ? p.user.toString() : 'null')
      });
      return res.status(403).json({
        success: false,
        message: 'Only group members can upload avatar'
      });
    }

    // Delete old avatar if exists
    if (chat.avatar) {
      const fs = require('fs');
      const path = require('path');
      const oldAvatarPath = path.join(__dirname, '../uploads/avatars', path.basename(chat.avatar));
      if (fs.existsSync(oldAvatarPath)) {
        fs.unlinkSync(oldAvatarPath);
      }
    }

    // Update chat avatar
    chat.avatar = `/uploads/avatars/${req.file.filename}`;
    await chat.save();

    console.log('Avatar uploaded successfully:', { 
      chatId: chat._id, 
      filename: req.file.filename 
    });

    res.json({
      success: true,
      message: 'Group avatar uploaded successfully',
      data: {
        avatarUrl: chat.avatar
      }
    });

  } catch (error) {
    console.error('Upload group avatar error:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error'
    });
  }
};

// @desc    Delete chat and all messages (admin only)
// @route   DELETE /api/chats/:id/admin
// @access  Private (Admin)
const deleteChatAdmin = async (req, res) => {
  try {
    const { id } = req.params;

    // Find chat
    const chat = await Chat.findById(id);
    if (!chat || !chat.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Chat not found'
      });
    }

    // Delete all messages in this chat
    await Message.deleteMany({ chat: id });

    // Delete the chat
    await Chat.findByIdAndDelete(id);

    res.json({
      success: true,
      message: 'Chat and all messages deleted successfully'
    });

  } catch (error) {
    console.error('Delete chat admin error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while deleting chat'
    });
  }
};

// @desc    Update group chat settings
// @route   PUT /api/chats/:id/settings
// @access  Private
const updateGroupChatSettings = async (req, res) => {
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
    const { settings } = req.body;

    const chat = await Chat.findById(id);
    if (!chat || !chat.isActive || chat.type !== 'group') {
      return res.status(404).json({
        success: false,
        message: 'Group chat not found'
      });
    }

    // Check if user is creator or admin
    const isCreator = chat.createdBy && chat.createdBy.toString() === req.user.id;
    const userParticipant = chat.participants.find(
      p => p.user && p.user.toString() === req.user.id && p.isActive
    );
    const isAdmin = userParticipant && userParticipant.role === 'admin';

    if (!isCreator && !isAdmin) {
      return res.status(403).json({
        success: false,
        message: 'Only group creator or admins can update settings'
      });
    }

    // Update chat settings
    if (settings) {
      chat.settings = { ...chat.settings, ...settings };
    }

    await chat.updateLastActivity();

    // Also update associated group if exists
    const group = await Group.findOne({ name: chat.name, isActive: true });
    if (group) {
      if (settings) {
        group.settings = { ...group.settings, ...settings };
      }
      await group.updateLastActivity();
    }

    res.json({
      success: true,
      message: 'Group chat settings updated successfully',
      data: {
        chat,
        group: group || null
      }
    });

  } catch (error) {
    console.error('Update group chat settings error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while updating group chat settings'
    });
  }
};

// @desc    Transfer group ownership to another user (via chat ID)
// @route   PUT /api/chats/:id/owner
// @access  Private (Admin or current owner)
const transferOwnership = async (req, res) => {
  try {
    const { id } = req.params; // chat ID
    const { newOwnerId } = req.body;

    if (!newOwnerId) {
      return res.status(400).json({ success: false, message: 'newOwnerId is required' });
    }

    // Find chat
    const chat = await Chat.findById(id);
    if (!chat || !chat.isActive || chat.type !== 'group') {
      return res.status(404).json({ success: false, message: 'Group chat not found' });
    }

    // Find associated group
    const group = await Group.findOne({ name: chat.name, isActive: true });
    if (!group) {
      return res.status(404).json({ success: false, message: 'Associated group not found' });
    }

    // Only global admin or current owner can transfer ownership
    const isCreator = group.createdBy && group.createdBy.toString() === req.user.id;
    if (req.user.role !== 'admin' && !isCreator) {
      return res.status(403).json({ success: false, message: 'Permission denied' });
    }

    // Validate new owner
    const newOwner = await User.findById(newOwnerId);
    if (!newOwner || !newOwner.isActive) {
      return res.status(404).json({ success: false, message: 'New owner not found or inactive' });
    }

    // Store old owner ID before changing
    const oldOwnerId = group.createdBy ? group.createdBy.toString() : null;

    // Ensure new owner is a member and set role to admin
    await group.addMember(newOwnerId, 'admin');

    // Set old owner as moderator if they exist and are different from new owner
    if (oldOwnerId && oldOwnerId !== newOwnerId) {
      const oldOwnerMember = group.members.find(m => m.user && m.user.toString() === oldOwnerId);
      if (oldOwnerMember) {
        oldOwnerMember.role = 'moderator';
      } else {
        // If old owner is not in members list, add them as moderator
        await group.addMember(oldOwnerId, 'moderator');
      }
    }

    // Update creator
    group.createdBy = newOwnerId;
    await group.save();

    // Update chat's createdBy and ensure new owner is admin participant
    // Also set old owner as moderator in chat participants
    chat.createdBy = newOwnerId;
    
    // Ensure participant record exists for new owner and set role to admin
    const newOwnerParticipant = chat.participants.find(x => x.user && x.user.toString() === newOwnerId.toString());
    if (newOwnerParticipant) {
      newOwnerParticipant.role = 'admin';
      newOwnerParticipant.isActive = true;
    } else {
      chat.participants.push({ user: newOwnerId, role: 'admin', isActive: true });
    }
    
    // Set old owner as moderator in chat participants
    if (oldOwnerId && oldOwnerId !== newOwnerId) {
      const oldOwnerParticipant = chat.participants.find(x => x.user && x.user.toString() === oldOwnerId.toString());
      if (oldOwnerParticipant) {
        oldOwnerParticipant.role = 'moderator';
        oldOwnerParticipant.isActive = true;
      } else {
        chat.participants.push({ user: oldOwnerId, role: 'moderator', isActive: true });
      }
    }
    
    await chat.save();

    return res.json({ success: true, message: 'Ownership transferred successfully', data: { group, chat } });
  } catch (error) {
    console.error('Transfer ownership error:', error);
    return res.status(500).json({ success: false, message: 'Server error while transferring ownership' });
  }
};

module.exports = {
  getChats,
  getAllChats,
  getChatById,
  createPrivateChat,
  createGroupChat,
  updateGroupChat,
  updateGroupChatSettings,
  addParticipant,
  removeParticipant,
  leaveChat,
  deleteChat,
  addMember,
  getGroupMembers,
  removeMember,
  updateMemberRole,
  uploadGroupAvatar,
  deleteChatAdmin,
  transferOwnership
};
