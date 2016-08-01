CREATE TABLE "public"."users" (
    "id" integer NOT NULL,
    "username" text,
    "first_name" text NOT NULL,
    "last_name" text,
    "created" timestamp without time zone,
    UNIQUE ("id")
);
--;;
CREATE INDEX usernames_key ON users USING btree (username);
--;;
ALTER TABLE "public"."users" ADD COLUMN "userpic" text;
--;;
CREATE OR REPLACE VIEW "public"."posts_full" AS SELECT posts.id AS pid,
    posts.text,
    posts.date,
    posts.parent_id,
    users.id AS uid,
    users.username,
    users.first_name,
    users.last_name,
    users.userpic
   FROM posts
     JOIN users ON posts.user_id = users.id;

--;;
CREATE TABLE "public"."attaches" (
    "id" serial,
    "type" text NOT NULL,
    "file_id" text NOT NULL,
    PRIMARY KEY ("id")
);
--;;
ALTER TABLE "public"."posts"
  ADD COLUMN "attach_id" integer,
  ADD FOREIGN KEY ("attach_id") REFERENCES "public"."attaches"("id");

--;;
DROP VIEW "public"."posts_full";
--;;
CREATE OR REPLACE VIEW "public"."posts_full" AS SELECT posts.id AS pid,
    posts.text,
    posts.date,
    posts.parent_id,
    posts.attach_id,
    users.id AS uid,
    users.username,
    users.first_name,
    users.last_name,
    users.userpic
   FROM posts
     JOIN users ON posts.user_id = users.id;
--;;
CREATE TABLE "public"."reactions" (
    "id" serial,
    "post_id" integer,
    "user_id" integer,
    "emoji" text NOT NULL,
    PRIMARY KEY ("id"),
    FOREIGN KEY ("post_id") REFERENCES "public"."posts"("id"),
    FOREIGN KEY ("user_id") REFERENCES "public"."users"("id")
);
--;;

CREATE UNIQUE INDEX reaction_id ON "public"."reactions" (post_id, user_id);
--;;
CREATE TYPE entity_type AS ENUM ('user', 'post');
--;;
CREATE TABLE "public"."subscriptions" (
    "id" serial,
    "subject_id" integer,
    "subject_type" entity_type,
    "object_id" integer,
    "object_type" entity_type,
    PRIMARY KEY ("id")
);
--;;
CREATE INDEX ON "public"."subscriptions" (subject_id);
--;;
CREATE INDEX ON "public"."subscriptions" (object_id);
--;;
CREATE UNIQUE INDEX ON "public"."subscriptions" (subject_id, object_id);
--;;
ALTER TABLE "public"."posts" ADD COLUMN "anon" boolean DEFAULT false;
--;;
CREATE TABLE "public"."recommendations" (
    "id" serial,
    "post_id" integer,
    "user_id" integer,
    PRIMARY KEY ("id")
);
--;;
ALTER TABLE "public"."recommendations"
  ADD FOREIGN KEY ("post_id") REFERENCES "public"."posts"("id"),
  ADD FOREIGN KEY ("user_id") REFERENCES "public"."users"("id");
