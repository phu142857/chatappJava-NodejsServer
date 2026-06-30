type RuntimeConfig = {
  VITE_API_BASE_URL?: string;
};

const runtimeConfig: RuntimeConfig =
  typeof window !== 'undefined' ? window.__APP_CONFIG__ || {} : {};

const resolveApiBaseUrl = (): string => {
  if (runtimeConfig.VITE_API_BASE_URL !== undefined) {
    return runtimeConfig.VITE_API_BASE_URL;
  }

  if (import.meta.env.VITE_API_BASE_URL !== undefined) {
    return import.meta.env.VITE_API_BASE_URL;
  }

  return 'http://localhost:49664';
};

export const API_BASE_URL: string = resolveApiBaseUrl().replace(/\/$/, '');

export const STORAGE_KEYS = {
  accessToken: 'access_token',
};


