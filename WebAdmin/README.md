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
- Login, save token, automatically attach Bearer to requests.
- Overview dashboard.
- User management:
  - List, search, pagination.
  - Display online/offline/away status.
  - Display `isActive` (activated/locked).
  - Toggle activation (ban/unban) via `PUT /api/users/:id/active`.
  - Filter to show locked users with "Show locked" toggle.

## Scripts
- `npm run dev`: run Vite dev server.
- `npm run build`: build production.
- `npm run preview`: preview build.
- `npm run lint`: run eslint.

## Main Structure
- `src/api/client.ts`: axios client + token interceptor.
- `src/router.tsx`: router + guard.
- `src/layouts/AdminLayout.tsx`: admin layout.
- `src/pages/Login.tsx`: login.
- `src/pages/Users.tsx`: user management.

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