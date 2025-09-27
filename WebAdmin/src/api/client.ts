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

export default apiClient;


