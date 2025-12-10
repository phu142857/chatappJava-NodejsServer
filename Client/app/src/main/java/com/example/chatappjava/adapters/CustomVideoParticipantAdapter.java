package com.example.chatappjava.adapters;

import android.content.Context;
import android.graphics.Bitmap;
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
import com.example.chatappjava.utils.VideoFrameEncoder;
import com.squareup.picasso.Picasso;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Custom adapter to display video participants without WebRTC
 * Uses ImageView to display decoded frames
 */
public class CustomVideoParticipantAdapter extends RecyclerView.Adapter<CustomVideoParticipantAdapter.ViewHolder> {
    
    private Context context;
    private List<CallParticipant> participants;
    private Map<String, Bitmap> videoFrames; // Cache of video frames by userId
    
    public CustomVideoParticipantAdapter(Context context, List<CallParticipant> participants) {
        this.context = context;
        this.participants = participants;
        this.videoFrames = new HashMap<>();
    }
    
    /**
     * Update video frame for a participant
     */
    public void updateVideoFrame(String userId, String base64Frame) {
        updateVideoFrame(userId, base64Frame, false);
    }
    
    /**
     * Update video frame for a participant with front camera flag
     * @param userId Participant user ID
     * @param base64Frame Base64 encoded frame
     * @param isFrontCamera True if using front camera (for mirror effect)
     */
    public void updateVideoFrame(String userId, String base64Frame, boolean isFrontCamera) {
        Bitmap bitmap = VideoFrameEncoder.decodeFrame(base64Frame);
        if (bitmap != null) {
            // CRITICAL FIX: Mirror bitmap horizontally for front camera
            // This fixes the 180-degree rotation issue for front camera
            if (isFrontCamera) {
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postScale(-1, 1); // Mirror horizontally
                Bitmap mirroredBitmap = Bitmap.createBitmap(bitmap, 0, 0, 
                                                           bitmap.getWidth(), bitmap.getHeight(), 
                                                           matrix, true);
                if (mirroredBitmap != bitmap) {
                    bitmap.recycle();
                }
                bitmap = mirroredBitmap;
            }
            
            // CRITICAL FIX: Always update video frame, even if participant is marked as video muted
            // This prevents race conditions where frames are received but not displayed
            // The onBindViewHolder will handle showing/hiding based on isVideoMuted state
            // Release old bitmap if it exists
            Bitmap oldBitmap = videoFrames.get(userId);
            if (oldBitmap != null && !oldBitmap.isRecycled()) {
                oldBitmap.recycle();
            }
            
            videoFrames.put(userId, bitmap);
            
            // Find participant index and notify change
            // CRITICAL: Always notify even if video is muted, so UI can update correctly
            for (int i = 0; i < participants.size(); i++) {
                if (participants.get(i).getUserId() != null && 
                    participants.get(i).getUserId().equals(userId)) {
                    notifyItemChanged(i);
                    break;
                }
            }
        }
    }
    
    /**
     * Clear video frame for a specific user
     */
    public void clearVideoFrameForUser(String userId) {
        if (userId != null && videoFrames.containsKey(userId)) {
            Bitmap bitmap = videoFrames.remove(userId);
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            // Notify adapter to update view
            for (int i = 0; i < participants.size(); i++) {
                if (participants.get(i).getUserId() != null && 
                    participants.get(i).getUserId().equals(userId)) {
                    notifyItemChanged(i);
                    break;
                }
            }
        }
    }
    
    /**
     * Clear video frames
     */
    public void clearVideoFrames() {
        for (Bitmap bitmap : videoFrames.values()) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        videoFrames.clear();
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_custom_video_participant, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CallParticipant participant = participants.get(position);
        
        // Set participant name
        holder.tvParticipantName.setText(participant.getUsername());
        // Removed tvParticipantNamePlaceholder - no longer needed
        
        // Display video frame if available AND video is not muted
        String userId = participant.getUserId();
        boolean isVideoMuted = participant.isVideoMuted();
        
        if (!isVideoMuted && userId != null && videoFrames.containsKey(userId)) {
            // Video is on and frame is available - show video frame
            Bitmap frame = videoFrames.get(userId);
            if (frame != null && !frame.isRecycled()) {
                holder.ivVideoFrame.setImageBitmap(frame);
                holder.ivVideoFrame.setVisibility(View.VISIBLE);
                holder.videoPlaceholder.setVisibility(View.GONE);
            } else {
                // Frame recycled, show placeholder
                holder.ivVideoFrame.setVisibility(View.GONE);
                holder.videoPlaceholder.setVisibility(View.VISIBLE);
            }
        } else {
            // Video is muted or no frame available - show avatar placeholder
            holder.ivVideoFrame.setVisibility(View.GONE);
            holder.videoPlaceholder.setVisibility(View.VISIBLE);
            
            // CRITICAL FIX: DO NOT clear video frame from cache in onBindViewHolder
            // This causes race conditions where frames are deleted while still being received
            // Video frames should only be cleared explicitly via clearVideoFrameForUser()
            // when the user actually turns off their camera
        }
        
        // CRITICAL FIX: Always load avatar (even when video is on, it's used in placeholder)
        // Load avatar - Construct full URL if needed
        String avatarUrl = participant.getAvatar();
        if (avatarUrl != null && !avatarUrl.isEmpty() && !avatarUrl.trim().isEmpty()) {
            // Construct full URL if it's a relative path (like other adapters)
            if (!avatarUrl.startsWith("http")) {
                // Ensure avatar starts with / if it doesn't already
                String avatarPath = avatarUrl.startsWith("/") ? avatarUrl : "/" + avatarUrl;
                avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                           ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarPath;
            }
            
            // Load avatar with Picasso - force reload to ensure it displays
            Picasso.get()
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .into(holder.ivParticipantAvatar);
        } else {
            // No avatar URL, use placeholder
            holder.ivParticipantAvatar.setImageResource(R.drawable.ic_profile_placeholder);
        }
        
        // Show muted indicator
        if (participant.isAudioMuted()) {
            holder.ivMutedIndicator.setVisibility(View.VISIBLE);
        } else {
            holder.ivMutedIndicator.setVisibility(View.GONE);
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
        return participants.size();
    }
    
    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        // Clear ImageView to free memory
        holder.ivVideoFrame.setImageBitmap(null);
    }
    
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView ivVideoFrame;
        public LinearLayout videoPlaceholder;
        public CircleImageView ivParticipantAvatar;
        public TextView tvParticipantName;
        public ImageView ivMutedIndicator;
        public ImageView ivConnectionQuality;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivVideoFrame = itemView.findViewById(R.id.iv_video_frame);
            videoPlaceholder = itemView.findViewById(R.id.video_placeholder);
            ivParticipantAvatar = itemView.findViewById(R.id.iv_participant_avatar);
            tvParticipantName = itemView.findViewById(R.id.tv_participant_name);
            ivMutedIndicator = itemView.findViewById(R.id.iv_muted_indicator);
            ivConnectionQuality = itemView.findViewById(R.id.iv_connection_quality);
        }
    }
}
