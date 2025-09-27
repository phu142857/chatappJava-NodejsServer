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

    // Add search functionality
    if (search) {
      query.$or = [
        { name: { $regex: search, $options: 'i' } },
        { description: { $regex: search, $options: 'i' } }
      ];
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
    .select('name description avatar status members settings')
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
      const isMember = currentUserGroupIds.includes(group._id.toString());
      const membershipStatus = group.getMembershipStatus(req.user.id);
      
      return {
        ...groupObj,
        isMember,
        membershipStatus: membershipStatus.status,
        role: membershipStatus.role
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
    const groups = await Group.findPublicGroups()
      .select('name description avatar status lastActivity')
      .populate('members.user', 'username email avatar status')
      .limit(50);

    res.json({
      success: true,
      data: {
        groups
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

    // Allow global admin or group admin/moderator or creator
    const isCreator = group.createdBy.toString() === req.user.id;
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
    if (settings) group.settings = { ...group.settings, ...settings };

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

    // Add member
    await group.addMember(userId, role);

    // Ensure chat participants include this member
    let chat = await Chat.findOne({ type: 'group', groupId: group._id, isActive: true });
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

    // Remove member
    await group.removeMember(memberId);

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

    // Only group creator can delete, allow global admin bypass
    if (req.user.role !== 'admin' && group.createdBy.toString() !== req.user.id) {
      return res.status(403).json({
        success: false,
        message: 'Permission denied'
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

    res.json({
      success: true,
      data: {
        members: group.members.filter(m => m.isActive),
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
  getGroupMembers
};
