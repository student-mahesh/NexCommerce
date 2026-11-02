// Master image mapping and fallback utility
const SKU_IMAGE_MAP = {
  'TECH-KB-01': '/images/products/apex-pro-keyboard.jpg',
  'AUD-HP-02': '/images/products/sonicwave-headphones.jpg',
  'DISP-MON-03': '/images/products/curved-gaming-monitor.jpg',
  'WEAR-SW-04': '/images/products/smartwatch.jpg',
  'TECH-MS-05': '/images/products/wireless-mouse.jpg',
  'AUD-SPK-06': '/images/products/waterproof-speaker.jpg',
  'TECH-CAM-07': '/images/products/hd-webcam.jpg',
  'ACC-CHG-08': '/images/products/charging-station.jpg',
};

export const PLACEHOLDER_IMAGE = '/images/placeholders/product-placeholder.svg';

export const getProductImage = (product) => {
  if (!product) return PLACEHOLDER_IMAGE;
  if (product.sku && SKU_IMAGE_MAP[product.sku]) {
    return SKU_IMAGE_MAP[product.sku];
  }
  if (product.imageUrl && product.imageUrl.trim() !== '') {
    return product.imageUrl;
  }
  return PLACEHOLDER_IMAGE;
};

export const handleImageError = (e) => {
  // Prevent infinite loop if fallback fails
  e.currentTarget.onerror = null;
  e.currentTarget.src = PLACEHOLDER_IMAGE;
};
