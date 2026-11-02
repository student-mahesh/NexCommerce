import React, { useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { fetchProducts, fetchCategories, setSelectedCategory, setSearchQuery } from './productSlice';
import ProductCard from './ProductCard';
import { Filter, RefreshCw, AlertCircle, Sparkles } from 'lucide-react';

export default function ProductList() {
  const dispatch = useDispatch();
  const { items, categories, selectedCategory, searchQuery, loading, error } = useSelector(
    (state) => state.products
  );

  useEffect(() => {
    dispatch(fetchCategories());
    dispatch(fetchProducts({ page: 0, category: selectedCategory, search: searchQuery }));
  }, [dispatch, selectedCategory, searchQuery]);

  const handleCategorySelect = (categoryName) => {
    dispatch(setSelectedCategory(categoryName));
  };

  const handleClearFilters = () => {
    dispatch(setSelectedCategory('All'));
    dispatch(setSearchQuery(''));
  };

  return (
    <div className="my-8">
      {/* Filters Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 mb-6 pb-4 border-b border-ecom-border">
        {/* Category Pills */}
        <div className="flex flex-wrap gap-2 items-center">
          <span className="text-xs font-bold uppercase text-ecom-muted me-2 flex items-center gap-1">
            <Filter className="w-3.5 h-3.5 text-indigo-400" /> Categories:
          </span>
          <button
            onClick={() => handleCategorySelect('All')}
            className={`px-3 py-1 rounded-full text-xs font-semibold transition ${
              selectedCategory === 'All'
                ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/30 border border-indigo-500'
                : 'bg-ecom-panel text-ecom-muted hover:text-white border border-ecom-border'
            }`}
          >
            All Products
          </button>
          {categories.map((cat) => (
            <button
              key={cat.id}
              onClick={() => handleCategorySelect(cat.name)}
              className={`px-3 py-1 rounded-full text-xs font-semibold transition ${
                selectedCategory === cat.name
                  ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/30 border border-indigo-500'
                  : 'bg-ecom-panel text-ecom-muted hover:text-white border border-ecom-border'
              }`}
            >
              {cat.name} ({cat.productCount})
            </button>
          ))}
        </div>

        {/* Clear Filter / Status indicator */}
        {(selectedCategory !== 'All' || searchQuery) && (
          <div className="flex items-center gap-2 text-xs">
            <span className="text-ecom-muted">
              Filtered by: <strong className="text-white">{selectedCategory}</strong>
              {searchQuery && ` | "${searchQuery}"`}
            </span>
            <button
              onClick={handleClearFilters}
              className="text-indigo-400 hover:text-indigo-300 font-bold"
            >
              Clear filters
            </button>
          </div>
        )}
      </div>

      {/* Loading Skeleton */}
      {loading && (
        <div className="text-center py-16">
          <RefreshCw className="w-8 h-8 text-indigo-400 animate-spin mx-auto mb-3" />
          <p className="text-xs text-ecom-muted font-medium">
            Fetching product catalogue from Redis cache...
          </p>
        </div>
      )}

      {/* Error State */}
      {error && !loading && (
        <div className="p-4 rounded-2xl bg-rose-950/60 border border-rose-800 text-rose-200 flex items-center gap-3 my-4 text-xs">
          <AlertCircle className="w-5 h-5 text-rose-400 flex-shrink-0" />
          <div>
            <h4 className="text-xs font-bold text-white mb-0.5">Failed to load product catalog</h4>
            <p className="mb-0 text-rose-300">{error}</p>
          </div>
        </div>
      )}

      {/* Empty State */}
      {!loading && !error && items.length === 0 && (
        <div className="text-center py-16 bg-ecom-card rounded-3xl border border-dashed border-ecom-border p-8">
          <Sparkles className="w-10 h-10 text-slate-600 mx-auto mb-3" />
          <h3 className="text-base font-bold text-white mb-1">No products found</h3>
          <p className="text-xs text-ecom-muted max-w-md mx-auto mb-4">
            We couldn't find any items matching your filter criteria. Try adjusting your search or selecting another category.
          </p>
          <button onClick={handleClearFilters} className="px-4 py-2 rounded-xl bg-indigo-600 text-white font-bold text-xs">
            Reset Filters
          </button>
        </div>
      )}

      {/* Products Grid */}
      {!loading && items.length > 0 && (
        <div className="row row-cols-1 row-cols-sm-2 row-cols-md-3 row-cols-lg-4 g-4">
          {items.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
      )}
    </div>
  );
}
