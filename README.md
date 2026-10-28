# Appointment Booking System

A full-stack system that lets customers book, reschedule and cancel branch appointments,
and sends a simulated confirmation for each change.

- **Backend:** Java 21, Spring Boot 3, PostgreSQL, Flyway
- **Frontend:** Angular 20, TypeScript, served by nginx
- **Packaging:** Docker, one command to run everything

```
Browser ──► nginx :3000 ─┬─ /      static Angular app
                         └─ /api   ──► Spring Boot :8080 ──► PostgreSQL :5432
```

## Quick start

You need Docker with Compose v2. Nothing else.

```bash
git clone <repository-url>
cd appointment-booking-system
docker compose up --build
```

When the logs settle, open **http://localhost:3000**.

The first build downloads dependencies and takes a few minutes. Later builds use the cache.
Stop everything with `Ctrl+C`, and remove the containers and database volume with:

```bash
docker compose down -v
```

### Try it

1. Open http://localhost:3000, pick a service, a branch, a date and a time, enter your details,
   and book. You get a reference like `CAP-7K2M9X`.
2. Watch the backend logs: `docker compose logs -f backend`. The simulated confirmation appears
   as `SIMULATED EMAIL to=t***@example.com ...` and `SIMULATED SMS ...`, with contact details masked.
3. Open **Manage booking**, enter the reference and the email you used, then reschedule or cancel.
4. Open the app in two tabs, select the same slot in both, and book in each. The second tab is
   told the time was just taken and its grid refreshes.

Seed data is loaded automatically: four branches, open Monday to Friday 08:30 to 17:00,
Saturday 08:30 to 12:30, closed on Sunday, with three service types.

## Run for development

Use this for live reload and debugging. You need Java 21, Node 22 and Docker (for the database).

```bash
# 1. Database
docker compose up -d db

# 2. Backend, on http://localhost:8080
cd backend/booking
./mvnw spring-boot:run

# 3. Frontend, on http://localhost:4200 (new terminal)
cd frontend
npm ci
npm start
```

The dev server proxies `/api` to port 8080, so the Angular code uses relative URLs in every
environment. On Windows use `mvnw.cmd` instead of `./mvnw`.

Backend health check: http://localhost:8080/actuator/health

## Test

| What | Command | Needs |
|---|---|---|
| Backend unit tests | `cd backend && ./mvnw test` | Java 21 |
| Backend unit and integration
All endpoints live under `/api`. Errors use RFC 7807 problem responses.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/branches` | List branches |
| GET | `/api/service-types` | List services |
| GET | `/api/branches/{id}/availability?date=YYYY-MM-DD` | Free slots for a branch on a date |
| POST | `/api/appointments` | Book a slot. Requires an `Idempotency-Key` header |
| POST | `/api/appointments/lookup` | Find a booking by reference and email |
| POST | `/api/appointments/{reference}/reschedule` | Move a booking to another slot |
| POST | `/api/appointments/{reference}/cancel` | Cancel a booking |

Status codes you will see: `201` booked, `200` replayed idempotent request, `400` validation
failed, `404` no booking for that reference and email, `409` slot taken or booking changed
concurrently, `422` business rule violated (for example the reschedule limit).

Example:

```bash
curl -i -X POST http://localhost:3000/api/appointments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "branchId": "11111111-1111-1111-1111-111111111111",
    "serviceType": "GENERAL_ENQUIRY",
    "start": "<a slot start from the availability endpoint>",
    "customerName": "Thandi Nkosi",
    "customerEmail": "thandi@example.com",
    "customerPhone": "+27821234567"
  }'
```

## Configuration

Set these as environment variables (for Compose, in a `.env` file next to `docker-compose.yml`).

| Variable | Default | Purpose |
|---|---|---|
| `DB_PASSWORD` | `booking` | Database password. The default is for local demos only |
| `DB_URL` | `jdbc:postgresql://localhost:5432/booking` | Backend database URL (set by Compose) |
| `DB_USER` | `booking` | Backend database user |

