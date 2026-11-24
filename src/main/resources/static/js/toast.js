(function () {
    'use strict';

    if (window.FLToast) {
        return;
    }

    const TYPES = {
        success: { label: 'Listo', duration: 5000, role: 'status' },
        info: { label: 'Aviso', duration: 6000, role: 'status' },
        warning: { label: 'Atención', duration: 8000, role: 'alert' },
        error: { label: 'Error', duration: 10000, role: 'alert' }
    };
    const MAX_VISIBLE = 4;
    const MAX_MESSAGE_LENGTH = 300;
    const MAX_TITLE_LENGTH = 40;
    const MAX_DURATION = 60000;
    const MIN_RESUME_MS = 400;
    const LEAVE_MS = 160;
    const STORAGE_KEY = 'fl-toast-next';

    const active = new Map();
    let stack = null;

    function clean(value, limit) {
        const text = String(value === undefined || value === null ? '' : value).replace(/\s+/g, ' ').trim();
        return text.length > limit ? text.slice(0, limit - 1) + '…' : text;
    }

    function el(tag, className) {
        const node = document.createElement(tag);
        node.className = className;
        return node;
    }

    function getStack() {
        if (!stack || !stack.isConnected) {
            stack = el('section', 'fl-toast-stack');
            stack.setAttribute('aria-label', 'Avisos');
            document.body.appendChild(stack);
        }
        return stack;
    }

    function startTimer(toast, ms) {
        toast.remaining = ms;
        toast.startedAt = Date.now();
        toast.timerId = setTimeout(function () { dismiss(toast); }, ms);
    }

    function pause(toast) {
        if (!toast.duration || toast.paused || toast.closed) {
            return;
        }
        clearTimeout(toast.timerId);
        toast.remaining -= Date.now() - toast.startedAt;
        toast.paused = true;
        toast.node.classList.add('is-paused');
    }

    function resume(toast) {
        if (!toast.duration || !toast.paused || toast.closed) {
            return;
        }
        toast.paused = false;
        toast.node.classList.remove('is-paused');
        startTimer(toast, Math.max(toast.remaining, MIN_RESUME_MS));
    }

    function restartTimerLine(toast) {
        if (!toast.timerLine) {
            return;
        }
        toast.timerLine.style.animation = 'none';
        void toast.timerLine.offsetWidth;
        toast.timerLine.style.animation = '';
    }

    function dismiss(toast) {
        if (toast.closed) {
            return;
        }
        toast.closed = true;
        clearTimeout(toast.timerId);
        active.delete(toast.key);
        toast.node.classList.add('fl-toast--leaving');
        setTimeout(function () { toast.node.remove(); }, LEAVE_MS);
    }

    function bump(toast) {
        toast.count += 1;
        toast.countEl.textContent = '×' + toast.count;
        toast.countEl.hidden = false;
        if (toast.duration) {
            clearTimeout(toast.timerId);
            toast.paused = false;
            toast.node.classList.remove('is-paused');
            restartTimerLine(toast);
            startTimer(toast, toast.duration);
        }
    }

    function show(options) {
        const opts = options || {};
        const type = TYPES[opts.type] ? opts.type : 'info';
        const message = clean(opts.message, MAX_MESSAGE_LENGTH);
        if (!message) {
            return null;
        }
        if (!document.body) {
            document.addEventListener('DOMContentLoaded', function () { show(opts); }, { once: true });
            return null;
        }

        const key = type + '|' + message;
        const existing = active.get(key);
        if (existing) {
            bump(existing);
            return existing.handle;
        }

        const requested = Number(opts.duration);
        const duration = opts.duration === 0 ? 0
            : Math.min(requested > 0 ? requested : TYPES[type].duration, MAX_DURATION);

        const node = el('div', 'fl-toast fl-toast--' + type);
        node.setAttribute('role', TYPES[type].role);
        node.style.setProperty('--fl-toast-duration', duration + 'ms');

        const body = el('div', 'fl-toast__body');
        const label = el('p', 'fl-toast__label');
        const countEl = el('span', 'fl-toast__count');
        countEl.hidden = true;
        label.append(document.createTextNode(clean(opts.title, MAX_TITLE_LENGTH) || TYPES[type].label), countEl);
        const text = el('p', 'fl-toast__message');
        text.textContent = message;
        body.append(label, text);

        const closeButton = el('button', 'fl-toast__close');
        closeButton.type = 'button';
        closeButton.setAttribute('aria-label', 'Cerrar aviso');
        closeButton.textContent = '×';
        node.append(body, closeButton);

        let timerLine = null;
        if (duration > 0) {
            timerLine = el('span', 'fl-toast__timer');
            timerLine.setAttribute('aria-hidden', 'true');
            node.appendChild(timerLine);
        }

        const toast = {
            key: key, node: node, countEl: countEl, timerLine: timerLine, duration: duration,
            count: 1, paused: false, closed: false, timerId: null, remaining: duration, startedAt: 0
        };
        toast.handle = { close: function () { dismiss(toast); } };

        closeButton.addEventListener('click', function () { dismiss(toast); });
        node.addEventListener('mouseenter', function () { pause(toast); });
        node.addEventListener('mouseleave', function () { resume(toast); });
        node.addEventListener('focusin', function () { pause(toast); });
        node.addEventListener('focusout', function () { resume(toast); });

        active.set(key, toast);
        getStack().appendChild(node);
        if (duration > 0) {
            startTimer(toast, duration);
        }
        while (active.size > MAX_VISIBLE) {
            dismiss(active.values().next().value);
        }
        return toast.handle;
    }

    document.addEventListener('visibilitychange', function () {
        active.forEach(function (toast) {
            if (document.hidden) {
                pause(toast);
            } else {
                resume(toast);
            }
        });
    });

    function later(type, message) {
        try {
            sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ type: type, message: message }));
        } catch (e) {

        }
    }

    function showStored() {
        try {
            const raw = sessionStorage.getItem(STORAGE_KEY);
            if (!raw) {
                return;
            }
            sessionStorage.removeItem(STORAGE_KEY);
            const stored = JSON.parse(raw);
            show({ type: stored.type, message: stored.message });
        } catch (e) {

        }
    }

    function showFlash() {
        showStored();
        const flash = document.getElementById('flToastFlash');
        if (!flash) {
            return;
        }
        ['success', 'error', 'warning', 'info'].forEach(function (type) {
            if (flash.dataset[type]) {
                show({ type: type, message: flash.dataset[type] });
            }
        });
        flash.remove();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', showFlash);
    } else {
        showFlash();
    }

    function withType(type) {
        return function (message, options) {
            return show(Object.assign({}, options, { type: type, message: message }));
        };
    }

    window.FLToast = Object.freeze({
        show: show,
        later: later,
        success: withType('success'),
        error: withType('error'),
        warning: withType('warning'),
        info: withType('info'),
        clear: function () { Array.from(active.values()).forEach(function (toast) { dismiss(toast); }); }
    });
}());