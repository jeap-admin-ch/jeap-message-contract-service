ALTER TABLE message_contract
    ADD COLUMN avro_schema VARCHAR;

ALTER TABLE message_contract
    ALTER COLUMN avro_protocol_schema DROP NOT NULL;
