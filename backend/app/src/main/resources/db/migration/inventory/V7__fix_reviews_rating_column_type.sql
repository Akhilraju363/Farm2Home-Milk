-- V6 used SMALLINT for rating, but the Review entity's Integer field maps to INTEGER by default
-- under Hibernate's schema validator - never modify an already-applied migration, so this widens
-- the column instead. The CHECK constraint (rating BETWEEN 1 AND 5) still applies unchanged.
ALTER TABLE inventory.reviews ALTER COLUMN rating TYPE INTEGER;
