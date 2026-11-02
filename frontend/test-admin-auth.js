// frontend/test-admin-auth.js
// Unit tests for Admin Route Protection, JWT Token Claim Verification & Role Validation

import assert from 'node:assert/strict';
import { parseJwt, isAdminToken } from './src/utils/jwt.js';

console.log('===============================================================');
console.log('Running NexCommerce Admin Auth & Route Protection Unit Tests...');
console.log('===============================================================\n');

// Helper to craft test JWTs
function createTestToken(payloadObj) {
  const header = { alg: 'HS256', typ: 'JWT' };
  const encodedHeader = Buffer.from(JSON.stringify(header)).toString('base64url');
  const encodedPayload = Buffer.from(JSON.stringify(payloadObj)).toString('base64url');
  const fakeSignature = 'test_signature_bytes';
  return `${encodedHeader}.${encodedPayload}.${fakeSignature}`;
}

const now = Math.floor(Date.now() / 1000);

const validAdminToken = createTestToken({
  sub: '2',
  email: 'admin@nexcommerce.com',
  role: 'ROLE_ADMIN',
  iat: now - 60,
  exp: now + 3600,
});

const validCustomerToken = createTestToken({
  sub: '1',
  email: 'customer@nexcommerce.com',
  role: 'ROLE_CUSTOMER',
  iat: now - 60,
  exp: now + 3600,
});

const expiredAdminToken = createTestToken({
  sub: '2',
  email: 'admin@nexcommerce.com',
  role: 'ROLE_ADMIN',
  iat: now - 7200,
  exp: now - 3600, // expired 1 hour ago
});

// Mock simulation of AdminRoute authorization logic
function checkAdminRouteAccess(authState) {
  const { user, token, isAuthenticated, loading } = authState;

  if (loading) {
    return { status: 'LOADING', view: 'Spinner' };
  }

  if (!isAuthenticated || !token) {
    return { status: 'REDIRECT', target: '/?auth=login', reason: 'Unauthenticated' };
  }

  const isVerifiedAdmin = user?.role === 'ROLE_ADMIN' && isAdminToken(token);

  if (!isVerifiedAdmin) {
    return { status: 'REDIRECT', target: '/', reason: 'Unauthorized customer or tampered role' };
  }

  return { status: 'AUTHORIZED', target: '/admin', view: 'AdminLayout' };
}

// Test 1: parseJwt correctly parses valid claims
console.log('[Test 1] parseJwt decodes payload and extracts role claim');
const parsed = parseJwt(validAdminToken);
assert.notEqual(parsed, null, 'Parsed payload should not be null');
assert.equal(parsed.sub, '2');
assert.equal(parsed.email, 'admin@nexcommerce.com');
assert.equal(parsed.role, 'ROLE_ADMIN');
console.log('  ✓ Pass: Valid admin claims extracted successfully\n');

// Test 2: parseJwt rejects expired token
console.log('[Test 2] parseJwt rejects expired tokens');
const parsedExpired = parseJwt(expiredAdminToken);
assert.equal(parsedExpired, null, 'Expired token must return null');
console.log('  ✓ Pass: Expired token correctly rejected as null\n');

// Test 3: parseJwt rejects malformed string
console.log('[Test 3] parseJwt handles malformed tokens');
assert.equal(parseJwt('not.a.valid.jwt'), null);
assert.equal(parseJwt(''), null);
assert.equal(parseJwt(null), null);
console.log('  ✓ Pass: Malformed strings safely handled without throwing\n');

// Test 4: isAdminToken returns true ONLY for valid unexpired ROLE_ADMIN
console.log('[Test 4] isAdminToken validation');
assert.equal(isAdminToken(validAdminToken), true, 'Valid admin token must return true');
assert.equal(isAdminToken(validCustomerToken), false, 'Customer token must return false');
assert.equal(isAdminToken(expiredAdminToken), false, 'Expired admin token must return false');
assert.equal(isAdminToken('invalid.token'), false, 'Malformed token must return false');
console.log('  ✓ Pass: isAdminToken strictly verifies ROLE_ADMIN and unexpired status\n');

