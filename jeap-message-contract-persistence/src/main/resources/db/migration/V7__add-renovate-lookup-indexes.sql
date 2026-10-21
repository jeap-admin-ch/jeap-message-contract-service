-- Support the Renovate datasource lookup of the currently deployed contracts of a message type: it filters the
-- non-deleted contracts by the lower-cased message type and joins each application's latest deployment in an
-- environment. Without these indexes, every request scans both tables.
CREATE INDEX message_contract_lower_message_type_not_deleted ON message_contract (lower(message_type)) WHERE deleted = false;
CREATE INDEX deployment_app_name_environment_created_at ON deployment (app_name, environment, created_at DESC);
