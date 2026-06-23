# Farm2Home Milk - Product Requirements Document (PRD)

Version: 1.0

Project Name: Farm2Home Milk

Business Type: Dairy Farm Management & Milk Delivery Platform

Technology Stack:

* Frontend: React + TypeScript
* Backend: Java 21 + Spring Boot 3 Microservices
* Database: PostgreSQL
* Authentication: JWT + OTP
* API Documentation: OpenAPI / Swagger
* Cloud: AWS
* Containerization: Docker
* CI/CD: GitLab CI/CD

---

# 1. Project Overview

Farm2Home Milk is a complete dairy farm management and milk delivery platform that enables customers to subscribe to fresh milk directly from the farm and receive daily deliveries.

The system will manage:

* Customers
* Milk subscriptions
* Daily orders
* Deliveries
* Payments
* Cow management
* Milk production
* Inventory
* Analytics
* Administration

The platform consists of:

1. Customer Web Application
2. Admin Portal
3. Delivery Partner Portal
4. REST API Backend
5. Reporting & Analytics Dashboard

---

# 2. User Roles

## Super Admin

Permissions:

* Manage entire system
* Manage users
* Manage farms
* Manage subscriptions
* Manage reports
* Manage inventory
* View analytics

---

## Farm Manager

Permissions:

* Manage cows
* Track milk production
* Manage inventory
* View reports

---

## Delivery Manager

Permissions:

* Assign routes
* Manage delivery partners
* Track deliveries

---

## Delivery Partner

Permissions:

* View assigned deliveries
* Update delivery status

---

## Customer

Permissions:

* Subscribe milk plans
* Manage orders
* Manage payments
* Pause subscriptions
* View history

---

# 3. Application Modules

---

# Module 1: Authentication Service

Microservice Name:
auth-service

Features:

* Registration
* Login
* Mobile OTP Verification
* Forgot Password
* Change Password
* JWT Authentication
* Refresh Tokens
* Role Based Access Control

Tables:

users
roles
user_roles
otp_verifications

API Endpoints:

POST /auth/register
POST /auth/login
POST /auth/send-otp
POST /auth/verify-otp
POST /auth/refresh-token
POST /auth/logout

---

# Module 2: Customer Service

Microservice Name:
customer-service

Features:

* Customer Profile
* Address Management
* Multiple Addresses
* Customer Preferences

Tables:

customers
customer_addresses

Fields:

Customer:

* id
* customer_code
* first_name
* last_name
* mobile
* email
* status

Address:

* id
* customer_id
* address_line1
* address_line2
* city
* state
* pincode
* latitude
* longitude

API:

GET /customers
GET /customers/{id}
POST /customers
PUT /customers/{id}
DELETE /customers/{id}

---

# Module 3: Subscription Service

Microservice:
subscription-service

Features:

* Daily Milk Subscription
* Alternate Day Subscription
* Weekly Subscription
* Pause Subscription
* Resume Subscription

Tables:

subscriptions

Fields:

* id
* customer_id
* milk_type
* quantity
* schedule_type
* start_date
* end_date
* status

API:

POST /subscriptions
GET /subscriptions
PUT /subscriptions/{id}
DELETE /subscriptions/{id}

---

# Module 4: Order Service

Microservice:
order-service

Features:

* Daily Order Generation
* One Time Orders
* Subscription Orders

Tables:

orders
order_items

Order Fields:

* id
* order_number
* customer_id
* order_date
* total_amount
* status

Statuses:

PENDING
ASSIGNED
OUT_FOR_DELIVERY
DELIVERED
CANCELLED

API:

POST /orders
GET /orders
GET /orders/{id}

---

# Module 5: Payment Service

Microservice:
payment-service

Features:

* UPI Payments
* Razorpay Integration
* Wallet
* Payment History

Tables:

payments

Fields:

* id
* order_id
* payment_reference
* amount
* payment_method
* payment_status

Statuses:

PENDING
SUCCESS
FAILED

API:

POST /payments
GET /payments
GET /payments/{id}

---

# Module 6: Delivery Service

Microservice:
delivery-service

Features:

* Route Assignment
* Delivery Tracking
* Delivery Confirmation

Tables:

delivery_routes
delivery_assignments

API:

POST /delivery/routes
POST /delivery/assign
GET /delivery/today

---

# Module 7: Farm Management Service

Microservice:
farm-service

Features:

* Cow Registration
* Breed Tracking
* Vaccination Records
* Health Monitoring

Tables:

cows
vaccinations
health_records

Cow Fields:

* id
* tag_number
* cow_name
* breed
* date_of_birth
* purchase_date
* status

API:

POST /cows
GET /cows
PUT /cows/{id}

---

