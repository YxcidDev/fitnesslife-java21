'use strict';

const DISPLAY_DURATION  = 4000;
const FRONTEND_DEDUP_MS = 3000;
const QR_MIN_LENGTH     = 4;
const QR_MAX_LENGTH     = 200;
const API_ENDPOINT      = '/api/attendance/verify';
const GYM_TIMEZONE      = 'America/Bogota';
const MESSAGE_MS        = 4000;
const ERROR_MESSAGE_MS  = 6000;

const STATUS_CONFIG = {
    ALLOWED:   { state: 'allowed', headline: 'Acceso permitido' },
    DENIED:    { state: 'denied',  headline: 'Acceso denegado' },
    EXIT:      { state: 'exit',    headline: 'Salida registrada' },
    NOT_FOUND: { state: 'denied',  headline: 'Código no reconocido' },
};

class SessionError extends Error {}

function getCsrfToken() {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : null;
}

const byId = (id) => document.getElementById(id);

const qrInput          = byId('qrInput');
const stateIdle        = byId('stateIdle');
const stateProcessing  = byId('stateProcessing');
const stateResult      = byId('stateResult');

const resultPhoto      = byId('resultPhoto');
const resultPhotoFb    = byId('resultPhotoFallback');
const resultName       = byId('resultName');
const resultPlan       = byId('resultPlan');
const resultStatus     = byId('resultStatus');
const resultDetail     = byId('resultDetail');
const resultFooterId   = byId('resultFooterId');
const resultFooterTime = byId('resultFooterTime');

const countdownBar     = byId('countdownBar');
const countdownFill    = byId('countdownFill');
const kioskMessage     = byId('kioskMessage');

const clockTime        = byId('currentTime');
const clockDate        = byId('currentDate');

let qrBuffer        = '';
let isProcessing    = false;
let returnTimer     = null;
let lastScannedCode = null;
let lastScannedTime = 0;

const timeFormat = new Intl.DateTimeFormat('es-CO', {
    hour: 'numeric', minute: '2-digit', hour12: true, timeZone: GYM_TIMEZONE,
});
const dateFormat = new Intl.DateTimeFormat('es-CO', {
    weekday: 'long', day: 'numeric', month: 'long', timeZone: GYM_TIMEZONE,
});
const footerDateFormat = new Intl.DateTimeFormat('es-CO', {
    day: 'numeric', month: 'short', year: 'numeric', timeZone: GYM_TIMEZONE,
});

function capitalize(text) {
    return text.charAt(0).toUpperCase() + text.slice(1);
}

function updateClock() {
    const now = new Date();
    clockTime.textContent = timeFormat.format(now);
    clockDate.textContent = capitalize(dateFormat.format(now));
}

setInterval(updateClock, 1000);
updateClock();

function refocusInput() {
    if (document.activeElement !== qrInput) {
        qrInput.focus();
    }
}

document.addEventListener('click',   refocusInput);
document.addEventListener('keydown', refocusInput);
document.addEventListener('visibilitychange', () => {
    if (!document.hidden) refocusInput();
});

window.addEventListener('load', () => {
    setTimeout(refocusInput, 100);
});

qrInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
        const code = qrBuffer.trim();
        qrBuffer = '';
        qrInput.value = '';

        if (code.length < QR_MIN_LENGTH) {
            return;
        }

        handleQrCode(code);
        return;
    }

    if (e.key.length === 1) {
        qrBuffer += e.key;
    }
});

qrInput.addEventListener('input', () => {
    const val = qrInput.value;
    if (val.includes('\n') || val.includes('\r')) {
        const code = val.replace(/[\r\n]/g, '').trim();
        qrInput.value = '';
        qrBuffer = '';
        if (code.length >= QR_MIN_LENGTH) {
            handleQrCode(code);
        }
    }
});

async function handleQrCode(code) {
    if (isProcessing) return;

    const now = Date.now();
    if (code === lastScannedCode && (now - lastScannedTime) < FRONTEND_DEDUP_MS) {
        showMessage('Ya leímos ese código. Espera un momento.');
        return;
    }

    if (!isValidQrFormat(code)) {
        showMessage('No reconocemos ese código QR.');
        return;
    }

    isProcessing = true;
    lastScannedCode = code;
    lastScannedTime = now;

    if (returnTimer) {
        clearTimeout(returnTimer);
        returnTimer = null;
    }

    showState('processing');

    try {
        const headers = { 'Content-Type': 'application/json' };
        const token = getCsrfToken();
        if (token) {
            headers['X-XSRF-TOKEN'] = token;
        }

        const response = await fetch(API_ENDPOINT, {
            method: 'POST',
            headers: headers,
            body: JSON.stringify({ qrCode: code }),
            credentials: 'same-origin',
        });

        if (response.redirected || response.status === 401 || response.status === 403) {
            throw new SessionError();
        }
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const data = await response.json();
        renderResult(data);
        showState('result');
        startCountdown();

        returnTimer = setTimeout(resetToIdle, DISPLAY_DURATION);

    } catch (err) {
        console.error('[Attendance] Error verifying QR:', err);

        lastScannedCode = null;
        showMessage(
            err instanceof SessionError
                ? 'No se pudo validar la sesión del kiosco. Recarga la página o inicia sesión de nuevo.'
                : 'No pudimos verificar el código. Inténtalo de nuevo.',
            true
        );
        resetToIdle();
    } finally {
        isProcessing = false;
    }
}

