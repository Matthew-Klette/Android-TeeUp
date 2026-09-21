-- Baseline seed data for EME-309 so the deployed API isn't returning empty
-- results purely because the database is unseeded. Safe to run more than
-- once (skips insert if a course already exists).
INSERT INTO "Courses" ("Id", "Name", "Latitude", "Longitude", "Rating")
SELECT gen_random_uuid(), 'Emeris Municipal Golf Course', 40.712776, -74.005974, 72.5
WHERE NOT EXISTS (SELECT 1 FROM "Courses");
