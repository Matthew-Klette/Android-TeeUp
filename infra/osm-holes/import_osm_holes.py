#!/usr/bin/env python3
"""
Builds TeeUp's hole map data for one golf course from OpenStreetMap.

Reads every golf=hole line, golf=green outline and golf=pin inside one
leisure=golf_course outline (via the Overpass API), turns each hole into the
golfapi.io-style point list the API stores (see api/TeeUp.Api/Models/HoleCoordinate.cs),
and writes two files from that single source so they can never drift apart:

  --sql    an idempotent seed script for the HoleCoordinates table
  --asset  the same holes in the API's HoleLayoutDto JSON shape, bundled in the
           Android app as an offline fallback (HoleLayoutSource.kt)

Per hole:
  tee           first node of the OSM hole line (poi 11)
  green middle  last node of the hole line, which OSM mappers put on the pin (poi 1, location 2)
  doglegs       interior nodes of the hole line (poi 9)
  green front/back  only when the green has a mapped outline: the outline's nearest and
                furthest points along the line of play into the pin (poi 1, location 1/3).
                Holes without an outline get no front/back rather than an invented one.

Usage (Fancourt Links demo course):
  python3 -I infra/osm-holes/import_osm_holes.py --osm-way 47154775 --course-name "Fancourt Links" \
      --sql infra/seed-fancourt-links-holes.sql \
      --asset android/app/src/main/assets/holes/fancourt-links.json

The public Overpass servers are often at capacity, so the fetch retries with backoff;
--input <file> reuses a previously saved Overpass response instead (--save-raw writes one).

Hole data (c) OpenStreetMap contributors, available under the ODbL 1.0.
"""

import argparse
import json
import math
import sys
import time
import urllib.parse
import urllib.request

OVERPASS_URL = "https://overpass-api.de/api/interpreter"
USER_AGENT = "TeeUp-PROG7314-student-project"
MAX_FETCH_ATTEMPTS = 6
FIRST_RETRY_DELAY_SECONDS = 15

# golfapi.io coordinate codes (GolfAPI, n.d.), mirrored by the API's PointOfInterest/PoiLocation enums.
POI_GREEN = 1
POI_DOGLEG = 9
POI_FRONT_TEE = 11
LOCATION_FRONT = 1
LOCATION_MIDDLE = 2
LOCATION_BACK = 3
SIDE_CENTER = 2

# A green outline counts as this hole's green when its centre is this close to the pin.
GREEN_MATCH_RADIUS_M = 40.0
EARTH_RADIUS_M = 6_371_000.0
COORD_DECIMALS = 7


def build_query(course_way_id):
    return (
        "[out:json][timeout:90];"
        f"way({course_way_id})->.course;"
        ".course out tags geom;"
        ".course map_to_area->.area;"
        "(way[golf](area.area);node[golf](area.area););"
        "out tags geom;"
    )