function isValidQrFormat(code) {
    return typeof code === 'string'
        && code.length >= QR_MIN_LENGTH
        && code.length <= QR_MAX_LENGTH
        && /^[a-zA-Z0-9\-_@.]+$/.test(code);
}

function normalize(text) {
    return String(text || '')
        .normalize('NFD').replace(/\p{M}+/gu, '')
        .toLowerCase().replace(/[^a-z0-9 ]/g, '').trim();
}

function planLabel(plan) {
    const value = (plan || '').trim();
    if (!value) return 'Sin plan';
    return /^plan\b/i.test(value) ? value : `Plan ${value}`;
}

function renderResult(data) {
    const cfg = STATUS_CONFIG[data.status] || STATUS_CONFIG.DENIED;
    document.body.dataset.state = cfg.state;

    resultName.textContent   = data.userName || 'Usuario';
    resultPlan.textContent   = planLabel(data.userPlan);
    resultStatus.textContent = cfg.headline;

    const detail = (data.message || '').trim();
    resultDetail.textContent = normalize(detail) === normalize(cfg.headline) ? '' : detail;

    const photoUrl = data.userPhoto;
    resultPhoto.alt = data.userName || '';
    if (photoUrl && photoUrl.trim() !== '') {
        resultPhoto.src = photoUrl;
        resultPhoto.style.display = 'block';
        resultPhotoFb.style.display = 'none';
        resultPhoto.onerror = () => {
            resultPhoto.style.display = 'none';
            resultPhotoFb.style.display = 'flex';
            renderPhotoFallback(data.userName);
        };
    } else {
        resultPhoto.removeAttribute('src');
        resultPhoto.style.display = 'none';
        resultPhotoFb.style.display = 'flex';
        renderPhotoFallback(data.userName);
    }

    resultFooterId.textContent = data.userId ? `ID ${formatUserId(data.userId)}` : '';

    const now = new Date();
    resultFooterTime.textContent = `${footerDateFormat.format(now)} · ${timeFormat.format(now)}`;
}

function renderPhotoFallback(name) {
    const initials = name
        ? name.split(' ').filter(Boolean).slice(0, 2).map((w) => w[0]).join('').toUpperCase()
        : '';
    resultPhotoFb.textContent = initials || '?';
}

function formatUserId(mongoId) {
    if (!mongoId) return '';
    const suffix = mongoId.slice(-5).toUpperCase();
    const year   = new Date().getFullYear().toString().slice(-2);
    return `FL${year}-C${suffix}`;
}

function showState(name) {
    stateIdle.classList.remove('state--active');
    stateProcessing.classList.remove('state--active');
    stateResult.classList.remove('state--active');

    if (name === 'idle')       stateIdle.classList.add('state--active');
    if (name === 'processing') stateProcessing.classList.add('state--active');
    if (name === 'result')     stateResult.classList.add('state--active');
}

function resetToIdle() {
    hideCountdown();
    showState('idle');
    qrBuffer = '';
    qrInput.value = '';
    refocusInput();
}

function startCountdown() {
    countdownBar.classList.add('bar--visible');
    countdownFill.style.transition = 'none';
    countdownFill.style.transform  = 'scaleX(1)';

    void countdownFill.offsetWidth;

    countdownFill.style.transition = `transform ${DISPLAY_DURATION}ms linear`;
    countdownFill.style.transform  = 'scaleX(0)';
}

function hideCountdown() {
    countdownBar.classList.remove('bar--visible');
    countdownFill.style.transition = 'none';
    countdownFill.style.transform  = 'scaleX(1)';
}

let messageTimer = null;

function showMessage(text, isError) {
    kioskMessage.textContent = text;
    kioskMessage.classList.toggle('kiosk-message--error', Boolean(isError));
    kioskMessage.classList.add('is-visible');

    clearTimeout(messageTimer);
    messageTimer = setTimeout(() => {
        kioskMessage.classList.remove('is-visible');
    }, isError ? ERROR_MESSAGE_MS : MESSAGE_MS);
}