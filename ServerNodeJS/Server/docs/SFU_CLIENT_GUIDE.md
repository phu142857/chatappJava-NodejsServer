# SFU Client Implementation Guide (Android)

## Tổng quan

Client cần thay đổi từ mesh topology sang SFU topology. Thay vì tạo N-1 peer connections, client chỉ cần:
- 1 send transport (để gửi audio/video)
- 1 receive transport (để nhận audio/video từ tất cả participants)

## Flow

### 1. Join Call
```
1. Client gọi API joinGroupCall
2. Server trả về SFU roomId và rtpCapabilities
3. Client emit 'sfu-create-room' với roomId
4. Server trả về rtpCapabilities
```

### 2. Create Transports
```
1. Client emit 'sfu-create-transport' với direction='send'
2. Server trả về send transport (iceParameters, iceCandidates, dtlsParameters)
3. Client tạo send transport trong WebRTC
4. Client emit 'sfu-connect-transport' với dtlsParameters
5. Lặp lại cho receive transport với direction='receive'
```

### 3. Produce (Send Media)
```
1. Client tạo local tracks (audio/video)
2. Client emit 'sfu-produce' với rtpParameters
3. Server trả về producerId
4. Server emit 'sfu-new-producer' đến tất cả participants khác
```

### 4. Consume (Receive Media)
```
1. Client nhận 'sfu-new-producer' event
2. Client emit 'sfu-consume' với producerId và rtpCapabilities
3. Server trả về consumer (rtpParameters)
4. Client tạo remote track từ consumer
5. Client render video track
```

## Socket Events

### Client → Server
- `sfu-create-room`: Tạo SFU room
- `sfu-get-router-capabilities`: Lấy router capabilities
- `sfu-create-transport`: Tạo transport (send/receive)
- `sfu-connect-transport`: Kết nối transport
- `sfu-produce`: Tạo producer (gửi media)
- `sfu-consume`: Tạo consumer (nhận media)
- `sfu-close-producer`: Đóng producer
- `sfu-close-consumer`: Đóng consumer
- `sfu-remove-peer`: Xóa peer khỏi room

### Server → Client
- `sfu-room-created`: Room đã được tạo
- `sfu-router-capabilities`: Router capabilities
- `sfu-transport-created`: Transport đã được tạo
- `sfu-transport-connected`: Transport đã kết nối
- `sfu-producer-created`: Producer đã được tạo
- `sfu-consumer-created`: Consumer đã được tạo
- `sfu-new-producer`: Có producer mới (từ participant khác)
- `sfu-producer-closed`: Producer đã đóng
- `sfu-consumer-closed`: Consumer đã đóng
- `sfu-peer-removed`: Peer đã rời khỏi room
- `sfu-error`: Lỗi SFU

## Android Implementation

### Dependencies
Cần thêm mediasoup client library cho Android. Hiện tại WebRTC Android SDK có thể dùng trực tiếp với mediasoup.

### Key Changes

1. **Thay vì tạo N-1 PeerConnections**, chỉ tạo 2 transports:
   - Send transport: để gửi local tracks
   - Receive transport: để nhận remote tracks

2. **Thay vì offer/answer**, dùng SFU signaling:
   - Create transport
   - Connect transport
   - Produce/Consume

3. **Thay vì ICE candidates**, SFU tự động xử lý

### Example Code Structure

```java
class SFUManager {
    private SendTransport sendTransport;
    private RecvTransport recvTransport;
    private Map<String, Consumer> consumers = new HashMap<>();
    private Map<String, Producer> producers = new HashMap<>();
    
    void joinSFURoom(String roomId, RtpCapabilities rtpCapabilities) {
        // Create send transport
        socket.emit("sfu-create-transport", {
            roomId: roomId,
            peerId: userId,
            direction: "send"
        });
        
        // Create receive transport
        socket.emit("sfu-create-transport", {
            roomId: roomId,
            peerId: userId,
            direction: "receive"
        });
    }
    
    void produceAudio(VideoTrack audioTrack) {
        // Get RTP parameters from track
        RtpParameters rtpParameters = getRtpParameters(audioTrack);
        
        socket.emit("sfu-produce", {
            roomId: roomId,
            peerId: userId,
            transportId: sendTransport.getId(),
            rtpParameters: rtpParameters,
            kind: "audio"
        });
    }
    
    void consumeMedia(String producerId) {
        socket.emit("sfu-consume", {
            roomId: roomId,
            peerId: userId,
            transportId: recvTransport.getId(),
            producerId: producerId,
            rtpCapabilities: localRtpCapabilities
        });
    }
}
```

## Migration Checklist

- [ ] Update SocketManager để hỗ trợ SFU events
- [ ] Tạo SFUManager class để quản lý SFU connections
- [ ] Update GroupVideoCallActivity để dùng SFU thay vì mesh
- [ ] Test với 2 participants
- [ ] Test với nhiều participants
- [ ] Test join/leave
- [ ] Test audio/video mute
- [ ] Test camera switch

