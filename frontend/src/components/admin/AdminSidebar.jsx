import React from 'react';
import { NavLink, Link, useNavigate } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import {
  Zap,
  LayoutDashboard,
  Package,
  ShoppingBag,
  Layers,
  Store,
  LogOut,
  X,
  ShieldCheck,
} from 'lucide-react';
import { logout } from '../../features/auth/authSlice';

export default function AdminSidebar({ isOpen, onClose }) {
  const dispatch = useDispatch();
  const navigate = useNavigate();

  const handleLogout = () => {
    dispatch(logout());
    if (onClose) onClose();
    navigate('/');
  };

  const navItems = [
    {
      to: '/admin',
      label: 'Overview',
      icon: LayoutDashboard,
      end: true,
      badge: 'Live',
    },
    {
      to: '/admin/products',
      label: 'Products',
      icon: Package,
      end: false,
      badge: 'Step 4',
    },
    {
      to: '/admin/orders',
      label: 'Orders',
      icon: ShoppingBag,
      end: false,
      badge: 'Step 5',
    },
    {
      to: '/admin/inventory',
      label: 'Inventory',
      icon: Layers,
      end: false,
      badge: 'Step 6',
    },
  ];

  return (
    <>
      {/* Mobile Drawer Backdrop */}
      {isOpen && (
        <div
          className="fixed inset-0 bg-black/60 backdrop-blur-sm z-40 lg:hidden"
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      {/* Sidebar Container */}
      <aside
        className={`fixed top-0 bottom-0 left-0 z-50 w-64 bg-[#111C30] border-r border-[#29384F] flex flex-col transition-transform duration-200 ease-in-out lg:translate-x-0 ${
          isOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
        aria-label="Admin Navigation Sidebar"
      >
        {/* Brand Header */}
        <div className="h-16 px-5 border-b border-[#29384F] flex items-center justify-between flex-shrink-0">
          <Link
            to="/admin"
            onClick={onClose}
            className="flex items-center gap-2.5 text-decoration-none"
          >
            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-600 to-indigo-500 flex items-center justify-center text-white shadow-md shadow-indigo-500/20">
              <Zap className="w-5 h-5 fill-current" />
            </div>
            <div>
              <span className="text-sm font-extrabold tracking-tight text-white block leading-none">
                NexCommerce
              </span>
              <span className="text-[10px] font-bold text-indigo-400 tracking-wider uppercase flex items-center gap-1 mt-0.5">
                <ShieldCheck className="w-3 h-3 inline text-emerald-400" />
                Admin Console
              </span>
            </div>
          </Link>

          {/* Close button on mobile/tablet */}
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-[#172338] lg:hidden transition"
            aria-label="Close admin sidebar"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Navigation Links */}
        <div className="flex-1 px-3 py-4 overflow-y-auto space-y-1">
          <div className="px-3 pb-2 text-[10px] font-bold uppercase tracking-wider text-slate-400">
            Navigation
          </div>

          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                onClick={onClose}
                className={({ isActive }) =>
                  `flex items-center justify-between px-3 py-2.5 rounded-xl text-xs font-semibold text-decoration-none transition ${
                    isActive
                      ? 'bg-indigo-600/20 text-indigo-300 border border-indigo-500/30'
                      : 'text-slate-300 hover:text-white hover:bg-[#172338]'
                  }`
                }
              >
                <div className="flex items-center gap-2.5">
                  <Icon className="w-4 h-4 text-indigo-400 flex-shrink-0" />
                  <span>{item.label}</span>
                </div>
                {item.badge && (
                  <span className="text-[9px] font-bold uppercase tracking-wide px-1.5 py-0.5 rounded bg-[#172338] text-slate-400 border border-[#29384F]">
                    {item.badge}
                  </span>
                )}
              </NavLink>
            );
          })}
        </div>

        {/* Bottom Actions */}
        <div className="p-3 border-t border-[#29384F] space-y-1 flex-shrink-0 bg-[#0F172A]/40">
          <Link
            to="/"
            onClick={onClose}
            className="flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs font-semibold text-slate-300 hover:text-white hover:bg-[#172338] transition text-decoration-none"
          >
            <Store className="w-4 h-4 text-emerald-400 flex-shrink-0" />
            <span>Back to Storefront</span>
          </Link>

          <button
            onClick={handleLogout}
            className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs font-semibold text-rose-400 hover:text-rose-300 hover:bg-[#172338] transition text-left"
          >
            <LogOut className="w-4 h-4 text-rose-400 flex-shrink-0" />
            <span>Sign Out</span>
          </button>
        </div>
      </aside>
    </>
  );
}

