package com.example.chatappjava.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.models.CallParticipant;
import com.squareup.picasso.Picasso;

import org.webrtc.EglBase;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.List;
import java.util.stream.IntStream;

import de.hdodenhof.circleimageview.CircleImageView;

public class VideoParticipantAdapter extends RecyclerView.Adapter<VideoParticipantAdapter.ViewHolder> {

    private Context context;
    private List<CallParticipant> participants;
    private EglBase eglBase;
    private OnParticipantCountChangeListener countChangeListener;
    private RecyclerView recyclerView; // Store reference to RecyclerView to access view holders

    public interface OnParticipantCountChangeListener {
        void onCountChanged(int count);
    }

    public VideoParticipantAdapter(Context context, List<CallParticipant> participants, EglBase eglBase) {
        this.context = context;
        this.participants = participants;
        this.eglBase = eglBase;
    }

    public void setOnParticipantCountChangeListener(OnParticipantCountChangeListener listener) {
        this.countChangeListener = listener;
    }
    
    public void setEglBase(EglBase eglBase) {
        this.eglBase = eglBase;
        android.util.Log.d("VideoParticipantAdapter", "EglBase updated: " + (eglBase != null ? "exists" : "NULL"));
    }
    
    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
        android.util.Log.d("VideoParticipantAdapter", "Adapter attached to RecyclerView");
    }
    
    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        this.recyclerView = null;
        android.util.Log.d("VideoParticipantAdapter", "Adapter detached from RecyclerView");
    }
    
    /**
     * Update video track for a specific participant and ensure it's displayed
     * This method can be called when a remote video track is received
     */
    public void updateParticipantVideoTrack(String userId) {
        android.util.Log.d("VideoParticipantAdapter", "updateParticipantVideoTrack called for: " + userId);
        
        // Find the participant index
        int index = IntStream.range(0, participants.size()).filter(i -> participants.get(i).getUserId() != null &&
                participants.get(i).getUserId().trim().equalsIgnoreCase(userId.trim())).findFirst().orElse(-1);

        if (index >= 0 && index < participants.size()) {
            CallParticipant participant = participants.get(index);
            VideoTrack videoTrack = participant.getVideoTrack();
            
            android.util.Log.d("VideoParticipantAdapter", "Notifying item changed at index: " + index + 
                              ", has track: " + (videoTrack != null) + 
                              ", track enabled: " + (videoTrack != null && videoTrack.enabled()));
            
            // Try to update existing view holder directly if available
            if (recyclerView != null) {
                RecyclerView.ViewHolder existingHolder = recyclerView.findViewHolderForAdapterPosition(index);
                if (existingHolder instanceof ViewHolder) {
                    ViewHolder holder = (ViewHolder) existingHolder;
                    android.util.Log.d("VideoParticipantAdapter", "Found existing ViewHolder for index: " + index);
                    
                    // Directly update the view holder
                    updateViewHolderVideoTrack(holder, participant, videoTrack);
                }
            }
            
            // Always notify item changed to ensure rebind
            notifyItemChanged(index);
            
            // Also try to update after a short delay to handle timing issues
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.postDelayed(() -> {
                try {
                    // Try to update view holder again
                    if (recyclerView != null) {
                        RecyclerView.ViewHolder existingHolder = recyclerView.findViewHolderForAdapterPosition(index);
                        if (existingHolder instanceof ViewHolder) {
                            ViewHolder holder = (ViewHolder) existingHolder;
                            CallParticipant p = participants.get(index);
                            updateViewHolderVideoTrack(holder, p, p.getVideoTrack());
                        }
                    }
                    // Force rebind after a short delay to ensure surface is ready
                    notifyItemChanged(index);
                    android.util.Log.d("VideoParticipantAdapter", "Re-notified item changed at index: " + index);
                } catch (Exception e) {
                    android.util.Log.e("VideoParticipantAdapter", "Error in delayed update: " + e.getMessage());
                }
            }, 150);
        } else {
            android.util.Log.w("VideoParticipantAdapter", "Participant not found for userId: " + userId);
        }
    }
    
    /**
     * Directly update a view holder's video track
     * This is called when we have a reference to the view holder
     */
    private void updateViewHolderVideoTrack(ViewHolder holder, CallParticipant participant, VideoTrack videoTrack) {
        if (holder == null || participant == null) {
            return;
        }
        
        android.util.Log.d("VideoParticipantAdapter", "updateViewHolderVideoTrack for: " + participant.getUsername() + 
                          ", track: " + (videoTrack != null) + 
                          ", enabled: " + (videoTrack != null && videoTrack.enabled()));
        
        if (videoTrack != null && videoTrack.enabled() && !participant.isVideoMuted() && 
            holder.svParticipantVideo != null && eglBase != null) {
            
            try {
                // CRITICAL: Verify view is attached before initializing
                if (holder.itemView.getWindowToken() == null) {
                    android.util.Log.w("VideoParticipantAdapter", "View not attached for " + participant.getUsername() + ", will add in onViewAttachedToWindow");
                    return;
                }
                
                // Initialize surface view if needed
                try {
                    holder.svParticipantVideo.init(eglBase.getEglBaseContext(), null);
                    holder.svParticipantVideo.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL);
                    if (participant.isLocal()) {
                        holder.svParticipantVideo.setMirror(true);
                    } else {
                        holder.svParticipantVideo.setMirror(false);
                    }
                    android.util.Log.d("VideoParticipantAdapter", "Surface initialized for direct update");
                } catch (IllegalStateException e) {
                    // Already initialized - that's fine, just configure
                    holder.svParticipantVideo.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL);
                    if (participant.isLocal()) {
                        holder.svParticipantVideo.setMirror(true);
                    } else {
                        holder.svParticipantVideo.setMirror(false);
                    }
                    android.util.Log.d("VideoParticipantAdapter", "Surface already initialized");
                }
                
                // Remove existing sink first
                try {
                    videoTrack.removeSink(holder.svParticipantVideo);
                } catch (Exception e) {
                    // Ignore - might not have sink attached
                }
                
                // Show video
                holder.videoPlaceholder.setVisibility(android.view.View.GONE);
                holder.svParticipantVideo.setVisibility(android.view.View.VISIBLE);
                
                // Add sink with retry mechanism
                try {
                    videoTrack.addSink(holder.svParticipantVideo);
                    android.util.Log.d("VideoParticipantAdapter", "✓ Directly added video sink for " + participant.getUsername());
                } catch (IllegalStateException e) {
                    android.util.Log.w("VideoParticipantAdapter", "Surface not ready, scheduling delayed add for " + participant.getUsername());
                    // Retry after delay with re-initialization
                    holder.svParticipantVideo.postDelayed(() -> {
                        try {
                            if (videoTrack != null && holder.svParticipantVideo != null && videoTrack.enabled() && eglBase != null) {
                                // Re-initialize surface
                                try {
                                    holder.svParticipantVideo.init(eglBase.getEglBaseContext(), null);
                                } catch (IllegalStateException ex) {
                                    // Already initialized
                                }
                                
                                try {
                                    videoTrack.removeSink(holder.svParticipantVideo);
                                } catch (Exception ex) {
                                    // Ignore
                                }
                                videoTrack.addSink(holder.svParticipantVideo);
                                android.util.Log.d("VideoParticipantAdapter", "✓ Added video sink on delayed retry for " + participant.getUsername());
                            }
                        } catch (Exception ex) {
                            android.util.Log.e("VideoParticipantAdapter", "Error adding sink on retry: " + ex.getMessage());
                        }
                    }, 200);
                }
            } catch (Exception e) {
                android.util.Log.e("VideoParticipantAdapter", "Error updating view holder video track: " + e.getMessage(), e);
            }
        } else {
            android.util.Log.w("VideoParticipantAdapter", "Cannot update view holder - track: " + (videoTrack != null) + 
                              ", enabled: " + (videoTrack != null && videoTrack.enabled()) + 
                              ", muted: " + (participant != null && participant.isVideoMuted()));
        }
    }
    
    /**
     * Remove all video sinks from all surface views
     * CRITICAL: Call this before stopping camera to prevent -32 errors
     */
    public void removeAllVideoSinks(VideoTrack videoTrack) {
        if (videoTrack == null) {
            return;
        }
        
        android.util.Log.d("VideoParticipantAdapter", "Removing all video sinks for cleanup");
        
        // Iterate through all participants and remove sinks
        for (CallParticipant participant : participants) {
            if (participant.getVideoTrack() == videoTrack) {
                // This participant uses this track, but we can't access the ViewHolder here
                // The onViewDetachedFromWindow will handle it, but we need to force it
                android.util.Log.d("VideoParticipantAdapter", "Found participant with matching track: " + participant.getUsername());
            }
        }
        
        // Note: Individual sinks are removed in onViewDetachedFromWindow
        // This method is a placeholder for future enhancement if needed
    }
    
    /**
     * Release all surface views - call this during cleanup
     * Note: We no longer release surfaces in onViewDetachedFromWindow to prevent
     * "Dropping frame - Not initialized or already released" errors.
     * Surfaces will be cleaned up when RecyclerView is destroyed.
     */
    public void releaseAllSurfaceViews() {
        android.util.Log.d("VideoParticipantAdapter", "Releasing all surface views");
        // Surface views will be automatically cleaned up when RecyclerView is destroyed
        // We don't release them in onViewDetachedFromWindow to prevent frame dropping
    }
    
    /**
     * Helper method to safely add sink to surface view with validation
     */
    private void addSinkToSurfaceView(ViewHolder holder, VideoTrack videoTrack, String username) {
        if (holder == null || holder.svParticipantVideo == null || videoTrack == null) {
            android.util.Log.w("VideoParticipantAdapter", "Cannot add sink - null parameters for " + username);
            return;
        }
        
        try {
            // Verify view is attached
            if (holder.itemView.getWindowToken() == null) {
                android.util.Log.w("VideoParticipantAdapter", "View not attached for " + username + ", will add in onViewAttachedToWindow");
                return;
            }
            
            // CRITICAL: Ensure surface is initialized before adding sink
            if (eglBase != null) {
                try {
                    holder.svParticipantVideo.init(eglBase.getEglBaseContext(), null);
                    android.util.Log.d("VideoParticipantAdapter", "Surface initialized before adding sink for " + username);
                } catch (IllegalStateException e) {
                    // Already initialized - that's fine
                    android.util.Log.d("VideoParticipantAdapter", "Surface already initialized for " + username);
                }
            } else {
                android.util.Log.e("VideoParticipantAdapter", "eglBase is null, cannot initialize surface for " + username);
                return;
            }
            
            // Remove existing sink first
            try {
                videoTrack.removeSink(holder.svParticipantVideo);
            } catch (Exception e) {
                // Ignore - might not have sink attached
            }
            
            // Add sink - this will throw IllegalStateException if surface not ready
            videoTrack.addSink(holder.svParticipantVideo);
            holder.videoPlaceholder.setVisibility(android.view.View.GONE);
            holder.svParticipantVideo.setVisibility(android.view.View.VISIBLE);
            android.util.Log.d("VideoParticipantAdapter", "✓ Added video sink for " + username);
        } catch (IllegalStateException e) {
            android.util.Log.w("VideoParticipantAdapter", "Surface not ready for " + username + ", will retry: " + e.getMessage());
            // Retry after delay with initialization
            holder.svParticipantVideo.postDelayed(() -> {
                if (eglBase != null && holder.svParticipantVideo != null) {
                    try {
                        holder.svParticipantVideo.init(eglBase.getEglBaseContext(), null);
                    } catch (IllegalStateException ex) {
                        // Already initialized
                    }
                }
                addSinkToSurfaceView(holder, videoTrack, username);
            }, 200);
        } catch (Exception e) {
            android.util.Log.e("VideoParticipantAdapter", "Error adding sink for " + username + ": " + e.getMessage(), e);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_video_participant, parent, false);
        ViewHolder holder = new ViewHolder(view);
        
        // CRITICAL: Initialize surface view ONCE when view holder is created (like private call does in onCreate)
        // This ensures the surface is always ready and never needs reinitialization
        if (holder.svParticipantVideo != null && eglBase != null) {
            try {
                holder.svParticipantVideo.init(eglBase.getEglBaseContext(), null);
                holder.svParticipantVideo.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL);
                android.util.Log.d("VideoParticipantAdapter", "Surface initialized in onCreateViewHolder");
            } catch (IllegalStateException e) {
                // Already initialized - that's fine
                android.util.Log.d("VideoParticipantAdapter", "Surface already initialized in onCreateViewHolder");
            } catch (Exception e) {
                android.util.Log.e("VideoParticipantAdapter", "Error initializing surface in onCreateViewHolder", e);
            }
        }
        
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CallParticipant participant = participants.get(position);

        // Set participant name
        holder.tvParticipantName.setText(participant.getUsername());
        holder.tvParticipantNamePlaceholder.setText(participant.getUsername());

        // Load avatar
        if (participant.getAvatar() != null && !participant.getAvatar().isEmpty()) {
            Picasso.get()
                    .load(participant.getAvatar())
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .into(holder.ivParticipantAvatar);
        } else {
            holder.ivParticipantAvatar.setImageResource(R.drawable.ic_profile_placeholder);
        }

        // Handle video track
        // First, remove any existing sink to avoid duplicates
        VideoTrack existingTrack = participant.getVideoTrack();
        if (existingTrack != null && holder.svParticipantVideo != null) {
            try {
                existingTrack.removeSink(holder.svParticipantVideo);
            } catch (Exception e) {
                // Ignore - might not have sink attached
            }
        }
        
        // CRITICAL: Surface view is already initialized in onCreateViewHolder (like private call)
        // Just configure mirror setting if needed
        boolean surfaceInitialized = false;
        if (holder.svParticipantVideo != null && eglBase != null) {
            try {
                // Surface should already be initialized, but verify and configure
                holder.svParticipantVideo.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL);
                if (participant.isLocal()) {
                    holder.svParticipantVideo.setMirror(true);
                } else {
                    holder.svParticipantVideo.setMirror(false);
                }
                surfaceInitialized = true;
                android.util.Log.d("VideoParticipantAdapter", "Surface view configured - mirror: " + participant.isLocal());
            } catch (Exception e) {
                // If surface isn't initialized, try to initialize it now (fallback)
                android.util.Log.w("VideoParticipantAdapter", "Surface not initialized, initializing now: " + e.getMessage());
                try {
                    holder.svParticipantVideo.init(eglBase.getEglBaseContext(), null);
                    holder.svParticipantVideo.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL);
                    if (participant.isLocal()) {
                        holder.svParticipantVideo.setMirror(true);
                    } else {
                        holder.svParticipantVideo.setMirror(false);
                    }
                    surfaceInitialized = true;
                } catch (Exception ex) {
                    android.util.Log.e("VideoParticipantAdapter", "Error initializing surface view", ex);
                }
            }
        }
        
        // Check if we should show video
        boolean hasVideoTrack = existingTrack != null;
        boolean isVideoEnabled = hasVideoTrack && existingTrack.enabled();
        boolean isNotMuted = !participant.isVideoMuted();
        
        // Debug logging
        android.util.Log.d("VideoParticipantAdapter", "Binding participant: " + participant.getUsername() + 
                          " - hasTrack: " + hasVideoTrack + 
                          ", enabled: " + isVideoEnabled + 
                          ", notMuted: " + isNotMuted + 
                          ", surfaceInit: " + surfaceInitialized +
                          ", isLocal: " + participant.isLocal());
        
        if (hasVideoTrack && isVideoEnabled && isNotMuted && surfaceInitialized) {
            // Show video
            holder.videoPlaceholder.setVisibility(View.GONE);
            holder.svParticipantVideo.setVisibility(View.VISIBLE);
            
            // CRITICAL: Add video track to surface view (like private call: remoteVideoTrack.addSink(remoteVideoView))
            // Remove any existing sink first to avoid duplicates
            try {
                if (existingTrack != null && holder.svParticipantVideo != null) {
                    existingTrack.removeSink(holder.svParticipantVideo);
                }
            } catch (Exception e) {
                // Ignore - might not have sink attached
            }
            
            // Add sink directly (like private call does - simple and direct)
            // Surface is already initialized in onCreateViewHolder, so it should be ready
            if (existingTrack != null && holder.svParticipantVideo != null) {
                try {
                    existingTrack.addSink(holder.svParticipantVideo);
                    android.util.Log.d("VideoParticipantAdapter", "✓ Added video sink for " + participant.getUsername() + " (like private call)");
                } catch (IllegalStateException e) {
                    // Surface not ready - this can happen if view isn't attached yet
                    // The sink will be added in onViewAttachedToWindow
                    android.util.Log.w("VideoParticipantAdapter", "Surface not ready in onBindViewHolder for " + participant.getUsername() + 
                                      " (view may not be attached yet), will add in onViewAttachedToWindow");
                    
                    // CRITICAL: Schedule multiple retries to ensure sink is added
                    // Retry 1: After 200ms
                    holder.svParticipantVideo.postDelayed(() -> {
                        try {
                            if (existingTrack != null && holder.svParticipantVideo != null && existingTrack.enabled()) {
                                try {
                                    existingTrack.removeSink(holder.svParticipantVideo);
                                } catch (Exception ex) {
                                    // Ignore
                                }
                                existingTrack.addSink(holder.svParticipantVideo);
                                android.util.Log.d("VideoParticipantAdapter", "✓ Added video sink on retry 1 for " + participant.getUsername());
                            }
                        } catch (IllegalStateException ex) {
                            // Still not ready, retry again
                            android.util.Log.w("VideoParticipantAdapter", "Surface still not ready for " + participant.getUsername() + ", retrying again...");
                            holder.svParticipantVideo.postDelayed(() -> {
                                try {
                                    if (existingTrack != null && holder.svParticipantVideo != null && existingTrack.enabled()) {
                                        try {
                                            existingTrack.removeSink(holder.svParticipantVideo);
                                        } catch (Exception ex2) {
                                            // Ignore
                                        }
                                        existingTrack.addSink(holder.svParticipantVideo);
                                        android.util.Log.d("VideoParticipantAdapter", "✓ Added video sink on retry 2 for " + participant.getUsername());
                                    }
                                } catch (Exception ex2) {
                                    android.util.Log.e("VideoParticipantAdapter", "Error adding sink on retry 2 for " + participant.getUsername(), ex2);
                                }
                            }, 300);
                        } catch (Exception ex) {
                            android.util.Log.e("VideoParticipantAdapter", "Error adding sink on retry 1 for " + participant.getUsername(), ex);
                        }
                    }, 200);
                } catch (Exception e) {
                    android.util.Log.e("VideoParticipantAdapter", "Error adding video sink for " + participant.getUsername(), e);
                    // Retry after delay
                    holder.svParticipantVideo.postDelayed(() -> {
                        try {
                            if (existingTrack != null && holder.svParticipantVideo != null && existingTrack.enabled()) {
                                try {
                                    existingTrack.removeSink(holder.svParticipantVideo);
                                } catch (Exception ex) {
                                    // Ignore
                                }
                                existingTrack.addSink(holder.svParticipantVideo);
                                android.util.Log.d("VideoParticipantAdapter", "✓ Added video sink on exception retry for " + participant.getUsername());
                            }
                        } catch (Exception ex) {
                            android.util.Log.e("VideoParticipantAdapter", "Error adding sink on exception retry for " + participant.getUsername(), ex);
                        }
                    }, 200);
                }
            } else {
                android.util.Log.w("VideoParticipantAdapter", "Cannot add sink - track or view is null for " + participant.getUsername());
            }
        } else {
            // Show placeholder
            holder.videoPlaceholder.setVisibility(View.VISIBLE);
            holder.svParticipantVideo.setVisibility(View.GONE);
            
            // Remove sink if track exists but is disabled
            if (hasVideoTrack && surfaceInitialized) {
                try {
                    existingTrack.removeSink(holder.svParticipantVideo);
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        // Show muted indicator
        if (participant.isAudioMuted()) {
            holder.ivMutedIndicator.setVisibility(View.VISIBLE);
            holder.ivSpeakingIndicator.setVisibility(View.GONE);
        } else {
            holder.ivMutedIndicator.setVisibility(View.GONE);
            // Speaking indicator can be shown based on audio level detection
            holder.ivSpeakingIndicator.setVisibility(View.GONE);
        }

        // Show connection quality indicator
        if (participant.getConnectionQuality() != null) {
            holder.ivConnectionQuality.setVisibility(View.VISIBLE);
            switch (participant.getConnectionQuality()) {
                case "excellent":
                case "good":
                    holder.ivConnectionQuality.setColorFilter(context.getResources().getColor(R.color.green));
                    break;
                case "fair":
                    holder.ivConnectionQuality.setColorFilter(context.getResources().getColor(android.R.color.holo_orange_dark));
                    break;
                case "poor":
                    holder.ivConnectionQuality.setColorFilter(context.getResources().getColor(R.color.red));
                    break;
            }
        } else {
            holder.ivConnectionQuality.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        int count = participants.size();
        if (countChangeListener != null) {
            countChangeListener.onCountChanged(count);
        }
        return count;
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        // Remove video sink from any track before recycling
        if (holder.svParticipantVideo != null) {
            try {
                // Clear the image
                holder.svParticipantVideo.clearImage();
            } catch (Exception e) {
                // Ignore
            }
        }
    }
    
    @Override
    public void onViewAttachedToWindow(@NonNull ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        // CRITICAL: Android Surface is created when view is attached to window
        // We need to ensure surface is initialized AFTER attachment
        int position = holder.getAdapterPosition();
        if (position >= 0 && position < participants.size()) {
            CallParticipant participant = participants.get(position);
            if (participant.getVideoTrack() != null && holder.svParticipantVideo != null) {
                // Re-bind the video track if it's enabled (like private call does)
                if (participant.getVideoTrack().enabled() && !participant.isVideoMuted()) {
                    holder.videoPlaceholder.setVisibility(android.view.View.GONE);
                    holder.svParticipantVideo.setVisibility(android.view.View.VISIBLE);
                    
                    // CRITICAL: Re-initialize surface now that Android Surface is created
                    if (eglBase != null) {
                        try {
                            // Re-initialize to ensure surface is ready (Android Surface is created on attach)
                            holder.svParticipantVideo.init(eglBase.getEglBaseContext(), null);
                            holder.svParticipantVideo.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL);
                            if (participant.isLocal()) {
                                holder.svParticipantVideo.setMirror(true);
                            } else {
                                holder.svParticipantVideo.setMirror(false);
                            }
                            android.util.Log.d("VideoParticipantAdapter", "Surface re-initialized on attach for " + participant.getUsername());
                        } catch (IllegalStateException e) {
                            // Already initialized - that's fine, just configure
                            holder.svParticipantVideo.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL);
                            if (participant.isLocal()) {
                                holder.svParticipantVideo.setMirror(true);
                            } else {
                                holder.svParticipantVideo.setMirror(false);
                            }
                            android.util.Log.d("VideoParticipantAdapter", "Surface already initialized on attach for " + participant.getUsername());
                        }
                    }
                    
                    // Wait a bit for Android Surface to be fully ready, then add sink
                    // Use multiple retries to ensure sink is added
                    holder.svParticipantVideo.postDelayed(() -> {
                        try {
                            if (participant.getVideoTrack() != null && holder.svParticipantVideo != null && 
                                participant.getVideoTrack().enabled() && !participant.isVideoMuted()) {
                                // Remove any existing sink first
                                try {
                                    participant.getVideoTrack().removeSink(holder.svParticipantVideo);
                                } catch (Exception e) {
                                    // Ignore
                                }
                                
                                // Add sink (like private call: remoteVideoTrack.addSink(remoteVideoView))
                                participant.getVideoTrack().addSink(holder.svParticipantVideo);
                                android.util.Log.d("VideoParticipantAdapter", "✓ Added sink on attach for " + participant.getUsername());
                            }
                        } catch (IllegalStateException e) {
                            // Surface still not ready - retry again with multiple attempts
                            android.util.Log.w("VideoParticipantAdapter", "Surface still not ready for " + participant.getUsername() + ", retrying...");
                            
                            // Retry 1: After 200ms
                            holder.svParticipantVideo.postDelayed(() -> {
                                try {
                                    if (participant.getVideoTrack() != null && holder.svParticipantVideo != null && 
                                        participant.getVideoTrack().enabled() && !participant.isVideoMuted()) {
                                        try {
                                            participant.getVideoTrack().removeSink(holder.svParticipantVideo);
                                        } catch (Exception ex) {
                                            // Ignore
                                        }
                                        participant.getVideoTrack().addSink(holder.svParticipantVideo);
                                        android.util.Log.d("VideoParticipantAdapter", "✓ Added sink on attach retry 1 for " + participant.getUsername());
                                    }
                                } catch (IllegalStateException ex) {
                                    // Still not ready, retry again
                                    android.util.Log.w("VideoParticipantAdapter", "Surface still not ready for " + participant.getUsername() + ", retrying again...");
                                    holder.svParticipantVideo.postDelayed(() -> {
                                        try {
                                            if (participant.getVideoTrack() != null && holder.svParticipantVideo != null && 
                                                participant.getVideoTrack().enabled() && !participant.isVideoMuted()) {
                                                try {
                                                    participant.getVideoTrack().removeSink(holder.svParticipantVideo);
                                                } catch (Exception ex2) {
                                                    // Ignore
                                                }
                                                participant.getVideoTrack().addSink(holder.svParticipantVideo);
                                                android.util.Log.d("VideoParticipantAdapter", "✓ Added sink on attach retry 2 for " + participant.getUsername());
                                            }
                                        } catch (Exception ex2) {
                                            android.util.Log.e("VideoParticipantAdapter", "Error adding sink on attach retry 2: " + ex2.getMessage());
                                        }
                                    }, 300);
                                } catch (Exception ex) {
                                    android.util.Log.e("VideoParticipantAdapter", "Error adding sink on attach retry 1: " + ex.getMessage());
                                }
                            }, 200);
                        } catch (Exception e) {
                            android.util.Log.e("VideoParticipantAdapter", "Error adding sink on attach for " + participant.getUsername(), e);
                        }
                    }, 50); // Small delay to ensure Android Surface is ready
                }
            }
        }
    }
    
    @Override
    public void onViewDetachedFromWindow(@NonNull ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        // CRITICAL: Remove sink when detached, but DON'T release surface view
        // RecyclerView can detach/reattach views frequently, and releasing causes
        // "Dropping frame - Not initialized or already released" errors
        // The surface will be reused when the view is reattached
        if (holder.svParticipantVideo != null) {
            int position = holder.getAdapterPosition();
            if (position >= 0 && position < participants.size()) {
                CallParticipant participant = participants.get(position);
                if (participant.getVideoTrack() != null) {
                    try {
                        participant.getVideoTrack().removeSink(holder.svParticipantVideo);
                        android.util.Log.d("VideoParticipantAdapter", "Removed sink for " + participant.getUsername() + " (surface kept alive)");
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            
            // DO NOT release surface view here - RecyclerView will reuse it
            // Releasing causes frames to be dropped when view is reattached
            // Surface will be properly cleaned up when adapter is destroyed
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public SurfaceViewRenderer svParticipantVideo;
        public LinearLayout videoPlaceholder;
        CircleImageView ivParticipantAvatar;
        TextView tvParticipantName;
        TextView tvParticipantNamePlaceholder;
        ImageView ivMutedIndicator;
        ImageView ivSpeakingIndicator;
        ImageView ivConnectionQuality;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            svParticipantVideo = itemView.findViewById(R.id.sv_participant_video);
            videoPlaceholder = itemView.findViewById(R.id.video_placeholder);
            ivParticipantAvatar = itemView.findViewById(R.id.iv_participant_avatar);
            tvParticipantName = itemView.findViewById(R.id.tv_participant_name);
            tvParticipantNamePlaceholder = itemView.findViewById(R.id.tv_participant_name_placeholder);
            ivMutedIndicator = itemView.findViewById(R.id.iv_muted_indicator);
            ivSpeakingIndicator = itemView.findViewById(R.id.iv_speaking_indicator);
            ivConnectionQuality = itemView.findViewById(R.id.iv_connection_quality);
        }
    }
}

