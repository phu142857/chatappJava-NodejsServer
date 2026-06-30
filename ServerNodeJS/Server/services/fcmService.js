const admin = require('firebase-admin');

// Initialize Firebase Admin SDK
// You need to set FIREBASE_SERVICE_ACCOUNT_KEY environment variable
// or place serviceAccountKey.json in the root directory
let initialized = false;

const initializeFirebase = () => {
  if (initialized) {
    return;
  }

  try {
    if (process.env.FIREBASE_SERVICE_ACCOUNT_KEY) {
      // If service account key is provided as environment variable (JSON string)
      const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_KEY);
      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
      });
    } else {
      // Try to load from file
      const serviceAccount = require('../../serviceAccountKey.json');
      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
      });
    }
    initialized = true;
    console.log('Firebase Admin SDK initialized successfully');
  } catch (error) {
    console.error('Error initializing Firebase Admin SDK:', error.message);
    console.error('Please provide FIREBASE_SERVICE_ACCOUNT_KEY environment variable or serviceAccountKey.json file');
  }
};

// Send push notification to a single device
const sendNotification = async (fcmToken, title, body, data = {}) => {
  if (!initialized) {
    initializeFirebase();
  }

  if (!admin.apps.length) {
    console.error('Firebase Admin SDK not initialized');
    return { success: false, error: 'Firebase not initialized' };
  }

  const message = {
    notification: {
      title: title,
      body: body
    },
    data: {
      ...data,
      // Convert all data values to strings (FCM requirement)
      ...Object.keys(data).reduce((acc, key) => {
        acc[key] = String(data[key]);
        return acc;
      }, {})
    },
    token: fcmToken,
    android: {
      priority: 'high',
      notification: {
        sound: 'default',
        channelId: 'default',
        priority: 'high'
      }
    },
    apns: {
      payload: {
        aps: {
          sound: 'default',
          badge: 1
        }
      }
    }
  };

  try {
    const response = await admin.messaging().send(message);
    console.log('[FCM] ✓ Successfully sent message:', response);
    return { success: true, messageId: response };
  } catch (error) {
    console.error('[FCM] ✗ Error sending message:', error);
    console.error('[FCM] Error details:', {
      code: error.code,
      message: error.message,
      token: fcmToken.substring(0, 20) + '...'
    });
    
    // If token is invalid, return error code
    if (error.code === 'messaging/invalid-registration-token' || 
        error.code === 'messaging/registration-token-not-registered') {
      return { success: false, error: 'invalid_token', code: error.code };
    }
    
    return { success: false, error: error.message, code: error.code };
  }
};

// Send push notification to multiple devices
const sendNotificationToMultiple = async (fcmTokens, title, body, data = {}) => {
  if (!initialized) {
    initializeFirebase();
  }

  if (!admin.apps.length || !fcmTokens || fcmTokens.length === 0) {
    return { success: false, error: 'Firebase not initialized or no tokens provided' };
  }

  const message = {
    notification: {
      title: title,
      body: body
    },
    data: {
      ...data,
      ...Object.keys(data).reduce((acc, key) => {
        acc[key] = String(data[key]);
        return acc;
      }, {})
    },
    android: {
      priority: 'high',
      notification: {
        sound: 'default',
        channelId: 'default',
        priority: 'high'
      }
    },
    apns: {
      payload: {
        aps: {
          sound: 'default',
          badge: 1
        }
      }
    }
  };

  try {
    const response = await admin.messaging().sendEachForMulticast({
      tokens: fcmTokens,
      ...message
    });
    
    console.log(`Successfully sent ${response.successCount} messages`);
    if (response.failureCount > 0) {
      console.log(`Failed to send ${response.failureCount} messages`);
    }
    
    return {
      success: true,
      successCount: response.successCount,
      failureCount: response.failureCount,
      responses: response.responses
    };
  } catch (error) {
    console.error('Error sending multicast message:', error);
    return { success: false, error: error.message };
  }
};

