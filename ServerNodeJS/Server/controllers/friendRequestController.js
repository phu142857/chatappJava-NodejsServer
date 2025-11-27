const FriendRequest = require('../models/FriendRequest');
const User = require('../models/User');
const Chat = require('../models/Chat');
const { validationResult } = require('express-validator');

// @desc    Send friend request
// @route   POST /api/friend-requests
// @access  Private
const sendFriendRequest = async (req, res) => {
  try {
    const { receiverId } = req.body;
    const senderId = req.user.id;

    // Check if receiver exists
    const receiver = await User.findById(receiverId);
    if (!receiver) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    // Check if trying to send request to self
    if (senderId === receiverId) {
      return res.status(400).json({
        success: false,
        message: 'Cannot send friend request to yourself'
      });
    }

    // Check if they are already friends
    const senderUser = await User.findById(senderId);
    const receiverUser = await User.findById(receiverId);
    
    console.log(`🔍 Checking friend relationship between ${senderId} and ${receiverId}`);
    console.log(`🔍 Sender user:`, senderUser ? senderUser.username : 'Not found');
    console.log(`🔍 Receiver user:`, receiverUser ? receiverUser.username : 'Not found');
    
    if (senderUser && receiverUser) {
      // Check if they are already friends
      const senderFriends = senderUser.friends || [];
      const receiverFriends = receiverUser.friends || [];
      
      console.log(`🔍 Sender friends:`, senderFriends);
      console.log(`🔍 Receiver friends:`, receiverFriends);
      
      // Use the new method to check friendship
      if (senderUser.isFriendWith(receiverId) || receiverUser.isFriendWith(senderId)) {
        console.log(`❌ Already friends - blocking request`);
        return res.status(400).json({
          success: false,
          message: 'You are already friends with this user'
        });
      }
    }

    // Check if friend request already exists (only pending requests)
    const existingRequest = await FriendRequest.findOne({
      $or: [
        { senderId, receiverId },
        { senderId: receiverId, receiverId: senderId }
      ],
      status: 'pending'  // Only check pending requests
    });

    if (existingRequest) {
      return res.status(400).json({
        success: false,
        message: 'Friend request already exists'
      });
    }

    // Create new friend request
    const friendRequest = new FriendRequest({
      senderId,
      receiverId
    });

    await friendRequest.save();

    // Populate sender and receiver info
    await friendRequest.populate([
      { 
        path: 'senderId', 
        select: 'username email avatar profile',
        model: 'User'
      },
      { 
        path: 'receiverId', 
        select: 'username email avatar profile',
        model: 'User'
      }
    ]);

    res.status(201).json({
      success: true,
      message: 'Friend request sent successfully',
      data: {
        friendRequest
      }
    });

  } catch (error) {
    console.error('Send friend request error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error'
    });
  }
};

// @desc    Get friend requests (sent and received)
// @route   GET /api/friend-requests
// @access  Private
const getFriendRequests = async (req, res) => {
  try {
    const userId = req.user.id;
    const isAdmin = req.user && (req.user.role === 'admin' || req.user.role === 'moderator');

    const baseQuery = isAdmin
      ? { status: 'pending' }
      : {
          $or: [
            { senderId: userId },
            { receiverId: userId }
          ],
          status: 'pending'
        };

    const friendRequests = await FriendRequest.find(baseQuery)
    .populate({
      path: 'senderId',
      select: 'username email avatar profile',
      model: 'User'
    })
    .populate({
      path: 'receiverId', 
      select: 'username email avatar profile',
      model: 'User'
    })
    .sort({ createdAt: -1 });

    res.json({
      success: true,
      data: {
        requests: friendRequests
      }
    });

  } catch (error) {
    console.error('Get friend requests error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error'
    });
  }
};

