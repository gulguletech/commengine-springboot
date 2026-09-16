# CommEngine — Spring Boot / Maven port

This is a 1:1 port of the CommEngine Node/Express backend to Spring Boot 3 + Maven,
covering every feature of the original app: phone/OTP auth (MSG91), profile
management + avatar upload (Cloudinary), audio rooms (seats, join requests,
passcodes, Agora RTC tokens), live video rooms (viewers, comments, Agora RTC
tokens), 1:1 chat (REST + real-time), and friend requests — all over the same
JSON shapes the existing React Native app already expects.

## What changed vs. the Node app (read this first)

1. **Real-time layer now runs as a second internal process**, since a servlet
   container (Tomcat, serving REST) and a Netty-based Socket.IO server can't
   bind the same OS port inside one JVM. Internally:
   - REST API listens on `SERVER_PORT` (default 5000)
   - Socket.IO listens on `SOCKETIO_PORT` (default 5001)

   **This is invisible to the frontend.** The bundled `Dockerfile` runs a small
   Nginx in front of both, listening on the *one* public port your host
   assigns (Render's `$PORT`, etc.) and routing by path: `/socket.io/*` →
   the Socket.IO server, everything else → the REST API. Your existing
   `API_BASE_URL` / `SOCKET_URL` (same host, no port) keep working exactly
   as they do today against the Node app — see the Docker/Render section below.

2. **One small required frontend change for socket auth.** `socket.io-client`
   sends its `auth: { token }` payload as part of the Socket.IO protocol
   handshake, a layer above what the Java Socket.IO server library
   (`netty-socketio`) can inspect at connection time. To keep this reliable
   across transports, the JWT is also read from a URL query parameter. In
   `src/lib/socket.ts`, change:
   ```ts
   io(SOCKET_URL, { auth: { token } })
   ```
   to:
   ```ts
   io(SOCKET_URL, { auth: { token }, query: { token } })
   ```
   That's it — no other frontend changes are needed. Every event name, payload
   shape, and REST response shape is unchanged.

3. **Audio room seats: 8 → 12.** `AudioRoomLiveState.SEAT_COUNT` is now 12
   (was 8, matching the original). If your frontend renders the seat grid
   from a fixed-size constant, update `AUDIO_SEAT_COUNT` in
   `src/constants/config.ts` from `8` to `12` as well — it's currently a
   standalone constant not wired into a screen in the frontend files I was
   given, so I couldn't confirm where else it's consumed; grep your full
   frontend repo for `AUDIO_SEAT_COUNT` to be sure nothing else assumes 8.

4. **Bug fix: Agora token expiry.** The original `config/agora.js` passed an
   *absolute* unix timestamp (`Date.now()/1000 + EXPIRY_SECONDS`) into
   `RtcTokenBuilder.buildTokenWithUid`, but that function expects a *relative*
   "seconds from now" duration for both the token and privilege expiry. In
   practice this made the encoded expiry a nonsensical, effectively-huge
   number. The Spring Boot port (`AgoraService` + the ported `AccessToken2`)
   passes the relative duration correctly.

5. **Bug fix: friend requests were dead code.** `src/routes/index.js` (the
   file Express actually loads) never mounts `/friends` — only the unused
   `src/routes/routes-index.js` does. The whole friend-request feature
   (send/accept/reject/list, `friendController.js`, the Prisma model, the
   socket events) was fully implemented but unreachable. This port mounts
   `/api/friends` properly, so the feature now actually works.

## Tech stack

- Spring Boot 3.3 (Java 17), Maven
- Spring Data JPA + PostgreSQL (same schema shape as the Prisma models)
- JWT via `jjwt` (same claims/expiry semantics as `jsonwebtoken`)
- `netty-socketio` for the real-time layer — a real Socket.IO **protocol**
  server, compatible with `socket.io-client` v4 used by the RN app (no
  frontend socket rewrite needed beyond the one line above)
- Cloudinary Java SDK for uploads
- MSG91 OTP widget verification via a plain HTTPS call (`RestClient`)
- Agora RTC token generation, ported directly from Agora's own `agora-token`
  npm package algorithm (not reimplemented from memory) for byte-for-byte
  compatible tokens

## Configuration / environment variables

Everything is environment-variable driven (see `src/main/resources/application.yml`).

| Variable | Required | Purpose |
|---|---|---|
| `DATABASE_URL` | yes | JDBC Postgres URL, e.g. `jdbc:postgresql://HOST:5432/DBNAME` |
| `DATABASE_USERNAME` | yes | Postgres user |
| `DATABASE_PASSWORD` | yes | Postgres password |
| `JWT_SECRET` | yes | Auth token signing secret — use a long random string |
| `JWT_EXPIRES_IN` | no (default `30d`) | Full auth token lifetime |
| `JWT_TEMP_EXPIRES_IN` | no (default `15m`) | Temp (pre-profile-completion) token lifetime |
| `MSG91_AUTH_KEY` | yes | MSG91 account auth key, for OTP widget verification |
| `CLOUDINARY_CLOUD_NAME` | yes | Cloudinary account |
| `CLOUDINARY_API_KEY` | yes | Cloudinary account |
| `CLOUDINARY_API_SECRET` | yes | Cloudinary account |
| `AGORA_APP_ID` | yes | Agora project App ID |
| `AGORA_APP_CERTIFICATE` | yes | Agora project App Certificate |
| `AGORA_TOKEN_EXPIRY_SECONDS` | no (default `3600`) | RTC token lifetime |
| `CLIENT_URL` | no (default `*`) | Allowed CORS origin(s), comma-separated |
| `JPA_DDL_AUTO` | no (default `update`) | Hibernate schema mode |
| `PORT` | set automatically by Render | The one public port — **do not set this yourself**, and do not confuse it with `SERVER_PORT`/`SOCKETIO_PORT` below |

You do **not** need to set `SERVER_PORT` or `SOCKETIO_PORT` on Render — the
Dockerfile fixes those internally (5000 and 5001) and only Nginx talks to
Render's `$PORT`. They only exist so the two internal servers have stable
ports for Nginx to proxy to.

## Deploying on Render (or any single-port host) with Docker

The included `Dockerfile` builds the jar with Maven, then runs it behind a
small Nginx that solves the "one process, two ports" problem described above:
Nginx binds the host's `$PORT` and proxies `/socket.io/*` to the internal
Socket.IO server and everything else to the internal REST API — so your
existing single-origin `API_BASE_URL` / `SOCKET_URL` config keeps working
unchanged.

**On Render:**
1. New → Web Service → connect this repo (or point it at the Dockerfile).
2. Environment: **Docker** (Render auto-detects the `Dockerfile`).
3. Add the environment variables from the table above (everything except
   `PORT`, `SERVER_PORT`, `SOCKETIO_PORT` — Render injects `PORT` itself).
4. Add a managed Postgres instance (Render → New → PostgreSQL) and set
   `DATABASE_URL` (Render gives you a connection string — split it into
   `DATABASE_URL`/`DATABASE_USERNAME`/`DATABASE_PASSWORD`, or adapt
   `application.yml` to parse Render's single connection string if you
   prefer).
5. Health check path: `/api/health`.
6. Deploy. No port configuration needed in the Render dashboard — it reads
   `$PORT` automatically and Nginx picks it up via `deploy/entrypoint.sh`.

**Building/running locally with Docker:**
```bash
docker build -t commengine .
docker run -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/commengine \
  -e DATABASE_USERNAME=postgres \
  -e DATABASE_PASSWORD=postgres \
  -e JWT_SECRET=dev_secret_change_me \
  -e MSG91_AUTH_KEY=xxx \
  -e CLOUDINARY_CLOUD_NAME=xxx -e CLOUDINARY_API_KEY=xxx -e CLOUDINARY_API_SECRET=xxx \
  -e AGORA_APP_ID=xxx -e AGORA_APP_CERTIFICATE=xxx \
  commengine
```
Everything (REST + Socket.IO) is then reachable at `http://localhost:8080`.

## Build & run without Docker

```bash
mvn clean package
java -jar target/commengine.jar
```
This runs REST on 5000 and Socket.IO on 5001 as two directly-reachable ports
(no Nginx) — fine for local development or a host where you can expose both
ports directly, but not for Render/Railway-style single-port platforms.

## Project layout

```
src/main/java/com/commengine/
  entity/        JPA entities (mirrors the Prisma schema 1:1)
  repository/    Spring Data JPA repositories
  service/       Business logic, ported 1:1 from src/controllers/*.js
  controller/    REST endpoints, ported 1:1 from src/routes/*.js
  socket/        Real-time layer, ported 1:1 from src/sockets/*.js
  security/      JWT + request auth (mirrors src/middleware/auth.js)
  dto/           Request DTOs (validation) + response shaping
  config/        App properties, CORS, Cloudinary
  util/          Agora token algorithm, Agora uid derivation, duration parsing
```

Every REST endpoint, request/response JSON shape, and socket event name is
unchanged from the original app, so the existing React Native frontend
should work against this backend with only the one-line socket change above.

## A note on verification

This project was assembled in a sandboxed environment without access to
Maven Central, so I was not able to run `mvn compile`/`mvn test` or `docker
build` here to confirm a clean build end-to-end. The code was written and
cross-checked carefully (every repository method, DTO mapping, and entity
getter/setter was verified against its usage), and the trickiest pieces —
the Agora token algorithm and the exact Socket.IO event contracts — were
ported directly from the original source rather than reconstructed from
memory. Still, please run `mvn clean package` and `docker build .` locally
as a first step and let me know if anything doesn't build — I'd rather fix
it than have you hunt it down.
