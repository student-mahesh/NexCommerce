import React, { useState, useEffect } from 'react';
import {
  X,
  Package,
  DollarSign,
  Tag,
  Layers,
  Image as ImageIcon,
  AlertCircle,
  Loader2,
  Check,
} from 'lucide-react';

export default function AdminProductModal({
  isOpen,
  onClose,
  product = null,
  categories = [],
  onSubmit,
  isLoading = false,
  serverError = null,
}) {
  const isEditMode = Boolean(product && product.id);

  const [formData, setFormData] = useState({
    name: '',
    description: '',
    price: '',
    sku: '',
    category: '',
    stockQuantity: '',
    imageUrl: '',
  });

  const [validationErrors, setValidationErrors] = useState({});
  const [imageError, setImageError] = useState(false);

  // Populate form data when modal opens or product changes
  useEffect(() => {
    if (product) {
      setFormData({
        name: product.name || '',
        description: product.description || '',
        price: product.price != null ? String(product.price) : '',
        sku: product.sku || '',
        category: product.category || (categories[0]?.name || ''),
        stockQuantity: product.stockQuantity != null ? String(product.stockQuantity) : '',
        imageUrl: product.imageUrl || '',
      });
    } else {
      setFormData({
        name: '',
        description: '',
        price: '',
        sku: '',
        category: categories[0]?.name || '',
        stockQuantity: '0',
        imageUrl: '',
      });
    }
    setValidationErrors({});
    setImageError(false);
  }, [product, categories, isOpen]);

  if (!isOpen) return null;

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: name === 'sku' ? value.toUpperCase() : value,
    }));

    if (name === 'imageUrl') {
      setImageError(false);
    }

    if (validationErrors[name]) {
      setValidationErrors((prev) => ({ ...prev, [name]: null }));
    }
  };

  const validate = () => {
    const errors = {};

    if (!formData.name.trim()) {
      errors.name = 'Product name is required';
    }

    if (!formData.description.trim()) {
      errors.description = 'Product description is required';
    }

    const numPrice = parseFloat(formData.price);
    if (isNaN(numPrice) || numPrice <= 0) {
      errors.price = 'Price must be greater than $0.00';
    }

    if (!formData.sku.trim()) {
      errors.sku = 'SKU identifier is required';
    } else if (!/^[A-Za-z0-9-_]+$/.test(formData.sku.trim())) {
      errors.sku = 'SKU can only contain alphanumeric characters, hyphens, and underscores';
    }

    if (!formData.category.trim()) {
      errors.category = 'Category is required';
    }

    const numStock = parseInt(formData.stockQuantity, 10);
    if (isNaN(numStock) || numStock < 0) {
      errors.stockQuantity = 'Stock quantity cannot be negative';
    }

    if (formData.imageUrl.trim()) {
      const url = formData.imageUrl.trim();
      if (!url.startsWith('http://') && !url.startsWith('https://')) {
        errors.imageUrl = 'Image URL must use http:// or https:// protocol';
      }
    }

    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) return;

    const payload = {
      name: formData.name.trim(),
      description: formData.description.trim(),
      price: parseFloat(formData.price),
      sku: formData.sku.trim().toUpperCase(),
      category: formData.category.trim(),
      stockQuantity: parseInt(formData.stockQuantity, 10),
      imageUrl: formData.imageUrl.trim() || null,
    };

    await onSubmit(payload);
  };

  const isValidUrl =
    formData.imageUrl.trim().startsWith('http://') ||
    formData.imageUrl.trim().startsWith('https://');

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/70 backdrop-blur-sm transition-opacity"
        onClick={isLoading ? undefined : onClose}
        aria-hidden="true"
      />

      {/* Modal Dialog */}
      <div className="relative w-full max-w-2xl bg-[#172338] border border-[#29384F] rounded-2xl shadow-2xl overflow-hidden z-10 text-[#E8EEF7] my-8">
        {/* Modal Header */}
        <div className="px-6 py-4 bg-[#101A2D] border-b border-[#29384F] flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-indigo-600/30 border border-indigo-400/40 flex items-center justify-center text-indigo-300">
              <Package className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-white mb-0">
                {isEditMode ? `Edit Product #${product.id}` : 'Create New Product'}
              </h3>
              <p className="text-[11px] text-slate-400 mb-0">
                Authoritative Catalog Management &bull; Product Service (:8081)
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            disabled={isLoading}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-[#1C2A40] transition disabled:opacity-50"
            aria-label="Close dialog"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Server Error Alert */}
        {serverError && (
          <div className="mx-6 mt-4 p-3 rounded-xl bg-rose-950/60 border border-rose-800 text-rose-300 text-xs flex items-start gap-2">
            <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
            <span>{serverError}</span>
          </div>
        )}

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Product Name */}
            <div className="md:col-span-2">
              <label className="block text-xs font-semibold text-slate-300 mb-1">
                Product Name <span className="text-rose-400">*</span>
              </label>
              <input
                type="text"
                name="name"
                value={formData.name}
                onChange={handleChange}
                placeholder="e.g. Apex Pro Mechanical Keyboard RGB"
                className={`w-full text-xs px-3 py-2 rounded-xl bg-[#0B1425] border text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500 transition ${
                  validationErrors.name ? 'border-rose-500' : 'border-[#29384F]'
                }`}
                disabled={isLoading}
              />
              {validationErrors.name && (
                <p className="text-[11px] text-rose-400 mt-1 mb-0">{validationErrors.name}</p>
              )}
            </div>

            {/* SKU */}
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">
                SKU (Stock Keeping Unit) <span className="text-rose-400">*</span>
              </label>
              <input
                type="text"
                name="sku"
                value={formData.sku}
                onChange={handleChange}
                placeholder="e.g. TECH-KB-01"
                className={`w-full text-xs font-mono px-3 py-2 rounded-xl bg-[#0B1425] border text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500 transition ${
                  validationErrors.sku ? 'border-rose-500' : 'border-[#29384F]'
                }`}
                disabled={isLoading}
              />
              {validationErrors.sku && (
                <p className="text-[11px] text-rose-400 mt-1 mb-0">{validationErrors.sku}</p>
              )}
            </div>

            {/* Category */}
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">
                Category <span className="text-rose-400">*</span>
              </label>
              <input
                type="text"
                name="category"
                list="category-suggestions"
                value={formData.category}
                onChange={handleChange}
                placeholder="e.g. Electronics, Audio, Wearables"
                className={`w-full text-xs px-3 py-2 rounded-xl bg-[#0B1425] border text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500 transition ${
                  validationErrors.category ? 'border-rose-500' : 'border-[#29384F]'
                }`}
                disabled={isLoading}
              />
              <datalist id="category-suggestions">
                {categories.map((c) => (
                  <option key={c.id || c.name} value={c.name} />
                ))}
              </datalist>
              {validationErrors.category && (
                <p className="text-[11px] text-rose-400 mt-1 mb-0">{validationErrors.category}</p>
              )}
            </div>

            {/* Authoritative Price */}
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">
                Authoritative Catalog Price ($) <span className="text-rose-400">*</span>
              </label>
              <div className="relative">
                <DollarSign className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  name="price"
                  value={formData.price}
                  onChange={handleChange}
                  placeholder="0.00"
                  className={`w-full text-xs pl-8 pr-3 py-2 rounded-xl bg-[#0B1425] border text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500 transition font-mono ${
                    validationErrors.price ? 'border-rose-500' : 'border-[#29384F]'
                  }`}
                  disabled={isLoading}
                />
              </div>
              {validationErrors.price && (
                <p className="text-[11px] text-rose-400 mt-1 mb-0">{validationErrors.price}</p>
              )}
            </div>

            {/* Stock Quantity */}
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">
                Initial Stock Quantity <span className="text-rose-400">*</span>
              </label>
              <div className="relative">
                <Layers className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
                <input
                  type="number"
                  step="1"
                  min="0"
                  name="stockQuantity"
                  value={formData.stockQuantity}
                  onChange={handleChange}
                  placeholder="0"
                  className={`w-full text-xs pl-8 pr-3 py-2 rounded-xl bg-[#0B1425] border text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500 transition font-mono ${
                    validationErrors.stockQuantity ? 'border-rose-500' : 'border-[#29384F]'
                  }`}
                  disabled={isLoading}
                />
              </div>
              {validationErrors.stockQuantity && (
                <p className="text-[11px] text-rose-400 mt-1 mb-0">
                  {validationErrors.stockQuantity}
                </p>
              )}
            </div>

            {/* Image URL & Live Preview */}
            <div className="md:col-span-2">
              <label className="block text-xs font-semibold text-slate-300 mb-1">
                Image URL (HTTP/HTTPS)
              </label>
              <div className="relative mb-2">
                <ImageIcon className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
                <input
                  type="url"
                  name="imageUrl"
                  value={formData.imageUrl}
                  onChange={handleChange}
                  placeholder="https://images.unsplash.com/..."
                  className={`w-full text-xs pl-8 pr-3 py-2 rounded-xl bg-[#0B1425] border text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500 transition ${
                    validationErrors.imageUrl ? 'border-rose-500' : 'border-[#29384F]'
                  }`}
                  disabled={isLoading}
                />
              </div>
              {validationErrors.imageUrl && (
                <p className="text-[11px] text-rose-400 mt-0 mb-2">{validationErrors.imageUrl}</p>
              )}

              {/* Live Preview Box */}
              <div className="p-3 rounded-xl bg-[#0B1425] border border-[#29384F] flex items-center gap-3">
                <div className="w-14 h-14 rounded-lg bg-[#1C2A40] border border-[#29384F] overflow-hidden flex items-center justify-center flex-shrink-0">
                  {isValidUrl && !imageError ? (
                    <img
                      src={formData.imageUrl.trim()}
                      alt="Preview"
                      className="w-full h-full object-cover"
                      onError={() => setImageError(true)}
                    />
                  ) : (
                    <ImageIcon className="w-6 h-6 text-slate-500" />
                  )}
                </div>
                <div className="text-xs">
                  <span className="font-semibold text-white block">
                    {isValidUrl
                      ? imageError
                        ? 'Image load failed (Fallback shown)'
                        : 'Live Thumbnail Preview'
                      : 'No image URL provided'}
                  </span>
                  <span className="text-[11px] text-slate-400">
                    Product image will be displayed on the customer storefront and cart summary.
                  </span>
                </div>
              </div>
            </div>

            {/* Description */}
            <div className="md:col-span-2">
              <label className="block text-xs font-semibold text-slate-300 mb-1">
                Description <span className="text-rose-400">*</span>
              </label>
              <textarea
                name="description"
                rows={3}
                value={formData.description}
                onChange={handleChange}
                placeholder="Enter detailed product specifications, highlights and materials..."
                className={`w-full text-xs px-3 py-2 rounded-xl bg-[#0B1425] border text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500 transition ${
                  validationErrors.description ? 'border-rose-500' : 'border-[#29384F]'
                }`}
                disabled={isLoading}
              />
              {validationErrors.description && (
                <p className="text-[11px] text-rose-400 mt-1 mb-0">
                  {validationErrors.description}
                </p>
              )}
            </div>
          </div>

          {/* Form Actions */}
          <div className="pt-4 border-t border-[#29384F] flex items-center justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              disabled={isLoading}
              className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-300 hover:text-white bg-[#1C2A40] border border-[#29384F] transition disabled:opacity-50"
            >
              Cancel
            </button>

            <button
              type="submit"
              disabled={isLoading}
              className="flex items-center gap-1.5 px-5 py-2 rounded-xl text-xs font-bold text-white bg-indigo-600 hover:bg-indigo-500 transition shadow-md shadow-indigo-600/20 disabled:opacity-50"
            >
              {isLoading ? (
                <>
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  <span>Saving...</span>
                </>
              ) : (
                <>
                  <Check className="w-3.5 h-3.5" />
                  <span>{isEditMode ? 'Update Product' : 'Create Product'}</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

