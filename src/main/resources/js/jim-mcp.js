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
        var sv = s.servedCatalog || {};
        var gwOk = !!s.gatewayReachable;                 // fresh heartbeat received (decoupled)
        var gwLabel = gwOk ? ((s.gatewayRelease || s.gatewayVersion || 'up') + ' · heartbeat ' +
                              (s.gatewayHeartbeatAgeSec != null ? s.gatewayHeartbeatAgeSec + 's ago' : 'ok'))
                           : (s.gatewayStatus === 'stale' ? 'STALE heartbeat' : 'no heartbeat yet');
        el.innerHTML =
            '<div class="jim-mcp-badges">' +
            badge('MCP', s.mcpEnabled ? 'ON' : 'OFF', s.mcpEnabled ? 'ok' : 'off') +
            badge('Gateway', gwLabel, gwOk ? 'ok' : 'off') +
            badge('Backend (MCP)', s.backendReachable === true ? ('reachable ' + (s.backendVersion || '')) : (s.backendReachable === false ? 'down' : 'unknown'),
                s.backendReachable === true ? 'ok' : 'off') +
            badge('Jira', s.jiraConnectivity ? 'connected' : '?', s.jiraConnectivity ? 'ok' : 'off') +
            badge('Secrets', s.configured ? 'configured' : 'missing', s.configured ? 'ok' : 'off') +
            badge('Shared fallback', s.sharedFallbackEnabled ? 'ENABLED' : 'disabled', s.sharedFallbackEnabled ? 'warn' : 'ok') +
            badge('Upstream', s.upstreamVersion, 'info') +
            badge('Policy rev', s.revision, 'info') +
            badge('Served rev', s.servedRevision, 'info') +
            '</div>' +
            // LIVE served MCP catalog (from the Gateway) — the real public contract
            '<div class="jim-mcp-counts"><b>Served MCP catalog</b> (live from Gateway): ' +
            (sv.available
                ? ((sv.toolCount || 0) + ' tools &middot; jira ' + (sv.jiraToolCount || 0) +
                   ' &middot; corbit ' + (sv.corbitToolCount || 0) +
                   ' &middot; read ' + (sv.read || 0) + ' &middot; write ' + (sv.write || 0) +
                   ' &middot; destructive ' + (sv.destructive || 0) +
                   ' &middot; schema ' + (sv.schemaVersion || 3) +
                   ' &middot; served hash ' + esc(sv.contractHash || ''))
                : ('unavailable (' + esc(sv.reason || 'no heartbeat') + ')')) + '</div>' +
            '<div class="jim-mcp-counts">Expected served hash ' + esc(s.expectedServedContractHash || s.contractHash) +
            (sv.available && sv.contractHash
                ? (String(sv.contractHash) === String(s.expectedServedContractHash || '')
                    ? ' <b>(matches plugin)</b>'
                    : (String(sv.contractHash).indexOf(String(s.contractHash || '')) === 0
                        ? ' <b>(prefix matches)</b>'
                        : ' <b style="color:#de350b">(DIFFERS from plugin expectation)</b>'))
                : '') +
            ' &middot; Jira upstream seed ' + esc(s.jiraUpstreamSeed || '') + '</div>' +
            // internal policy registry — NOT the served catalog
            '<div class="jim-mcp-counts" style="opacity:.75">Policy registry (internal, not served): ' +
            (c.total || 0) + ' entries &middot; available ' + (c.available || 0) +
            ' &middot; read ' + (c.read || 0) + ' &middot; write ' + (c.write || 0) +
            ' &middot; destructive ' + (c.destructive || 0) + ' &middot; N/A ' + (c.notApplicable || 0) + '</div>';
        document.getElementById('jim-mcp-enabled').checked = !!s.mcpEnabled;
        document.getElementById('jim-mcp-class-read').checked = !!(s.classes && s.classes.read);
        document.getElementById('jim-mcp-class-write').checked = !!(s.classes && s.classes.write);
        document.getElementById('jim-mcp-class-destructive').checked = !!(s.classes && s.classes.destructive);
        document.getElementById('jim-mcp-projects').value = s.allowedProjects || '';
        document.getElementById('jim-mcp-gwurl').value = s.gatewayUrl || '';

        var diag = document.getElementById('jim-mcp-diag');
        diag.innerHTML = 'Expected served prefix ' + esc(s.contractHash) +
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

    // ---- MCP Access management (Phase 2b) ----
    var access = null;

    function renderAccessTables() {
        var ut = document.querySelector('#jim-acc-users tbody');
        var up = access.userPolicy || {}, ub = access.userBlock || [];
        var rows = Object.keys(up).map(function (k) {
            return '<tr><td>' + esc(k) + '</td><td>' + esc(up[k]) + '</td><td><button class="aui-button aui-button-link jim-acc-user-del" data-k="' + esc(k) + '">remove</button></td></tr>';
        });
        ub.forEach(function (k) { if (!(k in up)) rows.push('<tr><td>' + esc(k) + '</td><td>blocked</td><td><button class="aui-button aui-button-link jim-acc-user-del" data-k="' + esc(k) + '">remove</button></td></tr>'); });
        ut.innerHTML = rows.length ? rows.join('') : '<tr><td colspan="3">No user rules (default preset applies)</td></tr>';
        var gt = document.querySelector('#jim-acc-groups tbody');
        var gp = access.groupPolicy || {};
        var grows = Object.keys(gp).map(function (k) {
            return '<tr><td>' + esc(k) + '</td><td>' + esc(gp[k]) + '</td><td><button class="aui-button aui-button-link jim-acc-group-del" data-k="' + esc(k) + '">remove</button></td></tr>';
        });
        gt.innerHTML = grows.length ? grows.join('') : '<tr><td colspan="3">No group rules</td></tr>';
        Array.prototype.forEach.call(document.querySelectorAll('.jim-acc-user-del'), function (b) {
            b.addEventListener('click', function () { var k = b.getAttribute('data-k'); delete access.userPolicy[k]; access.userBlock = (access.userBlock || []).filter(function (x) { return x !== k; }); renderAccessTables(); });
        });
        Array.prototype.forEach.call(document.querySelectorAll('.jim-acc-group-del'), function (b) {
            b.addEventListener('click', function () { delete access.groupPolicy[b.getAttribute('data-k')]; renderAccessTables(); });
        });
    }

    function renderAccess() {
        if (!access) return;
        document.getElementById('jim-acc-mode').value = access.mode || 'all';
        document.getElementById('jim-acc-default').value = access.defaultPreset || 'read_only';
        var pr = access.projectRestrict || { mode: 'jira', allow: [], block: [] };
        document.getElementById('jim-acc-proj-mode').value = pr.mode || 'jira';
        document.getElementById('jim-acc-proj-list').value = ((pr.mode === 'block' ? pr.block : pr.allow) || []).join(',');
        document.getElementById('jim-acc-sync').innerHTML = 'Access revision: <b>' + esc(access.revision) + '</b> &middot; applied to the Gateway automatically within ~20s of Apply.';
        renderAccessTables();
    }

    function loadAccess() {
        request('GET', BASE + '/access-policy', undefined, function (st, data) {
            if (st === 200 && data) {
                access = data; access.userPolicy = access.userPolicy || {}; access.groupPolicy = access.groupPolicy || {};
                access.userBlock = access.userBlock || []; access.groupBlock = access.groupBlock || [];
                renderAccess();
            }
        });
    }

    function applyAccess() {
        var mode = document.getElementById('jim-acc-proj-mode').value;
        var list = document.getElementById('jim-acc-proj-list').value.split(',').map(function (s) { return s.trim().toUpperCase(); }).filter(Boolean);
        var pr = { mode: mode, allow: mode === 'restrict' ? list : [], block: mode === 'block' ? list : [] };
        var draft = {
            mode: document.getElementById('jim-acc-mode').value,
            defaultPreset: document.getElementById('jim-acc-default').value,
            userPolicy: access.userPolicy, groupPolicy: access.groupPolicy,
            userBlock: access.userBlock, groupBlock: access.groupBlock,
            projectRestrict: pr, expectedRevision: access.revision
        };
        var grantsFull = draft.defaultPreset === 'full_controlled' ||
            Object.keys(access.userPolicy).some(function (k) { return access.userPolicy[k] === 'full_controlled'; }) ||
            Object.keys(access.groupPolicy).some(function (k) { return access.groupPolicy[k] === 'full_controlled'; });
        if (grantsFull && !confirm('This grants full_controlled (destructive tools allowed, with confirmation). Apply?')) return;
        request('POST', BASE + '/access-policy', draft, function (st, data) {
            var msg = document.getElementById('jim-acc-apply-msg');
            if (st === 200) { msg.textContent = ' Applied (revision ' + (data && data.revision) + ')'; loadAccess(); }
            else { msg.textContent = ' Failed: ' + ((data && data.message) || st); }
        });
    }

    function simulate() {
        var u = document.getElementById('jim-acc-sim-user').value.trim();
        var t = document.getElementById('jim-acc-sim-tool').value.trim();
        if (!u || !t) return;
        request('GET', BASE + '/access-simulate?user=' + encodeURIComponent(u) + '&tool=' + encodeURIComponent(t), undefined, function (st, data) {
            var el = document.getElementById('jim-acc-sim-result');
            if (st === 200 && data) {
                el.innerHTML = '<b class="jim-mcp-' + (data.decision === 'allow' ? 'ok' : 'off') + '">' + esc(data.decision) + '</b> &middot; preset ' + esc(data.effectivePreset) +
                    ' &middot; class ' + esc(data.toolClass) + ' &middot; rule ' + esc(data.matchedRule) +
                    (data.reason ? ' &middot; reason ' + esc(data.reason) : '') + ' &middot; confirm ' + esc(data.confirmationRequired);
            } else { el.textContent = 'error ' + st; }
        });
    }

    function init() {
        loadStatus();
        loadTools();
        loadAccess();
        document.getElementById('jim-acc-user-add').addEventListener('click', function () {
            var k = document.getElementById('jim-acc-user-key').value.trim(), p = document.getElementById('jim-acc-user-preset').value;
            if (!k) return;
            if (p === 'blocked') { if (access.userBlock.indexOf(k) < 0) access.userBlock.push(k); delete access.userPolicy[k]; }
            else { access.userPolicy[k] = p; access.userBlock = access.userBlock.filter(function (x) { return x !== k; }); }
            document.getElementById('jim-acc-user-key').value = ''; renderAccessTables();
        });
        document.getElementById('jim-acc-group-add').addEventListener('click', function () {
            var k = document.getElementById('jim-acc-group-name').value.trim(), p = document.getElementById('jim-acc-group-preset').value;
            if (!k) return; access.groupPolicy[k] = p; document.getElementById('jim-acc-group-name').value = ''; renderAccessTables();
        });
        document.getElementById('jim-acc-apply').addEventListener('click', applyAccess);
        document.getElementById('jim-acc-sim-run').addEventListener('click', simulate);
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
