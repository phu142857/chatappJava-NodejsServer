const User = require('../models/User');
const FriendRequest = require('../models/FriendRequest');
const Chat = require('../models/Chat');
const Message = require('../models/Message');
const AuditLog = require('../models/AuditLog');
const { validationResult } = require('express-validator');

// @desc    Get all users (admin view supports inactive)
// @route   GET /api/users
// @access  Private
const getUsers = async (req, res) => {
  try {
    const { search, page = 1, limit = 20, includeInactive } = req.query;
    const skip = (page - 1) * limit;

    let query;
    const isAdmin = req.user && (req.user.role === 'admin' || req.user.role === 'moderator');

    if (isAdmin) {
      // Admin/mod: can view everyone
      query = { _id: { $ne: undefined } };
      // Default: only active unless includeInactive is truthy
      if (!includeInactive || includeInactive === 'false') {
        query.isActive = true;
      }
    } else {
      // Regular user: only own profile regardless of includeInactive/search
      query = { _id: req.user.id };
    }

    // Add search functionality
    if (search && isAdmin) {
      query.$or = [
        { username: { $regex: search, $options: 'i' } },
        { email: { $regex: search, $options: 'i' } },
        { 'profile.firstName': { $regex: search, $options: 'i' } },
        { 'profile.lastName': { $regex: search, $options: 'i' } }
      ];
    }

    const users = await User.find(query)
      .select('-password')
      .sort({ lastSeen: -1 })
      .skip(skip)
      .limit(parseInt(limit));

    const total = await User.countDocuments(query);

    res.json({
      success: true,
      data: {
        users,
        pagination: {
          page: parseInt(page),
          limit: parseInt(limit),
          total,
          pages: Math.ceil(total / limit)
        }
      }
    });

  } catch (error) {
    console.error('Get users error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching users'
    });
  }
};

// @desc    Activate/Deactivate (ban/unban) a user
// @route   PUT /api/users/:id/active
// @access  Private (Admin - note: add proper role checks if available)
const setActive = async (req, res) => {
  try {
    const { id } = req.params;
    const { isActive, reason } = req.body;
    const adminId = req.user.id;

    if (typeof isActive !== 'boolean') {
      return res.status(400).json({ success: false, message: 'isActive must be boolean' });
    }

    const user = await User.findById(id);
    if (!user) {
      return res.status(404).json({ success: false, message: 'User not found' });
    }

    user.isActive = isActive;
    if (!isActive) {
      user.status = 'offline';
    }
    await user.save();

    // Log the action
    const action = isActive ? 'UNLOCK_USER' : 'LOCK_USER';
    const details = reason ? 
      `${isActive ? 'Unlocked' : 'Locked'} user ${user.username}. Reason: ${reason}` :
      `${isActive ? 'Unlocked' : 'Locked'} user ${user.username}`;
    
    await logAuditAction(adminId, action, 'User', details, req.ip);

    return res.json({ 
      success: true, 
      message: `User ${isActive ? 'unlocked' : 'locked'} successfully`, 
      data: { user: user.toJSON() } 
    });
  } catch (error) {
    console.error('Set active error:', error);
    return res.status(500).json({ success: false, message: 'Server error while updating user active state' });
  }
};

// @desc    Get user by ID
// @route   GET /api/users/:id
// @access  Private
const getUserById = async (req, res) => {
  try {
    const { id } = req.params;

    const user = await User.findById(id)
      .select('-password');

    if (!user || !user.isActive) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    res.json({
      success: true,
      data: {
        user
      }
    });

  } catch (error) {
    console.error('Get user by ID error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching user'
    });
  }
};

// @desc    Update user status
// @route   PUT /api/users/status
// @access  Private
const updateStatus = async (req, res) => {
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

    const { status } = req.body;
    const user = await User.findById(req.user.id);

    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    user.status = status;
    await user.updateLastSeen();

    res.json({
      success: true,
      message: 'Status updated successfully',
      data: {
        status: user.status,
        lastSeen: user.lastSeen
      }
    });

  } catch (error) {
    console.error('Update status error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while updating status'
    });
  }
};

