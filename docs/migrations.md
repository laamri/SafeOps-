# Database migrations

Each service owns its schema through Flyway and runs Hibernate with
`spring.jpa.hibernate.ddl-auto=validate`; Hibernate never alters a table.

## Existing databases

The services were built with `ddl-auto=update` before Flyway existed, so an already-populated
database has no `flyway_schema_history` table. On first start Flyway therefore *baselines* it:

```
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
```

The baseline is recorded as version 1 and `V1__baseline.sql` is skipped, because that script only
describes what update mode had already created. A fresh database has no rows, so Flyway applies
`V1__baseline.sql` normally and both paths end at the same schema.

## Production

`baseline-on-migrate` is a transition setting, not a permanent one. It silently accepts a
database whose schema it did not create and whose history it cannot verify. Before production:

- run the baseline adoption once on a copy of the real database, compare the resulting
  `flyway_schema_history` with the migrations in the repository, and deploy that copy;
- then set `spring.flyway.baseline-on-migrate=false` (keep `baseline-version=1`) so a missing
  or mismatched history aborts startup instead of being papered over;
- from that point on, every schema change is a new `V<n>__*.sql` file, reviewed like code.

CI always starts from empty volumes, so it exercises the fresh path and never relies on the
baseline. `V1__baseline.sql` must stay a faithful snapshot of the schema that version shipped
with; never edit an applied migration.
