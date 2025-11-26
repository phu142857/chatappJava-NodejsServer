# Icon Color System Style Guide - Vibrant/Colorful Style

## Overview
This document defines the vibrant, colorful iconography system that replaces the previous monochromatic black icons. The new system uses a modern, multi-color palette to enhance visual appeal and distinction across the entire application.

## Color Palette

### Primary Brand Colors
- **Primary Brand Color**: `#1877F2` (`@color/icon_primary_brand`)
  - Facebook blue - Used for primary actions and active states
- **Secondary Accent Color**: `#00C300` (`@color/icon_secondary_accent`)
  - Vibrant green - Used for success states, photo actions, share icons
- **Tertiary Accent Color**: `#FFC000` (`@color/icon_tertiary_accent`)
  - Warm yellow/orange - Used for location icons, warnings
- **Background Reference**: `#F0F2F5` (`@color/icon_background_reference`)
  - Light gray background for contrast reference

### Active/Primary Navigation Icons
- **Color**: `#1877F2` (`@color/icon_active` / `@color/icon_active_filled`)
- **Style**: Full color, filled version of glyph
- **Examples**: 
  - Active Home/Feed Icon
  - Active Tab Bar Icons
  - Current page indicators
- **Visual Note**: The active icon should be fully saturated and often use a filled version of the glyph to create a three-dimensional, vibrant appearance.

### Inactive Navigation Icons
- **Color**: `#8A8A8E` (`@color/icon_inactive`) or `#B0C4DE` (`@color/icon_inactive_accent`)
- **Style**: More colorful than simple gray, but less saturated than active state
- **Examples**:
  - Inactive Groups Icon
  - Inactive Watch Icon
  - Inactive navigation tabs
- **Visual Note**: Should signal interaction while maintaining a colorful look, avoiding the dullness of pure black/gray. May include subtle brand color elements.

### Interaction/Reaction Icons (Vibrant Colors)
- **Like Icon**: `#1877F2` (`@color/icon_like`)
  - Default state: Blue
  - Active state: `#1877F2` (`@color/icon_like_active`) - Same vibrant blue, filled
- **Love Icon**: `#E84C3D` (`@color/icon_love`)
  - Red for love/danger reactions
- **Comment Icon**: `#1877F2` (`@color/icon_comment`)
  - Blue to match primary brand
- **Share Icon**: `#00C300` (`@color/icon_share`)
  - Green for sharing/success
- **Visual Note**: Use vibrant, meaningful colors. These icons should be colorful in their default state, moving away from black/gray.

### Utility Icons (Search, Settings, Close)
- **Color**: `#555555` (`@color/icon_utility` / `@color/icon_search` / `@color/icon_settings` / `@color/icon_close`)
- **Alternative**: `#3A3A3C` (`@color/icon_utility_dark`) for darker contexts
- **Examples**:
  - Search Icon
  - Settings Gear Icon
  - Close/Cancel Icon
  - Three-dot Menu Icon (`@color/icon_more`)
