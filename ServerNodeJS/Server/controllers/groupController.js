const Group = require('../models/Group');
const User = require('../models/User');
const Chat = require('../models/Chat');
const Message = require('../models/Message');
const { validationResult } = require('express-validator');

// @desc    Get all groups (for search/contacts)
// @route   GET /api/groups
// @access  Private
const getGroups = async (req, res) => {
  try {
    const { search, page = 1, limit = 20 } = req.query;
    const skip = (page - 1) * limit;

    let query = { 
      isActive: true,
      status: 'active'
    };

    // Add search functionality (by name/description and exact ObjectId)
    if (search) {
      const orConds = [
        { name: { $regex: search, $options: 'i' } },
        { description: { $regex: search, $options: 'i' } }
      ];
      try {
        const mongoose = require('mongoose');
        if (mongoose.Types.ObjectId.isValid(search)) {
          orConds.push({ _id: search });
        }
      } catch (_) {}
      query.$or = orConds;
    }

    const groups = await Group.find(query)
      .select('name description avatar status lastActivity settings')
      .populate('members.user', 'username email avatar status')
      .sort({ lastActivity: -1 })
      .skip(skip)
      .limit(parseInt(limit));

    const total = await Group.countDocuments(query);

    res.json({
      success: true,
      data: {
        groups,
        pagination: {
          page: parseInt(page),
          limit: parseInt(limit),
          total,
          pages: Math.ceil(total / limit)
        }
      }
    });

  } catch (error) {
    console.error('Get groups error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching groups'
    });
  }
};

// @desc    Get group by ID
// @route   GET /api/groups/:id
// @access  Private
const getGroupById = async (req, res) => {
  try {
    const { id } = req.params;

    const group = await Group.findById(id)
      .select('name description avatar status lastActivity settings members createdAt')
      .populate('members.user', 'username email avatar status lastSeen')
      .populate('createdBy', 'username');

    if (!group || !group.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Group not found'
      });
    }

    res.json({
      success: true,
      data: {
        group
      }
    });

  } catch (error) {
    console.error('Get group by ID error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching group'
    });
  }
};

