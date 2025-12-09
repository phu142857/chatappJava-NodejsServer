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
        Bitmap bitmap = VideoFrameEncoder.decodeFrame(base64Frame);
        if (bitmap != null) {
            // Release old bitmap if it exists
            Bitmap oldBitmap = videoFrames.get(userId);
            if (oldBitmap != null && !oldBitmap.isRecycled()) {
                oldBitmap.recycle();
            }
            
            videoFrames.put(userId, bitmap);
            
            // Find participant index and notify change
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
        
        // Display video frame if available
        String userId = participant.getUserId();
        if (userId != null && videoFrames.containsKey(userId)) {
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
            // No frame available, show placeholder
            holder.ivVideoFrame.setVisibility(View.GONE);
            holder.videoPlaceholder.setVisibility(View.VISIBLE);
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
        public TextView tvParticipantNamePlaceholder;
        public ImageView ivMutedIndicator;
        public ImageView ivConnectionQuality;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivVideoFrame = itemView.findViewById(R.id.iv_video_frame);
            videoPlaceholder = itemView.findViewById(R.id.video_placeholder);
            ivParticipantAvatar = itemView.findViewById(R.id.iv_participant_avatar);
            tvParticipantName = itemView.findViewById(R.id.tv_participant_name);
            tvParticipantNamePlaceholder = itemView.findViewById(R.id.tv_participant_name_placeholder);
            ivMutedIndicator = itemView.findViewById(R.id.iv_muted_indicator);
            ivConnectionQuality = itemView.findViewById(R.id.iv_connection_quality);
        }
    }
}