Booking rules are in `backend/src/main/resources/application.yml` under `booking`:
minimum lead time (1 hour), booking window (60 days), reschedule cut-off (2 hours before the
appointment) and maximum reschedules per booking (2).

## Project structure

```
.
├── backend/                 Spring Boot API
│   └── src/main/java/.../booking
│       ├── config/          clock, async, typed properties
│       ├── common/          error handling, PII masking
│       ├── slot/            slot generation (pure logic, no Spring)
│       ├── catalog/         branches and service types
│       ├── appointment/     booking, reschedule, cancel, lookup
│       └── notification/    simulated email and SMS
├── frontend/                Angular app (see frontend/README.md)
├── docker-compose.yml       database, backend and frontend
└── README.md
```

Dependencies point one way: `notification` depends on `appointment`, which depends on
`catalog` and `slot`. Booking code never calls the notification code. It publishes events, which
is what lets a real email or SMS provider replace the simulator without touching booking logic.

## Design decisions

- **The database prevents double booking, not the application.** A partial unique index on
  `(branch_id, start_at) WHERE status = 'CONFIRMED'` allows one confirmed booking per branch per
  slot. Cancelling or moving a booking frees the slot with no cleanup code. Availability checks
  only improve the user experience; the index guarantees correctness under races.
- **Idempotent booking.** Each booking request carries an `Idempotency-Key`. A retry returns the
  original booking instead of creating a second one, including when two retries race.
- **Time zones.** Everything is stored and transmitted in UTC. Each branch has its own zone, and
  slots are generated in it. The frontend formats times in the branch's zone, not the browser's.
- **Reschedule keeps the reference.** A reschedule is one atomic update of the same booking,
  not cancel-and-rebook, so the customer never holds zero bookings and the reference does not change.
- **Ownership without accounts.** A booking is managed with its reference plus the email used
  to book it. A wrong reference and a wrong email return the identical `404`, so references
  cannot be probed. Lookup is a `POST` so the email stays out of URLs and logs.
- **Notifications run after commit, asynchronously.** A rolled-back booking never sends a
  confirmation, and a failing provider never fails a booking (the failure is recorded).
- **PII.** Email and phone are never returned in API responses. They are masked in logs and in
  stored notification records.
- **Same origin.** nginx serves the app and proxies `/api`, so no CORS configuration exists.
  nginx also sets security headers and a content security policy.
- **Containers.** Multi-stage builds, slim runtime images, non-root users, health checks, and
  only the frontend port published.

## Trade-offs and what I would do next

- **No authentication.** Reference plus email is the stated model. Real accounts and sessions
  would replace it.
- **Notifications can be lost on a crash** between commit and delivery. The production answer
  is a transactional outbox with a retrying poller.
- **One booking per branch per slot.** Per-branch capacity (several tellers) is a schema and
  index change, not a redesign.
- **A replayed idempotency key with a different payload** returns the original booking. A
  stricter version stores a request hash and rejects mismatches with `422`.
- **Seed data runs as a normal migration**, so it also runs in production. A real project would
  move it to a dev-only location.
- **Not built yet:** rate limiting on the booking and lookup endpoints, OpenAPI documentation,
  correlation IDs in logs, a CI pipeline, and browser end-to-end tests (Playwright).
- **Frontend:** the 60-day booking window is duplicated as a constant. A config endpoint would
  remove the duplication.

## Troubleshooting

- **Port already in use:** change the left-hand port in `docker-compose.yml` (for example
  `"3001:8080"` for the frontend), or stop whatever is using 3000, 8080 or 5432.
- **Backend tests fail immediately:** the Docker daemon is not running. Start Docker and retry.
- **Database errors after changing a migration:** reset the local database with
  `docker compose down -v`, then start again.
- **Page loads but data is missing:** the backend may still be starting. Check
  `docker compose ps` and wait for the backend to report `healthy`.
