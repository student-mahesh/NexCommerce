import React, { useState, useEffect, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useSelector, useDispatch } from 'react-redux';
import {
  Package,
  Calendar,
  Clock,
  CheckCircle2,
  XCircle,
  Truck,
  ArrowLeft,
  RefreshCw,
  Bell,
  AlertTriangle,
  Layers,
  CreditCard,
  ShieldAlert,
} from 'lucide-react';
import { fetchOrderById, fetchOrderNotifications } from '../features/orders/orderSlice';
import OrderStepper from '../features/orders/OrderStepper';
import Toast from '../components/Toast';

export default function OrderTrackingPage() {
  const { id } = useParams();
  const dispatch = useDispatch();

  const { currentOrder, notifications, loading, error } = useSelector((state) => state.orders);
  const [toast, setToast] = useState(null); // { message, type }
  const prevStatusRef = useRef(null);
  const intervalRef = useRef(null);

  // Polling logic
  useEffect(() => {
    if (!id) return;

    const loadData = async () => {
      const orderAction = await dispatch(fetchOrderById(id));
      await dispatch(fetchOrderNotifications(id));

      if (fetchOrderById.fulfilled.match(orderAction)) {
        const order = orderAction.payload;
        const currentStatus = order.status;

        // Check for meaningful status transition
        if (prevStatusRef.current && prevStatusRef.current !== currentStatus) {
          if (currentStatus === 'CONFIRMED') {
            setToast({
              message: `Order #${id} Confirmed! Warehouse inventory reserved and payment settled.`,
              type: 'success',
            });
          } else if (currentStatus === 'CANCELLED') {
            setToast({
              message: `Order #${id} Cancelled: ${order.cancellationReason || 'Saga failed.'}`,
              type: 'error',
            });
          }
        }
        prevStatusRef.current = currentStatus;

        // Stop polling if terminal state reached
        if (currentStatus === 'CONFIRMED' || currentStatus === 'CANCELLED') {
          if (intervalRef.current) {
            clearInterval(intervalRef.current);
            intervalRef.current = null;
          }
        }
      }
    };

    // Initial load
    loadData();

    // Start polling while in progress
    intervalRef.current = setInterval(() => {
      loadData();
    }, 2000);

    // Unmount cleanup to prevent memory leaks
    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [id, dispatch]);

  const handleManualRefresh = () => {
    if (id) {
      dispatch(fetchOrderById(id));
      dispatch(fetchOrderNotifications(id));
    }
  };

  const formatDateTime = (isoString) => {
    if (!isoString) return 'N/A';
    try {
      const d = new Date(isoString);
      return d.toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  if (loading && !currentOrder) {
    return (
      <div className="container py-20 text-center">
        <div className="w-10 h-10 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin mx-auto mb-4" />
        <p className="text-xs text-ecom-muted">Retrieving order details and event stream...</p>
      </div>
    );
  }

  if (error && !currentOrder) {
    return (
      <div className="container py-16 text-center max-w-md mx-auto">
        <div className="p-8 rounded-3xl bg-ecom-card border border-rose-800 text-rose-200">
          <XCircle className="w-12 h-12 text-rose-400 mx-auto mb-3" />
          <h2 className="text-lg font-bold text-white mb-2">Order Not Found</h2>
          <p className="text-xs text-rose-300 mb-6">{error}</p>
          <Link
            to="/orders"
            className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-indigo-600 text-white font-bold text-xs"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Back to My Orders</span>
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="container py-8">
      {/* Toast Notification */}
      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast(null)}
        />
      )}

      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-6 mb-6 border-b border-ecom-border gap-4">
        <div>
          <div className="flex items-center gap-3 mb-1">
            <h1 className="text-2xl font-black text-white tracking-tight mb-0">
              Order Tracking: {currentOrder?.orderNumber || `#${id}`}
            </h1>
            <span
              className={`px-2.5 py-0.5 rounded-full text-xs font-mono font-bold border ${
                currentOrder?.status === 'CONFIRMED'
                  ? 'bg-emerald-950/80 text-emerald-300 border-emerald-700'
                  : currentOrder?.status === 'CANCELLED'
                  ? 'bg-rose-950/80 text-rose-300 border-rose-700'
                  : 'bg-amber-950/80 text-amber-300 border-amber-700'
              }`}
            >
              {currentOrder?.status || 'PENDING'}
            </span>
          </div>
          <p className="text-xs text-ecom-muted mb-0">
            Live asynchronous Kafka Saga event stream, inventory reservation, and payment settlement.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={handleManualRefresh}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-ecom-panel border border-ecom-border hover:border-indigo-500 text-xs text-ecom-text transition"
            title="Refresh order and notifications"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span>Refresh</span>
          </button>
          <Link
            to="/orders"
            className="flex items-center gap-1.5 text-xs font-semibold text-indigo-400 hover:text-indigo-300 transition text-decoration-none"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>Order History</span>
          </Link>
        </div>
      </div>

      {/* Cancellation Banner (if cancelled) */}
      {currentOrder?.status === 'CANCELLED' && (
        <div className="mb-6 p-4 rounded-2xl bg-rose-950/50 border border-rose-800 text-rose-200 flex items-start gap-3">
          <AlertTriangle className="w-5 h-5 text-rose-400 flex-shrink-0 mt-0.5" />
          <div>
            <div className="text-xs font-bold text-white mb-0.5">Order Cancelled</div>
            <div className="text-xs text-rose-300 leading-relaxed">
              Reason: <strong>{currentOrder?.cancellationReason || 'Saga failed; compensation release finished.'}</strong>
            </div>
          </div>
        </div>
      )}

      {/* 1. Visual Distributed Saga Stepper */}
      <div className="mb-8">
        <OrderStepper order={currentOrder} notifications={notifications} />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Left Column: Notification Audit Timeline */}
        <div className="lg:col-span-2 space-y-6">
          <div className="p-6 rounded-2xl bg-ecom-card border border-ecom-border shadow-xl">
            <div className="flex items-center justify-between pb-4 mb-4 border-b border-ecom-border">
              <div className="flex items-center gap-2">
                <Bell className="w-4 h-4 text-indigo-400" />
                <h3 className="text-sm font-bold text-white mb-0">
                  Event Timeline & Notifications ({notifications.length})
                </h3>
              </div>
              <span className="text-[10px] text-ecom-muted">
                Source: <code>notification_logs</code>
              </span>
            </div>

            {notifications.length === 0 ? (
              <div className="text-center py-12 text-ecom-muted text-xs">
                <Clock className="w-8 h-8 mx-auto mb-2 opacity-50" />
                <p className="mb-0">Awaiting incoming Kafka event notifications...</p>
              </div>
            ) : (
              <div className="relative pl-6 space-y-6 before:absolute before:left-2.5 before:top-2 before:bottom-2 before:w-0.5 before:bg-ecom-border">
                {notifications.map((notif) => {
                  const isSuccessEvent =
                    notif.eventType === 'ORDER_CREATED' ||
                    notif.eventType === 'INVENTORY_RESERVED' ||
                    notif.eventType === 'PAYMENT_SUCCEEDED';
                  return (
                    <div key={notif.id} className="relative">
                      {/* Timeline Dot */}
                      <div
                        className={`absolute -left-[30px] top-1 w-4 h-4 rounded-full border-2 bg-ecom-card flex items-center justify-center ${
                          isSuccessEvent ? 'border-emerald-500' : 'border-rose-500'
                        }`}
                      >
                        <div
                          className={`w-1.5 h-1.5 rounded-full ${
                            isSuccessEvent ? 'bg-emerald-400' : 'bg-rose-400'
                          }`}
                        />
                      </div>

                      <div className="p-4 rounded-xl bg-ecom-panel border border-ecom-border">
                        <div className="flex flex-wrap items-center justify-between gap-2 mb-1.5">
                          <span
                            className={`inline-block px-2 py-0.5 rounded text-[10px] font-mono font-bold uppercase tracking-wider border ${
                              isSuccessEvent
                                ? 'bg-emerald-950/60 text-emerald-300 border-emerald-800'
                                : 'bg-rose-950/60 text-rose-300 border-rose-800'
                            }`}
                          >
                            {notif.eventType}
                          </span>
                          <span className="text-[11px] text-ecom-muted font-mono">
                            {formatDateTime(notif.createdAt)}
                          </span>
                        </div>

                        <h4 className="text-xs font-bold text-white mb-1">{notif.subject}</h4>
                        <p className="text-xs text-ecom-muted leading-relaxed mb-2">
                          {notif.message}
                        </p>

                        <div className="flex items-center gap-3 text-[10px] text-slate-500 font-mono">
                          <span>EventId: {notif.eventId ? notif.eventId.substring(0, 16) + '...' : 'N/A'}</span>
                          {notif.correlationId && (
                            <span>Corr: {notif.correlationId.substring(0, 14)}...</span>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* Right Column: Order Details & Items */}
        <div className="space-y-6">
          {/* Order Details Card */}
          <div className="p-6 rounded-2xl bg-ecom-card border border-ecom-border shadow-xl">
            <h3 className="text-sm font-bold text-white pb-3 border-b border-ecom-border mb-4">
              Order Details
            </h3>

            <div className="space-y-3 text-xs">
              <div>
                <span className="text-ecom-muted block text-[11px]">Placed On</span>
                <span className="font-mono text-white font-semibold">
                  {formatDateTime(currentOrder?.createdAt)}
                </span>
              </div>

              <div>
                <span className="text-ecom-muted block text-[11px]">Customer Recipient</span>
                <span className="text-white font-semibold">
                  {currentOrder?.customerName || 'N/A'} ({currentOrder?.customerEmail || 'N/A'})
                </span>
              </div>

              <div>
                <span className="text-ecom-muted block text-[11px]">Delivery Address</span>
                <span className="text-white font-semibold leading-relaxed">
                  {currentOrder?.shippingAddress || 'N/A'}
                </span>
              </div>

              <div>
                <span className="text-ecom-muted block text-[11px]">Payment Mode Tested</span>
                <span className="font-mono font-bold text-indigo-300">
                  {currentOrder?.paymentSimulateMode || 'SUCCESS'}
                </span>
              </div>
            </div>
          </div>

          {/* Ordered Items Card */}
          <div className="p-6 rounded-2xl bg-ecom-card border border-ecom-border shadow-xl">
            <h3 className="text-sm font-bold text-white pb-3 border-b border-ecom-border mb-4">
              Purchased Items ({currentOrder?.items?.length || 0})
            </h3>

            <div className="space-y-3 divide-y divide-ecom-border">
              {currentOrder?.items?.map((item) => (
                <div key={item.id} className="pt-3 first:pt-0 flex items-center justify-between gap-3 text-xs">
                  <div>
                    <div className="font-bold text-white">{item.productName}</div>
                    <div className="text-[11px] text-ecom-muted font-mono">
                      Qty: {item.quantity} × ${Number(item.unitPrice).toFixed(2)}
                    </div>
                  </div>
                  <div className="font-mono font-bold text-white">
                    ${Number(item.subtotal || item.unitPrice * item.quantity).toFixed(2)}
                  </div>
                </div>
              ))}
            </div>

            <div className="pt-4 mt-4 border-t border-ecom-border flex items-center justify-between text-xs">
              <span className="font-bold text-white">Total Amount</span>
              <span className="text-lg font-black text-white font-mono">
                ${Number(currentOrder?.totalAmount || 0).toFixed(2)}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

