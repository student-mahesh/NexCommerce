import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { ShieldAlert, Loader2 } from 'lucide-react';
import { isAdminToken } from '../utils/jwt';

/**
 * AdminRoute Component
 * Guards all /admin routes.
 *
 * Requirements:
 * 1. Logged-out visitor -> Redirect to storefront with login prompt (/?auth=login).
 * 2. Authenticated customer -> Redirect to storefront with access denied state.
 * 3. Verified Admin -> Render child admin routes (<Outlet />).
 * 4. Token validation -> Cryptographically-bound claim check via parseJwt (cannot be bypassed by editing localStorage user JSON).
 * 5. Refresh resilience -> Shows loading spinner while initial checkAuth verifies token with backend.
 */
export default function AdminRoute({ children }) {
  const location = useLocation();
  const { user, token, isAuthenticated, loading } = useSelector((state) => state.auth);

  // 1. If auth state is still validating (e.g. browser refresh running checkAuth)
  if (loading) {
    return (
      <div className="min-h-screen bg-[#111827] flex flex-col items-center justify-center text-[#E8EEF7] p-4">
        <div className="flex flex-col items-center gap-3 p-8 rounded-2xl bg-[#172338] border border-[#29384F] shadow-2xl max-w-sm text-center">
          <Loader2 className="w-8 h-8 text-indigo-400 animate-spin" />
          <h2 className="text-sm font-bold tracking-tight text-white mb-0">
            Verifying Admin Authorization
          </h2>
          <p className="text-xs text-slate-400 mb-0">
            Authorizing session against NexCommerce identity service...
          </p>
        </div>
      </div>
    );
  }

  // 2. Unauthenticated user: redirect to login
  if (!isAuthenticated || !token) {
    return (
      <Navigate
        to="/?auth=login"
        state={{ from: location, message: 'Please sign in with administrator credentials.' }}
        replace
      />
    );
  }

  // 3. Dual-layer verification: check both Redux user role AND JWT token payload role
  const isVerifiedAdmin = user?.role === 'ROLE_ADMIN' && isAdminToken(token);

  if (!isVerifiedAdmin) {
    // Authenticated customer attempting to access /admin
    return (
      <Navigate
        to="/"
        state={{ error: 'Access Denied: Administrator privileges (ROLE_ADMIN) required.' }}
        replace
      />
    );
  }

  // 4. Authorized Admin: render nested routes or children
  return children ? children : <Outlet />;
}

