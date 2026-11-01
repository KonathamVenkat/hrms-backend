-- Flyway's history has THREE rows for version 16 (ranks 20, 21, 22): the record script was run more than once, and the first row
-- still carries the old checksum of the file. Keep one row (rank 20) with the file's current checksum and remove the extras.
DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '16' AND "installed_rank" IN (21, 22);
UPDATE HRMS."flyway_schema_history" SET "checksum" = -1310738121 WHERE "version" = '16' AND "installed_rank" = 20;
COMMIT;
-- Expect: 2 rows deleted, 1 row updated. Check: SELECT "installed_rank","version","checksum" FROM HRMS."flyway_schema_history" WHERE "version" = '16';
