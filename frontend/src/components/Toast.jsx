import React, { useEffect } from 'react';
import { CheckCircle2, AlertTriangle, XCircle, Info, X } from 'lucide-react';

export default function Toast({ message, type = 'info', onClose, duration = 4000 }) {
  useEffect(() => {
    if (!duration) return;
    const timer = setTimeout(() => {
      onClose();
    }, duration);
    return () => clearTimeout(timer);
  }, [duration, onClose]);

  const getIcon = () => {
    switch (type) {
      case 'success':
        return <CheckCircle2 className="w-4 h-4 text-emerald-400" />;
      case 'warning':
        return <AlertTriangle className="w-4 h-4 text-amber-400" />;
      case 'error':
        return <XCircle className="w-4 h-4 text-rose-400" />;
      default:
        return <Info className="w-4 h-4 text-indigo-400" />;
    }
  };

  const getBorder = () => {
    switch (type) {
      case 'success':
        return 'border-emerald-500/40 bg-emerald-950/90 text-emerald-100';
      case 'warning':
        return 'border-amber-500/40 bg-amber-950/90 text-amber-100';
      case 'error':
        return 'border-rose-500/40 bg-rose-950/90 text-rose-100';
      default:
        return 'border-indigo-500/40 bg-indigo-950/90 text-indigo-100';
    }
  };

  return (
    <div className={`fixed bottom-5 right-5 z-50 flex items-center gap-3 px-4 py-3 rounded-2xl border shadow-2xl backdrop-blur-md text-xs font-semibold max-w-sm animate-bounce-short ${getBorder()}`}>
      <div className="flex-shrink-0">{getIcon()}</div>
      <div className="flex-1 leading-snug">{message}</div>
      <button
        onClick={onClose}
        className="p-1 rounded-lg text-white/60 hover:text-white hover:bg-white/10 transition"
      >
        <X className="w-3.5 h-3.5" />
      </button>
    </div>
  );
}

