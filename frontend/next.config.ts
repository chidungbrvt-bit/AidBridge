import type { NextConfig } from 'next';

const mode = process.env.NEXT_PUBLIC_API_MODE ?? 'mock';
if (!['mock', 'real'].includes(mode)) throw new Error('Invalid NEXT_PUBLIC_API_MODE');
const nextConfig: NextConfig = {
  poweredByHeader: false,
  agentRules: false,
  async headers() {
    return [{ source: '/api/:path*', headers: [
      { key: 'Cache-Control', value: 'no-store' },
      { key: 'X-Content-Type-Options', value: 'nosniff' },
    ] }];
  },
  async rewrites() {
    if (mode !== 'real') return [];
    const upstream = new URL(process.env.API_INTERNAL_BASE_URL ?? 'http://127.0.0.1:8081');
    if (!['http:', 'https:'].includes(upstream.protocol) || upstream.username || upstream.password || upstream.search || upstream.hash || upstream.pathname !== '/') {
      throw new Error('API_INTERNAL_BASE_URL must be an HTTP(S) origin without credentials');
    }
    // beforeFiles keeps the local mock catch-all out of the real API path.
    return { beforeFiles: [{ source: '/api/:path*', destination: `${upstream.origin}/api/:path*` }], afterFiles: [], fallback: [] };
  },
};
export default nextConfig;
