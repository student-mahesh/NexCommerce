// frontend/test-cart-logic.js
// Unit tests for Cart Redux slice calculations, state mutations, and quantity limits

import assert from 'node:assert/strict';
import cartReducer, {
  addToCart,
  updateQuantity,
  removeFromCart,
  clearCart,
  selectCartTotal,
  selectCartCount,
} from './src/features/cart/cartSlice.js';

console.log('Running Cart Redux Slice Unit Tests...');

let state = {
  items: [],
  isDrawerOpen: false,
};

// Test 1: Add new product to cart
state = cartReducer(state, addToCart({
  id: 1,
  name: 'Mechanical Keyboard RGB',
  price: 89.99,
  sku: 'TECH-KB-01',
  stockQuantity: 30,
  quantity: 1,
}));

assert.equal(state.items.length, 1, 'Should have 1 item in cart');
assert.equal(state.items[0].id, 1);
assert.equal(state.items[0].quantity, 1);
assert.equal(state.isDrawerOpen, true, 'Drawer should open on adding item');
console.log('✓ Test 1 Passed: addToCart new product');

// Test 2: Add same product increases quantity
state = cartReducer(state, addToCart({
  id: 1,
  name: 'Mechanical Keyboard RGB',
  price: 89.99,
  sku: 'TECH-KB-01',
  stockQuantity: 30,
  quantity: 2,
}));

assert.equal(state.items.length, 1, 'Should still have 1 distinct item');
assert.equal(state.items[0].quantity, 3, 'Quantity should increase to 3');
console.log('✓ Test 2 Passed: addToCart existing product aggregates quantity');

// Test 3: Add second product
state = cartReducer(state, addToCart({
  id: 2,
  name: 'Wireless Gaming Mouse',
  price: 49.99,
  sku: 'TECH-MOUSE-02',
  stockQuantity: 15,
  quantity: 1,
}));

assert.equal(state.items.length, 2, 'Should have 2 distinct items');
console.log('✓ Test 3 Passed: addToCart multiple distinct products');

// Test 4: Verify selectCartTotal calculation
const total = selectCartTotal({ cart: state });
const expectedTotal = 89.99 * 3 + 49.99 * 1; // 269.97 + 49.99 = 319.96
assert.equal(Math.round(total * 100) / 100, Math.round(expectedTotal * 100) / 100);
console.log(`✓ Test 4 Passed: selectCartTotal calculates correct sum: $${total.toFixed(2)}`);

// Test 5: Verify selectCartCount
const count = selectCartCount({ cart: state });
assert.equal(count, 4, 'Total item count should be 3 + 1 = 4');
console.log('✓ Test 5 Passed: selectCartCount calculates total units: 4');

// Test 6: updateQuantity directly
state = cartReducer(state, updateQuantity({ id: 1, quantity: 5 }));
assert.equal(state.items[0].quantity, 5);
console.log('✓ Test 6 Passed: updateQuantity modifies item quantity');

// Test 7: updateQuantity clamp to stock limit
state = cartReducer(state, updateQuantity({ id: 2, quantity: 50 }));
assert.equal(state.items[1].quantity, 15, 'Quantity should be clamped to stockQuantity (15)');
console.log('✓ Test 7 Passed: updateQuantity enforces stock limit clamping');

// Test 8: updateQuantity to 0 removes item
state = cartReducer(state, updateQuantity({ id: 2, quantity: 0 }));
assert.equal(state.items.length, 1, 'Item should be removed when quantity <= 0');
assert.equal(state.items[0].id, 1);
console.log('✓ Test 8 Passed: updateQuantity(0) removes item from cart');

// Test 9: removeFromCart by id
state = cartReducer(state, removeFromCart(1));
assert.equal(state.items.length, 0, 'Cart should be empty after removing remaining item');
console.log('✓ Test 9 Passed: removeFromCart deletes item');

// Test 10: clearCart empties all items
state = cartReducer(state, addToCart({ id: 3, name: 'Item 3', price: 10, stockQuantity: 5 }));
state = cartReducer(state, addToCart({ id: 4, name: 'Item 4', price: 20, stockQuantity: 5 }));
assert.equal(state.items.length, 2);
state = cartReducer(state, clearCart());
assert.equal(state.items.length, 0, 'clearCart should empty all items');
console.log('✓ Test 10 Passed: clearCart empties cart');

console.log('\n===========================================');
console.log('ALL 10 CART UNIT TESTS PASSED SUCCESSFULLY!');
console.log('===========================================');

