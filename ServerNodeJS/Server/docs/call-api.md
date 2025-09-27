# Call API Documentation

## Overview
The Call API provides comprehensive functionality for managing voice and video calls in the chat application. It includes call initiation, participant management, WebRTC signaling, and call history.

## Base URL
```
/api/calls
```

## Authentication
All endpoints require authentication via JWT token in the Authorization header:
```
Authorization: Bearer <jwt_token>
```

## Endpoints

### 1. Initiate Call
**POST** `/api/calls/initiate`

Initiates a new call in a chat.

**Request Body:**
```json
{
  "chatId": "string (required)",
  "type": "video" | "audio (optional, default: video)"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Call initiated successfully",
  "data": {
    "callId": "call_1234567890_abc12345",
    "type": "video",
    "chatId": "chat_id",
    "status": "initiated",
    "isGroupCall": false,
    "participants": [
      {
        "userId": "user_id",
        "username": "username",
        "avatar": "avatar_url",
        "status": "connected",
        "isCaller": true,
        "joinedAt": "2025-01-24T10:00:00.000Z"
      }
    ],
    "webrtcData": {
      "roomId": "room_1234567890_abcdef123456",
      "iceServers": [
        {
          "urls": "stun:stun.l.google.com:19302"
        }
      ]
    },
    "startedAt": "2025-01-24T10:00:00.000Z"
  }
}
```

### 2. Join Call
**POST** `/api/calls/:callId/join`

Joins an existing call.

**Response:**
```json
{
  "success": true,
  "message": "Joined call successfully",
  "data": {
    "callId": "call_1234567890_abc12345",
    "status": "active",
    "participants": [...]
  }
}
```

### 3. Decline Call
**POST** `/api/calls/:callId/decline`

Declines an incoming call.

**Response:**
```json
{
  "success": true,
  "message": "Call declined successfully",
  "data": {
    "callId": "call_1234567890_abc12345",
    "status": "declined"
  }
}
```

### 4. Leave Call
**POST** `/api/calls/:callId/leave`

Leaves an active call.

**Response:**
```json
{
  "success": true,
  "message": "Left call successfully",
  "data": {
    "callId": "call_1234567890_abc12345",
    "status": "ended"
  }
}
```

### 5. End Call
**POST** `/api/calls/:callId/end`

Ends a call (caller only).

**Response:**
```json
{
  "success": true,
  "message": "Call ended successfully",
  "data": {
    "callId": "call_1234567890_abc12345",
    "status": "ended",
    "endedAt": "2025-01-24T10:05:00.000Z",
    "duration": 300
  }
}
```

### 6. Get Call Details
**GET** `/api/calls/:callId`

Retrieves detailed information about a specific call.

**Response:**
```json
{
  "success": true,
  "data": {
    "callId": "call_1234567890_abc12345",
    "type": "video",
    "status": "active",
    "participants": [...],
    "webrtcData": {...},
    "settings": {
      "muteAudio": false,
      "muteVideo": false,
      "screenShare": false,
      "recording": false
    },
    "logs": [...]
  }
}
```

### 7. Get Call History
**GET** `/api/calls/history`

Retrieves call history for the authenticated user.

**Query Parameters:**
- `limit` (optional): Number of calls to return (default: 50)
- `page` (optional): Page number for pagination (default: 1)

**Response:**
```json
{
  "success": true,
  "data": {
    "calls": [
      {
        "callId": "call_1234567890_abc12345",
        "type": "video",
        "status": "ended",
        "duration": 300,
        "startedAt": "2025-01-24T10:00:00.000Z",
        "endedAt": "2025-01-24T10:05:00.000Z",
        "chatId": {
          "name": "Chat Name",
          "type": "private"
        }
      }
    ],
    "pagination": {
      "currentPage": 1,
      "totalPages": 5,
      "totalCalls": 250,
      "hasNext": true,
      "hasPrev": false
    }
  }
}
```

### 8. Get Active Calls
**GET** `/api/calls/active`

