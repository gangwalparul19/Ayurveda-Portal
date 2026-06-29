-- MySQL dump 10.13  Distrib 8.0.41, for Win64 (x86_64)
--
-- Host: localhost    Database: shifa_db
-- ------------------------------------------------------
-- Server version	8.0.41

-- Aiven compatibility: allow tables without primary keys during import
SET SESSION sql_require_primary_key = 0;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `audit_log`
--

DROP TABLE IF EXISTS `audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `details` json DEFAULT NULL,
  `ip_address` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `timestamp` datetime(6) DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_tenant` (`tenant_id`),
  KEY `idx_audit_user` (`user_id`),
  KEY `idx_audit_action` (`action`),
  KEY `idx_audit_timestamp` (`timestamp`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_log`
--

LOCK TABLES `audit_log` WRITE;
/*!40000 ALTER TABLE `audit_log` DISABLE KEYS */;
INSERT INTO `audit_log` VALUES (1,'ORDER_CREATED','{\"orderId\": 4, \"itemCount\": 2, \"orderNumber\": \"ORD-20260628-0001\", \"orderSource\": \"STOREFRONT\", \"totalAmount\": 748.0}',NULL,NULL,'2026-06-28 16:47:16.932330',NULL),(2,'LOGIN_FAILURE','{\"reason\": \"BadCredentialsException\", \"username\": \"admin\"}','0:0:0:0:0:0:0:1',NULL,'2026-06-28 16:55:44.760010',NULL),(3,'LOGIN_SUCCESS','{\"role\": \"SUPER_ADMIN\", \"username\": \"admin\"}','0:0:0:0:0:0:0:1',NULL,'2026-06-28 16:57:48.157002',1),(4,'LOGIN_SUCCESS','{\"role\": \"SUPER_ADMIN\", \"username\": \"admin\"}','0:0:0:0:0:0:0:1',NULL,'2026-06-28 17:28:34.495450',1),(5,'LOGIN_SUCCESS','{\"role\": \"TENANT_ADMIN\", \"username\": \"admin\"}','0:0:0:0:0:0:0:1',NULL,'2026-06-28 17:30:06.789574',1),(6,'LOGIN_SUCCESS','{\"role\": \"TENANT_ADMIN\", \"username\": \"admin\"}','0:0:0:0:0:0:0:1',NULL,'2026-06-28 17:45:23.127193',1),(7,'LOGIN_SUCCESS','{\"role\": \"SUPER_ADMIN\", \"username\": \"admin\"}','0:0:0:0:0:0:0:1',NULL,'2026-06-28 19:05:31.461548',1),(8,'ORDER_STATUS_CHANGED','{\"notes\": \"\", \"orderId\": 1, \"toStatus\": \"CONFIRMED\", \"fromStatus\": \"NEW\", \"orderNumber\": \"ORD-20260627-001\"}',NULL,NULL,'2026-06-28 22:14:08.472746',NULL),(9,'LOGIN_SUCCESS','{\"role\": \"TENANT_ADMIN\", \"username\": \"admin\"}','0:0:0:0:0:0:0:1',NULL,'2026-06-29 10:01:46.048989',1);
/*!40000 ALTER TABLE `audit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `billing_exports`
--

DROP TABLE IF EXISTS `billing_exports`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_exports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `export_type` enum('VYAPAR_CSV','VYAPAR_EXCEL','GST_JSON','CUSTOM') COLLATE utf8mb4_unicode_ci NOT NULL,
  `date_range_start` date DEFAULT NULL,
  `date_range_end` date DEFAULT NULL,
  `file_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `record_count` int DEFAULT '0',
  `generated_by` bigint DEFAULT NULL,
  `generated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_exports_type` (`export_type`),
  KEY `idx_exports_date` (`generated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `billing_exports`
--

LOCK TABLES `billing_exports` WRITE;
/*!40000 ALTER TABLE `billing_exports` DISABLE KEYS */;
/*!40000 ALTER TABLE `billing_exports` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `company_config`
--

DROP TABLE IF EXISTS `company_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `company_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `address` text COLLATE utf8mb4_unicode_ci,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `logo_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gstin` varchar(15) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `low_stock_threshold` int DEFAULT NULL,
  `order_number_prefix` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `default_tax_rate` decimal(5,2) DEFAULT NULL,
  `cgst_rate` decimal(5,2) DEFAULT NULL,
  `sgst_rate` decimal(5,2) DEFAULT NULL,
  `igst_rate` decimal(5,2) DEFAULT NULL,
  `enable_whatsapp_parsing` bit(1) DEFAULT NULL,
  `enable_storefront` bit(1) DEFAULT NULL,
  `default_shipping_charge` decimal(10,2) DEFAULT NULL,
  `minimum_order_value` decimal(10,2) DEFAULT NULL,
  `duplicate_check_days` int DEFAULT NULL,
  `fuzzy_match_threshold` decimal(3,2) DEFAULT NULL,
  `terms_and_conditions` text COLLATE utf8mb4_unicode_ci,
  `bank_details` text COLLATE utf8mb4_unicode_ci,
  `last_updated_by` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `company_config`
--

LOCK TABLES `company_config` WRITE;
/*!40000 ALTER TABLE `company_config` DISABLE KEYS */;
INSERT INTO `company_config` VALUES (1,'Shifa Ayurveda','Not Configured','0000000000','info@ayurveda.com',NULL,NULL,10,'ORD',18.00,9.00,9.00,18.00,_binary '',_binary '',0.00,0.00,7,0.60,NULL,NULL,NULL,'2026-06-28 11:10:08','2026-06-28 11:10:08');
/*!40000 ALTER TABLE `company_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `coupon_usages`
--

DROP TABLE IF EXISTS `coupon_usages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `coupon_usages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `coupon_id` bigint NOT NULL,
  `order_id` bigint DEFAULT NULL,
  `customer_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `used_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_cu_coupon` (`coupon_id`),
  KEY `idx_cu_phone` (`customer_phone`),
  KEY `idx_coupon_usages_coupon` (`coupon_id`),
  KEY `idx_coupon_usages_phone` (`customer_phone`),
  CONSTRAINT `fk_cu_coupon` FOREIGN KEY (`coupon_id`) REFERENCES `coupons` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `coupon_usages`
--

LOCK TABLES `coupon_usages` WRITE;
/*!40000 ALTER TABLE `coupon_usages` DISABLE KEYS */;
/*!40000 ALTER TABLE `coupon_usages` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `coupons`
--

DROP TABLE IF EXISTS `coupons`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `coupons` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `discount_type` enum('FLAT','PERCENT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `discount_value` decimal(10,2) NOT NULL,
  `min_order_amount` decimal(10,2) DEFAULT '0.00',
  `max_discount_amount` decimal(10,2) DEFAULT NULL,
  `usage_limit` int DEFAULT NULL,
  `usage_count` int DEFAULT '0',
  `per_user_limit` int DEFAULT '1',
  `is_active` bit(1) DEFAULT b'1',
  `valid_from` date DEFAULT (curdate()),
  `valid_until` date DEFAULT NULL,
  `applicable_category` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  UNIQUE KEY `idx_coupons_code` (`code`),
  KEY `idx_coupon_code` (`code`),
  KEY `idx_coupon_active` (`is_active`),
  KEY `idx_coupons_active` (`is_active`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `coupons`
--

LOCK TABLES `coupons` WRITE;
/*!40000 ALTER TABLE `coupons` DISABLE KEYS */;
INSERT INTO `coupons` VALUES (1,'WELCOME10','Welcome discount','PERCENT',10.00,300.00,100.00,100,0,1,_binary '','2026-06-29','2027-06-29',NULL,NULL,'2026-06-29 04:40:56','2026-06-29 04:40:56'),(2,'FLAT50','₹50 flat off','FLAT',50.00,500.00,NULL,200,0,1,_binary '','2026-06-29','2027-06-29',NULL,NULL,'2026-06-29 04:40:56','2026-06-29 04:40:56'),(3,'SHIFA20','20% off on orders above ₹800','PERCENT',20.00,800.00,200.00,50,0,1,_binary '','2026-06-29','2027-06-29',NULL,NULL,'2026-06-29 04:40:56','2026-06-29 04:40:56');
/*!40000 ALTER TABLE `coupons` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `customers`
--

DROP TABLE IF EXISTS `customers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_line_1` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_line_2` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `city` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `state` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `pincode` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gstin` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `phone` (`phone`),
  KEY `idx_customers_phone` (`phone`),
  KEY `idx_customers_name` (`name`),
  KEY `idx_customers_city` (`city`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `customers`
--

LOCK TABLES `customers` WRITE;
/*!40000 ALTER TABLE `customers` DISABLE KEYS */;
INSERT INTO `customers` VALUES (1,'xzxzxzxzX','1236547890','dev9.weblithic@gmail.com','dfdff, dfdf',NULL,'fdfdf','dffdfdf','411057',NULL,'2026-06-27 08:21:48','2026-06-27 08:21:48'),(2,'Test Customer 2','8888888888','test2@example.com','456 Test Road',NULL,'Delhi','Delhi','110001',NULL,'2026-06-27 08:26:58','2026-06-27 08:26:58'),(3,'zxczxczxc','8103276050','dev9.weblithic@gmail.com','czxc, czxczxc',NULL,'cxzxc','cxczxc','411057',NULL,'2026-06-27 08:28:10','2026-06-27 08:28:10'),(4,'Parul Gangwal','08103276050','gangwalparul19@gmail.com','431, Goyal nagar, Indore',NULL,'INDORE','Madhya Pradesh','452001',NULL,'2026-06-28 11:17:16','2026-06-28 11:17:16'),(21,'Ravi Sharma','9820012301','ravi@shifademo.test','12 Marine Drive','Near Charni Road','Mumbai','Maharashtra','400001',NULL,'2026-06-28 15:15:24','2026-06-28 15:15:24'),(22,'Priya Nair','9845012302','priya@shifademo.test','45 MG Road','Brigade Towers','Bengaluru','Karnataka','560001',NULL,'2026-06-28 15:15:24','2026-06-28 15:15:24'),(23,'Amit Verma','9811012303','amit@shifademo.test','8 Connaught Place','Block A','New Delhi','Delhi','110001',NULL,'2026-06-28 15:15:24','2026-06-28 15:15:24'),(24,'Lakshmi Iyer','9840012304','lakshmi@shifademo.test','23 Anna Salai','T Nagar','Chennai','Tamil Nadu','600001',NULL,'2026-06-28 15:15:24','2026-06-28 15:15:24'),(25,'Kiran Patel','9824012305','kiran@shifademo.test','56 CG Road','Navrangpura','Ahmedabad','Gujarat','380001',NULL,'2026-06-28 15:15:24','2026-06-28 15:15:24'),(26,'Sunita Joshi','9821012306','sunita@shifademo.test','9 FC Road','Shivajinagar','Pune','Maharashtra','411001',NULL,'2026-06-28 15:15:24','2026-06-28 15:15:24'),(27,'Manoj Reddy','9849012307','manoj@shifademo.test','101 Indiranagar','100ft Road','Bengaluru','Karnataka','560002',NULL,'2026-06-28 15:15:24','2026-06-28 15:15:24'),(28,'Deepa Menon','9847012308','deepa@shifademo.test','34 Adyar Main Road','Besant Nagar','Chennai','Tamil Nadu','600002',NULL,'2026-06-28 15:15:24','2026-06-28 15:15:24');
/*!40000 ALTER TABLE `customers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `dispatch_labels`
--

DROP TABLE IF EXISTS `dispatch_labels`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dispatch_labels` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `batch_id` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `courier_partner` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tracking_number` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `label_pdf_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `weight_grams` decimal(10,2) DEFAULT NULL,
  `dimensions` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('GENERATED','PRINTED','SHIPPED','DELIVERED') COLLATE utf8mb4_unicode_ci DEFAULT 'GENERATED',
  `generated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_labels_order` (`order_id`),
  KEY `idx_labels_batch` (`batch_id`),
  KEY `idx_labels_tracking` (`tracking_number`),
  KEY `idx_labels_status` (`status`),
  CONSTRAINT `fk_labels_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `dispatch_labels`
--

LOCK TABLES `dispatch_labels` WRITE;
/*!40000 ALTER TABLE `dispatch_labels` DISABLE KEYS */;
INSERT INTO `dispatch_labels` VALUES (2,43,'BATCH-6763C8FC','Default','TRK-F9E0E292AC17',NULL,500.00,NULL,'SHIPPED','2026-06-28 16:43:32');
/*!40000 ALTER TABLE `dispatch_labels` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `order_items`
--

DROP TABLE IF EXISTS `order_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `product_id` bigint DEFAULT NULL,
  `product_name_snapshot` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sku_snapshot` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `quantity` int NOT NULL,
  `unit_price` decimal(10,2) NOT NULL,
  `mrp_snapshot` decimal(10,2) DEFAULT NULL,
  `discount` decimal(10,2) DEFAULT '0.00',
  `tax_amount` decimal(10,2) DEFAULT '0.00',
  `line_total` decimal(12,2) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_items_order` (`order_id`),
  KEY `idx_items_product` (`product_id`),
  CONSTRAINT `fk_items_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_items_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=93 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `order_items`
--

LOCK TABLES `order_items` WRITE;
/*!40000 ALTER TABLE `order_items` DISABLE KEYS */;
INSERT INTO `order_items` VALUES (1,1,10,'Aloe Vera Juice','SHIFA-010',2,289.00,325.00,0.00,0.00,578.00),(2,2,1,'Ashwagandha Capsules','SHIFA-001',1,499.00,599.00,0.00,0.00,499.00),(3,3,10,'Aloe Vera Juice','SHIFA-010',2,289.00,325.00,0.00,0.00,578.00),(4,3,3,'Chyawanprash','SHIFA-003',1,399.00,450.00,0.00,0.00,399.00),(5,4,1,'Ashwagandha Capsules','SHIFA-001',1,499.00,599.00,0.00,0.00,499.00),(6,4,2,'Triphala Powder','SHIFA-002',1,249.00,299.00,0.00,0.00,249.00),(63,35,1,'Ashwagandha Capsules','SHIFA-001',2,499.00,599.00,0.00,0.00,998.00),(64,35,5,'Tulsi Drops','SHIFA-005',1,169.00,199.00,0.00,0.00,169.00),(65,36,3,'Chyawanprash','SHIFA-003',1,399.00,450.00,0.00,0.00,399.00),(66,36,6,'Amla Candy','SHIFA-006',2,129.00,150.00,0.00,0.00,258.00),(67,37,2,'Triphala Powder','SHIFA-002',3,249.00,299.00,0.00,0.00,747.00),(68,37,8,'Neem Capsules','SHIFA-008',1,349.00,399.00,0.00,0.00,349.00),(69,38,13,'Arjuna Tea','SHIFA-013',2,199.00,225.00,0.00,0.00,398.00),(70,38,18,'Haritaki Powder','SHIFA-018',1,219.00,249.00,0.00,0.00,219.00),(71,39,4,'Giloy Juice','SHIFA-004',2,299.00,350.00,0.00,0.00,598.00),(72,39,5,'Tulsi Drops','SHIFA-005',2,169.00,199.00,0.00,0.00,338.00),(73,40,1,'Ashwagandha Capsules','SHIFA-001',1,499.00,599.00,0.00,0.00,499.00),(74,40,8,'Neem Capsules','SHIFA-008',1,349.00,399.00,0.00,0.00,349.00),(75,41,11,'Shilajit Resin','SHIFA-011',1,849.00,999.00,0.00,0.00,849.00),(76,41,15,'Trikatu Churna','SHIFA-015',1,159.00,189.00,0.00,0.00,159.00),(77,42,6,'Amla Candy','SHIFA-006',1,129.00,150.00,0.00,0.00,129.00),(78,42,17,'Haridra Capsules','SHIFA-017',1,399.00,449.00,0.00,0.00,399.00),(79,42,13,'Arjuna Tea','SHIFA-013',1,199.00,225.00,0.00,0.00,199.00),(80,43,12,'Moringa Powder','SHIFA-012',3,329.00,375.00,0.00,0.00,987.00),(81,44,16,'Shatavari Capsules','SHIFA-016',1,479.00,549.00,0.00,0.00,479.00),(82,44,14,'Kumkumadi Oil','SHIFA-014',1,799.00,899.00,0.00,0.00,799.00),(83,45,7,'Brahmi Syrup','SHIFA-007',2,249.00,275.00,0.00,0.00,498.00),(84,45,8,'Neem Capsules','SHIFA-008',2,349.00,399.00,0.00,0.00,698.00),(85,46,1,'Ashwagandha Capsules','SHIFA-001',1,499.00,599.00,0.00,0.00,499.00),(86,46,3,'Chyawanprash','SHIFA-003',1,399.00,450.00,0.00,0.00,399.00),(87,47,9,'Karela Jamun Juice','SHIFA-009',1,375.00,425.00,0.00,0.00,375.00),(88,47,10,'Aloe Vera Juice','SHIFA-010',1,289.00,325.00,0.00,0.00,289.00),(89,48,5,'Tulsi Drops','SHIFA-005',1,169.00,199.00,0.00,0.00,169.00),(90,49,4,'Giloy Juice','SHIFA-004',1,299.00,350.00,0.00,0.00,299.00),(91,49,6,'Amla Candy','SHIFA-006',1,129.00,150.00,0.00,0.00,129.00),(92,49,12,'Moringa Powder','SHIFA-012',1,329.00,375.00,0.00,0.00,329.00);
/*!40000 ALTER TABLE `order_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `order_status_history`
--

DROP TABLE IF EXISTS `order_status_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_status_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `from_status` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `to_status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `changed_by` bigint DEFAULT NULL,
  `notes` text COLLATE utf8mb4_unicode_ci,
  `changed_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_history_order` (`order_id`),
  KEY `idx_history_date` (`changed_at`),
  CONSTRAINT `fk_history_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=102 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `order_status_history`
--

LOCK TABLES `order_status_history` WRITE;
/*!40000 ALTER TABLE `order_status_history` DISABLE KEYS */;
INSERT INTO `order_status_history` VALUES (1,1,NULL,'NEW',NULL,'Order placed via storefront','2026-06-27 08:21:48'),(2,2,NULL,'NEW',NULL,'Order placed via storefront','2026-06-27 08:26:59'),(3,3,NULL,'NEW',NULL,'Order placed via storefront','2026-06-27 08:28:10'),(4,4,NULL,'NEW',NULL,'Order placed via storefront','2026-06-28 11:17:17'),(69,35,NULL,'NEW',1,'[DEMOSEED] created','2026-06-03 04:30:00'),(70,35,'NEW','DELIVERED',1,'[DEMOSEED] fulfilled','2026-06-05 05:30:00'),(71,36,NULL,'NEW',1,'[DEMOSEED] created','2026-06-07 04:00:00'),(72,36,'NEW','DELIVERED',1,'[DEMOSEED] fulfilled','2026-06-09 06:30:00'),(73,37,NULL,'NEW',1,'[DEMOSEED] created','2026-06-10 03:15:00'),(74,37,'NEW','DELIVERED',1,'[DEMOSEED] fulfilled','2026-06-13 07:30:00'),(75,38,NULL,'NEW',1,'[DEMOSEED] created','2026-06-28 02:30:00'),(76,38,'NEW','DELIVERED',1,'[DEMOSEED] fulfilled','2026-06-28 09:30:00'),(77,39,NULL,'NEW',1,'[DEMOSEED] created','2026-06-28 03:45:00'),(78,39,'NEW','DELIVERED',1,'[DEMOSEED] fulfilled','2026-06-28 11:00:00'),(79,40,NULL,'NEW',1,'[DEMOSEED] created','2026-06-28 05:30:00'),(80,41,NULL,'NEW',1,'[DEMOSEED] created','2026-06-28 06:00:00'),(81,41,'NEW','CONFIRMED',1,'[DEMOSEED] confirmed','2026-06-28 06:30:00'),(82,42,NULL,'NEW',1,'[DEMOSEED] created','2026-06-28 07:00:00'),(83,43,NULL,'NEW',1,'[DEMOSEED] created','2026-06-14 04:30:00'),(84,43,'NEW','PAID',1,'[DEMOSEED] payment received','2026-06-14 07:30:00'),(85,44,NULL,'NEW',1,'[DEMOSEED] created','2026-06-28 04:15:00'),(86,44,'NEW','PAID',1,'[DEMOSEED] payment received','2026-06-28 05:30:00'),(87,45,NULL,'NEW',1,'[DEMOSEED] created','2026-06-14 03:30:00'),(88,45,'NEW','PAID',1,'[DEMOSEED] payment received','2026-06-14 08:30:00'),(89,45,'PAID','PACKED',1,'[DEMOSEED] packed','2026-06-14 11:30:00'),(90,46,NULL,'NEW',1,'[DEMOSEED] created','2026-06-28 03:00:00'),(91,46,'NEW','PAID',1,'[DEMOSEED] payment received','2026-06-28 05:00:00'),(92,46,'PAID','PACKED',1,'[DEMOSEED] packed','2026-06-28 07:30:00'),(93,47,NULL,'NEW',1,'[DEMOSEED] created','2026-06-07 03:30:00'),(94,47,'NEW','PAID',1,'[DEMOSEED] payment received','2026-06-07 06:30:00'),(95,47,'PAID','PACKED',1,'[DEMOSEED] packed','2026-06-07 10:30:00'),(96,47,'PACKED','DISPATCHED',1,'[DEMOSEED] dispatched','2026-06-08 04:30:00'),(97,48,NULL,'NEW',1,'[DEMOSEED] created','2026-06-10 04:30:00'),(98,48,'NEW','CANCELLED',1,'[DEMOSEED] cancelled','2026-06-10 05:30:00'),(99,49,NULL,'NEW',1,'[DEMOSEED] created','2026-05-19 03:30:00'),(100,49,'NEW','DELIVERED',1,'[DEMOSEED] fulfilled','2026-05-22 08:30:00'),(101,1,'NEW','CONFIRMED',NULL,'','2026-06-28 16:44:09');
/*!40000 ALTER TABLE `order_status_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_number` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `customer_id` bigint DEFAULT NULL,
  `salesperson_id` bigint DEFAULT NULL,
  `order_source` enum('WHATSAPP','MANUAL','STOREFRONT','API') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MANUAL',
  `raw_whatsapp_text` text COLLATE utf8mb4_unicode_ci,
  `status` enum('NEW','CONFIRMED','PAID','PACKED','DISPATCHED','DELIVERED','CANCELLED','RETURNED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NEW',
  `subtotal` decimal(12,2) DEFAULT '0.00',
  `discount_amount` decimal(10,2) DEFAULT '0.00',
  `tax_amount` decimal(10,2) DEFAULT '0.00',
  `shipping_charge` decimal(10,2) DEFAULT '0.00',
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `payment_mode` enum('COD','UPI','BANK_TRANSFER','ONLINE','CREDIT') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payment_status` enum('PENDING','PARTIAL','PAID','REFUNDED') COLLATE utf8mb4_unicode_ci DEFAULT 'PENDING',
  `notes` text COLLATE utf8mb4_unicode_ci,
  `order_date` date NOT NULL,
  `dispatched_at` timestamp NULL DEFAULT NULL,
  `delivered_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `coupon_id` bigint DEFAULT NULL,
  `coupon_discount` decimal(10,2) DEFAULT '0.00',
  `razorpay_order_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `razorpay_payment_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `order_number` (`order_number`),
  UNIQUE KEY `idx_orders_number` (`order_number`),
  KEY `idx_orders_status` (`status`),
  KEY `idx_orders_date` (`order_date`),
  KEY `idx_orders_customer` (`customer_id`),
  KEY `idx_orders_salesperson` (`salesperson_id`),
  KEY `idx_orders_source` (`order_source`),
  KEY `idx_orders_payment` (`payment_status`),
  KEY `idx_orders_status_date` (`status`,`order_date`),
  KEY `idx_orders_customer_date` (`customer_id`,`order_date`),
  KEY `idx_orders_salesperson_date` (`salesperson_id`,`order_date`),
  CONSTRAINT `fk_orders_customer` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `orders`
--

LOCK TABLES `orders` WRITE;
/*!40000 ALTER TABLE `orders` DISABLE KEYS */;
INSERT INTO `orders` VALUES (1,'ORD-20260627-001',1,NULL,'STOREFRONT',NULL,'CONFIRMED',578.00,0.00,0.00,0.00,578.00,'COD','PENDING','Delivery Address: dfdff, dfdf, fdfdf, dffdfdf, 411057\n\nCustomer Notes: dfdsfdsf','2026-06-27',NULL,NULL,'2026-06-27 08:21:48','2026-06-28 16:44:09',NULL,0.00,NULL,NULL),(2,'ORD-20260627-002',2,NULL,'STOREFRONT',NULL,'NEW',499.00,0.00,0.00,0.00,499.00,'COD','PENDING','Delivery Address: 456 Test Road, Delhi, Delhi, 110001','2026-06-27',NULL,NULL,'2026-06-27 08:26:59','2026-06-27 08:26:59',NULL,0.00,NULL,NULL),(3,'ORD-20260627-003',3,NULL,'STOREFRONT',NULL,'NEW',977.00,0.00,0.00,0.00,977.00,'COD','PENDING','Delivery Address: czxc, czxczxc, cxzxc, cxczxc, 411057\n\nCustomer Notes: czxczxc','2026-06-27',NULL,NULL,'2026-06-27 08:28:10','2026-06-27 08:28:10',NULL,0.00,NULL,NULL),(4,'ORD-20260628-0001',4,NULL,'STOREFRONT',NULL,'NEW',748.00,0.00,0.00,0.00,748.00,'COD','PENDING','Delivery Address: 431, Goyal nagar, Indore, INDORE, Madhya Pradesh, 452001','2026-06-28',NULL,NULL,'2026-06-28 11:17:16','2026-06-28 11:17:16',NULL,0.00,NULL,NULL),(35,'ORD-20260603-0001',21,7,'MANUAL',NULL,'DELIVERED',1167.00,0.00,58.35,50.00,1275.35,'UPI','PAID','[DEMOSEED] Delivered this month','2026-06-03','2026-06-03 08:30:00','2026-06-05 05:30:00','2026-06-28 15:15:24','2026-06-28 15:15:24',NULL,0.00,NULL,NULL),(36,'ORD-20260607-0002',22,8,'WHATSAPP',NULL,'DELIVERED',657.00,50.00,32.85,50.00,689.85,'COD','PAID','[DEMOSEED] Delivered this month','2026-06-07','2026-06-07 09:30:00','2026-06-09 06:30:00','2026-06-28 15:15:24','2026-06-28 15:15:24',NULL,0.00,NULL,NULL),(37,'ORD-20260610-0003',23,9,'STOREFRONT',NULL,'DELIVERED',1096.00,0.00,54.80,0.00,1150.80,'ONLINE','PAID','[DEMOSEED] Delivered this month','2026-06-10','2026-06-10 10:30:00','2026-06-13 07:30:00','2026-06-28 15:15:24','2026-06-28 15:15:24',NULL,0.00,NULL,NULL),(38,'ORD-20260628-0004',24,7,'MANUAL',NULL,'DELIVERED',617.00,0.00,30.85,50.00,697.85,'UPI','PAID','[DEMOSEED] Delivered today','2026-06-28','2026-06-28 03:30:00','2026-06-28 09:30:00','2026-06-28 15:15:24','2026-06-28 15:15:24',NULL,0.00,NULL,NULL),(39,'ORD-20260628-0005',25,8,'WHATSAPP',NULL,'DELIVERED',936.00,0.00,46.80,40.00,1022.80,'ONLINE','PAID','[DEMOSEED] Delivered today','2026-06-28','2026-06-28 04:30:00','2026-06-28 11:00:00','2026-06-28 15:15:24','2026-06-28 15:15:24',NULL,0.00,NULL,NULL),(40,'ORD-20260628-0006',26,9,'WHATSAPP',NULL,'NEW',848.00,0.00,42.40,50.00,940.40,'COD','PENDING','[DEMOSEED] New order today','2026-06-28',NULL,NULL,'2026-06-28 15:15:24','2026-06-28 15:15:24',NULL,0.00,NULL,NULL),(41,'ORD-20260628-0007',27,7,'MANUAL',NULL,'CONFIRMED',1008.00,0.00,50.40,50.00,1108.40,'UPI','PENDING','[DEMOSEED] Confirmed today','2026-06-28',NULL,NULL,'2026-06-28 15:15:25','2026-06-28 15:15:25',NULL,0.00,NULL,NULL),(42,'ORD-20260628-0008',28,NULL,'STOREFRONT',NULL,'NEW',727.00,0.00,36.35,50.00,813.35,'COD','PENDING','[DEMOSEED] New order today','2026-06-28',NULL,NULL,'2026-06-28 15:15:25','2026-06-28 15:15:25',NULL,0.00,NULL,NULL),(43,'ORD-20260614-0009',21,8,'MANUAL',NULL,'DISPATCHED',987.00,47.00,49.35,0.00,989.35,'UPI','PAID','[DEMOSEED] Paid - awaiting packing','2026-06-14','2026-06-28 16:43:46',NULL,'2026-06-28 15:15:25','2026-06-28 16:43:46',NULL,0.00,NULL,NULL),(44,'ORD-20260628-0010',22,9,'WHATSAPP',NULL,'DISPATCHED',1278.00,0.00,63.90,50.00,1391.90,'ONLINE','PAID','[DEMOSEED] Paid today - awaiting packing','2026-06-28','2026-06-28 16:43:46',NULL,'2026-06-28 15:15:25','2026-06-28 16:43:46',NULL,0.00,NULL,NULL),(45,'ORD-20260614-0011',23,7,'MANUAL',NULL,'PACKED',1196.00,20.00,59.80,0.00,1235.80,'UPI','PAID','[DEMOSEED] Packed - ready to dispatch','2026-06-14',NULL,NULL,'2026-06-28 15:15:25','2026-06-28 15:15:25',NULL,0.00,NULL,NULL),(46,'ORD-20260628-0012',24,8,'STOREFRONT',NULL,'PACKED',898.00,0.00,44.90,50.00,992.90,'ONLINE','PAID','[DEMOSEED] Packed today - ready to dispatch','2026-06-28',NULL,NULL,'2026-06-28 15:15:25','2026-06-28 15:15:25',NULL,0.00,NULL,NULL),(47,'ORD-20260607-0013',25,9,'WHATSAPP',NULL,'DISPATCHED',664.00,0.00,33.20,50.00,747.20,'UPI','PAID','[DEMOSEED] In transit','2026-06-07','2026-06-08 04:30:00',NULL,'2026-06-28 15:15:25','2026-06-28 15:15:25',NULL,0.00,NULL,NULL),(48,'ORD-20260610-0014',26,NULL,'WHATSAPP',NULL,'CANCELLED',169.00,0.00,8.45,50.00,227.45,'COD','PENDING','[DEMOSEED] Cancelled by customer','2026-06-10',NULL,NULL,'2026-06-28 15:15:25','2026-06-28 15:15:25',NULL,0.00,NULL,NULL),(49,'ORD-20260519-0015',27,7,'MANUAL',NULL,'DELIVERED',757.00,0.00,37.85,50.00,844.85,'COD','PAID','[DEMOSEED] Delivered previous month','2026-05-19','2026-05-20 04:30:00','2026-05-22 08:30:00','2026-06-28 15:15:25','2026-06-28 15:15:25',NULL,0.00,NULL,NULL);
/*!40000 ALTER TABLE `orders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `payment_records`
--

DROP TABLE IF EXISTS `payment_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(10,2) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `notes` text COLLATE utf8mb4_unicode_ci,
  `payment_date` datetime(6) NOT NULL,
  `payment_mode` enum('BANK_TRANSFER','COD','CREDIT','ONLINE','UPI') COLLATE utf8mb4_unicode_ci NOT NULL,
  `recorded_by` bigint NOT NULL,
  `transaction_reference` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `order_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_payments_order` (`order_id`),
  KEY `idx_payments_date` (`payment_date`),
  CONSTRAINT `FKcqu2d790mp868g1ogc678ygaj` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `payment_records`
--

LOCK TABLES `payment_records` WRITE;
/*!40000 ALTER TABLE `payment_records` DISABLE KEYS */;
INSERT INTO `payment_records` VALUES (23,1275.35,NULL,'[DEMOSEED]','2026-06-03 14:05:00.000000','UPI',1,'TXN-DEMO-0001',35),(24,689.85,NULL,'[DEMOSEED]','2026-06-09 12:00:00.000000','COD',1,'TXN-DEMO-0002',36),(25,1150.80,NULL,'[DEMOSEED]','2026-06-10 16:05:00.000000','ONLINE',1,'TXN-DEMO-0003',37),(26,697.85,NULL,'[DEMOSEED]','2026-06-28 09:05:00.000000','UPI',1,'TXN-DEMO-0004',38),(27,1022.80,NULL,'[DEMOSEED]','2026-06-28 10:05:00.000000','ONLINE',1,'TXN-DEMO-0005',39),(28,989.35,NULL,'[DEMOSEED]','2026-06-14 13:00:00.000000','UPI',1,'TXN-DEMO-0009',43),(29,1391.90,NULL,'[DEMOSEED]','2026-06-28 11:00:00.000000','ONLINE',1,'TXN-DEMO-0010',44),(30,1235.80,NULL,'[DEMOSEED]','2026-06-14 14:00:00.000000','UPI',1,'TXN-DEMO-0011',45),(31,992.90,NULL,'[DEMOSEED]','2026-06-28 10:30:00.000000','ONLINE',1,'TXN-DEMO-0012',46),(32,747.20,NULL,'[DEMOSEED]','2026-06-07 12:00:00.000000','UPI',1,'TXN-DEMO-0013',47),(33,844.85,NULL,'[DEMOSEED]','2026-05-22 14:00:00.000000','COD',1,'TXN-DEMO-0015',49);
/*!40000 ALTER TABLE `payment_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `platform_users`
--

DROP TABLE IF EXISTS `platform_users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `platform_users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `full_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `last_login_at` datetime(6) DEFAULT NULL,
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role` enum('ACCOUNTANT','DISPATCHER','MANAGER','SALESPERSON','SUPER_ADMIN','TENANT_ADMIN') COLLATE utf8mb4_unicode_ci NOT NULL,
  `username` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK9p4hqt7xpd0abiqnt1xhg9rxs` (`email`),
  UNIQUE KEY `UK80ldwbbdqu6emyyjgp04la2y1` (`username`),
  KEY `FKqp0kguip5moymt5fae85l3kuv` (`role_id`),
  KEY `FKbsna27ou4qebvsr6ilpkqp036` (`tenant_id`),
  CONSTRAINT `FKbsna27ou4qebvsr6ilpkqp036` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`),
  CONSTRAINT `FKqp0kguip5moymt5fae85l3kuv` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `platform_users`
--

LOCK TABLES `platform_users` WRITE;
/*!40000 ALTER TABLE `platform_users` DISABLE KEYS */;
INSERT INTO `platform_users` VALUES (1,NULL,'admin@shifa.local','Administrator',_binary '','2026-06-29 10:01:45.651682','$2a$12$Xgu4cp5MFLQXRJgRTBVfouoIPsBqBqd2TmlhVlDe1A1rzSM0/1D3y',NULL,'TENANT_ADMIN','admin',NULL,NULL);
/*!40000 ALTER TABLE `platform_users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_reviews`
--

DROP TABLE IF EXISTS `product_reviews`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_reviews` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `customer_id` bigint DEFAULT NULL,
  `storefront_user_id` bigint DEFAULT NULL,
  `reviewer_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `rating` int NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `review_text` text COLLATE utf8mb4_unicode_ci,
  `is_verified_purchase` bit(1) DEFAULT b'0',
  `is_approved` bit(1) DEFAULT b'0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_reviews_customer` (`customer_id`),
  KEY `fk_reviews_sf_user` (`storefront_user_id`),
  KEY `idx_reviews_product` (`product_id`),
  KEY `idx_reviews_approved` (`is_approved`),
  CONSTRAINT `fk_reviews_customer` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_reviews_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_reviews_sf_user` FOREIGN KEY (`storefront_user_id`) REFERENCES `storefront_users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `product_reviews_chk_1` CHECK ((`rating` between 1 and 5))
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_reviews`
--

LOCK TABLES `product_reviews` WRITE;
/*!40000 ALTER TABLE `product_reviews` DISABLE KEYS */;
INSERT INTO `product_reviews` VALUES (1,1,NULL,NULL,'Ramesh Patel',5,'Amazing energy booster!','I have been taking Ashwagandha capsules for 3 months now and the difference in my energy levels is remarkable. My stress levels have reduced significantly and I sleep much better. Highly recommend this authentic Ayurvedic product.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(2,1,NULL,NULL,'Sunita Sharma',5,'Best Ashwagandha I have tried','Pure and potent. I noticed improvement in my stamina within 2 weeks. The capsules are easy to swallow and have no bitter aftertaste. Will definitely reorder.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(3,1,NULL,NULL,'Vikram Nair',4,'Good quality, helps with stress','Using this for anxiety management. It has helped me feel calmer and more focused at work. Natural and effective, true to Ayurvedic tradition.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(4,2,NULL,NULL,'Meera Joshi',5,'Excellent digestive cleanse','Triphala has transformed my digestion. I take one teaspoon with warm water every night and my gut health has improved tremendously. This is the finest quality powder I have found.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(5,2,NULL,NULL,'Anil Gupta',4,'Authentic Ayurvedic formula','The powder mixes well and the quality is clearly superior. My digestion issues of years are slowly resolving. Consistent use makes all the difference.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(6,2,NULL,NULL,'Priya Krishnan',5,'My daily wellness ritual','I have made Triphala a part of my morning routine. Wonderful detox effect and my skin has started glowing. Pure Ayurvedic goodness in every spoon.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(7,3,NULL,NULL,'Deepa Menon',5,'Best immunity booster for the family','The whole family loves this Chyawanprash. Rich in Amla and packed with Ayurvedic herbs. Since we started having it daily, our seasonal illnesses have reduced noticeably. Excellent product!',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(8,3,NULL,NULL,'Sanjay Tiwari',5,'Traditional recipe, authentic taste','This is exactly how Chyawanprash should taste — rich, herbal, slightly spicy. My children eat it willingly which says a lot. Great for building ojas and vitality.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(9,3,NULL,NULL,'Lalita Devi',4,'Helps with strength and stamina','I am 58 years old and have been using Chyawanprash for decades. This one is among the best I have tried. Noticeable improvement in energy levels within a month.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(10,4,NULL,NULL,'Harish Verma',5,'Incredible immune support','Started taking Giloy juice after a bout of dengue fever on my doctor\'s advice. Recovery was faster and my platelet count stabilised quickly. This juice is pure gold for immunity.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(11,4,NULL,NULL,'Kavita Singh',4,'Good quality, bitter but effective','Giloy is naturally bitter but that is a sign of its potency. I dilute it with a little water and drink it every morning. My chronic fever spells have completely stopped.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(12,5,NULL,NULL,'Ananya Bose',5,'Holy Basil in a bottle','A few drops in water every morning keeps colds and coughs away. I have been using these Tulsi drops for a year and my respiratory health has improved greatly. Pure and potent concentrate.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(13,5,NULL,NULL,'Mohan Rao',5,'Great for throat and lungs','As a singer I rely on Tulsi for vocal health. These drops are highly concentrated and very effective. I add them to warm honey water for best results. Superb product.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(14,5,NULL,NULL,'Geeta Pillai',4,'Natural antibiotic from the garden','I prefer Tulsi over synthetic medicines for mild infections. These drops are convenient and travel-friendly. Works great for the whole family, children included.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(15,6,NULL,NULL,'Rekha Agarwal',5,'Tasty and nutritious snack','My children snack on these Amla candies instead of chocolates now. Packed with Vitamin C and the taste is delightful. Great way to consume Amla without the sourness.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(16,6,NULL,NULL,'Dinesh Kumar',4,'Good for hair and immunity','I eat two Amla candies daily and have noticed my hair fall has reduced. Amla is a superfood and this is a delicious way to consume it. Good quality product.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(17,7,NULL,NULL,'Sudha Iyer',5,'Memory and focus booster','My teenage son has been taking Brahmi syrup during exam season. His concentration has improved noticeably and he is less anxious. A wonderful brain tonic from Ayurveda.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(18,7,NULL,NULL,'Rajendra Mishra',5,'Ancient wisdom in modern form','Brahmi has been used for centuries to enhance cognitive function. This syrup is well-formulated, tastes good, and my memory has genuinely improved over 2 months of use.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(19,7,NULL,NULL,'Usha Rani',4,'Helps with anxiety and sleep','I started Brahmi for stress management. Sleep quality has improved and I feel calmer throughout the day. Natural and safe with no side effects noticed.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(20,8,NULL,NULL,'Pallavi Desai',5,'Cleared my skin in 6 weeks','I struggled with acne for years. Started Neem capsules and combined with a healthy diet, my skin has cleared dramatically. Neem is nature\'s best blood purifier.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(21,8,NULL,NULL,'Kiran Naik',4,'Great for blood sugar support','My doctor suggested Neem as a complementary support for my blood sugar levels. After 3 months the readings have improved. Good quality capsules with no fillers.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(22,9,NULL,NULL,'Santosh Patil',5,'Excellent for diabetes management','Both Karela and Jamun are known for managing blood sugar naturally. This combination juice has helped me keep my levels stable along with diet and exercise. Authentic and effective.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(23,9,NULL,NULL,'Vasantha Rajan',4,'Bitter but very beneficial','The taste takes getting used to, but the benefits are real. My fasting sugar has dropped noticeably. I drink a small glass every morning on an empty stomach.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(24,9,NULL,NULL,'Madhuri Kaul',5,'My father swears by this juice','My diabetic father has been using this juice for 4 months. His HbA1c improved and the doctor reduced his medication dosage. We are genuinely impressed with this product.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(25,10,NULL,NULL,'Divya Kapoor',5,'Skin glow from inside out','I drink Aloe Vera juice every morning and the results on my skin are visible. It also helps with acidity which I used to suffer from daily. Pure, fresh tasting juice.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(26,10,NULL,NULL,'Rahul Mehta',4,'Good for gut health','Aloe Vera juice has helped calm my irritable bowel symptoms. The quality is fresh and it does not have that artificial flavour some brands add. Will keep ordering.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(27,11,NULL,NULL,'Arjun Thakur',5,'The king of Ayurvedic supplements','Authentic Himalayan Shilajit. Dissolves in warm milk perfectly. After 6 weeks I feel a noticeable improvement in strength, vitality, and overall energy. This is the real deal.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(28,11,NULL,NULL,'Ravi Shastri',5,'Genuine product, incredible results','I have tried many brands but this Shilajit resin stands apart in quality and effectiveness. Rich mineral content, pure tar-like consistency — exactly as it should be.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(29,11,NULL,NULL,'Nisha Bhatt',4,'Great for energy and focus','My husband started taking Shilajit for fatigue. The difference in his energy levels within 3 weeks was remarkable. We are now regular customers.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(30,12,NULL,NULL,'Seema Jain',5,'Superfood that actually delivers','I add Moringa powder to my morning smoothie. The nutritional profile is incredible — iron, calcium, vitamins. My energy levels and haemoglobin have both improved. Brilliant product.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(31,12,NULL,NULL,'Tarun Saxena',4,'Pure and fine quality powder','No clumping, fine texture, and a pleasant mild flavour. Moringa has helped with my post-workout recovery. Goes well in soups and shakes. Very versatile supplement.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(32,13,NULL,NULL,'Savita Yadav',5,'Heart tonic in a cup','My cardiologist mentioned Arjuna as a complementary support for heart health. I brew this tea every day. My blood pressure readings have stabilised and I feel more energetic.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(33,13,NULL,NULL,'Prakash Nambiar',4,'Earthy, pleasant herbal tea','Great taste for a medicinal tea. I drink it twice daily with a little honey. The calming effect on the heart and mind is perceptible. Good packaging and freshness.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(34,13,NULL,NULL,'Kamala Reddy',5,'Best herbal tea for seniors','I am 65 and this Arjuna tea has become my evening ritual. It helps with mild chest discomfort and my overall cardiac wellness. Wonderful Ayurvedic product.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(35,14,NULL,NULL,'Pooja Malhotra',5,'Luxury skin oil, natural glow','Kumkumadi oil is a game changer for my skin. I apply two drops every night and my complexion has brightened significantly. The texture is light and absorbs well. Worth every rupee.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(36,14,NULL,NULL,'Shweta Khanna',5,'Faded my dark spots noticeably','Been using Kumkumadi oil for 8 weeks. My dark spots and uneven skin tone have visibly improved. The saffron and sandalwood fragrance is divine. Highly recommend!',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(37,14,NULL,NULL,'Mamta Sood',4,'Traditional recipe, excellent quality','Authentic Kumkumadi formulation with real saffron. A little goes a long way. Skin feels nourished and supple in the morning. Best face oil I have used.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(38,15,NULL,NULL,'Bharat Lal',5,'Fixed my sluggish digestion','Trikatu churna with warm water before meals has completely transformed my digestion. No more bloating or heaviness after food. A simple, powerful Ayurvedic formula.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(39,15,NULL,NULL,'Chameli Devi',4,'Excellent for metabolism','I have been using Trikatu for weight management support. My metabolism feels faster and the cold sensitivity I used to have has reduced. Pungent but effective.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(40,16,NULL,NULL,'Aarti Shukla',5,'Best women\'s wellness supplement','Shatavari capsules have helped me with hormonal balance and menstrual regularity. I also noticed improvement in my energy levels during my cycle. Pure Ayurvedic support for women.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(41,16,NULL,NULL,'Nandini Pillai',5,'Highly recommend for new mothers','My lactation improved significantly after starting Shatavari. My doctor confirmed it is safe and beneficial during breastfeeding. Excellent quality capsules.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(42,16,NULL,NULL,'Sudha Varma',4,'Gentle and effective','Using Shatavari for perimenopausal symptoms. Hot flashes have reduced and mood swings are more manageable. Gentle on the stomach and easy to take daily.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(43,17,NULL,NULL,'Girish Tripathi',5,'Turmeric in its purest form','These Haridra capsules are far superior to just adding turmeric to food. The curcumin concentration is higher and the results on my joint pain have been remarkable within 6 weeks.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(44,17,NULL,NULL,'Lata Agnihotri',4,'Great anti-inflammatory support','I take Haridra capsules for my arthritis along with my regular treatment. The inflammation and morning stiffness has reduced. Good quality and genuine Haridra extract.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(45,18,NULL,NULL,'Balram Pandey',5,'The king of herbs, rightly so','Haritaki is called the king of herbs in Ayurveda for good reason. This powder is fresh, fragrant, and genuinely effective. My constipation and digestive issues have resolved completely.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(46,18,NULL,NULL,'Rohini Das',5,'Wonderful detox and cleanse','I do a monthly Haritaki cleanse and this is the finest quality I have found. It works gently and thoroughly. My skin clarity and energy levels improve noticeably each time.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17'),(47,18,NULL,NULL,'Jagdish Tomar',4,'Pure and fine, very effective','Good quality Haritaki powder. I use it with warm water at bedtime for regular bowel movements. No cramping, works gently overnight. Authentic Ayurvedic quality.',_binary '',_binary '','2026-06-29 03:49:17','2026-06-29 03:49:17');
/*!40000 ALTER TABLE `product_reviews` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `products`
--

DROP TABLE IF EXISTS `products`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `products` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sku` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `category` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `mrp` decimal(10,2) NOT NULL,
  `sale_price` decimal(10,2) NOT NULL,
  `unit` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'pcs',
  `weight_grams` decimal(10,2) DEFAULT NULL,
  `hsn_code` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gst_rate` decimal(4,2) DEFAULT '0.00',
  `image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_active` tinyint(1) DEFAULT '1',
  `stock_quantity` int DEFAULT '0',
  `low_stock_threshold` int DEFAULT '10',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `whatsapp_share_count` int DEFAULT '0',
  `view_count` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `sku` (`sku`),
  KEY `idx_products_category` (`category`),
  KEY `idx_products_active` (`is_active`),
  KEY `idx_products_sku` (`sku`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `products`
--

LOCK TABLES `products` WRITE;
/*!40000 ALTER TABLE `products` DISABLE KEYS */;
INSERT INTO `products` VALUES (1,'SHIFA-001','Ashwagandha Capsules','Premium quality Ashwagandha for stress relief and energy boost. Each bottle contains 60 capsules of 500mg each.','Capsules',599.00,499.00,'bottle',100.00,NULL,0.00,'/images/shifa/products/1.jpg',1,50,10,'2026-06-27 06:18:22','2026-06-27 08:07:15',0,0),(2,'SHIFA-002','Triphala Powder','Natural digestive support and detoxification. Pure blend of Amalaki, Bibhitaki and Haritaki.','Powder',299.00,249.00,'pack',200.00,NULL,0.00,'/images/shifa/products/2.jpg',1,100,10,'2026-06-27 06:18:22','2026-06-27 08:07:15',0,0),(3,'SHIFA-003','Chyawanprash','Immunity booster with 41 natural ingredients. Traditional Ayurvedic recipe for overall wellness.','Paste',450.00,399.00,'jar',500.00,NULL,0.00,'/images/shifa/products/3.jpg',1,75,10,'2026-06-27 06:18:22','2026-06-27 08:07:15',0,0),(4,'SHIFA-004','Giloy Juice','Pure Giloy (Guduchi) extract for immunity and fever management. 100% natural with no added sugar.','Juice',350.00,299.00,'bottle',500.00,NULL,0.00,'/images/shifa/products/4.jpg',1,60,10,'2026-06-27 06:18:22','2026-06-27 08:07:15',0,0),(5,'SHIFA-005','Tulsi Drops','Holy Basil drops for respiratory health and immunity. Rich in antioxidants and essential oils.','Drops',199.00,169.00,'bottle',30.00,NULL,0.00,'/images/shifa/products/5.jpg',1,120,10,'2026-06-27 06:18:22','2026-06-27 08:07:15',0,0),(6,'SHIFA-006','Amla Candy','Vitamin C rich Indian Gooseberry candy. Natural immunity booster and digestive aid.','Candy',150.00,129.00,'pack',200.00,NULL,0.00,'/images/shifa/products/6.jpg',1,200,10,'2026-06-27 06:18:22','2026-06-27 08:07:15',0,0),(7,'SHIFA-007','Brahmi Syrup','Brain tonic for memory and concentration. Helps reduce stress and improve mental clarity.','Syrup',275.00,249.00,'bottle',200.00,NULL,0.00,'/images/shifa/products/7.jpg',1,80,10,'2026-06-27 06:18:22','2026-06-27 08:07:15',0,0),(8,'SHIFA-008','Neem Capsules','Blood purifier and skin health support. Natural antibacterial and antifungal properties.','Capsules',399.00,349.00,'bottle',60.00,NULL,0.00,'/images/shifa/products/8.jpg',1,90,10,'2026-06-27 06:18:22','2026-06-27 08:07:15',0,0),(9,'SHIFA-009','Karela Jamun Juice','Natural diabetes management support. Helps regulate blood sugar levels naturally.','Juice',425.00,375.00,'bottle',500.00,NULL,0.00,'/images/shifa/products/9.jpg',1,45,10,'2026-06-27 06:18:22','2026-06-27 08:07:15',0,0),(10,'SHIFA-010','Aloe Vera Juice','Digestive health and skin care. Pure Aloe extract with natural pulp for better absorption.','Juice',325.00,289.00,'bottle',500.00,NULL,0.00,'/images/shifa/products/10.jpg',1,100,10,'2026-06-27 06:18:22','2026-06-27 08:07:15',0,0),(11,'SHIFA-011','Shilajit Resin','Premium Himalayan Shilajit for vitality and energy. Rich in fulvic acid and 85+ minerals.','Resin',999.00,849.00,'jar',20.00,NULL,0.00,'/images/shifa/products/11.jpg',1,30,10,'2026-06-27 06:18:22','2026-06-27 08:07:15',0,0),(12,'SHIFA-012','Moringa Powder','Nutrient-rich superfood powder. Contains 92 nutrients and 46 antioxidants.','Powder',375.00,329.00,'pack',200.00,NULL,0.00,'/images/shifa/products/12.jpg',1,85,10,'2026-06-27 06:18:22','2026-06-27 08:07:15',0,0),(13,'SHIFA-013','Arjuna Tea','Heart health support tea. Natural cardio-tonic with Terminalia Arjuna bark.','Tea',225.00,199.00,'pack',100.00,NULL,0.00,'/images/shifa/products/13.jpg',1,110,10,'2026-06-27 06:18:22','2026-06-27 08:07:15',0,0),(14,'SHIFA-014','Kumkumadi Oil','Skin brightening facial oil. Traditional blend of 16 herbs and saffron for radiant skin.','Oil',899.00,799.00,'bottle',50.00,NULL,0.00,'/images/shifa/products/14.jpg',1,40,10,'2026-06-27 06:18:22','2026-06-27 08:07:15',0,0),(15,'SHIFA-015','Trikatu Churna','Digestive fire booster. Blend of ginger, black pepper and long pepper for metabolism.','Powder',189.00,159.00,'pack',100.00,NULL,0.00,'/images/shifa/products/15.jpg',1,150,10,'2026-06-27 06:18:22','2026-06-27 08:07:15',0,0),(16,'SHIFA-016','Shatavari Capsules','Women wellness supplement. Supports hormonal balance and reproductive health.','Capsules',549.00,479.00,'bottle',80.00,NULL,0.00,'/images/shifa/products/16.jpg',1,65,10,'2026-06-27 06:18:22','2026-06-27 08:07:15',0,0),(17,'SHIFA-017','Haridra Capsules','Turmeric capsules with 95% curcumin. Anti-inflammatory and joint health support.','Capsules',449.00,399.00,'bottle',70.00,NULL,0.00,'/images/shifa/products/17.jpg',1,95,10,'2026-06-27 06:18:22','2026-06-27 08:07:15',0,0),(18,'SHIFA-018','Haritaki Powder','King of medicines powder. Supports digestion, detoxification and rejuvenation.','Powder',249.00,219.00,'pack',150.00,NULL,0.00,'/images/shifa/products/18.jpg',1,125,10,'2026-06-27 06:18:22','2026-06-27 08:07:15',0,0);
/*!40000 ALTER TABLE `products` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `role_permissions`
--

DROP TABLE IF EXISTS `role_permissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_permissions` (
  `role_id` bigint NOT NULL,
  `permission` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`role_id`, `permission`),
  CONSTRAINT `FKn5fotdgk8d1xvo8nav9uv3muc` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `role_permissions`
--

LOCK TABLES `role_permissions` WRITE;
/*!40000 ALTER TABLE `role_permissions` DISABLE KEYS */;
/*!40000 ALTER TABLE `role_permissions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `roles`
--

DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `display_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `role_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK716hgxp60ym1lifrdgp67xt5k` (`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `roles`
--

LOCK TABLES `roles` WRITE;
/*!40000 ALTER TABLE `roles` DISABLE KEYS */;
/*!40000 ALTER TABLE `roles` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `salesperson_targets`
--

DROP TABLE IF EXISTS `salesperson_targets`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `salesperson_targets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `salesperson_user_id` bigint NOT NULL,
  `month` int NOT NULL,
  `year` int NOT NULL,
  `target_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `achieved_amount` decimal(12,2) DEFAULT '0.00',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_target` (`salesperson_user_id`,`month`,`year`),
  KEY `idx_targets_salesperson` (`salesperson_user_id`),
  KEY `idx_targets_period` (`year`,`month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `salesperson_targets`
--

LOCK TABLES `salesperson_targets` WRITE;
/*!40000 ALTER TABLE `salesperson_targets` DISABLE KEYS */;
/*!40000 ALTER TABLE `salesperson_targets` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `salespersons`
--

DROP TABLE IF EXISTS `salespersons`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `salespersons` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `commission_rate` decimal(5,2) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `employee_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `joining_date` date DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `platform_user_id` bigint NOT NULL,
  `status` enum('ACTIVE','INACTIVE','ON_LEAVE') COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_salespersons_code` (`employee_code`),
  KEY `idx_salespersons_status` (`status`),
  KEY `idx_salespersons_user` (`platform_user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `salespersons`
--

LOCK TABLES `salespersons` WRITE;
/*!40000 ALTER TABLE `salespersons` DISABLE KEYS */;
INSERT INTO `salespersons` VALUES (7,5.00,'2026-06-28 20:45:24.000000','rahul@shifademo.test','DEMO-S01','2025-05-24','Rahul Desai','9000000001',1,'ACTIVE','2026-06-28 20:45:24.000000'),(8,7.50,'2026-06-28 20:45:24.000000','anita@shifademo.test','DEMO-S02','2025-09-01','Anita Kulkarni','9000000002',1,'ACTIVE','2026-06-28 20:45:24.000000'),(9,6.00,'2026-06-28 20:45:24.000000','vikram@shifademo.test','DEMO-S03','2025-12-10','Vikram Singh','9000000003',1,'ACTIVE','2026-06-28 20:45:24.000000');
/*!40000 ALTER TABLE `salespersons` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `stock_history`
--

DROP TABLE IF EXISTS `stock_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `notes` text COLLATE utf8mb4_unicode_ci,
  `operation` enum('ADJUSTMENT','RETURN','STOCK_IN','STOCK_OUT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `performed_by` bigint DEFAULT NULL,
  `quantity_after` int NOT NULL,
  `quantity_before` int NOT NULL,
  `quantity_changed` int NOT NULL,
  `reference_id` bigint DEFAULT NULL,
  `reference_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_stock_product` (`product_id`),
  KEY `idx_stock_created` (`created_at`),
  KEY `idx_stock_operation` (`operation`),
  CONSTRAINT `FKjssgif5kuhhjh6bwyxq5xdbsf` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `stock_history`
--

LOCK TABLES `stock_history` WRITE;
/*!40000 ALTER TABLE `stock_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `stock_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `storefront_users`
--

DROP TABLE IF EXISTS `storefront_users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `storefront_users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_id` bigint DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `full_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_verified` bit(1) DEFAULT b'0',
  `is_active` bit(1) DEFAULT b'1',
  `last_login_at` datetime(6) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`),
  UNIQUE KEY `idx_sf_users_email` (`email`),
  UNIQUE KEY `customer_id` (`customer_id`),
  KEY `idx_sf_users_customer` (`customer_id`),
  CONSTRAINT `fk_sf_users_customer` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `storefront_users`
--

LOCK TABLES `storefront_users` WRITE;
/*!40000 ALTER TABLE `storefront_users` DISABLE KEYS */;
/*!40000 ALTER TABLE `storefront_users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tenant_ui_config`
--

DROP TABLE IF EXISTS `tenant_ui_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tenant_ui_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `accent_color` varchar(7) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `custom_css` text COLLATE utf8mb4_unicode_ci,
  `favicon_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `font_family` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `logo_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `primary_color` varchar(7) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `secondary_color` varchar(7) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `storefront_config` json DEFAULT NULL,
  `storefront_enabled` bit(1) DEFAULT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKllc2ikadv6u7ehl7e4wkpi71x` (`tenant_id`),
  CONSTRAINT `FKcdvpumoenxxtmhdys5fog0kqa` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tenant_ui_config`
--

LOCK TABLES `tenant_ui_config` WRITE;
/*!40000 ALTER TABLE `tenant_ui_config` DISABLE KEYS */;
/*!40000 ALTER TABLE `tenant_ui_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tenants`
--

DROP TABLE IF EXISTS `tenants`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tenants` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contact_email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contact_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `db_password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `db_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `db_username` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `domain` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('ACTIVE','ONBOARDING','SUSPENDED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `subscription_plan` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tenant_key` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4fwswms1l68hnsxgfew7m4ssw` (`tenant_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tenants`
--

LOCK TABLES `tenants` WRITE;
/*!40000 ALTER TABLE `tenants` DISABLE KEYS */;
/*!40000 ALTER TABLE `tenants` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-29 12:35:44
