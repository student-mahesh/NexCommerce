import React, { useEffect, useState, useMemo } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import {
  Boxes,
  Package,
  Search,
  Filter,
  RefreshCw,
  Plus,
  AlertTriangle,
  CheckCircle2,
  Lock,
  ArrowUpDown,
  Layers,
  Clock,
  ShieldCheck,
  Eye,
  Info,
} from 'lucide-react';
import {
  fetchInventory,
  replenishStock,
  clearInventoryFeedback,
} from '../../features/inventory/inventorySlice';
import { fetchProducts } from '../../features/products/productSlice';
import AdminReplenishModal from '../../components/admin/AdminReplenishModal';

export default function AdminInventoryPage() {
  const dispatch = useDispatch();

  const {
    items: inventoryItems,
    loading: inventoryLoading,
    error: inventoryError,
    replenishing,
    replenishError,
    replenishSuccess,
  } = useSelector((state) => state.inventory);

  const { items: catalogProducts } = useSelector((state) => state.products);

  // Search & Filter state
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

  // Replenish Modal state
  const [selectedItem, setSelectedItem] = useState(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  // Load inventory and product catalog on mount
  useEffect(() => {
    dispatch(fetchInventory());
    if (!catalogProducts || catalogProducts.length === 0) {
      dispatch(fetchProducts({ page: 0, size: 50 }));
    }
  }, [dispatch]);

  // Product map for quick lookup by productId
  const productMap = useMemo(() => {
    const map = new Map();
    if (catalogProducts && Array.isArray(catalogProducts)) {
      catalogProducts.forEach((p) => {
        map.set(Number(p.id), p);
      });
    }
    return map;
  }, [catalogProducts]);

  // Handle manual refresh
  const handleRefresh = () => {
    dispatch(fetchInventory());
    dispatch(fetchProducts({ page: 0, size: 50 }));
    dispatch(clearInventoryFeedback());
  };

  // Open modal
  const handleOpenReplenish = (item) => {
    setSelectedItem(item);
    setIsModalOpen(true);
    dispatch(clearInventoryFeedback());
  };

  // Close modal
  const handleCloseModal = () => {
    setIsModalOpen(false);
    setSelectedItem(null);
  };

  // Submit replenishment
  const handleConfirmReplenish = async (productId, quantity) => {
    const result = await dispatch(replenishStock({ productId, quantity }));
    if (!result.error) {
      handleCloseModal();
      // Re-fetch inventory to refresh list and summary calculations
      dispatch(fetchInventory());
    }
  };

  // Filter inventory items (client-side over complete returned list)
  const filteredItems = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();

    return inventoryItems.filter((item) => {
      const product = productMap.get(Number(item.productId));
      const productName = (product?.name || '').toLowerCase();
      const productCategory = (product?.category || '').toLowerCase();
      const sku = (item.sku || product?.sku || '').toLowerCase();

      // Search matching
      if (term) {
        const matchSku = sku.includes(term);
        const matchName = productName.includes(term);
        const matchCat = productCategory.includes(term);
        const matchId = String(item.productId).includes(term);
        if (!matchSku && !matchName && !matchCat && !matchId) {
          return false;
        }
      }

      // Status filter
      const available = item.availableQuantity ?? 0;
      const reserved = item.reservedQuantity ?? 0;

      if (statusFilter === 'IN_STOCK' && available <= 10) return false;
      if (statusFilter === 'LOW_STOCK' && (available < 1 || available > 10)) return false;
      if (statusFilter === 'OUT_OF_STOCK' && available !== 0) return false;
      if (statusFilter === 'ACTIVELY_RESERVED' && reserved <= 0) return false;

      return true;
    });
  }, [inventoryItems, productMap, searchTerm, statusFilter]);

  // Aggregate summary KPI calculations (calculated directly from actual API results)
  const summary = useMemo(() => {
    let totalAvailable = 0;
    let totalReserved = 0;
    let lowStockCount = 0;
    let outOfStockCount = 0;

    inventoryItems.forEach((item) => {
      const avail = item.availableQuantity ?? 0;
      const res = item.reservedQuantity ?? 0;
      totalAvailable += avail;
      totalReserved += res;

      if (avail === 0) {
        outOfStockCount++;
      } else if (avail <= 10) {
        lowStockCount++;
      }
    });

    return {
      totalSkus: inventoryItems.length,
      totalAvailable,
      totalReserved,
      lowStockCount,
      outOfStockCount,
    };
  }, [inventoryItems]);

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold text-blue-400 uppercase tracking-wider mb-1">
            <Boxes size={15} />
            <span>Warehouse & Operations</span>
          </div>
          <h1 className="text-2xl font-bold text-[#E8EEF7] tracking-tight">
            Inventory & Stock Oversight
          </h1>
          <p className="text-xs text-slate-400 mt-0.5">
            Real-time SKU availability, active Saga reservation tracking, and atomic stock replenishment.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleRefresh}
            disabled={inventoryLoading}
            className="px-3.5 py-2 text-xs font-medium rounded-xl bg-[#172338] hover:bg-slate-800 text-slate-300 hover:text-white border border-[#29384F] transition flex items-center gap-2 shadow-sm"
          >
            <RefreshCw size={14} className={inventoryLoading ? 'animate-spin text-blue-400' : ''} />
            <span>Refresh Stock</span>
          </button>
        </div>
      </div>

      {/* Global Alert Notification */}
      {replenishSuccess && (
        <div className="p-3.5 bg-emerald-500/10 border border-emerald-500/30 rounded-xl text-xs text-emerald-300 flex items-center justify-between animate-fadeIn">
          <div className="flex items-center gap-2">
            <CheckCircle2 size={16} className="text-emerald-400 shrink-0" />
            <span>{replenishSuccess}</span>
          </div>
          <button
            onClick={() => dispatch(clearInventoryFeedback())}
            className="text-emerald-400 hover:text-emerald-200 text-xs font-semibold ml-4"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Threshold Information Rule Banner */}
      <div className="p-3 bg-[#172338]/80 border border-[#29384F] rounded-xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 text-xs text-slate-300">
        <div className="flex items-center gap-2">
          <Info size={15} className="text-blue-400 shrink-0" />
          <span>
            <strong className="text-white">Classification Thresholds:</strong> In Stock (&gt;10 units) • Low Stock (1–10 units) • Out of Stock (0 units) • Actively Reserved (&gt;0 locked units)
          </span>
        </div>
        <span className="text-[11px] text-slate-400">
          Source: Authoritative MySQL <code className="font-mono text-blue-400">inventory_items</code>
        </span>
      </div>

      {/* Summary KPI Cards Grid */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-3.5">
        {/* Total SKUs */}
        <div className="p-4 bg-[#172338] border border-[#29384F] rounded-2xl shadow-sm">
          <div className="text-xs text-slate-400 font-medium">Tracked SKUs</div>
          <div className="text-2xl font-bold text-[#E8EEF7] mt-1">{summary.totalSkus}</div>
          <div className="text-[11px] text-slate-500 mt-1">Unique catalog entities</div>
        </div>

        {/* Total Available Units */}
        <div className="p-4 bg-[#172338] border border-[#29384F] rounded-2xl shadow-sm">
          <div className="text-xs text-slate-400 font-medium">Available Units</div>
          <div className="text-2xl font-bold text-emerald-400 mt-1">{summary.totalAvailable}</div>
          <div className="text-[11px] text-emerald-500/80 mt-1">Ready for sale</div>
        </div>

        {/* Total Reserved Units */}
        <div className="p-4 bg-[#172338] border border-[#29384F] rounded-2xl shadow-sm">
          <div className="text-xs text-slate-400 font-medium flex items-center gap-1">
            <Lock size={12} className="text-amber-400" />
            <span>Active Reservations</span>
          </div>
          <div className="text-2xl font-bold text-amber-400 mt-1">{summary.totalReserved}</div>
          <div className="text-[11px] text-amber-500/80 mt-1">Held in Saga workflows</div>
        </div>

        {/* Low Stock Alerts */}
        <div className="p-4 bg-[#172338] border border-[#29384F] rounded-2xl shadow-sm">
          <div className="text-xs text-slate-400 font-medium">Low Stock SKUs</div>
          <div className="text-2xl font-bold text-amber-400 mt-1">{summary.lowStockCount}</div>
          <div className="text-[11px] text-amber-500/80 mt-1">&le; 10 units threshold</div>
        </div>

        {/* Out of Stock Count */}
        <div className="p-4 bg-[#172338] border border-[#29384F] rounded-2xl shadow-sm">
          <div className="text-xs text-slate-400 font-medium">Out of Stock</div>
          <div className={`text-2xl font-bold mt-1 ${summary.outOfStockCount > 0 ? 'text-rose-400' : 'text-slate-400'}`}>
            {summary.outOfStockCount}
          </div>
          <div className="text-[11px] text-rose-500/80 mt-1">0 units available</div>
        </div>
      </div>

      {/* Search & Filter Toolbar */}
      <div className="p-4 bg-[#172338] border border-[#29384F] rounded-2xl space-y-3">
        <div className="flex flex-col md:flex-row items-stretch md:items-center gap-3">
          {/* Search Input */}
          <div className="relative flex-1">
            <Search size={16} className="absolute left-3.5 top-3 text-slate-400" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Search by SKU, product name, or product ID..."
              className="w-full pl-10 pr-4 py-2 bg-[#0B1425] border border-[#29384F] rounded-xl text-xs text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 font-sans"
            />
            {searchTerm && (
              <button
                onClick={() => setSearchTerm('')}
                className="absolute right-3 top-2.5 text-xs text-slate-400 hover:text-white"
              >
                Clear
              </button>
            )}
          </div>

          {/* Status Filter Buttons */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 md:pb-0">
            {[
              { id: 'ALL', label: 'All Items' },
              { id: 'IN_STOCK', label: 'In Stock (>10)' },
              { id: 'LOW_STOCK', label: 'Low Stock (1–10)' },
              { id: 'OUT_OF_STOCK', label: 'Out of Stock (0)' },
              { id: 'ACTIVELY_RESERVED', label: 'Reserved (>0)' },
            ].map((f) => (
              <button
                key={f.id}
                onClick={() => setStatusFilter(f.id)}
                className={`px-3 py-1.5 text-xs font-semibold rounded-xl whitespace-nowrap transition ${
                  statusFilter === f.id
                    ? 'bg-blue-600 text-white shadow-md shadow-blue-600/20'
                    : 'bg-[#0F172A] hover:bg-slate-800 text-slate-300 border border-[#29384F]'
                }`}
              >
                {f.label}
              </button>
            ))}
          </div>
        </div>

        {/* Results Counter & Context Note */}
        <div className="flex items-center justify-between text-xs text-slate-400 pt-1 border-t border-[#29384F]/50">
          <div>
            Showing <strong className="text-white">{filteredItems.length}</strong> of{' '}
            <strong className="text-white">{inventoryItems.length}</strong> total tracked SKUs
          </div>
          <div className="text-[11px] text-slate-400">
            Filters and metrics computed over active catalog items
          </div>
        </div>
      </div>

      {/* Main Inventory Table Panel */}
      <div className="bg-[#172338] border border-[#29384F] rounded-2xl overflow-hidden shadow-sm">
        {inventoryLoading ? (
          <div className="py-16 text-center text-slate-400 space-y-3">
            <RefreshCw size={28} className="animate-spin text-blue-400 mx-auto" />
            <p className="text-sm">Loading authoritative inventory state...</p>
          </div>
        ) : inventoryError ? (
          <div className="py-12 px-6 text-center space-y-3">
            <AlertTriangle size={32} className="text-rose-400 mx-auto" />
            <h3 className="text-base font-bold text-white">Failed to Load Inventory</h3>
            <p className="text-xs text-rose-300 max-w-md mx-auto">{inventoryError}</p>
            <button
              onClick={handleRefresh}
              className="mt-2 px-4 py-2 text-xs font-semibold rounded-xl bg-blue-600 hover:bg-blue-500 text-white transition"
            >
              Retry
            </button>
          </div>
        ) : filteredItems.length === 0 ? (
          <div className="py-16 text-center text-slate-400 space-y-2">
            <Package size={36} className="text-slate-600 mx-auto" />
            <h3 className="text-sm font-semibold text-slate-300">No matching inventory items</h3>
            <p className="text-xs text-slate-400 max-w-sm mx-auto">
              No SKU records match your search keyword or selected status filter.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-[#101A2D] text-slate-400 uppercase tracking-wider font-semibold border-b border-[#29384F]">
                <tr>
                  <th className="py-3.5 px-4">Product Details</th>
                  <th className="py-3.5 px-4">SKU Code</th>
                  <th className="py-3.5 px-4">Available Units</th>
                  <th className="py-3.5 px-4">Active Reserved</th>
                  <th className="py-3.5 px-4">Total Stock</th>
                  <th className="py-3.5 px-4">Stock Status</th>
                  <th className="py-3.5 px-4">Last Updated</th>
                  <th className="py-3.5 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#29384F]/60 text-slate-300">
                {filteredItems.map((item) => {
                  const product = productMap.get(Number(item.productId));
                  const displayName = product?.name || `Product #${item.productId}`;
                  const displayCategory = product?.category || 'General';
                  const isDeactivated = product && product.active === false;

                  const avail = item.availableQuantity ?? 0;
                  const res = item.reservedQuantity ?? 0;
                  const total = avail + res;

                  let statusBadge = {
                    text: 'In Stock',
                    classes: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30',
                  };
                  if (avail === 0) {
                    statusBadge = {
                      text: 'Out of Stock',
                      classes: 'bg-rose-500/10 text-rose-400 border-rose-500/30',
                    };
                  } else if (avail <= 10) {
                    statusBadge = {
                      text: 'Low Stock',
                      classes: 'bg-amber-500/10 text-amber-400 border-amber-500/30',
                    };
                  }

                  return (
                    <tr
                      key={item.id || item.productId}
                      className="hover:bg-slate-800/40 transition group"
                    >
                      {/* Product Thumbnail & Details */}
                      <td className="py-3.5 px-4">
                        <div className="flex items-center gap-3">
                          {product?.imageUrl ? (
                            <img
                              src={product.imageUrl}
                              alt={displayName}
                              className="w-10 h-10 rounded-lg object-cover bg-slate-800 border border-slate-700 shrink-0"
                            />
                          ) : (
                            <div className="w-10 h-10 rounded-lg bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-500 shrink-0">
                              <Package size={16} />
                            </div>
                          )}
                          <div className="min-w-0">
                            <div className="font-semibold text-white truncate max-w-xs group-hover:text-blue-300 transition">
                              {displayName}
                            </div>
                            <div className="flex items-center gap-2 mt-0.5">
                              <span className="text-[11px] text-slate-400">{displayCategory}</span>
                              <span className="text-[11px] text-slate-400 font-mono">
                                ID: {item.productId}
                              </span>
                              {isDeactivated && (
                                <span className="text-[10px] px-1.5 py-0.2 rounded bg-slate-700 text-slate-300">
                                  Archived
                                </span>
                              )}
                            </div>
                          </div>
                        </div>
                      </td>

                      {/* SKU */}
                      <td className="py-3.5 px-4">
                        <span className="font-mono text-xs px-2.5 py-1 rounded-lg bg-[#0F172A] text-blue-400 border border-[#29384F]">
                          {item.sku}
                        </span>
                      </td>

                      {/* Available Units */}
                      <td className="py-3.5 px-4">
                        <div className="flex items-center gap-1.5">
                          <span
                            className={`font-mono font-bold text-sm ${
                              avail === 0
                                ? 'text-rose-400'
                                : avail <= 10
                                ? 'text-amber-400'
                                : 'text-emerald-400'
                            }`}
                          >
                            {avail}
                          </span>
                          <span className="text-[11px] text-slate-400">units</span>
                        </div>
                      </td>

                      {/* Active Reserved */}
                      <td className="py-3.5 px-4">
                        {res > 0 ? (
                          <div className="flex items-center gap-1 text-amber-400 bg-amber-500/10 border border-amber-500/20 px-2 py-0.5 rounded-lg w-fit">
                            <Lock size={12} />
                            <span className="font-mono font-bold text-xs">{res}</span>
                            <span className="text-[10px] text-amber-300/80">held</span>
                          </div>
                        ) : (
                          <span className="text-slate-400 font-mono text-xs">0</span>
                        )}
                      </td>

                      {/* Total Physical Stock */}
                      <td className="py-3.5 px-4 font-mono text-xs text-slate-300">
                        {total}
                      </td>

                      {/* Status Badge */}
                      <td className="py-3.5 px-4">
                        <span
                          className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold border ${statusBadge.classes}`}
                        >
                          <span className="w-1.5 h-1.5 rounded-full bg-current"></span>
                          <span>{statusBadge.text}</span>
                        </span>
                      </td>

                      {/* Last Updated */}
                      <td className="py-3.5 px-4 text-[11px] text-slate-400 font-mono">
                        {item.updatedAt
                          ? new Date(item.updatedAt).toLocaleString()
                          : 'Initial seed'}
                      </td>

                      {/* Action */}
                      <td className="py-3.5 px-4 text-right">
                        <button
                          onClick={() => handleOpenReplenish(item)}
                          className="px-3 py-1.5 text-xs font-semibold rounded-xl bg-blue-600/10 hover:bg-blue-600 text-blue-400 hover:text-white border border-blue-500/30 transition flex items-center gap-1.5 ml-auto"
                        >
                          <Plus size={13} />
                          <span>Replenish</span>
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Replenish Modal */}
      {isModalOpen && selectedItem && (
        <AdminReplenishModal
          isOpen={isModalOpen}
          onClose={handleCloseModal}
          item={selectedItem}
          product={productMap.get(Number(selectedItem.productId))}
          onReplenish={handleConfirmReplenish}
          submitting={replenishing}
          error={replenishError}
        />
      )}
    </div>
  );
}

