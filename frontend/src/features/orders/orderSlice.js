import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import api from '../../services/api.js';

export const createOrder = createAsyncThunk(
  'orders/createOrder',
  async (orderPayload, { rejectWithValue }) => {
    try {
      const res = await api.post('/orders', orderPayload);
      return res.data;
    } catch (err) {
      return rejectWithValue(err.message || 'Failed to place order');
    }
  }
);

export const fetchOrderById = createAsyncThunk(
  'orders/fetchOrderById',
  async (orderId, { rejectWithValue }) => {
    try {
      const res = await api.get(`/orders/${orderId}`);
      return res.data;
    } catch (err) {
      return rejectWithValue(err.message || 'Failed to fetch order details');
    }
  }
);

export const fetchMyOrders = createAsyncThunk(
  'orders/fetchMyOrders',
  async (_, { rejectWithValue }) => {
    try {
      const res = await api.get('/orders/my-orders');
      return res.data;
    } catch (err) {
      return rejectWithValue(err.message || 'Failed to fetch order history');
    }
  }
);

export const fetchOrderNotifications = createAsyncThunk(
  'orders/fetchOrderNotifications',
  async (orderId, { rejectWithValue }) => {
    try {
      const res = await api.get(`/notifications/order/${orderId}`);
      return res.data;
    } catch (err) {
      return rejectWithValue(err.message || 'Failed to fetch order notifications');
    }
  }
);

export const fetchAllOrdersAdmin = createAsyncThunk(
  'orders/fetchAllOrdersAdmin',
  async (_, { rejectWithValue }) => {
    try {
      const res = await api.get('/orders/admin');
      return res.data;
    } catch (err) {
      return rejectWithValue(err.message || 'Failed to fetch all orders for admin');
    }
  }
);

export const fetchPaymentByOrderId = createAsyncThunk(
  'orders/fetchPaymentByOrderId',
  async (orderId, { rejectWithValue }) => {
    try {
      const res = await api.get(`/payments/order/${orderId}`);
      return res.data;
    } catch (err) {
      // 404 is normal if payment step was never reached (e.g. inventory rejected)
      return rejectWithValue(err.message || 'Payment transaction not found');
    }
  }
);

const orderSlice = createSlice({
  name: 'orders',
  initialState: {
    currentOrder: null,
    ordersList: [],
    notifications: [],
    loading: false,
    submitting: false,
    error: null,
    // Admin specific states
    adminOrders: [],
    adminOrdersLoading: false,
    adminOrdersError: null,
    adminSelectedPayment: null,
    adminPaymentLoading: false,
    adminPaymentError: null,
  },
  reducers: {
    clearCurrentOrder: (state) => {
      state.currentOrder = null;
      state.notifications = [];
      state.error = null;
      state.adminSelectedPayment = null;
      state.adminPaymentError = null;
    },
    clearOrderError: (state) => {
      state.error = null;
      state.adminOrdersError = null;
    },
    clearAdminPaymentDetails: (state) => {
      state.adminSelectedPayment = null;
      state.adminPaymentError = null;
      state.adminPaymentLoading = false;
    },
  },
  extraReducers: (builder) => {
    builder
      // createOrder
      .addCase(createOrder.pending, (state) => {
        state.submitting = true;
        state.error = null;
      })
      .addCase(createOrder.fulfilled, (state, action) => {
        state.submitting = false;
        state.currentOrder = action.payload;
        state.error = null;
      })
      .addCase(createOrder.rejected, (state, action) => {
        state.submitting = false;
        state.error = action.payload;
      })
      // fetchOrderById
      .addCase(fetchOrderById.pending, (state) => {
        state.loading = true;
      })
      .addCase(fetchOrderById.fulfilled, (state, action) => {
        state.loading = false;
        state.currentOrder = action.payload;
      })
      .addCase(fetchOrderById.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload;
      })
      // fetchMyOrders
      .addCase(fetchMyOrders.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchMyOrders.fulfilled, (state, action) => {
        state.loading = false;
        state.ordersList = action.payload || [];
      })
      .addCase(fetchMyOrders.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload;
      })
      // fetchOrderNotifications
      .addCase(fetchOrderNotifications.fulfilled, (state, action) => {
        state.notifications = action.payload || [];
      })
      .addCase(fetchOrderNotifications.rejected, (state) => {
        state.notifications = [];
      })
      // fetchAllOrdersAdmin
      .addCase(fetchAllOrdersAdmin.pending, (state) => {
        state.adminOrdersLoading = true;
        state.adminOrdersError = null;
      })
      .addCase(fetchAllOrdersAdmin.fulfilled, (state, action) => {
        state.adminOrdersLoading = false;
        state.adminOrders = action.payload || [];
      })
      .addCase(fetchAllOrdersAdmin.rejected, (state, action) => {
        state.adminOrdersLoading = false;
        state.adminOrdersError = action.payload;
      })
      // fetchPaymentByOrderId
      .addCase(fetchPaymentByOrderId.pending, (state) => {
        state.adminPaymentLoading = true;
        state.adminPaymentError = null;
        state.adminSelectedPayment = null;
      })
      .addCase(fetchPaymentByOrderId.fulfilled, (state, action) => {
        state.adminPaymentLoading = false;
        state.adminSelectedPayment = action.payload;
      })
      .addCase(fetchPaymentByOrderId.rejected, (state, action) => {
        state.adminPaymentLoading = false;
        state.adminPaymentError = action.payload;
      });
  },
});

export const {
  clearCurrentOrder,
  clearOrderError,
  clearAdminPaymentDetails,
} = orderSlice.actions;

export default orderSlice.reducer;
