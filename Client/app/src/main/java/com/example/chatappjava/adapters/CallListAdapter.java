package com.example.chatappjava.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.models.Call;
import com.example.chatappjava.utils.AvatarManager;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class CallListAdapter extends RecyclerView.Adapter<CallListAdapter.CallViewHolder> {
    
    private final Context context;
    private final List<Call> callList;
    private OnCallClickListener onCallClickListener;
    private final AvatarManager avatarManager;
    private String currentUserId;
    
    public interface OnCallClickListener {
        void onCallItemClick(Call call);
        void onCallActionClick(Call call);
    }
    
    public CallListAdapter(Context context) {
        this.context = context;
        this.callList = new ArrayList<>();
        this.avatarManager = AvatarManager.getInstance(context);
    }
    
    public void setOnCallClickListener(OnCallClickListener listener) {
        this.onCallClickListener = listener;
    }
    
    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = currentUserId;
    }
    
    @SuppressLint("NotifyDataSetChanged")
    public void updateCalls(List<Call> newCallList) {
        this.callList.clear();
        this.callList.addAll(newCallList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_call, parent, false);
        return new CallViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull CallViewHolder holder, int position) {
        Call call = callList.get(position);
        holder.bind(call);
    }
    
    @Override
    public int getItemCount() {
        return callList.size();
    }
    
    class CallViewHolder extends RecyclerView.ViewHolder {
        private final CircleImageView ivCallAvatar;
        private final TextView tvCallName;
        private final TextView tvCallTypeIcon;
        private final TextView tvCallStatus;
        private final TextView tvCallDuration;
        private final TextView tvCallTime;
        private final ImageButton btnCallAction;
        
        public CallViewHolder(@NonNull View itemView) {
            super(itemView);
            
            ivCallAvatar = itemView.findViewById(R.id.iv_call_avatar);
            tvCallName = itemView.findViewById(R.id.tv_call_name);
            tvCallTypeIcon = itemView.findViewById(R.id.tv_call_type_icon);
            tvCallStatus = itemView.findViewById(R.id.tv_call_status);
            tvCallDuration = itemView.findViewById(R.id.tv_call_duration);
            tvCallTime = itemView.findViewById(R.id.tv_call_time);
            btnCallAction = itemView.findViewById(R.id.btn_call_action);

            itemView.setOnClickListener(v -> {
                if (onCallClickListener != null) {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        onCallClickListener.onCallItemClick(callList.get(position));
                    }
                }
            });
            
            btnCallAction.setOnClickListener(v -> {
                if (onCallClickListener != null) {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        onCallClickListener.onCallActionClick(callList.get(position));
                    }
                }
            });
        }
        
        public void bind(Call call) {
            // Clean up avatar ImageView first to prevent showing wrong avatar from recycled ViewHolder
            if (ivCallAvatar != null) {
                com.squareup.picasso.Picasso.get().cancelRequest(ivCallAvatar);
                ivCallAvatar.setTag(null);
                // Show placeholder immediately while loading
                ivCallAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }
            
            tvCallName.setText(call.getDisplayName(currentUserId));
            tvCallTypeIcon.setText(call.getCallTypeIcon());
            tvCallStatus.setText(call.getStatusText());

            if (call.isEnded() && call.getDuration() > 0) {
                tvCallDuration.setText(call.getFormattedDuration());
                tvCallDuration.setVisibility(View.VISIBLE);
            } else {
                tvCallDuration.setVisibility(View.GONE);
            }

            tvCallTime.setText(call.getFormattedTimeWithDate());

            String avatarUrl;
            if (call.isGroupCall()) {
                avatarUrl = call.getCallerAvatar(); // For group calls, use caller avatar
            } else {
                avatarUrl = call.getOtherParticipantAvatar(currentUserId); // For private calls, use other participant's avatar
            }

            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                android.util.Log.d("CallListAdapter", "Loading call avatar: " + avatarUrl);

                // Construct full URL if needed (same as ChatListAdapter)
                if (!avatarUrl.startsWith("http")) {
                    // Ensure avatar starts with / if it doesn't already
                    String avatarPath = avatarUrl.startsWith("/") ? avatarUrl : "/" + avatarUrl;
                    avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                               ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarPath;
                    android.util.Log.d("CallListAdapter", "Constructed full URL: " + avatarUrl);
                }
                
                // Use AvatarManager to load avatar (same as ChatListAdapter)
                if (avatarManager != null) {
                    avatarManager.loadAvatar(
                        avatarUrl, 
                        ivCallAvatar, 
                        R.drawable.ic_profile_placeholder
                    );
                } else {
                    // Fallback to Picasso directly if AvatarManager is not available
                    android.util.Log.w("CallListAdapter", "AvatarManager is null, using Picasso directly");
                    com.squareup.picasso.Picasso.get()
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .fit()
                        .centerCrop()
                        .into(ivCallAvatar);
                }
            } else {
                android.util.Log.d("CallListAdapter", "No avatar URL, using default");
                if (ivCallAvatar != null) {
                    ivCallAvatar.setImageResource(R.drawable.ic_profile_placeholder);
                }
            }
            
            // Set status color
            int statusColor = getStatusColor(call.getStatus());
            tvCallStatus.setTextColor(statusColor);
            
            // Set call action button
            setCallActionButton(call);
        }
        
        private int getStatusColor(String status) {
            switch (status) {
                case "ended":
                    return ContextCompat.getColor(context, android.R.color.holo_green_dark);
                case "declined":
                case "missed":
                case "cancelled":
                    return ContextCompat.getColor(context, android.R.color.holo_red_dark);
                default:
                    return ContextCompat.getColor(context, android.R.color.darker_gray);
            }
        }
        
        private void setCallActionButton(Call call) {
            // Set appropriate icon based on call type
            if (call.isVideoCall()) {
                btnCallAction.setImageResource(R.drawable.ic_videocam);
            } else {
                btnCallAction.setImageResource(R.drawable.ic_call);
            }
            
            // Set content description
            String callType = call.isVideoCall() ? "Video call" : "Audio call";
            btnCallAction.setContentDescription(callType);
        }
    }
}