- **Visual Note**: Clear and accessible color, but not highly saturated. Avoid using pure black (#000000); introduce color depth while maintaining readability.

### Action Icons (Vibrant Colors)
- **Primary Action**: `#1877F2` (`@color/icon_primary_action`)
  - Create Post button, Post button, Send Message
- **Photo**: `#00C300` (`@color/icon_photo`)
  - Photo picker, gallery icons
- **Video**: `#1877F2` (`@color/icon_video`)
  - Video picker, video call icons
- **Location**: `#FFC000` (`@color/icon_location`)
  - Location picker, map icons
- **Tag People**: `#1877F2` (`@color/icon_tag`)
  - Tag people, mention icons
- **Live**: `#E84C3D` (`@color/icon_live`)
  - Live streaming, broadcast icons

## Icon Design Style

### Visual Transition
The overall icon library transitions from simple line icons to a system that utilizes:
- **Color fills**: Icons use solid color fills instead of just outlines
- **Gradient effects**: Subtle gradients can be applied for depth
- **Shadow effects**: Light shadows create three-dimensional appearance
- **Vibrant colors**: Full spectrum of colors replaces monochrome black

### State Management
- **Active States**: Fully saturated, filled icons with vibrant colors
- **Inactive States**: Less saturated but still colorful (not pure gray)
- **Hover/Pressed States**: Slightly darker or lighter shade of the base color
- **Disabled States**: Desaturated version (approximately 40% opacity)

## Implementation Guidelines

### Layout Files
```xml
<!-- Active Navigation Icon -->
<ImageView
    android:src="@drawable/ic_home"
    app:tint="@color/icon_active" />

<!-- Inactive Navigation Icon -->
<ImageView
    android:src="@drawable/ic_groups"
    app:tint="@color/icon_inactive" />

<!-- Interaction Icon (Default State) -->
<ImageView
    android:src="@drawable/ic_like"
    app:tint="@color/icon_like" />

<!-- Utility Icon -->
<ImageView
    android:src="@drawable/ic_search"
    app:tint="@color/icon_search" />
```

### Code (Java/Kotlin)
```java
// Active state
iconView.setColorFilter(context.getResources().getColor(R.color.icon_active, null));

// Inactive state
iconView.setColorFilter(context.getResources().getColor(R.color.icon_inactive, null));

// Interaction icon (default vibrant color)
likeIcon.setColorFilter(context.getResources().getColor(R.color.icon_like, null));

// Interaction icon (active state)
if (isLiked) {
    likeIcon.setColorFilter(context.getResources().getColor(R.color.icon_like_active, null));
}
```

## Component-Specific Rules

### Top Navigation Bars
- **Search Icon**: `@color/icon_search` (#555555)
- **Settings Icon**: `@color/icon_settings` (#555555)
- **Three-dot Menu**: `@color/icon_more` (#555555)
- **Back Arrow** (on dark): `@color/icon_white` (#FFFFFF)
- **Close Button**: `@color/icon_close` (#555555)

### Bottom Tab Bars
- **Active Tab**: `@color/icon_active` (#1877F2) - Full color, filled
- **Inactive Tabs**: `@color/icon_inactive` (#8A8A8E) - Colorful but less saturated

### List Items
- **Default action icons**: Use vibrant colors based on action type
  - Like: `@color/icon_like` (#1877F2)
  - Comment: `@color/icon_comment` (#1877F2)
  - Share: `@color/icon_share` (#00C300)
- **Three-dot menu**: `@color/icon_more` (#555555)
- **Active state icons**: Use active color variants

### Post Feed
- **Like button (default)**: `@color/icon_like` (#1877F2) - Vibrant blue
- **Like button (active)**: `@color/icon_like_active` (#1877F2) - Same blue, filled
- **Comment button**: `@color/icon_comment` (#1877F2) - Vibrant blue
- **Share button**: `@color/icon_share` (#00C300) - Vibrant green
- **Three-dot menu**: `@color/icon_more` (#555555)

### Create Post Activity
- **Close button**: `@color/icon_close` (#555555)
- **Post button**: Primary gradient (contains `@color/icon_primary_brand`)
- **Photo picker**: `@color/icon_photo` (#00C300) - Vibrant green
- **Video picker**: `@color/icon_video` (#1877F2) - Vibrant blue
- **Location picker**: `@color/icon_location` (#FFC000) - Warm yellow/orange
- **Tag people**: `@color/icon_tag` (#1877F2) - Vibrant blue
- **More options**: `@color/icon_more` (#555555)

### Quick Action Buttons (Post Action Bar)
- **Live**: `@color/icon_live` (#E84C3D) - Red
- **Photo**: `@color/icon_photo` (#00C300) - Green
- **Video**: `@color/icon_video` (#1877F2) - Blue

## Color Hex Codes Reference

### Active States
- Active Navigation: `#1877F2`
- Like Active: `#1877F2`
- Primary Action: `#1877F2`

### Inactive States
- Inactive Navigation: `#8A8A8E`
- Inactive Accent: `#B0C4DE`

### Interaction Icons (Default)
- Like: `#1877F2`
- Comment: `#1877F2`
- Share: `#00C300`
- Love: `#E84C3D`

### Utility Icons
- Search: `#555555`
- Settings: `#555555`
- Close: `#555555`
- More: `#555555`
- Utility Dark: `#3A3A3C`

### Action Icons
- Photo: `#00C300`
- Video: `#1877F2`
- Location: `#FFC000`
- Tag: `#1877F2`
- Live: `#E84C3D`

## Visual Transition Examples

### Before (Monochromatic)
- All icons: `#000000` (Pure black)
- No distinction between states
- Flat, two-dimensional appearance

### After (Vibrant/Colorful)
- Active icons: `#1877F2` (Vibrant blue, filled)
- Inactive icons: `#8A8A8E` (Colorful gray)
- Interaction icons: Various vibrant colors
- Utility icons: `#555555` (Dark gray with depth)
- Three-dimensional appearance with color fills

## Accessibility Considerations
- Ensure sufficient contrast ratios (WCAG AA minimum)
  - Active icons on light background: ✓ (High contrast)
  - Inactive icons on light background: ✓ (Sufficient contrast)
  - Utility icons: ✓ (Readable)
- Icons should be at least 24dp for touch targets
- Color should be combined with shape/size changes for state indication
- Provide content descriptions for all icons
- Test with color blindness simulators

## Maintenance
- All icon colors should reference color resources, not hardcoded hex values
- When adding new icon types, follow the vibrant color system
- Update this guide when new icon categories are introduced
- Maintain consistency with brand colors (#1877F2, #00C300, #FFC000)

## Migration Notes
- Legacy `icon_inactive` (#AAAAAA) is still available for backward compatibility
- New vibrant colors take precedence in new implementations
- Gradually migrate existing icons to the new vibrant system
