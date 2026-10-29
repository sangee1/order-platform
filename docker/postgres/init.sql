-- One database per service: services never share tables.
CREATE USER orders WITH PASSWORD 'orders';
CREATE DATABASE orders OWNER orders;
CREATE USER inventory WITH PASSWORD 'inventory';
CREATE DATABASE inventory OWNER inventory;
CREATE USER payments WITH PASSWORD 'payments';
CREATE DATABASE payments OWNER payments;
