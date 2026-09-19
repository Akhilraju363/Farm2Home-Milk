-- Farm2Home's own business address was never captured anywhere in this system - only
-- coordinates + radius existed (see V6__create_business_settings.sql). This is the single
-- authoritative place for it going forward; nothing else (delivery routes, frontend code) should
-- store or hardcode a copy of it - see the Route Management location-display fix in the same
-- change that added this migration.
ALTER TABLE farm.business_settings
    ADD COLUMN business_name VARCHAR(150),
    ADD COLUMN address_line  VARCHAR(255),
    ADD COLUMN locality      VARCHAR(150),
    ADD COLUMN city          VARCHAR(100),
    ADD COLUMN district      VARCHAR(100),
    ADD COLUMN state         VARCHAR(100),
    ADD COLUMN pincode       VARCHAR(10);

UPDATE farm.business_settings
SET business_name = 'Farm2Home',
    address_line  = 'ST Colony',
    locality      = 'Yerraguntlapalle',
    city          = 'Pileru',
    district      = 'Chittoor',
    state         = 'Andhra Pradesh',
    pincode       = '517214'
WHERE id = 1;
