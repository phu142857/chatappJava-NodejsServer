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
            tvGroupName.setText(group.getName());
            tvMemberCount.setText(group.getParticipantCount() + " members");
            
            // Set last message preview
            if (group.getLastMessage() != null && !group.getLastMessage().isEmpty()) {
                tvLastMessage.setText(group.getLastMessage());
            } else {
                tvLastMessage.setText("No messages yet");
            }
            
            // Load group avatar (ensure full URL if server returns relative path)
            String avatarUrl = group.getAvatar();
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                if (!(avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://"))) {
                    String path = avatarUrl.startsWith("/") ? avatarUrl : "/" + avatarUrl;
                    avatarUrl = "http://" + ServerConfig.getServerIp() + ":" + ServerConfig.getServerPort() + path;
                }
                try {
                    avatarManager.loadAvatar(avatarUrl, ivGroupAvatar, R.drawable.ic_group_placeholder);
                } catch (Exception ignored) {}
                // Also attempt Picasso as fallback to maximize success rate
                try {
                    Picasso.get()
                            .load(avatarUrl)
                            .placeholder(R.drawable.ic_group_placeholder)
                            .error(R.drawable.ic_group_placeholder)
                            .into(ivGroupAvatar);
                } catch (Exception ignored) {
                    ivGroupAvatar.setImageResource(R.drawable.ic_group_placeholder);
                }
            } else {
                ivGroupAvatar.setImageResource(R.drawable.ic_group_placeholder);
            }

            // Action button visibility and text for Discover
            if (discoverMode) {
                btnAction.setVisibility(View.VISIBLE);
                // Determine pending status from Chat model field
                String joinStatus = group.getJoinRequestStatus();

                if (joinStatus != null && joinStatus.equals("pending")) {
                    btnAction.setText("Requested");
                    btnAction.setEnabled(false);
                } else if (group.isPublicGroup()) {
                    btnAction.setText("Join");
                    btnAction.setEnabled(true);
                } else {
                    btnAction.setText("Request");
                    btnAction.setEnabled(true);
                }

                btnAction.setOnClickListener(v -> {
                    if (listener != null && btnAction.isEnabled()) {
                        listener.onGroupClick(group); // delegate action; activity decides join/request
                    }
                });
            } else {
                btnAction.setVisibility(View.GONE);
            }
        }
    }
}
