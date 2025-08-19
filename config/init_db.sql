-- Убедитесь, что вы подключены к messenger_db
\c messenger_db;

-- Таблица чатов (если еще не создана)
CREATE TABLE IF NOT EXISTS chats (
                                     chat_id SERIAL PRIMARY KEY,
                                     chat_name VARCHAR(100),
                                     chat_type VARCHAR(20) DEFAULT 'private',
                                     created_by INTEGER REFERENCES users(user_id),
                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Таблица участников чатов
CREATE TABLE IF NOT EXISTS chat_members (
                                            member_id SERIAL PRIMARY KEY,
                                            chat_id INTEGER REFERENCES chats(chat_id) ON DELETE CASCADE,
                                            user_id INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
                                            joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                            UNIQUE(chat_id, user_id)
);

-- Таблица сообщений
CREATE TABLE IF NOT EXISTS messages (
                                        message_id SERIAL PRIMARY KEY,
                                        chat_id INTEGER REFERENCES chats(chat_id) ON DELETE CASCADE,
                                        sender_id INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
                                        message_text TEXT NOT NULL,
                                        sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                        is_read BOOLEAN DEFAULT FALSE
);

-- Добавьте индексы для производительности
CREATE INDEX IF NOT EXISTS idx_messages_chat_id ON messages(chat_id);
CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_messages_sent_at ON messages(sent_at);