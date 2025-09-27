package com.example.chatappjava.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.models.Call;
import com.example.chatappjava.utils.AvatarManager;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class CallListAdapter extends RecyclerView.Adapter<CallListAdapter.CallViewHolder> {
    
    private Context context;
    private List<Call> callList;
    private OnCallClickListener onCallClickListener;
    private AvatarManager avatarManager;
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
    
    public void updateCalls(List<Call> newCallList) {
        this.callList.clear();
        this.callList.addAll(newCallList);
        notifyDataSetChanged();
    }
    
    public void addCall(Call call) {
        this.callList.add(0, call); // Add to beginning
        notifyItemInserted(0);
    }
    
    public void updateCall(Call updatedCall) {
        for (int i = 0; i < callList.size(); i++) {
            if (callList.get(i).getCallId().equals(updatedCall.getCallId())) {
                callList.set(i, updatedCall);
                notifyItemChanged(i);
                break;
            }
        }
    }
    
    public void removeCall(String callId) {
        for (int i = 0; i < callList.size(); i++) {
            if (callList.get(i).getCallId().equals(callId)) {
                callList.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
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
        private CircleImageView ivCallAvatar;
        private TextView tvCallName;
        private TextView tvCallTypeIcon;
        private TextView tvCallStatus;
        private TextView tvCallDuration;
        private TextView tvCallTime;
        private ImageButton btnCallAction;
        
        public CallViewHolder(@NonNull View itemView) {
            super(itemView);
            
            ivCallAvatar = itemView.findViewById(R.id.iv_call_avatar);
            tvCallName = itemView.findViewById(R.id.tv_call_name);
            tvCallTypeIcon = itemView.findViewById(R.id.tv_call_type_icon);
            tvCallStatus = itemView.findViewById(R.id.tv_call_status);
            tvCallDuration = itemView.findViewById(R.id.tv_call_duration);
            tvCallTime = itemView.findViewById(R.id.tv_call_time);
            btnCallAction = itemView.findViewById(R.id.btn_call_action);
            
            // Set click listeners
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onCallClickListener != null) {
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            onCallClickListener.onCallItemClick(callList.get(position));
                        }
                    }
                }
            });
            
            btnCallAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onCallClickListener != null) {
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            onCallClickListener.onCallActionClick(callList.get(position));
                        }
                    }
                }
            });
        }
        
        public void bind(Call call) {
            // Set name - use currentUserId to show the other participant's name
            tvCallName.setText(call.getDisplayName(currentUserId));
            
            // Set call type icon
            tvCallTypeIcon.setText(call.getCallTypeIcon());
            
            // Set status
            tvCallStatus.setText(call.getStatusText());
            
            // Set duration
            if (call.isEnded() && call.getDuration() > 0) {
                tvCallDuration.setText(call.getFormattedDuration());
                tvCallDuration.setVisibility(View.VISIBLE);
            } else {
                tvCallDuration.setVisibility(View.GONE);
            }
            
            // Set time
            tvCallTime.setText(call.getFormattedTime());
            
            // Set avatar - use other participant's avatar for private calls
            String avatarUrl;
            if (call.isGroupCall()) {
                avatarUrl = call.getCallerAvatar(); // For group calls, use caller avatar
            } else {
                avatarUrl = call.getOtherParticipantAvatar(currentUserId); // For private calls, use other participant's avatar
            }
            
            // Load avatar using AvatarManager (same logic as ChatListAdapter)
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                android.util.Log.d("CallListAdapter", "Loading call avatar: " + avatarUrl);
                
                // Construct full URL if needed (like ChatListAdapter logic)
                if (!avatarUrl.startsWith("http")) {
                    avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                               ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
                    android.util.Log.d("CallListAdapter", "Constructed full URL: " + avatarUrl);
                }
                
                avatarManager.loadAvatar(
                    avatarUrl, 
                    ivCallAvatar, 
                    R.drawable.default_avatar
                );
            } else {
                android.util.Log.d("CallListAdapter", "No avatar URL, using default");
                ivCallAvatar.setImageResource(R.drawable.default_avatar);
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
                    return context.getResources().getColor(android.R.color.holo_green_dark);
                case "declined":
                case "missed":
                case "cancelled":
                    return context.getResources().getColor(android.R.color.holo_red_dark);
                default:
                    return context.getResources().getColor(android.R.color.darker_gray);
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