// Test 5: Route Protection — Logged-out visitor
console.log('[Test 5] Route Protection: Logged-out visitor');
const loggedOutAccess = checkAdminRouteAccess({
  user: null,
  token: null,
  isAuthenticated: false,
  loading: false,
});
assert.equal(loggedOutAccess.status, 'REDIRECT');
assert.equal(loggedOutAccess.target, '/?auth=login');
console.log('  ✓ Pass: Logged-out visitor redirected to /?auth=login\n');

// Test 6: Route Protection — Authenticated customer rejected
console.log('[Test 6] Route Protection: Authenticated customer rejected');
const customerAccess = checkAdminRouteAccess({
  user: { id: 1, email: 'customer@test.com', role: 'ROLE_CUSTOMER' },
  token: validCustomerToken,
  isAuthenticated: true,
  loading: false,
});
assert.equal(customerAccess.status, 'REDIRECT');
assert.equal(customerAccess.target, '/');
console.log('  ✓ Pass: Authenticated customer blocked from /admin and redirected to /\n');

// Test 7: Route Protection — Tampered localStorage role with customer token
console.log('[Test 7] Route Protection: Tampered localStorage role with customer token');
const spoofedRoleAccess = checkAdminRouteAccess({
  // Attacker manually modified localStorage 'ecom_user' to { role: 'ROLE_ADMIN' }
  user: { id: 1, email: 'customer@test.com', role: 'ROLE_ADMIN' },
  // But token payload contains ROLE_CUSTOMER
  token: validCustomerToken,
  isAuthenticated: true,
  loading: false,
});
assert.equal(spoofedRoleAccess.status, 'REDIRECT');
assert.equal(spoofedRoleAccess.target, '/');
console.log('  ✓ Pass: Tampered user object blocked because JWT payload is ROLE_CUSTOMER\n');

// Test 8: Route Protection — Authenticated admin granted access
console.log('[Test 8] Route Protection: Authenticated admin granted access');
const adminAccess = checkAdminRouteAccess({
  user: { id: 2, email: 'admin@nexcommerce.com', role: 'ROLE_ADMIN' },
  token: validAdminToken,
  isAuthenticated: true,
  loading: false,
});
assert.equal(adminAccess.status, 'AUTHORIZED');
assert.equal(adminAccess.view, 'AdminLayout');
console.log('  ✓ Pass: Verified admin granted access to AdminLayout\n');

// Test 9: Route Protection — Browser refresh during checkAuth loading state
console.log('[Test 9] Route Protection: Browser refresh during loading state');
const loadingAccess = checkAdminRouteAccess({
  user: { id: 2, email: 'admin@nexcommerce.com', role: 'ROLE_ADMIN' },
  token: validAdminToken,
  isAuthenticated: true,
  loading: true, // checkAuth in flight
});
assert.equal(loadingAccess.status, 'LOADING');
assert.equal(loadingAccess.view, 'Spinner');
console.log('  ✓ Pass: Renders loading spinner on refresh rather than prematurely kicking user out\n');

// Test 10: Route Protection — Immediate redirect on logout
console.log('[Test 10] Route Protection: Immediate redirect on logout action');
const loggedOutAfterSession = checkAdminRouteAccess({
  user: null,
  token: null,
  isAuthenticated: false,
  loading: false,
});
assert.equal(loggedOutAfterSession.status, 'REDIRECT');
assert.equal(loggedOutAfterSession.target, '/?auth=login');
console.log('  ✓ Pass: Immediate redirection to login when user logs out\n');

console.log('===============================================================');
console.log('ALL 10 ADMIN AUTH & ROUTE PROTECTION TESTS PASSED CLEANLY! (10/10)');
console.log('===============================================================');

