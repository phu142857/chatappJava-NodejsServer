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

    // Add search functionality (support username/email/name and exact UUID)
    if (search && isAdmin) {
      const orConditions = [
        { username: { $regex: search, $options: 'i' } },
        { email: { $regex: search, $options: 'i' } },
        { 'profile.firstName': { $regex: search, $options: 'i' } },
        { 'profile.lastName': { $regex: search, $options: 'i' } }
      ];

      try {
        // If search looks like a valid ObjectId, also allow exact _id match
        const mongoose = require('mongoose');
        if (mongoose.Types.ObjectId.isValid(search)) {
          orConditions.push({ _id: search });
        }
      } catch (_) {}

      query.$or = orConditions;
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
    }).populate('participants.user', 'username email avatar status lastSeen blockedUsers');

    // Extract unique contacts from chats
    const contactsMap = new Map();
    
    chats.forEach(chat => {
      chat.participants.forEach(participant => {
        if (participant.user && participant.user._id && participant.user._id.toString() !== req.user.id && participant.isActive) {
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

    // Filter out blocked contacts (either direction)
    const me = await User.findById(req.user.id).select('blockedUsers');
    const myBlocked = new Set((me && me.blockedUsers ? me.blockedUsers : []).map(id => id.toString()));
    const contacts = Array.from(contactsMap.values())
      .filter(c => {
        const otherId = c._id.toString();
        // Exclude if I blocked them
        if (myBlocked.has(otherId)) return false;
        // Exclude if they blocked me
        const theirBlocked = (c.blockedUsers || []).map(id => id.toString());
        return !theirBlocked.includes(req.user.id);
      })
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
        // Exclude users blocked by current user or who have blocked current user
        { _id: { $nin: (await User.findById(req.user.id).select('blockedUsers')).blockedUsers } },
        { blockedUsers: { $ne: req.user.id } },
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

// @desc    Get my blocked users
// @route   GET /api/users/blocked
// @access  Private
const getBlockedUsers = async (req, res) => {
  try {
    const me = await User.findById(req.user.id).select('blockedUsers');
    if (!me) {
      return res.status(404).json({ success: false, message: 'User not found' });
    }
    const users = await User.find({ _id: { $in: me.blockedUsers } })
      .select('username email avatar status profile lastSeen');
    res.json({ success: true, data: { users } });
  } catch (error) {
    console.error('Get blocked users error:', error);
    res.status(500).json({ success: false, message: 'Server error while fetching blocked users' });
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
      return res.status(400).json({ success: false, message: 'Cannot block yourself' });
    }

    const currentUser = await User.findById(req.user.id);
    const targetUser = await User.findById(id);
    if (!currentUser || !targetUser) {
      return res.status(404).json({ success: false, message: 'User not found' });
    }

    if (action === 'block') {
      // Add to blockedUsers (one-way is enough to enforce both-direction restrictions)
      if (!currentUser.blockedUsers.some(u => u.toString() === id)) {
        currentUser.blockedUsers.push(id);
        await currentUser.save();
      }

      // Remove friendship both directions if exists
      await currentUser.removeFriend(targetUser._id);
      await targetUser.removeFriend(currentUser._id);

      // Do NOT delete messages; block only restricts new messages and discovery
      return res.json({ success: true, message: 'User blocked, friendship removed' });
    }

    if (action === 'unblock') {
      currentUser.blockedUsers = currentUser.blockedUsers.filter(u => u.toString() !== id);
      await currentUser.save();
      return res.json({ success: true, message: 'User unblocked' });
    }

    return res.status(400).json({ success: false, message: 'Invalid action' });
  } catch (error) {
    console.error('Toggle block user error:', error);
    res.status(500).json({ success: false, message: 'Server error while updating block status' });
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

// @desc    Get friends of a specific user by ID
// @route   GET /api/users/:id/friends
// @access  Private
const getFriendsByUserId = async (req, res) => {
  try {
    const { id } = req.params;

    const user = await User.findById(id)
      .populate('friends', 'username email avatar profile status lastSeen')
      .select('_id friends isActive');

    if (!user || !user.isActive) {
      return res.status(404).json({ success: false, message: 'User not found' });
    }

    return res.json({
      success: true,
      data: {
        friends: user.friends || [],
        friendsCount: (user.friends || []).length
      }
    });
  } catch (error) {
    console.error('Get friends by user id error:', error);
    return res.status(500).json({ success: false, message: 'Internal server error' });
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

// @desc    ADMIN: Add friendship between two users
// @route   POST /api/users/admin/friendship
// @access  Private (Admin)
const adminAddFriendship = async (req, res) => {
  try {
    const { userId1, userId2 } = req.body;

    if (!userId1 || !userId2) {
      return res.status(400).json({ success: false, message: 'userId1 and userId2 are required' });
    }
    if (userId1 === userId2) {
      return res.status(400).json({ success: false, message: 'Cannot befriend the same user' });
    }

    const user1 = await User.findById(userId1);
    const user2 = await User.findById(userId2);
    if (!user1 || !user2) {
      return res.status(404).json({ success: false, message: 'One or both users not found' });
    }

    // Add both sides (idempotent via model methods)
    await user1.addFriend(user2._id);
    await user2.addFriend(user1._id);

    // Ensure private chat exists
    try {
      let chat = await Chat.findOne({
        type: 'private',
        'participants.user': { $all: [user1._id, user2._id] },
        'participants.isActive': true
      });
      if (!chat) {
        chat = await Chat.create({
          type: 'private',
          createdBy: req.user.id,
          participants: [
            { user: user1._id, role: 'member', isActive: true },
            { user: user2._id, role: 'member', isActive: true }
          ]
        });
      }
    } catch (e) {
      console.error('Ensure private chat error:', e);
    }

    await logAuditAction(req.user.id, 'ADMIN_ADD_FRIENDSHIP', 'User', `Linked ${userId1} <-> ${userId2}`, req.ip);

    return res.json({ success: true, message: 'Friendship added' });
  } catch (error) {
    console.error('Admin add friendship error:', error);
    return res.status(500).json({ success: false, message: 'Server error while adding friendship' });
  }
};

// @desc    ADMIN: Remove friendship between two users
// @route   DELETE /api/users/admin/friendship
// @access  Private (Admin)
const adminRemoveFriendship = async (req, res) => {
  try {
    const { userId1, userId2 } = req.body;

    if (!userId1 || !userId2) {
      return res.status(400).json({ success: false, message: 'userId1 and userId2 are required' });
    }
    if (userId1 === userId2) {
      return res.status(400).json({ success: false, message: 'Cannot unfriend the same user' });
    }

    const user1 = await User.findById(userId1);
    const user2 = await User.findById(userId2);
    if (!user1 || !user2) {
      return res.status(404).json({ success: false, message: 'One or both users not found' });
    }

    await user1.removeFriend(user2._id);
    await user2.removeFriend(user1._id);

    await logAuditAction(req.user.id, 'ADMIN_REMOVE_FRIENDSHIP', 'User', `Unlinked ${userId1} <-> ${userId2}`, req.ip);

    return res.json({ success: true, message: 'Friendship removed' });
  } catch (error) {
    console.error('Admin remove friendship error:', error);
    return res.status(500).json({ success: false, message: 'Server error while removing friendship' });
  }
};


// @desc    Update user role
// @route   PUT /api/users/:id/role or PUT /api/users/me/role
// @access  Private (Admin or self)
const updateRole = async (req, res) => {
  try {
    // Handle both /users/:id/role and /users/me/role routes
    const { id } = req.params;
    const { role } = req.body;
    const currentUserId = req.user.id;
    const currentUserRole = req.user.role;
    
    // If id is 'me', use current user's ID
    const targetUserId = id === 'me' ? currentUserId : id;

    // Validate role
    const validRoles = ['user', 'admin', 'moderator'];
    if (!validRoles.includes(role)) {
      return res.status(400).json({
        success: false,
        message: 'Invalid role. Must be user, admin, or moderator'
      });
    }

    // Find user
    const user = await User.findById(targetUserId);
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    // Check permissions
    const isAdmin = currentUserRole === 'admin';
    const isSelf = currentUserId === targetUserId;
    
    if (!isAdmin && !isSelf) {
      return res.status(403).json({
        success: false,
        message: 'You can only change your own role or need admin privileges'
      });
    }

    // Prevent non-admin from promoting themselves to admin
    if (!isAdmin && role === 'admin') {
      return res.status(403).json({
        success: false,
        message: 'Only admins can promote users to admin role'
      });
    }

    // Store old role for audit
    const oldRole = user.role;

    // Update role
    user.role = role;
    await user.save();

    // Log the action
    const action = 'CHANGE_ROLE';
    const details = `Changed role from ${oldRole} to ${role}${isSelf ? ' (self-change)' : ''}`;
    await logAuditAction(currentUserId, action, 'User', details, req.ip);

    // Always require reauth when role is changed (for security)
    return res.json({
      success: true,
      message: isSelf ? 
        'Role updated successfully. Please log in again to apply changes.' : 
        'User role updated successfully. The user will need to log in again to apply changes.',
      data: { user: user.toJSON() },
      requireReauth: true, // Flag to indicate client should logout
      targetUserId: targetUserId // ID of user whose role was changed
    });

  } catch (error) {
    console.error('Update role error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while updating user role'
    });
  }
};

// @desc    Register FCM token for push notifications
// @route   POST /api/users/me/fcm-token
// @access  Private
const registerFCMToken = async (req, res) => {
  try {
    const { token, deviceId, platform } = req.body;
    
    if (!token) {
      return res.status(400).json({
        success: false,
        message: 'FCM token is required'
      });
    }

    const user = await User.findById(req.user.id);
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    await user.addFCMToken(token, deviceId || null, platform || 'android');

    res.json({
      success: true,
      message: 'FCM token registered successfully'
    });
  } catch (error) {
    console.error('Register FCM token error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error'
    });
  }
};

// @desc    Remove FCM token
// @route   DELETE /api/users/me/fcm-token
// @access  Private
const removeFCMToken = async (req, res) => {
  try {
    const { token } = req.body;
    
    if (!token) {
      return res.status(400).json({
        success: false,
        message: 'FCM token is required'
      });
    }

    const user = await User.findById(req.user.id);
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    await user.removeFCMToken(token);

    res.json({
      success: true,
      message: 'FCM token removed successfully'
    });
  } catch (error) {
    console.error('Remove FCM token error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error'
    });
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
  getContacts,
  getBlockedUsers,
  searchUsers,
  toggleBlockUser,
  getUserFriends,
  getFriendsByUserId,
  removeFriend,
  setActive,
  updateRole,
  adminAddFriendship,
  adminRemoveFriendship,
  registerFCMToken,
  removeFCMToken
};
