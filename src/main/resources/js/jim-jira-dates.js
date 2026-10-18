/*
 * CorbitChat — Jira-wide Jalali date DISPLAY transformer.
 *
 * Display-layer only. Controlled by the global admin setting
 * `jiraWideJalaliEnabled` (read via GET /rest/jim/1.0/date-display). When the
 * setting is OFF this script does nothing. When ON it rewrites the VISIBLE TEXT
 * of Jira's <time datetime="..."> elements to Jalali/Shamsi, using the ISO
 * `datetime` attribute as the authoritative source.
 *
 * Safety design:
 *   - Only <time datetime> elements are touched. The ISO attribute is the source
 *     of truth, so we never parse ambiguous free text and never mis-convert issue
 *     keys, story points, versions, IDs or prices.
 *   - Stored values, form inputs, hidden fields, JQL, REST and exports are never
 *     touched — <time> elements are presentational only.
 *   - Idempotent + loop-proof: we only write when the current text differs from
 *     the target Jalali string, so our own writes (and Jira livestamp re-renders)
 *     never cause an infinite MutationObserver loop.
 *   - The original Gregorian text is kept in data-jim-greg (rollback) and in the
 *     title tooltip. Disabling the admin setting + reload restores native dates.
 *   - The CorbitChat chat app (#jim-messenger-app) is skipped — it formats its
 *     own dates from per-user preferences.
 */
