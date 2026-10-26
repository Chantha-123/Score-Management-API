#!/bin/sh
# Starts the test stack on a Linux/macOS laptop.  Usage: ./start.sh [up|down|ps|logs|load]
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

if [ -f images/score-images.tar ] && { [ "$action" = load ] || { [ "$action" = up ] && ! docker image inspect "$(envval API_IMAGE mini-pawnshop-api):$(envval IMAGE_TAG local)" >/dev/null 2>&1; }; }; then
  echo "Loading images from images/score-images.tar ..."
  docker load -i images/score-images.tar
fi

case "$action" in
  load) exit 0 ;;
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
