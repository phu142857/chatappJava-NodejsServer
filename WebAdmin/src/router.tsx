import { createBrowserRouter } from 'react-router-dom';
import AdminLayout from './layouts/AdminLayout';
import Dashboard from './pages/Dashboard';
import Users from './pages/Users';
import Chats from './pages/Chats';
import Groups from './pages/Groups';
import FriendRequests from './pages/FriendRequests';
import Calls from './pages/Calls';
import Security from './pages/Security';
import Statistics from './pages/Statistics';
import Reports from './pages/Reports';
import Profile from './pages/Profile';
import UserChats from './pages/UserChats';
import Login from './pages/Login';
import NotFound from './pages/NotFound';
import { RequireAuth, RequireGuest, RequireAdmin, RequireUserOrAdmin } from './components/guards';

const router = createBrowserRouter([
  {
    path: '/login',
    element: (
      <RequireGuest>
        <Login />
      </RequireGuest>
    ),
  },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AdminLayout />
      </RequireAuth>
    ),
    children: [
      // Admin-only routes
      { 
        index: true, 
        element: (
          <RequireAdmin>
            <Dashboard />
          </RequireAdmin>
        ) 
      },
      { 
        path: 'users', 
        element: (
          <RequireAdmin>
            <Users />
          </RequireAdmin>
        ) 
      },
      { 
        path: 'chats', 
        element: (
          <RequireAdmin>
            <Chats />
          </RequireAdmin>
        ) 
      },
      { 
        path: 'groups', 
        element: (
          <RequireAdmin>
            <Groups />
          </RequireAdmin>
        ) 
      },
      { 
        path: 'friend-requests', 
        element: (
          <RequireAdmin>
            <FriendRequests />
          </RequireAdmin>
        ) 
      },
      { 
        path: 'calls', 
        element: (
          <RequireAdmin>
            <Calls />
          </RequireAdmin>
        ) 
      },
      { 
        path: 'security', 
        element: (
          <RequireAdmin>
            <Security />
          </RequireAdmin>
        ) 
      },
      { 
        path: 'statistics', 
        element: (
          <RequireAdmin>
            <Statistics />
          </RequireAdmin>
        ) 
      },
      { 
        path: 'reports', 
        element: (
          <RequireAdmin>
            <Reports />
          </RequireAdmin>
        ) 
      },
      
      // User and Admin routes
      { 
        path: 'profile', 
        element: (
          <RequireUserOrAdmin>
            <Profile />
          </RequireUserOrAdmin>
        ) 
      },
      { 
        path: 'my-chats', 
        element: (
          <RequireUserOrAdmin>
            <UserChats />
          </RequireUserOrAdmin>
        ) 
      },
    ],
  },
  { path: '*', element: <NotFound /> },
]);

export default router;


