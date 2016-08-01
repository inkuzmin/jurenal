-- :name get-user :? :1
-- :doc 123
SELECT * FROM users
WHERE id = :id

-- :name get-users-by-username :? :*
-- :doc 123
SELECT * FROM users
WHERE username = :username

-- :name get-user-by-username :? :1
-- :doc 123
SELECT * FROM users
WHERE username = :username

-- :name create-user! :! :n
-- :doc 123
INSERT INTO users
(id, username, "first_name", "last_name", created)
VALUES (:id, :username, :first_name, :last_name, :created)

-- :name update-user! :! :n
-- :doc update an existing user record
UPDATE users
SET "first_name" = :first_name, "last_name" = :last_name, "username" = :username
WHERE id = :id

-- :name update-userpic! :! :n
-- :doc update an existing user record
UPDATE users
SET "userpic" = :userpic
WHERE id = :id

-- :name create-post! :<!
-- :doc 123
INSERT INTO posts
("text", "date", "user_id", attach_id, anon)
VALUES (:text, :date, :user_id, :attach_id, :anon)
returning id

-- :name create-comment! :<!
-- :doc 123
INSERT INTO posts
("text", "date", "user_id", parent_id, anon)
VALUES (:text, :date, :user_id, :parent_id, :anon)
returning id

-- :name get-posts :? :*
-- :doc 123
SELECT * FROM posts
WHERE parent_id = 0
ORDER BY "date" DESC
LIMIT :limit OFFSET :offset

-- :name get-post :? :1
-- :doc 123
SELECT * FROM posts
WHERE id = :id

-- :name get-posts-by-user-id :? :*
-- :doc 123
SELECT * FROM posts
WHERE user_id = :user_id AND parent_id = 0 AND anon = false
ORDER BY "date" DESC
LIMIT :limit OFFSET :offset

-- :name get-comments-by-user-id :? :*
-- :doc 123
SELECT * FROM posts
WHERE user_id = :user_id AND parent_id > 0 AND anon = false
ORDER BY "date" DESC
LIMIT :limit OFFSET :offset

-- :name get-recommendations-by-user-id :? :*
-- :doc 123
SELECT * FROM recommendations
WHERE user_id = :user_id
ORDER BY id DESC
LIMIT :limit OFFSET :offset

-- :name get-reactions-by-user-id :? :*
-- :doc 123
SELECT * FROM reactions
WHERE user_id = :user_id
ORDER BY id DESC
LIMIT :limit OFFSET :offset


-- :name get-comments :? :*
-- :doc 123
WITH RECURSIVE comments(id) AS (
    SELECT *
    FROM posts
    WHERE parent_id = :id
  UNION ALL
    SELECT p.*
    FROM comments c, posts p
    WHERE p.parent_id = c.id
  )
SELECT *
FROM comments
ORDER BY "date" DESC;

-- :name count-comments :? :1
-- :doc 123
WITH RECURSIVE comments(id) AS (
    SELECT id FROM posts WHERE parent_id = :id
  UNION ALL
    SELECT p.id
    FROM comments c, posts p
    WHERE p.parent_id = c.id
  )
SELECT COUNT(id)
FROM comments;

-- :name create-attach! :<!
-- :doc 123
INSERT INTO attaches
("type", "file_id")
VALUES (:type, :file_id)
returning id

-- :name get-attach :? :1
-- :doc 123
SELECT * FROM attaches
WHERE id = :id

-- :name create-reaction! :<!
-- :doc 123
INSERT INTO reactions
("user_id", "post_id", "emoji")
VALUES (:user_id, :post_id, :emoji)
returning id

-- :name get-reaction :? :1
-- :doc 123
SELECT * FROM reactions
WHERE post_id = :post_id AND user_id = :user_id

-- :name get-reactions :? :*
-- :doc 123
SELECT * FROM reactions
WHERE post_id = :post_id

-- :name update-reaction! :! :n
-- :doc update an existing user record
UPDATE reactions
SET "emoji" = :emoji
WHERE post_id = :post_id AND user_id = :user_id

-- :name test-q :? *
SELECT *
FROM posts
JOIN users ON posts.user_id = users.id;

-- :name create-subscription! :<!
-- :doc 123
INSERT INTO subscriptions
("subject_id", "subject_type", "object_id", "object_type")
VALUES (:subject_id, 'user', :object_id, 'user')
ON CONFLICT DO NOTHING
returning id

-- :name get-subscribers :? :*
-- :doc 123
SELECT * FROM subscriptions
WHERE object_id = :object_id

-- :name get-subscriptions :? :*
-- :doc 123
SELECT * FROM subscriptions
WHERE subject_id = :subject_id

-- :name remove-subscription! :! :n
-- :doc 123
DELETE FROM subscriptions
WHERE subject_id = :subject_id AND object_id = :object_id;

-- :name count-subscriptions :? :1
-- :doc 123
SELECT COUNT(id)
FROM subscriptions
WHERE subject_id = :id;

-- :name count-subscribers :? :1
-- :doc 123
SELECT COUNT(id)
FROM subscriptions
WHERE object_id = :id;

-- :name create-recommendation! :<!
-- :doc 123
INSERT INTO recommendations
("user_id", "post_id")
VALUES (:user_id, :post_id)
returning id

-- :name get-recommendation :? :1
-- :doc 123
SELECT * FROM recommendations
WHERE post_id = :post_id AND user_id = :user_id

-- :name get-recommendations :? :*
-- :doc 123
SELECT * FROM recommendations
WHERE post_id = :post_id