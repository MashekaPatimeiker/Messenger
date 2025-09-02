let chatsData = [];
let currentChatId = null;
let currentUser = null;
let currentUserId = null;
let currentUsername = null;
let websocket = null;
let isWebSocketConnected = false;
let reconnectAttempts = 0;
const MAX_RECONNECT_ATTEMPTS = 5;
let searchContainer = null;
let searchInput = null;
let searchResults = null;
let searchFriendButton = null;
const chatMessages = document.getElementById('chat-messages');
const messageInput = document.getElementById('message-input');
const sendButton = document.getElementById('send-button');
const currentFriendName = document.getElementById('current-friend-name');
const friendsList = document.getElementById('friends-list');

document.addEventListener('DOMContentLoaded', async function() {
    await init();
});
fetch('/api/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
})
    .then(response => response.json())
    .then(data => {
        console.log('Login response:', data);
        if (data.status === 'success') {
            // Сохраняем токен в localStorage как fallback
            if (data.token) {
                localStorage.setItem('auth_token', data.token);
                console.log('Token saved to localStorage');
            }
            window.location.href = '/chat';
        }
    })
function getCookie(name) {
    try {
        const value = `; ${document.cookie}`;
        const parts = value.split(`; ${name}=`);
        if (parts.length === 2) {
            let cookieValue = parts.pop().split(';').shift();
            return cookieValue ? decodeURIComponent(cookieValue) : null;
        }
        return null;
    } catch (e) {
        console.error('Error reading cookie:', e);
        return null;
    }
}

function connectWebSocket() {
    try {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            console.log('Max reconnection attempts reached');
            return;
        }

        let token = getCookie('auth_token') || localStorage.getItem('auth_token');
        console.log('Token found:', token ? 'YES' : 'NO');

        if (!token) {
            console.log('No token found, will try to continue without WebSocket');
            return;
        }

        try {
            if (token.startsWith('dG9rZW4') || token.includes('_')) {
                // Это base64 токен, декодируем
                const decoded = atob(token);
                if (decoded.startsWith('token_')) {
                    token = decoded;
                }
            }
        } catch (e) {
            console.log('Token is not base64 encoded');
        }

        console.log('Using token:', token);

        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//192.168.100.5:8081/ws?token=${encodeURIComponent(token)}`;

        console.log('Connecting to WebSocket:', wsUrl);

        if (websocket) {
            websocket.close();
        }

        websocket = new WebSocket(wsUrl);

        websocket.onopen = function() {
            console.log('WebSocket connected successfully');
            isWebSocketConnected = true;
            reconnectAttempts = 0;

            // Отправляем токен для аутентификации
            const authMessage = JSON.stringify({
                type: 'auth',
                token: token
            });
            websocket.send(authMessage);
        };

        websocket.onmessage = function(event) {
            console.log('WebSocket message received:', event.data);
            handleWebSocketMessage(event.data);
        };

        websocket.onerror = function(error) {
            console.error('WebSocket error:', error);
            isWebSocketConnected = false;
        };

        websocket.onclose = function(event) {
            console.log('WebSocket connection closed:', event.code, event.reason);
            isWebSocketConnected = false;

            if (event.code !== 1000) {
                reconnectAttempts++;
                const delay = Math.min(3000 * Math.pow(2, reconnectAttempts), 30000);
                console.log(`Reconnecting in ${delay}ms (attempt ${reconnectAttempts})`);
                setTimeout(connectWebSocket, delay);
            }
        };

    } catch (error) {
        console.error('WebSocket connection failed:', error);
    }
}


