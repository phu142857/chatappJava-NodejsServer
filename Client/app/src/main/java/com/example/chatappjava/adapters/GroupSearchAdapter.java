package com.example.chatappjava.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chatappjava.R;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.utils.AvatarManager;
import com.example.chatappjava.config.ServerConfig;
import com.squareup.picasso.Picasso;
import java.util.List;

public class GroupSearchAdapter extends RecyclerView.Adapter<GroupSearchAdapter.GroupViewHolder> {
    
    private final Context context;
    private List<Chat> groups;
    private OnGroupClickListener listener;
    private boolean discoverMode = false; // when true, show join/request buttons
    private boolean forwardMode = false;  // when true (in "forward" mode on groups tab), show Forward button
    private final AvatarManager avatarManager;
    
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
    public void setDiscoverMode(boolean discover) {
        if (this.discoverMode == discover) {
            return;
        }
        this.discoverMode = discover;
        int count = getItemCount();
        if (count > 0) {
            notifyItemRangeChanged(0, count);
        }
    }

    public void setForwardMode(boolean forward) {
        if (this.forwardMode == forward) {
            return;
        }
        this.forwardMode = forward;
        int count = getItemCount();
        if (count > 0) {
            notifyItemRangeChanged(0, count);
        }
    }

    public void updateGroups(List<Chat> newGroups) {
        if (newGroups == null) {
            newGroups = new java.util.ArrayList<>();
        }
        int oldSize = groups != null ? groups.size() : 0;
        groups = newGroups;
        int newSize = groups.size();
        if (oldSize > newSize) {
            notifyItemRangeRemoved(newSize, oldSize - newSize);
        }
        if (newSize > oldSize) {
            notifyItemRangeInserted(oldSize, newSize - oldSize);
        }
        int overlap = Math.min(oldSize, newSize);
        if (overlap > 0) {
            notifyItemRangeChanged(0, overlap);
        }
    }

    public void notifyGroupItemChanged(Chat group) {
        if (groups == null || group == null || group.getId() == null) {
            return;
        }
        for (int i = 0; i < groups.size(); i++) {
            Chat item = groups.get(i);
            if (item != null && group.getId().equals(item.getId())) {
                notifyItemChanged(i);
                return;
            }
        }
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
        private final ImageView ivGroupAvatar;
        private final TextView tvGroupName;
        private final TextView tvMemberCount;
        private final TextView tvLastMessage;
        private final android.widget.Button btnAction;
        
        public GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            ivGroupAvatar = itemView.findViewById(R.id.iv_group_avatar);
            tvGroupName = itemView.findViewById(R.id.tv_group_name);
            tvMemberCount = itemView.findViewById(R.id.tv_member_count);
            tvLastMessage = itemView.findViewById(R.id.tv_last_message);
            btnAction = itemView.findViewById(R.id.btn_group_action);
            
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onGroupClick(groups.get(position));
                    }
                }
            });
        }
        
        @SuppressLint("SetTextI18n")
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
                    tvMemberCount.setText(context.getString(R.string.group_members_count, group.getParticipantCount()));
                }
            }
            
            // Set last message preview
            if (tvLastMessage != null) {
                if (group.getLastMessage() != null && !group.getLastMessage().isEmpty()) {
                    tvLastMessage.setText(group.getLastMessage());
                } else {
                    tvLastMessage.setText(R.string.group_no_messages_yet);
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

            // Action button visibility and text for Discover / Forward
            if (discoverMode && btnAction != null) {
                btnAction.setVisibility(View.VISIBLE);
                // Determine pending status from Chat model field
                String joinStatus = group.getJoinRequestStatus();
                android.util.Log.d("GroupSearchAdapter", "Group " + group.getName() + " joinRequestStatus: " + joinStatus + ", isPublic: " + group.isPublicGroup());

                if (joinStatus != null && joinStatus.equals("pending")) {
                    btnAction.setText(R.string.group_action_cancel);
                    btnAction.setEnabled(true);
                    btnAction.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_reject_button_modern));
                    btnAction.setTextColor(ContextCompat.getColor(context, R.color.text_white));
                    android.util.Log.d("GroupSearchAdapter", "Set button to Cancel Request (red)");
                } else if (group.isPublicGroup()) {
                    btnAction.setText(R.string.group_action_join);
                    btnAction.setEnabled(true);
                    btnAction.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_button_normal_ripple));
                    btnAction.setTextColor(ContextCompat.getColor(context, R.color.md3_on_primary));
                    android.util.Log.d("GroupSearchAdapter", "Set button to Join (blue)");
                } else {
                    btnAction.setText(R.string.group_action_request);
                    btnAction.setEnabled(true);
                    btnAction.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_button_normal_ripple));
                    btnAction.setTextColor(ContextCompat.getColor(context, R.color.md3_on_primary));
                    android.util.Log.d("GroupSearchAdapter", "Set button to Request (blue)");
                }

                btnAction.setOnClickListener(v -> {
                    if (listener != null && btnAction.isEnabled()) {
                        listener.onGroupClick(group); // delegate action; activity decides join/request/cancel
                    }
                });
            } else if (forwardMode && btnAction != null) {
                // Forward mode on My Groups tab: show a Forward button
                btnAction.setVisibility(View.VISIBLE);
                btnAction.setText(R.string.group_action_forward);
                btnAction.setEnabled(true);
                btnAction.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_button_normal_ripple));
                btnAction.setTextColor(ContextCompat.getColor(context, R.color.md3_on_primary));
                btnAction.setOnClickListener(v -> {
                    if (listener != null) listener.onGroupClick(group);
                });
            } else if (btnAction != null) {
                btnAction.setVisibility(View.GONE);
            }
        }
    }
}
