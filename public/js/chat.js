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