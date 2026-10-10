-- One Postgres instance locally, one database per service (database-per-service pattern).
-- In the cloud, point each service at its own managed database instead.
CREATE DATABASE auth_db;
CREATE DATABASE payments_db;
CREATE DATABASE reports_db;