// Send notification to user (gets all their FCM tokens)
const sendNotificationToUser = async (user, title, body, data = {}) => {
  if (!user || !user.fcmTokens || user.fcmTokens.length === 0) {
    return { success: false, error: 'No FCM tokens found for user' };
  }

  const tokens = user.fcmTokens.map(t => t.token).filter(t => t);
  if (tokens.length === 0) {
    return { success: false, error: 'No valid FCM tokens' };
  }

  return await sendNotificationToMultiple(tokens, title, body, data);
};

// Send chat message notification
const sendChatMessageNotification = async (recipient, sender, message, chatId, chatType = 'private') => {
  const senderName = sender.profile?.firstName 
    ? `${sender.profile.firstName} ${sender.profile.lastName || ''}`.trim()
    : sender.username;

  let title = senderName;
  let body = message.text || message.content || 'Sent an image';
  
  if (message.type === 'image') {
    body = '📷 Sent an image';
  } else if (message.type === 'file') {
    body = '📎 Sent a file';
  }

  if (chatType === 'group') {
    title = `${senderName} in ${message.chatName || 'Group'}`;
  }

  const data = {
    type: 'message',
    chatId: chatId.toString(),
    senderId: sender._id.toString(),
    messageId: message._id?.toString() || message.id || '',
    chatType: chatType
  };

  console.log(`[FCM] Preparing to send notification:`, {
    recipient: recipient.username,
    recipientId: recipient._id,
    sender: senderName,
    title,
    body,
    chatId: chatId.toString(),
    hasFCMTokens: recipient.fcmTokens?.length || 0
  });

  return await sendNotificationToUser(recipient, title, body, data);
};

// Send call notification
const sendCallNotification = async (recipient, caller, callId, callType = 'voice', chatId = null) => {
  const callerName = caller.profile?.firstName 
    ? `${caller.profile.firstName} ${caller.profile.lastName || ''}`.trim()
    : caller.username;

  const title = 'Incoming Call';
  const body = `${callerName} is calling you`;
  
  const data = {
    type: 'call',
    callId: callId.toString(),
    callerId: caller._id.toString(),
    callType: callType
  };
  
  // Add chatId if provided
  if (chatId) {
    data.chatId = chatId.toString();
  }

  return await sendNotificationToUser(recipient, title, body, data);
};

// Send friend request notification
const sendFriendRequestNotification = async (recipient, requester) => {
  const requesterName = requester.profile?.firstName 
    ? `${requester.profile.firstName} ${requester.profile.lastName || ''}`.trim()
    : requester.username;

  const title = 'New Friend Request';
  const body = `${requesterName} sent you a friend request`;
  
  const data = {
    type: 'friend_request',
    requesterId: requester._id.toString()
  };

  return await sendNotificationToUser(recipient, title, body, data);
};

// Send post notification (tagged, friend posted, etc.)
const sendPostNotification = async (recipient, actor, notificationType, postId = null) => {
  const actorName = actor.profile?.firstName 
    ? `${actor.profile.firstName} ${actor.profile.lastName || ''}`.trim()
    : actor.username;

  let title = '';
  let body = '';

  switch (notificationType) {
    case 'tagged_in_post':
      title = 'You were tagged';
      body = `${actorName} tagged you in a post`;
      break;
    case 'tagged_in_comment':
      title = 'You were tagged';
      body = `${actorName} tagged you in a comment`;
      break;
    case 'friend_posted':
      title = 'New Post';
      body = `${actorName} shared a new post`;
      break;
    case 'friend_shared':
      title = 'New Share';
      body = `${actorName} shared a post`;
      break;
    default:
      title = 'New Notification';
      body = `${actorName} has a new update`;
  }

  const data = {
    type: 'post_notification',
    notificationType: notificationType,
    actorId: actor._id.toString(),
    postId: postId ? postId.toString() : null
  };

  return await sendNotificationToUser(recipient, title, body, data);
};

module.exports = {
  initializeFirebase,
  sendNotification,
  sendNotificationToMultiple,
  sendNotificationToUser,
  sendChatMessageNotification,
  sendCallNotification,
  sendFriendRequestNotification,
  sendPostNotification
};

