import React, { useEffect, useState, useMemo } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import {
  ShoppingBag,
  Search,
  Filter,
  RefreshCw,
  Eye,
  CheckCircle2,
  XCircle,
  Clock,
  AlertCircle,
  Radio,
  FileText,
  DollarSign,
  User,
  Layers,
} from 'lucide-react';
import {
  fetchAllOrdersAdmin,
  fetchPaymentByOrderId,
  fetchOrderNotifications,
  clearCurrentOrder,
} from '../../features/orders/orderSlice';
import AdminOrderDetailsModal from '../../components/admin/AdminOrderDetailsModal';

export default function AdminOrdersPage() {
  const dispatch = useDispatch();
  const {
    adminOrders,
    adminOrdersLoading,
    adminOrdersError,
    adminSelectedPayment,
    adminPaymentLoading,
    notifications,
  } = useSelector((state) => state.orders);

  // Search & Filter state (client-side filtering across the loaded platform orders)
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedStatus, setSelectedStatus] = useState('ALL');
  const [selectedMode, setSelectedMode] = useState('ALL');

  // Modal inspection state
  const [inspectingOrder, setInspectingOrder] = useState(null);
  const [isDetailsModalOpen, setIsDetailsModalOpen] = useState(false);
  const [notificationsLoading, setNotificationsLoading] = useState(false);

  // Load all platform orders on mount
  useEffect(() => {
    dispatch(fetchAllOrdersAdmin());
  }, [dispatch]);

  const handleRefresh = () => {
    dispatch(fetchAllOrdersAdmin());
  };

  // Inspect specific order: fetch associated payment and notification audit logs
  const handleInspectOrder = async (order) => {
    setInspectingOrder(order);
    setIsDetailsModalOpen(true);
    setNotificationsLoading(true);

    // Fetch live payment record and notification audit logs in parallel
    dispatch(fetchPaymentByOrderId(order.id));
    await dispatch(fetchOrderNotifications(order.id));
    setNotificationsLoading(false);
  };

  // Client-side filtering logic
  const filteredOrders = useMemo(() => {
    return adminOrders.filter((order) => {
      // 1. Status Filter
      if (selectedStatus !== 'ALL' && order.status !== selectedStatus) {
        return false;
      }

      // 2. Simulation Mode Filter
      if (selectedMode !== 'ALL' && order.paymentSimulateMode !== selectedMode) {
        return false;
      }

      // 3. Search Keyword Filter
      if (searchTerm.trim()) {
        const query = searchTerm.toLowerCase().trim();
        const matchesNumber = order.orderNumber?.toLowerCase().includes(query);
        const matchesEmail = order.customerEmail?.toLowerCase().includes(query);
        const matchesName = order.customerName?.toLowerCase().includes(query);
        const matchesId = String(order.id).includes(query);
        const matchesCustomerId = String(order.customerId).includes(query);
        const matchesSku = order.items?.some((item) =>
          item.productSku?.toLowerCase().includes(query)
        );

        if (
          !matchesNumber &&
          !matchesEmail &&
          !matchesName &&
          !matchesId &&
          !matchesCustomerId &&
          !matchesSku
        ) {
          return false;
        }
      }

      return true;
    });
  }, [adminOrders, selectedStatus, selectedMode, searchTerm]);

  // High-level platform statistics
  const stats = useMemo(() => {
    const total = adminOrders.length;
    const confirmed = adminOrders.filter((o) => o.status === 'CONFIRMED').length;
    const cancelled = adminOrders.filter((o) => o.status === 'CANCELLED').length;
    const pending = adminOrders.filter((o) => o.status === 'PENDING').length;
    const revenue = adminOrders
      .filter((o) => o.status === 'CONFIRMED')
      .reduce((sum, o) => sum + (Number(o.totalAmount) || 0), 0);

    return { total, confirmed, cancelled, pending, revenue };
  }, [adminOrders]);

  const handleResetFilters = () => {
    setSearchTerm('');
    setSelectedStatus('ALL');
    setSelectedMode('ALL');
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2">
        <div>
          <h2 className="text-xl sm:text-2xl font-black text-white tracking-tight mb-1">
            Customer Order Oversight
          </h2>
          <p className="text-xs sm:text-sm text-slate-300 mb-0">
            Real-time Kafka Saga choreography tracking, cross-customer order inspection, payment simulation auditing, and compensation logs.
          </p>
        </div>

        <div className="flex items-center gap-2.5 flex-shrink-0">
          <button
            onClick={handleRefresh}
            disabled={adminOrdersLoading}
            className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-slate-300 hover:text-white bg-[#172338] border border-[#29384F] hover:border-indigo-500 transition disabled:opacity-50 text-xs font-semibold"
            title="Refresh order list"
          >
            <RefreshCw
              className={`w-3.5 h-3.5 ${adminOrdersLoading ? 'animate-spin text-indigo-400' : ''}`}
            />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* Stats Summary Bar */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div className="p-4 rounded-2xl bg-[#172338] border border-[#29384F] shadow-sm">
          <div className="flex items-center justify-between mb-1">
            <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400">
              Total Orders
            </span>
            <ShoppingBag className="w-4 h-4 text-indigo-400" />
          </div>
          <div className="text-xl font-mono font-extrabold text-white">
            {stats.total}
          </div>
          <span className="text-[10px] text-slate-400">All customer orders</span>
        </div>

        <div className="p-4 rounded-2xl bg-[#172338] border border-[#29384F] shadow-sm">
          <div className="flex items-center justify-between mb-1">
            <span className="text-[11px] font-bold uppercase tracking-wider text-emerald-400">
              Confirmed
            </span>
            <CheckCircle2 className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="text-xl font-mono font-extrabold text-emerald-400">
            {stats.confirmed}
          </div>
          <span className="text-[10px] text-slate-400">Sagas successfully settled</span>
        </div>

        <div className="p-4 rounded-2xl bg-[#172338] border border-[#29384F] shadow-sm">
          <div className="flex items-center justify-between mb-1">
            <span className="text-[11px] font-bold uppercase tracking-wider text-rose-400">
              Cancelled
            </span>
            <XCircle className="w-4 h-4 text-rose-400" />
          </div>
          <div className="text-xl font-mono font-extrabold text-rose-400">
            {stats.cancelled}
          </div>
          <span className="text-[10px] text-slate-400">Compensated / stock released</span>
        </div>

        <div className="p-4 rounded-2xl bg-[#172338] border border-[#29384F] shadow-sm">
          <div className="flex items-center justify-between mb-1">
            <span className="text-[11px] font-bold uppercase tracking-wider text-blue-400">
              Confirmed Volume
            </span>
            <DollarSign className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="text-xl font-mono font-extrabold text-white">
            ${stats.revenue.toFixed(2)}
          </div>
          <span className="text-[10px] text-slate-400">Settled simulated value</span>
        </div>
      </div>

      {/* Error Alert */}
      {adminOrdersError && (
        <div className="p-3.5 rounded-xl bg-rose-950/60 border border-rose-800 text-rose-300 text-xs flex items-center justify-between shadow-sm">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4 text-rose-400 flex-shrink-0" />
            <span>{adminOrdersError}</span>
          </div>
          <button
            onClick={handleRefresh}
            className="px-2.5 py-1 rounded bg-rose-900/60 hover:bg-rose-800 text-[11px] font-semibold text-white transition"
          >
            Retry
          </button>
        </div>
      )}

      {/* Search and Filters Bar */}
      <div className="p-4 rounded-2xl bg-[#172338] border border-[#29384F] flex flex-col md:flex-row md:items-center justify-between gap-3 shadow-sm">
        {/* Search Input */}
        <div className="flex-1 max-w-md">
          <div className="relative">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Search order #, customer email, SKU..."
              className="w-full text-xs py-2 pl-9 pr-3 rounded-xl bg-[#0B1425] border border-[#29384F] text-white focus:outline-none focus:border-indigo-500 transition placeholder:text-slate-500"
            />
          </div>
        </div>

        {/* Filters */}
        <div className="flex items-center gap-2.5 flex-wrap">
          {/* Status Filter */}
          <div className="relative">
            <select
              value={selectedStatus}
              onChange={(e) => setSelectedStatus(e.target.value)}
              className="text-xs py-2 pl-3 pr-8 rounded-xl bg-[#0B1425] border border-[#29384F] text-white focus:outline-none focus:border-indigo-500 transition appearance-none cursor-pointer"
              aria-label="Filter orders by status"
            >
              <option value="ALL">All Statuses</option>
              <option value="CONFIRMED">CONFIRMED</option>
              <option value="CANCELLED">CANCELLED</option>
              <option value="PENDING">PENDING</option>
            </select>
            <Filter className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-2.5 pointer-events-none" />
          </div>

          {/* Simulate Mode Filter */}
          <div className="relative">
            <select
              value={selectedMode}
              onChange={(e) => setSelectedMode(e.target.value)}
              className="text-xs py-2 pl-3 pr-8 rounded-xl bg-[#0B1425] border border-[#29384F] text-white focus:outline-none focus:border-indigo-500 transition appearance-none cursor-pointer"
              aria-label="Filter orders by simulation mode"
            >
              <option value="ALL">All Modes</option>
              <option value="SUCCESS">SUCCESS</option>
              <option value="FAILURE">FAILURE</option>
              <option value="OUT_OF_STOCK">OUT_OF_STOCK</option>
            </select>
            <Radio className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-2.5 pointer-events-none" />
          </div>

          {(searchTerm || selectedStatus !== 'ALL' || selectedMode !== 'ALL') && (
            <button
              onClick={handleResetFilters}
              className="text-xs px-3 py-2 rounded-xl bg-[#1C2A40] text-slate-300 hover:text-white border border-[#29384F] transition"
            >
              Clear Filters
            </button>
          )}
        </div>
      </div>

      {/* Orders Table Card */}
      <div className="rounded-2xl bg-[#172338] border border-[#29384F] overflow-hidden shadow-sm">
        {adminOrdersLoading && adminOrders.length === 0 ? (
          <div className="py-16 text-center text-slate-400">
            <RefreshCw className="w-8 h-8 animate-spin text-indigo-400 mx-auto mb-3" />
            <p className="text-xs font-semibold mb-0">Querying platform order service (:8082)...</p>
          </div>
        ) : filteredOrders.length === 0 ? (
          <div className="py-16 text-center text-slate-400 p-6">
            <ShoppingBag className="w-12 h-12 text-slate-600 mx-auto mb-3" />
            <h4 className="text-sm font-bold text-white mb-1">No orders found</h4>
            <p className="text-xs text-slate-400 max-w-sm mx-auto mb-4">
              {searchTerm || selectedStatus !== 'ALL' || selectedMode !== 'ALL'
                ? 'No customer orders match your current filters.'
                : 'No customer orders have been recorded in the platform database yet.'}
            </p>
            {(searchTerm || selectedStatus !== 'ALL' || selectedMode !== 'ALL') && (
              <button
                onClick={handleResetFilters}
                className="px-4 py-2 rounded-xl bg-[#1C2A40] text-xs font-semibold text-white border border-[#29384F] hover:border-indigo-500 transition"
              >
                Reset Order Filters
              </button>
            )}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs text-slate-300 border-collapse">
              <thead className="bg-[#101A2D] text-slate-400 uppercase text-[10px] font-bold tracking-wider border-b border-[#29384F]">
                <tr>
                  <th scope="col" className="py-3.5 px-4">
                    Order Number
                  </th>
                  <th scope="col" className="py-3.5 px-3">
                    Customer
                  </th>
                  <th scope="col" className="py-3.5 px-3">
                    Date & Time
                  </th>
                  <th scope="col" className="py-3.5 px-3 text-center">
                    Items
                  </th>
                  <th scope="col" className="py-3.5 px-3 text-right">
                    Total
                  </th>
                  <th scope="col" className="py-3.5 px-3 text-center">
                    Status
                  </th>
                  <th scope="col" className="py-3.5 px-3">
                    Cancellation Reason / Mode
                  </th>
                  <th scope="col" className="py-3.5 px-4 text-right">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#29384F]">
                {filteredOrders.map((order) => {
                  const isConfirmed = order.status === 'CONFIRMED';
                  const isCancelled = order.status === 'CANCELLED';

                  return (
                    <tr
                      key={order.id}
                      className="hover:bg-[#1C2A40]/40 transition-colors"
                    >
                      {/* Order Number */}
                      <td className="py-3.5 px-4 font-mono font-bold text-white">
                        <button
                          onClick={() => handleInspectOrder(order)}
                          className="hover:text-indigo-400 transition text-left underline decoration-slate-600 hover:decoration-indigo-400"
                        >
                          {order.orderNumber}
                        </button>
                      </td>

                      {/* Customer */}
                      <td className="py-3.5 px-3">
                        <div className="font-semibold text-white truncate max-w-[150px]">
                          {order.customerName || `Customer #${order.customerId}`}
                        </div>
                        <div className="text-[10px] text-slate-400 truncate max-w-[150px] font-mono">
                          {order.customerEmail}
                        </div>
                      </td>

                      {/* Date & Time */}
                      <td className="py-3.5 px-3 text-slate-300 font-mono text-[11px]">
                        <div>{new Date(order.createdAt).toLocaleDateString()}</div>
                        <div className="text-slate-500 text-[10px]">
                          {new Date(order.createdAt).toLocaleTimeString()}
                        </div>
                      </td>

                      {/* Items Count */}
                      <td className="py-3.5 px-3 text-center font-mono">
                        <span className="px-2 py-0.5 rounded bg-[#0B1425] border border-[#29384F]">
                          {order.items?.length || 0}
                        </span>
                      </td>

                      {/* Total Amount */}
                      <td className="py-3.5 px-3 text-right font-mono font-bold text-white">
                        ${Number(order.totalAmount).toFixed(2)}
                      </td>

                      {/* Status */}
                      <td className="py-3.5 px-3 text-center">
                        <span
                          className={`inline-flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full border ${
                            isConfirmed
                              ? 'text-emerald-400 bg-emerald-950/60 border-emerald-800'
                              : isCancelled
                              ? 'text-rose-400 bg-rose-950/60 border-rose-800'
                              : 'text-blue-400 bg-blue-950/60 border-blue-800'
                          }`}
                        >
                          <span
                            className={`w-1.5 h-1.5 rounded-full ${
                              isConfirmed
                                ? 'bg-emerald-400'
                                : isCancelled
                                ? 'bg-rose-400'
                                : 'bg-blue-400 animate-pulse'
                            }`}
                          />
                          {order.status}
                        </span>
                      </td>

                      {/* Cancellation Reason / Mode */}
                      <td className="py-3.5 px-3 max-w-xs">
                        {order.cancellationReason ? (
                          <span
                            className="text-[11px] text-rose-300 block truncate"
                            title={order.cancellationReason}
                          >
                            {order.cancellationReason}
                          </span>
                        ) : (
                          <span className="text-[11px] font-mono text-slate-400">
                            Mode: {order.paymentSimulateMode || 'DEFAULT'}
                          </span>
                        )}
                      </td>

                      {/* Actions */}
                      <td className="py-3.5 px-4 text-right">
                        <button
                          onClick={() => handleInspectOrder(order)}
                          className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs font-semibold text-slate-300 hover:text-white bg-[#0B1425] border border-[#29384F] hover:border-indigo-500 transition"
                          title="Inspect Order Details & Saga Status"
                        >
                          <Eye className="w-3.5 h-3.5 text-indigo-400" />
                          <span>Inspect</span>
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Table Footer Count Indicator */}
        <div className="px-6 py-3 bg-[#101A2D] border-t border-[#29384F] flex items-center justify-between text-xs text-slate-400">
          <div>
            Showing <span className="font-bold text-white">{filteredOrders.length}</span> of{' '}
            <span className="font-bold text-white">{adminOrders.length}</span> total orders loaded from order-service (:8082)
          </div>
          <div className="text-[11px] text-slate-500">
            * Client-side search and status filtering applied across all recorded platform orders
          </div>
        </div>
      </div>

      {/* Order Details & Saga Inspection Modal */}
      <AdminOrderDetailsModal
        isOpen={isDetailsModalOpen}
        onClose={() => {
          setIsDetailsModalOpen(false);
          setInspectingOrder(null);
          dispatch(clearCurrentOrder());
        }}
        order={inspectingOrder}
        payment={adminSelectedPayment}
        paymentLoading={adminPaymentLoading}
        notifications={notifications}
        notificationsLoading={notificationsLoading}
      />
    </div>
  );
}

