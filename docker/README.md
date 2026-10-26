# API + frontend on Docker

This stack builds **your local API and frontend code**. It uses Java 17, the repository's Gradle wrapper, AngularJS/Grunt/Ruby, Nginx, and MariaDB 10.11. The older root `docker-compose*.yml` files remain available for their upstream workflows; do not combine them with this stack.

## Prerequisites

- Docker Desktop in Linux-container mode (Windows), or Docker Engine with Compose v2 on Linux.
- Keep both repositories next to each other:
  ```text
  project/
    Score-Management-API/
    Score-Management-System-Frontend/
  ```
  Otherwise set `FRONTEND_PATH` in the env file. Relative paths resolve from this `docker/` directory; absolute Windows paths should use forward slashes.
- Allow at least 8 GB of Docker memory for the initial Java build, API, and database. First builds download substantial Gradle, npm, Bower, and Ruby dependencies.

## Development (Windows PowerShell)

Run from the API repository:

```powershell
./docker/stack.ps1 -Mode dev -Action up
./docker/stack.ps1 -Mode dev -Action ps
./docker/stack.ps1 -Mode dev -Action logs
```

The first command creates ignored `docker/.env.dev` from the example if needed. Open **http://localhost:9090**. Both UI and `/fineract-provider/` API requests use that URL; no API URL query parameter or self-signed certificate acceptance is needed. A fresh tenant has the upstream login `mifos` / `password`.

- Java source is mounted read-only at `/source` and synced into a native Docker workspace at API startup. Gradle project caches and compiled API/buildSrc output also live in Docker volumes, independently of Windows IDE builds. This avoids slow Java/Lombok file inspection over Windows mounts. After API changes run `./docker/stack.ps1 -Mode dev -Action restart-api`; the latest sources (including deletions) are synced and Gradle recompiles before starting the API. This is an explicit restart workflow, not Java hot reload. Development logs include Gradle build progress.
- Frontend `app/` and `Gruntfile.js` are bind mounted. Grunt watches JS, HTML, translations, and SCSS; browser LiveReload uses localhost port 35729. Refresh the browser if needed. Dependency directories stay inside Docker.
- After changing npm/Ruby manifests or Dockerfiles, rerun `up` to rebuild. Bower dependencies persist in a named volume; after editing `bower.json`, rebuild and run:
  ```powershell
  docker compose --env-file docker/.env.dev -p score-dev -f docker/compose.yml -f docker/compose.dev.yml run --rm ui-dev bower install --allow-root
  ./docker/stack.ps1 -Mode dev -Action up
  ```
- Direct API: http://localhost:8080/fineract-provider/api/v1. API readiness: http://localhost:9090/fineract-provider/actuator/health. Database tools can connect to localhost:3307, user `fineract`, password from `.env.dev`.
- Database/API ports bind to loopback in development. The project already adds a JDWP agent to `bootRun`; debugger access is not published by this stack.
- `LIVERELOAD_PORT` should remain 35729 for automatic reload; if changed, use manual browser refresh or also change Grunt's reload port.

## Deployment

Run from the API repository:

```powershell
Copy-Item docker/.env.prod.example docker/.env.prod
# Edit docker/.env.prod: set DB_ROOT_PASSWORD and DB_PASSWORD.
./docker/stack.ps1 -Mode prod -Action up
./docker/stack.ps1 -Mode prod -Action ps
```

Production builds a Java executable JAR and compiled frontend assets into runtime images. There are no source bind mounts, build tools, dev server, or public database/API ports in the production containers. The API runs as a non-root user. Database initialization creates the tenant databases and grants the `fineract` user access only to those schemas; Liquibase migrates them on API startup.

The default public entry point binds to **127.0.0.1:9090**. On a server, put an HTTPS reverse proxy in front of it, route the entire site including `/fineract-provider/`, and forward `Host`, `X-Forwarded-Proto`, and `X-Forwarded-For`. TLS terminates at that proxy; internal API traffic uses HTTP. Set `WEB_BIND=0.0.0.0` only when the frontend needs to be reachable directly or by an external proxy. Configure your domain/certificate at that proxy before serving real users. Change the bootstrap login after first deployment.

Set `API_IMAGE`, `FRONTEND_IMAGE`, and `IMAGE_TAG` for versioned images. To deploy prebuilt images from your registry, build and push both production images on your build machine, copy the repositories/configuration to the server, set the same image names/tag in its env file, then run:

```sh
docker compose --env-file docker/.env.prod -p score-prod -f docker/compose.yml -f docker/compose.prod.yml pull
docker compose --env-file docker/.env.prod -p score-prod -f docker/compose.yml -f docker/compose.prod.yml up -d --no-build
```

For a deployment built directly on the server, use `up -d --build` instead. API compilation skips tests and license/RAT checks for packaging; run your normal project verification before releasing. Application upgrades can migrate the database, so back up both database schemas and uploaded documents before upgrading; reverting an image does not undo database migrations.

## Linux/macOS or direct Compose commands

From the API repository, copy the appropriate example env file, then use:

```sh
cp docker/.env.dev.example docker/.env.dev
docker compose --env-file docker/.env.dev -p score-dev -f docker/compose.yml -f docker/compose.dev.yml up -d --build
docker compose --env-file docker/.env.dev -p score-dev -f docker/compose.yml -f docker/compose.dev.yml restart api
docker compose --env-file docker/.env.dev -p score-dev -f docker/compose.yml -f docker/compose.dev.yml logs -f --tail 100
docker compose --env-file docker/.env.dev -p score-dev -f docker/compose.yml -f docker/compose.dev.yml down
```

For deployment replace `dev` with `prod` in the env path, project name, and override filename. Start only one mode at a time with the default web port, or assign different `WEB_PORT` values.

## Persistence and troubleshooting

- `score-dev` and `score-prod` use separate named database/document volumes. Ordinary `down` preserves data. `down -v` deletes named volumes and their contents; use it only for an intentional reset.
- Documents are stored under `/data/.fineract`, matching this API's `user.home` storage implementation. Back up the `documents` volume as well as `fineract_tenants` and `fineract_default`.
- Existing local databases are **not automatically imported**. Restore dumps into the Docker database and adjust tenant connection metadata (`tenant_server_connections`) to host `db`, port 3306, and the configured `fineract` credentials before using imported tenants.
- Env-file database passwords initialize empty volumes only. Changing them later does not rotate existing DB users or existing tenant connection metadata.
- The frontend waits for the API health endpoint; the API waits for the database. First-run migrations and compilation can take several minutes. Check `logs` for the relevant service if startup fails.
- No frontend page: inspect `ui-dev` logs in development. API unhealthy: inspect `api` and `db` logs. If the browser shows a cached HTTPS API URL, remove the old `baseApiUrl` query parameter and reload http://localhost:9090.
- `.env.dev` and `.env.prod` are ignored by Git and excluded from Docker builds. Passwords containing `$` should be single-quoted in Compose env files to avoid interpolation.

Compose behavior references: [startup health dependencies](https://docs.docker.com/compose/how-tos/startup-order/), [environment interpolation](https://docs.docker.com/reference/compose-file/interpolation/).
