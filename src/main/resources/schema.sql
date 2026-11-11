-- FixDesk schema (MySQL 8.0.16+, InnoDB, utf8mb4).
-- Runs on every start while spring.sql.init.mode=always, so every statement is
-- CREATE TABLE IF NOT EXISTS. To change an existing table in development,
-- drop the database and let it be rebuilt.
-- Enum-like columns are VARCHAR with CHECK constraints so values stay readable in DAO code.
-- History tables (ticket_status_history, audit_log, ai_decisions, ai_actions, escalations)
-- are append-only.

CREATE TABLE IF NOT EXISTS users (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  name            VARCHAR(100) NOT NULL,
  email           VARCHAR(150) NOT NULL UNIQUE,
  password_hash   VARCHAR(100) NOT NULL,
  role            VARCHAR(20)  NOT NULL DEFAULT 'USER'
                  CHECK (role IN ('USER','TECHNICIAN','ADMIN')),
  active          BOOLEAN      NOT NULL DEFAULT TRUE,
  duty_status     VARCHAR(20)  NOT NULL DEFAULT 'OFF_DUTY'
                  CHECK (duty_status IN ('ON_DUTY','BUSY','OFF_DUTY','ON_LEAVE')),
  duty_updated_at DATETIME     NULL,
  voice_pin_hash  VARCHAR(100) NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS categories (
  id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  name   VARCHAR(60) NOT NULL UNIQUE,
  weight INT NOT NULL DEFAULT 30
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS locations (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(100) NOT NULL,
  building      VARCHAR(100) NOT NULL,
  is_critical   BOOLEAN      NOT NULL DEFAULT FALSE,
  critical_note VARCHAR(255) NULL,
  hidden        BOOLEAN      NOT NULL DEFAULT FALSE,
  UNIQUE (building, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS incidents (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  title       VARCHAR(150) NOT NULL,
  location_id BIGINT NULL,
  status      VARCHAR(20)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RESOLVED')),
  created_by  BIGINT NOT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at DATETIME NULL,
  FOREIGN KEY (location_id) REFERENCES locations(id),
  FOREIGN KEY (created_by)  REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tickets (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  title                VARCHAR(150) NOT NULL,
  description          TEXT         NOT NULL,
  category_id          BIGINT NOT NULL,
  location_id          BIGINT NOT NULL,
  incident_id          BIGINT NULL,
  reporter_id          BIGINT NOT NULL,
  assignee_id          BIGINT NULL,
  status               VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                       CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED','CANCELLED')),
  priority             VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                       CHECK (priority IN ('PENDING','LOW','MEDIUM','HIGH','CRITICAL')),
  priority_score       INT NULL,
  priority_source      VARCHAR(10) NULL CHECK (priority_source IN ('AI','RULES','ADMIN')),
  form_in_danger       BOOLEAN NOT NULL,
  form_has_alternative BOOLEAN NOT NULL,
  urgency_level        VARCHAR(20) NOT NULL DEFAULT 'NONE'
                       CHECK (urgency_level IN ('NONE','ACCESS_CRITICAL','LIFE_SAFETY')),
  danger_now           BOOLEAN NULL,
  sole_access_lost     BOOLEAN NULL,
  resolution_note      TEXT NULL,
  rating               TINYINT NULL CHECK (rating BETWEEN 1 AND 5),
  rating_comment       VARCHAR(500) NULL,
  attachment_path      VARCHAR(255) NULL,
  created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  closed_at            DATETIME NULL,
  FOREIGN KEY (category_id) REFERENCES categories(id),
  FOREIGN KEY (location_id) REFERENCES locations(id),
  FOREIGN KEY (incident_id) REFERENCES incidents(id),
  FOREIGN KEY (reporter_id) REFERENCES users(id),
  FOREIGN KEY (assignee_id) REFERENCES users(id),
  INDEX idx_tickets_queue (status, priority, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_decisions (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  ticket_id        BIGINT NOT NULL,
  priority         VARCHAR(20) NOT NULL,
  score            INT NOT NULL,
  reason           VARCHAR(200) NOT NULL,
  danger_now       BOOLEAN NULL,
  sole_access_lost BOOLEAN NULL,
  source           VARCHAR(10) NOT NULL CHECK (source IN ('AI','RULES','ADMIN')),
  model            VARCHAR(60) NULL,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (ticket_id) REFERENCES tickets(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ticket_status_history (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  ticket_id   BIGINT NOT NULL,
  from_status VARCHAR(20) NULL,
  to_status   VARCHAR(20) NOT NULL,
  changed_by  BIGINT NOT NULL,
  note        VARCHAR(500) NULL,
  changed_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (ticket_id)  REFERENCES tickets(id),
  FOREIGN KEY (changed_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS comments (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  ticket_id  BIGINT NOT NULL,
  user_id    BIGINT NOT NULL,
  body       TEXT   NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (ticket_id) REFERENCES tickets(id),
  FOREIGN KEY (user_id)   REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notifications (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT NOT NULL,
  ticket_id  BIGINT NULL,
  message    VARCHAR(255) NOT NULL,
  is_read    BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id)   REFERENCES users(id),
  FOREIGN KEY (ticket_id) REFERENCES tickets(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_articles (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  title       VARCHAR(150) NOT NULL,
  body        TEXT NOT NULL,
  category_id BIGINT NOT NULL,
  author_id   BIGINT NOT NULL,
  published   BOOLEAN NOT NULL DEFAULT FALSE,
  view_count  INT NOT NULL DEFAULT 0,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NULL,
  FOREIGN KEY (category_id) REFERENCES categories(id),
  FOREIGN KEY (author_id)   REFERENCES users(id),
  FULLTEXT INDEX ft_kb (title, body)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  actor_id    BIGINT NULL,
  action      VARCHAR(60) NOT NULL,
  entity_type VARCHAR(40) NULL,
  entity_id   BIGINT NULL,
  detail      VARCHAR(1000) NULL,
  ip_address  VARCHAR(45) NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (actor_id) REFERENCES users(id),
  INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS login_attempts (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  email        VARCHAR(150) NOT NULL,
  ip_address   VARCHAR(45) NOT NULL,
  success      BOOLEAN NOT NULL,
  attempted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_login_lookup (email, ip_address, attempted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_actions (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  ticket_id   BIGINT NOT NULL,
  action_type VARCHAR(20) NOT NULL
              CHECK (action_type IN ('ACKNOWLEDGE','ACCEPT','ASSIGN','LINK_INCIDENT','ADD_NOTE')),
  payload     JSON NULL,
  status      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
              CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED','EXECUTED')),
  channel     VARCHAR(10) NULL CHECK (channel IN ('PHONE','WEB')),
  proposed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at  DATETIME NOT NULL,
  approved_by BIGINT NULL,
  decided_at  DATETIME NULL,
  executed_at DATETIME NULL,
  FOREIGN KEY (ticket_id)   REFERENCES tickets(id),
  FOREIGN KEY (approved_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS escalations (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  ticket_id       BIGINT NOT NULL,
  level           VARCHAR(20) NOT NULL CHECK (level IN ('LIFE_SAFETY','ACCESS_CRITICAL')),
  step            INT NOT NULL,
  contacted_id    BIGINT NULL,
  channel         VARCHAR(10) NOT NULL CHECK (channel IN ('EMAIL','IN_APP','VOICE')),
  sent_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  acknowledged_at DATETIME NULL,
  acknowledged_by BIGINT NULL,
  FOREIGN KEY (ticket_id)       REFERENCES tickets(id),
  FOREIGN KEY (contacted_id)    REFERENCES users(id),
  FOREIGN KEY (acknowledged_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- push_subscriptions (bonus) is added when the voice call is built.