// @desc    Update group status
// @route   PUT /api/groups/status
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

    const { status, groupId } = req.body;
    const group = await Group.findById(groupId);

    if (!group) {
      return res.status(404).json({
        success: false,
        message: 'Group not found'
      });
    }

    // Allow global admin or group admin or creator
    const isCreator = group.createdBy.toString() === req.user.id;
    if (req.user.role !== 'admin' && !isCreator && !group.isAdmin(req.user.id)) {
      return res.status(403).json({
        success: false,
        message: 'Only admins can update group status'
      });
    }

    group.status = status;
    await group.updateLastActivity();

    res.json({
      success: true,
      message: 'Status updated successfully',
      data: {
        status: group.status,
        lastActivity: group.lastActivity
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

// @desc    Get user's groups/contacts
// @route   GET /api/groups/contacts
// @access  Private
const getContacts = async (req, res) => {
  try {
    // Get all groups where user is a member
    const groups = await Group.findUserGroups(req.user.id);

    // Get unread message counts for each group and format group data
    const groupsWithUnreadCount = await Promise.all(
      groups.map(async (group) => {
        // Find associated chat for this group
        const chat = await Chat.findOne({
          type: 'group',
          name: group.name,
          isActive: true
        });

        let unreadCount = 0;
        if (chat) {
          unreadCount = await Message.countDocuments({
            chat: chat._id,
            'readBy.user': { $ne: req.user.id },
            sender: { $ne: req.user.id },
            isDeleted: false
          });
        }

        return {
          ...group.toJSON(),
          unreadCount,
          chatId: chat ? chat._id : null
        };
      })
    );

    res.json({
      success: true,
      data: {
        contacts: groupsWithUnreadCount
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

// @desc    Search groups for joining
// @route   GET /api/groups/search
// @access  Private
const searchGroups = async (req, res) => {
  try {
    const { q, exclude } = req.query;

    if (!q || q.trim().length < 2) {
      return res.status(400).json({
        success: false,
        message: 'Search query must be at least 2 characters'
      });
    }

    let excludeIds = [];
    if (exclude) {
      const excludeArray = exclude.split(',');
      excludeIds = [...excludeIds, ...excludeArray];
    }

    const groups = await Group.find({
      $and: [
        { isActive: true },
        { status: 'active' },
        { _id: { $nin: excludeIds } },
        {
          $or: [
            { name: { $regex: q.trim(), $options: 'i' } },
            { description: { $regex: q.trim(), $options: 'i' } }
          ]
        }
      ]
    })
    .select('name description avatar status members settings joinRequests')
    .populate('members.user', 'username email avatar status')
    .limit(20)
    .sort({ name: 1 });

    // Get current user to check membership status
    const currentUserGroups = await Group.find({
      'members.user': req.user.id,
      'members.isActive': true,
      isActive: true
    }).select('_id');

    const currentUserGroupIds = currentUserGroups.map(g => g._id.toString());

    // Add membership status to each group
    const groupsWithMembershipStatus = groups.map(group => {
      const groupObj = group.toJSON();
      // Do NOT mark as member if only pending request exists
      const isMember = currentUserGroupIds.includes(group._id.toString());
      const membershipStatus = group.getMembershipStatus(req.user.id);
      const hasPending = (group.joinRequests || []).some(r => r.user && r.user.toString() === req.user.id && r.status === 'pending');
      
      return {
        ...groupObj,
        isMember: hasPending ? false : isMember,
        membershipStatus: hasPending ? 'pending' : membershipStatus.status,
        role: membershipStatus.role,
        joinRequestStatus: hasPending ? 'pending' : 'none'
      };
    });

    res.json({
      success: true,
      data: {
        groups: groupsWithMembershipStatus
      }
    });

  } catch (error) {
    console.error('Search groups error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while searching groups'
    });
  }
};

// @desc    Get public groups
// @route   GET /api/groups/public
// @access  Private
const getPublicGroups = async (req, res) => {
  try {
    // Query-level exclusion: hide groups where current user is creator or active member
    const groups = await Group.find({
      'settings.isPublic': true,
      isActive: true,
      status: 'active',
      createdBy: { $ne: req.user.id },
      members: { $not: { $elemMatch: { user: req.user.id, isActive: true } } }
    })
    .select('name description avatar status lastActivity members settings joinRequests createdBy')
    .populate('members.user', 'username email avatar status')
    .sort({ lastActivity: -1 })
    .limit(50);

    const mapped = groups.map(g => {
      const obj = g.toJSON();
      const joinRequestStatus = (g.joinRequests || []).some(r => r.user && r.user.toString() === req.user.id && r.status === 'pending') ? 'pending' : 'none';
      // Mark isMember false if pending exists (avoid showing already member)
      let isMember = false;
      try {
        isMember = g.isMember(req.user.id);
      } catch (e) {}
      const membershipStatus = joinRequestStatus === 'pending' ? 'pending' : (g.getMembershipStatus ? g.getMembershipStatus(req.user.id).status : 'not_member');
      return { ...obj, joinRequestStatus, isMember: joinRequestStatus === 'pending' ? false : isMember, membershipStatus };
    });

    res.json({
      success: true,
      data: {
        groups: mapped
      }
    });

  } catch (error) {
    console.error('Get public groups error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching public groups'
    });
  }
};

// @desc    Create group
// @route   POST /api/groups
// @access  Private
const createGroup = async (req, res) => {
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

    const { name, description, memberIds, avatar, settings } = req.body;

    // Allow duplicate names; no unique check on name

    // Validate members if provided
    let members = [{ user: req.user.id, role: 'admin' }]; // Creator is admin
    if (memberIds && memberIds.length > 0) {
      const users = await User.find({
        _id: { $in: memberIds },
        isActive: true
      });

      if (users.length !== memberIds.length) {
        return res.status(400).json({
          success: false,
          message: 'Some members not found'
        });
      }

      members = [
        { user: req.user.id, role: 'admin' },
        ...memberIds.map(id => ({ user: id, role: 'member' }))
      ];
    }

    // Create group
    const group = new Group({
      name,
      description,
      avatar: avatar || '',
      members,
      createdBy: req.user.id,
      settings: settings || {}
    });

    await group.save();

    // Create associated group chat linked by groupId
    try {
      await Chat.create({
        type: 'group',
        groupId: group._id,
        name: group.name,
        description: group.description,
        participants: members.map(m => ({ user: m.user, role: m.role })),
        createdBy: req.user.id,
        isActive: true
      });
    } catch (chatError) {
      console.error('Chat creation error:', chatError);
      // If chat creation fails, delete the group to maintain consistency
      await Group.findByIdAndDelete(group._id);
      throw chatError;
    }

    // Populate members
    await group.populate('members.user', 'username email avatar status');

    res.status(201).json({
      success: true,
      message: 'Group created successfully',
      data: {
        group
      }
    });

  } catch (error) {
    console.error('Create group error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while creating group'
    });
  }
};

// @desc    Transfer group ownership to another user
// @route   PUT /api/groups/:id/owner
// @access  Private (Admin or current owner)
const transferOwnership = async (req, res) => {
  try {
    const { id } = req.params;
    const { newOwnerId } = req.body;

    if (!newOwnerId) {
      return res.status(400).json({ success: false, message: 'newOwnerId is required' });
    }

    const group = await Group.findById(id);
    if (!group || !group.isActive) {
      return res.status(404).json({ success: false, message: 'Group not found' });
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

    // Update related chats' createdBy and ensure new owner is admin participant
    // Also set old owner as moderator in chat participants
    try {
      const chats = await Chat.find({ type: 'group', groupId: group._id });
      for (const chat of chats) {
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
      }
    } catch (e) {}

    return res.json({ success: true, message: 'Ownership transferred successfully', data: { group } });
  } catch (error) {
    console.error('Transfer ownership error:', error);
    return res.status(500).json({ success: false, message: 'Server error while transferring ownership' });
  }
};

// @desc    Update group
// @route   PUT /api/groups/:id
// @access  Private
const updateGroup = async (req, res) => {
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
    const { name, description, avatar, settings } = req.body;

    const group = await Group.findById(id);
    if (!group || !group.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Group not found'
      });
    }

    // Check permissions: owner and admins can update everything, moderators can update name/description/avatar but NOT settings
    const isCreator = group.createdBy.toString() === req.user.id;
    const isAdmin = group.isAdmin(req.user.id);
    const isModerator = group.hasPermission(req.user.id) && !isAdmin; // Has permission but not admin = moderator
    
    if (req.user.role !== 'admin' && !isCreator && !group.hasPermission(req.user.id)) {
      return res.status(403).json({
        success: false,
        message: 'Permission denied'
      });
    }

    // Update group fields
    if (name) group.name = name;
    if (description !== undefined) group.description = description;
    if (avatar !== undefined) group.avatar = avatar;
    
    // Only owner and admins can update settings (privacy settings)
    if (settings) {
      if (isCreator || isAdmin) {
        group.settings = { ...group.settings, ...settings };
      } else {
        return res.status(403).json({
          success: false,
          message: 'Only group creator and admins can change privacy settings'
        });
      }
    }

    await group.updateLastActivity();

    // Always sync Chat name with Group name (even if name wasn't explicitly changed)
    await Chat.updateMany(
      { type: 'group', groupId: group._id },
      { 
        name: group.name,
        description: group.description 
      }
    );

    res.json({
      success: true,
      message: 'Group updated successfully',
      data: {
        group
      }
    });

  } catch (error) {
    console.error('Update group error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while updating group'
    });
  }
};

// @desc    Join group
// @route   POST /api/groups/:id/join
// @access  Private
const joinGroup = async (req, res) => {
  try {
    const { id } = req.params;

    const group = await Group.findById(id);
    if (!group || !group.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Group not found'
      });
    }

    // Check if group allows invites
    if (!group.settings.allowInvites && !group.settings.isPublic) {
      return res.status(403).json({
        success: false,
        message: 'Group does not allow new members'
      });
    }

    // Check if user is already a member
    if (group.isMember(req.user.id)) {
      return res.status(400).json({
        success: false,
        message: 'Already a member of this group'
      });
    }

    // Add user to group
    await group.addMember(req.user.id, 'member');

    res.json({
      success: true,
      message: 'Joined group successfully'
    });

  } catch (error) {
    console.error('Join group error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while joining group'
    });
  }
};

