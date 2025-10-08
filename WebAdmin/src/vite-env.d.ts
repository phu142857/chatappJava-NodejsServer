// Ambient declarations for import.meta.env when vite/client types are unavailable
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  // add other env keys here as needed
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}


