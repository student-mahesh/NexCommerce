import React from 'react';
import {
  CheckCircle2,
  XCircle,
  Clock,
  Package,
  Layers,
  CreditCard,
  ShieldAlert,
  Check,
  AlertTriangle,
} from 'lucide-react';

export default function OrderStepper({ order, notifications = [] }) {
  if (!order) return null;

  const eventTypes = notifications.map((n) => n.eventType);
  const hasOrderCreated = eventTypes.includes('ORDER_CREATED') || Boolean(order.id);
  const hasInvReserved = eventTypes.includes('INVENTORY_RESERVED');
  const hasInvRejected = eventTypes.includes('INVENTORY_REJECTED');
  const hasPaySucceeded = eventTypes.includes('PAYMENT_SUCCEEDED');
  const hasPayFailed = eventTypes.includes('PAYMENT_FAILED');

  const isConfirmed = order.status === 'CONFIRMED';
  const isCancelled = order.status === 'CANCELLED';
  const isPending = order.status === 'PENDING';

  // Determine stage 2 status
  let stage2Status = 'upcoming';
  if (hasInvReserved) stage2Status = 'completed';
  else if (hasInvRejected) stage2Status = 'rejected';
  else if (isPending && hasOrderCreated) stage2Status = 'active';

  // Determine stage 3 status
  let stage3Status = 'upcoming';
  if (hasPaySucceeded) stage3Status = 'completed';
  else if (hasPayFailed) stage3Status = 'failed';
  else if (isPending && hasInvReserved) stage3Status = 'active';

  // Determine stage 4 status
  let stage4Status = 'upcoming';
  if (isConfirmed) stage4Status = 'completed';
  else if (isCancelled) stage4Status = 'cancelled';
  else if (isPending && hasPaySucceeded) stage4Status = 'active';

  const steps = [
    {
      id: 1,
      title: 'Order Placed',
      description: 'OrderCreatedEvent published to Kafka broker',
      status: 'completed',
      icon: Package,
    },
    {
      id: 2,
      title: hasInvRejected ? 'Inventory Rejected' : 'Inventory Reserved',
      description: hasInvRejected
        ? 'Stock unavailable in warehouse schema'
        : hasInvReserved
        ? 'Pessimistic write lock reserved items'
        : 'Awaiting stock evaluation in inventory-service',
      status: stage2Status,
      icon: hasInvRejected ? AlertTriangle : Layers,
    },
    {
      id: 3,
      title: hasPayFailed ? 'Payment Declined' : 'Payment Processed',
      description: hasPayFailed
        ? 'Card charge declined by mock gateway'
        : hasPaySucceeded
        ? 'Deterministic payment settled'
        : 'Awaiting transaction in payment-service',
      status: stage3Status,
      icon: hasPayFailed ? XCircle : CreditCard,
    },
    {
      id: 4,
      title: isCancelled ? 'Order Cancelled' : 'Order Confirmed',
      description: isCancelled
        ? (order.cancellationReason || 'Saga failed; compensation release finished')
        : isConfirmed
        ? 'Saga completed; order confirmed for dispatch'
        : 'Finalizing distributed saga status',
      status: stage4Status,
      icon: isCancelled ? ShieldAlert : CheckCircle2,
    },
  ];

  const getStepColor = (status) => {
    switch (status) {
      case 'completed':
        return {
          circle: 'bg-emerald-600 text-white border-emerald-500 shadow-emerald-500/30',
          badge: 'bg-emerald-950/60 text-emerald-400 border-emerald-800',
          line: 'bg-emerald-500',
        };
      case 'rejected':
      case 'failed':
      case 'cancelled':
        return {
          circle: 'bg-rose-600 text-white border-rose-500 shadow-rose-500/30',
          badge: 'bg-rose-950/60 text-rose-400 border-rose-800',
          line: 'bg-rose-500',
        };
      case 'active':
        return {
          circle: 'bg-indigo-600 text-white border-indigo-400 shadow-indigo-500/40 ring-4 ring-indigo-500/20 animate-pulse',
          badge: 'bg-indigo-950/60 text-indigo-300 border-indigo-700',
          line: 'bg-slate-700',
        };
      default:
        return {
          circle: 'bg-ecom-panel text-slate-500 border-ecom-border',
          badge: 'bg-slate-900 text-slate-500 border-slate-800',
          line: 'bg-ecom-border',
        };
    }
  };

  return (
    <div className="p-6 rounded-2xl bg-ecom-card border border-ecom-border shadow-xl">
      <div className="flex items-center justify-between pb-4 mb-6 border-b border-ecom-border">
        <div>
          <h3 className="text-sm font-bold text-white mb-0">Live Distributed Saga Stepper</h3>
          <p className="text-xs text-ecom-muted mb-0">
            Real-time state machine transitions orchestrated via Apache Kafka choreography.
          </p>
        </div>
        <div>
          {isPending && (
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-amber-950/60 text-amber-300 border border-amber-800">
              <Clock className="w-3.5 h-3.5 animate-spin" />
              <span>SAGA IN PROGRESS</span>
            </span>
          )}
          {isConfirmed && (
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-emerald-950/60 text-emerald-300 border border-emerald-800">
              <CheckCircle2 className="w-3.5 h-3.5" />
              <span>CONFIRMED</span>
            </span>
          )}
          {isCancelled && (
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-rose-950/60 text-rose-300 border border-rose-800">
              <XCircle className="w-3.5 h-3.5" />
              <span>CANCELLED</span>
            </span>
          )}
        </div>
      </div>

      {/* Stepper Graphic */}
      <div className="relative">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 relative z-10">
          {steps.map((step, idx) => {
            const colors = getStepColor(step.status);
            const Icon = step.icon;
            const isLast = idx === steps.length - 1;

            return (
              <div key={step.id} className="flex flex-col items-start relative">
                {/* Header Icon + Number */}
                <div className="flex items-center gap-3 mb-3 w-full">
                  <div
                    className={`w-10 h-10 rounded-xl border flex items-center justify-center font-bold text-sm shadow-md transition-all ${colors.circle}`}
                  >
                    <Icon className="w-5 h-5" />
                  </div>
                  <div className="flex-1">
                    <span className="text-[10px] font-bold text-ecom-muted block uppercase tracking-wider">
                      Step {step.id}
                    </span>
                    <span className="text-xs font-bold text-white block truncate">
                      {step.title}
                    </span>
                  </div>
                </div>

                {/* Description */}
                <p className="text-[11px] text-ecom-muted leading-relaxed mb-2">
                  {step.description}
                </p>

                {/* Status Badge */}
                <span
                  className={`inline-block px-2 py-0.5 rounded text-[9px] font-bold uppercase tracking-wider border ${colors.badge}`}
                >
                  {step.status}
                </span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

