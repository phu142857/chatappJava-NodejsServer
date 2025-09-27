import type { PropsWithChildren } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { STORAGE_KEYS } from '../config';

function isAuthenticated(): boolean {
  return Boolean(localStorage.getItem(STORAGE_KEYS.accessToken));
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


