// Хранилище данных
const friendsData = {
    'December00': {
        name: 'December00',
        lastMessage: 'Call me later',
        messages: [
            { sender: 'You', content: 'Are u ready?', time: '21:42' },
            { sender: 'December00', content: 'No', time: '21:42' },
            { sender: 'December00', content: 'Call me later.', time: '21:42' }
        ],
        online: true
    },
    'Tanyusha❤': {
        name: 'Tanyusha❤',
        lastMessage: 'Wanna play cs2 today?',
        messages: [
            { sender: 'Tanyusha❤', content: 'Wanna play cs2 today?', time: '20:15' }
        ],
        online: false
    },
    'Her': {
        name: 'Her',
        lastMessage: 'Sit on me pls...',
        messages: [
            { sender: 'Her', content: 'Sit on me pls...', time: '19:30' }
        ],
        online: true
    }
};

// Текущий выбранный друг
let currentFriend = 'December00';
let isAddingFriend = false;

// DOM элементы
const addFriendButton = document.getElementById('add-friend-button');
const addFriendContainer = document.getElementById('add-friend-container');
const cancelAddFriendButton = document.getElementById('cancel-add-friend');
const confirmAddFriendButton = document.getElementById('confirm-add-friend');
const friendUsernameInput = document.getElementById('friend-username');
const notification = document.getElementById('notification');
const friendsList = document.getElementById('friends-list');
const chatMessages = document.getElementById('chat-messages');
const messageInput = document.getElementById('message-input');
const sendButton = document.getElementById('send-button');
const currentFriendName = document.getElementById('current-friend-name');

// Показать/скрыть поле добавления друга
addFriendButton.addEventListener('click', () => {
    if (isAddingFriend) {
        closeAddFriendForm();
    } else {
        openAddFriendForm();
    }
});

function openAddFriendForm() {
    addFriendContainer.classList.add('active');
    friendUsernameInput.focus();
    isAddingFriend = true;
}

function closeAddFriendForm() {
    addFriendContainer.classList.remove('active');
    friendUsernameInput.value = '';
    isAddingFriend = false;
}

// Добавить друга
confirmAddFriendButton.addEventListener('click', () => {
    const username = friendUsernameInput.value.trim();
    if (username && !friendsData[username]) {
        // В реальном приложении здесь был бы запрос на сервер
        showNotification(`Запрос в друзья отправлен пользователю ${username}`);

        // Закрываем форму
        closeAddFriendForm();

        // Имитируем принятие запроса через 2 секунды
        setTimeout(() => {
            addNewFriend(username);
        }, 2000);
    } else if (friendsData[username]) {
        showNotification('Этот пользователь уже у вас в друзьях');
    } else {
        showNotification('Введите корректный ник');
    }
});

// Отмена добавления друга
cancelAddFriendButton.addEventListener('click', closeAddFriendForm);

// Показать уведомление
function showNotification(text) {
    notification.textContent = text;
    notification.classList.add('show');

    setTimeout(() => {
        notification.classList.remove('show');
    }, 3000);
}

// Добавить нового друга
function addNewFriend(username) {
    if (!friendsData[username]) {
        friendsData[username] = {
            name: username,
            lastMessage: '',
            messages: [],
            online: true
        };

        updateFriendsList();
        showNotification(`${username} принял ваш запрос в друзья!`);
    }
}

// Обновить список друзей
function updateFriendsList() {
    friendsList.innerHTML = '';

    Object.values(friendsData).forEach(friend => {
        const friendItem = document.createElement('li');
        friendItem.className = 'friend-item';
        friendItem.dataset.friend = friend.name;

        friendItem.innerHTML = `
                <div class="friend-avatar"></div>
                <div>
                    <span class="friend-name">${friend.name}</span>
                    <div class="friend-last-message">${friend.lastMessage || 'Нет сообщений'}</div>
                </div>
            `;

        friendItem.addEventListener('click', () => switchFriend(friend.name));
        friendsList.appendChild(friendItem);
    });
}

