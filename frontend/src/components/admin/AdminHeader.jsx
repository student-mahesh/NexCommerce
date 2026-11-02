import React from 'react';
import { useLocation, Link, useNavigate } from 'react-router-dom';
import { useSelector, useDispatch } from 'react-redux';
import {
  Menu,
  Shield,
  User,
  LogOut,
  Store,
} from 'lucide-react';
import { logout } from '../../features/auth/authSlice';

export default function AdminHeader({ onToggleSidebar }) {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useSelector((state) => state.auth);

  const handleLogout = () => {
    dispatch(logout());
    navigate('/');
  };

  // Derive dynamic page title based on path
  const getPageTitle = (pathname) => {
    switch (pathname) {
      case '/admin':
        return 'System Overview';
      case '/admin/products':
        return 'Product Catalog Management';
      case '/admin/orders':
        return 'Customer Order Oversight';
      case '/admin/inventory':
        return 'Inventory & Stock Oversight';
      default:
        return 'Admin Console';
    }
  };

  const pageTitle = getPageTitle(location.pathname);

  return (
    <header className="sticky-top h-16 bg-[#101A2D] border-b border-[#29384F] z-30 px-4 lg:px-8 flex items-center justify-between shadow-sm">
      {/* Left: Mobile Sidebar Toggle + Page Title */}
      <div className="flex items-center gap-3">
        <button
          onClick={onToggleSidebar}
          className="p-2 rounded-xl text-slate-300 hover:text-white bg-[#172338] border border-[#29384F] hover:border-indigo-500 lg:hidden transition"
          aria-label="Toggle admin navigation menu"
        >
          <Menu className="w-5 h-5" />
        </button>

        <div>
          <h1 className="text-base font-extrabold text-white tracking-tight leading-none mb-0.5">
            {pageTitle}
          </h1>
          <p className="text-[11px] text-slate-400 mb-0 hidden sm:block">
            NexCommerce Event-Driven Platform Control Center
          </p>
        </div>
      </div>

      {/* Right: Store Link & Admin Account Badge */}
      <div className="flex items-center gap-3">
        {/* Quick link back to storefront */}
        <Link
          to="/"
          className="hidden md:flex items-center gap-1.5 text-xs font-semibold text-slate-300 hover:text-white px-3 py-1.5 rounded-xl bg-[#172338] border border-[#29384F] hover:border-indigo-500 transition text-decoration-none"
        >
          <Store className="w-3.5 h-3.5 text-emerald-400" />
          <span>View Store</span>
        </Link>

        {/* Admin User Profile Pill */}
        <div className="flex items-center gap-2 pl-2 border-l border-[#29384F]">
          <div className="w-8 h-8 rounded-xl bg-indigo-600/30 border border-indigo-400/40 flex items-center justify-center text-indigo-300 flex-shrink-0 shadow-inner">
            <User className="w-4 h-4" />
          </div>

          <div className="hidden sm:flex flex-col text-left">
            <div className="flex items-center gap-1.5">
              <span className="text-xs font-bold text-white max-w-[120px] truncate leading-tight">
                {user?.name || user?.email || 'Administrator'}
              </span>
              <span className="inline-flex items-center gap-0.5 text-[9px] font-bold uppercase tracking-wider text-indigo-300 bg-indigo-950/70 border border-indigo-700/60 px-1.5 py-0.5 rounded">
                <Shield className="w-2.5 h-2.5 text-indigo-400" />
                Admin
              </span>
            </div>
            <span className="text-[10px] text-slate-400 truncate max-w-[150px]">
              {user?.email}
            </span>
          </div>

          <button
            onClick={handleLogout}
            className="p-2 rounded-xl text-slate-400 hover:text-rose-400 hover:bg-[#172338] border border-transparent hover:border-rose-900/50 transition ml-1"
            title="Sign Out"
            aria-label="Sign Out"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </div>
    </header>
  );
}