# Module 8: Milk Production Service

Microservice:
production-service

Features:

* Morning Collection
* Evening Collection
* Quality Tracking
* Fat Percentage
* SNF Percentage

Tables:

milk_production

Fields:

* id
* cow_id
* collection_date
* session
* quantity_liters
* fat_percentage
* snf_percentage

API:

POST /production
GET /production
GET /production/daily

---

# Module 9: Inventory Service

Microservice:
inventory-service

Features:

* Feed Inventory
* Medicine Inventory
* Equipment Inventory

Tables:

inventory_items

Fields:

* id
* item_name
* item_type
* quantity
* unit
* reorder_level

API:

POST /inventory
GET /inventory
PUT /inventory/{id}

---

# Module 10: Notification Service

Microservice:
notification-service

Features:

* SMS Notifications
* Email Notifications
* Push Notifications

Events:

* Order Created
* Delivery Assigned
* Delivery Completed
* Payment Success
* Subscription Renewal

API:

POST /notifications/send

---

# 4. Frontend Architecture

Technology:

React 19
TypeScript
Material UI
Redux Toolkit
React Query
React Router

Folder Structure:

src/

├── components

├── pages

├── layouts

├── services

├── store

├── hooks

├── routes

├── types

├── utils

├── assets

└── App.tsx

---

# 5. UI Screens

Authentication

* Login
* Register
* OTP Verification
* Forgot Password

Customer

* Dashboard
* Profile
* Addresses
* Subscriptions
* Orders
* Payments

Admin

* Dashboard
* Customers
* Deliveries
* Production
* Cows
* Inventory
* Reports

Delivery Partner

* Today's Deliveries
* Delivery Details
* Mark Delivered

---

# 6. Dashboard Analytics

Metrics:

* Total Customers
* Active Subscriptions
* Daily Production
* Monthly Revenue
* Delivery Success Rate
* Active Cows
* Inventory Alerts

Charts:

* Milk Production Trend
* Revenue Trend
* Customer Growth
* Order Trend

---

# 7. PostgreSQL Database Design

Schemas:

auth
customer
subscription
order
payment
delivery
farm
production
inventory

Each microservice owns its own schema.

Use:

* UUID Primary Keys
* Auditing Columns
* Soft Delete Support

Common Fields:

created_at
created_by
updated_at
updated_by
is_deleted

---

# 8. Security Requirements

Implement:

* JWT Authentication
* Role Based Access Control
* Password Encryption (BCrypt)
* Rate Limiting
* CORS Protection
* Input Validation

---

# 9. AWS Deployment

Services:

EC2
RDS PostgreSQL
S3
CloudWatch
Elastic Load Balancer

Environments:

Development
QA
UAT
Production

---

# 10. Docker

Every microservice must have:

Dockerfile

docker-compose.yml

Support:

* Local Development
* QA Deployment
* Production Deployment

---

# 11. CI/CD

GitLab Pipeline

Stages:

1. Build
2. Test
3. Sonar Scan
4. Docker Build
5. Deploy QA
6. Deploy UAT
7. Deploy Production

---

# 12. Future Enhancements

Phase 2

* Mobile App (React Native)
* Route Optimization
* Referral Program
* Coupon System
* Loyalty Points

Phase 3

* AI Demand Forecasting
* IoT Milk Sensors
* Cow Health Prediction
* Franchise Management
* Multi Farm Support

---

# 13. Non Functional Requirements

Performance:

* API Response < 500 ms
* Support 10000+ Customers
* 99.9% Availability

Scalability:

* Horizontal Scaling
* Stateless Services

Monitoring:

* Spring Actuator
* Prometheus
* Grafana
* CloudWatch

Logging:

* Centralized Logging
* ELK Stack Compatible

---

# 14. Deliverables

Generate:

1. Complete React Frontend
2. Complete Spring Boot Microservices
3. PostgreSQL Scripts
4. Swagger Documentation
5. Docker Files
6. Docker Compose
7. GitLab CI/CD
8. Unit Tests
9. Integration Tests
10. Role Based Security
11. Seed Data
12. Production Ready Deployment Configuration

Important:

Generate enterprise-grade production-ready code following Clean Architecture, SOLID Principles, Design Patterns, Exception Handling, Validation, Logging, Auditing, Security Best Practices, and Microservice Standards.

Code coverage target:

* Minimum 90%

API documentation:

* 100% Swagger documented

Database:

* Flyway migrations

Architecture:

* Domain Driven Design (DDD)

Testing:

* JUnit 5
* Mockito
* Testcontainers

Frontend:

* Responsive Design
* Mobile Friendly
* Material UI Design System

The generated solution must be deployable using Docker Compose with a single command.
