CREATE TABLE app_settings (
    id BIGSERIAL PRIMARY KEY,
    goal_amount NUMERIC(15, 0) NOT NULL DEFAULT 50000000,
    goal_deadline DATE,
    monthly_budget NUMERIC(15, 0) NOT NULL DEFAULT 700000,
    card_payment_day INT NOT NULL DEFAULT 15
);

CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    category_type VARCHAR(20) NOT NULL,
    color VARCHAR(20) NOT NULL DEFAULT '#7DBA8B',
    emoji VARCHAR(20) NOT NULL DEFAULT '🌱',
    detail_name VARCHAR(80),
    UNIQUE (name, category_type, detail_name)
);

CREATE TABLE debts (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    balance NUMERIC(15, 0) NOT NULL DEFAULT 0,
    interest_rate NUMERIC(5, 2) NOT NULL DEFAULT 0,
    due_date DATE,
    memo VARCHAR(500)
);

CREATE TABLE savings_products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    product_type VARCHAR(30) NOT NULL,
    interest_rate NUMERIC(5, 2) NOT NULL DEFAULT 0,
    maturity_date DATE,
    current_amount NUMERIC(15, 0) NOT NULL DEFAULT 0,
    target_amount NUMERIC(15, 0),
    memo VARCHAR(500)
);

CREATE TABLE ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    entry_date DATE NOT NULL,
    entry_type VARCHAR(20) NOT NULL,
    category_id BIGINT REFERENCES categories(id),
    category_name VARCHAR(80) NOT NULL,
    detail_name VARCHAR(80),
    amount NUMERIC(15, 0) NOT NULL,
    memo VARCHAR(500),
    savings_product_id BIGINT REFERENCES savings_products(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO app_settings (goal_amount, goal_deadline, monthly_budget, card_payment_day)
VALUES (50000000, '2029-12-31', 700000, 15);

INSERT INTO categories (name, category_type, color, emoji, detail_name) VALUES
('월급', 'INCOME', '#79B98A', '💼', NULL),
('카드 캐시백', 'INCOME', '#79B98A', '💳', NULL),
('용돈', 'INCOME', '#79B98A', '🎁', NULL),
('기타 수입', 'INCOME', '#79B98A', '✨', NULL),
('고정액', 'EXPENSE', '#83B8A8', '📌', '교통비'),
('고정액', 'EXPENSE', '#83B8A8', '📌', '클로드'),
('고정액', 'EXPENSE', '#83B8A8', '📌', '아이클라우드'),
('고정액', 'EXPENSE', '#83B8A8', '📌', '이모티콘플러스'),
('저축', 'SAVING', '#E3A96F', '🌱', '곗돈'),
('저축', 'SAVING', '#E3A96F', '🌱', '적금'),
('저축', 'SAVING', '#E3A96F', '🌱', '예금'),
('저축', 'SAVING', '#E3A96F', '🌱', '파킹통장'),
('신용카드 대금', 'EXPENSE', '#D99090', '💳', NULL),
('변동금액', 'EXPENSE', '#E3A96F', '🍽️', '점심'),
('변동금액', 'EXPENSE', '#E3A96F', '☕', '커피'),
('변동금액', 'EXPENSE', '#E3A96F', '💛', '데이트'),
('변동금액', 'EXPENSE', '#E3A96F', '🛍️', '쇼핑'),
('변동금액', 'EXPENSE', '#E3A96F', '🍔', '배달'),
('변동금액', 'EXPENSE', '#E3A96F', '🏪', '편의점'),
('기타 지출', 'EXPENSE', '#A59AC7', '🧾', NULL);
