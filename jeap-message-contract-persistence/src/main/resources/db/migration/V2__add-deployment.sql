CREATE TABLE deployment
(
    id                   uuid PRIMARY KEY,
    app_name             VARCHAR                  NOT NULL,
    app_version          VARCHAR                  NOT NULL,
    environment          VARCHAR                  NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL

);

CREATE INDEX deployment_app_name ON deployment (app_name);
CREATE INDEX deployment_app_version ON deployment (app_version);
CREATE INDEX deployment_environment ON deployment (environment);
CREATE INDEX deployment_created_at ON deployment (created_at);
