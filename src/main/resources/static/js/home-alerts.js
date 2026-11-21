(function () {
    'use strict';

    const SUCCESS_DISMISS_MS = 6000;
    const FADE_MS = 300;

    const alertBox = document.getElementById('homeAlert');
    if (alertBox) {
        const close = function () {
            alertBox.classList.add('home-alert--leaving');
            setTimeout(function () { alertBox.remove(); }, FADE_MS);
        };
        document.getElementById('homeAlertClose').addEventListener('click', close);
        if (!alertBox.classList.contains('home-alert--error')) {
            setTimeout(close, SUCCESS_DISMISS_MS);
        }
    }

    const forms = document.querySelectorAll('form[action*="/trainings/"]');
    forms.forEach(function (form) {
        form.addEventListener('submit', function () {
            const button = form.querySelector('button');
            if (button) {
                button.disabled = true;
            }
        });
    });

    window.addEventListener('pageshow', function (event) {
        if (event.persisted) {
            forms.forEach(function (form) {
                const button = form.querySelector('button');
                if (button) {
                    button.disabled = false;
                }
            });
        }
    });
}());