async function apiRequest(endpoint, options = {}) {
    try {
        const baseURL = window.location.origin;
        const url = `${baseURL}${endpoint}`;

        const token = getCookie('auth_token') || localStorage.getItem('auth_token');
        const headers = {
            'Content-Type': 'application/json',
            ...options.headers
        };

        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        const config = {
            headers: headers,
            credentials: 'include',
            ...options
        };

        if (options.body) {
            config.body = options.body;
        }

        const response = await fetch(url, config);

        if (response.status === 401) {
            console.log('Unauthorized - trying to refresh token');
            // Пробуем обновить токен
            const refreshed = await refreshToken();
            if (refreshed) {
                // Повторяем запрос с новым токеном
                return apiRequest(endpoint, options);
            }
            return null;
        }

        if (!response.ok) {
            throw new Error(`HTTP error: ${response.status}`);
        }

        return await response.json();
    } catch (error) {
        console.error('API request failed:', error);
        return null;
    }
}
async function refreshToken() {
    try {
        console.log('Attempting to refresh token...');

        const response = await fetch('/api/refresh-token', {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            const data = await response.json();
            if (data.token) {
                // Сохраняем новый токен
                localStorage.setItem('auth_token', data.token);
                setCookie('auth_token', data.token);
                console.log('Token refreshed successfully');
                return true;
            }
        }

        console.log('Token refresh failed');
        return false;

    } catch (error) {
        console.error('Token refresh error:', error);
        return false;
    }
}
function handleWebSocketMessage(message) {
    console.log('WebSocket message received:', message);

    try {
        const data = JSON.parse(message);

        if (data.type === 'auth') {
            if (data.status === 'success') {
                console.log('WebSocket authenticated successfully');
                if (data.user_id) currentUserId = data.user_id;
                if (data.username) currentUsername = data.username;
                loadChatsHTTP();
            }
            return;
        }

        if (data.type === 'chat_joined') {
            console.log('Successfully joined chat:', data.chat_id);
            return;
        }

        if (data.type === 'new_message') {
            handleNewMessage(data);
            return;
        }

        if (data.type === 'message_sent') {
            console.log('Message sent successfully, ID:', data.message_id);
            return;
        }

        if (data.type === 'error') {
            console.error('WebSocket error:', data.message);
            showNotification('Ошибка: ' + data.message);
            return;
        }

        if (data.type === 'messages') {
            console.log('Messages list received:', data.messages);
            renderMessages(data.messages || []);
            return;
        }

        if (data.type === 'chats') {
            console.log('Chats list received:', data.chats);
            chatsData = data.chats || [];
            renderChatsList();
            return;
        }

    } catch (e) {
        console.log('Plain text message:', message);
    }
}

function handleNewMessage(data) {
    console.log('New message received:', data);

    if (data.type === 'new_message' && data.chat_id) {
        console.log('Processing new message for chat:', data.chat_id, data);

        const existingMessage = document.querySelector(`[data-message-id="${data.message_id}"]`);
        if (existingMessage) {
            console.log('Message already exists in UI, skipping:', data.message_id);
            return;
        }

        if (data.chat_id == currentChatId) {
            addMessageToChat({
                message_id: data.message_id,
                sender_id: data.sender_id,
                sender: data.sender_name,
                text: data.text,
                time: data.sent_at || data.time,
                is_own: data.sender_id == currentUserId
            });

            // Прокручиваем вниз
            setTimeout(() => {
                if (chatMessages) chatMessages.scrollTop = chatMessages.scrollHeight;
            }, 100);
        }

        loadChatsHTTP();
    }
}
// Добавьте функцию для настройки поиска
function setupSearchFunctionality() {
    searchContainer = document.getElementById('search-container');
    searchInput = document.getElementById('search-username');
    searchResults = document.getElementById('search-results');
    searchFriendButton = document.getElementById('search-friend-button');

    if (searchFriendButton && searchInput) {
        searchFriendButton.addEventListener('click', toggleSearch);
        searchInput.addEventListener('input', debounce(handleSearch, 300));
        searchInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                handleSearch();
            }
        });
    }
}
function toggleSearch() {
    if (searchContainer.style.display === 'none') {
        searchContainer.style.display = 'block';
        searchInput.focus();
    } else {
        searchContainer.style.display = 'none';
        searchResults.innerHTML = '';
    }
}

// Функция для поиска пользователей
async function handleSearch() {
    const query = searchInput.value.trim();
    if (!query) {
        searchResults.innerHTML = '';
        return;
    }

    try {
        const users = await searchUsersAPI(query);
        displaySearchResults(users);
    } catch (error) {
        console.error('Search error:', error);
        showNotification('Ошибка поиска');
    }
}

async function searchUsersAPI(query) {
    try {
        const data = await apiRequest(`/api/search-users?q=${encodeURIComponent(query)}`);
        return data.users || [];
    } catch (error) {
        console.error('Search API error:', error);
        return [];
    }
}

function displaySearchResults(users) {
    searchResults.innerHTML = '';

    if (users.length === 0) {
        searchResults.innerHTML = '<div class="no-results">Пользователи не найдены</div>';
        return;
    }

    users.forEach(user => {
        const resultItem = document.createElement('div');
        resultItem.className = 'search-result-item';
        resultItem.innerHTML = `
            <div class="search-result-avatar">${user.username.charAt(0)}</div>
            <div class="search-result-info">
                <div class="search-result-name">${escapeHtml(user.username)}</div>
                <div class="search-result-status">${user.status || 'offline'}</div>
            </div>
        `;

        resultItem.addEventListener('click', () => startChatWithUser(user));
        searchResults.appendChild(resultItem);
    });
}

