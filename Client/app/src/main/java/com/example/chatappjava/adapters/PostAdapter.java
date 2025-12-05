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

public class PostAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    
    private static final int VIEW_TYPE_CREATE_POST_BAR = 0;
    private static final int VIEW_TYPE_POST = 1;
    
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
    
    public interface OnCreatePostBarClickListener {
        void onCreatePostClick();
        void onLivePostClick();
        void onPhotoPostClick();
        void onVideoPostClick();
    }
    
    private final List<Post> posts;
    private final OnPostClickListener listener;
    private final OnCreatePostBarClickListener createPostBarListener;
    private final Context context;
    private static AvatarManager avatarManager;
    
    public PostAdapter(Context context, List<Post> posts, OnPostClickListener listener, OnCreatePostBarClickListener createPostBarListener) {
        this.context = context;
        this.posts = posts != null ? new ArrayList<>(posts) : new ArrayList<>();
        this.listener = listener;
        this.createPostBarListener = createPostBarListener;
        avatarManager = AvatarManager.getInstance(context);
    }
    
    // Constructor overload for backward compatibility (without create post bar)
    public PostAdapter(Context context, List<Post> posts, OnPostClickListener listener) {
        this(context, posts, listener, null);
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
            // Adjust position for RecyclerView: +1 if create post bar exists
            int recyclerViewPosition = position + (createPostBarListener != null ? 1 : 0);
            notifyItemChanged(recyclerViewPosition);
        }
    }
    
    @Override
    public int getItemViewType(int position) {
        // Only show create post bar at position 0 if listener is not null
        if (position == 0 && createPostBarListener != null) {
            return VIEW_TYPE_CREATE_POST_BAR;
        }
        return VIEW_TYPE_POST;
    }
    
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_CREATE_POST_BAR) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_create_post_bar, parent, false);
            return new CreatePostBarViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_post, parent, false);
            return new PostViewHolder(view);
        }
    }
    
    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof CreatePostBarViewHolder) {
            ((CreatePostBarViewHolder) holder).bind(createPostBarListener, context);
        } else if (holder instanceof PostViewHolder) {
            // Adjust position: if create post bar exists, position 0 is create post bar, so posts start at position 1
            int postPosition = (createPostBarListener != null) ? position - 1 : position;
            if (postPosition >= 0 && postPosition < posts.size()) {
                Post post = posts.get(postPosition);
                ((PostViewHolder) holder).bind(post, listener, context, postPosition);
            }
        }
    }
    
    @Override
    public int getItemCount() {
        // +1 for create post bar if listener is not null
        return posts.size() + (createPostBarListener != null ? 1 : 0);
    }
    
    public static class CreatePostBarViewHolder extends RecyclerView.ViewHolder {
        private final de.hdodenhof.circleimageview.CircleImageView ivPostProfileThumbnail;
        private final LinearLayout llCreatePostInput;
        private final LinearLayout llLivePost;
        private final LinearLayout llPhotoPost;
        private final LinearLayout llVideoPost;
        
        public CreatePostBarViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPostProfileThumbnail = itemView.findViewById(R.id.iv_post_profile_thumbnail);
            llCreatePostInput = itemView.findViewById(R.id.ll_create_post_input);
            llLivePost = itemView.findViewById(R.id.ll_live_post);
            llPhotoPost = itemView.findViewById(R.id.ll_photo_post);
            llVideoPost = itemView.findViewById(R.id.ll_video_post);
        }
        
        public void bind(OnCreatePostBarClickListener listener, Context context) {
            // Load user avatar
            if (avatarManager != null) {
                com.example.chatappjava.utils.DatabaseManager dbManager = new com.example.chatappjava.utils.DatabaseManager(context);
                String avatarUrl = dbManager.getUserAvatar();
                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    // Construct full URL if needed
                    String fullAvatarUrl = avatarUrl;
                    if (!avatarUrl.startsWith("http")) {
                        fullAvatarUrl = "http://" + ServerConfig.getServerIp() + ":" + ServerConfig.getServerPort() + avatarUrl;
                    }
                    avatarManager.loadAvatar(fullAvatarUrl, ivPostProfileThumbnail, R.drawable.ic_profile_placeholder);
                } else {
                    ivPostProfileThumbnail.setImageResource(R.drawable.ic_profile_placeholder);
                }
            } else {
                ivPostProfileThumbnail.setImageResource(R.drawable.ic_profile_placeholder);
            }
            
            // Set click listeners
            if (llCreatePostInput != null && listener != null) {
                llCreatePostInput.setOnClickListener(v -> listener.onCreatePostClick());
            }
            
            if (llLivePost != null && listener != null) {
                llLivePost.setOnClickListener(v -> listener.onLivePostClick());
            }
            
            if (llPhotoPost != null && listener != null) {
                llPhotoPost.setOnClickListener(v -> listener.onPhotoPostClick());
            }
            
            if (llVideoPost != null && listener != null) {
                llVideoPost.setOnClickListener(v -> listener.onVideoPostClick());
            }
        }
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
        private final RecyclerView rvPostGallery;
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
        private int postListPosition = -1; // Store the position in the posts list
        
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
            rvPostGallery = itemView.findViewById(R.id.rv_post_gallery);
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
        public void bind(Post post, OnPostClickListener listener, Context context, int postPosition) {
            // Store the position in the posts list (not RecyclerView position)
            this.postListPosition = postPosition;
            
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
            
            // Set content with mention styling
            if (post.getContent() != null && !post.getContent().isEmpty()) {
                applyMentionStyling(tvPostContent, post.getContent());
                tvPostContent.setVisibility(View.VISIBLE);
            } else {
                tvPostContent.setVisibility(View.GONE);
            }
            
            // Handle media
            flPostMedia.setVisibility(View.GONE);
            ivPostImage.setVisibility(View.GONE);
            rvPostGallery.setVisibility(View.GONE);
            flPostVideo.setVisibility(View.GONE);
            
            if (post.getMediaUrls() != null && !post.getMediaUrls().isEmpty()) {
                flPostMedia.setVisibility(View.VISIBLE);
                String mediaType = post.getMediaType();
                List<String> mediaUrls = post.getMediaUrls();
                
                if ("video".equals(mediaType)) {
                    // Show video
                    flPostVideo.setVisibility(View.VISIBLE);
                    String videoUrl = mediaUrls.get(0);
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
                    // Check if multiple images (gallery)
                    if (mediaUrls.size() > 1) {
                        // Show gallery
                        rvPostGallery.setVisibility(View.VISIBLE);
                        ivPostImage.setVisibility(View.GONE);
                        
                        // Setup gallery adapter
                        // Determine grid columns: 2 columns for 2-3 images, 3 columns for 4-6 images
                        int columns = (mediaUrls.size() <= 3) ? 2 : 3;
                        androidx.recyclerview.widget.GridLayoutManager layoutManager = 
                            new androidx.recyclerview.widget.GridLayoutManager(context, columns);
                        rvPostGallery.setLayoutManager(layoutManager);
                        
                        PostGalleryAdapter galleryAdapter = new PostGalleryAdapter(context, mediaUrls);
                        galleryAdapter.setOnImageClickListener((position, imageUrl) -> {
                            if (listener != null) {
                                listener.onMediaClick(post, position);
                            }
                        });
                        rvPostGallery.setAdapter(galleryAdapter);
                    } else {
                        // Show single image
                        ivPostImage.setVisibility(View.VISIBLE);
                        rvPostGallery.setVisibility(View.GONE);
                        String imageUrl = mediaUrls.get(0);
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
                if (listener != null && postListPosition >= 0) {
                    // Use the stored postListPosition instead of getAdapterPosition()
                    // This ensures we use the correct position in the posts list, not the RecyclerView position
                    listener.onLikeClick(post, postListPosition);
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
    
    // Pattern for @mentions
    private static final java.util.regex.Pattern MENTION_PATTERN = java.util.regex.Pattern.compile("@([A-Za-z0-9_]+)");
    
    // Apply mention styling to TextView (similar to MessageAdapter)
    private static void applyMentionStyling(android.widget.TextView textView, String content) {
        if (content == null) {
            textView.setText("");
            return;
        }
        android.text.SpannableString spannable = new android.text.SpannableString(content);
        
        // Apply mention styling
        java.util.regex.Matcher mentionMatcher = MENTION_PATTERN.matcher(content);
        while (mentionMatcher.find()) {
            int start = mentionMatcher.start();
            int end = mentionMatcher.end();
            
            // Style: blue color and bold
            android.text.style.StyleSpan styleSpan = new android.text.style.StyleSpan(android.graphics.Typeface.BOLD);
            android.text.style.ForegroundColorSpan colorSpan = new android.text.style.ForegroundColorSpan(0xFF2D6BB3); // Primary blue color
            
            spannable.setSpan(colorSpan, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(styleSpan, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            // Clickable span to open profile
            android.text.style.ClickableSpan clickableSpan = getMentionClickableSpan(content, start, end);
            spannable.setSpan(clickableSpan, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        textView.setText(spannable);
        textView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
    }
    
    @androidx.annotation.NonNull
    private static android.text.style.ClickableSpan getMentionClickableSpan(String content, int start, int end) {
        final String username = content.substring(start + 1, end);
        return new android.text.style.ClickableSpan() {
            @Override
            public void onClick(@androidx.annotation.NonNull android.view.View widget) {
                android.content.Context ctx = widget.getContext();
                android.content.Intent intent = new android.content.Intent(ctx, com.example.chatappjava.ui.theme.ProfileViewActivity.class);
                intent.putExtra("username", username);
                ctx.startActivity(intent);
            }
            
            @Override
            public void updateDrawState(android.text.TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);
            }
        };
    }
}

