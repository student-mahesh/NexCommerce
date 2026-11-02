const crypto = require('crypto');

const secret = '404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970';

function base64url(str) {
  return Buffer.from(str)
    .toString('base64')
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
}

function createJwt(sub, email, role) {
  const header = { alg: 'HS256', typ: 'JWT' };
  const now = Math.floor(Date.now() / 1000);
  const payload = {
    sub: sub,
    email: email,
    role: role,
    iat: now,
    exp: now + 3600
  };

  const encodedHeader = base64url(JSON.stringify(header));
  const encodedPayload = base64url(JSON.stringify(payload));
  const data = `${encodedHeader}.${encodedPayload}`;

  const signature = crypto
    .createHmac('sha256', Buffer.from(secret, 'utf-8'))
    .update(data)
    .digest('base64')
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');

  return `${data}.${signature}`;
}

const adminToken = createJwt('2', 'admin@nexcommerce.com', 'ROLE_ADMIN');
const customerToken = createJwt('1', 'customer@nexcommerce.com', 'ROLE_CUSTOMER');

console.log(JSON.stringify({ adminToken, customerToken }));

