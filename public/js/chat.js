// Хранилище данных
let chatsData = [];
let currentChatId = null;
let currentUser = null;

// DOM элементы
const chatMessages = document.getElementById('chat-messages');
const messageInput = document.getElementById('message-input');
const sendButton = document.getElementById('send-button');
const currentFriendName = document.getElementById('current-friend-name');
const friendsList = document.getElementById('friends-list');

// Инициализация
document.addEventListener('DOMContentLoaded', async function() {
    await loadUserData();
    await loadChats();
    setupEventListeners();
});

// Загрузка данных пользователя
async function loadUserData() {
    try {
        const response = await fetch('/api/check-auth');
        const data = await response.json();

        if (data.authenticated) {
            // Получаем информацию о текущем пользователе
            // (в реальном приложении нужен отдельный endpoint)
            currentUser = { id: 1, name: 'You' }; // Заглушка
        } else {
            window.location.href = '/login';
        }
    } catch (error) {
        console.error('Error loading user data:', error);
    }
}

// Загрузка чатов
async function loadChats() {
    try {
        const response = await fetch('/api/chats');
        if (!response.ok) throw new Error('Failed to load chats');

        const data = await response.json();
        chatsData = data.chats || [];

        renderChatsList();

        // Автоматически выбираем первый чат
        if (chatsData.length > 0) {
            await selectChat(chatsData[0].chat_id);
        }
    } catch (error) {
        console.error('Error loading chats:', error);
        showNotification('Ошибка загрузки чатов');
    }
}

// Рендер списка чатов
function renderChatsList() {
    friendsList.innerHTML = '';

    chatsData.forEach(chat => {
        const chatItem = document.createElement('li');
        chatItem.className = 'friend-item';
        chatItem.dataset.chatId = chat.chat_id;

        chatItem.innerHTML = `
            <div class="friend-avatar"></div>
            <div>
                <span class="friend-name">${chat.chat_name}</span>
                <div class="friend-last-message">${chat.last_message || 'Нет сообщений'}</div>
            </div>
        `;

        chatItem.addEventListener('click', () => selectChat(chat.chat_id));
        friendsList.appendChild(chatItem);
    });
}

// Выбор чата
async function selectChat(chatId) {
    currentChatId = chatId;

    // Обновляем UI
    const chat = chatsData.find(c => c.chat_id === chatId);
    if (chat) {
        currentFriendName.textContent = chat.chat_name;
    }

    // Загружаем сообщения
    await loadMessages(chatId);

    // Прокручиваем вниз
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

// Загрузка сообщений
async function loadMessages(chatId) {
    try {
        const response = await fetch(`/api/messages?chat_id=${chatId}`);
        if (!response.ok) throw new Error('Failed to load messages');

        const data = await response.json();
        renderMessages(data.messages || []);
    } catch (error) {
        console.error('Error loading messages:', error);
        showNotification('Ошибка загрузки сообщений');
    }
}

// Рендер сообщений
function renderMessages(messages) {
    chatMessages.innerHTML = '';

    messages.forEach(message => {
        const messageClass = message.is_own ? 'message-outgoing' : 'message-incoming';
        const messageElement = document.createElement('div');
        messageElement.className = `message ${messageClass}`;

        messageElement.innerHTML = `
            <div class="message-content">${escapeHtml(message.text)}</div>
            <div class="message-info">
                <span class="user-message">${message.sender}</span>
                <span class="message-time">${formatTime(message.time)}</span>
            </div>
        `;

        chatMessages.appendChild(messageElement);
    });

    // Прокручиваем вниз
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

// Отправка сообщения
async function sendMessage() {
    const messageText = messageInput.value.trim();

    if (!messageText || !currentChatId) return;

    try {
        const response = await fetch('/api/send-message', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                chat_id: currentChatId,
                text: messageText
            })
        });

        if (!response.ok) throw new Error('Failed to send message');

        // Очищаем поле ввода
        messageInput.value = '';

        // Перезагружаем сообщения
        await loadMessages(currentChatId);
        await loadChats(); // Обновляем список чатов

    } catch (error) {
        console.error('Error sending message:', error);
        showNotification('Ошибка отправки сообщения');
    }
}

// Утилиты
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function formatTime(timestamp) {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function showNotification(text) {
    // Реализация показа уведомлений
    console.log('Notification:', text);
}

// Настройка обработчиков событий
function setupEventListeners() {
    sendButton.addEventListener('click', sendMessage);

    messageInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    // Периодическое обновление сообщений
    setInterval(() => {
        if (currentChatId) {
            loadMessages(currentChatId);
            loadChats();
        }
    }, 5000); // Обновление каждые 5 секунд
}