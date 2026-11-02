/**
 * JWT Utility Functions for Token Inspection & Role Verification.
 * Supports both browser and Node.js environments.
 */

export function parseJwt(token) {
  if (!token || typeof token !== 'string') return null;

  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;

    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');

    // Decode base64
    let jsonString;
    if (typeof atob === 'function') {
      jsonString = decodeURIComponent(
        atob(base64)
          .split('')
          .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
    } else if (typeof Buffer !== 'undefined') {
      jsonString = Buffer.from(base64, 'base64').toString('utf-8');
    } else {
      return null;
    }

    const payload = JSON.parse(jsonString);

    // Verify token expiration if present
    if (payload.exp && typeof payload.exp === 'number') {
      const nowInSeconds = Math.floor(Date.now() / 1000);
      if (payload.exp < nowInSeconds) {
        return null; // Expired token
      }
    }

    return payload;
  } catch {
    return null;
  }
}

/**
 * Checks whether the given token contains valid, unexpired ROLE_ADMIN privileges.
 */
export function isAdminToken(token) {
  const payload = parseJwt(token);
  return Boolean(payload && payload.role === 'ROLE_ADMIN');
}

