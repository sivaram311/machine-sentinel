INSERT INTO registered_applications (client_id, display_name, enabled, redirect_uris)
VALUES (
  'machine-sentinel',
  'Machine Sentinel',
  true,
  'http://127.0.0.1:3351/**,http://localhost:3351/**'
)
ON CONFLICT (client_id) DO UPDATE
SET display_name = EXCLUDED.display_name,
    enabled = true,
    redirect_uris = EXCLUDED.redirect_uris;

INSERT INTO user_application_roles (role_name, application_id, user_id)
SELECT 'ROLE_ADMIN', a.id, u.id
FROM registered_applications a, users u
WHERE a.client_id = 'machine-sentinel' AND u.username = 'admin'
ON CONFLICT (user_id, application_id, role_name) DO NOTHING;

INSERT INTO user_application_roles (role_name, application_id, user_id)
SELECT 'ROLE_USER', a.id, u.id
FROM registered_applications a, users u
WHERE a.client_id = 'machine-sentinel' AND u.username = 'admin'
ON CONFLICT (user_id, application_id, role_name) DO NOTHING;

INSERT INTO user_application_roles (role_name, application_id, user_id)
SELECT 'ROLE_USER', a.id, u.id
FROM registered_applications a, users u
WHERE a.client_id = 'machine-sentinel' AND u.username = 'demo'
ON CONFLICT (user_id, application_id, role_name) DO NOTHING;

SELECT a.client_id, a.redirect_uris, u.username, r.role_name
FROM registered_applications a
LEFT JOIN user_application_roles r ON r.application_id = a.id
LEFT JOIN users u ON u.id = r.user_id
WHERE a.client_id = 'machine-sentinel'
ORDER BY u.username, r.role_name;
