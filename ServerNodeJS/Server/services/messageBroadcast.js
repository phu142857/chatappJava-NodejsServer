/**
 * Broadcast chat messages to online participants via Socket.io user rooms.
 * Used by REST message endpoints so clients receive realtime updates without
 * relying solely on delta-sync polling.
 */

function toUserId(userRef) {
  if (!userRef) return null;
  if (typeof userRef === 'string') return userRef;
  if (userRef._id) return userRef._id.toString();
  return userRef.toString();
}

function formatMessagePayload(message, messageObj, chat) {
  const chatId = chat._id ? chat._id.toString() : String(chat);
  return {
    ...messageObj,
    chat: chatId,
    chatType: chat.type || 'private',
    sender: message.sender
      ? {
          ...messageObj.sender,
          avatar: message.sender.avatar || ''
        }
      : messageObj.sender,
    senderInfo: message.sender
      ? {
          id: message.sender._id,
          username: message.sender.username,
          avatar: message.sender.avatar || ''
        }
      : null
  };
}

function emitToChatParticipants(io, chat, eventName, payload, excludeUserId = null) {
  if (!io || !chat) return;

  const chatId = chat._id?.toString?.() || String(chat);
  const excludeId = excludeUserId ? excludeUserId.toString() : null;

  console.log('[SOCKET]', eventName, 'chat:', chatId, excludeId ? `exclude: ${excludeId}` : '');

  for (const participant of chat.participants || []) {
    if (!participant.isActive || !participant.user) continue;
    const userId = toUserId(participant.user);
    if (!userId || (excludeId && userId === excludeId)) continue;
    const room = `user_${userId}`;
    const socketsInRoom = io.sockets?.adapter?.rooms?.get(room)?.size ?? 0;
    console.log('[SOCKET] emit', eventName, '→', room, 'sockets:', socketsInRoom);
    io.to(room).emit(eventName, payload);
  }
}

/**
 * Emit private_message / group_message to every active participant except the sender.
 */
function broadcastChatMessage(io, chat, senderUserId, message, messageObj) {
  if (!io || !chat || !message) return;

  const event = chat.type === 'group' ? 'group_message' : 'private_message';
  const payload = {
    message: formatMessagePayload(message, messageObj, chat),
    chatId: chat._id ? chat._id.toString() : String(chat),
    chatType: chat.type || 'private'
  };

  emitToChatParticipants(io, chat, event, payload, senderUserId);
}

/**
 * Notify all participants (including editor) that a message was edited.
 */
function broadcastMessageEdited(io, chat, message, messageObj) {
  if (!io || !chat || !message) return;

  const payload = {
    message: formatMessagePayload(message, messageObj, chat)
  };

  emitToChatParticipants(io, chat, 'message_edited', payload);
}

/**
 * Notify all participants that reactions changed on a message.
 */
function broadcastReactionUpdated(io, chat, messageId, reactions) {
  if (!io || !chat || !messageId) return;

  const chatId = chat._id ? chat._id.toString() : String(chat);
  const payload = {
    messageId: messageId.toString(),
    chatId,
    chatType: chat.type || 'private',
    reactions: reactions || []
  };

  emitToChatParticipants(io, chat, 'reaction_updated', payload);
}

module.exports = {
  broadcastChatMessage,
  broadcastMessageEdited,
  broadcastReactionUpdated,
  formatMessagePayload
};
