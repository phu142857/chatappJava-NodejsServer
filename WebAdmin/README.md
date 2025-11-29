# WebAdmin (React + TypeScript + Vite)

Admin web for managing the chat system (NodeJS server).

## Requirements
- Backend running at `http://localhost:5000` (default from `server.js`).
- Node 18+.

## Environment Configuration
Create `.env` file in `WebAdmin` directory:

```
VITE_API_BASE_URL=http://localhost:5000
```

## Installation & Run
```bash
npm i
npm run dev
```
Open the displayed URL (usually `http://localhost:5173`).

Login with admin account (API: `POST /api/auth/login`).

## Main Features
- **Authentication**: Login, save token, automatically attach Bearer to requests.
- **Dashboard**: 
  - Real-time server statistics (CPU, Memory, Disk, Uptime)
  - User, message, and call statistics
  - Service status monitoring
  - System health alerts
  - Quick stats summary
- **User Management**:
  - List, search, pagination with advanced filtering
  - Display online/offline/away status
  - Display `isActive` (activated/locked)
  - Toggle activation (ban/unban) via `PUT /api/users/:id/active`
  - Filter to show locked users with "Show locked" toggle
  - Bulk operations: Delete, Lock, Unlock multiple users
  - Export users data to CSV/JSON
  - Create, edit, delete users
  - Change user roles (user/moderator/admin)
  - Reset user passwords
- **Posts Management** (NEW):
  - Full-text search with filters (friends only, media only, hashtags, date range)
  - View post details with images, engagement metrics
  - Delete posts
  - Export posts data to CSV
  - Pagination and sorting
- **Data Export**: Export users and posts to CSV or JSON format
- **Bulk Operations**: Perform actions on multiple selected items

## Scripts
- `npm run dev`: run Vite dev server.
- `npm run build`: build production.
- `npm run preview`: preview build.
- `npm run lint`: run eslint.

## Main Structure
- `src/api/client.ts`: axios client + token interceptor with role change detection.
- `src/router.tsx`: router + guard with role-based access control.
- `src/layouts/AdminLayout.tsx`: admin layout with navigation menu.
- `src/pages/Login.tsx`: login page.
- `src/pages/Dashboard.tsx`: dashboard with real-time statistics.
- `src/pages/Users.tsx`: user management with bulk operations.
- `src/pages/Posts.tsx`: posts management with search and moderation.
- `src/utils/export.ts`: utility functions for CSV/JSON export.
- `src/components/guards.tsx`: route guards for authentication and authorization.
- `src/hooks/useRoleCheck.ts`: hook for checking user role changes.

## Notes
- If there is role-based authorization, should add role checking in backend middleware for admin routes.

## Admin Milestones
Milestone A – User Management (CRUD + Authorization)
Create/edit/delete/lock accounts
Reset password
Change user/admin/mod roles
Display online/offline, last seen, device
Milestone B – Friends & Groups
Friend request list, approve/cancel
Group chat management: create/edit/delete, add/remove members, assign group admin
Milestone C – Messages & Calls
Message logs (filter by user/group), delete violating messages
Call list, duration, participants, status
Milestone D – Server/Monitoring, Security, Statistics, Configuration
Health/metrics (CPU, RAM, Disk, Network), services
Detailed permissions, audit log, block IP/user/device
DAU/WAU/MAU statistics, messages, calls
System configuration (Firebase/Redis/DB/TURN), timeout, notifications, storage