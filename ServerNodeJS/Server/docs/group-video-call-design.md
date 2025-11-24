# Group Video Call Logic & Design

## Goals
- Support up to 16 concurrent participants per group call with smooth join/leave handling in a **Discord-like equal grid** layout (no spotlight bias).
- Reuse existing chat/group membership rules for authorization.
- Provide deterministic call state transitions that clients can subscribe to via Socket.io while eliminating audible “ringing”.
- Remain compatible with current `Call` Mongo schema while enabling future expansion (recording, screen share, moderation).
- Standardize on peer-to-peer mesh media transport for this release; SFU support can remain a future enhancement.

## High-Level Architecture
- **Client (Android/Web)**: Captures media, renders remote streams, controls mute/video/share, and arranges tiles in a responsive, uniform grid (think Discord stage).
- **Signaling (Node.js + Socket.io)**: Orchestrates call lifecycle events, maintains participant roster, persists metadata in MongoDB, and emits **passive** alerts instead of audio ringing.
- **Media Transport (Mesh)**: All participants connect in a peer-to-peer mesh. Mesh is capped by `maxMeshParticipants` (16) plus adaptive video downgrades when bandwidth drops. Future SFU integration remains optional but out-of-scope here.
- **Persistence (MongoDB)**: `Call` collection stores call envelope, participant states, media stats, and passive-notification metadata.

```
Client <-> Socket.io (signaling) <-> Call Controller <-> MongoDB
```

## Data Model Enhancements
1. **Call schema additions**
   - `mediaTopology`: `"mesh"` (default). Keep enum for forward compatibility but persist `"mesh"` for all new group calls.
   - `participantMedia`: array capturing per-user media state (audio/video mute, screen share, current streamId).
   - `dominantSpeakerUserId`, `activePresenterUserId`.
   - `moderation`: `{ locked: Boolean, pinBoard: [{ userId, promotedAt }] }`.
   - `metrics`: aggregate call quality stats (avg bitrate, max packet loss, SFU region).
   - Extend participant `status` enum with `'notified'` and default group-call invitees to that value (represents silent notification).
   - Extend call `status` enum with `'notified'` to represent silent invite state before anyone connects.
2. **Indexes**
   - `{ isGroupCall: 1, status: 1 }` to accelerate group-specific queries.
3. **Chat linkage**
   - Enforce `chatId` referencing a `type: 'group'` document when `isGroupCall` is true.

## Call Lifecycle
1. **Initiate**
   - `POST /api/calls/group`: body `{ chatId, type: 'video', mediaTopology: 'mesh' }`.
   - Controller validates membership, ensures no overlapping active call on the same chat, seeds participants with `status: 'notified'` (no audio ring).
2. **Notify**
   - Emit `group_call_passive_alert` to each `user_{id}` room containing call metadata, ICE servers, and the copy to display inside the inline banner (e.g., “Group video call is live”).
   - Clients surface the alert via the inline indicator defined in `activity_private_chat.xml` (lines 126–153) rather than launching a modal sheet.
3. **Join**
   - `POST /api/calls/:callId/join` moves participant to `connected`, persists `peerConnectionId`, returns current roster + TURN config.
   - Socket broadcast `group_call_participant_joined`.
4. **Media negotiation**
   - Client emits `webrtc_offer` with SDP + media capabilities.
   - Server relays to SFU or target peers via `call_{callId}` room.
   - Responses via `webrtc_answer`, `ice_candidate`.
5. **In-call operations**
   - `PATCH /api/calls/:callId/media` with `{ audioMuted, videoMuted, screenShareTrackId }`.
   - Socket events `participant_media_updated`, `dominant_speaker_changed`, `screen_share_started/stopped`.
6. **Moderation**
   - Owners/admins can `POST /api/calls/:callId/pin` or `/lock` to freeze membership changes.
   - Kick via `DELETE /api/calls/:callId/participants/:userId`.
7. **End**
   - Any participant can `POST /api/calls/:callId/leave`.
   - Call auto-ends when active participants <= 1 for `gracePeriodMs` (default 15_000) or when caller/host triggers `/end`.

State transitions per participant:
```
invited -> notified -> connected -> (muted|speaking) -> left
invited -> notified -> declined
invited -> notified -> missed (timeout)
```

## Socket Event Contract
Event | Direction | Payload
------|-----------|--------
`group_call_invite` | server→client | `{ callId, chatId, caller, topology, participants[] }`
`group_call_passive_alert` | server→client | `{ callId, chatId, bannerCopy, caller, participants[] }`
`group_call_state` | server→client | `{ callId, status, participants[] }`
`group_call_participant_joined` | server→client | `{ callId, user }`
`group_call_participant_left` | server→client | `{ callId, userId, reason }`
`participant_media_updated` | server→client | `{ callId, userId, audioMuted, videoMuted, screenShareTrackId }`
`webrtc_offer/answer/ice_candidate` | bidirectional | `{ callId, fromUserId, toUserId?, sdp/candidate }`
`dominant_speaker_changed` | server→client | `{ callId, userId }`
`group_call_error` | server→client | `{ callId, code, message }`

## Android Client UX Flow
1. User taps video icon in group chat → optional subject prompt (topology locked to mesh).
2. Other members receive a passive banner (using the view at `activity_private_chat.xml` lines 126–153). No ringing tone or modal answer sheet is presented; the banner text defaults to “Live group video call in progress”.
3. In-call view:
   - Responsive equal grid (up to 16 tiles) that mimics Discord’s gallery layout. Everyone gets the same tile size; speaker highlighting is applied via subtle border color only.
   - Carousel pagination when participants exceed the first screen; swipe gestures switch pages.
   - Controls: mute, video toggle, screen share, camera switch, participant drawer, “Leave quietly”.
   - Inline badges for low-bandwidth derived from `metrics`.
4. End-of-call summary screen (duration, participants, notes).

## Scalability Considerations
- Peer-to-peer mesh is capped at 16 users; beyond 8, clients automatically downgrade remote video resolution to 360p and prefer audio-only for background participants.
- Track per-peer bitrate and dynamically pause remote video tiles when the device hits thermal/network ceilings.
- Future SFU integration remains a backlog item; hooks stay in place via `mediaTopology` but no SFU credentials are emitted yet.
- Store rolling metrics for each participant to feed adaptive UI (display low-bandwidth badge).
- Use Redis pub/sub to fan out socket events across multiple server instances.

## Testing Strategy
- **Unit**: Validate controller guards (auth, membership, concurrency, status transitions) plus new `'notified'` state transitions.
- **Integration**: Simulate signaling via Socket.io test harness; ensure passive alerts drive the correct banner copy with no ringing events.
- **Load**: Stress peer-to-peer mesh by simulating 16 users exchanging SDP/ICE; verify adaptive downgrade triggers.
- **Client QA**: Automated UI tests covering passive banner display, join flow, mute/unmute, leave, and reconnection without audible alerts.

## Future Enhancements
- Cloud recording through SFU compositing, storing artifacts in S3 with call metadata linkage.
- Live transcription pipeline via streaming ASR.
- Calendar-based scheduled group calls with reminder notifications.
- PSTN dial-in/out using SIP gateway for hybrid meetings.

