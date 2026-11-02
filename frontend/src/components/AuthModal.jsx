import React, { useState } from 'react';
import { useSelector, useDispatch } from 'react-redux';
import { X, Lock, Mail, User, Sparkles, Shield, ArrowRight } from 'lucide-react';
import { loginUser, registerUser, clearAuthError } from '../features/auth/authSlice';

export default function AuthModal({ isOpen, onClose }) {
  const dispatch = useDispatch();
  const { loading, error } = useSelector((state) => state.auth);

  const [tab, setTab] = useState('login'); // 'login' | 'register'
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    password: '',
  });

  if (!isOpen) return null;

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
    if (error) dispatch(clearAuthError());
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (tab === 'login') {
      const res = await dispatch(loginUser({ email: formData.email, password: formData.password }));
      if (!res.error) onClose();
    } else {
      const res = await dispatch(registerUser(formData));
      if (!res.error) onClose();
    }
  };

  const handleDemoSignIn = async (email, password) => {
    const res = await dispatch(loginUser({ email, password }));
    if (!res.error) onClose();
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm transition-opacity"
        onClick={onClose}
      />

      {/* Modal Card */}
      <div className="relative w-full max-w-md bg-ecom-card border border-ecom-border rounded-2xl shadow-2xl p-6 text-ecom-text z-10">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-ecom-border mb-4">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center text-white">
              <Lock className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white mb-0">
                {tab === 'login' ? 'Sign In to NexCommerce' : 'Create Customer Account'}
              </h3>
              <p className="text-xs text-ecom-muted mb-0">JWT Authentication & Gateway Security</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-ecom-muted hover:text-white p-1 rounded-lg hover:bg-ecom-panel transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Tab Switcher */}
        <div className="flex border border-ecom-border rounded-xl p-1 bg-ecom-input mb-4 text-xs font-semibold">
          <button
            type="button"
            onClick={() => { setTab('login'); dispatch(clearAuthError()); }}
            className={`flex-1 py-1.5 rounded-lg transition ${
              tab === 'login' ? 'bg-indigo-600 text-white shadow' : 'text-ecom-muted hover:text-white'
            }`}
          >
            Sign In
          </button>
          <button
            type="button"
            onClick={() => { setTab('register'); dispatch(clearAuthError()); }}
            className={`flex-1 py-1.5 rounded-lg transition ${
              tab === 'register' ? 'bg-indigo-600 text-white shadow' : 'text-ecom-muted hover:text-white'
            }`}
          >
            Register
          </button>
        </div>

        {/* Demo Fast Login Pills */}
        <div className="mb-4 p-3 rounded-xl bg-ecom-panel/60 border border-indigo-500/30">
          <div className="flex items-center gap-1.5 text-xs font-semibold text-indigo-400 mb-2">
            <Sparkles className="w-3.5 h-3.5" />
            <span>1-Click Demo Credentials:</span>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              disabled={loading}
              onClick={() => handleDemoSignIn('customer@test.com', 'Password123!')}
              className="text-xs py-1.5 px-2.5 rounded-lg bg-ecom-input hover:bg-indigo-950/60 border border-ecom-border hover:border-indigo-500 text-ecom-text transition text-left flex items-center justify-between"
            >
              <div>
                <div className="font-bold text-white">Demo Customer</div>
                <div className="text-[10px] text-ecom-muted">customer@test.com</div>
              </div>
              <ArrowRight className="w-3.5 h-3.5 text-indigo-400" />
            </button>
            <button
              type="button"
              disabled={loading}
              onClick={() => handleDemoSignIn('admin@test.com', 'AdminPass123!')}
              className="text-xs py-1.5 px-2.5 rounded-lg bg-ecom-input hover:bg-indigo-950/60 border border-ecom-border hover:border-indigo-500 text-ecom-text transition text-left flex items-center justify-between"
            >
              <div>
                <div className="font-bold text-white flex items-center gap-1">
                  <span>Admin</span>
                  <Shield className="w-3 h-3 text-amber-400" />
                </div>
                <div className="text-[10px] text-ecom-muted">admin@test.com</div>
              </div>
              <ArrowRight className="w-3.5 h-3.5 text-indigo-400" />
            </button>
          </div>
        </div>

        {/* Error Alert */}
        {error && (
          <div className="p-2.5 rounded-xl bg-rose-950/60 border border-rose-800 text-rose-300 text-xs mb-3">
            {error}
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-3">
          {tab === 'register' && (
            <div>
              <label className="block text-xs font-semibold text-ecom-muted mb-1">Full Name</label>
              <div className="relative">
                <User className="w-4 h-4 text-slate-500 absolute left-3 top-2.5" />
                <input
                  type="text"
                  name="name"
                  required
                  placeholder="Jane Doe"
                  value={formData.name}
                  onChange={handleChange}
                  className="w-full text-xs py-2 pl-9 pr-3 rounded-xl bg-ecom-input border border-ecom-border text-white focus:outline-none focus:border-indigo-500 transition"
                />
              </div>
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold text-ecom-muted mb-1">Email Address</label>
            <div className="relative">
              <Mail className="w-4 h-4 text-slate-500 absolute left-3 top-2.5" />
              <input
                type="email"
                name="email"
                required
                placeholder="customer@test.com"
                value={formData.email}
                onChange={handleChange}
                className="w-full text-xs py-2 pl-9 pr-3 rounded-xl bg-ecom-input border border-ecom-border text-white focus:outline-none focus:border-indigo-500 transition"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-ecom-muted mb-1">Password</label>
            <div className="relative">
              <Lock className="w-4 h-4 text-slate-500 absolute left-3 top-2.5" />
              <input
                type="password"
                name="password"
                required
                placeholder="••••••••"
                value={formData.password}
                onChange={handleChange}
                className="w-full text-xs py-2 pl-9 pr-3 rounded-xl bg-ecom-input border border-ecom-border text-white focus:outline-none focus:border-indigo-500 transition"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full mt-2 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs shadow-md transition flex items-center justify-center gap-2 disabled:opacity-50"
          >
            {loading ? (
              <span>Authenticating...</span>
            ) : tab === 'login' ? (
              <span>Sign In</span>
            ) : (
              <span>Register Account</span>
            )}
          </button>
        </form>
      </div>
    </div>
  );
}