// @desc    Leave group
// @route   POST /api/groups/:id/leave
// @access  Private
const leaveGroup = async (req, res) => {
  try {
    const { id } = req.params;

    const group = await Group.findById(id);
    if (!group || !group.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Group not found'
      });
    }

    // Check if user is a member
    if (!group.isMember(req.user.id)) {
      return res.status(400).json({
        success: false,
        message: 'Not a member of this group'
      });
    }

    // Check if user is the creator
    if (group.createdBy.toString() === req.user.id) {
      return res.status(400).json({
        success: false,
        message: 'Group creator cannot leave. Transfer ownership or delete group.'
      });
    }

    // Remove user from group
    await group.removeMember(req.user.id);

    res.json({
      success: true,
      message: 'Left group successfully'
    });

  } catch (error) {
    console.error('Leave group error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while leaving group'
    });
  }
};

// @desc    Add member to group
// @route   POST /api/groups/:id/members
// @access  Private
const addMember = async (req, res) => {
  try {
    const { id } = req.params;
    const { userId, role = 'member' } = req.body;

    const group = await Group.findById(id);
    if (!group || !group.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Group not found'
      });
    }

    // Allow global admin or group admin/moderator or creator
    const isCreator = group.createdBy.toString() === req.user.id;
    if (req.user.role !== 'admin' && !isCreator && !group.hasPermission(req.user.id)) {
      return res.status(403).json({
        success: false,
        message: 'Permission denied'
      });
    }

    // Check if member exists
    const member = await User.findById(userId);
    if (!member || !member.isActive) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    // If member record exists but inactive, reactivate; if active, just ensure chat participant is active
    const membership = group.getMembershipStatus(userId);
    if (membership && membership.status !== 'not_member') {
      // Reactivate at group level
      await group.addMember(userId, role);
      // Reactivate in chat
      let chat = await Chat.findOne({ type: 'group', groupId: group._id, isActive: true });
      if (!chat) chat = await Chat.findOne({ type: 'group', name: group.name, isActive: true });
      if (chat) await chat.addParticipant(userId, role);
      return res.json({ success: true, message: membership.isMember ? 'Member already active' : 'Member reactivated' });
    }

    // Add/Reactivate member in Group (covers inactive -> active)
    await group.addMember(userId, role);

    // If user had a pending join request, mark it approved to avoid stale pending state
    try {
      if (group.joinRequests && Array.isArray(group.joinRequests)) {
        const jr = group.joinRequests.find(r => r.user && r.user.toString() === userId);
        if (jr && jr.status === 'pending') {
          jr.status = 'approved';
          await group.save();
        }
      }
    } catch (e) {
      console.warn('Failed to update join request status on addMember:', e?.message || e);
    }

    // Ensure chat participants include this member
    let chat = await Chat.findOne({ type: 'group', groupId: group._id, isActive: true });
    if (!chat) {
      // Fallback for legacy data: locate by name
      chat = await Chat.findOne({ type: 'group', name: group.name, isActive: true });
    }
    if (!chat) {
      // As a last resort, recreate the group chat container to keep system consistent
      try {
        chat = await Chat.create({
          type: 'group',
          groupId: group._id,
          name: group.name,
          description: group.description,
          participants: [{ user: group.createdBy, role: 'admin' }],
          createdBy: group.createdBy,
          isActive: true
        });
      } catch (e) {
        console.error('Failed to recreate group chat for group', group._id, e);
      }
    }
    if (chat) {
      await chat.addParticipant(userId, role);
    }

    // Notify the added user via socket to refresh their group list
    try {
      const io = req.app.get('io');
      if (io) {
        io.to(`user_${userId}`).emit('added_to_group', {
          groupId: group._id,
          groupName: group.name,
          chatId: chat ? chat._id : null,
          timestamp: new Date()
        });
      }
    } catch (e) {
      console.error('Socket notify added_to_group error:', e);
    }

    res.json({
      success: true,
      message: 'Member added successfully'
    });

  } catch (error) {
    console.error('Add member error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while adding member'
    });
  }
};

