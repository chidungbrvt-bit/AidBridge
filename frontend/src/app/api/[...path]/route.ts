import type { createMockBackend } from '@/lib/mock-backend';

export const dynamic = 'force-dynamic';
export const runtime = 'nodejs';
type Backend = ReturnType<typeof createMockBackend>;
const mockGlobal = globalThis as typeof globalThis & { aidbridgeMock?: Backend };
async function handle(request: Request): Promise<Response> {
  if (process.env.NEXT_PUBLIC_API_MODE === 'real') return Response.json({ code: 'RESOURCE_NOT_FOUND' }, { status: 404 });
  const origin = request.headers.get('origin');
  // Next may normalize request.url to localhost; Host retains the browser's origin.
  const incoming = new URL(request.url);
  const browserOrigin = `${incoming.protocol}//${request.headers.get('host') ?? incoming.host}`;
  if (request.method !== 'GET' && origin && origin !== browserOrigin) {
    return Response.json({ status: 403, code: 'CSRF_INVALID', title: 'Cross-origin mutation denied' }, { status: 403 });
  }
  mockGlobal.aidbridgeMock ??= (await import('@/lib/mock-backend')).createMockBackend();
  const response = await mockGlobal.aidbridgeMock.handle(request);
  response.headers.set('X-AidBridge-Mock', 'true');
  response.headers.set('Cache-Control', 'no-store');
  return response;
}
export const GET = handle;
export const POST = handle;
