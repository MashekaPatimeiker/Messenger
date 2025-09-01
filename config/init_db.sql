-- Создание базы данных
CREATE DATABASE messenger_db;
\c messenger_db;

-- Таблица пользователей
CREATE TABLE users (
                       user_id SERIAL PRIMARY KEY,
                       user_name VARCHAR(50) UNIQUE NOT NULL,
                       user_password VARCHAR(255) NOT NULL,
                       email VARCHAR(100) UNIQUE,
                       avatar_url VARCHAR(255),
                       status VARCHAR(20) DEFAULT 'offline',
                       last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Таблица чатов
CREATE TABLE chats (
                       chat_id SERIAL PRIMARY KEY,
                       chat_name VARCHAR(100) NOT NULL,
                       chat_type VARCHAR(20) DEFAULT 'private', -- 'private', 'group', 'channel'
                       avatar_url VARCHAR(255),
                       created_by INTEGER REFERENCES users(user_id),
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Таблица участников чатов
CREATE TABLE chat_members (
                              member_id SERIAL PRIMARY KEY,
                              chat_id INTEGER REFERENCES chats(chat_id) ON DELETE CASCADE,
                              user_id INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
                              role VARCHAR(20) DEFAULT 'member', -- 'owner', 'admin', 'member'
                              joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              UNIQUE(chat_id, user_id)
);

-- Таблица сообщений
CREATE TABLE messages (
                          message_id SERIAL PRIMARY KEY,
                          chat_id INTEGER REFERENCES chats(chat_id) ON DELETE CASCADE,
                          sender_id INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
                          message_text TEXT NOT NULL,
                          message_type VARCHAR(20) DEFAULT 'text', -- 'text', 'image', 'file', 'system'
                          attachment_url VARCHAR(255),
                          sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                          edited_at TIMESTAMP,
                          is_deleted BOOLEAN DEFAULT FALSE,
                          deleted_at TIMESTAMP
);

-- Таблица прочитанных сообщений
CREATE TABLE read_receipts (
                               receipt_id SERIAL PRIMARY KEY,
                               message_id INTEGER REFERENCES messages(message_id) ON DELETE CASCADE,
                               user_id INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
                               read_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               UNIQUE(message_id, user_id)
);

-- Таблица сессий пользователей
CREATE TABLE user_sessions (
                               session_id SERIAL PRIMARY KEY,
                               user_id INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
                               token VARCHAR(255) UNIQUE NOT NULL,
                               ip_address VARCHAR(45),
                               user_agent TEXT,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               expires_at TIMESTAMP NOT NULL,
                               is_active BOOLEAN DEFAULT TRUE
);

-- Таблица друзей (запросы в друзья)
CREATE TABLE friend_requests (
                                 request_id SERIAL PRIMARY KEY,
                                 from_user_id INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
                                 to_user_id INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
                                 status VARCHAR(20) DEFAULT 'pending', -- 'pending', 'accepted', 'rejected'
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 UNIQUE(from_user_id, to_user_id)
);

-- Таблица блокировок
CREATE TABLE user_blocks (
                             block_id SERIAL PRIMARY KEY,
                             blocker_id INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
                             blocked_id INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
                             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                             UNIQUE(blocker_id, blocked_id)
);

-- Индексы для улучшения производительности
CREATE INDEX idx_users_username ON users(user_name);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_chats_type ON chats(chat_type);
CREATE INDEX idx_chat_members_user ON chat_members(user_id);
CREATE INDEX idx_chat_members_chat ON chat_members(chat_id);
CREATE INDEX idx_messages_chat ON messages(chat_id);
CREATE INDEX idx_messages_sender ON messages(sender_id);
CREATE INDEX idx_messages_timestamp ON messages(sent_at);
CREATE INDEX idx_read_receipts_user ON read_receipts(user_id);
CREATE INDEX idx_user_sessions_token ON user_sessions(token);
CREATE INDEX idx_user_sessions_user ON user_sessions(user_id);
CREATE INDEX idx_friend_requests_from ON friend_requests(from_user_id);
CREATE INDEX idx_friend_requests_to ON friend_requests(to_user_id);
CREATE INDEX idx_user_blocks_blocker ON user_blocks(blocker_id);
CREATE INDEX idx_user_blocks_blocked ON user_blocks(blocked_id);

-- Триггеры для автоматического обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_chats_updated_at BEFORE UPDATE ON chats
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_friend_requests_updated_at BEFORE UPDATE ON friend_requests
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Функция для создания приватного чата между двумя пользователями
CREATE OR REPLACE FUNCTION create_private_chat(user1_id INTEGER, user2_id INTEGER)
    RETURNS INTEGER AS $$
DECLARE
    new_chat_id INTEGER;
    chat_name TEXT;
BEGIN
    -- Получаем имена пользователей для названия чата
    SELECT string_agg(user_name, ' и ') INTO chat_name
    FROM users WHERE user_id IN (user1_id, user2_id);

    -- Создаем чат
    INSERT INTO chats (chat_name, chat_type, created_by)
    VALUES (chat_name, 'private', user1_id)
    RETURNING chat_id INTO new_chat_id;

    -- Добавляем участников
    INSERT INTO chat_members (chat_id, user_id, role) VALUES (new_chat_id, user1_id, 'member');
    INSERT INTO chat_members (chat_id, user_id, role) VALUES (new_chat_id, user2_id, 'member');

    RETURN new_chat_id;
END;
$$ LANGUAGE plpgsql;

-- Вставка тестовых данных
INSERT INTO users (user_name, user_password, email, status) VALUES
                                                                ('December00', 'password123', 'december@example.com', 'online'),
                                                                ('Tanyusha❤', 'password123', 'tanya@example.com', 'online'),
                                                                ('Her', 'password123', 'her@example.com', 'offline'),
                                                                ('You', 'password123', 'you@example.com', 'online');

-- Создаем приватные чаты
SELECT create_private_chat(1, 4); -- December00 и You
SELECT create_private_chat(2, 4); -- Tanyusha❤ и You
SELECT create_private_chat(3, 4); -- Her и You

-- Добавляем тестовые сообщения
INSERT INTO messages (chat_id, sender_id, message_text, sent_at) VALUES
                                                                     (1, 1, 'Are u ready?', NOW() - INTERVAL '10 minutes'),
                                                                     (1, 4, 'Yes, I am ready!', NOW() - INTERVAL '9 minutes'),
                                                                     (1, 1, 'Call me later', NOW() - INTERVAL '8 minutes'),
                                                                     (2, 2, 'Wanna play cs2 today?', NOW() - INTERVAL '15 minutes'),
                                                                     (2, 4, 'Sure, what time?', NOW() - INTERVAL '14 minutes'),
                                                                     (3, 3, 'Sit on me pls...', NOW() - INTERVAL '5 minutes');

-- Создаем групповой чат
INSERT INTO chats (chat_name, chat_type, created_by) VALUES
    ('Game Lovers', 'group', 4)
RETURNING chat_id;

-- Добавляем участников в групповой чат
INSERT INTO chat_members (chat_id, user_id, role) VALUES
                                                      (4, 1, 'member'),
                                                      (4, 2, 'member'),
                                                      (4, 4, 'admin');

-- Сообщения в групповом чате
INSERT INTO messages (chat_id, sender_id, message_text, sent_at) VALUES
                                                                     (4, 4, 'Welcome to the gaming group!', NOW() - INTERVAL '20 minutes'),
                                                                     (4, 1, 'What games are we playing today?', NOW() - INTERVAL '18 minutes'),
                                                                     (4, 2, 'CS2 and maybe some Valorant?', NOW() - INTERVAL '15 minutes');

-- Создаем несколько сессий
INSERT INTO user_sessions (user_id, token, expires_at) VALUES
    (4, 'sample-jwt-token', NOW() + INTERVAL '7 days');

-- Создаем запросы в друзья
INSERT INTO friend_requests (from_user_id, to_user_id, status) VALUES
                                                                   (1, 4, 'accepted'),
                                                                   (2, 4, 'accepted'),
                                                                   (3, 4, 'pending');

-- Вывод информации о созданных данных
SELECT 'Database created successfully!' as status;
SELECT COUNT(*) as users_count FROM users;
SELECT COUNT(*) as chats_count FROM chats;
SELECT COUNT(*) as messages_count FROM messages;