// @desc    Remove member from group
// @route   DELETE /api/groups/:id/members/:memberId
// @access  Private
const removeMember = async (req, res) => {
  try {
    const { id, memberId } = req.params;

    const group = await Group.findById(id);
    if (!group || !group.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Group not found'
      });
    }

    // Check permissions
    const userMember = group.members.find(m => m.user.toString() === req.user.id && m.isActive);
    const isRemovingSelf = memberId === req.user.id;
    const hasPermission = userMember && ['admin', 'moderator'].includes(userMember.role);
    const isCreator = group.createdBy.toString() === req.user.id;

    if (req.user.role !== 'admin' && !isCreator && !isRemovingSelf && !hasPermission) {
      return res.status(403).json({
        success: false,
        message: 'Permission denied'
      });
    }

    // Cannot remove creator
    if (memberId === group.createdBy.toString()) {
      return res.status(400).json({
        success: false,
        message: 'Cannot remove group creator'
      });
    }

    // Remove member (soft remove)
    await group.removeMember(memberId);

    // Clean up stale pending join request for this user (avoid duplicate states)
    try {
      if (group.joinRequests && Array.isArray(group.joinRequests)) {
        const jr = group.joinRequests.find(r => r.user && r.user.toString() === memberId && r.status === 'pending');
        if (jr) {
          jr.status = 'rejected';
          await group.save();
        }
      }
    } catch (e) {
      console.warn('Failed to update join request on removeMember:', e?.message || e);
    }

    // Also reflect removal in chat participants to avoid stuck inactive states on chat only
    try {
      let chat = await Chat.findOne({ type: 'group', groupId: group._id, isActive: true });
      if (!chat) chat = await Chat.findOne({ type: 'group', name: group.name, isActive: true });
    if (chat) {
      await chat.removeParticipant(memberId);
    }
    } catch (e) {
      console.error('Failed to sync chat participants on removeMember(Group):', e);
    }

    // Ensure chat participants reflect removal
    try {
      const chat = await Chat.findOne({ type: 'group', groupId: group._id, isActive: true });
      if (chat) {
        await chat.removeParticipant(memberId);
      }
    } catch (e) {
      console.error('Update chat participants on remove error:', e);
    }

    // Notify the removed user via socket
    try {
      const io = req.app.get('io');
      if (io) {
        io.to(`user_${memberId}`).emit('removed_from_group', {
          groupId: group._id,
          groupName: group.name,
          timestamp: new Date()
        });
      }
    } catch (e) {
      console.error('Socket notify removed_from_group error:', e);
    }

    res.json({
      success: true,
      message: 'Member removed successfully'
    });

  } catch (error) {
    console.error('Remove member error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while removing member'
    });
  }
};

