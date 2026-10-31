(function () {
    'use strict';

    const root = document.getElementById('flChat');
    if (!root) {
        return;
    }

    const API = root.dataset.api || '/api/chat';
    const DEFAULT_MAX_LENGTH = 1000;

    const el = {
        toggle: document.getElementById('flChatToggle'),
        panel: document.getElementById('flChatPanel'),
        close: document.getElementById('flChatClose'),
        newButton: document.getElementById('flChatNew'),
        deleteButton: document.getElementById('flChatDelete'),
        messages: document.getElementById('flChatMessages'),
        empty: document.getElementById('flChatEmpty'),
        error: document.getElementById('flChatError'),
        input: document.getElementById('flChatInput'),
        send: document.getElementById('flChatSend'),
        counter: document.getElementById('flChatCounter')
    };

    const maxLength = parseInt(el.input.getAttribute('maxlength'), 10) || DEFAULT_MAX_LENGTH;

    const state = {
        conversationId: null,
        loaded: false,
        loading: false,
        sending: false,
        followLatest: true
    };

    class ChatError extends Error {
        constructor(message, status) {
            super(message);
            this.status = status;
        }
    }

    function csrfToken() {
        const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
        return match ? decodeURIComponent(match[1]) : '';
    }

    function errorMessage(status, payload) {
        if (status === 401) {
            return 'Tu sesión expiró. Recarga la página para continuar.';
        }
        if (status === 403) {
            return 'No se pudo validar tu sesión. Recarga la página e inténtalo de nuevo.';
        }
        if (payload && Array.isArray(payload.fieldErrors) && payload.fieldErrors.length > 0
            && payload.fieldErrors[0].message) {
            return payload.fieldErrors[0].message;
        }
        if (payload && typeof payload.message === 'string' && payload.message) {
            return payload.message;
        }
        return 'No se pudo completar la solicitud. Inténtalo de nuevo en unos minutos.';
    }

    async function request(path, options) {
        const settings = options || {};
        const method = settings.method || 'GET';
        const headers = { Accept: 'application/json' };
        if (settings.body !== undefined) {
            headers['Content-Type'] = 'application/json';
        }
        if (method !== 'GET') {
            headers['X-XSRF-TOKEN'] = csrfToken();
        }

        let response;
        try {
            response = await fetch(API + path, {
                method: method,
                headers: headers,
                body: settings.body,
                credentials: 'same-origin'
            });
        } catch (networkError) {
            throw new ChatError('No se pudo conectar. Revisa tu conexión e inténtalo de nuevo.', 0);
        }

        if (response.redirected) {
            throw new ChatError(errorMessage(401, null), 401);
        }
        if (response.status === 204) {
            return null;
        }

        const isJson = (response.headers.get('Content-Type') || '').includes('application/json');
        const payload = isJson ? await response.json().catch(function () { return null; }) : null;

        if (!response.ok) {
            throw new ChatError(errorMessage(response.status, payload), response.status);
        }
        if (payload === null) {
            throw new ChatError(errorMessage(0, null), response.status);
        }
        return payload;
    }

    function isNearBottom() {
        return el.messages.scrollHeight - el.messages.scrollTop - el.messages.clientHeight < 80;
    }

    function scrollToBottom() {
        el.messages.scrollTop = el.messages.scrollHeight;
    }

    function scrollToTopOf(node) {
        el.messages.scrollTop = Math.max(node.offsetTop - 8, 0);
    }

    function refreshEmpty() {
        el.empty.hidden = el.messages.querySelector('.fl-chat__msg') !== null;
    }

    function addMessage(role, content, sources) {
        const isUser = role === 'USER';
        const wrapper = document.createElement('div');
        wrapper.className = 'fl-chat__msg fl-chat__msg--' + (isUser ? 'user' : 'assistant');

        const author = document.createElement('span');
        author.className = 'fl-chat__author';
        author.textContent = isUser ? 'Tú' : 'Asistente';

        const text = document.createElement('p');
        text.className = 'fl-chat__text';
        text.textContent = content;

        wrapper.append(author, text);

        if (!isUser && Array.isArray(sources) && sources.length > 0) {
            const block = document.createElement('div');
            block.className = 'fl-chat__sources';
            const label = document.createElement('strong');
            label.textContent = 'Fuentes';
            const list = document.createElement('ul');
            sources.forEach(function (source) {
                const item = document.createElement('li');
                item.textContent = source.section ? source.title + ' — ' + source.section : source.title;
                list.appendChild(item);
            });
            block.append(label, list);
            wrapper.appendChild(block);
        }

        el.messages.appendChild(wrapper);
        refreshEmpty();
        return wrapper;
    }

    function showTyping() {
        const node = document.createElement('div');
        node.className = 'fl-chat__msg fl-chat__msg--assistant fl-chat__typing';
        node.setAttribute('aria-label', 'El asistente está escribiendo');
        for (let i = 0; i < 3; i++) {
            node.appendChild(document.createElement('span'));
        }
        el.messages.appendChild(node);
        refreshEmpty();
        scrollToBottom();
        return node;
    }

    function clearMessages() {
        el.messages.querySelectorAll('.fl-chat__msg').forEach(function (node) {
            node.remove();
        });
        refreshEmpty();
    }

    function renderMessages(list) {
        clearMessages();
        list.forEach(function (message) {
            addMessage(message.role, message.content, message.sources);
        });
        scrollToBottom();
    }

    function showError(message) {
        el.error.textContent = message;
        el.error.hidden = false;
    }

    function clearError() {
        el.error.hidden = true;
        el.error.textContent = '';
    }

    function autosize() {
        el.input.style.height = 'auto';
        el.input.style.height = Math.min(el.input.scrollHeight, 120) + 'px';
    }

    function updateComposer() {
        const length = el.input.value.length;
        el.send.disabled = state.sending || el.input.value.trim().length === 0;

        if (length >= maxLength * 0.8) {
            el.counter.hidden = false;
            el.counter.textContent = length + '/' + maxLength;
            el.counter.classList.toggle('fl-chat__counter--limit', length >= maxLength);
        } else {
            el.counter.hidden = true;
        }
    }

    function updateHeader() {
        el.deleteButton.hidden = state.conversationId === null;
    }

    function setSending(flag) {
        state.sending = flag;
        el.input.readOnly = flag;
        el.newButton.disabled = flag;
        el.deleteButton.disabled = flag;
        el.messages.setAttribute('aria-busy', String(flag));
        updateComposer();
    }

    function resetConversation() {
        state.conversationId = null;
        clearMessages();
        clearError();
        updateHeader();
    }

    async function loadLatestConversation() {
        if (state.loaded || state.loading) {
            return;
        }
        state.loading = true;
        try {
            const conversations = await request('/conversations?page=0&size=1');
            if (conversations.length > 0) {
                const latest = conversations[0];
                const messages = await request(
                    '/conversations/' + encodeURIComponent(latest.id) + '/messages?limit=50');
                state.conversationId = latest.id;
                renderMessages(messages);
            }
            state.loaded = true;
        } catch (error) {
            showError(error.message);
        } finally {
            state.loading = false;
            updateHeader();
        }
    }

    async function newConversation() {
        if (state.sending) {
            return;
        }
        clearError();
        const hasMessages = el.messages.querySelector('.fl-chat__msg') !== null;
        if (state.conversationId === null || !hasMessages) {
            el.input.focus();
            return;
        }
        try {
            const created = await request('/conversations', { method: 'POST' });
            state.conversationId = created.id;
            clearMessages();
            updateHeader();
            el.input.focus();
        } catch (error) {
            showError(error.message);
        }
    }

    async function deleteConversation() {
        if (state.conversationId === null || state.sending) {
            return;
        }
        if (!window.confirm('¿Eliminar esta conversación? Esta acción no se puede deshacer.')) {
            return;
        }
        try {
            await request('/conversations/' + encodeURIComponent(state.conversationId), { method: 'DELETE' });
        } catch (error) {
            if (error.status !== 404) {
                showError(error.message);
                return;
            }
        }
        resetConversation();
        el.input.focus();
    }

    async function send() {
        if (state.sending) {
            return;
        }
        const text = el.input.value.trim();
        if (text.length === 0) {
            return;
        }
        if (text.length > maxLength) {
            showError('El mensaje no puede superar los ' + maxLength + ' caracteres.');
            return;
        }

        clearError();
        setSending(true);
        state.followLatest = true;

        const pending = addMessage('USER', text);
        el.input.value = '';
        autosize();
        updateComposer();
        scrollToBottom();
        const typing = showTyping();

        try {
            const reply = await request('/messages', {
                method: 'POST',
                body: JSON.stringify({ message: text, conversationId: state.conversationId })
            });
            typing.remove();
            state.conversationId = reply.conversationId;
            const node = addMessage('ASSISTANT', reply.reply.content, reply.reply.sources);
            if (state.followLatest) {
                scrollToTopOf(node);
            }
            updateHeader();
        } catch (error) {
            typing.remove();
            pending.remove();
            refreshEmpty();
            el.input.value = text;
            autosize();
            if (error.status === 404) {
                resetConversation();
                showError('La conversación ya no existe. Tu próximo mensaje iniciará una nueva.');
            } else {
                showError(error.message);
            }
        } finally {
            setSending(false);
            el.input.focus();
        }
    }

    function isOpen() {
        return !el.panel.hidden;
    }

    function openPanel() {
        el.panel.hidden = false;
        root.classList.add('is-open');
        document.body.classList.add('fl-chat-lock');
        el.toggle.setAttribute('aria-expanded', 'true');
        el.toggle.setAttribute('aria-label', 'Cerrar el asistente');
        document.addEventListener('keydown', onEscape);
        loadLatestConversation();
        el.input.focus();
    }

    function closePanel() {
        el.panel.hidden = true;
        root.classList.remove('is-open');
        document.body.classList.remove('fl-chat-lock');
        el.toggle.setAttribute('aria-expanded', 'false');
        el.toggle.setAttribute('aria-label', 'Abrir el asistente');
        document.removeEventListener('keydown', onEscape);
        el.toggle.focus();
    }

    function onEscape(event) {
        if (event.key === 'Escape' && isOpen()) {
            closePanel();
        }
    }

    function onInputKeydown(event) {
        if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
            event.preventDefault();
            send();
        }
    }

    function onInput() {
        autosize();
        updateComposer();
        if (!el.error.hidden) {
            clearError();
        }
    }

    el.toggle.addEventListener('click', function () {
        if (isOpen()) {
            closePanel();
        } else {
            openPanel();
        }
    });
    el.close.addEventListener('click', closePanel);
    el.send.addEventListener('click', send);
    el.newButton.addEventListener('click', newConversation);
    el.deleteButton.addEventListener('click', deleteConversation);
    el.input.addEventListener('keydown', onInputKeydown);
    el.input.addEventListener('input', onInput);
    el.messages.addEventListener('scroll', function () {
        state.followLatest = isNearBottom();
    });

    root.querySelectorAll('[data-question]').forEach(function (button) {
        button.addEventListener('click', function () {
            el.input.value = button.dataset.question;
            autosize();
            updateComposer();
            el.input.focus();
        });
    });

    updateHeader();
    updateComposer();
}());