// @desc    Respond to friend request (accept/reject)
// @route   PUT /api/friend-requests/:id
// @access  Private
const respondToFriendRequest = async (req, res) => {
  try {
    const { id } = req.params;
    const { action } = req.body;
    const userId = req.user.id;

    if (!['accept', 'reject'].includes(action)) {
      return res.status(400).json({
        success: false,
        message: 'Action must be either accept or reject'
      });
    }

    // Find the friend request
    const friendRequest = await FriendRequest.findById(id);
    if (!friendRequest) {
      return res.status(404).json({
        success: false,
        message: 'Friend request not found'
      });
    }

    console.log(`🔍 Friend request found:`, {
      id: friendRequest._id,
      senderId: friendRequest.senderId,
      receiverId: friendRequest.receiverId,
      status: friendRequest.status,
      userId: userId
    });

    // Check permission: receiver or admin/mod can respond
    const isAdmin = req.user && (req.user.role === 'admin' || req.user.role === 'moderator');
    if (!isAdmin && friendRequest.receiverId.toString() !== userId) {
      return res.status(403).json({
        success: false,
        message: 'You can only respond to friend requests sent to you'
      });
    }

    // Check if request is still pending
    if (friendRequest.status !== 'pending') {
      console.log(`❌ Request already processed with status: ${friendRequest.status}`);
      return res.status(400).json({
        success: false,
        message: 'Friend request has already been responded to'
      });
    }

    // If accepted, delete the request and create friend relationship
    // If rejected, just delete the request (no friend relationship)
    if (action === 'accept') {
      // Create friend relationship by adding each other to friends list
      const sender = await User.findById(friendRequest.senderId);
      const receiver = await User.findById(friendRequest.receiverId);
      
      if (sender && receiver) {
        // Initialize friends array if not exists
        if (!sender.friends) {
          sender.friends = [];
        }
        if (!receiver.friends) {
          receiver.friends = [];
        }
        
        // Add receiver to sender's friends list
        console.log(`📝 Adding ${receiver.username} to ${sender.username}'s friends list`);
        await sender.addFriend(receiver._id);
        console.log(`✅ ${sender.username} friends after add:`, sender.friends);
        
        // Add sender to receiver's friends list
        console.log(`📝 Adding ${sender.username} to ${receiver.username}'s friends list`);
        await receiver.addFriend(sender._id);
        console.log(`✅ ${receiver.username} friends after add:`, receiver.friends);
        
        console.log(`✅ Friend relationship created between ${sender.username} and ${receiver.username}`);
        console.log(`✅ ${sender.username} friends list:`, sender.friends);
        console.log(`✅ ${receiver.username} friends list:`, receiver.friends);
        
        // Create private chat for both users
        await createPrivateChat(sender._id, receiver._id);
      }
      
      // Delete the request since it's accepted
      await FriendRequest.findByIdAndDelete(friendRequest._id);
      
      res.json({
        success: true,
        message: 'Friend request accepted successfully',
        data: {
          message: 'Request accepted, friend relationship created, and request removed'
        }
      });
    } else {
      // Delete the request when rejected
      console.log(`🗑️ Deleting friend request: ${friendRequest._id}`);
      await FriendRequest.findByIdAndDelete(friendRequest._id);
      console.log(`✅ Friend request deleted successfully`);
      
      res.json({
        success: true,
        message: 'Friend request rejected successfully',
        data: {
          message: 'Request rejected and removed'
        }
      });
    }

  } catch (error) {
    console.error('Respond to friend request error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error'
    });
  }
};

// @desc    Cancel friend request
// @route   DELETE /api/friend-requests/:id
// @access  Private
const cancelFriendRequest = async (req, res) => {
  try {
    const { id } = req.params;
    const userId = req.user.id;

    // Find the friend request
    const friendRequest = await FriendRequest.findById(id);
    if (!friendRequest) {
      return res.status(404).json({
        success: false,
        message: 'Friend request not found'
      });
    }

    // Check if user is the sender
    if (friendRequest.senderId.toString() !== userId) {
      return res.status(403).json({
        success: false,
        message: 'You can only cancel friend requests you sent'
      });
    }

    // Check if request is still pending
    if (friendRequest.status !== 'pending') {
      return res.status(400).json({
        success: false,
        message: 'Cannot cancel a request that has already been responded to'
      });
    }

    await FriendRequest.findByIdAndDelete(id);

    res.json({
      success: true,
      message: 'Friend request cancelled successfully'
    });

  } catch (error) {
    console.error('Cancel friend request error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error'
    });
  }
};

// Helper function to create private chat
const createPrivateChat = async (userId1, userId2) => {
  try {
    console.log(`💬 Creating private chat between ${userId1} and ${userId2}`);
    
    // Prevent creating a chat with the same user
    if (userId1.toString() === userId2.toString()) {
      throw new Error('Cannot create a private chat with the same user');
    }
    
    // Check if chat already exists (including inactive participants)
    let existingChat = await Chat.findOne({
      type: 'private',
      'participants.user': { $all: [userId1, userId2] },
      'participants.isActive': true,
      isActive: true
    });
    
    // If not found, try to find with any participant status
    if (!existingChat) {
      existingChat = await Chat.findOne({
        type: 'private',
        'participants.user': { $all: [userId1, userId2] },
        isActive: true
      });
    }
    
    if (existingChat) {
      // Reactivate both participants if they were inactive
      const participant1 = existingChat.participants.find(p => p.user && p.user.toString() === userId1.toString());
      const participant2 = existingChat.participants.find(p => p.user && p.user.toString() === userId2.toString());
      
      if (participant1 && !participant1.isActive) {
        await existingChat.addParticipant(userId1, 'member');
      }
      if (participant2 && !participant2.isActive) {
        await existingChat.addParticipant(userId2, 'member');
      }
      
      console.log(`💬 Private chat already exists: ${existingChat._id}`);
      return existingChat;
    }
    
    // Create new private chat
    const chat = new Chat({
      type: 'private',
      createdBy: userId1, // Set the creator as the first user
      participants: [
        {
          user: userId1,
          role: 'member',
          isActive: true
        },
        {
          user: userId2,
          role: 'member',
          isActive: true
        }
      ],
      lastMessage: null,
      lastMessageAt: null
    });
    
    await chat.save();
    console.log(`✅ Private chat created: ${chat._id}`);
    
    return chat;
  } catch (error) {
    console.error('❌ Error creating private chat:', error);
    throw error;
  }
};

module.exports = {
  sendFriendRequest,
  getFriendRequests,
  respondToFriendRequest,
  cancelFriendRequest
};
