# Docker Deployment Guide

For this customized project's combined development and deployment setup, use the [new shared Docker stack](../docker/README.md). The remainder of this document describes the legacy upstream Compose/Jib setup; this repository now also provides a Dockerfile and `docker/compose*.yml` for builds from local source.

Practical guide to the Docker Compose setup in this repo, written from direct inspection of [`docker-compose.yml`](../docker-compose.yml), [`docker-compose-postgresql.yml`](../docker-compose-postgresql.yml), [`docker-compose-community-app.yml`](../docker-compose-community-app.yml), the Jib config in [`fineract-provider/build.gradle`](../fineract-provider/build.gradle), and the top-level [`README.md`](../README.md). There is no `Dockerfile` — the server image is built by the **Jib** Gradle plugin, not a traditional Docker build.

## 1. Compose files at a glance

| File | Purpose | Services |
|---|---|---|
| `docker-compose.yml` | Backend on **MariaDB** (default) | `fineractmysql`, `fineract-server` |
| `docker-compose-postgresql.yml` | Backend on **PostgreSQL** | `fineractpostgresql`, `fineract-server` |
| `docker-compose-community-app.yml` | Web UI (mifos community-app) | `community-app` |

**Pick one DB stack, not both.** Both DB compose files define a service named `fineract-server` bound to host port `8443`. Running them together will conflict on the port and on the container/service name. Choose MariaDB or PostgreSQL, then optionally layer the UI compose file on top.

## 2. Building the server image

The `fineract-server` service references image `fineract:latest`, which is **not** pulled from a registry by default — it must be built locally first via Jib:

```bash
./gradlew :fineract-provider:jibDockerBuild -x test
```

This reads the `jib { ... }` block in `fineract-provider/build.gradle`:
- Base image: `azul/zulu-openjdk-alpine:17`
- Produces local tags: the current `project.version` and `latest`
- Exposes `8080/tcp` and `8443/tcp`, runs as `nobody:nogroup`
- Bakes in JVM flags (`-XX:+UseContainerSupport`, `MinRAMPercentage=25`/`MaxRAMPercentage=80`, module `--add-opens` for Java 17, etc.) and args (`-Duser.timezone=UTC`, `-Dfile.encoding=UTF-8`)
- Bundles both `mariadb-java-client` and `postgresql` JDBC drivers, so the same built image works against either compose stack — only the `FINERACT_HIKARI_*` env vars differ

Re-run `jibDockerBuild` after any server code change; `docker-compose up` will not rebuild it for you.

## 3. Running the stack

MariaDB stack:
```bash
docker-compose up -d
```

PostgreSQL stack:
```bash
docker-compose -f docker-compose-postgresql.yml up -d
```

Add the web UI on top of either one (combine compose files so they share a network/project):
```bash
docker-compose -f docker-compose.yml -f docker-compose-community-app.yml up -d
```

Podman is a supported drop-in per the README (`podman-compose` instead of `docker-compose`).

### Verifying it's up
- `fineract-server` has a healthcheck that polls port 8443 internally; `docker-compose ps` will show `healthy` once ready.
- Externally, poll `https://localhost:8443/fineract-provider/actuator/health` until it returns `{"status":"UP"}`.
- The API uses a self-signed cert — visit `https://localhost:8443` in a browser first and accept the certificate, otherwise the UI's calls to the API will fail with a misleading error.
- UI (if started): `http://localhost:9090/?baseApiUrl=https://localhost:8443/fineract-provider&tenantIdentifier=default`, login `mifos` / `password`.

## 4. Known gotchas in the current compose files

- **`JAVA_TOOL_OPTIONS="-Xmx1G"` is quoted inside a YAML env entry.** In both `docker-compose.yml` and `docker-compose-postgresql.yml` the line is `- JAVA_TOOL_OPTIONS="-Xmx1G"`. Compose does not strip the inner quote characters, so the container actually receives the literal string `"-Xmx1G"` (with quotes) as the env var value. Verify with `docker exec <container> env | grep JAVA_TOOL_OPTIONS` before relying on it — if the JVM logs an "Picked up JAVA_TOOL_OPTIONS" warning with quotes still attached, drop the quotes in the compose file (`JAVA_TOOL_OPTIONS=-Xmx1G`).
- **Database state is not persisted by default.** `fineractmysql`/`fineractpostgresql` only mount config/init scripts (`server_collation.cnf`, `docker-entrypoint-initdb.d`) — there's no volume for the actual data directory (`/var/lib/mysql` or `/var/lib/postgresql/data`). `docker-compose down` (or any container recreation) wipes all tenant data. Add a named volume for that path if you need data to survive restarts; this is fine for throwaway dev/demo use but not for anything you want to keep.
- **`fineract-server`'s only persisted path is `./fineract-provider/build/data:/data`** — this is where the embedded H2/temp working data and uploaded documents typically live, not the tenant DB itself (that's in the MySQL/Postgres container, see above).
- **Only one DB backend stack should be up at a time** — same `fineract-server` service name/port in both files; don't `up -d` both simultaneously.
- Passwords in these files (`skdcnwauicn2ucnaecasdsajdnizucawencascdca`) are dev/demo defaults checked into source — fine for local use, must be overridden (e.g. via an env file or `docker-compose.override.yml`) for anything shared or long-lived.

## 5. Key environment variables (`fineract-server`)

Connection pool (Hikari) — must match whichever DB stack you're running:
```
FINERACT_HIKARI_DRIVER_SOURCE_CLASS_NAME   org.mariadb.jdbc.Driver | org.postgresql.Driver
FINERACT_HIKARI_JDBC_URL                   jdbc:mariadb://fineractmysql:3306/fineract_tenants | jdbc:postgresql://fineractpostgresql:5432/fineract_tenants
FINERACT_HIKARI_USERNAME / _PASSWORD
```
Other `FINERACT_HIKARI_*` vars (pool size, timeouts, prepared-statement caching) have sane defaults in `application.properties` — only override if tuning.

Default tenant bootstrap: `FINERACT_DEFAULT_TENANTDB_*` (hostname, port, uid/pwd, timezone, identifier, name, description) — used to auto-create the `default` tenant on first boot.

Security (mutually exclusive — enabling both fails startup):
```
FINERACT_SECURITY_BASICAUTH_ENABLED=true|false
FINERACT_SECURITY_OAUTH_ENABLED=true|false
FINERACT_SECURITY_2FA_ENABLED=true|false
```

SSL: `FINERACT_SERVER_SSL_ENABLED` (default true), `FINERACT_SERVER_SSL_KEY_STORE`, `FINERACT_SERVER_SSL_KEY_STORE_PASSWORD`. If you disable SSL, also set `FINERACT_SERVER_PORT=8080` and update the compose `ports:` mapping and healthcheck accordingly — none of that is done automatically.

`FINERACT_NODE_ID` — required for the node-aware scheduler; keep unique per instance if you ever scale to multiple `fineract-server` replicas (not something the current single-replica compose files handle).

## 6. Stopping / resetting

```bash
docker-compose down          # stops and removes containers (DB data lost, see gotchas above)
docker-compose down -v       # also removes named volumes, if you added any
```

## 7. Not covered by these compose files

- Kubernetes deployment is a separate path (`kubernetes/kubectl-startup.sh`, Minikube instructions) — see README §"Instructions to run on Kubernetes".
- These files are dev/demo-oriented (plaintext passwords, no data persistence, single node). For anything resembling production, add: persistent volumes for DB data, secrets management instead of inline env passwords, a real TLS cert instead of the embedded self-signed one, and multi-node `FINERACT_NODE_ID` planning if scaling the server.
