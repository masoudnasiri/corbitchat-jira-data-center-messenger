/* MCP Control Plane admin console (vanilla JS, no frameworks). */
(function () {
    'use strict';
    var BASE = '/rest/jim/1.0/mcp/admin';
    var allTools = [];

    function request(method, path, body, cb) {
        var xhr = new XMLHttpRequest();
        xhr.open(method, path, true);
        xhr.setRequestHeader('Accept', 'application/json');
        xhr.setRequestHeader('X-Atlassian-Token', 'no-check');
        if (body !== undefined) xhr.setRequestHeader('Content-Type', 'application/json');
        xhr.onreadystatechange = function () {
            if (xhr.readyState !== 4) return;
            var data = null;
            try { data = JSON.parse(xhr.responseText); } catch (e) {}
            cb(xhr.status, data);
        };
        xhr.send(body !== undefined ? JSON.stringify(body) : null);
    }

    function esc(s) {
        return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
        });
    }

    function renderStatus(s) {
        var el = document.getElementById('jim-mcp-status');
        if (!s) { el.textContent = 'Status unavailable'; return; }
        var c = s.toolCounts || {};
        var gwLabel = s.gatewayConfigured
            ? (s.gatewayReachable ? ('heartbeat ' + (s.gatewayHeartbeatAgeSec != null ? s.gatewayHeartbeatAgeSec + 's ago' : 'ok'))
                                  : (s.gatewayStatus === 'never' ? 'no heartbeat yet' : 'STALE'))
            : 'not set';
        el.innerHTML =
            '<div class="jim-mcp-badges">' +
            badge('MCP', s.mcpEnabled ? 'ON' : 'OFF', s.mcpEnabled ? 'ok' : 'off') +
            badge('Gateway', gwLabel, s.gatewayReachable ? 'ok' : 'off') +
            badge('Backend (MCP)', s.backendReachable === true ? 'reachable' : (s.backendReachable === false ? 'down' : 'unknown'),
                s.backendReachable === true ? 'ok' : 'off') +
            badge('Jira', s.jiraConnectivity ? 'connected' : '?', s.jiraConnectivity ? 'ok' : 'off') +
            badge('Secrets', s.configured ? 'configured' : 'missing', s.configured ? 'ok' : 'off') +
            badge('Upstream', s.upstreamVersion, 'info') +
            badge('Policy rev', s.revision, 'info') +
            badge('Served rev', s.servedRevision, 'info') +
            badge('GW policy rev', s.gatewayPolicyRevision, (s.gatewayPolicyRevision === s.revision ? 'ok' : 'warn')) +
            '</div>' +
            '<div class="jim-mcp-counts">Tools: ' + (c.total || 0) +
            ' &middot; enabled ' + (c.enabled || 0) +
            ' &middot; available ' + (c.available || 0) +
            ' &middot; read ' + (c.read || 0) +
            ' &middot; write ' + (c.write || 0) +
            ' &middot; destructive ' + (c.destructive || 0) +
            ' &middot; N/A ' + (c.notApplicable || 0) + '</div>';
        document.getElementById('jim-mcp-enabled').checked = !!s.mcpEnabled;
        document.getElementById('jim-mcp-class-read').checked = !!(s.classes && s.classes.read);
        document.getElementById('jim-mcp-class-write').checked = !!(s.classes && s.classes.write);
        document.getElementById('jim-mcp-class-destructive').checked = !!(s.classes && s.classes.destructive);
        document.getElementById('jim-mcp-projects').value = s.allowedProjects || '';
        document.getElementById('jim-mcp-gwurl').value = s.gatewayUrl || '';

        var diag = document.getElementById('jim-mcp-diag');
        diag.innerHTML = 'Contract ' + esc(s.contractHash) +
            ' &middot; gateway ' + esc(s.gatewayVersion || '—') +
            ' &middot; heartbeat ' + (s.gatewayHeartbeatAgeSec != null ? esc(s.gatewayHeartbeatAgeSec) + 's ago' : 'none') +
            ' &middot; last policy pull rev ' + esc(s.servedRevision) + ' at ' + esc(s.servedAt || '—') +
            ' &middot; gateway sees rev ' + esc(s.gatewayPolicyRevision) +
            ' &middot; url ' + esc(s.gatewayUrl || 'not configured');
    }

    function badge(label, value, kind) {
        return '<span class="jim-mcp-badge jim-mcp-' + kind + '">' +
            esc(label) + ': <b>' + esc(value) + '</b></span> ';
    }

    function renderTools() {
        var q = (document.getElementById('jim-mcp-search').value || '').toLowerCase();
        var tbody = document.querySelector('#jim-mcp-tools tbody');
        var rows = allTools.filter(function (t) {
            return !q || t.name.toLowerCase().indexOf(q) >= 0 ||
                (t.toolset || '').toLowerCase().indexOf(q) >= 0 ||
                (t.classification || '').toLowerCase().indexOf(q) >= 0;
        });
        if (!rows.length) { tbody.innerHTML = '<tr><td colspan="7">No tools</td></tr>'; return; }
        tbody.innerHTML = rows.map(function (t) {
            return '<tr class="jim-mcp-row jim-mcp-cls-' + esc(t.classification) + '">' +
                '<td><code>' + esc(t.name) + '</code><br><span class="jim-mcp-desc">' + esc(t.description) + '</span></td>' +
                '<td>' + esc(t.toolset) + '</td>' +
                '<td><span class="jim-mcp-tag jim-mcp-tag-' + esc(t.classification) + '">' + esc(t.classification) + '</span></td>' +
                '<td>' + verifTag(t.verification) + '</td>' +
                '<td>' + (t.available ? 'yes' : '<span class="jim-mcp-off">no</span>') + '</td>' +
                '<td><input type="checkbox" class="jim-mcp-enable" data-tool="' + esc(t.name) + '"' + (t.enabled ? ' checked' : '') + '/></td>' +
                '<td><input type="checkbox" class="jim-mcp-confirm" data-tool="' + esc(t.name) + '"' + (t.confirm ? ' checked' : '') + '/></td>' +
                '</tr>';
        }).join('');
        bindToolToggles();
    }

    function verifTag(v) {
        var kind = v === 'PASS' ? 'ok' : (v === 'NOT_APPLICABLE' ? 'off' : 'warn');
        return '<span class="jim-mcp-tag jim-mcp-' + kind + '">' + esc(v) + '</span>';
    }

    function bindToolToggles() {
        Array.prototype.forEach.call(document.querySelectorAll('.jim-mcp-enable'), function (cb) {
            cb.addEventListener('change', function () {
                request('PUT', BASE + '/tools/' + encodeURIComponent(cb.getAttribute('data-tool')),
                    { enabled: cb.checked }, function (st) { if (st >= 300) { cb.checked = !cb.checked; alert('Update failed'); } else loadStatus(); });
            });
        });
        Array.prototype.forEach.call(document.querySelectorAll('.jim-mcp-confirm'), function (cb) {
            cb.addEventListener('change', function () {
                request('PUT', BASE + '/tools/' + encodeURIComponent(cb.getAttribute('data-tool')),
                    { confirm: cb.checked }, function (st) { if (st >= 300) { cb.checked = !cb.checked; alert('Update failed'); } else loadStatus(); });
            });
        });
    }

    function loadStatus() {
        request('GET', BASE + '/status', undefined, function (st, data) { if (st === 200) renderStatus(data); });
    }
    function loadTools() {
        request('GET', BASE + '/tools', undefined, function (st, data) {
            if (st === 200 && data) { allTools = data.tools || []; renderTools(); }
        });
    }

    function init() {
        loadStatus();
        loadTools();
        document.getElementById('jim-mcp-search').addEventListener('input', renderTools);
        document.getElementById('jim-mcp-save-global').addEventListener('click', function () {
            request('PUT', BASE + '/global', {
                mcpEnabled: document.getElementById('jim-mcp-enabled').checked,
                classes: {
                    read: document.getElementById('jim-mcp-class-read').checked,
                    write: document.getElementById('jim-mcp-class-write').checked,
                    destructive: document.getElementById('jim-mcp-class-destructive').checked
                },
                allowedProjects: document.getElementById('jim-mcp-projects').value,
                gatewayUrl: document.getElementById('jim-mcp-gwurl').value
            }, function (st) { loadStatus(); loadTools(); });
        });
        Array.prototype.forEach.call(document.querySelectorAll('[data-bulk]'), function (btn) {
            btn.addEventListener('click', function () {
                request('POST', BASE + '/bulk', { op: btn.getAttribute('data-bulk') },
                    function () { loadStatus(); loadTools(); });
            });
        });
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
    else init();
})();