// @desc    Delete group
// @route   DELETE /api/groups/:id
// @access  Private
const deleteGroup = async (req, res) => {
  try {
    const { id } = req.params;

    const group = await Group.findById(id);
    if (!group || !group.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Group not found'
      });
    }

    // Only group creator, group admin, or global admin can delete (moderators excluded)
    const userMember = group.members.find(m => m.user.toString() === req.user.id && m.isActive);
    const isGroupAdmin = userMember && userMember.role === 'admin';
    const isCreator = group.createdBy.toString() === req.user.id;
    
    if (req.user.role !== 'admin' && !isCreator && !isGroupAdmin) {
      return res.status(403).json({
        success: false,
        message: 'Only group creator and admins can delete group'
      });
    }

    // Collect related chats (group chats use group.name as identifier in current design)
    const relatedChats = await Chat.find({ type: 'group', groupId: group._id }).select('_id');
    const relatedChatIds = relatedChats.map(c => c._id);

    // Delete messages in related chats first
    if (relatedChatIds.length > 0) {
      await Message.deleteMany({ chat: { $in: relatedChatIds } });
      await Chat.deleteMany({ _id: { $in: relatedChatIds } });
    }

    // Hard delete group document
    await Group.deleteOne({ _id: id });

    res.json({
      success: true,
      message: 'Group and related chats deleted successfully'
    });

  } catch (error) {
    console.error('Delete group error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while deleting group'
    });
  }
};

// @desc    Get group members
// @route   GET /api/groups/:id/members
// @access  Private
const getGroupMembers = async (req, res) => {
  try {
    const { id } = req.params;

    const group = await Group.findById(id)
      .populate('members.user', 'username email avatar status profile')
      .select('members');

    if (!group || !group.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Group not found'
      });
    }

    // Check if user is a member
    if (!group.isMember(req.user.id)) {
      return res.status(403).json({
        success: false,
        message: 'Access denied to this group'
      });
    }

    const ownerId = group.createdBy ? group.createdBy.toString() : null;
    const activeMembers = group.members
      .filter(m => m.isActive)
      .map(m => ({
        ...m.toObject(),
        isOwner: ownerId ? (m.user && m.user.toString() === ownerId) : false
      }));

    res.json({
      success: true,
      data: {
        members: activeMembers,
        membersCount: group.activeMembersCount
      }
    });

  } catch (error) {
    console.error('Get group members error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching group members'
    });
  }
};

