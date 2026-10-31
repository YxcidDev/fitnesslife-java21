const registerForm = document.querySelector('.sign-up-form');
const registerInputs = document.querySelectorAll('.sign-up-form input[type="text"], .sign-up-form input[type="email"], .sign-up-form input[type="password"]');

const expressions = {
    name: /^[a-zA-ZÀ-ÿ\s]{2,40}$/,
    lastname: /^[a-zA-ZÀ-ÿ\s]{2,40}$/,
    identification: /^\d{6,12}$/,
    phone: /^(\+?57)?[- ]?3[0-9]{9}$/,
    email: /^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/,
    password: /^(?=.*\d)(?=.*[a-z])(?=.*[A-Z])(?=.*[a-zA-Z]).{8,}$/
};

const errorMessages = {
    name: 'Solo letras, entre 2 y 40 caracteres',
    lastname: 'Solo letras, entre 2 y 40 caracteres',
    identification: 'Entre 6 y 12 dígitos',
    phone: '10 dígitos e inicia con 3',
    email: 'Correo electrónico inválido',
    password: 'Mín. 8 caracteres: mayúscula, minúscula y número'
};

const validateForm = (e) => {
    if (expressions[e.target.name]) {
        validateField(expressions[e.target.name], e.target);
    }
};

const validateField = (expression, input) => {
    const inputWrap = input.closest('.input-wrap');
    let errorMsg = inputWrap.querySelector('.form__input-error');

    if (!errorMsg) {
        errorMsg = document.createElement('p');
        errorMsg.className = 'form__input-error';
        errorMsg.textContent = errorMessages[input.name] || 'Campo inválido';
        inputWrap.appendChild(errorMsg);
    }

    if (expression.test(input.value)) {
        input.classList.remove('form__group-incorrect');
        input.classList.add('form__group-correct');
        errorMsg.classList.remove('form__input-error-active');
    } else {
        input.classList.remove('form__group-correct');
        input.classList.add('form__group-incorrect');
        errorMsg.classList.add('form__input-error-active');
    }
};

registerInputs.forEach((input) => {
    input.addEventListener('keyup', validateForm);
    input.addEventListener('blur', validateForm);
});

registerForm.addEventListener('submit', (e) => {
    let formValid = true;

    registerInputs.forEach((input) => {
        const expression = expressions[input.name];
        if (expression && !expression.test(input.value)) {
            validateField(expression, input);
            formValid = false;
        }
    });

    if (formValid) {
        console.log('Formulario válido. Enviando...');
    } else {
        console.log('Formulario inválido. Por favor revisa los campos.');
        e.preventDefault();
    }
});