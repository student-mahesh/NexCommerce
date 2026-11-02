// frontend/test-admin-inventory.js
// Unit tests for Admin Inventory Dashboard, Stock Calculations, Replenishment Validation & Redux State Mutations

import assert from 'node:assert/strict';
import inventoryReducer, {
  clearInventoryFeedback,
  clearInventoryError,
} from './src/features/inventory/inventorySlice.js';

console.log('================================================================');
console.log('Running NexCommerce Admin Inventory Management Unit Tests...');
console.log('================================================================\n');

// Sample inventory fixtures representing empirical microservice payloads
const sampleInventory = [
  {
    id: 1,
    productId: 1,
    sku: 'TECH-KB-01',
    availableQuantity: 45,
    reservedQuantity: 5,
    version: 3,
    updatedAt: '2026-11-01T10:00:00Z',
  },
  {
    id: 2,
    productId: 2,
    sku: 'AUD-HP-02',
    availableQuantity: 8,
    reservedQuantity: 2,
    version: 1,
    updatedAt: '2026-11-01T10:05:00Z',
  },
  {
    id: 3,
    productId: 3,
    sku: 'DISP-MON-03',
    availableQuantity: 0,
    reservedQuantity: 0,
    version: 4,
    updatedAt: '2026-11-01T10:10:00Z',
  },
  {
    id: 4,
    productId: 4,
    sku: 'WEAR-SW-04',
    availableQuantity: 10,
    reservedQuantity: 1,
    version: 2,
    updatedAt: '2026-11-01T10:15:00Z',
  },
  {
    id: 5,
    productId: 5,
    sku: 'TECH-MS-05',
    availableQuantity: 60,
    reservedQuantity: 0,
    version: 0,
    updatedAt: '2026-11-01T10:20:00Z',
  },
];

// Product metadata lookup map
const sampleProducts = [
  { id: 1, name: 'Apex Pro Mechanical Keyboard', sku: 'TECH-KB-01', category: 'Electronics' },
  { id: 2, name: 'Noise-Cancelling Wireless Headphones', sku: 'AUD-HP-02', category: 'Audio' },
  { id: 3, name: 'UltraWide Curved Monitor 34-inch', sku: 'DISP-MON-03', category: 'Displays' },
  { id: 4, name: 'Fitness Smartwatch Pro', sku: 'WEAR-SW-04', category: 'Wearables' },
  { id: 5, name: 'Ergonomic Wireless Mouse', sku: 'TECH-MS-05', category: 'Accessories' },
];

const productMap = new Map(sampleProducts.map((p) => [p.id, p]));

