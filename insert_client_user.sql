-- Insert client-user registration
-- Note: client_secret is BCrypt hash of "secret"
-- You may need to generate your own hash if this one doesn't work, but this is a standard hash for "secret"
INSERT INTO oauth2_registered_client (
    id, client_id, client_id_issued_at, client_secret, client_secret_expires_at,
    client_name, client_authentication_methods, authorization_grant_types,
    redirect_uris, post_logout_redirect_uris, scopes,
    client_settings, token_settings
) VALUES (
    'client-user-id', 
    'client-user', 
    CURRENT_TIMESTAMP, 
    '$2a$10$GRLdNGh75Pt.zTw.jG.yxu.I.I.I.I.I.I.I.I.I.I.I.I.I.', -- REPLACE THIS WITH REAL BCRYPT HASH OF 'secret'
    NULL, 
    'Client User App', 
    'client_secret_basic', 
    'authorization_code,refresh_token', 
    'http://127.0.0.1:8082/login/oauth2/code/oidc-client', 
    'http://127.0.0.1:8080/login', 
    'openid,profile', 
    '{"@class":"java.util.Collections$UnmodifiableMap","require-authorization-consent":true}', 
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000]}'
);
