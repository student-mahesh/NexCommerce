import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import api from '../../services/api.js';

export const fetchProducts = createAsyncThunk(
  'products/fetchProducts',
  async ({ page = 0, size = 12, category = '', search = '' } = {}, { rejectWithValue }) => {
    try {
      const params = new URLSearchParams();
      params.append('page', page);
      params.append('size', size);
      if (category && category !== 'All') params.append('category', category);
      if (search) params.append('search', search);

      const res = await api.get(`/products?${params.toString()}`);
      return res.data;
    } catch (err) {
      return rejectWithValue(err.message);
    }
  }
);

export const fetchProductById = createAsyncThunk(
  'products/fetchProductById',
  async (id, { rejectWithValue }) => {
    try {
      const res = await api.get(`/products/${id}`);
      return res.data;
    } catch (err) {
      return rejectWithValue(err.message);
    }
  }
);

export const fetchCategories = createAsyncThunk(
  'products/fetchCategories',
  async (_, { rejectWithValue }) => {
    try {
      const res = await api.get('/categories');
      return res.data;
    } catch (err) {
      return rejectWithValue(err.message);
    }
  }
);

export const createProduct = createAsyncThunk(
  'products/createProduct',
  async (productData, { rejectWithValue }) => {
    try {
      const res = await api.post('/products', productData);
      return res.data;
    } catch (err) {
      return rejectWithValue(err.message || 'Failed to create product');
    }
  }
);

export const updateProduct = createAsyncThunk(
  'products/updateProduct',
  async ({ id, productData }, { rejectWithValue }) => {
    try {
      const res = await api.put(`/products/${id}`, productData);
      return res.data;
    } catch (err) {
      return rejectWithValue(err.message || 'Failed to update product');
    }
  }
);

export const deleteProduct = createAsyncThunk(
  'products/deleteProduct',
  async (id, { rejectWithValue }) => {
    try {
      await api.delete(`/products/${id}`);
      return id;
    } catch (err) {
      return rejectWithValue(err.message || 'Failed to delete product');
    }
  }
);

const productSlice = createSlice({
  name: 'products',
  initialState: {
    items: [],
    selectedProduct: null,
    categories: [],
    selectedCategory: 'All',
    searchQuery: '',
    loading: false,
    detailsLoading: false,
    error: null,
    adminActionLoading: false,
    adminActionError: null,
    adminSuccessMessage: null,
    pagination: {
      pageNumber: 0,
      pageSize: 12,
      totalElements: 0,
      totalPages: 0,
    },
  },
  reducers: {
    setSelectedCategory: (state, action) => {
      state.selectedCategory = action.payload;
    },
    setSearchQuery: (state, action) => {
      state.searchQuery = action.payload;
    },
    clearSelectedProduct: (state) => {
      state.selectedProduct = null;
    },
    clearAdminFeedback: (state) => {
      state.adminActionError = null;
      state.adminSuccessMessage = null;
    },
  },
  extraReducers: (builder) => {
    builder
      // fetchProducts
      .addCase(fetchProducts.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchProducts.fulfilled, (state, action) => {
        state.loading = false;
        state.items = action.payload?.content || [];
        state.pagination = {
          pageNumber: action.payload?.pageNumber ?? 0,
          pageSize: action.payload?.pageSize ?? 12,
          totalElements: action.payload?.totalElements ?? 0,
          totalPages: action.payload?.totalPages ?? 0,
        };
      })
      .addCase(fetchProducts.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload;
      })
      // fetchProductById
      .addCase(fetchProductById.pending, (state) => {
        state.detailsLoading = true;
        state.selectedProduct = null;
      })
      .addCase(fetchProductById.fulfilled, (state, action) => {
        state.detailsLoading = false;
        state.selectedProduct = action.payload;
      })
      .addCase(fetchProductById.rejected, (state, action) => {
        state.detailsLoading = false;
        state.error = action.payload;
      })
      // fetchCategories
      .addCase(fetchCategories.fulfilled, (state, action) => {
        state.categories = action.payload || [];
      })
      // createProduct
      .addCase(createProduct.pending, (state) => {
        state.adminActionLoading = true;
        state.adminActionError = null;
        state.adminSuccessMessage = null;
      })
      .addCase(createProduct.fulfilled, (state, action) => {
        state.adminActionLoading = false;
        state.adminSuccessMessage = `Product '${action.payload?.name}' created successfully`;
      })
      .addCase(createProduct.rejected, (state, action) => {
        state.adminActionLoading = false;
        state.adminActionError = action.payload;
      })
      // updateProduct
      .addCase(updateProduct.pending, (state) => {
        state.adminActionLoading = true;
        state.adminActionError = null;
        state.adminSuccessMessage = null;
      })
      .addCase(updateProduct.fulfilled, (state, action) => {
        state.adminActionLoading = false;
        state.adminSuccessMessage = `Product '${action.payload?.name}' updated successfully`;
        const updated = action.payload;
        if (updated) {
          const idx = state.items.findIndex((p) => p.id === updated.id);
          if (idx !== -1) {
            state.items[idx] = updated;
          }
        }
      })
      .addCase(updateProduct.rejected, (state, action) => {
        state.adminActionLoading = false;
        state.adminActionError = action.payload;
      })
      // deleteProduct
      .addCase(deleteProduct.pending, (state) => {
        state.adminActionLoading = true;
        state.adminActionError = null;
        state.adminSuccessMessage = null;
      })
      .addCase(deleteProduct.fulfilled, (state, action) => {
        state.adminActionLoading = false;
        state.adminSuccessMessage = 'Product deactivated successfully';
        state.items = state.items.filter((p) => p.id !== action.payload);
      })
      .addCase(deleteProduct.rejected, (state, action) => {
        state.adminActionLoading = false;
        state.adminActionError = action.payload;
      });
  },
});

export const {
  setSelectedCategory,
  setSearchQuery,
  clearSelectedProduct,
  clearAdminFeedback,
} = productSlice.actions;

export default productSlice.reducer;
