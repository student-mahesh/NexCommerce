import React, { useEffect, useState, useCallback } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import {
  Package,
  Plus,
  Search,
  Filter,
  RefreshCw,
  Edit2,
  Trash2,
  CheckCircle2,
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  Layers,
  Image as ImageIcon,
  DollarSign,
  Tag,
} from 'lucide-react';
import {
  fetchProducts,
  fetchCategories,
  createProduct,
  updateProduct,
  deleteProduct,
  clearAdminFeedback,
} from '../../features/products/productSlice';
import AdminProductModal from '../../components/admin/AdminProductModal';
import AdminDeleteModal from '../../components/admin/AdminDeleteModal';

export default function AdminProductsPage() {
  const dispatch = useDispatch();
  const {
    items: products,
    categories,
    loading,
    error,
    pagination,
    adminActionLoading,
    adminActionError,
    adminSuccessMessage,
  } = useSelector((state) => state.products);

  // Search & Filter state
  const [searchInput, setSearchInput] = useState('');
  const [activeSearch, setActiveSearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');
  const [currentPage, setCurrentPage] = useState(0);

  // Modal states
  const [isProductModalOpen, setIsProductModalOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState(null);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [deletingProduct, setDeletingProduct] = useState(null);

  // Image load fallback tracker
  const [brokenImages, setBrokenImages] = useState({});

  // Fetch categories once on mount
  useEffect(() => {
    dispatch(fetchCategories());
  }, [dispatch]);

  // Load products based on current query parameters
  const loadData = useCallback(() => {
    dispatch(
      fetchProducts({
        page: currentPage,
        size: 10,
        category: selectedCategory,
        search: activeSearch,
      })
    );
  }, [dispatch, currentPage, selectedCategory, activeSearch]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Clear admin success message after 4 seconds
  useEffect(() => {
    if (adminSuccessMessage) {
      const timer = setTimeout(() => {
        dispatch(clearAdminFeedback());
      }, 4000);
      return () => clearTimeout(timer);
    }
  }, [adminSuccessMessage, dispatch]);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setActiveSearch(searchInput.trim());
    setCurrentPage(0);
  };

  const handleCategoryChange = (e) => {
    setSelectedCategory(e.target.value);
    setCurrentPage(0);
  };

  const handleResetFilters = () => {
    setSearchInput('');
    setActiveSearch('');
    setSelectedCategory('');
    setCurrentPage(0);
  };

  // Open Create Modal
  const handleOpenCreateModal = () => {
    setEditingProduct(null);
    dispatch(clearAdminFeedback());
    setIsProductModalOpen(true);
  };

  // Open Edit Modal
  const handleOpenEditModal = (prod) => {
    setEditingProduct(prod);
    dispatch(clearAdminFeedback());
    setIsProductModalOpen(true);
  };

  // Open Delete Modal
  const handleOpenDeleteModal = (prod) => {
    setDeletingProduct(prod);
    dispatch(clearAdminFeedback());
    setIsDeleteModalOpen(true);
  };

  // Handle Create or Update submit
  const handleProductSubmit = async (formData) => {
    if (editingProduct) {
      const result = await dispatch(
        updateProduct({ id: editingProduct.id, productData: formData })
      );
      if (!result.error) {
        setIsProductModalOpen(false);
        setEditingProduct(null);
        loadData();
      }
    } else {
      const result = await dispatch(createProduct(formData));
      if (!result.error) {
        setIsProductModalOpen(false);
        loadData();
        dispatch(fetchCategories()); // Refresh category counts
      }
    }
  };

  // Handle Soft-Delete confirm
  const handleDeleteConfirm = async (id) => {
    const result = await dispatch(deleteProduct(id));
    if (!result.error) {
      setIsDeleteModalOpen(false);
      setDeletingProduct(null);
      loadData();
      dispatch(fetchCategories()); // Refresh category counts
    }
  };

  const handleImageError = (productId) => {
    setBrokenImages((prev) => ({ ...prev, [productId]: true }));
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2">
        <div>
          <h2 className="text-xl sm:text-2xl font-black text-white tracking-tight mb-1">
            Product Management
          </h2>
          <p className="text-xs sm:text-sm text-slate-300 mb-0">
            Authoritative catalog administration, price configuration, inventory allocation, and soft deactivation.
          </p>
        </div>

        <div className="flex items-center gap-2.5 flex-shrink-0">
          <button
            onClick={loadData}
            disabled={loading}
            className="p-2 rounded-xl text-slate-300 hover:text-white bg-[#172338] border border-[#29384F] hover:border-indigo-500 transition disabled:opacity-50"
            title="Refresh catalog list"
            aria-label="Refresh catalog"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-indigo-400' : ''}`} />
          </button>

          <button
            onClick={handleOpenCreateModal}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold transition shadow-md shadow-indigo-600/20"
          >
            <Plus className="w-4 h-4" />
            <span>Add Product</span>
          </button>
        </div>
      </div>

      {/* Success Notification Alert */}
      {adminSuccessMessage && (
        <div className="p-3.5 rounded-xl bg-emerald-950/60 border border-emerald-800 text-emerald-300 text-xs flex items-center justify-between shadow-sm animate-fade-in">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 flex-shrink-0" />
            <span>{adminSuccessMessage}</span>
          </div>
          <button
            onClick={() => dispatch(clearAdminFeedback())}
            className="text-emerald-400 hover:text-white text-xs font-bold"
          >
            &times;
          </button>
        </div>
      )}

      {/* Error Alert */}
      {error && (
        <div className="p-3.5 rounded-xl bg-rose-950/60 border border-rose-800 text-rose-300 text-xs flex items-center justify-between shadow-sm">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4 text-rose-400 flex-shrink-0" />
            <span>{error}</span>
          </div>
          <button
            onClick={loadData}
            className="px-2.5 py-1 rounded bg-rose-900/60 hover:bg-rose-800 text-[11px] font-semibold text-white transition"
          >
            Retry
          </button>
        </div>
      )}

      {/* Search and Filters Bar */}
      <div className="p-4 rounded-2xl bg-[#172338] border border-[#29384F] flex flex-col md:flex-row md:items-center justify-between gap-3 shadow-sm">
        {/* Search Input */}
        <form onSubmit={handleSearchSubmit} className="flex-1 max-w-md">
          <div className="relative">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
            <input
              type="text"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Search product name or SKU..."
              className="w-full text-xs py-2 pl-9 pr-3 rounded-xl bg-[#0B1425] border border-[#29384F] text-white focus:outline-none focus:border-indigo-500 transition placeholder:text-slate-500"
            />
          </div>
        </form>

        {/* Category Filter & Reset */}
        <div className="flex items-center gap-2.5 flex-wrap">
          <div className="relative">
            <select
              value={selectedCategory}
              onChange={handleCategoryChange}
              className="text-xs py-2 pl-3 pr-8 rounded-xl bg-[#0B1425] border border-[#29384F] text-white focus:outline-none focus:border-indigo-500 transition appearance-none cursor-pointer"
              aria-label="Filter products by category"
            >
              <option value="">All Categories</option>
              {categories.map((c) => (
                <option key={c.id || c.name} value={c.name}>
                  {c.name} ({c.productCount ?? 0})
                </option>
              ))}
            </select>
            <Filter className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-2.5 pointer-events-none" />
          </div>

          {(activeSearch || selectedCategory) && (
            <button
              onClick={handleResetFilters}
              className="text-xs px-3 py-2 rounded-xl bg-[#1C2A40] text-slate-300 hover:text-white border border-[#29384F] transition"
            >
              Clear Filters
            </button>
          )}
        </div>
      </div>

      {/* Catalog Table Card */}
      <div className="rounded-2xl bg-[#172338] border border-[#29384F] overflow-hidden shadow-sm">
        {loading && products.length === 0 ? (
          <div className="py-16 text-center text-slate-400">
            <RefreshCw className="w-8 h-8 animate-spin text-indigo-400 mx-auto mb-3" />
            <p className="text-xs font-semibold mb-0">Querying authoritative product catalog...</p>
          </div>
        ) : products.length === 0 ? (
          <div className="py-16 text-center text-slate-400 p-6">
            <Package className="w-12 h-12 text-slate-600 mx-auto mb-3" />
            <h4 className="text-sm font-bold text-white mb-1">No products found</h4>
            <p className="text-xs text-slate-400 max-w-sm mx-auto mb-4">
              {activeSearch || selectedCategory
                ? 'No active products match your current search or category filter.'
                : 'The product catalog is currently empty. Click below to add the first item.'}
            </p>
            {activeSearch || selectedCategory ? (
              <button
                onClick={handleResetFilters}
                className="px-4 py-2 rounded-xl bg-[#1C2A40] text-xs font-semibold text-white border border-[#29384F] hover:border-indigo-500 transition"
              >
                Reset Search Filters
              </button>
            ) : (
              <button
                onClick={handleOpenCreateModal}
                className="px-4 py-2 rounded-xl bg-indigo-600 text-xs font-bold text-white hover:bg-indigo-500 transition shadow"
              >
                + Add Product
              </button>
            )}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs text-slate-300 border-collapse">
              <thead className="bg-[#101A2D] text-slate-400 uppercase text-[10px] font-bold tracking-wider border-b border-[#29384F]">
                <tr>
                  <th scope="col" className="py-3.5 px-4">
                    Product
                  </th>
                  <th scope="col" className="py-3.5 px-3">
                    SKU
                  </th>
                  <th scope="col" className="py-3.5 px-3">
                    Category
                  </th>
                  <th scope="col" className="py-3.5 px-3 text-right">
                    Price
                  </th>
                  <th scope="col" className="py-3.5 px-3 text-center">
                    Stock
                  </th>
                  <th scope="col" className="py-3.5 px-3 text-center">
                    Status
                  </th>
                  <th scope="col" className="py-3.5 px-4 text-right">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#29384F]">
                {products.map((prod) => {
                  const hasImage = prod.imageUrl && !brokenImages[prod.id];
                  const stock = prod.stockQuantity ?? 0;

                  return (
                    <tr
                      key={prod.id}
                      className="hover:bg-[#1C2A40]/40 transition-colors"
                    >
                      {/* Product Thumbnail & Details */}
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-3">
                          <div className="w-10 h-10 rounded-lg bg-[#0B1425] border border-[#29384F] overflow-hidden flex items-center justify-center flex-shrink-0">
                            {hasImage ? (
                              <img
                                src={prod.imageUrl}
                                alt={prod.name}
                                className="w-full h-full object-cover"
                                onError={() => handleImageError(prod.id)}
                              />
                            ) : (
                              <ImageIcon className="w-4 h-4 text-slate-600" />
                            )}
                          </div>
                          <div className="min-w-0 max-w-xs">
                            <span className="font-bold text-white block truncate leading-tight">
                              {prod.name}
                            </span>
                            <span className="text-[10px] text-slate-500 font-mono">
                              ID: #{prod.id}
                            </span>
                          </div>
                        </div>
                      </td>

                      {/* SKU */}
                      <td className="py-3 px-3">
                        <span className="font-mono text-[11px] font-semibold text-slate-200 bg-[#0B1425] px-2 py-0.5 rounded border border-[#29384F]">
                          {prod.sku}
                        </span>
                      </td>

                      {/* Category */}
                      <td className="py-3 px-3">
                        <span className="inline-flex items-center gap-1 text-[11px] font-medium text-indigo-300 bg-indigo-950/60 border border-indigo-800/60 px-2 py-0.5 rounded-md">
                          <Tag className="w-3 h-3 text-indigo-400" />
                          <span>{prod.category}</span>
                        </span>
                      </td>

                      {/* Authoritative Price */}
                      <td className="py-3 px-3 text-right">
                        <span className="font-mono font-bold text-white text-xs">
                          ${Number(prod.price).toFixed(2)}
                        </span>
                      </td>

                      {/* Stock Quantity Badge */}
                      <td className="py-3 px-3 text-center">
                        <span
                          className={`font-mono text-[11px] font-bold px-2 py-0.5 rounded-full border ${
                            stock > 10
                              ? 'text-emerald-400 bg-emerald-950/50 border-emerald-800/80'
                              : stock > 0
                              ? 'text-amber-400 bg-amber-950/50 border-amber-800/80'
                              : 'text-rose-400 bg-rose-950/50 border-rose-800/80'
                          }`}
                        >
                          {stock} in stock
                        </span>
                      </td>

                      {/* Active Status */}
                      <td className="py-3 px-3 text-center">
                        {prod.active !== false ? (
                          <span className="inline-flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider text-emerald-400 bg-emerald-950/60 border border-emerald-800 px-2 py-0.5 rounded-full">
                            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                            Active
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider text-rose-400 bg-rose-950/60 border border-rose-800 px-2 py-0.5 rounded-full">
                            <span className="w-1.5 h-1.5 rounded-full bg-rose-400" />
                            Inactive
                          </span>
                        )}
                      </td>

                      {/* Actions */}
                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          <button
                            onClick={() => handleOpenEditModal(prod)}
                            className="p-1.5 rounded-lg text-slate-300 hover:text-white bg-[#0B1425] border border-[#29384F] hover:border-indigo-500 transition"
                            title="Edit Product"
                            aria-label={`Edit ${prod.name}`}
                          >
                            <Edit2 className="w-3.5 h-3.5" />
                          </button>

                          <button
                            onClick={() => handleOpenDeleteModal(prod)}
                            className="p-1.5 rounded-lg text-rose-400 hover:text-rose-200 bg-[#0B1425] border border-[#29384F] hover:border-rose-500 transition"
                            title="Deactivate Product"
                            aria-label={`Deactivate ${prod.name}`}
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination Bar */}
        {pagination && pagination.totalPages > 1 && (
          <div className="px-6 py-3.5 bg-[#101A2D] border-t border-[#29384F] flex items-center justify-between text-xs text-slate-400">
            <div>
              Showing page <span className="font-bold text-white">{pagination.pageNumber + 1}</span> of{' '}
              <span className="font-bold text-white">{pagination.totalPages}</span> ({pagination.totalElements} items total)
            </div>

            <div className="flex items-center gap-2">
              <button
                onClick={() => setCurrentPage((prev) => Math.max(0, prev - 1))}
                disabled={pagination.pageNumber === 0 || loading}
                className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-[#172338] border border-[#29384F] text-slate-300 hover:text-white disabled:opacity-40 transition"
              >
                <ChevronLeft className="w-3.5 h-3.5" />
                <span>Prev</span>
              </button>

              <button
                onClick={() => setCurrentPage((prev) => prev + 1)}
                disabled={pagination.last || loading}
                className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-[#172338] border border-[#29384F] text-slate-300 hover:text-white disabled:opacity-40 transition"
              >
                <span>Next</span>
                <ChevronRight className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Create / Edit Modal */}
      <AdminProductModal
        isOpen={isProductModalOpen}
        onClose={() => {
          setIsProductModalOpen(false);
          setEditingProduct(null);
        }}
        product={editingProduct}
        categories={categories}
        onSubmit={handleProductSubmit}
        isLoading={adminActionLoading}
        serverError={adminActionError}
      />

      {/* Delete Confirmation Modal */}
      <AdminDeleteModal
        isOpen={isDeleteModalOpen}
        onClose={() => {
          setIsDeleteModalOpen(false);
          setDeletingProduct(null);
        }}
        product={deletingProduct}
        onConfirm={handleDeleteConfirm}
        isLoading={adminActionLoading}
        error={adminActionError}
      />
    </div>
  );
}

