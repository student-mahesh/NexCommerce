import React, { useState, useEffect } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useSelector, useDispatch } from 'react-redux';
import { ShoppingBag, Search, Zap, User, LogOut, Package, Shield, LogIn, LayoutDashboard } from 'lucide-react';
import { selectCartCount, toggleCartDrawer } from '../features/cart/cartSlice';
import { setSearchQuery, fetchProducts } from '../features/products/productSlice';
import { logout } from '../features/auth/authSlice';
import AuthModal from './AuthModal';

export default function Navbar() {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const cartCount = useSelector(selectCartCount);
  const { user, isAuthenticated } = useSelector((state) => state.auth);

  const [searchInput, setSearchInput] = useState('');
  const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
  const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);

  // Auto-open AuthModal if redirected with ?auth=login
  useEffect(() => {
    if (location.search.includes('auth=login') && !isAuthenticated) {
      setIsAuthModalOpen(true);
    }
  }, [location.search, isAuthenticated]);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    dispatch(setSearchQuery(searchInput));
    dispatch(fetchProducts({ page: 0, search: searchInput }));
    navigate('/');
  };

  const handleLogout = () => {
    dispatch(logout());
    setIsUserMenuOpen(false);
    navigate('/');
  };

  return (
    <>
      <nav className="sticky-top bg-ecom-header border-b border-ecom-border shadow-lg py-3 z-40">
        <div className="container">
          <div className="flex items-center justify-between gap-4">
            {/* Brand Logo */}
            <Link to="/" className="flex items-center gap-2.5 text-decoration-none flex-shrink-0">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-600 to-indigo-500 flex items-center justify-center text-white shadow-md shadow-indigo-500/20">
                <Zap className="w-5 h-5 fill-current" />
              </div>
              <div>
                <span className="text-xl font-extrabold tracking-tight text-white block leading-none">
                  NexCommerce
                </span>
                <span className="text-[10px] font-bold text-indigo-400 tracking-wider uppercase">
                  Event-Driven Microservices
                </span>
              </div>
            </Link>

            {/* Search Bar */}
            <form onSubmit={handleSearchSubmit} className="hidden md:flex flex-grow max-w-md mx-4">
              <div className="relative w-full">
                <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
                <input
                  type="text"
                  className="w-full text-xs py-2 pl-9 pr-3 rounded-xl bg-ecom-input border border-ecom-border text-white focus:outline-none focus:border-indigo-500 transition placeholder:text-slate-500"
                  placeholder="Search keyboard, headphones, monitor..."
                  value={searchInput}
                  onChange={(e) => setSearchInput(e.target.value)}
                />
              </div>
            </form>

            {/* Right Nav Actions */}
            <div className="flex items-center gap-3">
              <Link
                to="/"
                className="hidden lg:inline-block text-xs font-semibold text-ecom-muted hover:text-white transition text-decoration-none"
              >
                Catalog
              </Link>

              {isAuthenticated && (
                <Link
                  to="/orders"
                  className="hidden sm:flex items-center gap-1.5 text-xs font-semibold text-ecom-muted hover:text-white transition text-decoration-none px-2.5 py-1.5 rounded-lg hover:bg-ecom-panel"
                >
                  <Package className="w-3.5 h-3.5 text-indigo-400" />
                  <span>My Orders</span>
                </Link>
              )}

              {/* Cart Drawer Trigger */}
              <button
                onClick={() => dispatch(toggleCartDrawer(true))}
                className="relative p-2 rounded-xl bg-ecom-panel border border-ecom-border hover:border-indigo-500 text-ecom-text transition"
                aria-label="Open Shopping Cart"
              >
                <ShoppingBag className="w-4 h-4 text-white" />
                {cartCount > 0 && (
                  <span className="absolute -top-1.5 -right-1.5 min-w-[18px] h-[18px] px-1 rounded-full bg-indigo-600 text-white font-mono text-[10px] font-bold flex items-center justify-center shadow">
                    {cartCount}
                  </span>
                )}
              </button>

              {/* User Session / Auth Button */}
              {isAuthenticated && user ? (
                <div className="relative">
                  <button
                    onClick={() => setIsUserMenuOpen(!isUserMenuOpen)}
                    className="flex items-center gap-2 p-1.5 rounded-xl bg-ecom-panel border border-ecom-border hover:border-indigo-500 text-white transition text-xs font-semibold"
                  >
                    <div className="w-6 h-6 rounded-lg bg-indigo-600/30 border border-indigo-400/40 flex items-center justify-center text-indigo-300">
                      <User className="w-3.5 h-3.5" />
                    </div>
                    <span className="hidden sm:inline max-w-[100px] truncate">{user.name || user.email}</span>
                  </button>

                  {/* Dropdown Menu */}
                  {isUserMenuOpen && (
                    <div className="absolute right-0 mt-2 w-48 rounded-xl bg-ecom-card border border-ecom-border shadow-2xl py-2 z-50">
                      <div className="px-3 py-1.5 border-b border-ecom-border mb-1">
                        <div className="text-xs font-bold text-white truncate">{user.name || 'User'}</div>
                        <div className="text-[10px] text-ecom-muted truncate">{user.email}</div>
                        <span className="inline-block mt-1 text-[9px] font-bold uppercase tracking-wider text-indigo-300 bg-indigo-950/60 border border-indigo-800 px-1.5 py-0.5 rounded">
                          {user.role}
                        </span>
                      </div>
                      {user.role === 'ROLE_ADMIN' && (
                        <Link
                          to="/admin"
                          onClick={() => setIsUserMenuOpen(false)}
                          className="flex items-center gap-2 px-3 py-1.5 text-xs text-indigo-400 hover:bg-ecom-panel transition text-decoration-none font-semibold border-b border-ecom-border mb-1"
                        >
                          <LayoutDashboard className="w-3.5 h-3.5 text-indigo-400" />
                          <span>Admin Console</span>
                        </Link>
                      )}
                      <Link
                        to="/orders"
                        onClick={() => setIsUserMenuOpen(false)}
                        className="flex items-center gap-2 px-3 py-1.5 text-xs text-ecom-text hover:bg-ecom-panel transition text-decoration-none"
                      >
                        <Package className="w-3.5 h-3.5 text-indigo-400" />
                        <span>My Orders</span>
                      </Link>
                      <button
                        onClick={handleLogout}
                        className="w-full flex items-center gap-2 px-3 py-1.5 text-xs text-rose-400 hover:bg-ecom-panel transition text-left"
                      >
                        <LogOut className="w-3.5 h-3.5" />
                        <span>Sign Out</span>
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                <button
                  onClick={() => setIsAuthModalOpen(true)}
                  className="flex items-center gap-1.5 text-xs font-bold px-3 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white transition shadow shadow-indigo-600/20"
                >
                  <LogIn className="w-3.5 h-3.5" />
                  <span>Sign In</span>
                </button>
              )}
            </div>
          </div>
        </div>
      </nav>

      {/* Authentication Modal */}
      <AuthModal
        isOpen={isAuthModalOpen}
        onClose={() => setIsAuthModalOpen(false)}
      />
    </>
  );
}
