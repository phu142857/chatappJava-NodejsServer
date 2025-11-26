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
import com.example.chatappjava.config.ServerConfig;
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
        void onTaggedUsersClick(Post post);
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
        private final TextView tvPostTaggedUsers;
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
        private final View embeddedPostCard;
        private final CircleImageView ivEmbeddedAvatar;
        private final TextView tvEmbeddedUsername;
        private final TextView tvEmbeddedContent;
        private final FrameLayout flEmbeddedMedia;
        private final ImageView ivEmbeddedImage;
        private final FrameLayout flEmbeddedVideo;
        private final ImageView ivEmbeddedVideoThumbnail;
        private final ImageButton ivEmbeddedVideoPlay;
        private final ImageView ivEmbeddedArrow;
        
        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPostAvatar = itemView.findViewById(R.id.iv_post_avatar);
            tvPostUsername = itemView.findViewById(R.id.tv_post_username);
            tvPostTaggedUsers = itemView.findViewById(R.id.tv_post_tagged_users);
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
            
            // Embedded post views
            embeddedPostCard = itemView.findViewById(R.id.embedded_post_card);
            ivEmbeddedAvatar = embeddedPostCard != null ? embeddedPostCard.findViewById(R.id.iv_embedded_avatar) : null;
            tvEmbeddedUsername = embeddedPostCard != null ? embeddedPostCard.findViewById(R.id.tv_embedded_username) : null;
            tvEmbeddedContent = embeddedPostCard != null ? embeddedPostCard.findViewById(R.id.tv_embedded_content) : null;
            flEmbeddedMedia = embeddedPostCard != null ? embeddedPostCard.findViewById(R.id.fl_embedded_media) : null;
            ivEmbeddedImage = embeddedPostCard != null ? embeddedPostCard.findViewById(R.id.iv_embedded_image) : null;
            flEmbeddedVideo = embeddedPostCard != null ? embeddedPostCard.findViewById(R.id.fl_embedded_video) : null;
            ivEmbeddedVideoThumbnail = embeddedPostCard != null ? embeddedPostCard.findViewById(R.id.iv_embedded_video_thumbnail) : null;
            ivEmbeddedVideoPlay = embeddedPostCard != null ? embeddedPostCard.findViewById(R.id.iv_embedded_video_play) : null;
            ivEmbeddedArrow = embeddedPostCard != null ? embeddedPostCard.findViewById(R.id.iv_embedded_arrow) : null;
            
            // Debug logging
            if (llShareButton == null) {
                android.util.Log.e("PostAdapter", "llShareButton is NULL! View not found.");
            } else {
                android.util.Log.d("PostAdapter", "llShareButton found successfully");
            }
        }
        
        @SuppressLint("SetTextI18n")
        public void bind(Post post, OnPostClickListener listener, Context context) {
            // Set author info
            tvPostUsername.setText(post.getAuthorUsername());
            
            // Set tagged users display: "User with A, B, ..."
            List<com.example.chatappjava.models.User> taggedUsers = post.getTaggedUsers();
            if (taggedUsers != null && !taggedUsers.isEmpty()) {
                StringBuilder tagsText = new StringBuilder();
                tagsText.append("with "); // "with" in English
                for (int i = 0; i < taggedUsers.size(); i++) {
                    if (i > 0) {
                        if (i == taggedUsers.size() - 1) {
                            tagsText.append(" and "); // "and" in English
                        } else {
                            tagsText.append(", ");
                        }
                    }
                    String displayName = taggedUsers.get(i).getUsername();
                    if (taggedUsers.get(i).getFirstName() != null && !taggedUsers.get(i).getFirstName().isEmpty()) {
                        displayName = taggedUsers.get(i).getFirstName();
                    }
                    tagsText.append(displayName);
                }
                tvPostTaggedUsers.setText(tagsText.toString());
                tvPostTaggedUsers.setVisibility(View.VISIBLE);
                
                // Make tagged users clickable
                tvPostTaggedUsers.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onTaggedUsersClick(post);
                    }
                });
            } else {
                tvPostTaggedUsers.setVisibility(View.GONE);
            }
            
            tvPostTimestamp.setText(post.getFormattedTimestamp());
            
            // Load avatar
            String avatarUrl = post.getAuthorAvatar();
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                // Construct full URL if needed
                if (!avatarUrl.startsWith("http")) {
                    // Ensure avatarUrl starts with /
                    if (!avatarUrl.startsWith("/")) {
                        avatarUrl = "/" + avatarUrl;
                    }
                    avatarUrl = ServerConfig.getBaseUrl() + avatarUrl;
                }
                
                if (avatarManager != null) {
                    avatarManager.loadAvatar(avatarUrl, ivPostAvatar, R.drawable.ic_profile_placeholder);
                } else {
                    // Fallback to Picasso if AvatarManager is not available
                    Picasso.get()
                            .load(avatarUrl)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .error(R.drawable.ic_profile_placeholder)
                            .into(ivPostAvatar);
                }
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
            
            // Handle embedded post (shared post)
            if (embeddedPostCard != null) {
                Post sharedPost = post.getSharedPost();
                if (sharedPost != null && post.getSharedPostId() != null) {
                    // Show embedded post
                    embeddedPostCard.setVisibility(View.VISIBLE);
                    
                    // Set original author info
                    if (tvEmbeddedUsername != null) {
                        String username = sharedPost.getAuthorUsername();
                        tvEmbeddedUsername.setText(username != null && !username.isEmpty() ? username : "Unknown User");
                    }
                    
                    // Load original author avatar
                    if (ivEmbeddedAvatar != null) {
                        String embeddedAvatarUrl = sharedPost.getAuthorAvatar();
                        if (avatarManager != null && embeddedAvatarUrl != null && !embeddedAvatarUrl.isEmpty()) {
                            if (!embeddedAvatarUrl.startsWith("http")) {
                                if (!embeddedAvatarUrl.startsWith("/")) {
                                    embeddedAvatarUrl = "/" + embeddedAvatarUrl;
                                }
                                embeddedAvatarUrl = ServerConfig.getBaseUrl() + embeddedAvatarUrl;
                            }
                            avatarManager.loadAvatar(embeddedAvatarUrl, ivEmbeddedAvatar, R.drawable.ic_profile_placeholder);
                        } else {
                            ivEmbeddedAvatar.setImageResource(R.drawable.ic_profile_placeholder);
                        }
                    }
                    
                    // Set full content (not preview)
                    if (tvEmbeddedContent != null) {
                        String content = sharedPost.getContent();
                        if (content != null && !content.isEmpty()) {
                            tvEmbeddedContent.setText(content);
                            tvEmbeddedContent.setVisibility(View.VISIBLE);
                            // Remove maxLines restriction to show full content
                            tvEmbeddedContent.setMaxLines(Integer.MAX_VALUE);
                        } else {
                            tvEmbeddedContent.setText("Shared a post");
                            tvEmbeddedContent.setVisibility(View.VISIBLE);
                        }
                    }
                    
                    // Handle embedded media
                    if (flEmbeddedMedia != null) {
                        flEmbeddedMedia.setVisibility(View.GONE);
                        ivEmbeddedImage.setVisibility(View.GONE);
                        flEmbeddedVideo.setVisibility(View.GONE);
                        
                        if (sharedPost.getMediaUrls() != null && !sharedPost.getMediaUrls().isEmpty()) {
                            flEmbeddedMedia.setVisibility(View.VISIBLE);
                            String mediaType = sharedPost.getMediaType();
                            
                            if ("video".equals(mediaType)) {
                                // Show video
                                flEmbeddedVideo.setVisibility(View.VISIBLE);
                                String videoUrl = sharedPost.getMediaUrls().get(0);
                                if (videoUrl != null && !videoUrl.isEmpty()) {
                                    // Load thumbnail if available
                                    ivEmbeddedVideoThumbnail.setImageResource(R.drawable.ic_profile_placeholder);
                                }
                                if (ivEmbeddedVideoPlay != null) {
                                    ivEmbeddedVideoPlay.setOnClickListener(v -> {
                                        if (listener != null) {
                                            listener.onMediaClick(sharedPost, 0);
                                        }
                                    });
                                }
                            } else if ("image".equals(mediaType) || "gallery".equals(mediaType)) {
                                // Show image
                                ivEmbeddedImage.setVisibility(View.VISIBLE);
                                String imageUrl = sharedPost.getMediaUrls().get(0);
                                if (imageUrl != null && !imageUrl.isEmpty()) {
                                    Picasso.get()
                                            .load(imageUrl)
                                            .placeholder(R.drawable.ic_profile_placeholder)
                                            .error(R.drawable.ic_profile_placeholder)
                                            .into(ivEmbeddedImage);
                                }
                                ivEmbeddedImage.setOnClickListener(v -> {
                                    if (listener != null) {
                                        listener.onMediaClick(sharedPost, 0);
                                    }
                                });
                            }
                        }
                    }
                    
                    // Make entire card clickable to view original post
                    embeddedPostCard.setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onPostClick(sharedPost);
                        }
                    });
                    
                    // Make arrow icon also clickable
                    if (ivEmbeddedArrow != null) {
                        ivEmbeddedArrow.setOnClickListener(v -> {
                            if (listener != null) {
                                listener.onPostClick(sharedPost);
                            }
                        });
                    }
                } else {
                    // Hide embedded post
                    embeddedPostCard.setVisibility(View.GONE);
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
            
            if (llShareButton != null) {
                llShareButton.setOnClickListener(v -> {
                    try {
                        android.util.Log.d("PostAdapter", "=== Share button clicked ===");
                        android.util.Log.d("PostAdapter", "Post ID: " + (post != null ? post.getId() : "null"));
                        android.util.Log.d("PostAdapter", "Listener: " + (listener != null ? "not null" : "NULL"));
                        android.util.Log.d("PostAdapter", "Listener class: " + (listener != null ? listener.getClass().getName() : "null"));
                        if (listener != null) {
                            android.util.Log.d("PostAdapter", "Calling listener.onShareClick");
                            try {
                                listener.onShareClick(post);
                                android.util.Log.d("PostAdapter", "listener.onShareClick returned successfully");
                            } catch (Exception e) {
                                android.util.Log.e("PostAdapter", "Exception in listener.onShareClick: " + e.getMessage(), e);
                                e.printStackTrace();
                            }
                        } else {
                            android.util.Log.e("PostAdapter", "ERROR: Listener is null!");
                        }
                    } catch (Exception e) {
                        android.util.Log.e("PostAdapter", "Exception in share button click: " + e.getMessage(), e);
                        e.printStackTrace();
                    }
                });
            } else {
                android.util.Log.e("PostAdapter", "ERROR: llShareButton is null, cannot set click listener!");
            }
            
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