// @desc    Request to join a group (for non-public, or when approval is needed)
// @route   POST /api/groups/:id/join-requests
// @access  Private
const requestJoinGroup = async (req, res) => {
  try {
    const { id } = req.params;
    let group = await Group.findById(id);
    if (!group) {
      try {
        const chat = await Chat.findById(id);
        if (chat && chat.groupId) {
          group = await Group.findById(chat.groupId);
        } else if (chat && chat.name) {
          group = await Group.findOne({ name: chat.name });
        }
      } catch (e) {}
    }
    if (!group || !group.isActive) {
      return res.status(404).json({ success: false, message: 'Group not found' });
    }

    // If already has a pending request, return pending (must check BEFORE member to avoid wrong UX)
    const existing = group.joinRequests.find(r => r.user && r.user.toString() === req.user.id && r.status === 'pending');
    if (existing) {
      return res.status(200).json({ success: true, message: 'Request already pending' });
    }

    // If already a member (active)
    if (group.isMember(req.user.id)) {
      return res.status(400).json({ success: false, message: 'Already a member' });
    }
    group.joinRequests.push({ user: req.user.id, status: 'pending' });
    await group.save();
    return res.status(201).json({ success: true, message: 'Join request submitted' });
  } catch (error) {
    console.error('Request join group error:', error);
    res.status(500).json({ success: false, message: 'Server error while requesting to join' });
  }
};

// @desc    Cancel join request for a group
// @route   DELETE /api/groups/:id/join-requests
// @access  Private
const cancelJoinRequest = async (req, res) => {
  try {
    const { id } = req.params;
    const userId = req.user.id;
    
    let group = await Group.findById(id);
    if (!group) {
      try {
        const chat = await Chat.findById(id);
        if (chat && chat.groupId) {
          group = await Group.findById(chat.groupId);
        } else if (chat && chat.name) {
          group = await Group.findOne({ name: chat.name });
        }
      } catch (e) {}
    }
    
    if (!group || !group.isActive) {
      return res.status(404).json({ success: false, message: 'Group not found' });
    }

    // Find the pending join request
    const requestIndex = group.joinRequests.findIndex(r => 
      r.user && r.user.toString() === userId && r.status === 'pending'
    );

    if (requestIndex === -1) {
      return res.status(404).json({ 
        success: false, 
        message: 'No pending join request found' 
      });
    }

    // Remove the join request
    group.joinRequests.splice(requestIndex, 1);
    await group.save();

    res.json({
      success: true,
      message: 'Join request cancelled successfully'
    });

  } catch (error) {
    console.error('Cancel join request error:', error);
    res.status(500).json({ 
      success: false, 
      message: 'Server error while cancelling join request' 
    });
  }
};

// @desc    Get pending join requests count
// @route   GET /api/groups/:id/join-requests/count
// @access  Private (admins/moderators only)
const getJoinRequestsCount = async (req, res) => {
  try {
    const { id } = req.params;
    let group = await Group.findById(id);
    if (!group) {
      try {
        const chat = await Chat.findById(id);
        if (chat && chat.groupId) {
          group = await Group.findById(chat.groupId);
        } else if (chat && chat.name) {
          group = await Group.findOne({ name: chat.name });
        }
      } catch (e) {}
    }
    if (!group || !group.isActive) {
      return res.status(404).json({ success: false, message: 'Group not found' });
    }
    if (!group.hasPermission(req.user.id)) {
      return res.status(403).json({ success: false, message: 'Permission denied' });
    }
    const count = (group.joinRequests || []).filter(r => r.status === 'pending').length;
    return res.json({ success: true, data: { count } });
  } catch (error) {
    console.error('Get join requests count error:', error);
    res.status(500).json({ success: false, message: 'Server error while counting join requests' });
  }
};

