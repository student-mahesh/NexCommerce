-- ==============================================================================
-- Initialization Script: Dedicated Schemas for E-Commerce Microservices
-- Ensures Database-Per-Service isolation across all domains.
-- ==============================================================================

CREATE DATABASE IF NOT EXISTS `ecommerce_auth` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `ecommerce_product` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `ecommerce_order` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `ecommerce_inventory` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `ecommerce_payment` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `ecommerce_notification` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant permissions to standard application user
GRANT ALL PRIVILEGES ON `ecommerce_auth`.* TO 'ecom_user'@'%';
GRANT ALL PRIVILEGES ON `ecommerce_product`.* TO 'ecom_user'@'%';
GRANT ALL PRIVILEGES ON `ecommerce_order`.* TO 'ecom_user'@'%';
GRANT ALL PRIVILEGES ON `ecommerce_inventory`.* TO 'ecom_user'@'%';
GRANT ALL PRIVILEGES ON `ecommerce_payment`.* TO 'ecom_user'@'%';
GRANT ALL PRIVILEGES ON `ecommerce_notification`.* TO 'ecom_user'@'%';

FLUSH PRIVILEGES;
