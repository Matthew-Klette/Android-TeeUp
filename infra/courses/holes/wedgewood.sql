-- Hole map seed for Wedgewood Golf Estate. OpenStreetMap has no hole lines for this course, so it
-- was traced by hand: greens traced from Esri World Imagery; which tee plays to which green checked against ProVisualizer's published course plan (provisualizer.com, course 4310); tee distances from the GolfPass scorecard (yards x 0.9144), with each tee placed on the line of play at that distance.
--
-- Safe to run more than once: creates the course row if it's missing, moves an existing
-- row's pin to the course centre, then replaces that course's hole coordinates.
-- Fails (and rolls back) if more than one course has this exact name.
BEGIN;

INSERT INTO "Courses" ("Id", "Name", "Latitude", "Longitude")
SELECT gen_random_uuid(), 'Wedgewood Golf Estate', -33.91149, 25.39287
WHERE NOT EXISTS (SELECT 1 FROM "Courses" WHERE "Name" = 'Wedgewood Golf Estate');

UPDATE "Courses" SET "Latitude" = -33.91149, "Longitude" = 25.39287 WHERE "Name" = 'Wedgewood Golf Estate';

DELETE FROM "HoleCoordinates"
WHERE "CourseId" = (SELECT "Id" FROM "Courses" WHERE "Name" = 'Wedgewood Golf Estate');

INSERT INTO "HoleCoordinates" ("Id", "CourseId", "Hole", "Poi", "Location", "SideFw", "Latitude", "Longitude")
SELECT gen_random_uuid(), (SELECT "Id" FROM "Courses" WHERE "Name" = 'Wedgewood Golf Estate'),
       v.hole, v.poi, v.location, v.side_fw, v.lat, v.lng
FROM (VALUES
    (1, 11, 2, 2, -33.9077014, 25.3938618),
    (1, 1, 2, 2, -33.9108613, 25.3925853),
    (2, 11, 2, 2, -33.9115496, 25.3929434),
    (2, 1, 2, 2, -33.9160176, 25.3909616),
    (3, 11, 2, 2, -33.9166416, 25.3905732),
    (3, 1, 2, 2, -33.9182005, 25.3910482),
    (4, 11, 2, 2, -33.9176561, 25.3896379),
    (4, 1, 2, 2, -33.915263, 25.3845535),
    (5, 11, 2, 2, -33.9148972, 25.3842658),
    (5, 1, 2, 2, -33.915281, 25.3883204),
    (6, 11, 2, 2, -33.9155858, 25.3885412),
    (6, 1, 2, 2, -33.9126309, 25.3897709),
    (7, 11, 2, 2, -33.9117076, 25.3898762),
    (7, 1, 2, 2, -33.9125681, 25.3886127),
    (8, 11, 2, 2, -33.9127472, 25.3878592),
    (8, 1, 2, 2, -33.9103223, 25.3907234),
    (9, 11, 2, 2, -33.9108575, 25.3918282),
    (9, 1, 2, 2, -33.9075734, 25.3932672),
    (10, 11, 2, 2, -33.9080915, 25.3943885),
    (10, 1, 2, 2, -33.9083819, 25.3979326),
    (11, 11, 2, 2, -33.9082474, 25.3988779),
    (11, 1, 2, 2, -33.9060283, 25.400682),
    (12, 11, 2, 2, -33.9059108, 25.4014802),
    (12, 1, 2, 2, -33.9047886, 25.400433),
    (13, 11, 2, 2, -33.9050491, 25.3999855),
    (13, 1, 2, 2, -33.9074656, 25.3978243),
    (14, 11, 2, 2, -33.9082032, 25.3971096),
    (14, 1, 2, 2, -33.9075824, 25.3987552),
    (15, 11, 2, 2, -33.9081481, 25.3991197),
    (15, 1, 2, 2, -33.912083, 25.3986686),
    (16, 11, 2, 2, -33.912523, 25.3981698),
    (16, 1, 2, 2, -33.908885, 25.3981382),
    (17, 11, 2, 2, -33.9091447, 25.397616),
    (17, 1, 2, 2, -33.9115979, 25.3963522),
    (18, 11, 2, 2, -33.911809, 25.3961807),
    (18, 1, 2, 2, -33.9080226, 25.3941332)
) AS v(hole, poi, location, side_fw, lat, lng);

COMMIT;