// @desc    Get pending join requests list
// @route   GET /api/groups/:id/join-requests
// @access  Private (admins/moderators only)
const getJoinRequests = async (req, res) => {
  try {
    const { id } = req.params;
    let group = await Group.findById(id).populate('joinRequests.user', 'username email avatar');
    if (!group) {
      try {
        const chat = await Chat.findById(id);
        if (chat && chat.groupId) {
          group = await Group.findById(chat.groupId).populate('joinRequests.user', 'username email avatar');
        } else if (chat && chat.name) {
          group = await Group.findOne({ name: chat.name }).populate('joinRequests.user', 'username email avatar');
        }
      } catch (e) {}
    }
    if (!group || !group.isActive) {
      return res.status(404).json({ success: false, message: 'Group not found' });
    }
    if (!group.hasPermission(req.user.id)) {
      return res.status(403).json({ success: false, message: 'Permission denied' });
    }
    const pending = (group.joinRequests || []).filter(r => r.status === 'pending').map(r => ({
      user: r.user,
      status: r.status,
      createdAt: r.createdAt
    }));
    return res.json({ success: true, data: { requests: pending } });
  } catch (error) {
    console.error('Get join requests error:', error);
    res.status(500).json({ success: false, message: 'Server error while fetching join requests' });
  }
};

// @desc    Approve/Reject a join request
// @route   POST /api/groups/:id/join-requests/:userId
// @access  Private (admins/moderators only)
const respondJoinRequest = async (req, res) => {
  try {
    const { id, userId } = req.params;
    const { action } = req.body; // 'approve' | 'reject'
    let group = await Group.findById(id);
    if (!group) {
      try {
        const chat = await Chat.findById(id);
        if (chat && chat.groupId) {
          group = await Group.findById(chat.groupId);
        } else if (chat && chat.name) {
          group = await Group.findOne({ name: chat.name });
        }
      } catch (e) {}
    }
    if (!group || !group.isActive) {
      return res.status(404).json({ success: false, message: 'Group not found' });
    }
    if (!group.hasPermission(req.user.id)) {
      return res.status(403).json({ success: false, message: 'Permission denied' });
    }
    const reqItem = (group.joinRequests || []).find(r => r.user && r.user.toString() === userId && r.status === 'pending');
    if (!reqItem) {
      return res.status(404).json({ success: false, message: 'Join request not found' });
    }
    if (action === 'approve') {
      // Mark approved and perform full add-member sync (same như addMember)
      reqItem.status = 'approved';
      // 1) Kích hoạt/Thêm vào Group
      await group.addMember(userId, 'member');
      // 2) Đảm bảo tồn tại Chat nhóm và thêm participant
      let chat = await Chat.findOne({ type: 'group', groupId: group._id, isActive: true });
      if (!chat) chat = await Chat.findOne({ type: 'group', name: group.name, isActive: true });
      if (!chat) {
        try {
          chat = await Chat.create({
            type: 'group',
            groupId: group._id,
            name: group.name,
            description: group.description,
            participants: [{ user: group.createdBy, role: 'admin' }],
            createdBy: group.createdBy,
            isActive: true
          });
        } catch (e) {
          console.error('Failed to recreate group chat during respondJoinRequest:', e);
        }
      }
      if (chat) {
        await chat.addParticipant(userId, 'member');
      }
      // 3) Gửi socket notify cho user được duyệt
      try {
        const io = req.app.get('io');
        if (io) {
          io.to(`user_${userId}`).emit('added_to_group', {
            groupId: group._id,
            groupName: group.name,
            chatId: chat ? chat._id : null,
            timestamp: new Date()
          });
        }
      } catch (e) {
        console.error('Socket notify (approve join) error:', e);
      }
    } else {
      reqItem.status = 'rejected';
    }
    await group.save();
    return res.json({ success: true, message: `Request ${action}d` });
  } catch (error) {
    console.error('Respond join request error:', error);
    res.status(500).json({ success: false, message: 'Server error while responding join request' });
  }
};

module.exports = {
  getGroups,
  getGroupById,
  updateStatus,
  getContacts,
  searchGroups,
  getPublicGroups,
  createGroup,
  updateGroup,
  joinGroup,
  leaveGroup,
  addMember,
  removeMember,
  deleteGroup,
  getGroupMembers,
  requestJoinGroup,
  cancelJoinRequest,
  getJoinRequestsCount,
  respondJoinRequest,
  getJoinRequests,
  transferOwnership
};
