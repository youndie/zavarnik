#!/usr/bin/env bash
# Truncate and re-seed the stand's table.
#
# `POST /db/items` inserts, so the table grows across a run and across runs. A stand that
# accumulates does not add noise, it adds monotonic drift: the same variant gets slower every
# repetition, the drift lands in the spread, and the spread eats the resolution the comparison
# needed. Measured elsewhere in this portfolio at 13.3 % of spread inside one variant, against 4.5 %
# once the reset was there.
#
# Run it before EVERY run, not between groups.
#
#   SEED=200 ./reset-db.sh
set -euo pipefail
SEED=${SEED:-200}
CONTAINER=${PG_CONTAINER:-bench-pg}
# The table is created here as well as by the service, so that a reset works on a database the
# service has never touched. A reset script that needs the thing it resets to have run first is one
# that fails exactly once - on the first run of a new machine, where it is least expected.
docker exec -i "$CONTAINER" psql -U bench -d bench -q -v ON_ERROR_STOP=1 <<SQL
CREATE TABLE IF NOT EXISTS items (
  id    BIGSERIAL PRIMARY KEY,
  sku   VARCHAR(32)  NOT NULL,
  name  VARCHAR(128) NOT NULL,
  price DOUBLE PRECISION NOT NULL,
  tags  VARCHAR(256) NOT NULL
);
TRUNCATE items RESTART IDENTITY;
INSERT INTO items (sku, name, price, tags)
SELECT 'SKU-' || lpad(i::text, 4, '0'), 'item ' || i, i * 1.5, 'a,b'
FROM generate_series(1, $SEED) AS i;
SQL
echo "items reset to $SEED rows"
