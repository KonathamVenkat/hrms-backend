-- UNDO of db/seed_nilepet_south_sudan_branches.sql: removes the sample company branches and office locations.
-- Run as the HRMS user. Each DELETE only removes a row nothing refers to: if employees were already assigned to a
-- branch, or placed in a location, Oracle refuses that row (ORA-02292) and you must move them first.
-- Order: branches before locations.

DELETE FROM HRMS.COMPANY_BRANCHES
 WHERE COMPANY_BRANCHES_CODE IN ('JUBA_HO', 'JUBA_RETAIL', 'JUBA_DEPOT', 'WAU', 'MALAKAL', 'BOR', 'YEI', 'RUMBEK', 'BENTIU', 'NIMULE');

DELETE FROM HRMS.OFFICE_LOCATIONS
 WHERE LOCATION_CODE IN ('JUBA_HQ', 'JUBA_DEPOT', 'WAU', 'MALAKAL', 'BOR', 'YEI', 'RUMBEK', 'BENTIU', 'NIMULE');

COMMIT;
