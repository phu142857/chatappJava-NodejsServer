# SFU (Selective Forwarding Unit) Implementation Guide

## Tổng quan

SFU là giải pháp tốt hơn mesh topology cho group video calls vì:
- **Scale tốt hơn**: Hỗ trợ hàng trăm participants
- **Bandwidth hiệu quả**: Mỗi participant chỉ upload 1 stream
- **Chất lượng tốt hơn**: Server có thể điều chỉnh chất lượng cho từng participant
- **Ổn định hơn**: Ít peer connections = ít lỗi kết nối

## Kiến trúc

```
Client 1 ──┐
Client 2 ──┤
Client 3 ──┼──> SFU Server ──> Forward streams to all clients
Client 4 ──┤
Client 5 ──┘
```

## Các lựa chọn SFU Server

### 1. **Mediasoup** (Khuyến nghị)
- ✅ Open source, Node.js
- ✅ Rất mạnh mẽ và linh hoạt
- ✅ Hỗ trợ tốt cho Android
- ✅ Tài liệu tốt

### 2. **Janus WebRTC Server**
- ✅ Open source, C
- ✅ Rất ổn định
- ⚠️ Phức tạp hơn để setup

### 3. **Kurento Media Server**
- ✅ Open source, Java
- ⚠️ Nặng hơn, phức tạp hơn

## Triển khai với Mediasoup (Khuyến nghị)

### Bước 1: Cài đặt Mediasoup

```bash
cd ServerNodeJS
npm install mediasoup
```

### Bước 2: Tạo SFU Service

Tạo file `Server/services/sfuService.js`:

```javascript
const mediasoup = require('mediasoup');

class SFUService {
    constructor() {
        this.workers = [];
        this.rooms = new Map(); // roomId -> Room
        this.router = null;
    }

    async initialize() {
        // Tạo worker processes
        const numWorkers = 1; // Có thể tăng cho production
        for (let i = 0; i < numWorkers; i++) {
            const worker = await mediasoup.createWorker({
                logLevel: 'warn',
                logTags: ['info', 'ice', 'dtls', 'rtp', 'srtp', 'rtcp'],
                rtcMinPort: 40000,
                rtcMaxPort: 49999
            });

            worker.on('died', () => {
                console.error('Mediasoup worker died, exiting in 2 seconds...');
                setTimeout(() => process.exit(1), 2000);
            });

            this.workers.push(worker);
        }

        console.log(`[SFU] Initialized ${this.workers.length} worker(s)`);
    }

    async createRoom(roomId) {
        if (this.rooms.has(roomId)) {
            return this.rooms.get(roomId);
        }

        const worker = this.workers[0]; // Round-robin trong production
        const router = await worker.createRouter({
            mediaCodecs: [
                {
                    kind: 'audio',
                    mimeType: 'audio/opus',
                    clockRate: 48000,
                    channels: 2
                },
                {
                    kind: 'video',
                    mimeType: 'video/VP8',
                    clockRate: 90000
                },
                {
                    kind: 'video',
                    mimeType: 'video/VP9',
                    clockRate: 90000
                },
                {
                    kind: 'video',
                    mimeType: 'video/H264',
                    clockRate: 90000,
                    parameters: {
                        'packetization-mode': 1
                    }
                }
            ]
        });

        const room = {
            id: roomId,
            router,
            peers: new Map() // peerId -> Peer
        };

        this.rooms.set(roomId, room);
        console.log(`[SFU] Created room: ${roomId}`);

        return room;
    }

    getRoom(roomId) {
        return this.rooms.get(roomId);
    }

    async createTransport(roomId, peerId, direction) {
        const room = this.getRoom(roomId);
        if (!room) {
            throw new Error(`Room ${roomId} not found`);
        }

        const transport = direction === 'send'
            ? await room.router.createWebRtcTransport({
                listenIps: [{ ip: '0.0.0.0', announcedIp: process.env.SFU_PUBLIC_IP || '127.0.0.1' }],
                enableUdp: true,
                enableTcp: true,
                preferUdp: true,
                initialAvailableOutgoingBitrate: 1000000
            })
            : await room.router.createWebRtcTransport({
                listenIps: [{ ip: '0.0.0.0', announcedIp: process.env.SFU_PUBLIC_IP || '127.0.0.1' }],
                enableUdp: true,
                enableTcp: true,
                preferUdp: true
            });

        // Store transport
        if (!room.peers.has(peerId)) {
            room.peers.set(peerId, {
                id: peerId,
                sendTransport: null,
                recvTransport: null,
                producers: new Map(),
                consumers: new Map()
            });
        }

        const peer = room.peers.get(peerId);
        if (direction === 'send') {
            peer.sendTransport = transport;
        } else {
            peer.recvTransport = transport;
        }

        return {
            id: transport.id,
            iceParameters: transport.iceParameters,
            iceCandidates: transport.iceCandidates,
            dtlsParameters: transport.dtlsParameters
        };
    }

    async connectTransport(roomId, peerId, transportId, dtlsParameters) {
        const room = this.getRoom(roomId);
        if (!room) {
            throw new Error(`Room ${roomId} not found`);
        }

        const peer = room.peers.get(peerId);
        if (!peer) {
            throw new Error(`Peer ${peerId} not found`);
        }

        const transport = peer.sendTransport?.id === transportId
            ? peer.sendTransport
            : peer.recvTransport;

        if (!transport) {
            throw new Error(`Transport ${transportId} not found`);
        }

        await transport.connect({ dtlsParameters });
    }

    async createProducer(roomId, peerId, transportId, rtpParameters, kind) {
        const room = this.getRoom(roomId);
        if (!room) {
            throw new Error(`Room ${roomId} not found`);
        }

        const peer = room.peers.get(peerId);
        if (!peer || !peer.sendTransport) {
            throw new Error(`Peer ${peerId} or sendTransport not found`);
        }

        const producer = await peer.sendTransport.produce({
            kind,
            rtpParameters
        });

        peer.producers.set(producer.id, producer);

        // Notify other peers about new producer
        this.broadcastNewProducer(roomId, peerId, producer.id, kind);

        return {
            id: producer.id,
            kind: producer.kind
        };
    }

    async createConsumer(roomId, peerId, transportId, producerId, rtpCapabilities) {
        const room = this.getRoom(roomId);
        if (!room) {
            throw new Error(`Room ${roomId} not found`);
        }

        const peer = room.peers.get(peerId);
        if (!peer || !peer.recvTransport) {
            throw new Error(`Peer ${peerId} or recvTransport not found`);
        }

        // Find producer
        let producer = null;
        for (const [pid, p] of room.peers) {
            if (p.producers.has(producerId)) {
                producer = p.producers.get(producerId);
                break;
            }
        }

        if (!producer) {
            throw new Error(`Producer ${producerId} not found`);
        }

        if (!room.router.canConsume({ producerId: producer.id, rtpCapabilities })) {
            throw new Error('Cannot consume producer');
        }

        const consumer = await peer.recvTransport.consume({
            producerId: producer.id,
            rtpCapabilities
        });

        peer.consumers.set(consumer.id, consumer);

        return {
            id: consumer.id,
            producerId: producer.id,
            kind: consumer.kind,
            rtpParameters: consumer.rtpParameters
        };
    }

    broadcastNewProducer(roomId, peerId, producerId, kind) {
        const room = this.getRoom(roomId);
        if (!room) return;

        // Notify all other peers
        for (const [pid, peer] of room.peers) {
            if (pid !== peerId) {
                // Emit event via Socket.io (sẽ implement sau)
                // io.to(`peer_${pid}`).emit('new-producer', { peerId, producerId, kind });
            }
        }
    }

    async closeRoom(roomId) {
        const room = this.rooms.get(roomId);
        if (room) {
            // Close all transports
            for (const [pid, peer] of room.peers) {
                if (peer.sendTransport) peer.sendTransport.close();
                if (peer.recvTransport) peer.recvTransport.close();
            }
            this.rooms.delete(roomId);
            console.log(`[SFU] Closed room: ${roomId}`);
        }
    }
}

module.exports = new SFUService();
```

