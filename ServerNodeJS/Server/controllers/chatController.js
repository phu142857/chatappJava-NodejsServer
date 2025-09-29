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

    // Filter out chats with null participants (deleted users)
    const validChats = chats.filter(chat => {
      if (chat.type === 'private') {
        // For private chats, ensure both participants exist
        return chat.participants.every(p => p.user && p.user._id);
      }
      return true; // Group chats can have some null participants
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

        // Format chat data for private chats
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
              avatar: otherParticipant.user.avatar,
              status: otherParticipant.user.status
            };
          } else {
            // Handle case where other participant user is null/deleted
            chatData.name = 'Unknown User';
            chatData.otherParticipant = {
              id: null,
              username: 'Unknown User',
              email: '',
              avatar: '',
              status: 'offline'
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
      p => p.user._id.toString() === req.user.id && p.isActive
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

    // Check if participant exists
    const participant = await User.findById(participantId);
    if (!participant || !participant.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Participant not found'
      });
    }

    // Check if private chat already exists
    const existingChat = await Chat.findPrivateChat(req.user.id, participantId);
    if (existingChat) {
      return res.status(200).json({
        success: true,
        message: 'Private chat already exists',
        data: {
          chat: existingChat
        }
      });
    }

    // Create new private chat
    const chat = new Chat({
      type: 'private',
      participants: [
        { user: req.user.id, role: 'member' },
        { user: participantId, role: 'member' }
      ],
      createdBy: req.user.id
    });

    await chat.save();

    // Populate participants
    await chat.populate('participants.user', 'username email avatar status');

    res.status(201).json({
      success: true,
      message: 'Private chat created successfully',
      data: {
        chat
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

    const { name, description, participantIds, avatar } = req.body;

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
      createdBy: req.user.id
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

    // Check if user is admin
    const userParticipant = chat.participants.find(
      p => p.user.toString() === req.user.id && p.isActive
    );

    if (!userParticipant || userParticipant.role !== 'admin') {
      return res.status(403).json({
        success: false,
        message: 'Only admins can update group chat'
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

    // Check if user is admin in group
    if (!group.isAdmin(req.user.id)) {
      return res.status(403).json({
        success: false,
        message: 'Only group admins can update group chat'
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
      p => p.user.toString() === req.user.id && p.isActive
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

    // Add participant to both chat and group
    await chat.addParticipant(participantId);
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

    // Check permissions
    const userParticipant = chat.participants.find(
      p => p.user.toString() === req.user.id && p.isActive
    );

    const isRemovingSelf = participantId === req.user.id;
    const hasPermission = userParticipant && ['admin', 'moderator'].includes(userParticipant.role);

    if (!isRemovingSelf && !hasPermission) {
      return res.status(403).json({
        success: false,
        message: 'Permission denied'
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

    // Check permissions in group
    const isRemovingSelfFromGroup = participantId === req.user.id;
    const hasGroupPermission = group.hasPermission(req.user.id);

    if (!isRemovingSelfFromGroup && !hasGroupPermission) {
      return res.status(403).json({
        success: false,
        message: 'Permission denied in group'
      });
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
      p => p.user.toString() === req.user.id && p.isActive
    );

    const isCreator = chat.createdBy.toString() === req.user.id;
    const isAdmin = userParticipant;// && userParticipant.role === 'admin';

    if (!isCreator && !isAdmin) {
      return res.status(403).json({
        success: false,
        message: 'Permission denied'
      });
    }

    // If it's a group chat, also handle the associated group
    if (chat.type === 'group') {
      const group = await Group.findOne({ name: chat.name, isActive: true });
      if (group) {
        // Check if user is group creator or admin
        const isGroupCreator = group.createdBy.toString() === req.user.id;
        const isGroupAdmin = group.isAdmin(req.user.id);

        if (!isGroupCreator && !isGroupAdmin) {
          return res.status(403).json({
            success: false,
            message: 'Permission denied in group'
          });
        }

        // Soft delete group
        group.isActive = false;
        await group.save();
      }
    }

    // Soft delete chat
    chat.isActive = false;
    await chat.save();

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

    // Check if user is admin or creator
    if (chat.createdBy.toString() !== req.user.id) {
      return res.status(403).json({
        success: false,
        message: 'Only group creator can add members'
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

    // Add users to both chat and group
    const addedUsers = [];
    for (const userId of userIds) {
      // Check if user is already a participant in chat
      const existingParticipant = chat.participants.find(p => p.user.toString() === userId);
      if (!existingParticipant) {
        console.log('Adding user to chat participants:', userId);
        chat.participants.push({
          user: userId,
          role: 'member',
          joinedAt: new Date(),
          isActive: true
        });
        addedUsers.push(userId);
      } else {
        console.log('User already in chat participants:', userId);
      }
      
      // Also add to group if not already a member
      if (!group.isMember(userId)) {
        console.log('Adding user to group members:', userId);
        await group.addMember(userId, 'member');
      } else {
        console.log('User already in group members:', userId);
      }
    }

    await chat.save();

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
    const userParticipant = chat.participants.find(p => p.user._id.toString() === req.user.id && p.isActive);
    if (!userParticipant) {
      return res.status(403).json({
        success: false,
        message: 'Access denied'
      });
    }

    // Get active members only
    const activeMembers = chat.participants
      .filter(p => p.isActive)
      .map(p => ({
        id: p.user._id,
        username: p.user.username,
        email: p.user.email,
        displayName: p.user.displayName,
        avatar: p.user.avatar,
        isOnline: p.user.isOnline,
        lastSeen: p.user.lastSeen,
        role: p.role,
        joinedAt: p.joinedAt
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

    // Check if user is admin or creator
    const userParticipant = chat.participants.find(p => p.user.toString() === req.user.id && p.isActive);
    if (!userParticipant || !['admin', 'moderator'].includes(userParticipant.role)) {
      console.log('Permission denied:', { 
        chatCreatedBy: chat.createdBy.toString(), 
        requesterId: req.user.id,
        userParticipant: userParticipant ? { role: userParticipant.role, isActive: userParticipant.isActive } : null
      });
      return res.status(403).json({
        success: false,
        message: 'Only group admins and moderators can remove members'
      });
    }

    // Cannot remove creator
    if (userId === chat.createdBy.toString()) {
      return res.status(400).json({
        success: false,
        message: 'Cannot remove group creator'
      });
    }

    // Remove user from chat
    const initialLength = chat.participants.length;
    console.log('Before removal:', { 
      initialLength, 
      participants: chat.participants.map(p => ({ user: p.user.toString(), isActive: p.isActive })),
      userIdToRemove: userId
    });
    
    chat.participants = chat.participants.filter(p => p.user.toString() !== userId);
    
    console.log('After removal:', { 
      finalLength: chat.participants.length, 
      removed: initialLength !== chat.participants.length 
    });
    
    if (chat.participants.length === initialLength) {
      console.log('User not found in group:', { userId, participants: chat.participants.map(p => p.user.toString()) });
      return res.status(400).json({
        success: false,
        message: 'User is not a member of this group'
      });
    }

    await chat.save();

    res.json({
      success: true,
      message: 'Member removed successfully',
      data: {
        removedUser: userId,
        totalMembers: chat.participants.length
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
      participant.user.toString() === req.user.id
    );
    
    if (!isParticipant) {
      console.log('Permission denied - not a participant:', { 
        chatId: chat._id,
        userId: req.user.id,
        participants: chat.participants.map(p => p.user.toString())
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

module.exports = {
  getChats,
  getAllChats,
  getChatById,
  createPrivateChat,
  createGroupChat,
  updateGroupChat,
  addParticipant,
  removeParticipant,
  leaveChat,
  deleteChat,
  addMember,
  getGroupMembers,
  removeMember,
  uploadGroupAvatar,
  deleteChatAdmin
};
