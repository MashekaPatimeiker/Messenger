document.getElementById('showRegisterLink').addEventListener('click', function(e) {
    e.preventDefault();
    document.getElementById('loginContainer').style.display = 'none';
    document.getElementById('registerContainer').style.display = 'block';
});

document.getElementById('showLoginLink').addEventListener('click', function(e) {
    e.preventDefault();
    document.getElementById('registerContainer').style.display = 'none';
    document.getElementById('loginContainer').style.display = 'block';
});

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

document.getElementById('registerForm').addEventListener('submit', async function(e) {
    e.preventDefault();
    const errorElement = document.getElementById('registerError');
    errorElement.textContent = '';

    const username = document.getElementById('regUsername').value;
    const email = document.getElementById('regEmail').value;
    const password = document.getElementById('regPassword').value;
    const confirmPassword = document.getElementById('regConfirmPassword').value;

    if (password !== confirmPassword) {
        document.getElementById('regPassword').classList.add('password-mismatch');
        document.getElementById('regConfirmPassword').classList.add('password-mismatch');
        errorElement.textContent = 'Пароли не совпадают';
        return;
    } else {
        document.getElementById('regPassword').classList.remove('password-mismatch');
        document.getElementById('regConfirmPassword').classList.remove('password-mismatch');
    }

    try {
        const response = await fetch('/api/register', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                username: username,
                email: email,
                password: password
            })
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || 'Registration failed');
        }

        if (data.status === "success") {
            alert('Регистрация успешна! Теперь вы можете войти.');
            document.getElementById('registerContainer').style.display = 'none';
            document.getElementById('loginContainer').style.display = 'block';
            document.getElementById('registerForm').reset();
        }
    } catch (error) {
        console.error('Registration error:', error);
        errorElement.textContent = error.message;
    }
});