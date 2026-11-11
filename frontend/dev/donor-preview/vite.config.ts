import { defineConfig } from 'vite';
import { fileURLToPath } from 'node:url';
export default defineConfig({
  esbuild: { jsx: 'automatic', jsxImportSource: 'react' },
  resolve: { alias: { react: fileURLToPath(new URL('./node_modules/react',import.meta.url)), 'react-dom': fileURLToPath(new URL('./node_modules/react-dom',import.meta.url)) } },
  server: { fs: { allow: [fileURLToPath(new URL('../../../',import.meta.url))] } },
});
