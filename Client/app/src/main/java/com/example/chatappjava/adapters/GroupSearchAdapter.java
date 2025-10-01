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
import java.util.List;

public class GroupSearchAdapter extends RecyclerView.Adapter<GroupSearchAdapter.GroupViewHolder> {
    
    private Context context;
    private List<Chat> groups;
    private OnGroupClickListener listener;
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
        
        public GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            ivGroupAvatar = itemView.findViewById(R.id.iv_group_avatar);
            tvGroupName = itemView.findViewById(R.id.tv_group_name);
            tvMemberCount = itemView.findViewById(R.id.tv_member_count);
            tvLastMessage = itemView.findViewById(R.id.tv_last_message);
            
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
            
            // Load group avatar
            String avatarUrl = group.getAvatar();
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                avatarManager.loadAvatar(avatarUrl, ivGroupAvatar, R.drawable.ic_group_placeholder);
            } else {
                ivGroupAvatar.setImageResource(R.drawable.ic_group_placeholder);
            }
        }
    }
}
