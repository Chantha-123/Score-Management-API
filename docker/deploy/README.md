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
`compose.yml`, `.env.example`, `start.ps1`, `start.sh` and `open-firewall.ps1`
are needed. If the Docker Hub repository is private, also run `docker login` on
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
