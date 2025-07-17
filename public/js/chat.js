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
});