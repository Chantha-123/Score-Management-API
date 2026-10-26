# MariaDB with the Fineract tenant bootstrap baked in, so a deploy machine
# needs only this image (no source checkout). Build from the repo root:
#   docker build -f docker/db/Dockerfile -t mini-pawnshop-db:local .
FROM mariadb:10.11
COPY fineract-db/server_collation.cnf /etc/mysql/conf.d/server_collation.cnf
COPY docker/initdb/ /docker-entrypoint-initdb.d/
ENV MARIADB_DATABASE=fineract_tenants \
    MARIADB_USER=fineract
