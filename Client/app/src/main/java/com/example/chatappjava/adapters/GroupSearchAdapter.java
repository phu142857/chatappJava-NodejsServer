package com.example.chatappjava.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chatappjava.R;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.utils.AvatarManager;
import com.example.chatappjava.config.ServerConfig;
import com.squareup.picasso.Picasso;
import java.util.List;

public class GroupSearchAdapter extends RecyclerView.Adapter<GroupSearchAdapter.GroupViewHolder> {
    
    private Context context;
    private List<Chat> groups;
    private OnGroupClickListener listener;
    private boolean discoverMode = false; // when true, show join/request buttons
    private AvatarManager avatarManager;
    
    public interface OnGroupClickListener {
        void onGroupClick(Chat group);
    }
    
    public GroupSearchAdapter(Context context, List<Chat> groups) {
        this.context = context;
        this.groups = groups;
        this.avatarManager = AvatarManager.getInstance(context);
    }
    
    public void setOnGroupClickListener(OnGroupClickListener listener) {
        this.listener = listener;
    }
    public void setDiscoverMode(boolean discover) { this.discoverMode = discover; notifyDataSetChanged(); }
    
    public void updateGroups(List<Chat> newGroups) {
        this.groups = newGroups;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_group_search, parent, false);
        return new GroupViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        Chat group = groups.get(position);
        holder.bind(group);
    }
    
    @Override
    public int getItemCount() {
        return groups.size();
    }
    
    class GroupViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivGroupAvatar;
        private TextView tvGroupName;
        private TextView tvMemberCount;
        private TextView tvLastMessage;
        private android.widget.Button btnAction;
        
        public GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            ivGroupAvatar = itemView.findViewById(R.id.iv_group_avatar);
            tvGroupName = itemView.findViewById(R.id.tv_group_name);
            tvMemberCount = itemView.findViewById(R.id.tv_member_count);
            tvLastMessage = itemView.findViewById(R.id.tv_last_message);
            btnAction = itemView.findViewById(R.id.btn_group_action);
            
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onGroupClick(groups.get(position));
                    }
                }
            });
        }
        
        public void bind(Chat group) {
            if (tvGroupName != null) {
                tvGroupName.setText(group.getName());
            }
            
            // In Discover mode, hide member count
            if (discoverMode) {
                if (tvMemberCount != null) {
                    tvMemberCount.setVisibility(View.GONE);
                }
            } else {
                if (tvMemberCount != null) {
                    tvMemberCount.setVisibility(View.VISIBLE);
                    tvMemberCount.setText(group.getParticipantCount() + " members");
                }
            }
            
            // Set last message preview
            if (tvLastMessage != null) {
                if (group.getLastMessage() != null && !group.getLastMessage().isEmpty()) {
                    tvLastMessage.setText(group.getLastMessage());
                } else {
                    tvLastMessage.setText("No messages yet");
                }
            }
            
            // Load group avatar (ensure full URL if server returns relative path)
            if (ivGroupAvatar != null) {
                String avatarUrl = group.getAvatar();
                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    if (!(avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://"))) {
                        String path = avatarUrl.startsWith("/") ? avatarUrl : "/" + avatarUrl;
                        avatarUrl = "http://" + ServerConfig.getServerIp() + ":" + ServerConfig.getServerPort() + path;
                    }
                    try {
                        avatarManager.loadAvatar(avatarUrl, ivGroupAvatar, R.drawable.ic_group_avatar);
                    } catch (Exception ignored) {}
                    // Also attempt Picasso as fallback to maximize success rate
                    try {
                        Picasso.get()
                                .load(avatarUrl)
                                .placeholder(R.drawable.ic_group_avatar)
                                .error(R.drawable.ic_group_avatar)
                                .into(ivGroupAvatar);
                    } catch (Exception ignored) {
                        ivGroupAvatar.setImageResource(R.drawable.ic_group_avatar);
                    }
                } else {
                    ivGroupAvatar.setImageResource(R.drawable.ic_group_avatar);
                }
            }

            // Action button visibility and text for Discover
            if (discoverMode && btnAction != null) {
                btnAction.setVisibility(View.VISIBLE);
                // Determine pending status from Chat model field
                String joinStatus = group.getJoinRequestStatus();
                android.util.Log.d("GroupSearchAdapter", "Group " + group.getName() + " joinRequestStatus: " + joinStatus + ", isPublic: " + group.isPublicGroup());

                if (joinStatus != null && joinStatus.equals("pending")) {
                    btnAction.setText("Cancel Request");
                    btnAction.setEnabled(true);
                    btnAction.setBackgroundColor(context.getResources().getColor(android.R.color.holo_red_light));
                    android.util.Log.d("GroupSearchAdapter", "Set button to Cancel Request (red)");
                } else if (group.isPublicGroup()) {
                    btnAction.setText("Join");
                    btnAction.setEnabled(true);
                    btnAction.setBackgroundColor(context.getResources().getColor(R.color.primary_color));
                    android.util.Log.d("GroupSearchAdapter", "Set button to Join (blue)");
                } else {
                    btnAction.setText("Request");
                    btnAction.setEnabled(true);
                    btnAction.setBackgroundColor(context.getResources().getColor(R.color.primary_color));
                    android.util.Log.d("GroupSearchAdapter", "Set button to Request (blue)");
                }

                btnAction.setOnClickListener(v -> {
                    if (listener != null && btnAction.isEnabled()) {
                        listener.onGroupClick(group); // delegate action; activity decides join/request/cancel
                    }
                });
            } else if (btnAction != null) {
                btnAction.setVisibility(View.GONE);
            }
        }
    }
}