// @desc    Get user's contacts/friends
// @route   GET /api/users/contacts
// @access  Private
const getContacts = async (req, res) => {
  try {
    // Get all chats where user is a participant
    const chats = await Chat.find({
      'participants.user': req.user.id,
      'participants.isActive': true,
      isActive: true
    }).populate('participants.user', 'username email avatar status lastSeen');

    // Extract unique contacts from chats
    const contactsMap = new Map();
    
    chats.forEach(chat => {
      chat.participants.forEach(participant => {
        if (participant.user._id.toString() !== req.user.id && participant.isActive) {
          const userId = participant.user._id.toString();
          if (!contactsMap.has(userId)) {
            contactsMap.set(userId, {
              ...participant.user.toJSON(),
              chatId: chat._id,
              chatType: chat.type
            });
          }
        }
      });
    });

    const contacts = Array.from(contactsMap.values())
      .sort((a, b) => new Date(b.lastSeen) - new Date(a.lastSeen));

    res.json({
      success: true,
      data: {
        contacts
      }
    });

  } catch (error) {
    console.error('Get contacts error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching contacts'
    });
  }
};

// @desc    Search users for adding to chat
// @route   GET /api/users/search
// @access  Private
const searchUsers = async (req, res) => {
  try {
    const { q, exclude } = req.query;

    if (!q || q.trim().length < 2) {
      return res.status(400).json({
        success: false,
        message: 'Search query must be at least 2 characters'
      });
    }

    let excludeIds = [req.user.id];
    if (exclude) {
      const excludeArray = exclude.split(',');
      excludeIds = [...excludeIds, ...excludeArray];
    }

    const users = await User.find({
      $and: [
        { isActive: true },
        { _id: { $nin: excludeIds } },
        {
          $or: [
            { username: { $regex: q.trim(), $options: 'i' } },
            { email: { $regex: q.trim(), $options: 'i' } },
            { 'profile.firstName': { $regex: q.trim(), $options: 'i' } },
            { 'profile.lastName': { $regex: q.trim(), $options: 'i' } }
          ]
        }
      ]
    })
    .select('username email avatar status profile friends')
    .limit(20)
    .sort({ username: 1 });

    // Get current user to check friendship status
    const currentUser = await User.findById(req.user.id).select('friends');
    const currentUserFriends = currentUser ? currentUser.friends : [];

    // Fetch pending friend requests involving current user
    const pendingRequests = await FriendRequest.find({
      status: 'pending',
      $or: [
        { senderId: req.user.id },
        { receiverId: req.user.id }
      ]
    }).select('senderId receiverId status');

    const pendingSentToIds = new Set(
      pendingRequests
        .filter(r => r.senderId.toString() === req.user.id)
        .map(r => r.receiverId.toString())
    );
    const pendingReceivedFromIds = new Set(
      pendingRequests
        .filter(r => r.receiverId.toString() === req.user.id)
        .map(r => r.senderId.toString())
    );

    // Add friendship status to each user
    // Build maps for quick lookup of pending request ids
    const pendingSentMap = new Map(); // key: receiverId -> requestId
    const pendingReceivedMap = new Map(); // key: senderId -> requestId
    pendingRequests.forEach(r => {
      if (r.senderId.toString() === req.user.id) {
        pendingSentMap.set(r.receiverId.toString(), r._id.toString());
      } else if (r.receiverId.toString() === req.user.id) {
        pendingReceivedMap.set(r.senderId.toString(), r._id.toString());
      }
    });

    const usersWithFriendshipStatus = users.map(user => {
      const userObj = user.toJSON();
      const isFriend = currentUserFriends.some(friendId => 
        friendId.toString() === user._id.toString()
      );
      let friendRequestStatus = 'none';
      let friendRequestId = undefined;
      if (!isFriend) {
        if (pendingReceivedFromIds.has(user._id.toString())) {
          friendRequestStatus = 'received';
          friendRequestId = pendingReceivedMap.get(user._id.toString());
        } else if (pendingSentToIds.has(user._id.toString())) {
          friendRequestStatus = 'sent';
          friendRequestId = pendingSentMap.get(user._id.toString());
        }
      }
      
      return {
        ...userObj,
        isFriend,
        friendshipStatus: isFriend ? 'friends' : 'not_friends',
        friendRequestStatus,
        friendRequestId
      };
    });

    res.json({
      success: true,
      data: {
        users: usersWithFriendshipStatus
      }
    });

  } catch (error) {
    console.error('Search users error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while searching users'
    });
  }
};

