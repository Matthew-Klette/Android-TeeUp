-- TeeUp's course catalogue: real South African golf courses only, every course in
-- Port Elizabeth (Gqeberha) included. Replaces seed-course.sql's made-up "Emeris Municipal
-- Golf Course", which sat on New York's coordinates.
--
-- Each pin is the middle of the course's outline in OpenStreetMap (or, where OSM hasn't
-- mapped the course, the source named below), so Course Preview's satellite map opens on the
-- real course. Ratings are left as they are: only courses already in the table have one.
-- Course locations (c) OpenStreetMap contributors, ODbL 1.0.
--
-- Safe to run more than once. Run the hole seeds in infra/courses/holes/ after this one.
--
-- Sources:
--   Fancourt Links: OSM way 47154775
--   Humewood Golf Club: OSM way 4306573
--   Wanderers Golf Club: OSM way 18826619
--   Port Elizabeth Golf Club: OSM way 4018629
--   Little Walmer Golf Club: OSM relation 17245579
--   Durban Country Club: OSM way 25744060
--   Royal Johannesburg and Kensington Golf Club: OSM relation 19820891
--   Leopard Creek Country Club: OSM relation 11020310
--   Gary Player Country Club: OSM way 1516928087
--   Erinvale Golf Club: OSM way 88132789
--   Simola Golf Club: OSM way 176194575
--   Zwartkop Country Club: OSM way 966807848
--   Silver Lakes Golf Estate: OSM relation 21450776
--   Arabella Golf Club: OSM way 49188988
--   Steenberg Golf Club: OSM way 947275731
--   Selborne Golf Estate: OSM way 299924272
--   East London Golf Club: OSM relation 20603806
--   Sardinia Bay Golf Estate: Heron Road estate, Esri World Imagery
--   St Francis Links: where2golf.com course page
--   Wedgewood Golf Estate: middle of its 18 holes as traced from Esri World Imagery (OSM way 340684668's outline sits north-west of the course)
--   Pinnacle Point Golf Estate: middle of its 18 greens and 92 tees in OSM (the course has no outline there)
--   Pearl Valley Golf Estate: Val de Vie estate, Esri World Imagery (OSM way 240820807 nearby is Boschenmeer)
BEGIN;

-- The made-up course goes, unless someone has booked a tee time on it (TeeTimes restricts
-- deletes); then it stays and this says so, so those tee times can be dealt with by hand.
DO $$
DECLARE booked int;
BEGIN
    SELECT count(*) INTO booked FROM "TeeTimes" t JOIN "Courses" c ON c."Id" = t."CourseId"
    WHERE c."Name" = 'Emeris Municipal Golf Course';
    IF booked = 0 THEN
        DELETE FROM "Courses" WHERE "Name" = 'Emeris Municipal Golf Course';
    ELSE
        RAISE NOTICE 'Emeris Municipal Golf Course kept: % tee time(s) still use it', booked;
    END IF;
END $$;

-- Two existing rows had names the clubs don't use.
UPDATE "Courses" SET "Name" = 'Little Walmer Golf Club' WHERE "Name" = 'Little Walmer Golf Course'
  AND NOT EXISTS (SELECT 1 FROM "Courses" WHERE "Name" = 'Little Walmer Golf Club');
UPDATE "Courses" SET "Name" = 'Wedgewood Golf Estate' WHERE "Name" = 'Wedgewood Golf & Country Estate'
  AND NOT EXISTS (SELECT 1 FROM "Courses" WHERE "Name" = 'Wedgewood Golf Estate');

INSERT INTO "Courses" ("Id", "Name", "Latitude", "Longitude")
SELECT gen_random_uuid(), v.name, v.lat, v.lng
FROM (VALUES
    ('Fancourt Links', -33.96776, 22.41567),
    ('Humewood Golf Club', -33.99959, 25.67858),
    ('Wanderers Golf Club', -26.12946, 28.05751),
    ('Port Elizabeth Golf Club', -33.95878, 25.58108),
    ('Little Walmer Golf Club', -33.97387, 25.57340),
    ('Durban Country Club', -29.82133, 31.03458),
    ('Royal Johannesburg and Kensington Golf Club', -26.14993, 28.11108),
    ('Leopard Creek Country Club', -25.44434, 31.53620),
    ('Gary Player Country Club', -25.33957, 27.10674),
    ('Erinvale Golf Club', -34.06396, 18.88280),
    ('Simola Golf Club', -34.00765, 23.03357),
    ('Zwartkop Country Club', -25.83258, 28.16368),
    ('Silver Lakes Golf Estate', -25.77234, 28.37121),
    ('Arabella Golf Club', -34.31589, 19.13099),
    ('Steenberg Golf Club', -34.06796, 18.42695),
    ('Selborne Golf Estate', -30.37229, 30.67770),
    ('East London Golf Club', -33.00012, 27.93485),
    ('Sardinia Bay Golf Estate', -34.01710, 25.49530),
    ('St Francis Links', -34.16080, 24.81640),
    ('Wedgewood Golf Estate', -33.91149, 25.39287),
    ('Pinnacle Point Golf Estate', -34.20170, 22.09330),
    ('Pearl Valley Golf Estate', -33.82220, 18.97930)
) AS v(name, lat, lng)
WHERE NOT EXISTS (SELECT 1 FROM "Courses" c WHERE c."Name" = v.name);

UPDATE "Courses" c SET "Latitude" = v.lat, "Longitude" = v.lng
FROM (VALUES
    ('Fancourt Links', -33.96776, 22.41567),
    ('Humewood Golf Club', -33.99959, 25.67858),
    ('Wanderers Golf Club', -26.12946, 28.05751),
    ('Port Elizabeth Golf Club', -33.95878, 25.58108),
    ('Little Walmer Golf Club', -33.97387, 25.57340),
    ('Durban Country Club', -29.82133, 31.03458),
    ('Royal Johannesburg and Kensington Golf Club', -26.14993, 28.11108),
    ('Leopard Creek Country Club', -25.44434, 31.53620),
    ('Gary Player Country Club', -25.33957, 27.10674),
    ('Erinvale Golf Club', -34.06396, 18.88280),
    ('Simola Golf Club', -34.00765, 23.03357),
    ('Zwartkop Country Club', -25.83258, 28.16368),
    ('Silver Lakes Golf Estate', -25.77234, 28.37121),
    ('Arabella Golf Club', -34.31589, 19.13099),
    ('Steenberg Golf Club', -34.06796, 18.42695),
    ('Selborne Golf Estate', -30.37229, 30.67770),
    ('East London Golf Club', -33.00012, 27.93485),
    ('Sardinia Bay Golf Estate', -34.01710, 25.49530),
    ('St Francis Links', -34.16080, 24.81640),
    ('Wedgewood Golf Estate', -33.91149, 25.39287),
    ('Pinnacle Point Golf Estate', -34.20170, 22.09330),
    ('Pearl Valley Golf Estate', -33.82220, 18.97930)
) AS v(name, lat, lng)
WHERE c."Name" = v.name;

COMMIT;
