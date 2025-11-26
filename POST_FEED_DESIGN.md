# Social Media Post Feed - UI/UX Design Documentation

## Overview
This document describes the complete UI/UX design and implementation for a Social Media 'Post' Feed Tab, designed for both Mobile (Android) and Web (Responsive) platforms. The design follows a modern, clean Material/Fluent-inspired style with flat elements and subtle shadows.

## Design System

### Color Scheme
- **Primary Color**: `#0E394B` (Dark Teal/Blue) - Used for app title, active states, and primary actions
- **Primary Light**: `#7BCCFF` (Light Blue) - Used for accents and gradients
- **Background Primary**: `#E0E0E0` (Light Gray) - Main background
- **Background Secondary**: `#FFFFFF` (White) - Card backgrounds and surfaces
- **Text Primary**: `#000000` (Black) - Main text content
- **Text Secondary**: `#666666` (Gray) - Secondary text (timestamps, counts)
- **Border Light**: `#E0E0E0` - Subtle borders and separators

### Typography
- **App Title**: 24sp, Bold, Primary Color
- **Post Username**: 16sp, Bold, Text Primary
- **Post Timestamp**: 12sp, Regular, Text Secondary
- **Post Content**: 15sp, Regular, Text Primary, Line Spacing 1.2
- **Action Buttons**: 15sp, Regular, Text Primary
- **Interaction Counts**: 14sp, Regular, Text Secondary

### Component Styles
- **Card Elevation**: 2dp for post cards, 4dp for top nav, 8dp for bottom nav
- **Corner Radius**: 12dp for cards and containers
- **Padding**: 12-16dp for content areas, 8dp for compact areas

## Component Breakdown

### 1. Main Navigation Bar (Top)
**Location**: Fixed at top of screen
**Background**: White (`@color/background_secondary`)
**Elevation**: 4dp

**Elements**:
- **App Logo/Title** (Left): "SocialApp" text, 24sp, Bold, Primary Color
- **Search Icon** (Right): 40dp × 40dp, Gray tint, opens SearchActivity
- **Notifications Icon** (Right): 40dp × 40dp, Gray tint, shows notifications

**Behavior**: 
- Fixed position, scrolls with content
- White background with subtle shadow

### 2. Action Bar (Create Post Section)
**Location**: Below top navigation, sticky/fixed
**Background**: White (`@color/background_secondary`)
**Elevation**: 2dp

**Elements**:
- **Profile Picture Thumbnail**: 40dp × 40dp circular image with 2dp border
- **"What's on your mind?" Input**: Rounded container, clickable, opens create post screen
- **Separator Line**: 1dp, Light Gray
- **Quick Action Buttons** (Horizontal Layout):
  - **Live**: Camera icon + "Live" text, opens live streaming
  - **Photo**: Gallery icon + "Photo" text, opens photo picker
  - **Video**: Video icon + "Video" text, opens video picker

**Behavior**:
- Sticky at top when scrolling
- Profile picture opens user profile
- Input field opens create post dialog/screen
- Quick action buttons trigger respective creation flows

### 3. Single Post Card (Repeatable)
**Layout**: CardView with 12dp corner radius, 2dp elevation
**Background**: White (`@color/background_secondary`)
**Margins**: 8dp horizontal, 6dp vertical

#### Header Block
- **User Avatar**: 48dp × 48dp circular image with 2dp border
- **Username**: 16sp, Bold, clickable (opens author profile)
- **Timestamp**: 12sp, Gray, relative time (e.g., "2 hours ago")
- **Three-dot Menu**: 40dp × 40dp, opens post options (edit, delete, report)

#### Content Body
- **Text Content**: 
  - Multiline support
  - Max 5 lines with ellipsis
  - Expandable on tap
  - Line spacing: 1.2
- **Media Content**:
  - **Single Image**: Full width, centerCrop, clickable for fullscreen
  - **Image Gallery**: Grid layout for multiple images
  - **Video**: Thumbnail with play button overlay, clickable to play

#### Interaction Summary
- **Likes/Reactions Count**: Heart icon + formatted count (e.g., "1.2K")
- **Comments Count**: "245 comments" text, clickable
- **Shares Count**: "12 shares" text

#### Action Bar (Bottom of Post)
Horizontal layout with three equal-width buttons:
- **Like/React Button**: 
  - Icon: Star/Heart (filled when liked)
  - Text: "Like" or "Liked"
  - Long-press: Opens reaction picker (Love, Wow, Sad, Angry, etc.)
- **Comment Button**: 
  - Icon: Speech bubble
  - Text: "Comment"
  - Opens comment thread
- **Share Button**: 
  - Icon: Share arrow
  - Text: "Share"
  - Opens share dialog

**Separator**: 1dp line above action bar

### 4. Main Navigation Bar (Bottom)
**Location**: Fixed at bottom of screen
**Background**: White (`@color/background_secondary`)
**Elevation**: 8dp

**Elements** (Equal width, horizontal layout):
- **Feed/Home Icon** (Active): 
  - Icon: View icon, 24dp × 24dp
  - Text: "Feed", 12sp
  - Color: Primary Blue (`@color/icon_active`)
- **Groups/Explore Icon**: 
  - Icon: Group icon, 24dp × 24dp
  - Text: "Groups", 12sp
  - Color: Gray (`@color/icon_inactive`)
