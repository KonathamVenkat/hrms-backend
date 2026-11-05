-- V23: when a refresh token was rotated (used up by a refresh). The short grace for a second tab that presents the token a
-- moment later now looks at THIS column of the presented token, not at whether the user got any new token recently, so a
-- stolen token that was revoked by logout, password change or an earlier rotation can no longer ride on someone else's login.
-- Not applied yet. Run by hand on HRMS with the backend stopped (Flyway is validate-only), then record it with
-- db/flyway_record_v23.sql. Rollback: db/rollback_V23.sql.
-- Additive and nullable: existing tokens have no rotation time, so none of them gets a grace.
ALTER TABLE HRMS.AUTH_REFRESH_TOKENS ADD (ROTATED_AT TIMESTAMP (9));
