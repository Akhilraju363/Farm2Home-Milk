-- ============================================================
-- Carries the order's customer_id/order_number onto the delivery assignment so
-- DeliveryEventProducer can publish them on DeliveryEvent - without this, notification-service
-- has no way to resolve who a DELIVERY_* event is for (see OrderEventConsumer, which already
-- receives both on the OrderEvent it consumes, but previously discarded them).
-- Nullable: assignments created via manual admin assignment (DeliveryAssignmentServiceImpl
-- .manualAssign()) have no OrderEvent to source these from, so they stay null and simply don't
-- trigger customer notifications - a pre-existing gap, not a regression.
-- ============================================================

ALTER TABLE delivery.delivery_assignments ADD COLUMN customer_id UUID;
ALTER TABLE delivery.delivery_assignments ADD COLUMN order_number VARCHAR(50);
