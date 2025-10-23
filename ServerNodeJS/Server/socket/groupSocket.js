const Group = require('../models/Group');
const User = require('../models/User');
const Chat = require('../models/Chat');
const Message = require('../models/Message');

class GroupSocketHandler {
  constructor(io) {
    this.io = io;
    this.groupRooms = new Map(); // Map to track group rooms
  }

  // Handle group socket connection
  handleConnection(socket) {
    console.log('Group socket connected:', socket.id);

    // Join group room
    socket.on('join_group', async (data) => {
      try {
        const { groupId, userId } = data;
        
        if (!groupId || !userId) {
          socket.emit('error', { message: 'Group ID and User ID are required' });
          return;
        }

        // Verify user is member of group
        const group = await Group.findById(groupId);
        if (!group || !group.isActive) {
          socket.emit('error', { message: 'Group not found' });
          return;
        }

        if (!group.isMember(userId)) {
          socket.emit('error', { message: 'Not a member of this group' });
          return;
        }

        // Join group room
        const roomName = `group_${groupId}`;
        socket.join(roomName);
        
        // Track user in group room
        if (!this.groupRooms.has(roomName)) {
          this.groupRooms.set(roomName, new Set());
        }
        this.groupRooms.get(roomName).add(userId);


        // Notify other members
        socket.to(roomName).emit('user_joined_group', {
          userId,
          groupId,
          timestamp: new Date()
        });

        // Send current online members
        socket.emit('group_online_members', {
          groupId,
          onlineMembers: []
        });

        console.log(`User ${userId} joined group room ${roomName}`);

      } catch (error) {
        console.error('Join group error:', error);
        socket.emit('error', { message: 'Failed to join group' });
      }
    });

    // Leave group room
    socket.on('leave_group', async (data) => {
      try {
        const { groupId, userId } = data;
        
        if (!groupId || !userId) {
          return;
        }

        const roomName = `group_${groupId}`;
        socket.leave(roomName);

        // Remove user from group room tracking
        if (this.groupRooms.has(roomName)) {
          this.groupRooms.get(roomName).delete(userId);
          if (this.groupRooms.get(roomName).size === 0) {
            this.groupRooms.delete(roomName);
          }
        }


        // Notify other members
        socket.to(roomName).emit('user_left_group', {
          userId,
          groupId,
          timestamp: new Date()
        });

        console.log(`User ${userId} left group room ${roomName}`);

      } catch (error) {
        console.error('Leave group error:', error);
      }
    });

    // Send group message
    socket.on('send_group_message', async (data) => {
      try {
        const { groupId, userId, content, messageType = 'text' } = data;
        
        if (!groupId || !userId || !content) {
          socket.emit('error', { message: 'Group ID, User ID, and content are required' });
          return;
        }

        // Verify user is member of group
        const group = await Group.findById(groupId);
        if (!group || !group.isActive) {
          socket.emit('error', { message: 'Group not found' });
          return;
        }

        if (!group.isMember(userId)) {
          socket.emit('error', { message: 'Not a member of this group' });
          return;
        }

        // Find or create associated chat by groupId
        let chat = await Chat.findOne({
          type: 'group',
          groupId: group._id,
          isActive: true
        });

        if (!chat) {
          // Create new group chat
          chat = new Chat({
            name: group.name,
            description: group.description,
            type: 'group',
            groupId: group._id,
            participants: group.members.map(member => ({
              user: member.user,
              role: member.role,
              isActive: member.isActive
            })),
            createdBy: group.createdBy,
            isActive: true
          });
          await chat.save();
        }

        // Create message
        const message = new Message({
          chat: chat._id,
          sender: userId,
          content,
          messageType,
          readBy: [{ user: userId, readAt: new Date() }]
        });

        await message.save();

        // Update chat last message and activity
        chat.lastMessage = message._id;
        chat.lastActivity = new Date();
        await chat.save();

        // Update group last activity
        await group.updateLastActivity();

        // Populate message with sender info
        await message.populate('sender', 'username email avatar');

        // Broadcast message to group room
        const roomName = `group_${groupId}`;
        const messageObj = message.toJSON();
        this.io.to(roomName).emit('group_message', {
          message: {
            ...messageObj,
            // Ensure sender has avatar info
            sender: {
              ...messageObj.sender,
              avatar: message.sender.avatar || ''
            }
          },
          groupId,
          chatId: chat._id,
          chatType: 'group',
          senderInfo: {
            id: message.sender._id,
            username: message.sender.username,
            avatar: message.sender.avatar || ''
          }
        });

        console.log(`Group message sent in ${roomName} by user ${userId}`);

      } catch (error) {
        console.error('Send group message error:', error);
        socket.emit('error', { message: 'Failed to send message' });
      }
    });

    // Handle group typing
    socket.on('group_typing', (data) => {
      try {
        const { groupId, userId, isTyping } = data;
        
        if (!groupId || !userId) {
          return;
        }

        const roomName = `group_${groupId}`;
        socket.to(roomName).emit('group_typing', {
          groupId,
          userId,
          isTyping,
          timestamp: new Date()
        });

      } catch (error) {
        console.error('Group typing error:', error);
      }
    });


    // Handle disconnect
    socket.on('disconnect', async () => {
      try {
        console.log('Group socket disconnected:', socket.id);
        
        // Update status for all groups user was in
        const userId = socket.userId; // Assuming userId is stored on socket
        if (userId) {
          await this.handleUserDisconnect(userId);
        }

      } catch (error) {
        console.error('Group socket disconnect error:', error);
      }
    });
  }

  // Helper methods


  async handleUserDisconnect(userId) {
    try {
      // Find all groups user is member of
      const groups = await Group.find({
        'members.user': userId,
        'members.isActive': true,
        isActive: true
      });

      // Update status in each group
      for (const group of groups) {
        const roomName = `group_${group._id}`;
        
        // Remove user from room tracking
        if (this.groupRooms.has(roomName)) {
          this.groupRooms.get(roomName).delete(userId);
        }

        // Notify group members
        this.io.to(roomName).emit('user_left_group', {
          userId,
          groupId: group._id,
          timestamp: new Date()
        });

        // Update group activity
        await group.updateLastActivity();
      }

    } catch (error) {
      console.error('Handle user disconnect error:', error);
    }
  }

  // Get group room info
  getGroupRoomInfo(groupId) {
    const roomName = `group_${groupId}`;
    return {
      roomName,
      memberCount: this.groupRooms.get(roomName)?.size || 0,
      members: Array.from(this.groupRooms.get(roomName) || [])
    };
  }

  // Broadcast to group
  broadcastToGroup(groupId, event, data) {
    const roomName = `group_${groupId}`;
    this.io.to(roomName).emit(event, data);
  }
}

module.exports = GroupSocketHandler;
