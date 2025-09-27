# Avatar Display Guide for Group Chat

## Overview

The server has been updated to return avatar information of message senders in group chat. Avatars are available in multiple locations for easy client access.

## Data Structure

### 1. API Response Structure

#### Get Messages API (`GET /api/messages/:chatId`)
```json
{
  "success": true,
  "data": {
    "messages": [
      {
        "_id": "message_id",
        "content": "Hello group!",
        "type": "text",
        "chatType": "group",
        "sender": {
          "_id": "user_id",
          "username": "username",
          "avatar": "/uploads/avatars/avatar.jpg",
          "status": "online"
        },
        "senderInfo": {
          "id": "user_id",
          "username": "username",
          "avatar": "/uploads/avatars/avatar.jpg",
          "status": "online"
        },
        "createdAt": "2025-09-23T09:00:00.000Z"
      }
    ],
    "chatInfo": {
      "id": "chat_id",
      "type": "group",
      "name": "Group Name"
    }
  }
}
```

#### Send Message API (`POST /api/messages`)
```json
{
  "success": true,
  "message": "Message sent successfully",
  "data": {
    "message": {
      "_id": "message_id",
      "content": "Hello group!",
      "type": "text",
      "chatType": "group",
      "sender": {
        "_id": "user_id",
        "username": "username",
        "avatar": "/uploads/avatars/avatar.jpg",
        "status": "online"
      },
      "senderInfo": {
        "id": "user_id",
        "username": "username",
        "avatar": "/uploads/avatars/avatar.jpg",
        "status": "online"
      }
    }
  }
}
```

### 2. Socket Event Structure

#### Group Message Event (`group_message`)
```json
{
  "message": {
    "_id": "message_id",
    "content": "Hello group!",
    "type": "text",
    "sender": {
      "_id": "user_id",
      "username": "username",
      "avatar": "/uploads/avatars/avatar.jpg",
      "status": "online"
    }
  },
  "groupId": "group_id",
  "chatId": "chat_id",
  "chatType": "group",
  "senderInfo": {
    "id": "user_id",
    "username": "username",
    "avatar": "/uploads/avatars/avatar.jpg",
    "status": "online"
  }
}
```

#### Private Message Event (`private_message`)
```json
{
  "message": {
    "_id": "message_id",
    "content": "Hello!",
    "type": "text",
    "sender": {
      "_id": "user_id",
      "username": "username",
      "avatar": "/uploads/avatars/avatar.jpg",
      "status": "online"
    }
  },
  "chatId": "chat_id",
  "chatType": "private",
  "senderInfo": {
    "id": "user_id",
    "username": "username",
    "avatar": "/uploads/avatars/avatar.jpg",
    "status": "online"
  }
}
```

## Usage on Client

### 1. Android/Java Implementation

#### Message Model
```java
public class Message {
    private String id;
    private String content;
    private String type;
    private String chatType;
    private Sender sender;
    private SenderInfo senderInfo;
    private String createdAt;
    
    // Getters and setters
}

public class Sender {
    private String id;
    private String username;
    private String avatar;
    private String status;
    
    // Getters and setters
}

public class SenderInfo {
    private String id;
    private String username;
    private String avatar;
    private String status;
    
    // Getters and setters
}
```

#### Message Adapter
```java
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    
    @Override
    public void onBindViewHolder(MessageViewHolder holder, int position) {
        Message message = messages.get(position);
        
        // Display avatar for group chat
        if ("group".equals(message.getChatType())) {
            String avatarUrl = message.getSender().getAvatar();
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                // Load avatar image
                Glide.with(context)
                    .load(avatarUrl)
                    .placeholder(R.drawable.default_avatar)
                    .into(holder.avatarImageView);
                
                // Display avatar
                holder.avatarImageView.setVisibility(View.VISIBLE);
            } else {
                // Hide avatar if not available
                holder.avatarImageView.setVisibility(View.GONE);
            }
            
            // Display username
            holder.usernameTextView.setText(message.getSender().getUsername());
            holder.usernameTextView.setVisibility(View.VISIBLE);
        } else {
            // Private chat - hide avatar and username
            holder.avatarImageView.setVisibility(View.GONE);
            holder.usernameTextView.setVisibility(View.GONE);
        }
        
        // Display message content
        holder.messageTextView.setText(message.getContent());
    }
}
```

