(function () {
    'use strict';

    const forms = document.querySelectorAll('form[action*="/trainings/"]');

    function setDisabled(form, disabled) {
        const button = form.querySelector('button');
        if (button) {
            button.disabled = disabled;
        }
    }

    forms.forEach(function (form) {
        form.addEventListener('submit', function () { setDisabled(form, true); });
    });

    window.addEventListener('pageshow', function (event) {
        if (event.persisted) {
            forms.forEach(function (form) { setDisabled(form, false); });
        }
    });
}());