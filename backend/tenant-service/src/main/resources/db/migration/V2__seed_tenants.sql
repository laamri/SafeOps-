INSERT INTO tenant (id, slug, name, plan, status, config_json, created_at, updated_at)
VALUES
('00000000-0000-0000-0000-0000000000a1', 'tenant-a', 'Clinique Al Amal', 'STARTER', 'ACTIVE',
 '{"branding":{"name":"Clinique Al Amal","primaryColor":"#1FA38A","accentColor":"#F3B761"}}',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('00000000-0000-0000-0000-0000000000b1', 'tenant-b', 'Agence Guéliz', 'PRO', 'ACTIVE',
 '{"branding":{"name":"Agence Guéliz","primaryColor":"#D96C4F","accentColor":"#5E81AC"}}',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
