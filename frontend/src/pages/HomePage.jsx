import React from 'react';
import HeroBanner from '../components/HeroBanner';
import ProductList from '../features/products/ProductList';

export default function HomePage() {
  return (
    <div className="container py-2">
      <HeroBanner />
      <div className="mb-4">
        <h2 className="text-2xl font-black tracking-tight text-white mb-1">
          Explore Products
        </h2>
        <p className="text-xs text-ecom-muted">
          Real-time catalog synchronized with Redis caching. Instant sub-20ms queries.
        </p>
      </div>
      <ProductList />
    </div>
  );
}