### Bước 3: Tích hợp với Socket.io

Thêm handlers trong `socketHandler.js`:

```javascript
// SFU signaling handlers
socket.on('sfu-create-room', async (data) => {
    const { roomId } = data;
    await sfuService.createRoom(roomId);
    socket.emit('sfu-room-created', { roomId });
});

socket.on('sfu-create-transport', async (data) => {
    const { roomId, peerId, direction } = data;
    try {
        const transport = await sfuService.createTransport(roomId, peerId, direction);
        socket.emit('sfu-transport-created', { transport, direction });
    } catch (error) {
        socket.emit('sfu-error', { message: error.message });
    }
});

socket.on('sfu-connect-transport', async (data) => {
    const { roomId, peerId, transportId, dtlsParameters } = data;
    try {
        await sfuService.connectTransport(roomId, peerId, transportId, dtlsParameters);
        socket.emit('sfu-transport-connected', { transportId });
    } catch (error) {
        socket.emit('sfu-error', { message: error.message });
    }
});

socket.on('sfu-produce', async (data) => {
    const { roomId, peerId, transportId, rtpParameters, kind } = data;
    try {
        const producer = await sfuService.createProducer(roomId, peerId, transportId, rtpParameters, kind);
        socket.emit('sfu-producer-created', { producer });
    } catch (error) {
        socket.emit('sfu-error', { message: error.message });
    }
});

socket.on('sfu-consume', async (data) => {
    const { roomId, peerId, transportId, producerId, rtpCapabilities } = data;
    try {
        const consumer = await sfuService.createConsumer(roomId, peerId, transportId, producerId, rtpCapabilities);
        socket.emit('sfu-consumer-created', { consumer });
    } catch (error) {
        socket.emit('sfu-error', { message: error.message });
    }
});
```

### Bước 4: Update Client (Android)

Cần thay đổi cách client kết nối:
- Thay vì tạo N-1 peer connections, chỉ tạo 2 transports (send + receive)
- Kết nối đến SFU server thay vì peer-to-peer

## So sánh Mesh vs SFU

| Tiêu chí | Mesh | SFU |
|----------|------|-----|
| Peer Connections | N-1 per client | 2 per client (send + receive) |
| Bandwidth Upload | N-1 streams | 1 stream |
| Scale | Tối đa ~16 users | Hàng trăm users |
| Server Load | Không cần server | Cần SFU server |
| Complexity | Đơn giản hơn | Phức tạp hơn |
| Stability | Phụ thuộc vào tất cả peers | Phụ thuộc vào SFU server |

## Migration Path

1. **Phase 1**: Giữ mesh cho nhóm nhỏ (< 8 users), dùng SFU cho nhóm lớn
2. **Phase 2**: Chuyển tất cả sang SFU khi đã test kỹ
3. **Phase 3**: Tối ưu hóa SFU (adaptive bitrate, simulcast, etc.)

## Next Steps

1. Cài đặt Mediasoup
2. Implement SFU service
3. Tích hợp với Socket.io
4. Update Android client để hỗ trợ SFU
5. Test với nhóm nhỏ trước
6. Scale lên dần

