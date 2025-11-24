const mediasoup = require('mediasoup');

class SFUService {
    constructor() {
        this.workers = [];
        this.rooms = new Map(); // roomId -> Room
        this.initialized = false;
    }

    async initialize() {
        if (this.initialized) {
            return;
        }

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
                console.error('[SFU] Mediasoup worker died, exiting in 2 seconds...');
                setTimeout(() => process.exit(1), 2000);
            });

            this.workers.push(worker);
        }

        this.initialized = true;
        console.log(`[SFU] Initialized ${this.workers.length} worker(s)`);
    }

    getWorker() {
        // Round-robin trong production, hiện tại chỉ dùng worker đầu tiên
        return this.workers[0];
    }

    async createRoom(roomId) {
        // Normalize roomId - extract actual chatId if roomId contains JSON
        let normalizedRoomId = roomId;
        if (roomId.startsWith('room_')) {
            const chatIdPart = roomId.replace('room_', '');
            if (chatIdPart.startsWith('{')) {
                try {
                    const chatObj = JSON.parse(chatIdPart);
                    const actualChatId = chatObj._id || chatObj.id || chatIdPart;
                    normalizedRoomId = `room_${actualChatId}`;
                    console.log(`[SFU] Normalized roomId from ${roomId} to ${normalizedRoomId}`);
                } catch (e) {
                    console.warn(`[SFU] Could not parse roomId: ${roomId}`);
                }
            }
        }
        
        if (this.rooms.has(normalizedRoomId)) {
            console.log(`[SFU] Room ${normalizedRoomId} already exists`);
            return this.rooms.get(normalizedRoomId);
        }

        if (!this.initialized) {
            await this.initialize();
        }

        const worker = this.getWorker();
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
                    clockRate: 90000,
                    rtcpFeedback: [
                        { type: 'goog-remb' },
                        { type: 'transport-cc' },
                        { type: 'ccm', parameter: 'fir' },
                        { type: 'nack' },
                        { type: 'nack', parameter: 'pli' }
                    ]
                },
                {
                    kind: 'video',
                    mimeType: 'video/VP9',
                    clockRate: 90000,
                    rtcpFeedback: [
                        { type: 'goog-remb' },
                        { type: 'transport-cc' },
                        { type: 'ccm', parameter: 'fir' },
                        { type: 'nack' },
                        { type: 'nack', parameter: 'pli' }
                    ]
                },
                {
                    kind: 'video',
                    mimeType: 'video/H264',
                    clockRate: 90000,
                    parameters: {
                        'packetization-mode': 1,
                        'profile-level-id': '42e01f',
                        'level-asymmetry-allowed': 1
                    },
                    rtcpFeedback: [
                        { type: 'goog-remb' },
                        { type: 'transport-cc' },
                        { type: 'ccm', parameter: 'fir' },
                        { type: 'nack' },
                        { type: 'nack', parameter: 'pli' }
                    ]
                }
            ]
        });

        const room = {
            id: normalizedRoomId,
            router,
            peers: new Map(), // peerId -> Peer
            createdAt: new Date()
        };

        this.rooms.set(normalizedRoomId, room);
        console.log(`[SFU] Created room: ${normalizedRoomId}`);

        return room;
    }

    getRoom(roomId) {
        return this.rooms.get(roomId);
    }

    async createTransport(roomId, peerId, direction, io) {
        const room = this.getRoom(roomId);
        if (!room) {
            throw new Error(`Room ${roomId} not found`);
        }

        // Get public IP from environment or use localhost for development
        const publicIp = process.env.SFU_PUBLIC_IP || '127.0.0.1';
        
        const transport = direction === 'send'
            ? await room.router.createWebRtcTransport({
                listenIps: [{ ip: '0.0.0.0', announcedIp: publicIp }],
                enableUdp: true,
                enableTcp: true,
                preferUdp: true,
                initialAvailableOutgoingBitrate: 1000000, // 1 Mbps
                enableSctp: false
            })
            : await room.router.createWebRtcTransport({
                listenIps: [{ ip: '0.0.0.0', announcedIp: publicIp }],
                enableUdp: true,
                enableTcp: true,
                preferUdp: true,
                enableSctp: false
            });

        // Store transport
        if (!room.peers.has(peerId)) {
            room.peers.set(peerId, {
                id: peerId,
                sendTransport: null,
                recvTransport: null,
                producers: new Map(), // producerId -> Producer
                consumers: new Map(), // consumerId -> Consumer
                joinedAt: new Date()
            });
        }

        const peer = room.peers.get(peerId);
        if (direction === 'send') {
            peer.sendTransport = transport;
        } else {
            peer.recvTransport = transport;
        }

        // Handle transport events
        transport.on('dtlsstatechange', (dtlsState) => {
            if (dtlsState === 'closed') {
                console.log(`[SFU] Transport ${transport.id} closed for peer ${peerId}`);
            }
        });

        transport.on('icestatechange', (iceState) => {
            console.log(`[SFU] ICE state changed to ${iceState} for peer ${peerId}`);
        });

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
        console.log(`[SFU] Transport ${transportId} connected for peer ${peerId}`);
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

        if (peer.sendTransport.id !== transportId) {
            throw new Error(`Transport ${transportId} mismatch`);
        }

        const producer = await peer.sendTransport.produce({
            kind,
            rtpParameters
        });

        peer.producers.set(producer.id, producer);

        // Notify other peers about new producer
        this.broadcastNewProducer(roomId, peerId, producer.id, kind);

        console.log(`[SFU] Created producer ${producer.id} (${kind}) for peer ${peerId} in room ${roomId}`);

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

        if (peer.recvTransport.id !== transportId) {
            throw new Error(`Transport ${transportId} mismatch`);
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
            throw new Error('Cannot consume producer - unsupported codec');
        }

        const consumer = await peer.recvTransport.consume({
            producerId: producer.id,
            rtpCapabilities
        });

        peer.consumers.set(consumer.id, consumer);

        console.log(`[SFU] Created consumer ${consumer.id} for peer ${peerId} consuming producer ${producerId}`);

        return {
            id: consumer.id,
            producerId: producer.id,
            kind: consumer.kind,
            rtpParameters: consumer.rtpParameters
        };
    }

    broadcastNewProducer(roomId, producerPeerId, producerId, kind) {
        const room = this.getRoom(roomId);
        if (!room) return;

        // Notify all other peers via Socket.io
        // This will be handled by socketHandler
        return {
            roomId,
            producerPeerId,
            producerId,
            kind
        };
    }

    getRouterCapabilities(roomId) {
        const room = this.getRoom(roomId);
        if (!room) {
            throw new Error(`Room ${roomId} not found`);
        }
        return room.router.rtpCapabilities;
    }

    /**
     * Get all existing producers in a room (for a peer that just joined)
     * Returns array of { producerPeerId, producerId, kind }
     */
    getExistingProducers(roomId, excludePeerId) {
        const room = this.getRoom(roomId);
        if (!room) {
            return [];
        }

        const existingProducers = [];
        for (const [peerId, peer] of room.peers.entries()) {
            if (peerId === excludePeerId) {
                continue; // Skip self
            }
            for (const [producerId, producer] of peer.producers.entries()) {
                existingProducers.push({
                    producerPeerId: peerId,
                    producerId: producerId,
                    kind: producer.kind
                });
            }
        }
        return existingProducers;
    }

    async closeProducer(roomId, peerId, producerId) {
        const room = this.getRoom(roomId);
        if (!room) return;

        const peer = room.peers.get(peerId);
        if (!peer) return;

        const producer = peer.producers.get(producerId);
        if (producer) {
            producer.close();
            peer.producers.delete(producerId);
            console.log(`[SFU] Closed producer ${producerId} for peer ${peerId}`);
        }
    }

    async resumeConsumer(roomId, peerId, consumerId) {
        const room = this.getRoom(roomId);
        if (!room) {
            throw new Error(`Room ${roomId} not found`);
        }

        const peer = room.peers.get(peerId);
        if (!peer) {
            throw new Error(`Peer ${peerId} not found`);
        }

        const consumer = peer.consumers.get(consumerId);
        if (!consumer) {
            throw new Error(`Consumer ${consumerId} not found`);
        }

        await consumer.resume();
        console.log(`[SFU] Resumed consumer ${consumerId} for peer ${peerId}`);
    }

    async closeConsumer(roomId, peerId, consumerId) {
        const room = this.getRoom(roomId);
        if (!room) return;

        const peer = room.peers.get(peerId);
        if (!peer) return;

        const consumer = peer.consumers.get(consumerId);
        if (consumer) {
            consumer.close();
            peer.consumers.delete(consumerId);
            console.log(`[SFU] Closed consumer ${consumerId} for peer ${peerId}`);
        }
    }

    async removePeer(roomId, peerId) {
        const room = this.getRoom(roomId);
        if (!room) return;

        const peer = room.peers.get(peerId);
        if (!peer) return;

        // Close all producers
        for (const [producerId, producer] of peer.producers) {
            producer.close();
        }

        // Close all consumers
        for (const [consumerId, consumer] of peer.consumers) {
            consumer.close();
        }

        // Close transports
        if (peer.sendTransport) {
            peer.sendTransport.close();
        }
        if (peer.recvTransport) {
            peer.recvTransport.close();
        }

        room.peers.delete(peerId);
        console.log(`[SFU] Removed peer ${peerId} from room ${roomId}`);

        // If room is empty, close it
        if (room.peers.size === 0) {
            this.closeRoom(roomId);
        }
    }

    async closeRoom(roomId) {
        const room = this.rooms.get(roomId);
        if (room) {
            // Close all peers
            for (const [peerId, peer] of room.peers) {
                if (peer.sendTransport) peer.sendTransport.close();
                if (peer.recvTransport) peer.recvTransport.close();
            }
            this.rooms.delete(roomId);
            console.log(`[SFU] Closed room: ${roomId}`);
        }
    }

    getRoomStats(roomId) {
        const room = this.getRoom(roomId);
        if (!room) return null;

        return {
            roomId: room.id,
            peerCount: room.peers.size,
            peers: Array.from(room.peers.keys())
        };
    }
}

module.exports = new SFUService();

