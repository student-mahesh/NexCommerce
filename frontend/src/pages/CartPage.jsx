import React from 'react';
import { useSelector, useDispatch } from 'react-redux';
import { Link, useNavigate } from 'react-router-dom';
import { ShoppingBag, Trash2, Plus, Minus, ArrowRight, ArrowLeft, ShieldAlert, AlertTriangle } from 'lucide-react';
import {
  updateQuantity,
  removeFromCart,
  clearCart,
  selectCartTotal,
  selectCartCount,
} from '../features/cart/cartSlice';
import { getProductImage, handleImageError } from '../services/imageHelper';

export default function CartPage() {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const items = useSelector((state) => state.cart.items);
  const subtotal = useSelector(selectCartTotal);
  const totalCount = useSelector(selectCartCount);

  const handleSimulateOutOfStock = (productId) => {
    dispatch(updateQuantity({ id: productId, quantity: 99999 }));
  };

  if (items.length === 0) {
    return (
      <div className="container py-16 text-center">
        <div className="max-w-md mx-auto p-8 rounded-3xl bg-ecom-card border border-ecom-border shadow-xl">
          <div className="w-16 h-16 rounded-2xl bg-ecom-panel flex items-center justify-center mx-auto mb-4 text-slate-500">
            <ShoppingBag className="w-8 h-8" />
          </div>
          <h2 className="text-xl font-bold text-white mb-2">Your Shopping Cart is Empty</h2>
          <p className="text-xs text-ecom-muted mb-6">
            Looks like you haven't added any products to your cart yet. Explore our catalog of high-performance gear.
          </p>
          <Link
            to="/"
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs shadow-lg shadow-indigo-600/30 transition text-decoration-none"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Explore Catalog</span>
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="container py-8">
      {/* Header */}
      <div className="flex items-center justify-between pb-6 mb-6 border-b border-ecom-border">
        <div>
          <h1 className="text-2xl font-black text-white tracking-tight mb-1">Shopping Cart</h1>
          <p className="text-xs text-ecom-muted mb-0">
            Review your selected gear before proceeding to checkout and Kafka saga initiation.
          </p>
        </div>
        <Link
          to="/"
          className="text-xs font-semibold text-indigo-400 hover:text-indigo-300 flex items-center gap-1 text-decoration-none"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>Continue Shopping</span>
        </Link>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Left Column: Cart Items List */}
        <div className="lg:col-span-2 space-y-4">
          <div className="rounded-2xl bg-ecom-card border border-ecom-border overflow-hidden">
            <div className="p-4 bg-ecom-header border-b border-ecom-border flex items-center justify-between text-xs font-bold text-ecom-muted">
              <span>PRODUCT DETAILS</span>
              <span>QUANTITY & SUBTOTAL</span>
            </div>

            <div className="divide-y divide-ecom-border">
              {items.map((item) => {
                const isOverStock = item.quantity > (item.stockQuantity || 99);
                return (
                  <div key={item.id} className="p-4 sm:p-5 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                    <div className="flex items-center gap-4">
                      <img
                        src={getProductImage(item)}
                        alt={item.name}
                        onError={handleImageError}
                        className="w-20 h-20 object-cover rounded-xl border border-ecom-border bg-ecom-surface flex-shrink-0"
                      />
                      <div>
                        <Link
                          to={`/products/${item.id}`}
                          className="text-sm font-bold text-white hover:text-indigo-400 transition text-decoration-none block mb-1"
                        >
                          {item.name}
                        </Link>
                        <div className="flex items-center gap-2 text-[11px] text-ecom-muted mb-2">
                          <span className="font-mono">SKU: {item.sku || 'N/A'}</span>
                          <span>•</span>
                          <span className="text-indigo-400 font-mono font-bold">${Number(item.price).toFixed(2)}</span>
                        </div>
                        {isOverStock ? (
                          <div className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-amber-950/60 border border-amber-800 text-amber-400 text-[10px] font-bold">
                            <AlertTriangle className="w-3 h-3" />
                            <span>Requested ({item.quantity}) exceeds warehouse stock ({item.stockQuantity})</span>
                          </div>
                        ) : (
                          <span className="text-[10px] text-emerald-400 font-medium">
                            In stock ({item.stockQuantity} available)
                          </span>
                        )}
                      </div>
                    </div>

                    <div className="flex sm:flex-col items-center sm:items-end justify-between w-full sm:w-auto gap-3">
                      {/* Quantity Selector */}
                      <div className="flex items-center border border-ecom-border rounded-lg overflow-hidden bg-ecom-input">
                        <button
                          onClick={() => dispatch(updateQuantity({ id: item.id, quantity: item.quantity - 1 }))}
                          className="px-2.5 py-1 text-ecom-muted hover:text-white hover:bg-ecom-panel transition"
                          title="Decrease quantity"
                        >
                          <Minus className="w-3.5 h-3.5" />
                        </button>
                        <span className="px-3 py-1 text-xs font-bold font-mono text-white min-w-[32px] text-center">
                          {item.quantity}
                        </span>
                        <button
                          onClick={() => dispatch(updateQuantity({ id: item.id, quantity: item.quantity + 1 }))}
                          className="px-2.5 py-1 text-ecom-muted hover:text-white hover:bg-ecom-panel transition"
                          title="Increase quantity"
                        >
                          <Plus className="w-3.5 h-3.5" />
                        </button>
                      </div>

                      <div className="flex items-center gap-3">
                        <span className="text-sm font-extrabold text-white font-mono">
                          ${(item.price * item.quantity).toFixed(2)}
                        </span>
                        <button
                          onClick={() => dispatch(removeFromCart(item.id))}
                          className="p-1 text-slate-500 hover:text-rose-400 transition"
                          title="Remove item"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Test Scenario Simulation Card */}
          <div className="p-4 rounded-2xl bg-ecom-card border border-indigo-500/30">
            <div className="flex items-start gap-3">
              <ShieldAlert className="w-5 h-5 text-indigo-400 flex-shrink-0 mt-0.5" />
              <div className="flex-1">
                <h4 className="text-xs font-bold text-white mb-1">
                  Testing / Interview Simulation: Out-of-Stock Scenario
                </h4>
                <p className="text-[11px] text-ecom-muted leading-relaxed mb-3">
                  Want to verify the Inventory Service stock rejection saga? Click below to request 99,999 units of the first product. The Order Service will accept the order in <code>PENDING</code>, the Inventory Service will reject stock, and the order will automatically transition to <code>CANCELLED</code>.
                </p>
                <button
                  type="button"
                  onClick={() => handleSimulateOutOfStock(items[0].id)}
                  className="px-3 py-1.5 rounded-lg bg-amber-500/20 border border-amber-500/40 text-amber-300 hover:bg-amber-500/30 text-xs font-bold transition flex items-center gap-1.5"
                >
                  <AlertTriangle className="w-3.5 h-3.5" />
                  <span>Set {items[0].name} Quantity to 99,999 (Test Rejection)</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Right Column: Order Summary */}
        <div className="space-y-4">
          <div className="p-6 rounded-2xl bg-ecom-card border border-ecom-border shadow-xl">
            <h3 className="text-sm font-bold text-white pb-3 border-b border-ecom-border mb-4">
              Order Summary
            </h3>

            <div className="space-y-2.5 text-xs text-ecom-muted pb-4 border-b border-ecom-border">
              <div className="flex justify-between">
                <span>Items ({totalCount})</span>
                <span className="font-mono text-white font-semibold">${subtotal.toFixed(2)}</span>
              </div>
              <div className="flex justify-between">
                <span>Shipping & Handling</span>
                <span className="font-semibold text-emerald-400">FREE</span>
              </div>
              <div className="flex justify-between">
                <span>Estimated Tax (0%)</span>
                <span className="font-mono text-white font-semibold">$0.00</span>
              </div>
            </div>

            <div className="flex justify-between items-center py-4 mb-4">
              <span className="text-sm font-bold text-white">Total Amount</span>
              <span className="text-2xl font-black text-white font-mono">
                ${subtotal.toFixed(2)}
              </span>
            </div>

            <button
              onClick={() => navigate('/checkout')}
              className="w-full py-3 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs flex items-center justify-center gap-2 shadow-lg shadow-indigo-600/30 transition"
            >
              <span>Proceed to Checkout</span>
              <ArrowRight className="w-4 h-4" />
            </button>

            <button
              onClick={() => dispatch(clearCart())}
              className="w-full mt-2 py-2 text-xs text-slate-500 hover:text-rose-400 transition"
            >
              Clear Shopping Cart
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

