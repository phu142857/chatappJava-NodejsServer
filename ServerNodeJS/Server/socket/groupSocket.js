// Group room socket handlers removed — client uses user_* rooms and REST for messaging.
class GroupSocketHandler {
  constructor(io) {
    this.io = io;
  }

  handleConnection(socket) {
    // no-op
  }

  handleUserDisconnect(userId) {
    // no-op
  }

  broadcastToGroup(groupId, event, data) {
    this.io.to(`group_${groupId}`).emit(event, data);
  }
}

module.exports = GroupSocketHandler;
