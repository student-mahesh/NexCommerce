import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import api from '../../services/api.js';

export const fetchInventory = createAsyncThunk(
  'inventory/fetchInventory',
  async (_, { rejectWithValue }) => {
    try {
      const res = await api.get('/inventory');
      return res.data;
    } catch (err) {
      return rejectWithValue(err.message || 'Failed to fetch inventory');
    }
  }
);

export const replenishStock = createAsyncThunk(
  'inventory/replenishStock',
  async ({ productId, quantity }, { rejectWithValue }) => {
    try {
      const res = await api.post(`/inventory/${productId}/replenish`, { quantity });
      return res.data;
    } catch (err) {
      return rejectWithValue(err.message || 'Failed to replenish stock');
    }
  }
);

const inventorySlice = createSlice({
  name: 'inventory',
  initialState: {
    items: [],
    loading: false,
    error: null,
    replenishing: false,
    replenishError: null,
    replenishSuccess: null,
  },
  reducers: {
    clearInventoryFeedback: (state) => {
      state.replenishError = null;
      state.replenishSuccess = null;
    },
    clearInventoryError: (state) => {
      state.error = null;
      state.replenishError = null;
    },
  },
  extraReducers: (builder) => {
    builder
      // fetchInventory
      .addCase(fetchInventory.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchInventory.fulfilled, (state, action) => {
        state.loading = false;
        state.items = action.payload || [];
      })
      .addCase(fetchInventory.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload;
      })
      // replenishStock
      .addCase(replenishStock.pending, (state) => {
        state.replenishing = true;
        state.replenishError = null;
        state.replenishSuccess = null;
      })
      .addCase(replenishStock.fulfilled, (state, action) => {
        state.replenishing = false;
        const updatedItem = action.payload;
        if (updatedItem && updatedItem.productId) {
          const index = state.items.findIndex(
            (i) => i.productId === updatedItem.productId
          );
          if (index !== -1) {
            state.items[index] = updatedItem;
          } else {
            state.items.push(updatedItem);
          }
        }
        state.replenishSuccess = `Stock for SKU ${updatedItem?.sku || updatedItem?.productId} replenished successfully.`;
      })
      .addCase(replenishStock.rejected, (state, action) => {
        state.replenishing = false;
        state.replenishError = action.payload;
      });
  },
});

export const { clearInventoryFeedback, clearInventoryError } =
  inventorySlice.actions;

export default inventorySlice.reducer;

