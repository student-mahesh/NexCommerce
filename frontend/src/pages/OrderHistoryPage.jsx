import React, { useEffect } from 'react';
import { useSelector, useDispatch } from 'react-redux';
import { Link, useNavigate } from 'react-router-dom';
import {
  Package,
  Calendar,
  CheckCircle2,
  XCircle,
  Clock,
  ArrowRight,
  ShoppingBag,
  Lock,
  Layers,
} from 'lucide-react';
import { fetchMyOrders } from '../features/orders/orderSlice';

export default function OrderHistoryPage() {
  const dispatch = useDispatch();
  const navigate = useNavigate();

  const { ordersList, loading, error } = useSelector((state) => state.orders);
  const { user, isAuthenticated } = useSelector((state) => state.auth);

  useEffect(() => {
    if (isAuthenticated) {
      dispatch(fetchMyOrders());
    }
  }, [isAuthenticated, dispatch]);

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
      });
    } catch {
      return isoString;
    }
  };

  if (!isAuthenticated) {
    return (
      <div className="container py-16 text-center max-w-md mx-auto">
        <div className="p-8 rounded-3xl bg-ecom-card border border-ecom-border shadow-xl">
          <div className="w-16 h-16 rounded-2xl bg-indigo-600/20 border border-indigo-500/40 flex items-center justify-center text-indigo-400 mx-auto mb-4">
            <Lock className="w-8 h-8" />
          </div>
          <h2 className="text-xl font-bold text-white mb-2">Authentication Required</h2>
          <p className="text-xs text-ecom-muted mb-6">
            Please sign in to view your order history and live Saga tracking timelines.
          </p>
          <Link
            to="/"
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-indigo-600 text-white font-bold text-xs"
          >
            <span>Return to Storefront</span>
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="container py-8">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-6 mb-6 border-b border-ecom-border gap-4">
        <div>
          <h1 className="text-2xl font-black text-white tracking-tight mb-1">
            My Order History
          </h1>
          <p className="text-xs text-ecom-muted mb-0">
            Authenticated customer orders for <strong>{user?.name || user?.email}</strong>.
          </p>
        </div>
        <Link
          to="/"
          className="text-xs font-semibold text-indigo-400 hover:text-indigo-300 transition text-decoration-none"
        >
          Browse More Products
        </Link>
      </div>

      {loading && ordersList.length === 0 ? (
        <div className="py-20 text-center">
          <div className="w-10 h-10 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin mx-auto mb-4" />
          <p className="text-xs text-ecom-muted">Loading your past orders...</p>
        </div>
      ) : ordersList.length === 0 ? (
        <div className="py-16 text-center max-w-md mx-auto">
          <div className="p-8 rounded-3xl bg-ecom-card border border-ecom-border shadow-xl">
            <Package className="w-12 h-12 text-slate-600 mx-auto mb-3" />
            <h2 className="text-lg font-bold text-white mb-2">No Orders Found</h2>
            <p className="text-xs text-ecom-muted mb-6">
              You haven't placed any orders yet. Put our distributed Kafka Saga to the test!
            </p>
            <Link
              to="/"
              className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs shadow-md transition text-decoration-none"
            >
              <ShoppingBag className="w-4 h-4" />
              <span>Explore Products</span>
            </Link>
          </div>
        </div>
      ) : (
        <div className="rounded-2xl bg-ecom-card border border-ecom-border shadow-xl overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-ecom-header border-b border-ecom-border text-[11px] font-bold text-ecom-muted uppercase tracking-wider">
                <tr>
                  <th className="py-3.5 px-4">Order Number</th>
                  <th className="py-3.5 px-4">Date Placed</th>
                  <th className="py-3.5 px-4">Items</th>
                  <th className="py-3.5 px-4">Total Amount</th>
                  <th className="py-3.5 px-4">Status</th>
                  <th className="py-3.5 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ecom-border text-ecom-text">
                {ordersList.map((order) => {
                  const itemCount = order.items?.reduce((s, it) => s + it.quantity, 0) || order.items?.length || 1;
                  return (
                    <tr key={order.id} className="hover:bg-ecom-panel/50 transition">
                      <td className="py-3.5 px-4 font-mono font-bold text-white">
                        {order.orderNumber}
                      </td>
                      <td className="py-3.5 px-4 text-ecom-muted font-mono">
                        {formatDateTime(order.createdAt)}
                      </td>
                      <td className="py-3.5 px-4">
                        <span className="text-white font-semibold">{itemCount} items</span>
                      </td>
                      <td className="py-3.5 px-4 font-mono font-extrabold text-white">
                        ${Number(order.totalAmount).toFixed(2)}
                      </td>
                      <td className="py-3.5 px-4">
                        <span
                          className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold uppercase tracking-wider border ${
                            order.status === 'CONFIRMED'
                              ? 'bg-emerald-950/80 text-emerald-300 border-emerald-700'
                              : order.status === 'CANCELLED'
                              ? 'bg-rose-950/80 text-rose-300 border-rose-700'
                              : 'bg-amber-950/80 text-amber-300 border-amber-700'
                          }`}
                        >
                          {order.status === 'CONFIRMED' && <CheckCircle2 className="w-3 h-3" />}
                          {order.status === 'CANCELLED' && <XCircle className="w-3 h-3" />}
                          {order.status === 'PENDING' && <Clock className="w-3 h-3 animate-spin" />}
                          <span>{order.status}</span>
                        </span>
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <button
                          onClick={() => navigate(`/orders/${order.id}`)}
                          className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-ecom-panel hover:bg-indigo-600/30 border border-ecom-border hover:border-indigo-500 text-indigo-300 font-bold transition text-[11px]"
                        >
                          <span>Track Saga</span>
                          <ArrowRight className="w-3.5 h-3.5" />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

