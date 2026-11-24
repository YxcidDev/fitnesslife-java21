(function () {
    'use strict';

    function readCookie(name) {
        var match = document.cookie.split('; ').find(function (c) { return c.indexOf(name + '=') === 0; });
        return match ? decodeURIComponent(match.substring(name.length + 1)) : null;
    }

    function csrfHeaders() {
        var headers = { 'Content-Type': 'application/json' };
        var token = readCookie('XSRF-TOKEN');
        if (token) { headers['X-XSRF-TOKEN'] = token; }
        return headers;
    }

    function showToast(message, isError) {
        FLToast.show({ type: isError ? 'error' : 'success', message: message });
    }

    function errorMessage(res) {
        return res.json().catch(function () { return {}; }).then(function (err) {
            var msg = err.message || ('Error HTTP ' + res.status);
            if (err.fieldErrors && err.fieldErrors.length) {
                msg += ': ' + err.fieldErrors.map(function (v) { return v.message; }).join('; ');
            }
            return msg;
        });
    }

    function bindStatusSelect(select) {
        select.addEventListener('change', function () {
            var previous = select.getAttribute('data-current');
            var next = select.value;
            select.disabled = true;

            fetch('/api/retention/assignments/' + encodeURIComponent(select.getAttribute('data-id')) + '/status', {
                method: 'PATCH',
                headers: csrfHeaders(),
                body: JSON.stringify({ status: next })
            })
                .then(function (res) {
                    if (res.ok) { return res.json(); }
                    return errorMessage(res).then(function (msg) { throw new Error(msg); });
                })
                .then(function () {
                    select.setAttribute('data-current', next);
                    showToast('Estado actualizado');
                })
                .catch(function (e) {
                    select.value = previous;
                    showToast(e.message, true);
                })
                .finally(function () {
                    select.disabled = false;
                });
        });
    }

    document.querySelectorAll('.js-status-select').forEach(bindStatusSelect);

    var panel = document.getElementById('dispatchPanel');
    if (!panel) { return; }

    var campaignId = panel.getAttribute('data-campaign-id');
    var apiBase = '/api/retention/campaigns/' + encodeURIComponent(campaignId) + '/dispatch';
    var POLL_MS = 3000;
    var pollTimer = null;
    var wasSending = false;

    var STATUS_LABELS = {
        NOT_LAUNCHED: 'Sin lanzar',
        SENDING: 'Enviando correos...',
        COMPLETED: 'Campaña enviada',
        COMPLETED_WITH_ERRORS: 'Enviada con errores',
        CANCELLED: 'Envío detenido'
    };

    var el = {
        label: document.getElementById('dispatchStatusLabel'),
        meta: document.getElementById('dispatchMeta'),
        launchBtn: document.getElementById('launchBtn'),
        cancelBtn: document.getElementById('cancelDispatchBtn'),
        retryBtn: document.getElementById('retryBtn'),
        progress: document.getElementById('dispatchProgress'),
        bar: document.getElementById('dispatchBar'),
        counts: document.getElementById('dispatchCounts'),
        modal: document.getElementById('launchModal'),
        loading: document.getElementById('launchLoading'),
        error: document.getElementById('launchError'),
        content: document.getElementById('launchContent'),
        ack: document.getElementById('lmAck'),
        confirmBtn: document.getElementById('lmConfirmBtn'),
        confirmLabel: document.getElementById('lmConfirmLabel')
    };

    function money(n) { return '$' + Math.round(n).toLocaleString('en-US'); }

    function fetchJson(url, options) {
        return fetch(url, options).then(function (res) {
            if (res.ok) { return res.json(); }
            return errorMessage(res).then(function (msg) { throw new Error(msg); });
        });
    }

    function renderStatus(status, progress) {
        el.label.textContent = STATUS_LABELS[status] || status;
        panel.setAttribute('data-status', status);

        el.launchBtn.hidden = status !== 'NOT_LAUNCHED';
        el.cancelBtn.hidden = status !== 'SENDING';
        el.retryBtn.hidden = !(status === 'COMPLETED_WITH_ERRORS' && progress && progress.failed > 0);

        if (!progress || status === 'NOT_LAUNCHED') {
            el.progress.hidden = true;
            el.meta.textContent = '';
            return;
        }

        el.progress.hidden = false;
        el.bar.style.width = progress.percent + '%';
        el.bar.textContent = progress.percent + '%';

        var parts = [
            progress.sent + ' enviados',
            progress.queued + progress.sending + ' en cola',
            progress.failed + ' fallidos',
            progress.skipped + ' omitidos'
        ];
        if (progress.cancelled > 0) { parts.push(progress.cancelled + ' cancelados'); }
        el.counts.textContent = parts.join(' · ');

        if (progress.launchedBy) {
            el.meta.textContent = 'Lanzada por ' + progress.launchedBy;
        }
    }

    function refreshProgress() {
        return fetchJson(apiBase + '/progress').then(function (progress) {
            renderStatus(progress.dispatchStatus, progress);

            if (progress.dispatchStatus === 'SENDING') {
                wasSending = true;
                schedulePoll();
            } else {
                stopPoll();
                if (wasSending) {
                    wasSending = false;
                    FLToast.later(progress.failed > 0 ? 'warning' : 'success',
                        STATUS_LABELS[progress.dispatchStatus] + '.');
                    window.location.reload();
                }
            }
            return progress;
        });
    }

    function schedulePoll() {
        if (pollTimer === null) {
            pollTimer = setInterval(function () {
                refreshProgress().catch(function () { /* se reintenta en el próximo ciclo */ });
            }, POLL_MS);
        }
    }

    function stopPoll() {
        if (pollTimer !== null) { clearInterval(pollTimer); pollTimer = null; }
    }

    var modalInstance = el.modal ? bootstrap.Modal.getOrCreateInstance(el.modal) : null;
    var currentPreview = null;

    function setText(id, value) { document.getElementById(id).textContent = value; }

    function showModalError(message) {
        el.loading.hidden = true;
        el.content.hidden = true;
        el.error.hidden = false;
        el.error.textContent = message;
        el.confirmBtn.disabled = true;
    }

    function loadPreview() {
        el.loading.hidden = false;
        el.content.hidden = true;
        el.error.hidden = true;
        el.ack.checked = false;
        el.confirmBtn.disabled = true;

        return fetchJson(apiBase + '/preview').then(function (p) {
            currentPreview = p;

            if (!p.canLaunch) {
                showModalError(p.totalRecipients === 0
                    ? 'No hay destinatarios elegibles: todas las asignaciones están descartadas o sin correo válido.'
                    : 'Esta campaña ya fue lanzada.');
                return;
            }

            setText('lmTotal', p.totalRecipients);
            setText('lmPromotions', p.promotions);
            setText('lmDiscount', p.discountPercent);
            setText('lmImpact', money(p.estimatedDiscountImpact));
            setText('lmClassInvites', p.classInvites);
            setText('lmReminders', p.reminders);
            document.getElementById('lmDryRun').hidden = !p.dryRun;

            var skipped = document.getElementById('lmSkipped');
            skipped.hidden = p.skipped === 0;
            skipped.textContent = p.skipped + ' asignaciones no recibirán correo (descartadas o sin correo válido).';

            el.confirmLabel.textContent = 'Sí, enviar ' + p.totalRecipients + ' correos';
            el.loading.hidden = true;
            el.content.hidden = false;
        }).catch(function (e) { showModalError(e.message); });
    }

    function openLaunchModal() {
        if (!modalInstance) { return; }
        modalInstance.show();
        loadPreview();
    }

    if (el.ack) {
        el.ack.addEventListener('change', function () {
            el.confirmBtn.disabled = !(el.ack.checked && currentPreview && currentPreview.canLaunch);
        });
    }

    el.confirmBtn.addEventListener('click', function () {
        if (!currentPreview || !el.ack.checked) { return; }
        el.confirmBtn.disabled = true;
        el.ack.disabled = true;

        fetchJson(apiBase + '/launch', {
            method: 'POST',
            headers: csrfHeaders(),
            body: JSON.stringify({ expectedRecipients: currentPreview.totalRecipients, acknowledged: true })
        }).then(function (progress) {
            modalInstance.hide();
            showToast((progress.total - progress.notSent - progress.skipped) + ' correos en cola de envío.');
            wasSending = true;
            return refreshProgress();
        }).catch(function (e) {
            loadPreview().then(function () {
                el.error.hidden = false;
                el.error.textContent = e.message;
            });
        }).finally(function () {
            el.ack.disabled = false;
        });
    });

    el.launchBtn.addEventListener('click', openLaunchModal);

    el.cancelBtn.addEventListener('click', function () {
        if (!window.confirm('¿Detener el envío? Los correos que aún no salieron se cancelarán; los ya enviados no se pueden recuperar.')) {
            return;
        }
        el.cancelBtn.disabled = true;
        fetchJson(apiBase + '/cancel', { method: 'POST', headers: csrfHeaders() })
            .then(function (progress) {
                stopPoll();
                wasSending = false;
                renderStatus(progress.dispatchStatus, progress);
                FLToast.later('info', 'Envío detenido.');
                window.location.reload();
            })
            .catch(function (e) { showToast(e.message, true); })
            .finally(function () { el.cancelBtn.disabled = false; });
    });

    el.retryBtn.addEventListener('click', function () {
        el.retryBtn.disabled = true;
        fetchJson(apiBase + '/retry', { method: 'POST', headers: csrfHeaders() })
            .then(function (progress) {
                wasSending = true;
                renderStatus(progress.dispatchStatus, progress);
                schedulePoll();
                showToast('Reintentando los correos fallidos.');
            })
            .catch(function (e) { showToast(e.message, true); })
            .finally(function () { el.retryBtn.disabled = false; });
    });

    renderStatus(panel.getAttribute('data-status'), null);
    refreshProgress().catch(function (e) { showToast(e.message, true); });

    if (panel.getAttribute('data-auto-launch') === 'true' && panel.getAttribute('data-status') === 'NOT_LAUNCHED') {
        if (window.history && window.history.replaceState) {
            var url = new URL(window.location.href);
            url.searchParams.delete('launch');
            window.history.replaceState({}, '', url.toString());
        }
        openLaunchModal();
    }
})();