/*
 * CorbitChat — Phase 8D custom read-only Workflow Viewer (web issue tab "فرآیند").
 * Fetches /rest/jim/1.0/issues/{key}/workflow-view and renders statuses, transitions,
 * available-now transitions, and safe condition/validator/post-function summaries.
 * Plain DOM + hand-built SVG. No global lodash/underscore. No Jira Workflow Designer,
 * no Raphael, no iframe. Read-only — never transitions the issue.
 */
(function () {
    'use strict';
    var SVGNS = 'http://www.w3.org/2000/svg';
    var L = {
        view: 'مشاهده فرآیند', workflow: 'فرآیند انجام کار', current: 'وضعیت فعلی',
        available: 'انتقال‌های قابل انجام', conditions: 'شرایط انتقال', validators: 'اعتبارسنجی‌ها',
        post: 'اقدامات پس از انتقال', start: 'شروع', end: 'پایان', global: 'انتقال عمومی',
        tech: 'جزئیات فنی', redacted: 'جزئیات محرمانه مخفی شده است', transitions: 'انتقال‌ها',
        from: 'از', to: 'به', loop: 'بازگشتی', none: 'موردی ثبت نشده', loading: 'در حال بارگذاری…',
        error: 'خطا در بارگذاری فرآیند', list: 'نمایش فهرستی', graph: 'نمایش نموداری', warn: 'هشدارها'
    };
    function t(k) { return L[k] || k; }
    function esc(s) { return (s == null ? '' : String(s)).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
    function el(tag, cls, html) { var e = document.createElement(tag); if (cls) e.className = cls; if (html != null) e.innerHTML = html; return e; }
    function svg(tag, attrs) { var e = document.createElementNS(SVGNS, tag); if (attrs) { for (var k in attrs) { if (attrs.hasOwnProperty(k)) e.setAttribute(k, attrs[k]); } } return e; }

    function req(url, cb) {
        var x = new XMLHttpRequest();
        x.open('GET', url, true);
        x.setRequestHeader('Accept', 'application/json');
        x.onreadystatechange = function () {
            if (x.readyState !== 4) { return; }
            var body = null;
            try { body = JSON.parse(x.responseText || '{}'); } catch (e) { body = null; }
            cb(x.status, body);
        };
        x.send();
    }

    function catColor(cat, isCurrent) {
        if (isCurrent) { return { fill: '#0b6e6e', text: '#ffffff', border: '#0b6e6e' }; } // teal
        if (cat === 'done') { return { fill: '#e3fcef', text: '#0b5a3a', border: '#36b37e' }; } // green
        if (cat === 'indeterminate') { return { fill: '#deebff', text: '#0b3d91', border: '#4c9aff' }; } // blue-ish
        return { fill: '#f1f2f4', text: '#42526e', border: '#c1c7d0' }; // neutral/new
    }

    function render(root, data) {
        root.innerHTML = '';
        var wrap = el('div', 'jim-wf-wrap');

        // header
        var head = el('div', 'jim-wf-head');
        var title = (data.issueKey ? data.issueKey + ' — ' : '') + esc(data.summary || '');
        head.appendChild(el('div', 'jim-wf-title', esc(title)));
        var sub = el('div', 'jim-wf-sub');
        var wfName = (data.workflow && data.workflow.name) ? data.workflow.name : '—';
        sub.appendChild(el('span', 'jim-wf-chip jim-wf-chip-wf', t('workflow') + ': ' + esc(wfName)
            + (data.workflow && data.workflow.isDraft ? ' (draft)' : '')));
        if (data.issueType && data.issueType.name) { sub.appendChild(el('span', 'jim-wf-chip', esc(data.issueType.name))); }
        if (data.currentStatus && data.currentStatus.name) {
            sub.appendChild(el('span', 'jim-wf-chip jim-wf-chip-current', t('current') + ': ' + esc(data.currentStatus.name)));
        }
        head.appendChild(sub);
        wrap.appendChild(head);

        // available transitions
        var avail = data.availableTransitions || [];
        var availRow = el('div', 'jim-wf-avail');
        availRow.appendChild(el('span', 'jim-wf-avail-label', t('available') + ':'));
        if (!avail.length) { availRow.appendChild(el('span', 'jim-wf-muted', t('none'))); }
        for (var i = 0; i < avail.length; i++) {
            availRow.appendChild(el('span', 'jim-wf-avail-chip', esc(avail[i].name)));
        }
        wrap.appendChild(availRow);

        // view toggle
        var dense = !!data.dense;
        var toggle = el('div', 'jim-wf-toggle');
        var bGraph = el('button', 'aui-button' + (dense ? '' : ' jim-wf-active'), t('graph'));
        var bList = el('button', 'aui-button' + (dense ? ' jim-wf-active' : ''), t('list'));
        bGraph.type = 'button'; bList.type = 'button';
        toggle.appendChild(bGraph); toggle.appendChild(bList);
        wrap.appendChild(toggle);

        var graphBox = el('div', 'jim-wf-graph');
        var listBox = el('div', 'jim-wf-list');
        graphBox.appendChild(buildGraph(data));
        listBox.appendChild(buildList(data));
        if (dense) { graphBox.style.display = 'none'; } else { listBox.style.display = 'none'; }
        bGraph.onclick = function () { graphBox.style.display = ''; listBox.style.display = 'none'; bGraph.classList.add('jim-wf-active'); bList.classList.remove('jim-wf-active'); };
        bList.onclick = function () { graphBox.style.display = 'none'; listBox.style.display = ''; bList.classList.add('jim-wf-active'); bGraph.classList.remove('jim-wf-active'); };
        wrap.appendChild(graphBox);
        wrap.appendChild(listBox);

        // transition details (shared)
        var details = el('div', 'jim-wf-details');
        details.id = 'jim-wf-details';
        wrap.appendChild(details);

        // warnings
        if (data.warnings && data.warnings.length) {
            var wbox = el('div', 'jim-wf-warn');
            wbox.appendChild(el('div', 'jim-wf-warn-h', '⚠ ' + t('warn')));
            for (var w = 0; w < data.warnings.length; w++) { wbox.appendChild(el('div', 'jim-wf-warn-item', esc(data.warnings[w]))); }
            wrap.appendChild(wbox);
        }
        root.appendChild(wrap);
    }

    function buildGraph(data) {
        var statuses = data.statuses || [];
        var transitions = data.transitions || [];
        if (!statuses.length) { return el('div', 'jim-wf-muted', t('none')); }
        var W = 110, H = 44, padX = 40, padY = 24;
        var maxX = 0, maxY = 0, byId = {};
        for (var i = 0; i < statuses.length; i++) {
            var s = statuses[i];
            s._x = (s.x || 0) + padX; s._y = (s.y || 0) + padY;
            byId[s.id] = s;
            if (s._x + W > maxX) { maxX = s._x + W; }
            if (s._y + H > maxY) { maxY = s._y + H; }
        }
        var svgW = Math.max(maxX + padX, 320), svgH = Math.max(maxY + padY, 160);
        var root = svg('svg', { 'class': 'jim-wf-svg', viewBox: '0 0 ' + svgW + ' ' + svgH, width: '100%', height: Math.min(svgH, 520) });
        var defs = svg('defs');
        var marker = svg('marker', { id: 'jim-wf-arrow', markerWidth: 8, markerHeight: 8, refX: 7, refY: 3, orient: 'auto', markerUnits: 'strokeWidth' });
        marker.appendChild(svg('path', { d: 'M0,0 L7,3 L0,6 Z', fill: '#7a869a' }));
        defs.appendChild(marker); root.appendChild(defs);

        // edges
        for (var e = 0; e < transitions.length; e++) {
            var tr = transitions[e];
            var a = byId[tr.fromStatusId], b = byId[tr.toStatusId];
            if (tr.type === 'loop' && a) {
                var lp = svg('path', { d: 'M' + (a._x + W) + ',' + (a._y + 10) + ' q 28,-6 0,22', fill: 'none', stroke: tr.isAvailableNow ? '#0b6e6e' : '#a5adba', 'stroke-width': tr.isAvailableNow ? 2.5 : 1.5, 'marker-end': 'url(#jim-wf-arrow)' });
                root.appendChild(lp); continue;
            }
            if (!a || !b) { continue; }
            var x1 = a._x + W / 2, y1 = a._y + H / 2, x2 = b._x + W / 2, y2 = b._y + H / 2;
            var ln = svg('line', { x1: x1, y1: y1, x2: x2, y2: y2, stroke: tr.isAvailableNow ? '#0b6e6e' : '#a5adba', 'stroke-width': tr.isAvailableNow ? 2.5 : 1.5, 'stroke-dasharray': tr.type === 'global' ? '5,4' : '', 'marker-end': 'url(#jim-wf-arrow)' });
            root.appendChild(ln);
        }
        // nodes
        for (var n = 0; n < statuses.length; n++) {
            var st = statuses[n];
            var c = catColor(st.category, st.isCurrent);
            var g = svg('g', {});
            var rect = svg('rect', { x: st._x, y: st._y, rx: 10, ry: 10, width: W, height: H, fill: c.fill, stroke: c.border, 'stroke-width': st.isCurrent ? 3 : 1.5 });
            g.appendChild(rect);
            var label = (st.name || '').length > 16 ? (st.name.substring(0, 15) + '…') : (st.name || '');
            var txt = svg('text', { x: st._x + W / 2, y: st._y + H / 2 + 4, 'text-anchor': 'middle', fill: c.text, 'font-size': 12, 'font-weight': st.isCurrent ? 700 : 500 });
            txt.textContent = label;
            g.appendChild(txt);
            if (st.isInitial) { var bi = svg('text', { x: st._x + 8, y: st._y - 4, fill: '#36b37e', 'font-size': 10 }); bi.textContent = '▸ ' + t('start'); g.appendChild(bi); }
            if (st.isTerminal) { var bt = svg('text', { x: st._x + W - 8, y: st._y - 4, 'text-anchor': 'end', fill: '#0b5a3a', 'font-size': 10 }); bt.textContent = t('end') + ' ■'; g.appendChild(bt); }
            root.appendChild(g);
        }
        return root;
    }

    function buildList(data) {
        var box = el('div', 'jim-wf-tlist');
        var statuses = {}; (data.statuses || []).forEach(function (s) { statuses[s.id] = s; });
        var trs = data.transitions || [];
        box.appendChild(el('div', 'jim-wf-tlist-h', t('transitions') + ' (' + trs.length + ')'));
        for (var i = 0; i < trs.length; i++) {
            (function (tr) {
                var row = el('div', 'jim-wf-trow' + (tr.isAvailableNow ? ' jim-wf-trow-avail' : ''));
                var fromN = tr.fromStatusId && statuses[tr.fromStatusId] ? statuses[tr.fromStatusId].name : (tr.type === 'global' ? t('global') : '—');
                var toN = tr.toStatusId && statuses[tr.toStatusId] ? statuses[tr.toStatusId].name : (tr.type === 'loop' ? t('loop') : '—');
                var head = el('div', 'jim-wf-trow-head');
                head.innerHTML = '<span class="jim-wf-tname">' + esc(tr.name) + '</span>'
                    + '<span class="jim-wf-tpath">' + esc(fromN) + ' → ' + esc(toN) + '</span>'
                    + (tr.type !== 'normal' ? '<span class="jim-wf-ttype">' + esc(tr.type) + '</span>' : '')
                    + (tr.isAvailableNow ? '<span class="jim-wf-tavail">●</span>' : '');
                var detail = el('div', 'jim-wf-tdetail'); detail.style.display = 'none';
                detail.appendChild(ruleBlock(t('conditions'), tr.conditions));
                detail.appendChild(ruleBlock(t('validators'), tr.validators));
                detail.appendChild(ruleBlock(t('post'), tr.postFunctions));
                head.onclick = function () { detail.style.display = detail.style.display === 'none' ? '' : 'none'; };
                row.appendChild(head); row.appendChild(detail);
                box.appendChild(row);
            })(trs[i]);
        }
        return box;
    }

    function ruleBlock(label, rules) {
        var b = el('div', 'jim-wf-rblock');
        b.appendChild(el('div', 'jim-wf-rlabel', label));
        if (!rules || !rules.length) { b.appendChild(el('div', 'jim-wf-muted', t('none'))); return b; }
        for (var i = 0; i < rules.length; i++) {
            var r = rules[i];
            var vis = r.visibility === 'redacted' ? ' jim-wf-redacted' : (r.visibility === 'adminOnly' ? ' jim-wf-admin' : '');
            var item = el('div', 'jim-wf-ritem' + vis);
            var line = '<span class="jim-wf-rname">' + esc(r.name || '—') + '</span>';
            if (r.visibility === 'redacted') { line += '<span class="jim-wf-rbadge">' + t('redacted') + '</span>'; }
            if (r.className) { line += '<span class="jim-wf-rclass">' + esc(r.className) + '</span>'; }
            item.innerHTML = line;
            if (r.parameters && r.parameters.length) {
                var pl = el('div', 'jim-wf-rparams');
                for (var p = 0; p < r.parameters.length; p++) { pl.appendChild(el('span', 'jim-wf-rparam', esc(r.parameters[p].key) + '=' + esc(r.parameters[p].value))); }
                item.appendChild(pl);
            }
            b.appendChild(item);
        }
        return b;
    }

    function openModal(data) {
        var ov = el('div', 'jim-wf-modal-overlay');
        var modal = el('div', 'jim-wf-modal');
        var close = el('button', 'aui-button jim-wf-modal-close', '✕'); close.type = 'button';
        close.onclick = function () { document.body.removeChild(ov); };
        modal.appendChild(close);
        var body = el('div', 'jim-wf-modal-body');
        render(body, data);
        modal.appendChild(body);
        ov.appendChild(modal);
        ov.onclick = function (ev) { if (ev.target === ov) { document.body.removeChild(ov); } };
        document.body.appendChild(ov);
    }

    // Idempotent per (element, issueKey): a mount reused for a DIFFERENT issue
    // (Issue Navigator switching) re-initializes; a mount already showing THIS
    // issue is left untouched (no duplicate API calls). Uses an element property
    // (not a DOM attribute) so a freshly AJAX-loaded tab fragment always inits.
    function init(root) {
        if (!root) { return; }
        var issueKey = root.getAttribute('data-issue-key');
        if (!issueKey) { return; }
        if (root._jimWfKey === issueKey) { return; }
        root._jimWfKey = issueKey;
        root.innerHTML = '<div class="jim-wf-loading">' + t('loading') + '</div>';
        var base = (window.contextPath || '') + '/rest/jim/1.0/issues/';
        req(base + encodeURIComponent(issueKey) + '/workflow-view', function (status, data) {
            // Guard against a late response after the mount was reused for another issue.
            if (root._jimWfKey !== issueKey) { return; }
            // The plugin REST returns the DTO at top-level (JimRestResponses.okJson);
            // accept a {data:...} envelope too for forward-compat.
            var dto = data && data.data ? data.data : data;
            if (status !== 200 || !dto || !dto.statuses) {
                root._jimWfKey = null; // allow a later re-scan/retry
                var msg = (dto && dto.message) ? (' ' + dto.message) : '';
                root.innerHTML = '<div class="jim-wf-error">' + t('error') + ' (' + status + ')' + esc(msg) + '</div>';
                return;
            }
            render(root, dto);
            var openBtn = el('button', 'aui-button jim-wf-openmodal', L.view); openBtn.type = 'button';
            openBtn.onclick = function () { openModal(dto); };
            root.insertBefore(openBtn, root.firstChild);
        });
    }

    function scan() {
        var nodes = document.querySelectorAll('#jim-workflow-app, .jim-workflow-app');
        for (var i = 0; i < nodes.length; i++) { init(nodes[i]); }
    }

    // Lifecycle hooks mirroring the Report tab: the issue-view AJAX-loads a tab
    // fragment (and the Navigator swaps issues) WITHOUT a page reload, so cover
    // Jira content events, jQuery ajaxComplete, and a debounced MutationObserver.
    function bootstrap() {
        scan();
        try {
            if (window.JIRA && JIRA.bind && JIRA.Events && JIRA.Events.NEW_CONTENT_ADDED) {
                JIRA.bind(JIRA.Events.NEW_CONTENT_ADDED, scan);
            }
            if (window.AJS && AJS.$) { AJS.$(document).on('ajaxComplete', scan); }
        } catch (e) { /* ignore */ }
        try {
            var scheduled = false;
            var mo = new MutationObserver(function () {
                if (scheduled) { return; }
                scheduled = true;
                window.setTimeout(function () { scheduled = false; scan(); }, 60);
            });
            mo.observe(document.body, { childList: true, subtree: true });
        } catch (e) { /* older browsers: event hooks only */ }
    }
    if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', bootstrap); } else { bootstrap(); }
    window.JimWorkflowViewer = { scan: scan };
})();
