import React from 'react';
import { Link } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import { ShoppingCart, Eye, CheckCircle2, AlertTriangle, XCircle } from 'lucide-react';
import { addToCart } from '../cart/cartSlice';
import { getProductImage, handleImageError } from '../../services/imageHelper';

export default function ProductCard({ product }) {
  const dispatch = useDispatch();

  const handleAddToCart = (e) => {
    e.preventDefault();
    e.stopPropagation();
    dispatch(addToCart(product));
  };

  const renderStockBadge = () => {
    const qty = product.stockQuantity || 0;
    if (qty > 10) {
      return (
        <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-400 bg-emerald-950/60 border border-emerald-800 px-2.5 py-0.5 rounded-full">
          <CheckCircle2 className="w-3 h-3 text-emerald-400" /> In Stock ({qty})
        </span>
      );
    }
    if (qty > 0) {
      return (
        <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-amber-400 bg-amber-950/60 border border-amber-800 px-2.5 py-0.5 rounded-full">
          <AlertTriangle className="w-3 h-3 text-amber-400" /> Only {qty} left
        </span>
      );
    }
    return (
      <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-rose-400 bg-rose-950/60 border border-rose-800 px-2.5 py-0.5 rounded-full">
        <XCircle className="w-3 h-3 text-rose-400" /> Out of stock
      </span>
    );
  };

  return (
    <div className="col">
      <div className="card h-100 border border-ecom-border shadow-lg rounded-2xl overflow-hidden product-card bg-ecom-card flex flex-col text-ecom-text">
        {/* Product Image */}
        <Link to={`/products/${product.id}`} className="relative block aspect-[4/3] overflow-hidden bg-ecom-surface group border-b border-ecom-border">
          <img
            src={getProductImage(product)}
            alt={product.name}
            onError={handleImageError}
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
            loading="lazy"
          />
          <div className="absolute top-3 left-3">
            <span className="text-[10px] font-bold uppercase tracking-wider text-white bg-slate-950/80 backdrop-blur-md px-2 py-1 rounded-md shadow-sm border border-slate-700">
              {product.category}
            </span>
          </div>
        </Link>

        {/* Product Info */}
        <div className="card-body p-4 flex flex-col flex-grow justify-between">
          <div>
            <div className="flex items-center justify-between mb-2">
              {renderStockBadge()}
              <span className="text-[11px] font-mono text-ecom-muted">{product.sku}</span>
            </div>

            <Link to={`/products/${product.id}`} className="text-decoration-none">
              <h3 className="text-sm font-bold text-white hover:text-indigo-400 transition line-clamp-1 mb-1">
                {product.name}
              </h3>
            </Link>

            <p className="text-xs text-ecom-muted line-clamp-2 mb-3">
              {product.description}
            </p>
          </div>

          <div className="pt-3 border-t border-ecom-border flex items-center justify-between">
            <div>
              <span className="text-[10px] text-ecom-muted block font-medium uppercase tracking-wider">Price</span>
              <span className="text-base font-extrabold text-white font-mono">
                ${Number(product.price).toFixed(2)}
              </span>
            </div>

            <div className="flex gap-2">
              <Link
                to={`/products/${product.id}`}
                className="p-2 rounded-xl bg-ecom-panel border border-ecom-border hover:border-indigo-500 text-ecom-muted hover:text-white transition"
                title="View Details"
              >
                <Eye className="w-4 h-4" />
              </Link>
              <button
                onClick={handleAddToCart}
                disabled={product.stockQuantity <= 0}
                className="btn btn-sm btn-primary rounded-xl px-3 flex items-center gap-1.5 shadow-md shadow-indigo-600/20 hover:shadow-lg transition disabled:opacity-40"
                style={{ backgroundColor: '#4f46e5', borderColor: '#4f46e5' }}
              >
                <ShoppingCart className="w-4 h-4" />
                <span className="text-xs font-semibold">Add</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
