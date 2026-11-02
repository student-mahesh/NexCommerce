import React, { useState, useEffect } from 'react';
import { useSelector, useDispatch } from 'react-redux';
import { useNavigate, Link } from 'react-router-dom';
import {
  CreditCard,
  CheckCircle2,
  XCircle,
  Truck,
  ArrowRight,
  ShieldCheck,
  Sparkles,
  ShoppingBag,
  ArrowLeft,
  AlertCircle,
  Lock,
} from 'lucide-react';
import { selectCartTotal, selectCartCount, clearCart } from '../features/cart/cartSlice';
import { createOrder, clearOrderError } from '../features/orders/orderSlice';
import { getProductImage, handleImageError } from '../services/imageHelper';
import AuthModal from '../components/AuthModal';

export default function CheckoutPage() {
  const dispatch = useDispatch();
  const navigate = useNavigate();

  const items = useSelector((state) => state.cart.items);
  const subtotal = useSelector(selectCartTotal);
  const totalCount = useSelector(selectCartCount);
  const { user, isAuthenticated } = useSelector((state) => state.auth);
  const { submitting, error } = useSelector((state) => state.orders);

  const [customerName, setCustomerName] = useState(user?.name || 'Demo Customer');
  const [shippingAddress, setShippingAddress] = useState('42 Silicon Way, Tech City');
  const [paymentSimulateMode, setPaymentSimulateMode] = useState('SUCCESS'); // 'SUCCESS' | 'FAILURE'
  const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
  const [validationError, setValidationError] = useState('');

  useEffect(() => {
    if (user?.name) {
      setCustomerName(user.name);
    }
  }, [user]);

  useEffect(() => {
    dispatch(clearOrderError());
  }, [dispatch]);

  // Guard against empty cart
  if (items.length === 0) {
    return (
      <div className="container py-16 text-center">
        <div className="max-w-md mx-auto p-8 rounded-3xl bg-ecom-card border border-ecom-border shadow-xl">
          <ShoppingBag className="w-12 h-12 text-slate-600 mx-auto mb-3" />
          <h2 className="text-xl font-bold text-white mb-2">Your Cart is Empty</h2>
          <p className="text-xs text-ecom-muted mb-6">
            Cannot proceed to checkout without any items in your cart.
          </p>
          <Link
            to="/"
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs shadow-lg shadow-indigo-600/30 transition text-decoration-none"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Browse Catalog</span>
          </Link>
        </div>
      </div>
    );
  }

  const handleSubmitOrder = async (e) => {
    e.preventDefault();
    setValidationError('');

    if (!isAuthenticated) {
      setIsAuthModalOpen(true);
      return;
    }

    if (!customerName.trim()) {
      setValidationError('Please provide the recipient customer name.');
      return;
    }
    if (!shippingAddress.trim()) {
      setValidationError('Please provide a valid shipping address.');
      return;
    }

    const orderPayload = {
      customerName: customerName.trim(),
      shippingAddress: shippingAddress.trim(),
      paymentSimulateMode: paymentSimulateMode,
      items: items.map((it) => ({
        productId: it.id,
        productName: it.name,
        productSku: it.sku || `SKU-${it.id}`,
        unitPrice: Number(it.price),
        quantity: it.quantity,
      })),
    };

    const resultAction = await dispatch(createOrder(orderPayload));
    if (createOrder.fulfilled.match(resultAction)) {
      const createdOrder = resultAction.payload;
      // Cart is cleared ONLY after successful order creation
      dispatch(clearCart());
      navigate(`/order-confirmation/${createdOrder.id}`);
    }
  };

  return (
    <div className="container py-8">
      {/* Page Title */}
      <div className="flex items-center justify-between pb-6 mb-6 border-b border-ecom-border">
        <div>
          <h1 className="text-2xl font-black text-white tracking-tight mb-1">
            Checkout & Saga Simulation
          </h1>
          <p className="text-xs text-ecom-muted mb-0">
            Submit your order to trigger the distributed choreography saga across Order, Inventory, and Payment services.
          </p>
        </div>
        <Link
          to="/cart"
          className="text-xs font-semibold text-indigo-400 hover:text-indigo-300 flex items-center gap-1 text-decoration-none"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>Back to Cart</span>
        </Link>
      </div>

      {/* Authentication Reminder Banner */}
      {!isAuthenticated && (
        <div className="mb-6 p-4 rounded-2xl bg-indigo-950/60 border border-indigo-500/40 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-xl bg-indigo-600/30 border border-indigo-400/30 flex items-center justify-center text-indigo-300">
              <Lock className="w-4 h-4" />
            </div>
            <div>
              <div className="text-xs font-bold text-white">Sign In Required for Checkout</div>
              <div className="text-[11px] text-indigo-200">
                To place an order and track its real-time event timeline, please sign in with demo credentials.
              </div>
            </div>
          </div>
          <button
            type="button"
            onClick={() => setIsAuthModalOpen(true)}
            className="px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs shadow-md transition flex-shrink-0"
          >
            Sign In with 1-Click Demo
          </button>
        </div>
      )}

      {/* Error Banners */}
      {(validationError || error) && (
        <div className="mb-6 p-4 rounded-2xl bg-rose-950/60 border border-rose-800 text-rose-200 text-xs flex items-center gap-3">
          <AlertCircle className="w-5 h-5 text-rose-400 flex-shrink-0" />
          <div>{validationError || error}</div>
        </div>
      )}

      <form onSubmit={handleSubmitOrder}>
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Left Column: Shipping & Simulation Selector */}
          <div className="lg:col-span-2 space-y-6">
            {/* 1. Customer & Shipping Details */}
            <div className="p-6 rounded-2xl bg-ecom-card border border-ecom-border shadow-xl">
              <div className="flex items-center gap-2 pb-3 mb-4 border-b border-ecom-border">
                <Truck className="w-4 h-4 text-indigo-400" />
                <h3 className="text-sm font-bold text-white mb-0">1. Customer & Shipping Address</h3>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-ecom-muted mb-1">
                    Recipient Full Name <span className="text-rose-400">*</span>
                  </label>
                  <input
                    type="text"
                    required
                    value={customerName}
                    onChange={(e) => setCustomerName(e.target.value)}
                    className="w-full text-xs py-2 px-3 rounded-xl bg-ecom-input border border-ecom-border text-white focus:outline-none focus:border-indigo-500 transition"
                    placeholder="Demo Customer"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-ecom-muted mb-1">
                    Customer Account Email
                  </label>
                  <input
                    type="email"
                    disabled
                    value={user?.email || 'customer@test.com'}
                    className="w-full text-xs py-2 px-3 rounded-xl bg-ecom-input/60 border border-ecom-border text-slate-400 cursor-not-allowed"
                  />
                </div>

                <div className="sm:col-span-2">
                  <label className="block text-xs font-semibold text-ecom-muted mb-1">
                    Delivery Shipping Address <span className="text-rose-400">*</span>
                  </label>
                  <textarea
                    rows={2}
                    required
                    value={shippingAddress}
                    onChange={(e) => setShippingAddress(e.target.value)}
                    className="w-full text-xs py-2 px-3 rounded-xl bg-ecom-input border border-ecom-border text-white focus:outline-none focus:border-indigo-500 transition resize-none"
                    placeholder="42 Silicon Way, Tech City"
                  />
                </div>
              </div>
            </div>

            {/* 2. Simulated Payment Selector (INTERVIEW / DEMO CONTROLS) */}
            <div className="p-6 rounded-2xl bg-ecom-card border border-indigo-500/40 shadow-xl relative overflow-hidden">
              <div className="absolute top-0 right-0 px-3 py-1 bg-indigo-600/30 border-b border-l border-indigo-400/30 rounded-bl-xl text-[10px] font-bold text-indigo-300 uppercase tracking-wider flex items-center gap-1">
                <Sparkles className="w-3 h-3" />
                <span>Interview / Demo Controls</span>
              </div>

              <div className="flex items-center gap-2 pb-3 mb-3 border-b border-ecom-border">
                <CreditCard className="w-4 h-4 text-indigo-400" />
                <h3 className="text-sm font-bold text-white mb-0">2. Deterministic Payment Simulation</h3>
              </div>

              <p className="text-xs text-ecom-muted leading-relaxed mb-4">
                This project runs a <strong>Free Mock Payment Gateway</strong>. No real credit card or bank account is charged. Select an execution scenario below to test the distributed Saga choreography:
              </p>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-4">
                {/* SUCCESS Mode Option */}
                <label
                  onClick={() => setPaymentSimulateMode('SUCCESS')}
                  className={`p-4 rounded-xl border cursor-pointer transition flex flex-col justify-between ${
                    paymentSimulateMode === 'SUCCESS'
                      ? 'bg-emerald-950/40 border-emerald-500 shadow-md shadow-emerald-500/10'
                      : 'bg-ecom-input border-ecom-border hover:border-slate-600'
                  }`}
                >
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <div className={`w-4 h-4 rounded-full border flex items-center justify-center ${
                        paymentSimulateMode === 'SUCCESS' ? 'border-emerald-500 bg-emerald-500' : 'border-slate-500'
                      }`}>
                        {paymentSimulateMode === 'SUCCESS' && <div className="w-1.5 h-1.5 rounded-full bg-white" />}
                      </div>
                      <span className="text-xs font-bold text-white">SUCCESS Scenario</span>
                    </div>
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                      <CheckCircle2 className="w-3 h-3" /> Happy Path
                    </span>
                  </div>
                  <p className="text-[11px] text-ecom-muted mb-0 leading-relaxed">
                    Card charge is approved. Order transitions from <code>PENDING</code> ➔ <code>CONFIRMED</code>.
                  </p>
                </label>

                {/* FAILURE Mode Option */}
                <label
                  onClick={() => setPaymentSimulateMode('FAILURE')}
                  className={`p-4 rounded-xl border cursor-pointer transition flex flex-col justify-between ${
                    paymentSimulateMode === 'FAILURE'
                      ? 'bg-rose-950/40 border-rose-500 shadow-md shadow-rose-500/10'
                      : 'bg-ecom-input border-ecom-border hover:border-slate-600'
                  }`}
                >
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <div className={`w-4 h-4 rounded-full border flex items-center justify-center ${
                        paymentSimulateMode === 'FAILURE' ? 'border-rose-500 bg-rose-500' : 'border-slate-500'
                      }`}>
                        {paymentSimulateMode === 'FAILURE' && <div className="w-1.5 h-1.5 rounded-full bg-white" />}
                      </div>
                      <span className="text-xs font-bold text-white">FAILURE Scenario</span>
                    </div>
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-rose-500/20 text-rose-300 border border-rose-500/30">
                      <XCircle className="w-3 h-3" /> Compensation
                    </span>
                  </div>
                  <p className="text-[11px] text-ecom-muted mb-0 leading-relaxed">
                    Card is declined. Triggers inventory rollback: reserved stock is released, and order becomes <code>CANCELLED</code>.
                  </p>
                </label>
              </div>

              <div className="p-3 rounded-xl bg-ecom-surface border border-ecom-border flex items-center gap-2 text-[11px] text-ecom-muted">
                <ShieldCheck className="w-4 h-4 text-indigo-400 flex-shrink-0" />
                <span>
                  Zero payment credentials, card numbers, or CVVs are collected or stored in compliance with PCI-DSS guardrails.
                </span>
              </div>
            </div>
          </div>

          {/* Right Column: Order Review & Placement */}
          <div className="space-y-4">
            <div className="p-6 rounded-2xl bg-ecom-card border border-ecom-border shadow-xl">
              <h3 className="text-sm font-bold text-white pb-3 border-b border-ecom-border mb-4">
                Order Review ({totalCount} items)
              </h3>

              {/* Items Thumbnail List */}
              <div className="max-h-60 overflow-y-auto space-y-3 pr-1 mb-4 divide-y divide-ecom-border">
                {items.map((item) => (
                  <div key={item.id} className="pt-2 first:pt-0 flex items-center gap-3">
                    <img
                      src={getProductImage(item)}
                      alt={item.name}
                      onError={handleImageError}
                      className="w-12 h-12 object-cover rounded-lg border border-ecom-border bg-ecom-surface flex-shrink-0"
                    />
                    <div className="flex-1 min-w-0">
                      <div className="text-xs font-bold text-white truncate">{item.name}</div>
                      <div className="text-[11px] text-ecom-muted">
                        Qty: {item.quantity} × <span className="text-indigo-400 font-mono">${Number(item.price).toFixed(2)}</span>
                      </div>
                    </div>
                    <div className="text-xs font-mono font-bold text-white">
                      ${(item.price * item.quantity).toFixed(2)}
                    </div>
                  </div>
                ))}
              </div>

              {/* Totals */}
              <div className="space-y-2 text-xs text-ecom-muted pb-4 border-b border-ecom-border">
                <div className="flex justify-between">
                  <span>Subtotal</span>
                  <span className="font-mono text-white">${subtotal.toFixed(2)}</span>
                </div>
                <div className="flex justify-between">
                  <span>Standard Shipping</span>
                  <span className="font-semibold text-emerald-400">FREE</span>
                </div>
              </div>

              <div className="flex justify-between items-center py-4 mb-4">
                <span className="text-sm font-bold text-white">Total Charge</span>
                <span className="text-2xl font-black text-white font-mono">
                  ${subtotal.toFixed(2)}
                </span>
              </div>

              {/* Place Order CTA Button */}
              <button
                type="submit"
                disabled={submitting}
                className="w-full py-3.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-bold text-xs flex items-center justify-center gap-2 shadow-lg shadow-indigo-600/30 transition cursor-pointer"
              >
                {submitting ? (
                  <>
                    <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    <span>Initiating Saga Workflow...</span>
                  </>
                ) : (
                  <>
                    <span>Place Order & Initiate Saga</span>
                    <ArrowRight className="w-4 h-4" />
                  </>
                )}
              </button>

              <p className="text-[10px] text-center text-ecom-muted mt-3 mb-0">
                Clicking above writes order to MySQL and dispatches <code>OrderCreatedEvent</code> to Kafka.
              </p>
            </div>
          </div>
        </div>
      </form>

      {/* Auth Modal */}
      <AuthModal
        isOpen={isAuthModalOpen}
        onClose={() => setIsAuthModalOpen(false)}
      />
    </div>
  );
}