#### Socket Event Handler
```java
// Handle group message
socket.on("group_message", new Emitter.Listener() {
    @Override
    public void call(Object... args) {
        JSONObject data = (JSONObject) args[0];
        
        runOnUiThread(() -> {
            try {
                JSONObject message = data.getJSONObject("message");
                String chatType = data.getString("chatType");
                JSONObject senderInfo = data.getJSONObject("senderInfo");
                
                if ("group".equals(chatType)) {
                    // Display message with avatar
                    String avatarUrl = senderInfo.getString("avatar");
                    String username = senderInfo.getString("username");
                    
                    // Add message to adapter with avatar information
                    Message newMessage = new Message();
                    newMessage.setContent(message.getString("content"));
                    newMessage.setChatType(chatType);
                    newMessage.setSenderInfo(senderInfo);
                    
                    messageAdapter.addMessage(newMessage);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }
});
```

### 2. React Native Implementation

#### Message Component
```javascript
import React from 'react';
import { View, Text, Image, StyleSheet } from 'react-native';

const MessageItem = ({ message }) => {
  const isGroupChat = message.chatType === 'group';
  
  return (
    <View style={styles.messageContainer}>
      {isGroupChat && (
        <View style={styles.senderInfo}>
          <Image 
            source={{ uri: message.sender.avatar || 'default_avatar' }}
            style={styles.avatar}
          />
          <Text style={styles.username}>{message.sender.username}</Text>
        </View>
      )}
      <Text style={styles.messageContent}>{message.content}</Text>
    </View>
  );
};

const styles = StyleSheet.create({
  messageContainer: {
    padding: 10,
    marginVertical: 2,
  },
  senderInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 5,
  },
  avatar: {
    width: 30,
    height: 30,
    borderRadius: 15,
    marginRight: 8,
  },
  username: {
    fontSize: 12,
    color: '#666',
    fontWeight: 'bold',
  },
  messageContent: {
    fontSize: 16,
    color: '#000',
  },
});
```

#### Socket Event Handler
```javascript
import io from 'socket.io-client';

const socket = io('ws://your-server-url');

socket.on('group_message', (data) => {
  const { message, chatType, senderInfo } = data;
  
  if (chatType === 'group') {
    // Display message with avatar
    const messageWithAvatar = {
      ...message,
      chatType,
      senderInfo
    };
    
    // Add message to state
    setMessages(prev => [...prev, messageWithAvatar]);
  }
});
```

### 3. Web/JavaScript Implementation

#### Message Display
```javascript
function displayMessage(message) {
  const messageElement = document.createElement('div');
  messageElement.className = 'message';
  
  if (message.chatType === 'group') {
    // Display avatar and username for group chat
    const senderInfo = document.createElement('div');
    senderInfo.className = 'sender-info';
    
    const avatar = document.createElement('img');
    avatar.src = message.sender.avatar || '/default-avatar.png';
    avatar.className = 'avatar';
    
    const username = document.createElement('span');
    username.textContent = message.sender.username;
    username.className = 'username';
    
    senderInfo.appendChild(avatar);
    senderInfo.appendChild(username);
    messageElement.appendChild(senderInfo);
  }
  
  const content = document.createElement('div');
  content.textContent = message.content;
  content.className = 'message-content';
  messageElement.appendChild(content);
  
  document.getElementById('messages').appendChild(messageElement);
}

// Socket event handler
socket.on('group_message', (data) => {
  const { message, chatType } = data;
  
  if (chatType === 'group') {
    displayMessage({
      ...message,
      chatType
    });
  }
});
```

## Available Avatar Locations

Avatars are available in the following locations for client access:

1. **`message.sender.avatar`** - Avatar from sender information
2. **`message.senderInfo.avatar`** - Avatar from senderInfo object
3. **`data.senderInfo.avatar`** - Avatar from socket event data

## Important Notes

1. **Fallback Avatar**: Always has a default fallback avatar when no avatar is available
2. **Chat Type Check**: Only display avatar for group chat (`chatType === 'group'`)
3. **Avatar URL**: Avatar URL includes the full path `/uploads/avatars/filename.jpg`
4. **Status**: Can use `status` to display online/offline status

## Testing

Server has been tested and confirmed:
- ✅ Avatar available in all locations
- ✅ API responses include avatar
- ✅ Socket events include avatar
- ✅ Consistent data structure

## Conclusion

The server is ready to provide avatar information to the client. The client only needs to:
1. Check `chatType === 'group'`
2. Get avatar from `message.sender.avatar` or `message.senderInfo.avatar`
3. Display avatar before message in group chat

Avatars will help users easily distinguish who sent which message in group chat! 🎉
