import { useEffect } from 'react';
import apiClient from '../api/client';

export const useRoleCheck = () => {
  useEffect(() => {
    const checkRole = async () => {
      try {
        const response = await apiClient.get('/auth/me');
        const currentUser = response.data.data.user;
        const storedUser = JSON.parse(localStorage.getItem('user') || '{}');
        
        // If stored role is different from server role, logout
        if (storedUser.role && currentUser.role !== storedUser.role) {
          console.log('Role mismatch detected, forcing logout...');
          localStorage.clear();
          window.location.href = '/login';
        }
      } catch (error) {
        // If we can't get user info, might be unauthorized
        console.log('Failed to check role, forcing logout...');
        localStorage.clear();
        window.location.href = '/login';
      }
    };

    // Check role every 30 seconds
    const interval = setInterval(checkRole, 30000);
    
    // Also check on page focus
    const handleFocus = () => {
      checkRole();
    };
    
    window.addEventListener('focus', handleFocus);
    
    return () => {
      clearInterval(interval);
      window.removeEventListener('focus', handleFocus);
    };
  }, []);
};
