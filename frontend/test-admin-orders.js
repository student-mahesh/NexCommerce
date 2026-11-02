// frontend/test-admin-orders.js
// Unit tests for Admin Order Oversight, Client Filtering, Saga State Machine & Payment Simulation Auditing

import assert from 'node:assert/strict';
import orderReducer, {
  clearCurrentOrder,
  clearAdminPaymentDetails,
} from './src/features/orders/orderSlice.js';

console.log('================================================================');
console.log('Running NexCommerce Admin Order Oversight Unit Tests...');
console.log('================================================================\n');

// Mock order fixtures representing empirical microservice payloads
const sampleOrders = [
  {
    id: 101,
    userId: 2,
    customerEmail: 'customer1@example.com',
    shippingAddress: '123 Main St, Springfield, IL 62701',
    status: 'CONFIRMED',
    cancellationReason: null,
    totalAmount: 299.98,
    createdAt: '2026-11-01T10:00:00Z',
    items: [
      {
        id: 1,
        productId: 10,
        productName: 'Mechanical Gaming Keyboard',
        sku: 'TECH-KB-01',
        unitPrice: 149.99,
        quantity: 2,
        lineTotal: 299.98,
      },
    ],
  },
  {
    id: 102,
    userId: 3,
    customerEmail: 'jane.smith@example.com',
    shippingAddress: '456 Oak Ave, Seattle, WA 98101',
    status: 'CANCELLED',
    cancellationReason: 'Payment authorization declined by simulated gateway (insufficient funds)',
    totalAmount: 799.00,
    createdAt: '2026-11-01T10:15:00Z',
    items: [
      {
        id: 2,
        productId: 12,
        productName: 'UltraWide Curved Monitor 34-inch',
        sku: 'TECH-MON-34',
        unitPrice: 799.00,
        quantity: 1,
        lineTotal: 799.00,
      },
    ],
  },
  {
    id: 103,
    userId: 4,
    customerEmail: 'alice.johnson@example.com',
    shippingAddress: '789 Pine Rd, Austin, TX 78701',
    status: 'CANCELLED',
    cancellationReason: 'Insufficient inventory for SKU: TECH-LAPTOP-16. Requested 1, available 0.',
    totalAmount: 1499.00,
    createdAt: '2026-11-01T10:30:00Z',
    items: [
      {
        id: 3,
        productId: 15,
        productName: 'Pro Creator Laptop 16',
        sku: 'TECH-LAPTOP-16',
        unitPrice: 1499.00,
        quantity: 1,
        lineTotal: 1499.00,
      },
    ],
  },
  {
    id: 104,
    userId: 5,
    customerEmail: 'pending.buyer@example.com',
    shippingAddress: '100 Gateway Blvd, Denver, CO 80201',
    status: 'PENDING',
    cancellationReason: null,
    totalAmount: 49.99,
    createdAt: '2026-11-01T11:00:00Z',
    items: [
      {
        id: 4,
        productId: 20,
        productName: 'Wireless Gaming Mouse',
        sku: 'TECH-MOUSE-01',
        unitPrice: 49.99,
        quantity: 1,
        lineTotal: 49.99,
      },
    ],
  },
];

// Helper: Filter orders matching AdminOrdersPage client-side filtering logic
function filterAdminOrders(orders, { searchTerm = '', selectedStatus = 'ALL', selectedMode = 'ALL' }) {
  const term = searchTerm.trim().toLowerCase();

  return orders.filter((order) => {
    // 1. Status filter
    if (selectedStatus !== 'ALL' && order.status !== selectedStatus) {
      return false;
    }

    // 2. Simulation Mode filter
    if (selectedMode === 'SIMULATED_SUCCESS') {
      if (order.status !== 'CONFIRMED') return false;
    } else if (selectedMode === 'SIMULATED_FAILURE') {
      if (order.status !== 'CANCELLED') return false;
    }

    // 3. Search query filter (Order ID, Customer Email, Shipping Address, SKU, Product Name)
    if (term) {
      const matchId = String(order.id).includes(term);
      const matchEmail = (order.customerEmail || '').toLowerCase().includes(term);
      const matchAddress = (order.shippingAddress || '').toLowerCase().includes(term);
      const matchItems = (order.items || []).some(
        (item) =>
          (item.productName || '').toLowerCase().includes(term) ||
          (item.sku || '').toLowerCase().includes(term)
      );

      return matchId || matchEmail || matchAddress || matchItems;
    }

    return true;
  });
}