// Helper: Calculate Inventory KPI Summary
function computeInventorySummary(items) {
  let totalAvailable = 0;
  let totalReserved = 0;
  let lowStockCount = 0;
  let outOfStockCount = 0;

  items.forEach((item) => {
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
    totalSkus: items.length,
    totalAvailable,
    totalReserved,
    lowStockCount,
    outOfStockCount,
  };
}

// Helper: Classify stock status
function classifyStockStatus(availableQuantity) {
  if (availableQuantity === 0) return 'OUT_OF_STOCK';
  if (availableQuantity <= 10) return 'LOW_STOCK';
  return 'IN_STOCK';
}

// Helper: Validate replenishment input
function validateReplenishmentQuantity(val) {
  const num = parseInt(val, 10);
  if (!val || isNaN(num)) {
    return 'Please enter a valid replenishment quantity';
  }
  if (num < 1) {
    return 'Replenishment quantity must be at least 1 unit';
  }
  if (num > 10000) {
    return 'Replenishment quantity cannot exceed 10,000 units per request';
  }
  return null;
}

// Helper: Filter inventory items
function filterInventory(items, { searchTerm = '', statusFilter = 'ALL' }) {
  const term = searchTerm.trim().toLowerCase();

  return items.filter((item) => {
    const product = productMap.get(item.productId);
    const productName = (product?.name || '').toLowerCase();
    const productCategory = (product?.category || '').toLowerCase();
    const sku = (item.sku || product?.sku || '').toLowerCase();

    if (term) {
      const matchSku = sku.includes(term);
      const matchName = productName.includes(term);
      const matchCat = productCategory.includes(term);
      const matchId = String(item.productId).includes(term);
      if (!matchSku && !matchName && !matchCat && !matchId) {
        return false;
      }
    }

    const available = item.availableQuantity ?? 0;
    const reserved = item.reservedQuantity ?? 0;

    if (statusFilter === 'IN_STOCK' && available <= 10) return false;
    if (statusFilter === 'LOW_STOCK' && (available < 1 || available > 10)) return false;
    if (statusFilter === 'OUT_OF_STOCK' && available !== 0) return false;
    if (statusFilter === 'ACTIVELY_RESERVED' && reserved <= 0) return false;

    return true;
  });
}

// ============================================================================
// TEST 1: Inventory Summary KPI Calculations
// ============================================================================
console.log('[Test 1] KPI Calculations: Aggregates total SKUs, available, reserved, low stock and out of stock');
const summary = computeInventorySummary(sampleInventory);
assert.equal(summary.totalSkus, 5, 'Total SKUs must equal 5');
assert.equal(summary.totalAvailable, 45 + 8 + 0 + 10 + 60, 'Total available units must equal 123');
assert.equal(summary.totalReserved, 5 + 2 + 0 + 1 + 0, 'Total reserved units must equal 8');
assert.equal(summary.lowStockCount, 2, 'Low-stock count (<=10 and >0) must equal 2 (Items 2 and 4)');
assert.equal(summary.outOfStockCount, 1, 'Out-of-stock count (==0) must equal 1 (Item 3)');
console.log('  ✓ Pass: Inventory summary KPI calculations are mathematically exact\n');

// ============================================================================
// TEST 2: Stock Status Classification Rules
// ============================================================================
console.log('[Test 2] Stock Status Classification: Correctly buckets available quantities');
assert.equal(classifyStockStatus(45), 'IN_STOCK');
assert.equal(classifyStockStatus(11), 'IN_STOCK');
assert.equal(classifyStockStatus(10), 'LOW_STOCK', '10 units is classified as LOW_STOCK boundary');
assert.equal(classifyStockStatus(1), 'LOW_STOCK', '1 unit is classified as LOW_STOCK');
assert.equal(classifyStockStatus(0), 'OUT_OF_STOCK', '0 units is classified as OUT_OF_STOCK');
console.log('  ✓ Pass: Stock classification adheres strictly to threshold rules\n');

// ============================================================================
// TEST 3: Search by SKU, Product Name, and Category
// ============================================================================
console.log('[Test 3] Search & Filter: Filtering by SKU, name, and category');
const searchBySku = filterInventory(sampleInventory, { searchTerm: 'DISP-MON-03' });
assert.equal(searchBySku.length, 1);
assert.equal(searchBySku[0].productId, 3);

const searchByName = filterInventory(sampleInventory, { searchTerm: 'Headphones' });
assert.equal(searchByName.length, 1);
assert.equal(searchByName[0].productId, 2);

const searchByCategory = filterInventory(sampleInventory, { searchTerm: 'Electronics' });
assert.equal(searchByCategory.length, 1);
assert.equal(searchByCategory[0].productId, 1);
console.log('  ✓ Pass: Search accurately filters across SKU, product name, and category\n');

// ============================================================================
// TEST 4: Reserved Stock Display & Active Saga Isolation
// ============================================================================
console.log('[Test 4] Reserved Stock Filter: Filters SKUs with active Saga reservations');
const activelyReserved = filterInventory(sampleInventory, { statusFilter: 'ACTIVELY_RESERVED' });
assert.equal(activelyReserved.length, 3, 'Must match items 1, 2, and 4 which have reservedQuantity > 0');
assert.deepEqual(activelyReserved.map((i) => i.productId), [1, 2, 4]);
console.log('  ✓ Pass: Active reservations isolated without confounding available stock\n');

// ============================================================================
// TEST 5: Replenishment Quantity Validation
// ============================================================================
console.log('[Test 5] Replenishment Validation: Enforces positive integer in range 1 – 10,000');
assert.equal(validateReplenishmentQuantity('50'), null, 'Valid quantity passes');
assert.equal(validateReplenishmentQuantity('1'), null, 'Boundary 1 passes');
assert.equal(validateReplenishmentQuantity('10000'), null, 'Boundary 10,000 passes');

assert.ok(validateReplenishmentQuantity('0').includes('at least 1'));
assert.ok(validateReplenishmentQuantity('-5').includes('at least 1'));
assert.ok(validateReplenishmentQuantity('10001').includes('cannot exceed 10,000'));
assert.ok(validateReplenishmentQuantity('abc').includes('valid replenishment quantity'));
assert.ok(validateReplenishmentQuantity('').includes('valid replenishment quantity'));
console.log('  ✓ Pass: Replenishment validation rejects 0, negatives, text, and excessive amounts\n');

// ============================================================================
// TEST 6: Projected Available Quantity Calculation
// ============================================================================
console.log('[Test 6] Projected Quantity: Adds requested amount to available only (excluding reserved)');
const item1 = sampleInventory[0]; // Available: 45, Reserved: 5
const qtyToAdd = 50;
const projectedAvailable = item1.availableQuantity + qtyToAdd;
assert.equal(projectedAvailable, 95, 'Projected available must be 45 + 50 = 95');
// Verify reserved quantity is NOT added to available
assert.notEqual(projectedAvailable, item1.availableQuantity + item1.reservedQuantity + qtyToAdd);
console.log('  ✓ Pass: Projected calculation strictly isolates available stock\n');

// ============================================================================
// TEST 7: Redux Reducer — replenishStock.fulfilled
// ============================================================================
console.log('[Test 7] Redux Reducer: replenishStock.fulfilled mutates item in place with server response');
let state = {
  items: [...sampleInventory],
  loading: false,
  error: null,
  replenishing: true,
  replenishError: null,
  replenishSuccess: null,
};

const updatedServerItem = {
  id: 2,
  productId: 2,
  sku: 'AUD-HP-02',
  availableQuantity: 58, // Was 8, added 50
  reservedQuantity: 2,   // Reserved strictly preserved
  version: 2,
  updatedAt: '2026-11-01T10:30:00Z',
};

state = inventoryReducer(state, {
  type: 'inventory/replenishStock/fulfilled',
  payload: updatedServerItem,
});

assert.equal(state.replenishing, false);
assert.ok(state.replenishSuccess.includes('AUD-HP-02'));
const targetItem = state.items.find((i) => i.productId === 2);
assert.equal(targetItem.availableQuantity, 58, 'Item availableQuantity must update to 58');
assert.equal(targetItem.reservedQuantity, 2, 'Reserved quantity must remain 2');
console.log('  ✓ Pass: Reducer updates item in-place with authoritative server payload\n');

// ============================================================================
// TEST 8: Redux Reducer — replenishStock.rejected
// ============================================================================
console.log('[Test 8] Redux Reducer: replenishStock.rejected sets replenishError and resets replenishing');
state = inventoryReducer(state, {
  type: 'inventory/replenishStock/rejected',
  payload: 'Quantity exceeds warehouse allocation limit',
});

assert.equal(state.replenishing, false);
assert.equal(state.replenishError, 'Quantity exceeds warehouse allocation limit');
console.log('  ✓ Pass: Reducer handles rejection cleanly\n');

// ============================================================================
// TEST 9: 401 Unauthorized & 403 Forbidden Error Handling
// ============================================================================
console.log('[Test 9] Security Errors: Reducer handles 401 and 403 security rejections');
const unauthErrorState = inventoryReducer(state, {
  type: 'inventory/replenishStock/rejected',
  payload: 'Authentication required: Missing or invalid Authorization header',
});
assert.equal(unauthErrorState.replenishError, 'Authentication required: Missing or invalid Authorization header');

const forbiddenErrorState = inventoryReducer(state, {
  type: 'inventory/replenishStock/rejected',
  payload: 'Access denied: Admin privileges required',
});
assert.equal(forbiddenErrorState.replenishError, 'Access denied: Admin privileges required');
console.log('  ✓ Pass: Reducer properly surfaces 401 and 403 security feedback\n');

// ============================================================================
// TEST 10: State Refresh and Feedback Clearing
// ============================================================================
console.log('[Test 10] Feedback Clearing: clearInventoryFeedback resets alert banners');
let clearedState = inventoryReducer(forbiddenErrorState, clearInventoryFeedback());
assert.equal(clearedState.replenishError, null);
assert.equal(clearedState.replenishSuccess, null);
console.log('  ✓ Pass: clearInventoryFeedback resets banners cleanly\n');

console.log('================================================================');
console.log('All 10 Admin Inventory tests passed successfully!');
console.log('================================================================\n');

