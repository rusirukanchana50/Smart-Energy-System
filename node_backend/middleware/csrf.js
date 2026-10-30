/**
 * csrf.js
 * -------
 * Cross-Site Request Forgery (CSRF) defense middleware.
 * Protects authenticated state-changing requests (POST, PUT, DELETE, PATCH).
 * Validates Origin/Referer and cryptographically verifies CSRF tokens.
 */

const crypto = require('crypto');

// Default trusted origins for local development, test suites, and production
const DEFAULT_TRUSTED_ORIGINS = new Set([
  'http://localhost:5000',
  'http://127.0.0.1:5000',
  'http://localhost:3000',
  'http://127.0.0.1:3000',
  'https://smart-energy-system-zuqh.onrender.com'
]);

/**
 * Evaluates whether an incoming Origin string is trusted.
 * Automatically trusts:
 * - Local development (localhost, 127.0.0.1 on any port)
 * - Main Render backend (smart-energy-system-zuqh.onrender.com and *.onrender.com)
 * - Vercel deployments (*.vercel.app and vercel.app)
 * - Custom origins defined via APP_ORIGIN or ALLOWED_ORIGINS
 * 
 * @param {string} origin
 * @returns {boolean}
 */
function isTrustedOrigin(origin) {
  if (!origin || typeof origin !== 'string') {
    return false;
  }

  // Exact match with default trusted origins set
  if (DEFAULT_TRUSTED_ORIGINS.has(origin)) {
    return true;
  }

  // Explicit origins from environment variables
  if (process.env.APP_ORIGIN && origin === process.env.APP_ORIGIN) {
    return true;
  }
  if (process.env.ALLOWED_ORIGINS) {
    const list = process.env.ALLOWED_ORIGINS.split(',').map((s) => s.trim());
    if (list.includes(origin)) {
      return true;
    }
  }

  try {
    const url = new URL(origin);
    const hostname = url.hostname.toLowerCase();

    // Trust local development hosts
    if (hostname === 'localhost' || hostname === '127.0.0.1') {
      return true;
    }

    // Trust main Render backend and *.onrender.com
    if (hostname === 'smart-energy-system-zuqh.onrender.com' || hostname.endsWith('.onrender.com')) {
      return true;
    }

    // Trust any Vercel domain (*.vercel.app or vercel.app)
    if (hostname === 'vercel.app' || hostname.endsWith('.vercel.app')) {
      return true;
    }
  } catch (err) {
    return false;
  }

  return false;
}

/**
 * Compares two strings using constant-time comparison to prevent timing attacks.
 * @param {string} a
 * @param {string} b
 * @returns {boolean}
 */
function safeCompare(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') {
    return false;
  }
  const bufA = Buffer.from(a);
  const bufB = Buffer.from(b);
  if (bufA.length !== bufB.length) {
    return false;
  }
  return crypto.timingSafeEqual(bufA, bufB);
}

/**
 * Generates a cryptographically secure hex CSRF token.
 * @returns {string}
 */
function generateCsrfToken() {
  return crypto.randomBytes(32).toString('hex');
}

/**
 * CSRF middleware for state-changing requests.
 * Exempts safe methods (GET, HEAD, OPTIONS) and public unauthenticated endpoints (/login).
 */
function csrfProtection(req, res, next) {
  const method = req.method.toUpperCase();

  // 1. Skip safe read-only methods
  if (['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    return next();
  }

  // 2. Skip unauthenticated public entrypoints (e.g. initial login)
  if (req.path === '/login' || req.path === '/api/login') {
    return next();
  }

  // 3. Origin header check: If browser provided Origin header, verify against trusted origins
  const origin = req.headers.origin;
  if (origin) {
    if (!isTrustedOrigin(origin)) {
      return res.status(403).json({
        status: 'error',
        message: `CSRF validation failed: Cross-origin request from untrusted origin '${origin}' is forbidden.`
      });
    }
  }

  // 4. Token verification for authenticated sessions
  if (req.session && req.session.userId) {
    const expectedToken = req.session.csrfToken;
    const receivedToken =
      req.headers['x-csrf-token'] ||
      req.headers['x-xsrf-token'] ||
      (req.body && (req.body.csrf_token || req.body._csrf));

    if (!expectedToken || !receivedToken || !safeCompare(receivedToken, expectedToken)) {
      return res.status(403).json({
        status: 'error',
        message: 'CSRF validation failed: Invalid or missing CSRF token.'
      });
    }
  }

  return next();
}

module.exports = {
  csrfProtection,
  generateCsrfToken,
  safeCompare,
  isTrustedOrigin,
  DEFAULT_TRUSTED_ORIGINS
};
