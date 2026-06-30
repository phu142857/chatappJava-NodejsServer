# Icon Color System — Void Copper

## Overview

Icons use **copper for active/primary**, **ink muted for utility**, and **semantic colors only for status** (accept, danger). No Facebook blue, no multi-accent rainbow navigation.

See `DESIGN.md` and `colors.xml` for canonical tokens.

## Roles

### Active navigation & primary actions
- **Color:** `@color/icon_active` / `@color/palette_crimson` (`#C8875A`)
- **Use:** Active tab, send, create, primary toolbar actions
- **Style:** Filled or tinted vector; copper on void canvas

### Inactive navigation
- **Color:** `@color/icon_inactive` / `@color/ink_whisper` (`#7F8996`)
- **Use:** Inactive dock tabs, disabled controls

### Utility (search, settings, close, more)
- **Color:** `@color/icon_utility` / `@color/ink_muted` (`#8A939F`)
- **Use:** Search, settings gear, dismiss, overflow menu

### Semantic (status only — never decorative)
- **Accept:** `@color/accept` — incoming call accept, success
- **Danger:** `@color/danger` — decline, delete, live indicator
- **Warning:** `@color/warning_color` — caution banners

### Post interactions
- **Like / comment / tag:** `@color/icon_like` → copper when active
- **Share:** `@color/icon_share` → ink muted (not green accent)

## Rules

1. Reference `@color/*` tokens — never hardcode hex in layouts or drawables.
2. One accent (copper) on any screen; semantic green/red only for status.
3. Tint vectors with `app:tint` — do not maintain separate multi-color icon assets unless required.
4. Touch targets ≥ 48dp; every icon-only control needs `contentDescription` from `strings.xml`.
5. Stroke weight: keep vector icons consistent within the set (24dp canvas, 2dp effective stroke).

## Anti-patterns (banned)

- Facebook blue `#1877F2` or Messenger-bright chrome
- Rainbow per-icon accent colors on one screen
- Pure black `#000000` icon fills
- Lucide/Feather as the only icon set without project tint discipline
- Hardcoded hex in drawable XML

## XML example

```xml
<ImageView
    android:layout_width="@dimen/icon_md"
    android:layout_height="@dimen/icon_md"
    android:contentDescription="@string/search_icon_description"
    android:src="@drawable/ic_search"
    app:tint="@color/icon_utility" />
```

```java
imageView.setColorFilter(ContextCompat.getColor(context, R.color.icon_active));
```