Retrieves all active calls for the authenticated user.

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "callId": "call_1234567890_abc12345",
      "type": "video",
      "status": "active",
      "chatId": {
        "name": "Chat Name",
        "type": "group"
      },
      "participants": [...]
    }
  ]
}
```

### 9. Update Call Settings
**PUT** `/api/calls/:callId/settings`

Updates call settings (mute, video, screen share, recording).

**Request Body:**
```json
{
  "muteAudio": true,
  "muteVideo": false,
  "screenShare": true,
  "recording": false
}
```

**Response:**
```json
{
  "success": true,
  "message": "Call settings updated successfully",
  "data": {
    "callId": "call_1234567890_abc12345",
    "settings": {
      "muteAudio": true,
      "muteVideo": false,
      "screenShare": true,
      "recording": false
    }
  }
}
```

## WebSocket Events

### Client to Server Events

#### Join Call Room
```javascript
socket.emit('join_call_room', {
  callId: 'call_1234567890_abc12345'
});
```

#### Leave Call Room
```javascript
socket.emit('leave_call_room', {
  callId: 'call_1234567890_abc12345'
});
```

#### WebRTC Offer
```javascript
socket.emit('webrtc_offer', {
  callId: 'call_1234567890_abc12345',
  offer: rtcPeerConnection.localDescription,
  targetUserId: 'user_id'
});
```

#### WebRTC Answer
```javascript
socket.emit('webrtc_answer', {
  callId: 'call_1234567890_abc12345',
  answer: rtcPeerConnection.localDescription,
  targetUserId: 'user_id'
});
```

#### WebRTC ICE Candidate
```javascript
socket.emit('webrtc_ice_candidate', {
  callId: 'call_1234567890_abc12345',
  candidate: iceCandidate,
  targetUserId: 'user_id'
});
```

#### Call Status Update
```javascript
socket.emit('call_status_update', {
  callId: 'call_1234567890_abc12345',
  status: 'call_answered',
  details: 'User answered the call'
});
```

#### Call Settings Update
```javascript
socket.emit('call_settings_update', {
  callId: 'call_1234567890_abc12345',
  settings: {
    muteAudio: true,
    muteVideo: false
  }
});
```

### Server to Client Events

#### Call Room Joined
```javascript
socket.on('call_room_joined', (data) => {
  console.log('Joined call room:', data);
  // data contains: callId, roomId, participants, iceServers
});
```

#### User Joined Call
```javascript
socket.on('user_joined_call', (data) => {
  console.log('User joined:', data);
  // data contains: callId, userId, username, avatar
});
```

#### User Left Call
```javascript
socket.on('user_left_call', (data) => {
  console.log('User left:', data);
  // data contains: callId, userId, username
});
```

#### WebRTC Offer Received
```javascript
socket.on('webrtc_offer', (data) => {
  console.log('Received offer:', data);
  // data contains: callId, offer, fromUserId, fromUsername
});
```

#### WebRTC Answer Received
```javascript
socket.on('webrtc_answer', (data) => {
  console.log('Received answer:', data);
  // data contains: callId, answer, fromUserId, fromUsername
});
```

#### WebRTC ICE Candidate Received
```javascript
socket.on('webrtc_ice_candidate', (data) => {
  console.log('Received ICE candidate:', data);
  // data contains: callId, candidate, fromUserId
});
```

#### Call Status Updated
```javascript
socket.on('call_status_updated', (data) => {
  console.log('Call status updated:', data);
  // data contains: callId, status, userId, username, details, timestamp
});
```

#### Call Settings Updated
```javascript
socket.on('call_settings_updated', (data) => {
  console.log('Call settings updated:', data);
  // data contains: callId, settings, userId, username, timestamp
});
```

#### Call Error
```javascript
socket.on('call_error', (data) => {
  console.error('Call error:', data);
  // data contains: message
});
```

## Error Responses

All endpoints may return the following error responses:

### 400 Bad Request
```json
{
  "success": false,
  "message": "Validation failed",
  "errors": [...]
}
```

### 401 Unauthorized
```json
{
  "success": false,
  "message": "Authentication required"
}
```

### 403 Forbidden
```json
{
  "success": false,
  "message": "You are not a participant in this call"
}
```

### 404 Not Found
```json
{
  "success": false,
  "message": "Call not found"
}
```

### 409 Conflict
```json
{
  "success": false,
  "message": "There is already an active call in this chat",
  "data": {
    "callId": "existing_call_id",
    "status": "active"
  }
}
```

### 500 Internal Server Error
```json
{
  "success": false,
  "message": "Internal server error",
  "error": "Error details"
}
```

## Call Status Values

- `initiated`: Call has been created but not yet answered
- `ringing`: Call is ringing (participants are being notified)
- `active`: Call is in progress
- `ended`: Call has ended normally
- `declined`: Call was declined by a participant
- `missed`: Call was not answered
- `cancelled`: Call was cancelled by the caller

## Participant Status Values

- `invited`: Participant has been invited to the call
- `ringing`: Participant is being notified of the call
- `connected`: Participant has joined the call
- `declined`: Participant declined the call
- `missed`: Participant missed the call
- `left`: Participant left the call

## Database Schema

The Call model includes the following main fields:

- `callId`: Unique identifier for the call
- `type`: Call type (audio/video)
- `chatId`: Reference to the chat
- `participants`: Array of call participants
- `status`: Current call status
- `webrtcData`: WebRTC configuration and room information
- `settings`: Call settings (mute, video, etc.)
- `logs`: Array of call events and actions
- `recording`: Recording information
- `quality`: Call quality metrics
- `startedAt`, `endedAt`, `duration`: Timing information
