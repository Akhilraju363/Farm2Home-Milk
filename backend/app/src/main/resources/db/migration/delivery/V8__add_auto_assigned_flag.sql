-- Distinguishes an automatically-created assignment (OrderEventConsumer, triggered by
-- ORDER_CREATED) from an admin's manual one (DeliveryAssignmentServiceImpl.manualAssign) - needed
-- for the admin dashboard's "Automatically assigned" / "Manually assigned" counts, which must be
-- backed by real stored data rather than guessed at read time.
ALTER TABLE delivery.delivery_assignments
    ADD COLUMN auto_assigned BOOLEAN NOT NULL DEFAULT false;
