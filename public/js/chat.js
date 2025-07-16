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
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ username, password })
        });

        if (!response.ok) {
            const error = await response.json().catch(() => null);
            console.error(`Login failed for ${username}: ${error?.message || response.status}`);
            throw new Error(error?.message || `Login failed with status ${response.status}`);
        }

        const data = await response.json();
        authToken = data.token;
        console.log(`User ${username} logged in successfully. Token: ${authToken.substring(0, 10)}...`);

        document.getElementById('manualToken').value = authToken;
        updateAuthStatus(`Logged in successfully! Token: ${authToken.substring(0, 10)}...`, true);
        enableProtectedFeatures();
        modal.style.display = "none";

        // Логирование перед входом в чат
        console.log(`User ${username} entering chat...`);
        // Здесь можно добавить дополнительный запрос к серверу для логирования
    } catch (error) {
        console.error(`Login error for ${username}:`, error);
        errorElement.textContent = error.message;
    }
});