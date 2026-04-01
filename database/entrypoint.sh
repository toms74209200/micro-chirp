#!/bin/bash
apt-get update -qq && apt-get install -y -qq postgresql-18-cron

echo "shared_preload_libraries = 'pg_cron'" >> /usr/share/postgresql/postgresql.conf.sample
echo "cron.database_name = 'chirpdb'" >> /usr/share/postgresql/postgresql.conf.sample

exec /usr/local/bin/docker-entrypoint.sh "$@"
