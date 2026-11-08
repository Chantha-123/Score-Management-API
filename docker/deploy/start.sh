#!/bin/sh
# Starts the test stack on a Linux/macOS laptop.  Usage: ./start.sh [up|down|ps|logs|pull|load]
set -eu
cd "$(dirname "$0")"
action="${1:-up}"

if [ ! -f .env ]; then
  gen() { tr -dc 'a-z0-9' </dev/urandom | head -c 24; }
  sed -e "s/^DB_ROOT_PASSWORD=\$/DB_ROOT_PASSWORD=$(gen)/" \
      -e "s/^DB_PASSWORD=\$/DB_PASSWORD=$(gen)/" .env.example > .env
  echo "Created .env with generated database passwords (keep this file)."
fi
envval() { v=$(grep "^$1=" .env | head -n1 | cut -d= -f2-); echo "${v:-$2}"; }

repo=$(envval IMAGE_REPO pichoudam/mini_pawnshop_v1); tag=$(envval IMAGE_TAG "")
[ -n "$tag" ] || { echo "Set IMAGE_TAG in .env to the pushed version (e.g. IMAGE_TAG=1.0.0)." >&2; exit 1; }
missing_images() {
  for svc in db api frontend; do
    docker image inspect "$repo:$svc-$tag" >/dev/null 2>&1 || printf ' %s' "$repo:$svc-$tag"
  done
}

# Offline laptops: images/score-images.tar (build-images.ps1 -Export) instead of Docker Hub.
if [ "$action" = load ] || { [ "$action" = up ] && [ -f images/score-images.tar ] && [ -n "$(missing_images)" ]; }; then
  echo "Loading images from images/score-images.tar ..."
  docker load -i images/score-images.tar
fi

if [ "$action" = pull ] || { [ "$action" = up ] && [ -n "$(missing_images)" ]; }; then
  echo "Pulling $repo:*-$tag from Docker Hub ..."
  docker compose pull
fi

case "$action" in
  load|pull)
    missing=$(missing_images)
    [ -z "$missing" ] || { echo "Image(s) not found:$missing" >&2; exit 1; }
    echo "All images present."; exit 0 ;;
  down) exec docker compose down ;;
  ps)   exec docker compose ps ;;
  logs) exec docker compose logs -f --tail 100 ;;
esac

docker compose up -d --wait --wait-timeout 900
web=$(envval WEB_PORT 9002); api=$(envval API_PORT 8443)
echo
echo "Ready. Login: mifos / password (change it after first login)."
echo "  This laptop : http://localhost:$web/?baseApiUrl=https://localhost:$api/fineract-provider/api/v1#"
echo "                (first open https://localhost:$api/fineract-provider/actuator/health and accept the certificate)"
ips=$( { ip -4 -o addr show scope global 2>/dev/null | awk '{sub(/\/.*/, "", $4); print $4}'; } || true)
[ -n "$ips" ] || ips=$(ifconfig 2>/dev/null | awk '/inet / && $2 != "127.0.0.1" {sub(/addr:/, "", $2); print $2}')
for ip in $ips; do
  # Skip Docker's own bridge networks.
  case "$ip" in 172.1[7-9].*|172.2[0-9].*|172.3[01].*) ;; *) echo "  LAN         : http://$ip:$web/" ;; esac
done

dbbind=$(envval DB_BIND_ADDRESS 127.0.0.1); dbport=$(envval DB_PORT 3306)
echo
echo "DBeaver (MariaDB): database fineract_default, user fineract, password = DB_PASSWORD in .env"
echo "  This laptop : localhost:$dbport"
if [ "$dbbind" = 127.0.0.1 ]; then
  echo "  Other PCs   : disabled (set DB_BIND_ADDRESS=0.0.0.0 in .env and rerun)"
else
  for ip in $ips; do
    case "$ip" in 172.1[7-9].*|172.2[0-9].*|172.3[01].*) ;; *) echo "  LAN         : $ip:$dbport" ;; esac
  done
fi
