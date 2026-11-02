import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { fetchProductById, clearSelectedProduct } from '../features/products/productSlice';
import { addToCart } from '../features/cart/cartSlice';
import { getProductImage, handleImageError } from '../services/imageHelper';
import {
  ArrowLeft,
  ShoppingCart,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Truck,
  ShieldCheck,
  RefreshCw,
  Layers,
} from 'lucide-react';

export default function ProductDetailsPage() {
  const { id } = useParams();
  const dispatch = useDispatch();
  const { selectedProduct: product, detailsLoading, error } = useSelector((state) => state.products);
  const [quantity, setQuantity] = useState(1);

  useEffect(() => {
    dispatch(fetchProductById(id));
    return () => {
      dispatch(clearSelectedProduct());
    };
  }, [dispatch, id]);

  const handleAddToCart = () => {
    if (product) {
      dispatch(addToCart({ ...product, quantity }));
    }
  };

  if (detailsLoading) {
    return (
      <div className="container py-24 text-center">
        <RefreshCw className="w-8 h-8 text-indigo-400 animate-spin mx-auto mb-3" />
        <p className="text-xs text-ecom-muted font-medium">
          Loading product specifications from Redis cache...
        </p>
      </div>
    );
  }

  if (error || !product) {
    return (
      <div className="container py-16 text-center max-w-md mx-auto">
        <div className="p-8 rounded-3xl bg-ecom-card border border-ecom-border shadow-xl">
          <h2 className="text-xl font-bold text-white mb-2">Product Not Found</h2>
          <p className="text-xs text-ecom-muted mb-4">{error || 'This item could not be retrieved.'}</p>
          <Link to="/" className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-indigo-600 text-white font-bold text-xs">
            <ArrowLeft className="w-4 h-4" />
            <span>Return to Storefront</span>
          </Link>
        </div>
      </div>
    );
  }

  const renderStockBadge = () => {
    const qty = product.stockQuantity || 0;
    if (qty > 10) {
      return (
        <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-400 bg-emerald-950/60 border border-emerald-800 px-3 py-1 rounded-full">
          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" /> In Stock ({qty} available)
        </span>
      );
    }
    if (qty > 0) {
      return (
        <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-amber-400 bg-amber-950/60 border border-amber-800 px-3 py-1 rounded-full">
          <AlertTriangle className="w-3.5 h-3.5 text-amber-400" /> Only {qty} units left!
        </span>
      );
    }
    return (
      <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-rose-400 bg-rose-950/60 border border-rose-800 px-3 py-1 rounded-full">
        <XCircle className="w-3.5 h-3.5 text-rose-400" /> Out of stock
      </span>
    );
  };

  return (
    <div className="container py-8">
      {/* Back Button */}
      <Link
        to="/"
        className="inline-flex items-center gap-2 text-xs font-semibold text-ecom-muted hover:text-white mb-6 text-decoration-none transition"
      >
        <ArrowLeft className="w-4 h-4" /> Back to Storefront
      </Link>

      <div className="row g-5">
        {/* Product Image */}
        <div className="col-12 col-md-6">
          <div className="rounded-3xl overflow-hidden border border-ecom-border bg-ecom-card shadow-xl aspect-[4/3]">
            <img
              src={getProductImage(product)}
              alt={product.name}
              onError={handleImageError}
              className="w-full h-full object-cover"
            />
          </div>
        </div>

        {/* Product Details & Actions */}
        <div className="col-12 col-md-6 flex flex-col justify-between">
          <div>
            <div className="flex items-center gap-3 mb-3">
              <span className="text-xs font-bold uppercase tracking-wider text-indigo-300 bg-indigo-950/60 border border-indigo-700 px-2.5 py-1 rounded-md">
                {product.category}
              </span>
              <span className="text-xs font-mono text-ecom-muted">SKU: {product.sku}</span>
            </div>

            <h1 className="text-2xl md:text-3xl font-extrabold text-white mb-3">
              {product.name}
            </h1>

            <div className="mb-4">
              <span className="text-3xl font-black text-white font-mono">
                ${Number(product.price).toFixed(2)}
              </span>
            </div>

            <div className="mb-6">{renderStockBadge()}</div>

            <div className="border-t border-b border-ecom-border py-4 my-4">
              <h4 className="text-xs font-bold uppercase tracking-wider text-ecom-muted mb-2">
                Description
              </h4>
              <p className="text-xs text-ecom-text leading-relaxed">{product.description}</p>
            </div>
          </div>

          <div>
            {/* Quantity and Add to Cart */}
            <div className="flex items-center gap-4 mb-6">
              <div className="flex items-center border border-ecom-border rounded-xl overflow-hidden bg-ecom-input">
                <button
                  onClick={() => setQuantity(Math.max(1, quantity - 1))}
                  className="px-3 py-2 text-ecom-muted hover:text-white hover:bg-ecom-panel transition font-bold"
                  aria-label="Decrease quantity"
                >
                  -
                </button>
                <span className="px-4 py-2 text-xs font-bold font-mono text-white">
                  {quantity}
                </span>
                <button
                  onClick={() => setQuantity(Math.min(product.stockQuantity || 99, quantity + 1))}
                  disabled={quantity >= product.stockQuantity}
                  className="px-3 py-2 text-ecom-muted hover:text-white hover:bg-ecom-panel transition font-bold disabled:opacity-30"
                  aria-label="Increase quantity"
                >
                  +
                </button>
              </div>

              <button
                onClick={handleAddToCart}
                disabled={product.stockQuantity <= 0}
                className="btn btn-primary rounded-xl px-5 py-2.5 d-flex align-items-center gap-2 shadow-lg shadow-indigo-600/30 hover:shadow-xl transition disabled:opacity-40"
                style={{ backgroundColor: '#4f46e5', borderColor: '#4f46e5' }}
              >
                <ShoppingCart className="w-4 h-4" />
                <span className="text-xs font-bold">Add to Cart</span>
              </button>
            </div>

            {/* Microservice Architecture Callouts */}
            <div className="row g-3">
              <div className="col-6">
                <div className="p-3 rounded-2xl bg-ecom-card border border-ecom-border flex items-center gap-2.5">
                  <Truck className="w-5 h-5 text-indigo-400 flex-shrink-0" />
                  <div>
                    <span className="text-xs font-bold text-white block">Fast Shipping</span>
                    <span className="text-[10px] text-ecom-muted">Dispatches in 24 hrs</span>
                  </div>
                </div>
              </div>
              <div className="col-6">
                <div className="p-3 rounded-2xl bg-ecom-card border border-ecom-border flex items-center gap-2.5">
                  <ShieldCheck className="w-5 h-5 text-emerald-400 flex-shrink-0" />
                  <div>
                    <span className="text-xs font-bold text-white block">Saga Choreography</span>
                    <span className="text-[10px] text-ecom-muted">Stock reservation safe</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
