/// <reference types="vite/client" />

// Ambient declarations for import.meta.env when vite/client types are unavailable
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  // add other env keys here as needed
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

// Declare CSS modules
declare module '*.css' {
  const content: string;
  export default content;
}

declare module '*.scss' {
  const content: string;
  export default content;
}

declare module '*.sass' {
  const content: string;
  export default content;
}

declare module '*.less' {
  const content: string;
  export default content;
}


