--liquibase formatted sql
--changeset opik:add-api-keys-description-column

ALTER TABLE user_api_keys ADD COLUMN description VARCHAR(500) NULL AFTER name;

--rollback ALTER TABLE user_api_keys DROP COLUMN description;


