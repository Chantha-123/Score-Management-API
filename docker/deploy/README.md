# Testing-laptop deployment

This folder is a self-contained bundle: three Docker images (database, API,
frontend) plus a compose file. The testing laptop needs **only Docker** — no
source code, Java, Node or internet access.

## 1. On the build machine (this repo)

```powershell
powershell -ExecutionPolicy Bypass -File docker\build-images.ps1
```

This builds `mini-pawnshop-db`, `mini-pawnshop-api` and
`mini-pawnshop-frontend` (tag `local`) and saves them to
`docker\deploy\images\score-images.tar`. The frontend repo is expected next to
this one (`..\Score-Management-System-Frontend`); pass `-FrontendPath` otherwise.
First API build takes ~7 minutes and needs internet.

Copy the whole `docker\deploy` folder (USB drive, network share, …) to the
testing laptop. Don't copy `.env` from another machine unless you mean to reuse
its passwords.

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

The first start creates `.env` with random database passwords and runs the
database migrations (a few minutes). Other actions: `start.ps1 -Action ps|logs|down|load`
(`./start.sh ps|logs|down|load`).

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
- **Updating:** rebuild on the build machine, copy the new `images\score-images.tar`,
  then on the laptop run `start.ps1 -Action load` and `start.ps1`. Data volumes
  are kept, and new migrations run on start.
