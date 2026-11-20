-- V12 (step 8): where Spring Session keeps login sessions.
--
-- Before, a session lived in the server's memory, so every restart logged everyone out.
-- On Render's free plan the server sleeps after 15 idle minutes and restarts on the next
-- request, so sessions now live in the database instead.
--
-- This is Spring Session's own schema for PostgreSQL (schema-postgresql.sql inside the
-- spring-session-jdbc jar), copied here so Flyway creates it like every other table.
-- (application.properties sets spring.session.jdbc.initialize-schema=never for that reason.)

-- One row per session: who is logged in and when it expires (times in milliseconds).
CREATE TABLE SPRING_SESSION (
	PRIMARY_ID CHAR(36) NOT NULL,
	SESSION_ID CHAR(36) NOT NULL,
	CREATION_TIME BIGINT NOT NULL,
	LAST_ACCESS_TIME BIGINT NOT NULL,
	MAX_INACTIVE_INTERVAL INT NOT NULL,
	EXPIRY_TIME BIGINT NOT NULL,
	PRINCIPAL_NAME VARCHAR(100),
	CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

-- What's stored IN a session (e.g. the logged-in user), as serialized Java objects.
-- ON DELETE CASCADE: deleting a session (logout, expiry) deletes its attributes too.
CREATE TABLE SPRING_SESSION_ATTRIBUTES (
	SESSION_PRIMARY_ID CHAR(36) NOT NULL,
	ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
	ATTRIBUTE_BYTES BYTEA NOT NULL,
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);