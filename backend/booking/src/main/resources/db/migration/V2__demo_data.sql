INSERT INTO branch (id, name, address, timezone, slot_minutes) VALUES
                                                                   ('11111111-1111-1111-1111-111111111111', 'Stellenbosch Main', '1 Demo Street, Stellenbosch', 'Africa/Johannesburg', 30),
                                                                   ('22222222-2222-2222-2222-222222222222', 'Sandton City',      '2 Demo Avenue, Sandton',      'Africa/Johannesburg', 30),
                                                                   ('33333333-3333-3333-3333-333333333333', 'Cape Town CBD',     '3 Demo Road, Cape Town',      'Africa/Johannesburg', 30),
                                                                   ('44444444-4444-4444-4444-444444444444', 'Durban Gateway',    '4 Demo Lane, Umhlanga',       'Africa/Johannesburg', 20);

INSERT INTO branch_opening_hours (branch_id, day_of_week, open_time, close_time)
SELECT b.id, d, TIME '08:30', TIME '17:00' FROM branch b, generate_series(1, 5) AS d;   -- Mon-Fri

INSERT INTO branch_opening_hours (branch_id, day_of_week, open_time, close_time)
SELECT b.id, 6, TIME '08:30', TIME '12:30' FROM branch b;                               -- Sat

INSERT INTO service_type (id, code, name) VALUES
                                              (gen_random_uuid(), 'GENERAL_ENQUIRY',  'General enquiry'),
                                              (gen_random_uuid(), 'ACCOUNT_OPENING',  'Open an account'),
                                              (gen_random_uuid(), 'CARD_REPLACEMENT', 'Replace a card');