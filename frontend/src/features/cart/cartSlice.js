import { createSlice } from '@reduxjs/toolkit';

const isBrowser = typeof window !== 'undefined' && typeof window.localStorage !== 'undefined';

const loadCartFromStorage = () => {
  if (!isBrowser) return [];
  try {
    const saved = window.localStorage.getItem('ecom_cart');
    return saved ? JSON.parse(saved) : [];
  } catch {
    return [];
  }
};

const saveCartToStorage = (items) => {
  if (!isBrowser) return;
  try {
    window.localStorage.setItem('ecom_cart', JSON.stringify(items));
  } catch (err) {
    console.error('Failed to save cart to localStorage', err);
  }
};

const cartSlice = createSlice({
  name: 'cart',
  initialState: {
    items: loadCartFromStorage(),
    isDrawerOpen: false,
  },
  reducers: {
    addToCart: (state, action) => {
      const product = action.payload;
      const quantityToAdd = action.payload.quantity || 1;
      const existing = state.items.find((item) => item.id === product.id);

      if (existing) {
        existing.quantity = Math.min(existing.quantity + quantityToAdd, product.stockQuantity || 99);
      } else {
        state.items.push({
          id: product.id,
          name: product.name,
          price: product.price,
          sku: product.sku,
          imageUrl: product.imageUrl,
          stockQuantity: product.stockQuantity,
          quantity: quantityToAdd,
        });
      }
      saveCartToStorage(state.items);
      state.isDrawerOpen = true;
    },
    updateQuantity: (state, action) => {
      const { id, quantity } = action.payload;
      const item = state.items.find((it) => it.id === id);
      if (item) {
        if (quantity <= 0) {
          state.items = state.items.filter((it) => it.id !== id);
        } else {
          item.quantity = Math.min(quantity, item.stockQuantity || 99);
        }
      }
      saveCartToStorage(state.items);
    },
    removeFromCart: (state, action) => {
      state.items = state.items.filter((item) => item.id !== action.payload);
      saveCartToStorage(state.items);
    },
    clearCart: (state) => {
      state.items = [];
      saveCartToStorage([]);
    },
    toggleCartDrawer: (state, action) => {
      state.isDrawerOpen = typeof action.payload === 'boolean' ? action.payload : !state.isDrawerOpen;
    },
  },
});

export const { addToCart, updateQuantity, removeFromCart, clearCart, toggleCartDrawer } = cartSlice.actions;

export const selectCartTotal = (state) =>
  state.cart.items.reduce((total, item) => total + item.price * item.quantity, 0);

export const selectCartCount = (state) =>
  state.cart.items.reduce((total, item) => total + item.quantity, 0);

export default cartSlice.reducer;