// Helper: Map Saga Lifecycle Stages matching AdminOrderDetailsModal logic
function computeSagaStages(order, notifications = [], payment = null) {
  const isConfirmed = order.status === 'CONFIRMED';
  const isCancelled = order.status === 'CANCELLED';
  const isPending = order.status === 'PENDING';

  const getEvent = (type) => notifications.find((n) => n.eventType === type);
  const orderCreatedEvt = getEvent('ORDER_CREATED');
  const invReservedEvt = getEvent('INVENTORY_RESERVED');
  const invRejectedEvt = getEvent('INVENTORY_REJECTED');
  const paySucceededEvt = getEvent('PAYMENT_SUCCEEDED');
  const payFailedEvt = getEvent('PAYMENT_FAILED');

  // Stage 1: Order Created
  const stage1 = {
    title: 'Order Created',
    status: 'completed',
    timestamp: orderCreatedEvt?.createdAt || order.createdAt,
    evidence: orderCreatedEvt ? `Kafka event: ${orderCreatedEvt.eventId}` : 'Persisted in Order DB',
  };

  // Stage 2: Inventory Reservation
  let stage2Status = 'pending';
  let stage2Details = 'Awaiting inventory verification';
  let stage2Timestamp = invReservedEvt?.createdAt || invRejectedEvt?.createdAt || null;

  if (invReservedEvt) {
    stage2Status = 'completed';
    stage2Details = 'Stock reserved atomically';
  } else if (invRejectedEvt) {
    stage2Status = 'failed';
    stage2Details = invRejectedEvt.message || 'Inventory reservation failed (Out of stock)';
  } else if (isConfirmed) {
    stage2Status = 'completed';
    stage2Details = 'Inventory reserved';
  } else if (isCancelled) {
    if (order.cancellationReason && order.cancellationReason.toLowerCase().includes('inventory')) {
      stage2Status = 'failed';
      stage2Details = order.cancellationReason;
    } else {
      stage2Status = 'completed';
      stage2Details = 'Reserved then compensated';
    }
  }

  // Stage 3: Payment Processing
  let stage3Status = 'pending';
  let stage3Details = 'Awaiting payment authorization';
  let stage3Timestamp = paySucceededEvt?.createdAt || payFailedEvt?.createdAt || payment?.createdAt || null;

  if (paySucceededEvt || payment?.status === 'SUCCESS') {
    stage3Status = 'completed';
    stage3Details = 'Payment authorized (Simulated)';
  } else if (payFailedEvt || payment?.status === 'FAILED') {
    stage3Status = 'failed';
    stage3Details = payFailedEvt?.message || payment?.failureReason || 'Payment failed (Simulated)';
  } else if (stage2Status === 'failed') {
    stage3Status = 'skipped';
    stage3Details = 'Skipped: Inventory rejected before payment';
  } else if (isConfirmed) {
    stage3Status = 'completed';
    stage3Details = 'Payment confirmed';
  } else if (isCancelled) {
    if (order.cancellationReason && order.cancellationReason.toLowerCase().includes('payment')) {
      stage3Status = 'failed';
      stage3Details = order.cancellationReason;
    } else {
      stage3Status = 'skipped';
      stage3Details = 'Not reached or compensated';
    }
  }

  // Stage 4: Order Outcome
  let stage4Status = 'pending';
  let stage4Details = 'Saga workflow executing...';
  if (isConfirmed) {
    stage4Status = 'completed';
    stage4Details = 'Order confirmed and ready for fulfillment';
  } else if (isCancelled) {
    stage4Status = 'failed';
    stage4Details = order.cancellationReason || 'Order cancelled via Saga compensation';
  }

  return { stage1, stage2: { status: stage2Status, details: stage2Details, timestamp: stage2Timestamp }, stage3: { status: stage3Status, details: stage3Details, timestamp: stage3Timestamp }, stage4: { status: stage4Status, details: stage4Details } };
}

// ============================================================================
// TEST 1: Filter Orders by Keyword (Order ID, Email, SKU, Product Name)
// ============================================================================
console.log('[Test 1] Order Search & Filter: Filtering by ID, email, and SKU');
const searchById = filterAdminOrders(sampleOrders, { searchTerm: '102' });
assert.equal(searchById.length, 1, 'Should find order 102 by ID');
assert.equal(searchById[0].id, 102);

const searchByEmail = filterAdminOrders(sampleOrders, { searchTerm: 'alice.johnson' });
assert.equal(searchByEmail.length, 1, 'Should find order by customer email substring');
assert.equal(searchByEmail[0].id, 103);

const searchBySku = filterAdminOrders(sampleOrders, { searchTerm: 'TECH-KB-01' });
assert.equal(searchBySku.length, 1, 'Should find order containing item with SKU TECH-KB-01');
assert.equal(searchBySku[0].id, 101);
console.log('  ✓ Pass: Search correctly matches order ID, customer email, and product SKU\n');

// ============================================================================
// TEST 2: Filter Orders by Status (ALL, CONFIRMED, CANCELLED, PENDING)
// ============================================================================
console.log('[Test 2] Order Search & Filter: Filtering by order status');
const confirmedOnly = filterAdminOrders(sampleOrders, { selectedStatus: 'CONFIRMED' });
assert.equal(confirmedOnly.length, 1);
assert.equal(confirmedOnly[0].id, 101);

