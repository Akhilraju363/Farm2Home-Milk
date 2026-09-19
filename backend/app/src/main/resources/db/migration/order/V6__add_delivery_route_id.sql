-- Cross-service reference (no FK - delivery_routes lives in delivery-service's own schema/
-- database instance, same convention as customer_id/product_id elsewhere in this table).
-- Nullable: existing orders keep working with no route ("Route not assigned" in the UI), and a
-- new order's address may fall in a route-coverage gap (see DeliveryRouteSelectionServiceImpl).
ALTER TABLE "order".orders
    ADD COLUMN delivery_route_id UUID;
