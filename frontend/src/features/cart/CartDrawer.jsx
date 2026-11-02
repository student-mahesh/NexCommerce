import React from 'react';
import { useSelector, useDispatch } from 'react-redux';
import { Link, useNavigate } from 'react-router-dom';
import { X, Trash2, Plus, Minus, ShoppingBag, ArrowRight } from 'lucide-react';
import {
  toggleCartDrawer,
  updateQuantity,
  removeFromCart,
  clearCart,
  selectCartTotal,
} from './cartSlice';
import { getProductImage, handleImageError } from '../../services/imageHelper';

export default function CartDrawer() {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { items, isDrawerOpen } = useSelector((state) => state.cart);
  const subtotal = useSelector(selectCartTotal);

  if (!isDrawerOpen) return null;

  const handleCheckoutClick = () => {
    dispatch(toggleCartDrawer(false));
    navigate('/checkout');
  };

  const handleViewCartClick = () => {
    dispatch(toggleCartDrawer(false));
    navigate('/cart');
  };

  return (
    <div className="fixed inset-0 z-50 overflow-hidden">
      {/* Backdrop */}
      <div
        onClick={() => dispatch(toggleCartDrawer(false))}
        className="absolute inset-0 bg-slate-950/70 backdrop-blur-sm transition-opacity"
      />

      <div className="fixed inset-y-0 right-0 max-w-full flex pl-10">
        <div className="w-screen max-w-md bg-ecom-card border-l border-ecom-border shadow-2xl flex flex-col text-ecom-text">
          {/* Header */}
          <div className="p-4 border-b border-ecom-border flex items-center justify-between bg-ecom-header">
            <div className="flex items-center gap-2">
              <ShoppingBag className="w-5 h-5 text-indigo-400" />
              <h2 className="text-base font-bold text-white mb-0">Shopping Cart</h2>
              <span className="badge bg-indigo-950 text-indigo-300 border border-indigo-700 rounded-full font-mono text-xs">
                {items.length} items
              </span>
            </div>
            <button
              onClick={() => dispatch(toggleCartDrawer(false))}
              className="p-1.5 rounded-lg text-ecom-muted hover:text-white hover:bg-ecom-panel transition"
              aria-label="Close cart"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Cart Items List */}
          <div className="flex-1 overflow-y-auto p-4 divide-y divide-ecom-border">
            {items.length === 0 ? (
              <div className="text-center py-20">
                <ShoppingBag className="w-12 h-12 text-slate-600 mx-auto mb-3" />
                <p className="text-ecom-muted text-sm font-medium">Your shopping cart is empty.</p>
                <button
                  onClick={() => dispatch(toggleCartDrawer(false))}
                  className="btn btn-sm btn-outline-primary rounded-xl mt-3 text-xs"
                >
                  Start Browsing
                </button>
              </div>
            ) : (
              items.map((item) => (
                <div key={item.id} className="py-3 flex gap-3 items-center">
                  <img
                    src={getProductImage(item)}
                    alt={item.name}
                    onError={handleImageError}
                    className="w-16 h-16 object-cover rounded-xl border border-ecom-border bg-ecom-surface flex-shrink-0"
                  />
                  <div className="flex-1 min-w-0">
                    <h3 className="text-xs font-bold text-white truncate mb-1">{item.name}</h3>
                    <div className="text-xs text-indigo-400 font-bold mb-2 font-mono">
                      ${Number(item.price).toFixed(2)}
                    </div>
                    {/* Quantity Selector */}
                    <div className="flex items-center gap-2">
                      <div className="flex items-center border border-ecom-border rounded-lg overflow-hidden bg-ecom-input">
                        <button
                          onClick={() =>
                            dispatch(updateQuantity({ id: item.id, quantity: item.quantity - 1 }))
                          }
                          className="px-2 py-0.5 text-ecom-muted hover:text-white hover:bg-ecom-panel transition"
                          title="Decrease quantity"
                        >
                          <Minus className="w-3 h-3" />
                        </button>
                        <span className="px-2 py-0.5 text-xs font-bold font-mono text-white">
                          {item.quantity}
                        </span>
                        <button
                          onClick={() =>
                            dispatch(updateQuantity({ id: item.id, quantity: item.quantity + 1 }))
                          }
                          className="px-2 py-0.5 text-ecom-muted hover:text-white hover:bg-ecom-panel transition"
                          title="Increase quantity"
                        >
                          <Plus className="w-3 h-3" />
                        </button>
                      </div>
                      <span className="text-[11px] text-ecom-muted font-mono">
                        Subtotal: ${(item.price * item.quantity).toFixed(2)}
                      </span>
                    </div>
                  </div>
                  <button
                    onClick={() => dispatch(removeFromCart(item.id))}
                    className="text-slate-500 hover:text-rose-400 p-1 rounded transition"
                    title="Remove item"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              ))
            )}
          </div>

          {/* Footer & Checkout Summary */}
          {items.length > 0 && (
            <div className="p-4 border-t border-ecom-border bg-ecom-header">
              <div className="flex justify-between items-center mb-1 text-xs text-ecom-muted">
                <span>Shipping</span>
                <span className="font-semibold text-emerald-400">FREE</span>
              </div>
              <div className="flex justify-between items-center mb-4">
                <span className="text-sm font-bold text-white">Estimated Total</span>
                <span className="text-xl font-extrabold text-white font-mono">
                  ${subtotal.toFixed(2)}
                </span>
              </div>
              <button
                onClick={handleCheckoutClick}
                className="w-full py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs flex items-center justify-center gap-2 shadow-lg shadow-indigo-600/30 transition mb-2"
              >
                <span>Proceed to Checkout</span>
                <ArrowRight className="w-4 h-4" />
              </button>
              <div className="flex items-center justify-between text-xs pt-1">
                <button
                  onClick={handleViewCartClick}
                  className="text-indigo-400 hover:text-indigo-300 font-semibold"
                >
                  View Full Cart
                </button>
                <button
                  onClick={() => dispatch(clearCart())}
                  className="text-rose-400 hover:text-rose-300"
                >
                  Clear Cart
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