- **Watch/Video Icon**: 
  - Icon: Video call icon, 24dp × 24dp
  - Text: "Watch", 12sp
  - Color: Gray (`@color/icon_inactive`)
- **Profile/More Icon**: 
  - Icon: Profile placeholder, 24dp × 24dp
  - Text: "Profile", 12sp
  - Color: Gray (`@color/icon_inactive`)

**Behavior**:
- Fixed at bottom, always visible
- Active tab highlighted in primary blue
- Tabs navigate to respective screens

## User Interactions

### Pull-to-Refresh
- **Implementation**: SwipeRefreshLayout wrapping RecyclerView
- **Behavior**: Pull down to refresh feed, shows loading indicator
- **Action**: Reloads posts from server

### Tap Interactions
- **Post Card**: Opens post detail view
- **Like Button**: Toggles like state, updates count immediately
- **Comment Button**: Opens comment thread/screen
- **Share Button**: Opens share dialog with options
- **Author Avatar/Username**: Opens author profile
- **Post Menu**: Shows options (Edit, Delete, Report, etc.)
- **Media**: Opens fullscreen media viewer

### Long-Press Interactions
- **Like Button**: Opens reaction picker menu with emoji options:
  - ❤️ Love
  - 😮 Wow
  - 😢 Sad
  - 😡 Angry
  - 👍 Like (default)

### Infinite Scrolling
- **Implementation**: RecyclerView scroll listener
- **Trigger**: When user scrolls near bottom (last 3 items visible)
- **Behavior**: 
  - Loads next page of posts
  - Appends to existing list
  - Shows loading indicator at bottom
  - Stops when no more posts available

## Layout Structure

### Main Activity Layout (`activity_post_feed.xml`)
```
ConstraintLayout (Root)
├── ImageView (Background)
├── LinearLayout (Top Nav Bar)
│   ├── TextView (App Title)
│   ├── ImageButton (Search)
│   └── ImageButton (Notifications)
├── LinearLayout (Action Bar)
│   ├── LinearLayout (Profile + Input)
│   ├── View (Separator)
│   └── LinearLayout (Quick Actions)
├── SwipeRefreshLayout
│   └── RecyclerView (Posts)
└── LinearLayout (Bottom Nav Bar)
    ├── LinearLayout (Feed Tab)
    ├── LinearLayout (Groups Tab)
    ├── LinearLayout (Watch Tab)
    └── LinearLayout (Profile Tab)
```

### Post Item Layout (`item_post.xml`)
```
CardView
└── LinearLayout
    ├── LinearLayout (Header)
    │   ├── CircleImageView (Avatar)
    │   ├── LinearLayout (User Info)
    │   └── ImageButton (Menu)
    ├── LinearLayout (Content)
    │   ├── TextView (Text Content)
    │   └── FrameLayout (Media Container)
    ├── LinearLayout (Interaction Summary)
    ├── View (Separator)
    └── LinearLayout (Action Bar)
        ├── LinearLayout (Like Button)
        ├── LinearLayout (Comment Button)
        └── LinearLayout (Share Button)
```

## Implementation Files

### Layout Files
1. `activity_post_feed.xml` - Main feed activity layout
2. `item_post.xml` - Individual post card layout

### Java Classes
1. `Post.java` - Post data model
2. `PostAdapter.java` - RecyclerView adapter for posts
3. `PostFeedActivity.java` - Main activity handling feed logic

### Features Implemented
- ✅ Top navigation bar with logo, search, and notifications
- ✅ Action bar with profile picture and quick post creation
- ✅ Post cards with header, content, media, and interactions
- ✅ Bottom navigation bar with tabs
- ✅ Pull-to-refresh functionality
- ✅ Infinite scrolling
- ✅ Like/Unlike with visual feedback
- ✅ Click handlers for all interactive elements
- ✅ Media display (images and videos)
- ✅ Formatted timestamps
- ✅ Formatted counts (K, M notation)

### Features Pending Implementation
- ⏳ Reaction picker dialog (long-press on like)
- ⏳ Comment thread screen
- ⏳ Share dialog
- ⏳ Post detail view
- ⏳ Create post screen
- ⏳ Media gallery viewer
- ⏳ API integration (currently using mock data)
- ⏳ Real-time post updates via WebSocket

## Responsive Design Considerations

### Mobile (Android)
- Full-width cards with 8dp horizontal margins
- Touch-friendly button sizes (minimum 48dp)
- Swipe gestures for refresh
- Bottom navigation for easy thumb access

### Web (Future)
- Max-width container (e.g., 600px) for readability
- Hover states for interactive elements
- Keyboard navigation support
- Desktop-optimized spacing

## Accessibility

- Content descriptions for all icons
- Minimum touch target sizes (48dp)
- High contrast text colors
- Clear visual hierarchy
- Screen reader support ready

## Performance Optimizations

- RecyclerView for efficient list rendering
- Image loading with Picasso (placeholder, error handling)
- Lazy loading for media content
- Pagination for large feeds
- ViewHolder pattern in adapter

## Future Enhancements

1. **Reactions**: Full emoji reaction picker
2. **Comments**: Inline comment thread
3. **Sharing**: Native share sheet integration
4. **Media**: Fullscreen image/video viewer
5. **Create Post**: Rich text editor with media upload
6. **Real-time**: WebSocket updates for new posts
7. **Filtering**: Sort by recent, popular, following
8. **Search**: Search within posts
9. **Bookmarks**: Save posts for later
10. **Notifications**: Push notifications for interactions