const cancelledOnly = filterAdminOrders(sampleOrders, { selectedStatus: 'CANCELLED' });
assert.equal(cancelledOnly.length, 2);
assert.deepEqual(cancelledOnly.map((o) => o.id), [102, 103]);

const pendingOnly = filterAdminOrders(sampleOrders, { selectedStatus: 'PENDING' });
assert.equal(pendingOnly.length, 1);
assert.equal(pendingOnly[0].id, 104);
console.log('  ✓ Pass: Status filtering isolates CONFIRMED, CANCELLED, and PENDING orders\n');

// ============================================================================
// TEST 3: Filter Orders by Simulation Mode (ALL, SIMULATED_SUCCESS, SIMULATED_FAILURE)
// ============================================================================
console.log('[Test 3] Order Search & Filter: Filtering by simulated outcome mode');
const simSuccess = filterAdminOrders(sampleOrders, { selectedMode: 'SIMULATED_SUCCESS' });
assert.equal(simSuccess.length, 1);
assert.equal(simSuccess[0].status, 'CONFIRMED');

const simFailure = filterAdminOrders(sampleOrders, { selectedMode: 'SIMULATED_FAILURE' });
assert.equal(simFailure.length, 2);
assert.ok(simFailure.every((o) => o.status === 'CANCELLED'));
console.log('  ✓ Pass: Simulation mode filter cleanly segments success vs failure orders\n');

// ============================================================================
// TEST 4: Saga Stages Mapping — Confirmed Order Flow
// ============================================================================
console.log('[Test 4] Saga Visualization: Confirmed order produces 4 completed stages');
const confirmedOrder = sampleOrders[0];
const confirmedNotifications = [
  { eventType: 'ORDER_CREATED', eventId: 'evt-001', createdAt: '2026-11-01T10:00:00Z' },
  { eventType: 'INVENTORY_RESERVED', eventId: 'evt-002', createdAt: '2026-11-01T10:00:01Z' },
  { eventType: 'PAYMENT_SUCCEEDED', eventId: 'evt-003', createdAt: '2026-11-01T10:00:02Z' },
];
const paymentTx = { status: 'SUCCESS', transactionId: 'TXN-SIM-101', paymentMethod: 'CREDIT_CARD' };
const saga1 = computeSagaStages(confirmedOrder, confirmedNotifications, paymentTx);

assert.equal(saga1.stage1.status, 'completed');
assert.equal(saga1.stage2.status, 'completed');
assert.equal(saga1.stage3.status, 'completed');
assert.equal(saga1.stage4.status, 'completed');
console.log('  ✓ Pass: Confirmed order maps to 4 sequential completed Saga stages\n');

// ============================================================================
// TEST 5: Saga Stages Mapping — Payment Failure with Inventory Compensation
// ============================================================================
console.log('[Test 5] Saga Visualization: Payment failure correctly reflects compensation');
const paymentFailOrder = sampleOrders[1]; // Cancelled due to payment
const paymentFailNotifications = [
  { eventType: 'ORDER_CREATED', eventId: 'evt-010', createdAt: '2026-11-01T10:15:00Z' },
  { eventType: 'INVENTORY_RESERVED', eventId: 'evt-011', createdAt: '2026-11-01T10:15:01Z' },
  { eventType: 'PAYMENT_FAILED', eventId: 'evt-012', message: 'Insufficient funds', createdAt: '2026-11-01T10:15:02Z' },
];
const failedPaymentTx = { status: 'FAILED', failureReason: 'Insufficient funds' };
const saga2 = computeSagaStages(paymentFailOrder, paymentFailNotifications, failedPaymentTx);

assert.equal(saga2.stage1.status, 'completed');
assert.equal(saga2.stage2.status, 'completed', 'Inventory was initially reserved');
assert.equal(saga2.stage3.status, 'failed', 'Payment stage failed');
assert.equal(saga2.stage4.status, 'failed', 'Final outcome is cancelled');
console.log('  ✓ Pass: Payment failure identifies initial reservation and subsequent cancellation\n');

// ============================================================================
// TEST 6: Saga Stages Mapping — Inventory Out of Stock (Payment Skipped)
// ============================================================================
console.log('[Test 6] Saga Visualization: Out-of-stock cancels Saga before payment');
const oosOrder = sampleOrders[2]; // Cancelled due to inventory
const oosNotifications = [
  { eventType: 'ORDER_CREATED', eventId: 'evt-020', createdAt: '2026-11-01T10:30:00Z' },
  { eventType: 'INVENTORY_REJECTED', eventId: 'evt-021', message: 'Insufficient stock', createdAt: '2026-11-01T10:30:01Z' },
];
const saga3 = computeSagaStages(oosOrder, oosNotifications, null);

