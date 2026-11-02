import React from 'react';
import {
  X,
  Package,
  ShoppingBag,
  CreditCard,
  Layers,
  Clock,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  RotateCcw,
  ShieldCheck,
  User,
  MapPin,
  Calendar,
  DollarSign,
  Radio,
  FileText,
  Loader2,
} from 'lucide-react';

export default function AdminOrderDetailsModal({
  isOpen,
  onClose,
  order,
  payment = null,
  paymentLoading = false,
  notifications = [],
  notificationsLoading = false,
}) {
  if (!isOpen || !order) return null;

  const isConfirmed = order.status === 'CONFIRMED';
  const isCancelled = order.status === 'CANCELLED';
  const isPending = order.status === 'PENDING';

  // Gather verified event evidence from notification logs
  const getEventEvidence = (eventType) => {
    return notifications.find((n) => n.eventType === eventType);
  };

  const orderCreatedEvt = getEventEvidence('ORDER_CREATED');
  const invReservedEvt = getEventEvidence('INVENTORY_RESERVED');
  const invRejectedEvt = getEventEvidence('INVENTORY_REJECTED');
  const paySucceededEvt = getEventEvidence('PAYMENT_SUCCEEDED');
  const payFailedEvt = getEventEvidence('PAYMENT_FAILED');

  // Determine actual evidence-backed stage statuses
  // Stage 1: Order Created
  const stage1 = {
    title: 'Order Created',
    status: 'completed',
    timestamp: orderCreatedEvt?.createdAt || order.createdAt,
    evidence: orderCreatedEvt
      ? `Kafka event verified: ${orderCreatedEvt.eventId || 'order-created'}`
      : 'Order persisted in order-service database',
  };

  // Stage 2: Inventory Reservation
  let stage2 = {
    title: 'Inventory Evaluation',
    status: 'pending',
    timestamp: null,
    evidence: 'Awaiting stock evaluation in inventory-service',
  };
  if (invReservedEvt) {
    stage2 = {
      title: 'Inventory Reserved',
      status: 'completed',
      timestamp: invReservedEvt.createdAt,
      evidence: `Stock reserved with pessimistic write lock (Kafka: ${invReservedEvt.eventId || 'reserved'})`,
    };
  } else if (invRejectedEvt || (isCancelled && order.cancellationReason?.toLowerCase().includes('inventory'))) {
    stage2 = {
      title: 'Inventory Rejected',
      status: 'rejected',
      timestamp: invRejectedEvt?.createdAt || (isCancelled ? order.updatedAt : null),
      evidence: invRejectedEvt?.message || order.cancellationReason || 'Stock check failed: Out of stock',
    };
  } else if (isConfirmed) {
    stage2 = {
      title: 'Inventory Reserved',
      status: 'completed',
      timestamp: null,
      evidence: 'Derived from CONFIRMED order state (Pre-notification audit log)',
    };
  }

  // Stage 3: Payment Settlement
  let stage3 = {
    title: 'Payment Settlement',
    status: 'pending',
    timestamp: null,
    evidence: 'Awaiting transaction in payment-service',
  };
  if (paySucceededEvt || payment?.status === 'SUCCEEDED') {
    stage3 = {
      title: 'Payment Succeeded',
      status: 'completed',
      timestamp: paySucceededEvt?.createdAt || payment?.createdAt,
      evidence: payment?.transactionReference
        ? `Simulated transaction reference: ${payment.transactionReference}`
        : 'Payment settled successfully via mock engine',
    };
  } else if (payFailedEvt || payment?.status === 'FAILED' || (isCancelled && order.cancellationReason?.toLowerCase().includes('payment'))) {
    stage3 = {
      title: 'Payment Declined',
      status: 'failed',
      timestamp: payFailedEvt?.createdAt || payment?.createdAt || (isCancelled ? order.updatedAt : null),
      evidence: payment?.failureReason || payFailedEvt?.message || order.cancellationReason || 'Payment declined by simulated gateway',
    };
  } else if (stage2.status === 'rejected') {
    stage3 = {
      title: 'Payment Skipped',
      status: 'skipped',
      timestamp: null,
      evidence: 'Payment was not executed because inventory was rejected',
    };
  } else if (isConfirmed) {
    stage3 = {
      title: 'Payment Succeeded',
      status: 'completed',
      timestamp: null,
      evidence: 'Derived from CONFIRMED order state (Pre-notification audit log)',
    };
  }

  // Stage 4: Order Finalization
  let stage4 = {
    title: isConfirmed ? 'Order Confirmed' : isCancelled ? 'Order Cancelled' : 'Order Pending',
    status: isConfirmed ? 'completed' : isCancelled ? 'cancelled' : 'active',
    timestamp: isConfirmed || isCancelled ? order.updatedAt || order.createdAt : null,
    evidence: isCancelled
      ? order.cancellationReason || 'Order marked CANCELLED in state machine'
      : isConfirmed
      ? 'Order marked CONFIRMED in state machine'
      : 'Saga currently in-flight',
  };

  // Stage 5: Inventory Compensation (Only applicable if payment failed after inventory was reserved)
  const isCompensationApplicable =
    (invReservedEvt && (payFailedEvt || payment?.status === 'FAILED')) ||
    (isCancelled && order.cancellationReason?.toLowerCase().includes('payment'));

  const sagaStages = [stage1, stage2, stage3, stage4];

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/75 backdrop-blur-sm transition-opacity"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Modal Dialog */}
      <div className="relative w-full max-w-4xl bg-[#172338] border border-[#29384F] rounded-2xl shadow-2xl overflow-hidden z-10 text-[#E8EEF7] my-8 max-h-[90vh] flex flex-col">
        {/* Header */}
        <div className="px-6 py-4 bg-[#101A2D] border-b border-[#29384F] flex items-center justify-between flex-shrink-0">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-indigo-600/30 border border-indigo-400/40 flex items-center justify-center text-indigo-300">
              <ShoppingBag className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-extrabold text-white mb-0 font-mono">
                  {order.orderNumber}
                </h3>
                <span
                  className={`text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full border ${
                    isConfirmed
                      ? 'text-emerald-400 bg-emerald-950/60 border-emerald-800'
                      : isCancelled
                      ? 'text-rose-400 bg-rose-950/60 border-rose-800'
                      : 'text-blue-400 bg-blue-950/60 border-blue-800'
                  }`}
                >
                  {order.status}
                </span>
              </div>
              <p className="text-[11px] text-slate-400 mb-0">
                Order ID: #{order.id} &bull; Placed{' '}
                {new Date(order.createdAt).toLocaleString()}
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-[#1C2A40] transition"
            aria-label="Close details"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Scrollable Body */}
        <div className="p-6 overflow-y-auto space-y-6">
          {/* Customer & Delivery Metadata */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div className="p-3.5 rounded-xl bg-[#0F172A] border border-[#29384F]">
              <div className="flex items-center gap-2 text-slate-400 text-xs font-semibold mb-1">
                <User className="w-3.5 h-3.5 text-indigo-400" />
                <span>Customer Identity</span>
              </div>
              <div className="text-xs font-bold text-white truncate">
                {order.customerName || `Customer #${order.customerId}`}
              </div>
              <div className="text-[11px] text-slate-400 truncate font-mono">
                {order.customerEmail} (ID: {order.customerId})
              </div>
            </div>

            <div className="p-3.5 rounded-xl bg-[#0F172A] border border-[#29384F]">
              <div className="flex items-center gap-2 text-slate-400 text-xs font-semibold mb-1">
                <MapPin className="w-3.5 h-3.5 text-indigo-400" />
                <span>Delivery Address</span>
              </div>
              <div className="text-xs text-slate-200 line-clamp-2">
                {order.shippingAddress || 'No shipping address specified'}
              </div>
            </div>

            <div className="p-3.5 rounded-xl bg-[#0F172A] border border-[#29384F]">
              <div className="flex items-center gap-2 text-slate-400 text-xs font-semibold mb-1">
                <Radio className="w-3.5 h-3.5 text-indigo-400" />
                <span>Payment Simulation Mode</span>
              </div>
              <div className="text-xs font-mono font-bold text-indigo-300">
                {order.paymentSimulateMode || 'DEFAULT'}
              </div>
              <div className="text-[10px] text-slate-400">
                Deterministic mock gateway behavior
              </div>
            </div>
          </div>

          {/* Cancellation Reason Alert if Cancelled */}
          {isCancelled && order.cancellationReason && (
            <div className="p-4 rounded-xl bg-rose-950/40 border border-rose-800 text-rose-300 text-xs flex items-start gap-3">
              <AlertTriangle className="w-5 h-5 text-rose-400 flex-shrink-0 mt-0.5" />
              <div>
                <span className="font-bold text-white block mb-0.5">
                  Saga Failure & Cancellation Reason:
                </span>
                <span>{order.cancellationReason}</span>
              </div>
            </div>
          )}

          {/* Section: Order Items Table */}
          <div>
            <h4 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-2.5">
              Purchased Order Items ({order.items?.length || 0})
            </h4>

            <div className="rounded-xl bg-[#0F172A] border border-[#29384F] overflow-hidden">
              <table className="w-full text-left text-xs text-slate-300 border-collapse">
                <thead className="bg-[#101A2D] text-slate-400 uppercase text-[10px] font-bold tracking-wider border-b border-[#29384F]">
                  <tr>
                    <th scope="col" className="py-2.5 px-3">
                      Item / SKU
                    </th>
                    <th scope="col" className="py-2.5 px-3 text-right">
                      Unit Price
                    </th>
                    <th scope="col" className="py-2.5 px-3 text-center">
                      Qty
                    </th>
                    <th scope="col" className="py-2.5 px-3 text-right">
                      Subtotal
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#29384F]">
                  {order.items?.map((item, idx) => (
                    <tr key={item.id || idx} className="hover:bg-[#1C2A40]/30 transition">
                      <td className="py-2.5 px-3">
                        <div className="font-semibold text-white">
                          {item.productName || `Product #${item.productId}`}
                        </div>
                        <div className="text-[10px] text-slate-400 font-mono">
                          SKU: {item.productSku || 'N/A'} &bull; Product ID: #{item.productId}
                        </div>
                      </td>
                      <td className="py-2.5 px-3 text-right font-mono">
                        ${Number(item.unitPrice).toFixed(2)}
                      </td>
                      <td className="py-2.5 px-3 text-center font-mono">
                        {item.quantity}
                      </td>
                      <td className="py-2.5 px-3 text-right font-mono font-semibold text-white">
                        ${Number(item.subtotal).toFixed(2)}
                      </td>
                    </tr>
                  ))}
                </tbody>
                <tfoot className="bg-[#101A2D] border-t border-[#29384F] font-bold">
                  <tr>
                    <td colSpan="3" className="py-3 px-3 text-right text-slate-400">
                      Authoritative Order Total:
                    </td>
                    <td className="py-3 px-3 text-right font-mono text-sm text-emerald-400">
                      ${Number(order.totalAmount).toFixed(2)}
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>

          {/* Section: Saga Choreography Visualizer & Evidence */}
          <div>
            <div className="flex items-center justify-between mb-2.5">
              <h4 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-0">
                Saga Choreography Lifecycle
              </h4>
              <span className="text-[10px] text-slate-400">
                Evidence verified via Kafka notification audit logs
              </span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
              {sagaStages.map((stage, i) => {
                const isComplete = stage.status === 'completed';
                const isRejected = stage.status === 'rejected';
                const isFailed = stage.status === 'failed';
                const isCancelledStage = stage.status === 'cancelled';
                const isSkipped = stage.status === 'skipped';

                return (
                  <div
                    key={i}
                    className={`p-3.5 rounded-xl border flex flex-col justify-between ${
                      isComplete
                        ? 'bg-emerald-950/20 border-emerald-800/80 text-emerald-300'
                        : isRejected || isFailed || isCancelledStage
                        ? 'bg-rose-950/20 border-rose-800/80 text-rose-300'
                        : isSkipped
                        ? 'bg-slate-900 border-slate-700 text-slate-400'
                        : 'bg-[#0F172A] border-[#29384F] text-blue-300'
                    }`}
                  >
                    <div>
                      <div className="flex items-center justify-between mb-2">
                        <span className="text-[10px] font-mono font-bold text-slate-400">
                          STAGE {i + 1}
                        </span>
                        {isComplete && <CheckCircle2 className="w-4 h-4 text-emerald-400" />}
                        {(isRejected || isFailed || isCancelledStage) && (
                          <XCircle className="w-4 h-4 text-rose-400" />
                        )}
                        {isSkipped && <Clock className="w-4 h-4 text-slate-500" />}
                      </div>

                      <div className="font-bold text-xs text-white mb-1">
                        {stage.title}
                      </div>

                      <p className="text-[11px] text-slate-300/80 mb-2 leading-relaxed">
                        {stage.evidence}
                      </p>
                    </div>

                    <div className="pt-2 border-t border-slate-700/50 text-[10px] font-mono text-slate-400">
                      {stage.timestamp
                        ? new Date(stage.timestamp).toLocaleTimeString()
                        : 'No timestamp recorded'}
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Compensation Indicator */}
            {isCompensationApplicable && (
              <div className="mt-3 p-3 rounded-xl bg-amber-950/30 border border-amber-800 text-amber-300 text-xs flex items-center gap-2">
                <RotateCcw className="w-4 h-4 text-amber-400 flex-shrink-0" />
                <span>
                  <strong>Inventory Compensation Triggered:</strong> Payment failure initiated stock restitution in <code>inventory-service</code>. Reserved stock was released.
                </span>
              </div>
            )}
          </div>

          {/* Section: Payment Service Simulator Details */}
          <div>
            <h4 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-2.5">
              Payment Service Transaction Details
            </h4>

            {paymentLoading ? (
              <div className="p-4 rounded-xl bg-[#0F172A] border border-[#29384F] text-center text-xs text-slate-400">
                <Loader2 className="w-4 h-4 animate-spin text-indigo-400 inline mr-2" />
                Querying payment transaction in payment-service (:8084)...
              </div>
            ) : payment ? (
              <div className="p-4 rounded-xl bg-[#0F172A] border border-[#29384F] space-y-3">
                <div className="flex items-center justify-between pb-2 border-b border-[#29384F]">
                  <div className="flex items-center gap-2">
                    <CreditCard className="w-4 h-4 text-indigo-400" />
                    <span className="text-xs font-bold text-white">
                      Transaction #{payment.id} &bull; {payment.transactionReference}
                    </span>
                  </div>
                  <span
                    className={`text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full border ${
                      payment.status === 'SUCCEEDED'
                        ? 'text-emerald-400 bg-emerald-950/60 border-emerald-800'
                        : 'text-rose-400 bg-rose-950/60 border-rose-800'
                    }`}
                  >
                    {payment.status}
                  </span>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs">
                  <div>
                    <span className="text-[10px] text-slate-400 block">Method</span>
                    <span className="font-mono text-white">{payment.paymentMethod}</span>
                  </div>
                  <div>
                    <span className="text-[10px] text-slate-400 block">Amount</span>
                    <span className="font-mono text-white font-bold">
                      ${Number(payment.amount).toFixed(2)}
                    </span>
                  </div>
                  <div>
                    <span className="text-[10px] text-slate-400 block">Simulate Mode</span>
                    <span className="font-mono text-indigo-300 font-bold">
                      {payment.paymentSimulateMode}
                    </span>
                  </div>
                  <div>
                    <span className="text-[10px] text-slate-400 block">Settled At</span>
                    <span className="font-mono text-slate-300">
                      {new Date(payment.createdAt).toLocaleTimeString()}
                    </span>
                  </div>
                </div>

                {payment.failureReason && (
                  <div className="p-2 rounded bg-rose-950/50 border border-rose-800 text-[11px] text-rose-300">
                    <strong>Failure Reason:</strong> {payment.failureReason}
                  </div>
                )}

                <div className="text-[10px] text-amber-400/80 bg-amber-950/20 p-2 rounded border border-amber-900/40">
                  {payment.demoNotice || 'DEMO/SIMULATED PAYMENT ONLY'} &bull; This microservice operates in simulation mode. No real cardholder data is collected or charged.
                </div>
              </div>
            ) : (
              <div className="p-3.5 rounded-xl bg-[#0F172A] border border-[#29384F] text-xs text-slate-400">
                No payment transaction found in <code>payment-service</code>. Payment processing was bypassed or not initiated for this order.
              </div>
            )}
          </div>

          {/* Section: Notification Service Audit Trail */}
          <div>
            <div className="flex items-center justify-between mb-2.5">
              <h4 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-0">
                Notification Service Audit Trail ({notifications.length})
              </h4>
              <span className="text-[10px] text-slate-400">
                Consumer records in notification-service (:8085)
              </span>
            </div>

            {notificationsLoading ? (
              <div className="p-4 rounded-xl bg-[#0F172A] border border-[#29384F] text-center text-xs text-slate-400">
                <Loader2 className="w-4 h-4 animate-spin text-indigo-400 inline mr-2" />
                Querying audit events in notification-service (:8085)...
              </div>
            ) : notifications.length === 0 ? (
              <div className="p-3.5 rounded-xl bg-[#0F172A] border border-[#29384F] text-xs text-slate-400">
                No notification audit records logged for this order.
              </div>
            ) : (
              <div className="rounded-xl bg-[#0F172A] border border-[#29384F] overflow-hidden">
                <table className="w-full text-left text-xs text-slate-300 border-collapse">
                  <thead className="bg-[#101A2D] text-slate-400 uppercase text-[10px] font-bold tracking-wider border-b border-[#29384F]">
                    <tr>
                      <th scope="col" className="py-2.5 px-3">
                        Event Type
                      </th>
                      <th scope="col" className="py-2.5 px-3">
                        Message / Subject
                      </th>
                      <th scope="col" className="py-2.5 px-3 text-center">
                        Status
                      </th>
                      <th scope="col" className="py-2.5 px-3 text-right">
                        Recorded At
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#29384F]">
                    {notifications.map((n) => (
                      <tr key={n.id} className="hover:bg-[#1C2A40]/30 transition">
                        <td className="py-2.5 px-3">
                          <span className="font-mono text-[10px] font-bold px-1.5 py-0.5 rounded bg-[#172338] text-indigo-300 border border-[#29384F]">
                            {n.eventType}
                          </span>
                        </td>
                        <td className="py-2.5 px-3">
                          <div className="font-medium text-white">{n.subject}</div>
                          <div className="text-[11px] text-slate-400">{n.message}</div>
                        </td>
                        <td className="py-2.5 px-3 text-center">
                          <span className="text-[10px] font-mono text-emerald-400 bg-emerald-950/50 px-2 py-0.5 rounded border border-emerald-800">
                            {n.status || 'RECORDED'}
                          </span>
                        </td>
                        <td className="py-2.5 px-3 text-right font-mono text-slate-400 text-[11px]">
                          {new Date(n.createdAt).toLocaleTimeString()}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <div className="mt-2 text-[10px] text-slate-500">
              * Audit trail reflects Kafka events ingested into <code>ecommerce_notification</code>. Events are logged internally and not dispatched via real external SMTP or SMS gateways.
            </div>
          </div>
        </div>

        {/* Modal Footer */}
        <div className="px-6 py-3.5 bg-[#101A2D] border-t border-[#29384F] flex items-center justify-between flex-shrink-0">
          <div className="text-[11px] text-slate-400">
            Platform Order Oversight &bull; M15
          </div>
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-300 hover:text-white bg-[#1C2A40] border border-[#29384F] transition"
          >
            Close Details
          </button>
        </div>
      </div>
    </div>
  );
}

