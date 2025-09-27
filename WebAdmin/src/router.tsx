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
import Login from './pages/Login';
import NotFound from './pages/NotFound';
import { RequireAuth, RequireGuest } from './components/guards';

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
      { index: true, element: <Dashboard /> },
      { path: 'users', element: <Users /> },
      { path: 'chats', element: <Chats /> },
      { path: 'groups', element: <Groups /> },
      { path: 'friend-requests', element: <FriendRequests /> },
      { path: 'calls', element: <Calls /> },
      { path: 'security', element: <Security /> },
      { path: 'statistics', element: <Statistics /> },
    ],
  },
  { path: '*', element: <NotFound /> },
]);

export default router;


