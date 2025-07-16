document.getElementById('loginForm').addEventListener('submit', async function(e) {
    e.preventDefault();
    const errorElement = document.getElementById('loginError');
    errorElement.textContent = '';

    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;

    try {
        const response = await fetch('/api/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                username: username,
                password: password
            })
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || 'Login failed');
        }

        if (data.status === "success") {
            window.location.href = '/chat';
        } else {
            throw new Error(data.message || 'Authentication failed');
        }
    } catch (error) {
        console.error('Login error:', error);
        errorElement.textContent = error.message;
    }
});

// Заглушка для будущей регистрации йоу
//document.getElementById('registerLink').addEventListener('click', function(e) {
  //  e.preventDefault();
    //alert('Функция регистрации новых пользователей находится в разработке');
    //console.log('Регистрация нового пользователя - функция в разработке');
//});