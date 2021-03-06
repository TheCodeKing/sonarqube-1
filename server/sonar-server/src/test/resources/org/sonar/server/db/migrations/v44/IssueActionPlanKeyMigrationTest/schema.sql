-- 4.4

CREATE TABLE "ISSUES" (
  "ID" INTEGER NOT NULL GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1),
  "KEE" VARCHAR(50) UNIQUE NOT NULL,
  "COMPONENT_ID" INTEGER NOT NULL,
  "ROOT_COMPONENT_ID" INTEGER,
  "RULE_ID" INTEGER,
  "SEVERITY" VARCHAR(10),
  "MANUAL_SEVERITY" BOOLEAN NOT NULL,
  "MESSAGE" VARCHAR(4000),
  "LINE" INTEGER,
  "EFFORT_TO_FIX" DOUBLE,
  "STATUS" VARCHAR(20),
  "RESOLUTION" VARCHAR(20),
  "CHECKSUM" VARCHAR(1000),
  "REPORTER" VARCHAR(40),
  "ASSIGNEE" VARCHAR(40),
  "AUTHOR_LOGIN" VARCHAR(100),
  "ACTION_PLAN_KEY" VARCHAR(50) NULL,
  "ISSUE_ATTRIBUTES" VARCHAR(4000),
  "ISSUE_CREATION_DATE" TIMESTAMP,
  "ISSUE_CLOSE_DATE" TIMESTAMP,
  "ISSUE_UPDATE_DATE" TIMESTAMP,
  "CREATED_AT" TIMESTAMP,
  "UPDATED_AT" TIMESTAMP,
  "TECHNICAL_DEBT" INTEGER
);

CREATE TABLE "ACTION_PLANS" (
  "ID" BIGINT NOT NULL GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1),
  "KEE" VARCHAR(100),
  "NAME" VARCHAR(200),
  "DESCRIPTION" VARCHAR(1000),
  "DEADLINE" TIMESTAMP,
  "USER_LOGIN" VARCHAR(40),
  "PROJECT_ID" INTEGER,
  "STATUS" VARCHAR(10),
  "CREATED_AT" TIMESTAMP,
  "UPDATED_AT" TIMESTAMP
);
