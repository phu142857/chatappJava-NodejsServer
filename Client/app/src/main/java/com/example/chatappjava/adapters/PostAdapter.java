package com.example.chatappjava.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.models.Post;
import com.example.chatappjava.utils.AvatarManager;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {
    
    public interface OnPostClickListener {
        void onPostClick(Post post);
        void onLikeClick(Post post, int position);
        void onCommentClick(Post post);
        void onShareClick(Post post);
        void onPostMenuClick(Post post);
        void onAuthorClick(Post post);
        void onMediaClick(Post post, int mediaIndex);
    }
    
    private final List<Post> posts;
    private final OnPostClickListener listener;
    private final Context context;
    private static AvatarManager avatarManager;
    
    public PostAdapter(Context context, List<Post> posts, OnPostClickListener listener) {
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
    
    public void addPosts(List<Post> newPosts) {
        if (newPosts != null && !newPosts.isEmpty()) {
            int startPosition = posts.size();
            posts.addAll(newPosts);
            notifyItemRangeInserted(startPosition, newPosts.size());
        }
    }
    
    public void updatePost(int position, Post post) {
        if (position >= 0 && position < posts.size()) {
            posts.set(position, post);
            notifyItemChanged(position);
        }
    }
    
    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }
    
    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = posts.get(position);
        holder.bind(post, listener, context);
    }
    
    @Override
    public int getItemCount() {
        return posts.size();
    }
    
    public static class PostViewHolder extends RecyclerView.ViewHolder {
        private final CircleImageView ivPostAvatar;
        private final TextView tvPostUsername;
        private final TextView tvPostTimestamp;
        private final ImageButton ivPostMenu;
        private final TextView tvPostContent;
        private final FrameLayout flPostMedia;
        private final ImageView ivPostImage;
        private final FrameLayout flPostVideo;
        private final ImageView ivPostVideoThumbnail;
        private final ImageButton ivPostVideoPlay;
        private final LinearLayout llLikesSummary;
        private final TextView tvLikesCount;
        private final TextView tvCommentsCount;
        private final TextView tvSharesCount;
        private final LinearLayout llLikeButton;
        private final ImageView ivLikeIcon;
        private final TextView tvLikeText;
        private final LinearLayout llCommentButton;
        private final LinearLayout llShareButton;
        
        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPostAvatar = itemView.findViewById(R.id.iv_post_avatar);
            tvPostUsername = itemView.findViewById(R.id.tv_post_username);
            tvPostTimestamp = itemView.findViewById(R.id.tv_post_timestamp);
            ivPostMenu = itemView.findViewById(R.id.iv_post_menu);
            tvPostContent = itemView.findViewById(R.id.tv_post_content);
            flPostMedia = itemView.findViewById(R.id.fl_post_media);
            ivPostImage = itemView.findViewById(R.id.iv_post_image);
            flPostVideo = itemView.findViewById(R.id.fl_post_video);
            ivPostVideoThumbnail = itemView.findViewById(R.id.iv_post_video_thumbnail);
            ivPostVideoPlay = itemView.findViewById(R.id.iv_post_video_play);
            llLikesSummary = itemView.findViewById(R.id.ll_likes_summary);
            tvLikesCount = itemView.findViewById(R.id.tv_likes_count);
            tvCommentsCount = itemView.findViewById(R.id.tv_comments_count);
            tvSharesCount = itemView.findViewById(R.id.tv_shares_count);
            llLikeButton = itemView.findViewById(R.id.ll_like_button);
            ivLikeIcon = itemView.findViewById(R.id.iv_like_icon);
            tvLikeText = itemView.findViewById(R.id.tv_like_text);
            llCommentButton = itemView.findViewById(R.id.ll_comment_button);
            llShareButton = itemView.findViewById(R.id.ll_share_button);
        }
        
        @SuppressLint("SetTextI18n")
        public void bind(Post post, OnPostClickListener listener, Context context) {
            // Set author info
            tvPostUsername.setText(post.getAuthorUsername());
            tvPostTimestamp.setText(post.getFormattedTimestamp());
            
            // Load avatar
            if (avatarManager != null && post.getAuthorAvatar() != null && !post.getAuthorAvatar().isEmpty()) {
                String avatarUrl = post.getAuthorAvatar();
                // Construct full URL if needed
                if (!avatarUrl.startsWith("http")) {
                    avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                               ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
                }
                avatarManager.loadAvatar(avatarUrl, ivPostAvatar, R.drawable.ic_profile_placeholder);
            } else {
                ivPostAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }
            
            // Set content
            if (post.getContent() != null && !post.getContent().isEmpty()) {
                tvPostContent.setText(post.getContent());
                tvPostContent.setVisibility(View.VISIBLE);
            } else {
                tvPostContent.setVisibility(View.GONE);
            }
            
            // Handle media
            flPostMedia.setVisibility(View.GONE);
            ivPostImage.setVisibility(View.GONE);
            flPostVideo.setVisibility(View.GONE);
            
            if (post.getMediaUrls() != null && !post.getMediaUrls().isEmpty()) {
                flPostMedia.setVisibility(View.VISIBLE);
                String mediaType = post.getMediaType();
                
                if ("video".equals(mediaType)) {
                    // Show video
                    flPostVideo.setVisibility(View.VISIBLE);
                    String videoUrl = post.getMediaUrls().get(0);
                    // Load thumbnail if available (you might need to extract thumbnail URL)
                    if (videoUrl != null && !videoUrl.isEmpty()) {
                        // For now, just show a placeholder
                        ivPostVideoThumbnail.setImageResource(R.drawable.ic_profile_placeholder);
                    }
                    ivPostVideoPlay.setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onMediaClick(post, 0);
                        }
                    });
                } else if ("image".equals(mediaType) || "gallery".equals(mediaType)) {
                    // Show image(s)
                    ivPostImage.setVisibility(View.VISIBLE);
                    String imageUrl = post.getMediaUrls().get(0);
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Picasso.get()
                                .load(imageUrl)
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .error(R.drawable.ic_profile_placeholder)
                                .into(ivPostImage);
                    }
                    ivPostImage.setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onMediaClick(post, 0);
                        }
                    });
                }
            }
            
            // Set interaction counts
            tvLikesCount.setText(formatCount(post.getLikesCount()));
            tvCommentsCount.setText(formatCount(post.getCommentsCount()) + " comments");
            tvSharesCount.setText(formatCount(post.getSharesCount()) + " shares");
            
            // Update like button state
            if (post.isLiked()) {
                ivLikeIcon.setImageResource(android.R.drawable.btn_star_big_on);
                ivLikeIcon.setColorFilter(context.getResources().getColor(R.color.icon_like));
                tvLikeText.setText("Liked");
            } else {
                ivLikeIcon.setImageResource(android.R.drawable.btn_star_big_off);
                ivLikeIcon.setColorFilter(context.getResources().getColor(R.color.icon_like));
                tvLikeText.setText("Like");
            }
            
            // Set click listeners
            ivPostAvatar.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAuthorClick(post);
                }
            });
            
            tvPostUsername.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAuthorClick(post);
                }
            });
            
            ivPostMenu.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPostMenuClick(post);
                }
            });
            
            llLikeButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onLikeClick(post, getAdapterPosition());
                }
            });
            
            // Long press on like for reaction picker
            llLikeButton.setOnLongClickListener(v -> {
                // TODO: Show reaction picker dialog
                return true;
            });
            
            llCommentButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCommentClick(post);
                }
            });
            
            llShareButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onShareClick(post);
                }
            });
            
            tvCommentsCount.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCommentClick(post);
                }
            });
            
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPostClick(post);
                }
            });
        }
        
        private String formatCount(int count) {
            if (count >= 1000000) {
                return String.format("%.1fM", count / 1000000.0);
            } else if (count >= 1000) {
                return String.format("%.1fK", count / 1000.0);
            } else {
                return String.valueOf(count);
            }
        }
    }
}