// Функция для начала чата с пользователем
async function startChatWithUser(user) {
    try {
        const chatData = await createOrGetPrivateChatAPI(user.id);

        if (chatData && chatData.chat_id) {
            // Добавляем чат в список
            if (!chatsData.find(c => c.chat_id === chatData.chat_id)) {
                chatsData.push({
                    chat_id: chatData.chat_id,
                    chat_name: user.username,
                    chat_type: 'private',
                    last_message: '',
                    last_message_time: new Date().toISOString()
                });
            }

            // Выбираем чат
            await selectChat(chatData.chat_id);

            // Скрываем поиск
            searchContainer.style.display = 'none';
            searchInput.value = '';
            searchResults.innerHTML = '';

            showNotification(`Чат с ${user.username} начат`);
        }
    } catch (error) {
        console.error('Error starting chat:', error);
        showNotification('Ошибка создания чата');
    }
}

async function createOrGetPrivateChatAPI(userId) {
    try {
        const data = await apiRequest('/api/create-chat', {
            method: 'POST',
            body: JSON.stringify({ user_id: userId })
        });
        return data;
    } catch (error) {
        console.error('Create chat API error:', error);
        throw error;
    }
}

// Функция для debounce (задержки поиска)
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}
async function loadChatsHTTP() {
    try {
        chatsData = await loadChatsAPI();
        renderChatsList();
        if (chatsData.length > 0 && !currentChatId) {
            await selectChat(chatsData[0].chat_id);
        }
    } catch (error) {
        console.error('Error loading chats:', error);
        chatsData = getTestChats();
        renderChatsList();
    }
}

async function loadChatsAPI() {
    try {
        const data = await apiRequest('/api/chats');
        return data.chats || [];
    } catch (error) {
        console.error('Error loading chats:', error);
        return getTestChats();
    }
}
async function joinChat(chatId) {
    if (!isWebSocketConnected || !websocket || websocket.readyState !== WebSocket.OPEN) {
        console.log('WebSocket not connected, skipping join chat');
        return;
    }

    console.log('Joining chat:', chatId);

    try {
        const joinData = {
            type: 'join_chat',
            chat_id: parseInt(chatId)
        };

        websocket.send(JSON.stringify(joinData));
        console.log('Join chat request sent');

    } catch (error) {
        console.error('Error joining chat:', error);
    }
}

async function selectChat(chatId) {
    currentChatId = chatId;
    const chat = chatsData.find(c => c.chat_id == chatId);
    if (chat && currentFriendName) {
        currentFriendName.textContent = chat.chat_name;
    }

    await joinChat(chatId);

    if (friendsList) {
        const chatItems = friendsList.querySelectorAll('.friend-item');
        chatItems.forEach(item => {
            item.classList.remove('active');
            if (item.dataset.chatId == chatId) {
                item.classList.add('active');
                const badge = item.querySelector('.unread-badge');
                if (badge) badge.remove();
            }
        });
    }

    await loadMessagesHTTP(chatId);
}
function setCookie(name, value, days = 7) {
    try {
        const encodedValue = encodeURIComponent(value);
        let expires = "";
        if (days) {
            const date = new Date();
            date.setTime(date.getTime() + (days * 24 * 60 * 60 * 1000));
            expires = "; expires=" + date.toUTCString();
        }
        document.cookie = name + "=" + encodedValue + expires + "; path=/; SameSite=Lax";
        console.log('Cookie set manually:', name);
    } catch (e) {
        console.error('Error setting cookie:', e);
    }
}

async function loadUserData() {
    try {
        console.log('Loading user data...');

        let token = getCookie('auth_token') || localStorage.getItem('auth_token');
        console.log('Token available:', !!token);

        if (!token) {
            console.log('No token available, running in guest mode');
            currentUserId = 1;
            currentUsername = 'Гость';
            return;
        }

        try {
            const data = await apiRequest('/api/user-info');
            if (data && data.user) {
                currentUser = data.user;
                currentUserId = data.user.id;
                currentUsername = data.user.username;
                console.log('User loaded from API:', currentUsername);
            } else {
                throw new Error('No user data');
            }
        } catch (apiError) {
            console.log('API user info failed, using fallback');
            // Пробуем извлечь из токена
            try {
                const tokenParts = token.split('_');
                if (tokenParts.length >= 3) {
                    currentUserId = parseInt(tokenParts[1]) || 1;
                    currentUsername = 'Пользователь';
                }
            } catch (e) {
                currentUserId = 1;
                currentUsername = 'Пользователь';
            }
        }

    } catch (error) {
        console.error('Error loading user info:', error);
        currentUserId = 1;
        currentUsername = 'Пользователь';
    }
}
async function loadMessagesHTTP(chatId) {
    try {
        const messages = await loadMessagesAPI(chatId);
        renderMessages(messages);
    } catch (error) {
        console.error('Error loading messages:', error);
        renderMessages(getTestMessages());
    }
}

