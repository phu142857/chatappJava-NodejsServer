package com.example.chatappjava.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chatappjava.R;
import com.example.chatappjava.models.User;
import java.util.List;

public class SelectableUserAdapter extends RecyclerView.Adapter<SelectableUserAdapter.VH> {

    public interface OnSelectionChangedListener {
        void onSelectionChanged(String userId, boolean selected);
    }

    private final List<User> users;
    private final OnSelectionChangedListener listener;

    public SelectableUserAdapter(List<User> users, OnSelectionChangedListener listener) {
        this.users = users;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_selectable, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        User u = users.get(position);
        holder.tvName.setText(u.getDisplayName());
        holder.tvUsername.setText("@" + u.getUsername());
        
        // Load avatar - handle URL like other adapters
        if (u.getAvatar() != null && !u.getAvatar().isEmpty()) {
            String avatarUrl = u.getAvatar();
            android.util.Log.d("SelectableUserAdapter", "Loading user avatar: " + avatarUrl);
            
            // Construct full URL if needed
            if (!avatarUrl.startsWith("http")) {
                avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                           ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
                android.util.Log.d("SelectableUserAdapter", "Constructed full URL: " + avatarUrl);
            }
            
            try {
                // Try AvatarManager first
                com.example.chatappjava.utils.AvatarManager.getInstance(holder.itemView.getContext())
                        .loadAvatar(avatarUrl, holder.iv, R.drawable.ic_profile_placeholder);
                
                // Backup: Also try Picasso directly
                com.squareup.picasso.Picasso.get()
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .into(holder.iv);
                        
            } catch (Exception e) {
                android.util.Log.e("SelectableUserAdapter", "Error loading avatar: " + e.getMessage());
                // Fallback to direct Picasso load
                try {
                    com.squareup.picasso.Picasso.get()
                            .load(avatarUrl)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .error(R.drawable.ic_profile_placeholder)
                            .into(holder.iv);
                } catch (Exception e2) {
                    android.util.Log.e("SelectableUserAdapter", "Picasso also failed: " + e2.getMessage());
                    holder.iv.setImageResource(R.drawable.ic_profile_placeholder);
                }
            }
        } else {
            android.util.Log.d("SelectableUserAdapter", "No avatar URL, using placeholder");
            holder.iv.setImageResource(R.drawable.ic_profile_placeholder);
        }
        
        holder.cb.setOnCheckedChangeListener(null);
        holder.cb.setChecked(false);
        holder.cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) listener.onSelectionChanged(u.getId(), isChecked);
        });
        holder.itemView.setOnClickListener(v -> holder.cb.performClick());
    }

    @Override
    public int getItemCount() { return users.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView iv;
        TextView tvName;
        TextView tvUsername;
        CheckBox cb;
        VH(@NonNull View itemView) {
            super(itemView);
            iv = itemView.findViewById(R.id.iv_profile_picture);
            tvName = itemView.findViewById(R.id.tv_display_name);
            tvUsername = itemView.findViewById(R.id.tv_username);
            cb = itemView.findViewById(R.id.cb_select);
        }
    }
}


