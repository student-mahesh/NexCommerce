import React, { useEffect } from 'react';
import { Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import Navbar from './components/Navbar';
import Footer from './components/Footer';
import CartDrawer from './features/cart/CartDrawer';
import HomePage from './pages/HomePage';
import ProductDetailsPage from './pages/ProductDetailsPage';
import CartPage from './pages/CartPage';
import CheckoutPage from './pages/CheckoutPage';
import OrderConfirmationPage from './pages/OrderConfirmationPage';
import OrderTrackingPage from './pages/OrderTrackingPage';
import OrderHistoryPage from './pages/OrderHistoryPage';
import AdminRoute from './components/AdminRoute';
import AdminLayout from './layouts/AdminLayout';
import AdminOverviewPage from './pages/admin/AdminOverviewPage';
import AdminProductsPage from './pages/admin/AdminProductsPage';
import AdminOrdersPage from './pages/admin/AdminOrdersPage';
import AdminInventoryPage from './pages/admin/AdminInventoryPage';
import AdminPlaceholderPage from './pages/admin/AdminPlaceholderPage';
import { checkAuth } from './features/auth/authSlice';

/**
 * Customer Layout Wrapper
 * Renders storefront Navbar, CartDrawer, content, and Footer.
 * Keeps customer layout strictly isolated from the Admin Dashboard.
 */
function CustomerLayout() {
  return (
    <div className="d-flex flex-column min-vh-100 bg-ecom-bg text-ecom-text">
      <Navbar />
      <CartDrawer />
      <main className="flex-grow-1">
        <Outlet />
      </main>
      <Footer />
    </div>
  );
}

export default function App() {
  const dispatch = useDispatch();

  useEffect(() => {
    dispatch(checkAuth());
  }, [dispatch]);

  return (
    <Routes>
      {/* ========================================================================= */}
      {/* CUSTOMER STOREFRONT ROUTES (Isolated with Customer Navbar, Cart & Footer)   */}
      {/* ========================================================================= */}
      <Route element={<CustomerLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/products/:id" element={<ProductDetailsPage />} />
        <Route path="/cart" element={<CartPage />} />
        <Route path="/checkout" element={<CheckoutPage />} />
        <Route path="/order-confirmation/:id" element={<OrderConfirmationPage />} />
        <Route path="/orders/:id" element={<OrderTrackingPage />} />
        <Route path="/orders" element={<OrderHistoryPage />} />
      </Route>

      {/* ========================================================================= */}
      {/* ADMIN DASHBOARD ROUTES (Protected by AdminRoute & wrapped in AdminLayout) */}
      {/* ========================================================================= */}
      <Route path="/admin" element={<AdminRoute />}>
        <Route element={<AdminLayout />}>
          <Route index element={<AdminOverviewPage />} />
          <Route path="products" element={<AdminProductsPage />} />
          <Route path="orders" element={<AdminOrdersPage />} />
          <Route path="inventory" element={<AdminInventoryPage />} />
        </Route>
      </Route>

      {/* ========================================================================= */}
      {/* CATCH-ALL ROUTE (Redirects to storefront)                                */}
      {/* ========================================================================= */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