async function loadMessagesAPI(chatId) {
    try {
        const data = await apiRequest(`/api/messages?chat_id=${chatId}`);
        return data.messages || [];
    } catch (error) {
        console.error('Error loading messages:', error);
        return getTestMessages();
    }
}

async function sendMessage() {
    const messageText = messageInput.value.trim();
    if (!messageText || !currentChatId) return;

    console.log('Sending message:', messageText, 'to chat:', currentChatId);

    try {
        await joinChat(currentChatId);

        await new Promise(resolve => setTimeout(resolve, 100));

        if (isWebSocketConnected && websocket && websocket.readyState === WebSocket.OPEN) {
            const messageData = {
                type: 'message',
                chat_id: parseInt(currentChatId),
                text: messageText
            };
            websocket.send(JSON.stringify(messageData));
            console.log('Message sent via WebSocket');
        } else {
            await sendMessageAPI(currentChatId, messageText);
            console.log('Message sent via HTTP');
        }
        messageInput.value = '';
        if (chatMessages) {
            chatMessages.scrollTop = chatMessages.scrollHeight;
        }

    } catch (error) {
        console.error('Error sending message:', error);
        showNotification('Ошибка отправки сообщения');
    }
}
async function sendMessageAPI(chatId, text) {
    try {
        const data = await apiRequest('/api/send-message', {
            method: 'POST',
            body: JSON.stringify({ chat_id: chatId, text: text })
        });
        return data;
    } catch (error) {
        console.error('Error sending message:', error);
        throw error;
    }
}

function getTestChats() {
    return [
        {
            chat_id: 1,
            chat_name: "Тестовый чат",
            chat_type: "private",
            last_message: "Добро пожаловать!",
            last_message_time: new Date().toISOString()
        }
    ];
}

function getTestMessages() {
    return [
        {
            message_id: 1,
            sender: "Система",
            text: "Добро пожаловать в чат!",
            time: new Date().toISOString(),
            is_own: false
        }
    ];
}

async function init() {
    try {
        console.log('Initializing chat application...');
        await loadUserData();
        setupEventListeners();
        setupSearchFunctionality(); // Добавьте эту строку

        setTimeout(connectWebSocket, 1000);
        await loadChatsHTTP();

        console.log('Chat application initialized successfully');

    } catch (error) {
        console.error('Initialization error:', error);
        showNotification('Приложение загружено в ограниченном режиме');
    }
}

function setupEventListeners() {
    if (sendButton) {
        sendButton.addEventListener('click', sendMessage);
    }

    if (messageInput) {
        messageInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showNotification(text) {
    console.log('Notification:', text);
    alert(text);
}

function renderChatsList() {
    if (!friendsList) return;
    friendsList.innerHTML = '';

    chatsData.forEach(chat => {
        const chatItem = document.createElement('div');
        chatItem.className = 'friend-item';
        chatItem.dataset.chatId = chat.chat_id;
        chatItem.innerHTML = `
            <div class="friend-avatar">${chat.chat_name.charAt(0)}</div>
            <div class="friend-info">
                <div class="friend-name">${escapeHtml(chat.chat_name)}</div>
                <div class="last-message">${escapeHtml(chat.last_message)}</div>
            </div>
            <div class="message-time">${formatTime(chat.last_message_time)}</div>
        `;
        chatItem.addEventListener('click', () => selectChat(chat.chat_id));
        friendsList.appendChild(chatItem);
    });
}

function renderMessages(messages) {
    if (!chatMessages) return;
    chatMessages.innerHTML = '';

    messages.forEach(message => {
        addMessageToChat(message);
    });

    setTimeout(() => {
        if (chatMessages) chatMessages.scrollTop = chatMessages.scrollHeight;
    }, 100);
}

function addMessageToChat(message) {
    if (!chatMessages) return;

    const messageElement = document.createElement('div');
    messageElement.className = `message ${message.is_own ? 'message-outgoing' : 'message-incoming'}`;

    if (message.message_id) {
        messageElement.setAttribute('data-message-id', message.message_id);
    }

    const time = new Date(message.time);
    const timeString = time.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    messageElement.innerHTML = `
        ${!message.is_own ? `<div class="message-avatar">${message.sender ? message.sender.charAt(0) : '?'}</div>` : ''}
        <div class="message-content">
            <div class="message-text">${escapeHtml(message.text)}</div>
            <div class="message-info">
                ${!message.is_own ? `<span class="user-name">${escapeHtml(message.sender)}</span>` : ''}
                <span class="message-time">${timeString}</span>
            </div>
        </div>
    `;

    chatMessages.appendChild(messageElement);

    setTimeout(() => {
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }, 50);
}
function formatTime(dateString) {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}