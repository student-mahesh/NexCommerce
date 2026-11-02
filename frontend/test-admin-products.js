// frontend/test-admin-products.js
// Unit tests for Admin Product Catalog Management, Form Validation & Redux State Mutations

import assert from 'node:assert/strict';
import productReducer, {
  clearAdminFeedback,
  setSelectedCategory,
  setSearchQuery,
} from './src/features/products/productSlice.js';

console.log('================================================================');
console.log('Running NexCommerce Admin Product Management Unit Tests...');
console.log('================================================================\n');

// Validation helper replicating AdminProductModal validation rules
function validateProductForm(formData) {
  const errors = {};

  if (!formData.name || !formData.name.trim()) {
    errors.name = 'Product name is required';
  }

  if (!formData.description || !formData.description.trim()) {
    errors.description = 'Product description is required';
  }

  const numPrice = parseFloat(formData.price);
  if (isNaN(numPrice) || numPrice <= 0) {
    errors.price = 'Price must be greater than $0.00';
  }

  if (!formData.sku || !formData.sku.trim()) {
    errors.sku = 'SKU identifier is required';
  } else if (!/^[A-Za-z0-9-_]+$/.test(formData.sku.trim())) {
    errors.sku = 'SKU can only contain alphanumeric characters, hyphens, and underscores';
  }

  if (!formData.category || !formData.category.trim()) {
    errors.category = 'Category is required';
  }

  const numStock = parseInt(formData.stockQuantity, 10);
  if (isNaN(numStock) || numStock < 0) {
    errors.stockQuantity = 'Stock quantity cannot be negative';
  }

  if (formData.imageUrl && formData.imageUrl.trim()) {
    const url = formData.imageUrl.trim();
    if (!url.startsWith('http://') && !url.startsWith('https://')) {
      errors.imageUrl = 'Image URL must use http:// or https:// protocol';
    }
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}

// Test 1: Validation passes for valid product
console.log('[Test 1] Form validation: Valid product request passes');
const validProduct = {
  name: 'Apex Pro Mechanical Keyboard RGB',
  description: 'Premium gaming keyboard with OLED smart display',
  price: '189.99',
  sku: 'TECH-KB-01',
  category: 'Electronics',
  stockQuantity: '50',
  imageUrl: 'https://example.com/keyboard.jpg',
};
const val1 = validateProductForm(validProduct);
assert.equal(val1.isValid, true, 'Valid form should pass with zero errors');
assert.equal(Object.keys(val1.errors).length, 0);
console.log('  ✓ Pass: Valid product inputs accepted\n');

// Test 2: Validation rejects non-positive price
console.log('[Test 2] Form validation: Rejects zero and negative price');
const zeroPriceProduct = { ...validProduct, price: '0.00' };
const negativePriceProduct = { ...validProduct, price: '-19.99' };
const invalidPriceProduct = { ...validProduct, price: 'abc' };

assert.equal(validateProductForm(zeroPriceProduct).isValid, false);
assert.equal(validateProductForm(negativePriceProduct).isValid, false);
assert.equal(validateProductForm(invalidPriceProduct).isValid, false);
console.log('  ✓ Pass: Zero, negative, and non-numeric prices rejected\n');

// Test 3: Validation rejects negative stock quantity
console.log('[Test 3] Form validation: Rejects negative stock quantity');
const negativeStockProduct = { ...validProduct, stockQuantity: '-5' };
const val3 = validateProductForm(negativeStockProduct);
assert.equal(val3.isValid, false);
assert.equal(val3.errors.stockQuantity, 'Stock quantity cannot be negative');
console.log('  ✓ Pass: Negative stock quantity rejected\n');

// Test 4: Validation rejects unapproved image URL schemes
console.log('[Test 4] Form validation: Rejects non-HTTP/HTTPS image URL protocols');
const xssImageProduct = { ...validProduct, imageUrl: 'javascript:alert(1)' };
const ftpImageProduct = { ...validProduct, imageUrl: 'ftp://files.example.com/img.jpg' };
const validHttpsProduct = { ...validProduct, imageUrl: 'https://cdn.example.com/img.png' };

assert.equal(validateProductForm(xssImageProduct).isValid, false);
assert.equal(validateProductForm(ftpImageProduct).isValid, false);
assert.equal(validateProductForm(validHttpsProduct).isValid, true);
console.log('  ✓ Pass: Script and FTP image URL protocols rejected; HTTP/S accepted\n');

// Test 5: Validation rejects empty SKU or invalid SKU characters
console.log('[Test 5] Form validation: Rejects missing and invalid SKU formats');
const emptySku = { ...validProduct, sku: '   ' };
const invalidSku = { ...validProduct, sku: 'TECH KB 01!' };
assert.equal(validateProductForm(emptySku).isValid, false);
assert.equal(validateProductForm(invalidSku).isValid, false);
console.log('  ✓ Pass: Invalid and whitespace-only SKUs rejected\n');

// Test 6: Redux Reducer — Initial State
console.log('[Test 6] Redux Reducer: Initial state inspection');
const initialState = productReducer(undefined, { type: '@@INIT' });
assert.equal(Array.isArray(initialState.items), true);
assert.equal(initialState.adminActionLoading, false);
assert.equal(initialState.adminActionError, null);
assert.equal(initialState.adminSuccessMessage, null);
console.log('  ✓ Pass: Redux slice initializes with admin action fields\n');

// Test 7: Redux Reducer — fetchProducts.fulfilled updates catalog items
console.log('[Test 7] Redux Reducer: fetchProducts.fulfilled updates catalog');
const sampleCatalog = [
  { id: 1, name: 'Keyboard', price: 189.99, sku: 'TECH-KB-01', category: 'Electronics', stockQuantity: 50, active: true },
  { id: 2, name: 'Headphones', price: 249.99, sku: 'AUD-HP-02', category: 'Audio', stockQuantity: 30, active: true },
];
const stateWithProducts = productReducer(initialState, {
  type: 'products/fetchProducts/fulfilled',
  payload: {
    content: sampleCatalog,
    pageNumber: 0,
    pageSize: 10,
    totalElements: 2,
    totalPages: 1,
  },
});
assert.equal(stateWithProducts.items.length, 2);
assert.equal(stateWithProducts.pagination.totalElements, 2);
console.log('  ✓ Pass: Catalog populated with 2 active products\n');

// Test 8: Redux Reducer — updateProduct.fulfilled updates item in place
console.log('[Test 8] Redux Reducer: updateProduct.fulfilled mutates product in place');
const updatedProduct = {
  id: 1,
  name: 'Apex Pro Mechanical Keyboard RGB (Updated Edition)',
  price: 199.99,
  sku: 'TECH-KB-01',
  category: 'Electronics',
  stockQuantity: 45,
  active: true,
};
const stateAfterUpdate = productReducer(stateWithProducts, {
  type: 'products/updateProduct/fulfilled',
  payload: updatedProduct,
});
assert.equal(stateAfterUpdate.items[0].name, 'Apex Pro Mechanical Keyboard RGB (Updated Edition)');
assert.equal(stateAfterUpdate.items[0].price, 199.99);
assert.equal(stateAfterUpdate.adminSuccessMessage.includes('updated successfully'), true);
console.log('  ✓ Pass: Product #1 authoritative price and name updated cleanly\n');

// Test 9: Redux Reducer — deleteProduct.fulfilled soft-deletes item from active list
console.log('[Test 9] Redux Reducer: deleteProduct.fulfilled removes deactivated product from active items');
const stateAfterDelete = productReducer(stateAfterUpdate, {
  type: 'products/deleteProduct/fulfilled',
  payload: 1, // product ID 1 deactivated
});
assert.equal(stateAfterDelete.items.length, 1);
assert.equal(stateAfterDelete.items[0].id, 2);
assert.equal(stateAfterDelete.adminSuccessMessage, 'Product deactivated successfully');
console.log('  ✓ Pass: Deactivated product filtered out of active list with confirmation\n');

// Test 10: Redux Reducer — clearAdminFeedback resets alerts
console.log('[Test 10] Redux Reducer: clearAdminFeedback resets alert messages');
const stateCleared = productReducer(stateAfterDelete, clearAdminFeedback());
assert.equal(stateCleared.adminActionError, null);
assert.equal(stateCleared.adminSuccessMessage, null);
console.log('  ✓ Pass: Admin alerts cleared cleanly\n');

console.log('================================================================');
console.log('ALL 10 ADMIN PRODUCT MANAGEMENT TESTS PASSED CLEANLY! (10/10)');
console.log('================================================================');

