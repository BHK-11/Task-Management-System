CREATE TABLE task_os.roles (
    role_name VARCHAR(32) PRIMARY KEY
);

INSERT INTO task_os.roles (role_name) VALUES ('ROLE_USER'), ('ROLE_ADMIN');

CREATE TABLE task_os.users (
    id UUID PRIMARY KEY,
    display_name VARCHAR(80) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(512) NOT NULL,
    account_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    theme_preference VARCHAR(16) NOT NULL DEFAULT 'SYSTEM',
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    locale VARCHAR(10) NOT NULL DEFAULT 'en',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_account_status CHECK (account_status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_users_theme CHECK (theme_preference IN ('SYSTEM', 'LIGHT', 'DARK')),
    CONSTRAINT ck_users_locale CHECK (locale IN ('en'))
);

CREATE TABLE task_os.user_roles (
    user_id UUID NOT NULL,
    role_name VARCHAR(32) NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_name),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id)
        REFERENCES task_os.users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_name)
        REFERENCES task_os.roles (role_name)
);

CREATE INDEX ix_users_account_status ON task_os.users (account_status);
CREATE INDEX ix_user_roles_role_name ON task_os.user_roles (role_name);

CREATE TABLE task_os.auth_events (
    id UUID PRIMARY KEY,
    user_id UUID,
    event_type VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_auth_events_type CHECK (event_type IN (
        'ACCOUNT_REGISTERED', 'LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'LOGOUT',
        'PROFILE_UPDATED', 'PASSWORD_CHANGED'
    )),
    CONSTRAINT fk_auth_events_user FOREIGN KEY (user_id)
        REFERENCES task_os.users (id) ON DELETE SET NULL
);

CREATE INDEX ix_auth_events_occurred_at ON task_os.auth_events (occurred_at);
CREATE INDEX ix_auth_events_user_time ON task_os.auth_events (user_id, occurred_at);
