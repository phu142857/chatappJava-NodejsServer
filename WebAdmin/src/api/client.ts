import axios from 'axios';
import { API_BASE_URL, STORAGE_KEYS } from '../config';

const apiClient = axios.create({
  baseURL: `${API_BASE_URL}/api`,
  headers: {
    'Content-Type': 'application/json',
    'Cache-Control': 'no-cache',
  },
});

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem(STORAGE_KEYS.accessToken);
  if (token) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  // Prevent stale 304 caching for GETs during admin operations
  if (config.method === 'get') {
    config.params = { ...(config.params || {}), _ts: Date.now() };
    if (config.headers) {
      (config.headers as any)['Cache-Control'] = 'no-cache';
    } else {
      (config as any).headers = { 'Cache-Control': 'no-cache' };
    }
  }
  return config;
});

// Response interceptor to handle role changes
apiClient.interceptors.response.use(
  (response) => {
    // Check if response indicates role change requiring reauth
    if (response.data?.requireReauth) {
      const currentUser = JSON.parse(localStorage.getItem('user') || '{}');
      if (response.data.targetUserId === currentUser._id) {
        // Current user's role was changed, force logout
        console.log('Role changed, forcing logout...');
        localStorage.clear();
        window.location.href = '/login';
      }
    }
    return response;
  },
  (error) => {
    // Handle 401/403 errors that might indicate role/permission changes
    if (error.response?.status === 401 || error.response?.status === 403) {
      const errorMessage = error.response?.data?.message || '';
      if (errorMessage.includes('role') || errorMessage.includes('permission') || errorMessage.includes('access')) {
        console.log('Access denied, possible role change, forcing logout...');
        localStorage.clear();
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;


