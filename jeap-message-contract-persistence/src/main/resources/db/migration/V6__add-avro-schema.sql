ALTER TABLE message_contract
    ADD COLUMN avro_schema VARCHAR;

ALTER TABLE message_contract
    ALTER COLUMN avro_protocol_schema DROP NOT NULL;

ALTER TABLE message_contract
    ADD CONSTRAINT message_contract_schema_present
        CHECK (avro_schema IS NOT NULL OR avro_protocol_schema IS NOT NULL);
