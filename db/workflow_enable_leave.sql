-- Switches the approval workflow ON for leave requests (WORKFLOW_SETTINGS.WORKFLOW_ENABLED_LEAVE = 'true').
-- Needs a published ACTIVE leave flow first (HR_ADMIN: publish-default). New leave requests then go branch manager ->
-- department secretary -> department head -> General Manager -> HR. Requests already pending stay on the old path.
-- Undo: set it back to 'false'.
UPDATE HRMS.WORKFLOW_SETTINGS SET SETTING_VALUE = 'true', UPDATED_AT = SYSTIMESTAMP, VERSION = VERSION + 1
 WHERE SETTING_KEY = 'WORKFLOW_ENABLED_LEAVE'
   AND EXISTS (SELECT 1 FROM HRMS.WORKFLOW_DEFINITIONS WHERE REQUEST_TYPE = 'LEAVE' AND STATUS = 'ACTIVE');
-- Expect: 1 row.
COMMIT;
