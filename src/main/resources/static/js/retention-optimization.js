(function () {
    'use strict';

    var RISK_LABELS = { LOW: 'Bajo', MEDIUM: 'Medio', HIGH: 'Alto' };
    var RISK_CLASS = { LOW: 'churn-risk-low', MEDIUM: 'churn-risk-medium', HIGH: 'churn-risk-high' };

    var cop = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
    var num = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 });

    var defaults = null;
    var lastAssignments = [];
    var currentRunId = null;

    var $ = function (id) { return document.getElementById(id); };

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

    function el(tag, text, cls) {
        var e = document.createElement(tag);
        if (text !== undefined && text !== null) { e.textContent = text; }
        if (cls) { e.className = cls; }
        return e;
    }

    function showError(msg) { FLToast.error(msg); }

    function handleResponse(res) {
        if (res.ok) { return res.json(); }
        return res.json().catch(function () { return {}; }).then(function (err) {
            var msg = err.message || ('Error HTTP ' + res.status);
            if (err.fieldErrors && err.fieldErrors.length) {
                msg += ': ' + err.fieldErrors.map(function (v) { return v.message; }).join('; ');
            }
            throw new Error(msg);
        });
    }

    function fillForm(p) {
        $('budget').value = p.budget;
        $('classSlots').value = p.classSlots;
        $('maxPromotions').value = p.maxPromotions;
        $('maxReminders').value = p.maxReminders;
        $('minHighRiskCoverage').value = Math.round(p.minHighRiskCoverage * 100);
        $('minConfidence').value = p.minConfidence;
        $('reminderCost').value = p.reminderCost;
        $('classCost').value = p.classCost;
        $('discountRate').value = Math.round(p.discountRate * 100);
        for (var i = 0; i < 3; i++) {
            $('months' + i).value = p.expectedMonths[i];
            for (var j = 0; j < 3; j++) {
                $('p' + i + j).value = Math.round(p.retentionLift[i][j] * 10000) / 100;
            }
        }
        $('integerMode').checked = p.integer !== false;
    }

    function readForm() {
        var f = function (id) { return parseFloat($(id).value); };
        var lift = [];
        var months = [];
        for (var i = 0; i < 3; i++) {
            months.push(f('months' + i));
            var row = [];
            for (var j = 0; j < 3; j++) { row.push(f('p' + i + j) / 100); }
            lift.push(row);
        }
        return {
            minConfidence: f('minConfidence'),
            expectedMonths: months,
            retentionLift: lift,
            reminderCost: f('reminderCost'),
            discountRate: f('discountRate') / 100,
            classCost: f('classCost'),
            budget: f('budget'),
            classSlots: parseInt($('classSlots').value, 10),
            maxPromotions: parseInt($('maxPromotions').value, 10),
            maxReminders: parseInt($('maxReminders').value, 10),
            minHighRiskCoverage: f('minHighRiskCoverage') / 100,
            integer: $('integerMode').checked
        };
    }

    function renderSegments(segments, averagePlanPrice) {
        segments.forEach(function (s, i) {
            $('segCandidates' + i).textContent = num.format(s.candidates);
            $('segValue' + i).textContent = cop.format(s.expectedValue);
        });
        if (averagePlanPrice !== undefined) {
            $('avgPrice').textContent = cop.format(averagePlanPrice);
        }
    }

    function loadDefaults() {
        return fetch('/api/retention/defaults', { headers: { 'Accept': 'application/json' } })
            .then(handleResponse)
            .then(function (data) {
                defaults = data;
                fillForm(data.parameters);
                renderSegments(data.segments, data.averagePlanPrice);
            })
            .catch(function (e) { showError(e.message); });
    }

    function renderResult(r) {
        $('results').classList.remove('d-none');

        var status = $('resStatus');
        status.textContent = r.status === 'Optimal' ? 'Óptimo' : r.status;
        status.className = 'churn-stat-value retention-stat-value--sm ' +
            (r.status === 'Optimal' ? 'churn-prediction-no' : 'churn-prediction-yes');
        status.style.fontSize = '1.35rem';

        $('resObjective').textContent = cop.format(r.objectiveValue);
        $('resCost').textContent = cop.format(r.totalCost) + ' / ' + cop.format(r.budget);
        $('resUsers').textContent = num.format(r.assignments.length);

        var msgBox = $('resMessages');
        msgBox.replaceChildren();
        if (r.messages && r.messages.length) {
            msgBox.className = 'retention-alert ' +
                (r.status === 'Optimal' ? 'retention-alert--info' : 'retention-alert--warn');
            r.messages.forEach(function (m) { msgBox.appendChild(el('div', m)); });
        } else {
            msgBox.className = 'd-none';
        }

        prepareSavePanel(r.runId);

        renderSegments(r.segments);
        renderAllocation(r);
        renderConstraints(r.constraints);

        lastAssignments = r.assignments;
        renderAssignments();

        $('results').scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    function prepareSavePanel(runId) {
        currentRunId = runId || null;
        var panel = $('savePanel');
        var result = $('saveResult');
        result.className = 'd-none';
        result.replaceChildren();
        $('saveBtn').disabled = false;
        panel.classList.toggle('d-none', !currentRunId);
    }

    function saveCampaign() {
        if (!currentRunId) { return; }

        var btn = $('saveBtn');
        var original = btn.innerHTML;
        btn.disabled = true;
        btn.textContent = 'Guardando…';

        fetch('/api/retention/campaigns/' + encodeURIComponent(currentRunId) + '/save', {
            method: 'POST',
            headers: csrfHeaders(),
            body: JSON.stringify({
                name: $('campaignName').value.trim(),
                notes: $('campaignNotes').value.trim()
            })
        })
            .then(handleResponse)
            .then(function (saved) {
                var savedId = saved.id;
                currentRunId = null;

                var box = $('saveResult');
                box.replaceChildren();
                box.className = 'retention-alert retention-alert--success mt-3';
                box.appendChild(document.createTextNode(
                    '«' + saved.name + '» guardada con ' + num.format(saved.assignmentCount) + ' asignaciones. '));
                var link = document.createElement('a');
                link.href = '/admin/retention/history?campaignId=' + encodeURIComponent(savedId);
                link.textContent = 'Ver en el historial';
                link.style.color = 'var(--primary-color)';
                box.appendChild(link);

                box.appendChild(document.createTextNode(' · '));
                var launch = document.createElement('a');
                launch.href = '/admin/retention/history?campaignId=' + encodeURIComponent(savedId) + '&launch=true';
                launch.textContent = 'Revisar y lanzar campaña';
                launch.style.color = 'var(--primary-color)';
                launch.style.fontWeight = '700';
                box.appendChild(launch);

                btn.innerHTML = original;
                btn.disabled = true;
            })
            .catch(function (e) {
                showError(e.message);
                btn.innerHTML = original;
                btn.disabled = false;
            });
    }

    function renderAllocation(r) {
        var table = $('allocTable');
        table.replaceChildren();

        var thead = el('thead');
        var hr = el('tr');
        hr.appendChild(el('th', 'Segmento'));
        r.actionLabels.forEach(function (a, j) {
            hr.appendChild(el('th', a + ' · ' + cop.format(r.actionCosts[j]) + ' c/u', 'text-end'));
        });
        hr.appendChild(el('th', 'Atendidos / candidatos', 'text-end'));
        thead.appendChild(hr);
        table.appendChild(thead);

        var tbody = el('tbody');
        var colTotals = [0, 0, 0];
        var order = [2, 1, 0];
        order.forEach(function (i) {
            var row = r.allocation[i];
            var tr = el('tr');
            tr.appendChild(el('td', r.segments[i].label, 'fw-bold'));
            var sum = 0;
            row.forEach(function (v, j) {
                tr.appendChild(el('td', num.format(v), 'text-end ' + (v > 0 ? 'retention-positive' : 'retention-zero')));
                sum += v;
                colTotals[j] += v;
            });
            tr.appendChild(el('td', num.format(sum) + ' / ' + num.format(r.segments[i].candidates), 'text-end'));
            tbody.appendChild(tr);
        });
        table.appendChild(tbody);

        var tfoot = el('tfoot');
        var tf = el('tr');
        tf.appendChild(el('td', 'Total'));
        var grand = 0;
        colTotals.forEach(function (t) { tf.appendChild(el('td', num.format(t), 'text-end')); grand += t; });
        tf.appendChild(el('td', num.format(grand), 'text-end'));
        tfoot.appendChild(tf);
        table.appendChild(tfoot);
    }

    function renderConstraints(list) {
        var body = $('constraintsBody');
        body.replaceChildren();
        (list || []).forEach(function (c) {
            var money = c.code === 'R4';
            var fmt = function (v) { return money ? cop.format(v) : num.format(v); };
            var tr = el('tr');
            tr.appendChild(el('td', c.code + ' · ' + c.name));
            tr.appendChild(el('td', fmt(c.used), 'text-end'));
            tr.appendChild(el('td', (c.sense === '>=' ? '≥ ' : '≤ ') + fmt(c.limit), 'text-end'));
            tr.appendChild(el('td', c.binding ? 'Activa' : 'Holgura ' + fmt(c.slack),
                c.binding ? 'retention-binding' : 'retention-slack'));
            tr.appendChild(el('td', c.shadowPrice ? cop.format(c.shadowPrice) : '—',
                'text-end ' + (c.shadowPrice ? 'retention-positive' : 'retention-zero')));
            body.appendChild(tr);
        });
    }

    function renderAssignments() {
        var filter = $('actionFilter').value;
        var body = $('assignBody');
        body.replaceChildren();

        var rows = lastAssignments.filter(function (a) { return !filter || a.action === filter; });

        if (!rows.length) {
            var empty = el('tr');
            var td = el('td', 'No hay usuarios para mostrar con este filtro', 'text-center py-4');
            td.colSpan = 6;
            td.style.color = 'var(--text-gray)';
            empty.appendChild(td);
            body.appendChild(empty);
            return;
        }

        rows.forEach(function (a) {
            var tr = el('tr');

            var userTd = el('td');
            userTd.appendChild(el('p', a.name, 'mb-0 fw-bold user-name'));
            userTd.appendChild(el('small', a.email));
            tr.appendChild(userTd);

            tr.appendChild(el('td', a.phone || '—'));
            tr.appendChild(el('td', a.plan || '—'));

            var riskTd = el('td');
            riskTd.appendChild(el('span', RISK_LABELS[a.riskLevel] || a.riskLevel,
                'churn-risk-badge ' + (RISK_CLASS[a.riskLevel] || '')));
            tr.appendChild(riskTd);

            tr.appendChild(el('td', num.format(a.confidence) + '%'));
            tr.appendChild(el('td', a.actionLabel));
            body.appendChild(tr);
        });
    }

    function exportCsv() {
        var esc = function (v) { return '"' + String(v == null ? '' : v).replace(/"/g, '""') + '"'; };
        var rows = [['Usuario', 'Correo', 'Teléfono', 'Plan', 'Riesgo', 'Confianza', 'Acción']];
        lastAssignments.forEach(function (a) {
            rows.push([a.name, a.email, a.phone, a.plan, RISK_LABELS[a.riskLevel] || a.riskLevel,
                a.confidence, a.actionLabel]);
        });
        var csv = '﻿' + rows.map(function (r) { return r.map(esc).join(','); }).join('\n');
        var url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
        var link = document.createElement('a');
        link.href = url;
        link.download = 'acciones_retencion.csv';
        link.click();
        URL.revokeObjectURL(url);
    }

    $('optForm').addEventListener('submit', function (ev) {
        ev.preventDefault();
        if (!ev.target.checkValidity()) {
            ev.target.reportValidity();
            return;
        }
        var btn = $('runBtn');
        var original = btn.innerHTML;
        btn.disabled = true;
        btn.textContent = 'Calculando…';

        fetch('/api/retention/optimize', {
            method: 'POST',
            headers: csrfHeaders(),
            body: JSON.stringify(readForm())
        })
            .then(handleResponse)
            .then(renderResult)
            .catch(function (e) { showError(e.message); })
            .finally(function () {
                btn.disabled = false;
                btn.innerHTML = original;
            });
    });

    $('resetBtn').addEventListener('click', function () {
        if (defaults) { fillForm(defaults.parameters); }
    });
    $('actionFilter').addEventListener('change', renderAssignments);
    $('csvBtn').addEventListener('click', exportCsv);
    $('saveBtn').addEventListener('click', saveCampaign);

    loadDefaults();
})();