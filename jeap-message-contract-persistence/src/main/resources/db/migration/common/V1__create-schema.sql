CREATE TABLE message_contract
(
    id                   uuid PRIMARY KEY,
    app_name             VARCHAR                  NOT NULL,
    app_version          VARCHAR                  NOT NULL,
    message_type         VARCHAR                  NOT NULL,
    message_type_version VARCHAR                  NOT NULL,
    role                 VARCHAR                  NOT NULL,
    topic                VARCHAR                  NOT NULL,
    registry_url         VARCHAR                  NOT NULL,
    commit_hash          VARCHAR,
    branch               VARCHAR,
    compatibility_mode   VARCHAR                  NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted              BOOL                     DEFAULT false,
    deleted_at           TIMESTAMP WITH TIME ZONE DEFAULT NULL,

    CONSTRAINT unique_entry UNIQUE (app_name, app_version, message_type, message_type_version, role, topic)
);

CREATE INDEX message_contract_app_name ON message_contract (app_name);
CREATE INDEX message_contract_app_version ON message_contract (app_version);
CREATE INDEX message_contract_message_type ON message_contract (message_type);
CREATE INDEX message_contract_message_type_version ON message_contract (message_type_version);
CREATE INDEX message_contract_message_topic ON message_contract (topic);
CREATE INDEX message_contract_message_role ON message_contract (role);
