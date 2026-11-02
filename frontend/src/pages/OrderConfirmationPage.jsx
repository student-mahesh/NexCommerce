import React, { useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useSelector, useDispatch } from 'react-redux';
import {
  CheckCircle2,
  Package,
  ArrowRight,
  Clock,
  Sparkles,
  ShoppingBag,
  ListOrdered,
} from 'lucide-react';
import { fetchOrderById } from '../features/orders/orderSlice';

export default function OrderConfirmationPage() {
  const { id } = useParams();
  const dispatch = useDispatch();
  const navigate = useNavigate();

  const { currentOrder, loading } = useSelector((state) => state.orders);

  useEffect(() => {
    if (id) {
      dispatch(fetchOrderById(id));
    }
  }, [id, dispatch]);

  return (
    <div className="container py-12 max-w-2xl mx-auto">
      <div className="p-8 rounded-3xl bg-ecom-card border border-ecom-border shadow-2xl text-center">
        {/* Celebration Icon */}
        <div className="w-16 h-16 rounded-3xl bg-emerald-500/20 border border-emerald-500/40 flex items-center justify-center text-emerald-400 mx-auto mb-5 shadow-lg shadow-emerald-500/10">
          <CheckCircle2 className="w-8 h-8" />
        </div>

        <h1 className="text-2xl font-black text-white tracking-tight mb-2">
          Order Placed Successfully!
        </h1>
        <p className="text-xs text-ecom-muted max-w-md mx-auto mb-6 leading-relaxed">
          Your order has been recorded into MySQL and dispatched to the Apache Kafka broker. The distributed Saga choreography is currently in progress.
        </p>

        {/* Order Details Summary Card */}
        <div className="p-4 rounded-2xl bg-ecom-panel border border-ecom-border text-left mb-6 space-y-3">
          <div className="flex items-center justify-between pb-3 border-b border-ecom-border text-xs">
            <span className="text-ecom-muted">Order Number</span>
            <span className="font-mono font-bold text-white">
              {currentOrder?.orderNumber || `ORD-#${id}`}
            </span>
          </div>

          <div className="flex items-center justify-between pb-3 border-b border-ecom-border text-xs">
            <span className="text-ecom-muted">System Order ID</span>
            <span className="font-mono font-bold text-indigo-400">#{id}</span>
          </div>

          <div className="flex items-center justify-between pb-3 border-b border-ecom-border text-xs">
            <span className="text-ecom-muted">Saga Execution State</span>
            <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full font-bold text-[10px] bg-amber-950/80 text-amber-300 border border-amber-700">
              <Clock className="w-3 h-3 animate-spin" />
              <span>{currentOrder?.status || 'PENDING'}</span>
            </span>
          </div>

          {currentOrder?.totalAmount && (
            <div className="flex items-center justify-between text-xs">
              <span className="text-ecom-muted">Total Amount</span>
              <span className="font-mono font-extrabold text-white text-sm">
                ${Number(currentOrder.totalAmount).toFixed(2)}
              </span>
            </div>
          )}
        </div>

        {/* Action Buttons */}
        <div className="space-y-3">
          <Link
            to={`/orders/${id}`}
            className="w-full py-3 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs flex items-center justify-center gap-2 shadow-lg shadow-indigo-600/30 transition text-decoration-none"
          >
            <Sparkles className="w-4 h-4" />
            <span>Track Live Order & View Kafka Event Timeline</span>
            <ArrowRight className="w-4 h-4" />
          </Link>

          <div className="flex items-center justify-center gap-4 pt-2">
            <Link
              to="/orders"
              className="text-xs font-semibold text-ecom-muted hover:text-white transition flex items-center gap-1 text-decoration-none"
            >
              <ListOrdered className="w-3.5 h-3.5" />
              <span>View All My Orders</span>
            </Link>
            <span className="text-slate-600">•</span>
            <Link
              to="/"
              className="text-xs font-semibold text-ecom-muted hover:text-white transition flex items-center gap-1 text-decoration-none"
            >
              <ShoppingBag className="w-3.5 h-3.5" />
              <span>Continue Shopping</span>
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}