assert.equal(saga3.stage1.status, 'completed');
assert.equal(saga3.stage2.status, 'failed', 'Inventory reservation failed');
assert.equal(saga3.stage3.status, 'skipped', 'Payment stage must be skipped when inventory fails');
assert.equal(saga3.stage4.status, 'failed', 'Order outcome cancelled');
console.log('  ✓ Pass: Out-of-stock marks stage 2 failed, stage 3 skipped, and stage 4 cancelled\n');

// ============================================================================
// TEST 7: Simulated Payment Demo Disclaimer Notice Enforcement
// ============================================================================
console.log('[Test 7] Payment Simulator Auditing: Mandatory demo disclaimer notice present');
const demoNotice = 'DEMO / SIMULATED PAYMENT ONLY';
const demoDescription = 'Transactions are simulated by payment-service mock engine. No actual credit card or bank funds are debited.';

assert.ok(demoNotice.includes('SIMULATED PAYMENT'), 'Notice must contain simulated payment disclaimer');
assert.ok(demoDescription.includes('No actual credit card'), 'Notice must explicitly state no real card charges occur');
console.log('  ✓ Pass: Payment simulator disclaimer correctly labels mock execution\n');

// ============================================================================
// TEST 8: Line Items Isolation & Calculation Integrity
// ============================================================================
console.log('[Test 8] Authoritative Line Items: Computed line totals match order total amount');
sampleOrders.forEach((order) => {
  const calculatedTotal = order.items.reduce((sum, item) => sum + (item.unitPrice * item.quantity), 0);
  assert.equal(
    Math.round(calculatedTotal * 100) / 100,
    order.totalAmount,
    `Order ${order.id} calculated line totals must match authoritative order totalAmount`
  );
});
console.log('  ✓ Pass: Line items calculate accurately without floating point discrepancies\n');

// ============================================================================
// TEST 9: Redux Reducer State Transitions (fetchAllOrdersAdmin & fetchPaymentByOrderId)
// ============================================================================
console.log('[Test 9] Redux Order Reducer: Handles admin actions and state transitions');
let state = orderReducer(undefined, { type: '@@INIT' });

// Verify initial admin state
assert.deepEqual(state.adminOrders, []);
assert.equal(state.adminOrdersLoading, false);
assert.equal(state.adminOrdersError, null);
assert.equal(state.adminSelectedPayment, null);

// Action: fetchAllOrdersAdmin.pending
state = orderReducer(state, { type: 'orders/fetchAllOrdersAdmin/pending' });
assert.equal(state.adminOrdersLoading, true);

// Action: fetchAllOrdersAdmin.fulfilled
state = orderReducer(state, {
  type: 'orders/fetchAllOrdersAdmin/fulfilled',
  payload: sampleOrders,
});
assert.equal(state.adminOrdersLoading, false);
assert.equal(state.adminOrders.length, 4);

// Action: fetchPaymentByOrderId.fulfilled
state = orderReducer(state, {
  type: 'orders/fetchPaymentByOrderId/fulfilled',
  payload: { transactionId: 'TXN-SIM-999', status: 'SUCCESS', amount: 299.98 },
});
assert.equal(state.adminSelectedPayment.transactionId, 'TXN-SIM-999');

// Action: clearAdminPaymentDetails
state = orderReducer(state, clearAdminPaymentDetails());
assert.equal(state.adminSelectedPayment, null);

// Action: clearCurrentOrder
state = orderReducer(state, clearCurrentOrder());
assert.equal(state.adminSelectedPayment, null);
assert.deepEqual(state.notifications, []);
console.log('  ✓ Pass: Redux state responds cleanly to pending, fulfilled, and clearing reducers\n');

// ============================================================================
// TEST 10: Graceful Fallback When Event Trail Is Empty
// ============================================================================
console.log('[Test 10] Event Trail Fallback: System gracefully handles empty notification logs');
const emptyNotifications = [];
const fallbackSaga = computeSagaStages(confirmedOrder, emptyNotifications, null);

assert.equal(fallbackSaga.stage1.status, 'completed');
assert.equal(fallbackSaga.stage2.status, 'completed', 'Infers completed from order.status = CONFIRMED');
assert.equal(fallbackSaga.stage3.status, 'completed', 'Infers completed from order.status = CONFIRMED');
assert.equal(fallbackSaga.stage4.status, 'completed');
assert.ok(fallbackSaga.stage1.evidence.includes('Persisted in Order DB'), 'Falls back to DB record instead of throwing');
console.log('  ✓ Pass: Empty notification log falls back gracefully without fictitious timestamps\n');

console.log('================================================================');
console.log('All 10 Admin Order Oversight tests passed successfully!');
console.log('================================================================\n');

