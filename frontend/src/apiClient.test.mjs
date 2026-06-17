import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import { request } from './apiClient.js';

const originalFetch = globalThis.fetch;

afterEach(() => {
  globalThis.fetch = originalFetch;
});

test('clears auth state when refresh fails', async () => {
  let expiredPayload = null;
  globalThis.fetch = async (url) => {
    if (url === '/api/groups') {
      return new Response(JSON.stringify({ message: '인증이 필요합니다.' }), { status: 401 });
    }
    if (url === '/api/auth/refresh') {
      return new Response(JSON.stringify({ message: '인증이 필요합니다.' }), { status: 401 });
    }
    throw new Error(`unexpected url ${url}`);
  };

  await assert.rejects(
    () => request('/groups', {}, { token: 'expired-access' }, (nextAuth, options) => {
      expiredPayload = { nextAuth, options };
    }),
    /인증이 필요합니다/,
  );

  assert.deepEqual(expiredPayload, { nextAuth: null, options: { expired: true } });
});

test('stores refreshed auth and retries original request once', async () => {
  const authUpdates = [];
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, authorization: options?.headers?.Authorization });
    if (url === '/api/groups' && calls.length === 1) {
      return new Response(JSON.stringify({ message: '인증이 필요합니다.' }), { status: 401 });
    }
    if (url === '/api/auth/refresh') {
      return new Response(JSON.stringify({ token: 'new-access', userId: 1, nickname: 'tester', email: 't@example.com' }), { status: 200 });
    }
    if (url === '/api/groups' && calls.length === 3) {
      return new Response(JSON.stringify([{ id: 1, name: '친구방' }]), { status: 200 });
    }
    throw new Error(`unexpected call ${calls.length} ${url}`);
  };

  const result = await request('/groups', {}, { token: 'old-access' }, (nextAuth) => {
    authUpdates.push(nextAuth);
  });

  assert.deepEqual(result, [{ id: 1, name: '친구방' }]);
  assert.equal(authUpdates[0].token, 'new-access');
  assert.deepEqual(calls.map((call) => call.authorization), ['Bearer old-access', undefined, 'Bearer new-access']);
});