(function () {
    'use strict';

    var CONFIG_URL_PATH = '/rest/jim/1.0/date-display';

    // ---- Jalali (Borkowski) — same verified algorithm as the chat converter ---
    function div(a, b) { return Math.floor(a / b); }
    var FA_DIGITS = ['\u06F0', '\u06F1', '\u06F2', '\u06F3', '\u06F4',
        '\u06F5', '\u06F6', '\u06F7', '\u06F8', '\u06F9'];
    var G_DAYS_IN_MONTH = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334];

    function gregorianToJalali(gy, gm, gd) {
        var jy, jm, jd;
        var gy2 = (gm > 2) ? (gy + 1) : gy;
        var days = 355666 + (365 * gy) + div(gy2 + 3, 4) - div(gy2 + 99, 100)
            + div(gy2 + 399, 400) + gd + G_DAYS_IN_MONTH[gm - 1];
        jy = -1595 + (33 * div(days, 12053));
        days %= 12053;
        jy += 4 * div(days, 1461);
        days %= 1461;
        if (days > 365) {
            jy += div(days - 1, 365);
            days = (days - 1) % 365;
        }
        if (days < 186) {
            jm = 1 + div(days, 31);
            jd = 1 + (days % 31);
        } else {
            jm = 7 + div(days - 186, 30);
            jd = 1 + ((days - 186) % 30);
        }
        return { jy: jy, jm: jm, jd: jd };
    }

    function pad2(n) { return n < 10 ? '0' + n : '' + n; }

    function faDigits(str) {
        var out = '';
        str = String(str);
        for (var i = 0; i < str.length; i++) {
            var c = str.charCodeAt(i);
            out += (c >= 48 && c <= 57) ? FA_DIGITS[c - 48] : str.charAt(i);
        }
        return out;
    }

    // JS Date -> "۱۴۰۵/۰۴/۲۲" or "۱۴۰۵/۰۴/۲۲ ۱۴:۳۰" (respects local/user timezone).
    function formatJalali(date, withTime) {
        var j = gregorianToJalali(date.getFullYear(), date.getMonth() + 1, date.getDate());
        var out = j.jy + '/' + pad2(j.jm) + '/' + pad2(j.jd);
        if (withTime) { out += ' ' + pad2(date.getHours()) + ':' + pad2(date.getMinutes()); }
        return faDigits(out);
    }

    // Parse the value of a <time datetime> attribute. Date-only strings are
    // parsed as LOCAL midnight (never new Date("YYYY-MM-DD"), which is UTC and
    // would shift the day). Full timestamps go through Date, which honours the
    // embedded timezone and renders in the browser/user timezone.
    function parseDateTimeAttr(v) {
        if (!v) { return null; }
        v = String(v).trim();
        var dateOnly = /^(\d{4})-(\d{2})-(\d{2})$/.exec(v);
        if (dateOnly) {
            return {
                date: new Date(parseInt(dateOnly[1], 10), parseInt(dateOnly[2], 10) - 1, parseInt(dateOnly[3], 10)),
                withTime: false
            };
        }
        var d = new Date(v);
        if (!isNaN(d.getTime())) {
            return { date: d, withTime: /[T\s]\d{2}:\d{2}/.test(v) };
        }
        // epoch millis / seconds fallback
        if (/^\d{10,13}$/.test(v)) {
            var ts = parseInt(v, 10);
            if (v.length <= 10) { ts *= 1000; }
            var e = new Date(ts);
            if (!isNaN(e.getTime())) { return { date: e, withTime: true }; }
        }
        return null;
    }

    // Elements/contexts we must never touch.
    function isUnsafe(el) {
        try {
            return !!(el.closest
                && el.closest('#jim-messenger-app, [contenteditable="true"], textarea, input, select, script, style'));
        } catch (e) {
            return false;
        }
    }

    // Convert a single <time datetime> element. Loop-proof: only writes when the
    // current text differs from the target, so re-entrant mutations are no-ops.
    function transformTime(el) {
        if (!el || el.nodeType !== 1 || el.tagName !== 'TIME') { return; }
        var attr = el.getAttribute('datetime') || el.getAttribute('data-datetime');
        if (!attr) { return; }
        if (isUnsafe(el)) { return; }
        var parsed = parseDateTimeAttr(attr);
        if (!parsed) { return; }
        var jalali = formatJalali(parsed.date, parsed.withTime);
        if (!jalali || el.textContent === jalali) { return; }
        if (!el.hasAttribute('data-jim-greg')) {
            el.setAttribute('data-jim-greg', el.textContent);
        }
        if (!el.getAttribute('title')) {
            var orig = el.getAttribute('data-jim-greg');
            if (orig) { el.setAttribute('title', orig.trim()); }
        }
        el.textContent = jalali;
    }

    // ---- Date INPUT fields: show a read-only Jalali preview, never touch the
    // input value or the native picker (form submission + picker stay Gregorian).
    var MONTHS = { jan: 1, feb: 2, mar: 3, apr: 4, may: 5, jun: 6, jul: 7,
        aug: 8, sep: 9, oct: 10, nov: 11, dec: 12 };

    // Parse Jira's common visible date formats, e.g. "19/Jul/26" and
    // "14/Jul/26 2:07 PM" (also 4-digit years). Returns null if not a date.
    function parseJiraDisplayDate(str) {
        if (!str) { return null; }
        var m = /^\s*(\d{1,2})\/([A-Za-z]{3})\/(\d{2,4})(?:\s+(\d{1,2}):(\d{2})\s*([AaPp][Mm]))?\s*$/.exec(str);
        if (!m) { return null; }
        var mon = MONTHS[m[2].toLowerCase()];
        if (!mon) { return null; }
        var day = parseInt(m[1], 10);
        var yy = parseInt(m[3], 10);
        var year = m[3].length === 4 ? yy : (yy < 70 ? 2000 + yy : 1900 + yy);
        var withTime = m[4] != null;
        var hh = 0, mm = 0;
        if (withTime) {
            hh = parseInt(m[4], 10) % 12;
            if (/p/i.test(m[6])) { hh += 12; }
            mm = parseInt(m[5], 10);
        }
        var d = new Date(year, mon - 1, day, hh, mm);
        return isNaN(d.getTime()) ? null : { date: d, withTime: withTime };
    }

    function inputUnsafe(input) {
        var skip = ['hidden', 'checkbox', 'radio', 'password', 'number', 'range', 'color', 'file'];
        if (input.type && skip.indexOf(input.type) !== -1) { return true; }
        try { if (input.closest && input.closest('#jim-messenger-app')) { return true; } } catch (e) { /* */ }
        return false;
    }

    // Only decorate inputs that both (a) contain a Jira-format date value and
    // (b) look like a date field, to avoid false positives on other text fields.
    function isDateFieldish(input) {
        var s = ((input.id || '') + ' ' + (input.name || '') + ' ' + (input.className || '')).toLowerCase();
        if (/date|due|deadline|\bdob\b|start|end/.test(s)) { return true; }
        if (input.classList && input.classList.contains('aui-date-picker')) { return true; }
        var p = input.parentNode;
        try {
            if (p && p.querySelector
                && p.querySelector('.aui-icon-date, .icon-date, .calendar, .aui-date-picker, [data-datepicker]')) {
                return true;
            }
        } catch (e) { /* */ }
        return false;
    }

    function updateInputHint(input) {
        var parsed = parseJiraDisplayDate(input.value || '');
        var hint = input.__jimHint;
        if (!parsed) {
            if (hint) { hint.textContent = ''; }
            return;
        }
        if (!hint || !hint.parentNode) {
            hint = document.createElement('span');
            hint.className = 'jim-jalali-input-hint';
            hint.setAttribute('data-jim-jalali-hint', '1');
            hint.style.cssText = 'margin-inline-start:8px;margin-left:8px;color:#626F86;'
                + 'font-size:12px;direction:rtl;unicode-bidi:isolate;white-space:nowrap;';
            if (input.parentNode) { input.parentNode.insertBefore(hint, input.nextSibling); }
            input.__jimHint = hint;
        }
        hint.textContent = '\u200E(' + formatJalali(parsed.date, parsed.withTime) + ')';
    }

    function transformDateInput(input) {
        if (!input || input.nodeType !== 1 || input.tagName !== 'INPUT') { return; }
        if (inputUnsafe(input)) { return; }
        if (!parseJiraDisplayDate(input.value || '')) { return; }
        if (!isDateFieldish(input)) { return; }
        if (input.getAttribute('data-jim-hint-bound') !== '1') {
            input.setAttribute('data-jim-hint-bound', '1');
            var upd = function () { updateInputHint(input); };
            input.addEventListener('input', upd);
            input.addEventListener('change', upd);
            input.addEventListener('blur', upd);
        }
        updateInputHint(input);
    }

    function scan(root) {
        if (!root || root.nodeType !== 1) { return; }
        try {
            if (root.tagName === 'TIME') { transformTime(root); }
            var list = root.querySelectorAll ? root.querySelectorAll('time[datetime], time[data-datetime]') : [];
            for (var i = 0; i < list.length; i++) { transformTime(list[i]); }
            if (root.tagName === 'INPUT') { transformDateInput(root); }
            var inputs = root.querySelectorAll ? root.querySelectorAll('input') : [];
            for (var k = 0; k < inputs.length; k++) { transformDateInput(inputs[k]); }
        } catch (e) { /* never let a DOM edge-case break the page */ }
    }

    // Restore native Gregorian text (used if the server reconciles to disabled).
    function revertAll() {
        try {
            var list = document.querySelectorAll('time[data-jim-greg]');
            for (var i = 0; i < list.length; i++) {
                var el = list[i];
                el.textContent = el.getAttribute('data-jim-greg');
                el.removeAttribute('data-jim-greg');
            }
            var hints = document.querySelectorAll('span[data-jim-jalali-hint]');
            for (var h = 0; h < hints.length; h++) {
                if (hints[h].parentNode) { hints[h].parentNode.removeChild(hints[h]); }
            }
        } catch (e) { /* ignore */ }
    }

    // ---- Throttled MutationObserver for dynamically loaded Jira content -------
    var observer = null;
    var pending = [];
    var flushScheduled = false;

    function flush() {
        flushScheduled = false;
        var roots = pending;
        pending = [];
        for (var i = 0; i < roots.length; i++) { scan(roots[i]); }
    }

    function scheduleFlush() {
        if (flushScheduled) { return; }
        flushScheduled = true;
        (window.requestAnimationFrame || function (cb) { setTimeout(cb, 150); })(flush);
    }

    function startObserver() {
        if (observer) { return; }
        observer = new MutationObserver(function (mutations) {
            for (var i = 0; i < mutations.length; i++) {
                var m = mutations[i];
                if (m.type === 'characterData') {
                    if (m.target && m.target.parentNode) { pending.push(m.target.parentNode); }
                } else if (m.addedNodes && m.addedNodes.length) {
                    for (var j = 0; j < m.addedNodes.length; j++) {
                        if (m.addedNodes[j].nodeType === 1) { pending.push(m.addedNodes[j]); }
                    }
                } else if (m.target && m.target.nodeType === 1) {
                    pending.push(m.target);
                }
            }
            if (pending.length) { scheduleFlush(); }
        });
        observer.observe(document.documentElement, {
            childList: true,
            subtree: true,
            characterData: true
        });
    }

    function enable() {
        scan(document);
        startObserver();
    }

    // ---- Bootstrap: read the global admin setting, then act ------------------
    function contextPath() {
        if (window.AJS && typeof AJS.contextPath === 'function') { return AJS.contextPath(); }
        return (window.contextPath || '');
    }

    function boot() {
        var url = contextPath() + CONFIG_URL_PATH;
        var done = false;
        function apply(enabled) {
            if (done) { return; }
            done = true;
            if (enabled) { enable(); }
            else if (observer) { revertAll(); }
        }
        try {
            fetch(url, { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
                .then(function (r) { return r.ok ? r.json() : null; })
                .then(function (cfg) {
                    apply(!!(cfg && cfg.jiraWideDateDisplayEnabled && cfg.jiraWideCalendar === 'jalali'));
                })
                .catch(function () { /* on any error, leave Jira dates untouched */ });
        } catch (e) { /* fetch unavailable — do nothing (safe default) */ }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', boot);
    } else {
        boot();
    }
})();
