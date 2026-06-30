import type { PropsWithChildren } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { STORAGE_KEYS } from '../config';

function isAuthenticated(): boolean {
  return Boolean(localStorage.getItem(STORAGE_KEYS.accessToken));
}

function getUserRole(): string | null {
  const userStr = localStorage.getItem('user');
  if (!userStr) return null;
  try {
    const user = JSON.parse(userStr);
    return user.role || 'user';
  } catch {
    return null;
  }
}

export function RequireAuth({ children }: PropsWithChildren) {
  const location = useLocation();
  if (!isAuthenticated()) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return children as React.ReactElement;
}

export function RequireGuest({ children }: PropsWithChildren) {
  if (isAuthenticated()) {
    return <Navigate to="/" replace />;
  }
  return children as React.ReactElement;
}

export function RequireAdmin({ children }: PropsWithChildren) {
  const location = useLocation();
  if (!isAuthenticated()) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  
  const role = getUserRole();
  if (role !== 'admin') {
    return <Navigate to="/profile" replace />;
  }
  
  return children as React.ReactElement;
}

export function RequireUserOrAdmin({ children }: PropsWithChildren) {
  const location = useLocation();
  if (!isAuthenticated()) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  
  const role = getUserRole();
  if (!['user', 'admin'].includes(role || '')) {
    return <Navigate to="/profile" replace />;
  }
  
  return children as React.ReactElement;
}


