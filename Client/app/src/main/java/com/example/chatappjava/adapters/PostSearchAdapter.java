package com.example.chatappjava.adapters;

import android.content.Context;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.config.ServerConfig;
import com.example.chatappjava.models.Post;
import com.example.chatappjava.utils.AvatarManager;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class PostSearchAdapter extends RecyclerView.Adapter<PostSearchAdapter.PostSearchViewHolder> {
    
    public interface OnPostSearchClickListener {
        void onPostClick(Post post);
        void onAuthorClick(String authorId, String authorName);
    }
    
    private final List<Post> posts;
    private final OnPostSearchClickListener listener;
    private final Context context;
    private String searchQuery = "";
    private static AvatarManager avatarManager;
    
    public PostSearchAdapter(Context context, List<Post> posts, OnPostSearchClickListener listener) {
        this.context = context;
        this.posts = posts != null ? new ArrayList<>(posts) : new ArrayList<>();
        this.listener = listener;
        avatarManager = AvatarManager.getInstance(context);
    }
    
    public void setPosts(List<Post> newPosts) {
        this.posts.clear();
        if (newPosts != null) {
            this.posts.addAll(newPosts);
        }
        notifyDataSetChanged();
    }
    
    public void setSearchQuery(String query) {
        this.searchQuery = query != null ? query : "";
    }
    
    public void addPosts(List<Post> newPosts) {
        if (newPosts != null && !newPosts.isEmpty()) {
            int startPosition = posts.size();
            posts.addAll(newPosts);
            notifyItemRangeInserted(startPosition, newPosts.size());
        }
    }
    
    @Override
    public int getItemCount() {
        return posts.size();
    }
    
    @NonNull
    @Override
    public PostSearchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_post_search, parent, false);
        return new PostSearchViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull PostSearchViewHolder holder, int position) {
        Post post = posts.get(position);
        if (post == null) return;
        
        // Author info
        String authorName = post.getAuthorUsername();
        if (post.getAuthorId() != null) {
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPostClick(post);
                }
            });
            
            holder.tvAuthorName.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAuthorClick(post.getAuthorId(), authorName);
                }
            });
        }
        
        holder.tvAuthorName.setText(authorName);
        
        // Avatar
        if (post.getAuthorAvatar() != null && !post.getAuthorAvatar().isEmpty()) {
            String avatarUrl = post.getAuthorAvatar();
            if (!avatarUrl.startsWith("http")) {
                avatarUrl = "http://" + ServerConfig.getServerIp() + ":" + ServerConfig.getServerPort() + avatarUrl;
            }
            if (avatarManager != null) {
                avatarManager.loadAvatar(avatarUrl, holder.ivAvatar, R.drawable.ic_profile_placeholder);
            }
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_profile_placeholder);
        }
        
        // Content with highlighting
        String content = post.getContent();
        if (content != null && !content.isEmpty()) {
            holder.tvContent.setVisibility(View.VISIBLE);
            // Highlight search query in content
            if (!searchQuery.isEmpty() && content.toLowerCase().contains(searchQuery.toLowerCase())) {
                String highlighted = content.replaceAll("(?i)(" + java.util.regex.Pattern.quote(searchQuery) + ")", "<b>$1</b>");
                Spanned spanned = Html.fromHtml(highlighted, Html.FROM_HTML_MODE_LEGACY);
                holder.tvContent.setText(spanned);
            } else {
                holder.tvContent.setText(content);
            }
        } else {
            holder.tvContent.setVisibility(View.GONE);
        }
        
        // Media thumbnail
        if (post.getMediaUrls() != null && !post.getMediaUrls().isEmpty()) {
            holder.ivMedia.setVisibility(View.VISIBLE);
            String imageUrl = post.getMediaUrls().get(0);
            if (!imageUrl.startsWith("http")) {
                imageUrl = "http://" + ServerConfig.getServerIp() + ":" + ServerConfig.getServerPort() + imageUrl;
            }
            Picasso.get()
                .load(imageUrl)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .fit()
                .centerCrop()
                .into(holder.ivMedia);
        } else {
            holder.ivMedia.setVisibility(View.GONE);
        }
        
        // Engagement stats
        holder.tvLikes.setText(String.valueOf(post.getLikesCount()));
        holder.tvComments.setText(String.valueOf(post.getCommentsCount()));
        holder.tvShares.setText(String.valueOf(post.getSharesCount()));
        
        // Timestamp
        if (post.getTimestamp() > 0) {
            long timeDiff = System.currentTimeMillis() - post.getTimestamp();
            String timeAgo = formatTimeAgo(timeDiff);
            holder.tvTimestamp.setText(timeAgo);
        }
    }
    
    private String formatTimeAgo(long timeDiff) {
        long seconds = timeDiff / 1000;
        if (seconds < 60) return seconds + "s ago";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days < 7) return days + "d ago";
        long weeks = days / 7;
        if (weeks < 4) return weeks + "w ago";
        long months = days / 30;
        return months + "mo ago";
    }
    
    static class PostSearchViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivAvatar;
        TextView tvAuthorName;
        TextView tvContent;
        ImageView ivMedia;
        TextView tvLikes;
        TextView tvComments;
        TextView tvShares;
        TextView tvTimestamp;
        
        PostSearchViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvAuthorName = itemView.findViewById(R.id.tv_author_name);
            tvContent = itemView.findViewById(R.id.tv_content);
            ivMedia = itemView.findViewById(R.id.iv_media);
            tvLikes = itemView.findViewById(R.id.tv_likes);
            tvComments = itemView.findViewById(R.id.tv_comments);
            tvShares = itemView.findViewById(R.id.tv_shares);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
        }
    }
}

