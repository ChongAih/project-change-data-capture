CREATE DATABASE auth;

USE auth;

CREATE TABLE users (
  username VARCHAR(50) NOT NULL,
  password VARCHAR(500) NOT NULL,
  country VARCHAR(50) NOT NULL,
  enabled BOOLEAN NOT NULL,
  ts BIGINT NULL DEFAULT (UNIX_TIMESTAMP(CURRENT_TIMESTAMP())),
  PRIMARY KEY (username)
);

CREATE TABLE authorities (
  username VARCHAR(50) NOT NULL,
  authority VARCHAR(50) NOT NULL,
  FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE,
  ts BIGINT NULL DEFAULT (UNIX_TIMESTAMP(CURRENT_TIMESTAMP())),
  UNIQUE (username, authority)
);

-- insert the encoded password (encoder must be the same used in coded  --password1 --password2
INSERT INTO users (username, password, country, enabled)
VALUES
('user1', '$2a$10$W9jd1d6sVe6dNKxeYzTlZuuKovX5rHj36zHnrtkVOcyFI/jhc7ASW', 'SG', true),
('user2', '$2a$10$OOHTa4Hm1fAnbprRTfi.teewvskNUO2jFpGEe7w.Xevhmi33OBG2K', 'ID', true);

INSERT INTO authorities (username, authority)
VALUES
('user1', 'ROLE_USER'),
('user2', 'ROLE_USER');