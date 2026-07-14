# Berlin Demo Map

A Kotlin + Spring Boot application that parses the Berlin Police public assembly page, classifies events, geocodes locations, and shows them on an interactive map with a city-impact timeline.

Built as a demo project for a Java/Spring Meetup to showcase idiomatic Kotlin in a Spring Boot context.

## Screenshots

The UI supports three interactive map display modes:

**Points view** — one marker per demonstration, with filters and timeline.  
![Points view](docs/ui/mainscreen.png)

**Heatmap view** — density of protest activity across the city.  
![Heatmap view](docs/ui/heatmap.png)

**PLZ view** — choropleth of event counts per Berlin postal code.  
![PLZ view](docs/ui/plz.png)

---

## Requirements

- JDK 21
- Gradle (wrapper included)
- PostgreSQL 14+ with PostGIS and pgcrypto extensions (or use offline mode)

---

## Local development setup

### PostgreSQL setup

All commands below must be run as the **PostgreSQL superuser** (`postgres`).  
Do **not** use the `berlin_demo` user — it does not have permission to create roles, databases, or extensions.

Connect as superuser:
```bash
psql -U postgres
```

Then run **step 1 and step 2 while connected as `postgres`**:

```sql
-- Step 1: create user and database (connect to any database as postgres)
CREATE USER berlin_demo WITH PASSWORD 'berlin_demo';
CREATE DATABASE berlin_demo OWNER berlin_demo;

-- Step 2: install extensions — MUST connect to berlin_demo as postgres
\c berlin_demo
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

> **IntelliJ tip:** when running step 2, make sure your data source is set to  
> **User = postgres**, **Database = berlin_demo**. Extensions can only be created by a superuser.

Verify the setup:
```bash
psql -U berlin_demo -d berlin_demo -c "SELECT PostGIS_Version();"
```

Start the application — Flyway creates all tables automatically on first run.

### IntelliJ IDEA setup

1. Open the project root in IntelliJ IDEA.
2. Gradle sync runs automatically; if not, click **Reload All Gradle Projects**.
3. Set Project SDK to JDK 21: **File → Project Structure → SDK**.
4. Run the `BerlinProtestTrackerApplication` run configuration, or use the terminal commands below.

---

## Running the application

### macOS / Linux

```bash
JAVA_HOME=~/.jdks/corretto-21.0.11 ./gradlew bootRun
```

### Windows PowerShell

```powershell
$env:JAVA_HOME="C:\path\to\jdk-21"; .\gradlew.bat bootRun
```

The application starts at [http://localhost:8080](http://localhost:8080). Data loads automatically on startup without waiting for the first cron tick.

---

## Offline mode (no PostgreSQL)

Flyway, geocode cache, and external geocoding are all disabled. The pipeline still parses, normalizes, classifies, and builds a snapshot — events appear in the timeline without map geometry.

### macOS / Linux

```bash
JAVA_HOME=~/.jdks/corretto-21.0.11 SPRING_PROFILES_ACTIVE=offline ./gradlew bootRun
```

### Windows PowerShell

```powershell
$env:JAVA_HOME="C:\path\to\jdk-21"; $env:SPRING_PROFILES_ACTIVE="offline"; .\gradlew.bat bootRun
```

### IntelliJ IDEA

Add `SPRING_PROFILES_ACTIVE=offline` to **Environment variables** in the run configuration.

---

## Triggering an import manually

### macOS / Linux

```bash
curl -X POST http://localhost:8080/internal/import/run \
  -H "X-Internal-Token: dev-token"
```

### Windows PowerShell

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/internal/import/run `
  -Headers @{"X-Internal-Token"="dev-token"}
```

The import runs asynchronously; the endpoint returns immediately:
```json
{"status": "accepted", "message": "Import started; poll /api/snapshot/status for progress"}
```
`202 Accepted` when the import started, `409 Conflict` if one is already running. Poll
`/api/snapshot/status` for progress.

---

## Key API endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Map + timeline UI |
| GET | `/api/demonstrations` | Filtered list of events (JSON) |
| GET | `/api/timeline` | Timeline items |
| GET | `/api/demonstrations.geojson` | GeoJSON FeatureCollection |
| GET | `/api/demonstrations.impact-zones.geojson` | City-impact zone polygons |
| GET | `/api/snapshot/status` | Import status (includes `geocodingComplete`) |
| GET | `/api/districts` | Berlin district list |
| GET | `/actuator/health` | Health check |
| POST | `/internal/import/run` | Manual import trigger |

Query parameters for filter endpoints: `date`, `district`, `impactLevel`, `category`, `minImpactScore`.

Example:
```
GET /api/demonstrations?dateFrom=2026-06-26&impactLevel=HIGH
```

The single-page UI at `/` offers three map display modes — **Points**, **Heatmap**, and **PLZ**
(per-postal-code density choropleth) — served by `/api/demonstrations.geojson`,
`/api/demonstrations.impact-zones.geojson`, and `/api/demonstrations.plz-heatmap.geojson`.

---

## Architecture

```
Berlin Police HTML page
  -> BerlinPolicePageClient            (fetch, force UTF-8)
  -> BerlinPolicePageParser (Jsoup)    -> List<RawDemonstrationRow>
  -> DemonstrationNormalizer           -> List<NormalizedDemonstration> + rejected rows
  -> ImpactClassifier (Kotlin DSL)     -> ImpactAssessment (score + reasons)
  -> GeocoderService + Nominatim       -> EventGeometry (Point | Unknown)
  -> DemonstrationReadModel            (immutable AtomicReference snapshot)
  -> Spring MVC API                    -> Thymeleaf + vanilla JS + MapLibre GL
```

Events are not persisted as the primary model. The latest successful import is the current state. A failed import never replaces a good snapshot.

Geocoding resolves an event's location to a map point via Nominatim (address, enriched with the
postal code when available), falling back to the postal-code centroid, and finally to
`EventGeometry.Unknown` — such events still appear in the timeline, just not on the map.

---

## City-impact levels

| Score | Level | Color |
|-------|-------|-------|
| 0–24 | LOW | green |
| 25–49 | MEDIUM | yellow |
| 50–74 | HIGH | orange |
| 75–100 | VERY_HIGH | red |

Impact is a purely **operational** score for moving through the city: route, duration, expected crowd size, time of day, and visible counter-event wording. It is assessed on **events and routes**, not on people. No demographic characteristics — political stance, religion, ethnicity, or community — are used as impact factors, and the public label of every assembly is deliberately neutral.

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `SCRAM authentication error` | Make sure the password is set: `ALTER USER berlin_demo WITH PASSWORD 'berlin_demo';` |
| `database "berlin_demo" does not exist` | Run `CREATE DATABASE berlin_demo OWNER berlin_demo;` as superuser |
| `role "berlin_demo" does not exist` | Run `CREATE USER berlin_demo WITH PASSWORD 'berlin_demo';` as superuser |
| `could not open extension control file … postgis.control` | Install PostGIS: `sudo apt install postgresql-14-postgis-3` or equivalent |
| `Web server failed to start. Port 8080 was already in use` | Kill the occupying process or set `server.port=8081` in `application.yml` |

---

## Running tests

```bash
JAVA_HOME=~/.jdks/corretto-21.0.11 ./gradlew test
```