// @desc    Get online users
// @route   GET /api/users/online
// @access  Private
const getOnlineUsers = async (req, res) => {
  try {
    const users = await User.find({
      status: 'online',
      isActive: true,
      _id: { $ne: req.user.id }
    })
    .select('username email avatar status lastSeen')
    .sort({ lastSeen: -1 })
    .limit(50);

    res.json({
      success: true,
      data: {
        users
      }
    });

  } catch (error) {
    console.error('Get online users error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching online users'
    });
  }
};

// @desc    Block/Unblock user
// @route   PUT /api/users/:id/block
// @access  Private
const toggleBlockUser = async (req, res) => {
  try {
    const { id } = req.params;
    const { action } = req.body; // 'block' or 'unblock'

    if (id === req.user.id) {
      return res.status(400).json({
        success: false,
        message: 'Cannot block yourself'
      });
    }

    const targetUser = await User.findById(id);
    if (!targetUser) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    // Note: Implement blocking logic based on your requirements
    // This could involve adding a blocked users array to the User model
    // or implementing a separate BlockedUsers collection

    res.json({
      success: true,
      message: `User ${action}ed successfully`
    });

  } catch (error) {
    console.error('Toggle block user error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while updating block status'
    });
  }
};

// @desc    Report user
// @route   POST /api/users/:id/report
// @access  Private
const reportUser = async (req, res) => {
  try {
    const { id } = req.params;
    const { reason, description } = req.body;

    if (id === req.user.id) {
      return res.status(400).json({
        success: false,
        message: 'Cannot report yourself'
      });
    }

    const targetUser = await User.findById(id);
    if (!targetUser) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    // Note: Implement reporting logic
    // This could involve creating a UserReports collection
    // and storing the report details

    res.json({
      success: true,
      message: 'User reported successfully'
    });

  } catch (error) {
    console.error('Report user error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while reporting user'
    });
  }
};

// @desc    Get user friends
// @route   GET /api/users/friends
// @access  Private
const getUserFriends = async (req, res) => {
  try {
    const userId = req.user.id;
    const user = await User.findById(userId)
      .populate('friends', 'username email avatar profile')
      .select('-password');
    
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }
    
    res.json({
      success: true,
      data: { 
        friends: user.friends || [],
        friendsCount: (user.friends || []).length
      }
    });
  } catch (error) {
    console.error('Get user friends error:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error'
    });
  }
};

// @desc    Unfriend a user (remove friendship both ways)
// @route   DELETE /api/users/:id/friends
// @access  Private
const removeFriend = async (req, res) => {
  try {
    const { id: otherUserId } = req.params;
    const currentUserId = req.user.id;

    if (otherUserId === currentUserId) {
      return res.status(400).json({ success: false, message: 'Cannot unfriend yourself' });
    }

    const currentUser = await User.findById(currentUserId);
    const otherUser = await User.findById(otherUserId);

    if (!currentUser || !otherUser) {
      return res.status(404).json({ success: false, message: 'User not found' });
    }

    await currentUser.removeFriend(otherUserId);
    await otherUser.removeFriend(currentUserId);

    return res.json({ success: true, message: 'Unfriended successfully' });
  } catch (error) {
    console.error('Remove friend error:', error);
    return res.status(500).json({ success: false, message: 'Server error while unfriending' });
  }
};


// Helper function to log audit actions
const logAuditAction = async (userId, action, resource, details, ipAddress) => {
  try {
    const auditLog = new AuditLog({
      user: userId,
      action,
      resource,
      details,
      ipAddress
    });
    await auditLog.save();
  } catch (error) {
    console.error('Error logging audit action:', error);
  }
};

module.exports = {
  getUsers,
  getUserById,
  updateStatus,
  getContacts,
  searchUsers,
  getOnlineUsers,
  toggleBlockUser,
  reportUser,
  getUserFriends,
  removeFriend,
  setActive
};
