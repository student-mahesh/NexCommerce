import React from 'react';
import { AlertTriangle, X, Trash2, Loader2, ShieldAlert } from 'lucide-react';

export default function AdminDeleteModal({
  isOpen,
  onClose,
  product,
  onConfirm,
  isLoading = false,
  error = null,
}) {
  if (!isOpen || !product) return null;

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/70 backdrop-blur-sm transition-opacity"
        onClick={isLoading ? undefined : onClose}
        aria-hidden="true"
      />

      {/* Modal Dialog */}
      <div className="relative w-full max-w-md bg-[#172338] border border-[#29384F] rounded-2xl shadow-2xl p-6 text-[#E8EEF7] z-10">
        {/* Header Icon & Title */}
        <div className="flex items-start gap-3.5 mb-4">
          <div className="w-10 h-10 rounded-xl bg-rose-950/70 border border-rose-800/80 flex items-center justify-center text-rose-400 flex-shrink-0 shadow-inner">
            <Trash2 className="w-5 h-5" />
          </div>
          <div className="flex-1">
            <h3 className="text-base font-bold text-white mb-1">
              Deactivate Product?
            </h3>
            <p className="text-xs text-slate-400 mb-0">
              Soft-delete catalog operation &bull; ID: #{product.id}
            </p>
          </div>
          <button
            onClick={onClose}
            disabled={isLoading}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-[#1C2A40] transition disabled:opacity-50"
            aria-label="Close dialog"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Product Details Pill */}
        <div className="p-3 rounded-xl bg-[#0F172A] border border-[#29384F] mb-4 space-y-1">
          <div className="text-xs font-bold text-white truncate">
            {product.name}
          </div>
          <div className="flex items-center gap-2 text-[11px] text-slate-400 font-mono">
            <span>SKU: {product.sku}</span>
            <span>&bull;</span>
            <span>Category: {product.category}</span>
            <span>&bull;</span>
            <span className="text-emerald-400">${Number(product.price).toFixed(2)}</span>
          </div>
        </div>

        {/* Informational Warning */}
        <div className="p-3 rounded-xl bg-amber-950/40 border border-amber-800/60 text-amber-300 text-xs mb-5 space-y-1">
          <div className="font-semibold flex items-center gap-1.5">
            <AlertTriangle className="w-4 h-4 text-amber-400 flex-shrink-0" />
            <span>Soft-Deletion Notice</span>
          </div>
          <p className="text-[11px] text-amber-200/80 mb-0 leading-relaxed">
            This action sets <code className="text-amber-200">active = false</code> in the database. The product will be evicted from Redis and removed from customer browsing, but historical order records and inventory saga logs will remain intact.
          </p>
        </div>

        {error && (
          <div className="p-2.5 rounded-xl bg-rose-950/60 border border-rose-800 text-rose-300 text-xs mb-4">
            {error}
          </div>
        )}

        {/* Actions */}
        <div className="flex items-center justify-end gap-3 pt-3 border-t border-[#29384F]">
          <button
            type="button"
            onClick={onClose}
            disabled={isLoading}
            className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-300 hover:text-white bg-[#1C2A40] border border-[#29384F] transition disabled:opacity-50"
          >
            Cancel
          </button>

          <button
            type="button"
            onClick={() => onConfirm(product.id)}
            disabled={isLoading}
            className="flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-bold text-white bg-rose-600 hover:bg-rose-500 transition shadow-md shadow-rose-600/20 disabled:opacity-50"
          >
            {isLoading ? (
              <>
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
                <span>Deactivating...</span>
              </>
            ) : (
              <>
                <Trash2 className="w-3.5 h-3.5" />
                <span>Deactivate Product</span>
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

