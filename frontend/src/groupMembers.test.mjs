import assert from 'node:assert/strict';
import { test } from 'node:test';
import { canManageMember, visibleMembers } from './groupMembers.js';

test('visibleMembers removes deleted users and keeps joined order', () => {
  const members = [
    { userId: 3, status: 'ACTIVE', joinedAt: '2026-06-17T03:00:00Z' },
    { userId: 1, status: 'DELETED', joinedAt: '2026-06-17T01:00:00Z' },
    { userId: 2, status: 'ACTIVE', joinedAt: '2026-06-17T02:00:00Z' },
  ];

  assert.deepEqual(visibleMembers(members).map((member) => member.userId), [2, 3]);
});

test('owners cannot manage themselves or deleted users', () => {
  assert.equal(canManageMember(true, 1, { userId: 2, status: 'ACTIVE' }), true);
  assert.equal(canManageMember(true, 1, { userId: 1, status: 'ACTIVE' }), false);
  assert.equal(canManageMember(true, 1, { userId: 2, status: 'DELETED' }), false);
  assert.equal(canManageMember(false, 1, { userId: 2, status: 'ACTIVE' }), false);
});
