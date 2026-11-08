# Testing-laptop deployment

The stack is three Docker images (database, API, frontend), all published as
tags of one Docker Hub repository, `pichoudam/mini_pawnshop_v1`:
`db-<version>`, `api-<version>` and `frontend-<version>`. The testing laptop
needs **only Docker** and the small files in this folder. It pulls the images
itself, so no source code, Java or Node is needed.

## 1. On the build machine (this repo)

```powershell
docker login        # once, as the Docker Hub account that owns pichoudam/mini_pawnshop_v1
powershell -ExecutionPolicy Bypass -File docker\build-images.ps1 -Tag 1.0.0 -Push
```

This builds and pushes `pichoudam/mini_pawnshop_v1:db-1.0.0`, `:api-1.0.0` and
`:frontend-1.0.0`. Use a new `-Tag` for every release. The frontend repo is
expected next to this one (`..\Score-Management-System-Frontend`); pass
`-FrontendPath` otherwise. The first API build takes about 7 minutes. Add
`-NoBuild` to push images that are already built.

Give the testing laptop this folder **without** `images\` and `.env`. Only
`compose.yml`, `.env.example`, `start.ps1`, `start.sh`, `open-firewall.ps1` and
`import-data.ps1` are needed (plus `data\` if you copy data, see below). If the Docker Hub repository is private, also run `docker login` on
the laptop.

For a laptop without internet, add `-Export`. That also writes
`docker\deploy\images\score-images.tar`. Copy the tar along with the folder,
and `start.ps1` loads it instead of pulling.

## 2. On the testing laptop

Requirements: Docker Desktop (Windows/macOS) or Docker Engine + compose plugin
(Linux), with **at least 4 GB RAM** allocated to Docker.

Windows:

```powershell
powershell -ExecutionPolicy Bypass -File start.ps1          # load images, start, wait until healthy
# once, as Administrator, to allow access from other laptops:
powershell -ExecutionPolicy Bypass -File open-firewall.ps1
```

Docker Desktop usually already allows this. On first run Windows asks whether
to allow "Docker Desktop Backend" through the firewall; if you allowed it,
other laptops can connect without running anything. To check:
`Get-NetFirewallRule -DisplayName "Docker Desktop Backend" | Select Profile, Action`.
If another laptop still can't open `http://<IP>:9002/`, run the script above.

`open-firewall.ps1` opens TCP 9002 and 8443 for Private/Domain networks and
for the profile of the network the laptop is on now. Windows often marks
Wi-Fi as **Public**, and the script warns when it opens ports there. On a
shared network, switch the Wi-Fi to Private instead (Settings > Network &
internet).

Linux/macOS: `sh start.sh`

The first start creates `.env` with random database passwords. Check that
`IMAGE_TAG` in `.env` is the version that was pushed. The start script then
pulls the images and runs the database migrations, which takes a few minutes.
Other actions: `start.ps1 -Action ps|logs|down|pull|load`
(`./start.sh ps|logs|down|pull|load`).

## 3. Open the app

| Where | URL |
|---|---|
| Testing laptop | `http://localhost:9002/?baseApiUrl=https://localhost:8443/fineract-provider/api/v1#` |
| Testing laptop (simpler) | `http://localhost:9002/` |
| Other laptops on the LAN | `http://<testing-laptop-IP>:9002/` |

Default login: `mifos` / `password`. Change it after the first login.

**Certificate:** the API serves HTTPS on 8443 with Fineract's bundled
self-signed certificate. When the page calls `https://…:8443` directly (the
`baseApiUrl=` form), the browser blocks those calls silently until you open
`https://localhost:8443/fineract-provider/actuator/health` once and accept the
warning. The plain `http://<host>:9002/` form needs no prompt, because the
frontend proxies `/fineract-provider` to the API itself.

On another laptop, **don't** use `baseApiUrl=https://localhost:8443/…`, because
`localhost` there means that laptop. Use `http://<IP>:9002/`, or if you need the
explicit form, `http://<IP>:9002/?baseApiUrl=https://<IP>:8443/fineract-provider/api/v1#`
after accepting the certificate at `https://<IP>:8443/fineract-provider/actuator/health`.

## 4. Connect with DBeaver

New Connection > **MariaDB**. The passwords are in `.env` on the testing laptop.

| Field | Value |
|---|---|
| Host | `localhost` on the testing laptop, `<testing-laptop-IP>` from another PC |
| Port | `DB_PORT` from `.env` (default `3306`) |
| Database | `fineract_default` (app data); `fineract_tenants` is the tenant registry |
| Username / Password | `fineract` / `DB_PASSWORD` (or `root` / `DB_ROOT_PASSWORD` for admin) |

If DBeaver reports an SSL or public-key error, set `allowPublicKeyRetrieval=true`
and `useSSL=false` under Driver properties.

By default the database listens on `127.0.0.1` only, so DBeaver works only on
the testing laptop itself. To connect from another PC:

1. Set `DB_BIND_ADDRESS=0.0.0.0` in `.env` and run `start.ps1` again (this
   recreates only the database container; data is kept).
2. As Administrator: `powershell -ExecutionPolicy Bypass -File open-firewall.ps1 -IncludeDb`

Both `fineract` and `root` can log in from any host. Open the database to the
LAN only on a trusted network, prefer the `fineract` user, and switch back to
`127.0.0.1` when you're done.

## 5. Copy data from another laptop (optional)

A new laptop starts with an empty database. To give it the same data as an
existing one:

1. On the source laptop (stack running): `powershell -ExecutionPolicy Bypass -File export-data.ps1`.
   This writes `data\fineract_default.sql`.
2. Copy that `data` folder next to `start.ps1` on the target laptop.
3. On the target laptop, run `start.ps1` once, then
   `powershell -ExecutionPolicy Bypass -File import-data.ps1` and type `YES`.

The import **replaces** everything in `fineract_default` on the target. App
logins become the source laptop's users and passwords. Only the app data is
copied: each laptop keeps its own `.env` and database passwords. A dump from an
older image version works, because the API applies newer migrations on start.
The dump contains real customer data, so don't commit or share it.

## Notes

- **Data** lives in the Docker volumes `score-test_database` and
  `score-test_documents`. `start.ps1 -Action down` keeps it.
  `docker compose down -v` **deletes** it.
- **Passwords:** set them in `.env` before the first start if you want specific
  ones. Changing `DB_PASSWORD` after the first start breaks the API.
- **Ports:** change `WEB_PORT` / `API_PORT` in `.env` if 9002 or 8443 are taken,
  and set `BIND_ADDRESS=127.0.0.1` to keep the stack off the LAN.
- **Security:** traffic to port 9002 is plain HTTP and logins use Basic auth, so
  use this only on a trusted network.
- **Updating:** on the build machine, run `build-images.ps1 -Tag <new version> -Push`.
  On the laptop, set `IMAGE_TAG=<new version>` in `.env` and run `start.ps1`.
  The start script pulls the new images and recreates the containers. Data
  volumes are kept, and new migrations run on start. If you re-pushed the same
  tag, run `start.ps1 -Action pull` first.