// Переключиться на чат с другом
function switchFriend(friendName) {
    currentFriend = friendName;
    currentFriendName.textContent = friendName;

    // Очищаем сообщения
    chatMessages.innerHTML = '';

    // Добавляем сообщения для выбранного друга
    friendsData[friendName].messages.forEach(msg => {
        const messageClass = msg.sender === 'You' ? 'message-outgoing' : 'message-incoming';
        const messageElement = document.createElement('div');
        messageElement.className = `message message-${messageClass}`;
        messageElement.innerHTML = `
                <div class="message-content">${msg.content}</div>
                <div class="message-info">
                    <span class="user-message">${msg.sender}</span>
                    <span class="message-time">${msg.time}</span>
                </div>
            `;
        chatMessages.appendChild(messageElement);
    });

    // Прокручиваем вниз
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

// Отправить сообщение
function sendMessage() {
    const messageText = messageInput.value.trim();
    if (messageText && currentFriend) {
        // Добавляем сообщение в историю
        const newMessage = {
            sender: 'You',
            content: messageText,
            time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };

        friendsData[currentFriend].messages.push(newMessage);
        friendsData[currentFriend].lastMessage = messageText;

        // Обновляем интерфейс
        updateFriendsList();
        switchFriend(currentFriend);

        // Очищаем поле ввода
        messageInput.value = '';

        // Имитируем ответ
        setTimeout(() => {
            const replyMessage = {
                sender: currentFriend,
                content: getRandomReply(),
                time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
            };

            friendsData[currentFriend].messages.push(replyMessage);
            friendsData[currentFriend].lastMessage = replyMessage.content;

            updateFriendsList();
            switchFriend(currentFriend);
        }, 1000 + Math.random() * 2000);
    }
}

// Случайные ответы
function getRandomReply() {
    const replies = [
        'Ок',
        'Я подумаю',
        'Не сейчас',
        'Давай позже',
        'Хорошо',
        'Не знаю',
        'Может быть',
        'Я занят',
        'Спасибо',
        'Угу'
    ];
    return replies[Math.floor(Math.random() * replies.length)];
}

// Обработчики отправки сообщения
sendButton.addEventListener('click', sendMessage);
messageInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
    }
});

// Инициализация
updateFriendsList();
document.addEventListener("DOMContentLoaded", function() {
    const messageInput = document.getElementById("message-input");
    const sendButton = document.getElementById("input-chat-button");
    const chatMessages = document.getElementById("chat-messages");

    function sendMessage() {
        const messageText = messageInput.value.trim();

        if (messageText === "") return;

        const messageElement = document.createElement("div");
        messageElement.className = "message message-outgoing";
        messageElement.innerHTML = `
                <div class="message-content">${messageText}</div>
                <div class="message-info">
                    <span class="user-message">You</span>
                    <span class="message-time">${getCurrentTime()}</span>
                </div>
            `;

        chatMessages.appendChild(messageElement);

        messageInput.value = "";

        chatMessages.scrollTop = chatMessages.scrollHeight;

        messageInput.focus();
    }

    sendButton.addEventListener("click", sendMessage);

    messageInput.addEventListener("keypress", function(e) {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    function getCurrentTime() {
        const now = new Date();
        return now.getHours().toString().padStart(2, '0') + ":" +
            now.getMinutes().toString().padStart(2, '0');
    }

    messageInput.focus();
});

document.getElementById('loginForm').addEventListener('submit', async function(e) {
    e.preventDefault();
    const errorElement = document.getElementById('loginError');
    errorElement.textContent = '';

    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;

    console.log(`Login attempt for user: ${username}`); // Логирование на клиенте

    try {
        const response = await fetch('/api/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        const data = await response.json();

        if (data.status === "success") {
            // Сохраняем токен и переходим в чат
            localStorage.setItem('auth_token', data.token);
            window.location.href = '/chat';
        } else {
            throw new Error(data.message || "Login failed");
        }
    } catch (error) {
        errorElement.textContent = error.message;
    }
}
);