def fetch_overpass(query):
    body = urllib.parse.urlencode({"data": query}).encode()
    delay = FIRST_RETRY_DELAY_SECONDS
    for attempt in range(1, MAX_FETCH_ATTEMPTS + 1):
        request = urllib.request.Request(OVERPASS_URL, data=body, headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                return json.load(response)
        except (OSError, ValueError) as error:
            print(f"Overpass attempt {attempt}/{MAX_FETCH_ATTEMPTS} failed: {error}", file=sys.stderr)
            if attempt == MAX_FETCH_ATTEMPTS:
                raise SystemExit("Overpass is unavailable; try again later or pass --input.")
            time.sleep(delay)
            delay *= 2


def to_local_xy(origin, point):
    """Equirectangular projection to metres around origin; accurate to well under a metre at hole scale."""
    lat0 = math.radians(origin[0])
    x = math.radians(point[1] - origin[1]) * math.cos(lat0) * EARTH_RADIUS_M
    y = math.radians(point[0] - origin[0]) * EARTH_RADIUS_M
    return x, y


def from_local_xy(origin, x, y):
    lat0 = math.radians(origin[0])
    lat = origin[0] + math.degrees(y / EARTH_RADIUS_M)
    lng = origin[1] + math.degrees(x / (EARTH_RADIUS_M * math.cos(lat0)))
    return lat, lng


def distance_m(a, b):
    x, y = to_local_xy(a, b)
    return math.hypot(x, y)


def polygon_centre(points):
    return sum(p[0] for p in points) / len(points), sum(p[1] for p in points) / len(points)


def green_front_and_back(pin, approach_from, outline):
    """Projects the green outline onto the line of play through the pin and returns the
    points on that line where the green starts (front) and ends (back)."""
    ax, ay = to_local_xy(pin, approach_from)
    length = math.hypot(ax, ay)
    if length == 0:
        return None
    # Unit vector pointing from the pin back towards the golfer; front is on that side.
    ux, uy = ax / length, ay / length
    projections = [x * ux + y * uy for x, y in (to_local_xy(pin, p) for p in outline)]
    front_offset, back_offset = max(projections), min(projections)
    return from_local_xy(pin, ux * front_offset, uy * front_offset), from_local_xy(pin, ux * back_offset, uy * back_offset)


def geometry(element):
    return [(node["lat"], node["lon"]) for node in element.get("geometry", [])]


def build_holes(elements):
    hole_ways = [e for e in elements if e["type"] == "way" and e["tags"].get("golf") == "hole"]
    green_outlines = [geometry(e) for e in elements if e["type"] == "way" and e["tags"].get("golf") == "green"]

    holes = {}
    for way in hole_ways:
        ref = way["tags"].get("ref", "")
        line = geometry(way)
        if not ref.isdigit() or len(line) < 2:
            print(f"Skipping OSM way {way['id']}: no numeric ref or too few nodes", file=sys.stderr)
            continue
        number = int(ref)
        if number in holes:
            raise SystemExit(f"Hole {number} is mapped twice (OSM way {way['id']}); fix the data first.")
        holes[number] = line

    layouts = []
    for number in sorted(holes):
        line = holes[number]
        tee, pin, doglegs = line[0], line[-1], line[1:-1]
        doglegs.sort(key=lambda p: distance_m(tee, p))
        approach_from = doglegs[-1] if doglegs else tee

        front = back = None
        nearby = [o for o in green_outlines if o and distance_m(polygon_centre(o), pin) <= GREEN_MATCH_RADIUS_M]
        if nearby:
            outline = min(nearby, key=lambda o: distance_m(polygon_centre(o), pin))
            edges = green_front_and_back(pin, approach_from, outline)
            if edges:
                front, back = edges

        layouts.append({"hole": number, "tee": tee, "front": front, "middle": pin, "back": back, "doglegs": doglegs})
    return layouts


def point_json(point):
    if point is None:
        return None
    return {"latitude": round(point[0], COORD_DECIMALS), "longitude": round(point[1], COORD_DECIMALS)}


def asset_json(layouts):
    return [
        {
            "hole": h["hole"],
            "tee": point_json(h["tee"]),
            "greenFront": point_json(h["front"]),
            "greenMiddle": point_json(h["middle"]),
            "greenBack": point_json(h["back"]),
            "doglegs": [point_json(p) for p in h["doglegs"]],
        }
        for h in layouts
    ]


def coordinate_rows(layouts):
    rows = []
    for h in layouts:
        rows.append((h["hole"], POI_FRONT_TEE, LOCATION_MIDDLE, SIDE_CENTER, h["tee"]))
        rows.append((h["hole"], POI_GREEN, LOCATION_MIDDLE, SIDE_CENTER, h["middle"]))
        if h["front"]:
            rows.append((h["hole"], POI_GREEN, LOCATION_FRONT, SIDE_CENTER, h["front"]))
        if h["back"]:
            rows.append((h["hole"], POI_GREEN, LOCATION_BACK, SIDE_CENTER, h["back"]))
        rows.extend((h["hole"], POI_DOGLEG, LOCATION_MIDDLE, SIDE_CENTER, p) for p in h["doglegs"])
    return rows


def sql_literal(text):
    return "'" + text.replace("'", "''") + "'"


def seed_sql(course_name, course_centre, course_way_id, layouts):
    name = sql_literal(course_name)
    values = ",\n    ".join(
        f"({hole}, {poi}, {location}, {side}, {round(p[0], COORD_DECIMALS)}, {round(p[1], COORD_DECIMALS)})"
        for hole, poi, location, side, p in coordinate_rows(layouts)
    )
    lat, lng = (round(v, COORD_DECIMALS) for v in course_centre)
    return f"""-- Hole map seed for {course_name}. GENERATED by infra/osm-holes/import_osm_holes.py from
-- OpenStreetMap way {course_way_id}; re-run the script rather than editing this file.
-- Hole data (c) OpenStreetMap contributors, ODbL 1.0.
--
-- Safe to run more than once: creates the course row if it's missing, moves an existing
-- row's pin to the course centre, then replaces that course's hole coordinates.
-- Fails (and rolls back) if more than one course has this exact name.
BEGIN;

INSERT INTO "Courses" ("Id", "Name", "Latitude", "Longitude")
SELECT gen_random_uuid(), {name}, {lat}, {lng}
WHERE NOT EXISTS (SELECT 1 FROM "Courses" WHERE "Name" = {name});

UPDATE "Courses" SET "Latitude" = {lat}, "Longitude" = {lng} WHERE "Name" = {name};

DELETE FROM "HoleCoordinates"
WHERE "CourseId" = (SELECT "Id" FROM "Courses" WHERE "Name" = {name});

INSERT INTO "HoleCoordinates" ("Id", "CourseId", "Hole", "Poi", "Location", "SideFw", "Latitude", "Longitude")
SELECT gen_random_uuid(), (SELECT "Id" FROM "Courses" WHERE "Name" = {name}),
       v.hole, v.poi, v.location, v.side_fw, v.lat, v.lng
FROM (VALUES
    {values}
) AS v(hole, poi, location, side_fw, lat, lng);

COMMIT;
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--osm-way", type=int, required=True, help="OSM way id of the leisure=golf_course outline")
    parser.add_argument("--course-name", required=True, help="Course name exactly as stored in the Courses table")
    parser.add_argument("--sql", required=True, help="Seed SQL output path")
    parser.add_argument("--asset", required=True, help="Android asset JSON output path")
    parser.add_argument("--input", help="Use a saved Overpass response instead of fetching")
    parser.add_argument("--save-raw", help="Also save the Overpass response here")
    args = parser.parse_args()

    if args.input:
        with open(args.input, encoding="utf-8") as f:
            data = json.load(f)
    else:
        data = fetch_overpass(build_query(args.osm_way))
        if args.save_raw:
            with open(args.save_raw, "w", encoding="utf-8") as f:
                json.dump(data, f)

    elements = data.get("elements", [])
    course = next((e for e in elements if e["type"] == "way" and e["id"] == args.osm_way), None)
    if course is None or not geometry(course):
        raise SystemExit(f"OSM way {args.osm_way} wasn't in the response; check the id.")

    layouts = build_holes(elements)
    if not layouts:
        raise SystemExit("No golf=hole lines inside that course; nothing to write.")

    with open(args.asset, "w", encoding="utf-8") as f:
        json.dump(asset_json(layouts), f, indent=2)
        f.write("\n")
    with open(args.sql, "w", encoding="utf-8") as f:
        f.write(seed_sql(args.course_name, polygon_centre(geometry(course)), args.osm_way, layouts))

    with_edges = sum(1 for h in layouts if h["front"])
    print(f"{args.course_name}: {len(layouts)} holes, {with_edges} with green front/back -> {args.sql}, {args.asset}")


if __name__ == "__main__":
    main()

# References:
#
# GolfAPI (n.d.). GolfAPI documentation: GET /coordinates/{id}. [online] Postman. Available at:
#   <https://documenter.getpostman.com/view/1756312/UVeDsT2b> [Accessed 23 Oct. 2026].
# OpenStreetMap Wiki (n.d.). Tag:golf=hole. [online] Available at:
#   <https://wiki.openstreetmap.org/wiki/Tag:golf%3Dhole> [Accessed 23 Oct. 2026].
# OpenStreetMap Wiki (n.d.). Overpass API. [online] Available at:
#   <https://wiki.openstreetmap.org/wiki/Overpass_API> [Accessed 23 Oct. 2026].
