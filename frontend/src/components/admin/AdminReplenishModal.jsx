import React, { useState, useEffect } from 'react';
import {
  X,
  Package,
  Plus,
  ArrowRight,
  ShieldAlert,
  CheckCircle2,
  AlertTriangle,
  Loader2,
  Lock,
} from 'lucide-react';

export default function AdminReplenishModal({
  isOpen,
  onClose,
  item,
  product = null,
  onReplenish,
  submitting = false,
  error = null,
}) {
  const [quantity, setQuantity] = useState('10');
  const [validationError, setValidationError] = useState('');

  useEffect(() => {
    if (isOpen) {
      setQuantity('10');
      setValidationError('');
    }
  }, [isOpen, item]);

  if (!isOpen || !item) return null;

  const currentAvailable = item.availableQuantity ?? 0;
  const currentReserved = item.reservedQuantity ?? 0;

  // Validation
  const validateQuantity = (val) => {
    const num = parseInt(val, 10);
    if (!val || isNaN(num)) {
      return 'Please enter a valid replenishment quantity';
    }
    if (num < 1) {
      return 'Replenishment quantity must be at least 1 unit';
    }
    if (num > 10000) {
      return 'Replenishment quantity cannot exceed 10,000 units per request';
    }
    return '';
  };

  const handleQuantityChange = (e) => {
    const val = e.target.value;
    setQuantity(val);
    setValidationError(validateQuantity(val));
  };

  const parsedQty = parseInt(quantity, 10);
  const isValidQty = !isNaN(parsedQty) && parsedQty >= 1 && parsedQty <= 10000;
  // Authoritative projected available stock calculation (available only, reserved excluded)
  const projectedAvailable = isValidQty ? currentAvailable + parsedQty : currentAvailable;

  const handleSubmit = (e) => {
    e.preventDefault();
    const err = validateQuantity(quantity);
    if (err) {
      setValidationError(err);
      return;
    }
    onReplenish(item.productId, parsedQty);
  };

  const displayName = product?.name || `Product #${item.productId}`;
  const displaySku = item.sku || product?.sku || 'N/A';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fadeIn">
      <div
        className="w-full max-w-md bg-[#172338] border border-[#29384F] rounded-2xl shadow-2xl overflow-hidden flex flex-col animate-scaleUp"
        role="dialog"
        aria-modal="true"
        aria-labelledby="replenish-modal-title"
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 bg-[#101A2D] border-b border-[#29384F]">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-xl bg-blue-500/10 text-blue-400 border border-blue-500/20">
              <Package size={20} />
            </div>
            <div>
              <h3 id="replenish-modal-title" className="text-lg font-bold text-[#E8EEF7]">
                Replenish Stock
              </h3>
              <p className="text-xs text-slate-400">Atomic inventory restock</p>
            </div>
          </div>
          <button
            onClick={onClose}
            disabled={submitting}
            className="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition"
            aria-label="Close modal"
          >
            <X size={18} />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-5">
          {/* Target Product Summary Card */}
          <div className="p-3.5 bg-[#0F172A] border border-[#29384F] rounded-xl flex items-center gap-3">
            {product?.imageUrl ? (
              <img
                src={product.imageUrl}
                alt={displayName}
                className="w-12 h-12 rounded-lg object-cover bg-slate-800 border border-slate-700 shrink-0"
              />
            ) : (
              <div className="w-12 h-12 rounded-lg bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-500 shrink-0">
                <Package size={20} />
              </div>
            )}
            <div className="min-w-0 flex-1">
              <div className="font-semibold text-sm text-[#E8EEF7] truncate">{displayName}</div>
              <div className="flex items-center gap-2 mt-1">
                <span className="font-mono text-xs px-2 py-0.5 rounded bg-blue-500/10 text-blue-400 border border-blue-500/20">
                  {displaySku}
                </span>
                <span className="text-xs text-slate-400">ID: {item.productId}</span>
              </div>
            </div>
          </div>

          {/* Current Stock Metrics */}
          <div className="grid grid-cols-2 gap-3">
            <div className="p-3 bg-[#0F172A]/80 border border-[#29384F] rounded-xl">
              <div className="text-xs text-slate-400">Current Available</div>
              <div className="text-xl font-bold text-emerald-400 mt-0.5">
                {currentAvailable} <span className="text-xs text-slate-400 font-normal">units</span>
              </div>
            </div>
            <div className="p-3 bg-[#0F172A]/80 border border-[#29384F] rounded-xl">
              <div className="text-xs text-slate-400 flex items-center gap-1">
                <Lock size={12} className="text-amber-400" />
                <span>Active Reserved</span>
              </div>
              <div className="text-xl font-bold text-amber-400 mt-0.5">
                {currentReserved} <span className="text-xs text-slate-400 font-normal">units</span>
              </div>
            </div>
          </div>

          {/* Input Quantity to Add */}
          <div>
            <label htmlFor="replenish-quantity-input" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
              Units to Add (1 – 10,000)
            </label>
            <div className="relative">
              <input
                id="replenish-quantity-input"
                type="number"
                min="1"
                max="10000"
                step="1"
                value={quantity}
                onChange={handleQuantityChange}
                disabled={submitting}
                className={`w-full px-4 py-2.5 bg-[#0B1425] border rounded-xl text-white placeholder-slate-500 focus:outline-none focus:ring-2 font-mono text-base ${
                  validationError
                    ? 'border-rose-500 focus:ring-rose-500/20'
                    : 'border-[#29384F] focus:ring-blue-500/20 focus:border-blue-500'
                }`}
                placeholder="Enter unit count"
              />
              <div className="absolute right-3 top-2.5 text-xs text-slate-400 pointer-events-none">
                units
              </div>
            </div>
            {validationError && (
              <p className="mt-1.5 text-xs text-rose-400 flex items-center gap-1">
                <AlertTriangle size={13} />
                <span>{validationError}</span>
              </p>
            )}
          </div>

          {/* Quick Increment Pill Buttons */}
          <div className="flex items-center gap-2">
            <span className="text-xs text-slate-400">Quick add:</span>
            {[10, 25, 50, 100].map((amt) => (
              <button
                key={amt}
                type="button"
                onClick={() => {
                  setQuantity(String(amt));
                  setValidationError('');
                }}
                disabled={submitting}
                className="px-2.5 py-1 text-xs rounded-lg bg-[#0F172A] hover:bg-slate-800 border border-[#29384F] text-slate-300 transition"
              >
                +{amt}
              </button>
            ))}
          </div>

          {/* Projected Stock Preview Card */}
          <div className="p-3.5 bg-blue-950/20 border border-blue-500/30 rounded-xl">
            <div className="text-xs text-blue-300 font-semibold mb-1 flex items-center gap-1.5">
              <ArrowRight size={14} />
              <span>Projected Available Stock</span>
            </div>
            <div className="flex items-center justify-between mt-2">
              <div className="text-slate-400 text-xs">
                <span>{currentAvailable}</span>
                <span className="mx-1 text-blue-400">+</span>
                <span className="font-semibold text-white">{isValidQty ? parsedQty : 0}</span>
              </div>
              <div className="text-lg font-bold text-blue-400">
                = {projectedAvailable} <span className="text-xs font-normal text-slate-400">available</span>
              </div>
            </div>
            <p className="mt-2 text-[11px] text-slate-400 leading-relaxed border-t border-blue-500/20 pt-2">
              Note: Active reservations ({currentReserved} units) are strictly preserved and unaffected.
            </p>
          </div>

          {/* Backend Error Banner */}
          {error && (
            <div className="p-3 bg-rose-500/10 border border-rose-500/30 rounded-xl text-xs text-rose-300 flex items-start gap-2">
              <AlertTriangle size={16} className="shrink-0 mt-0.5 text-rose-400" />
              <span>{error}</span>
            </div>
          )}

          {/* Action Buttons */}
          <div className="flex items-center justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="px-4 py-2 text-xs font-semibold text-slate-300 hover:text-white rounded-xl hover:bg-slate-800 transition border border-transparent"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting || !isValidQty}
              className="px-5 py-2 text-xs font-semibold rounded-xl bg-blue-600 hover:bg-blue-500 text-white transition disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2 shadow-lg shadow-blue-600/20"
            >
              {submitting ? (
                <>
                  <Loader2 size={14} className="animate-spin" />
                  <span>Restocking...</span>
                </>
              ) : (
                <>
                  <Plus size={14} />
                  <span>Confirm Replenishment</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

