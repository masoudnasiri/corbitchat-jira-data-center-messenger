/*
 * CorbitChat — Issue Report-compliance tab (Sprint 12 Phase C).
 *
 * Renders the compliance summary, the separated list of report comments
 * (marker-tagged Jira comments), a date filter, and Submit/Reply actions.
 * Talks to /rest/jim/1.0/report/*. Fully client-rendered so it works with the
 * issue-view AJAX tab load. Persian UI + Jalali dates honor the per-user web
 * locale injected on the mount element. No sensitive /rest data is cached.
 */
(function () {
    'use strict';

    var BASE = (window.contextPath || '') + '/rest/jim/1.0/report';

    // ----- localization -----------------------------------------------------
    var FA_DIGITS = ['\u06f0', '\u06f1', '\u06f2', '\u06f3', '\u06f4', '\u06f5', '\u06f6', '\u06f7', '\u06f8', '\u06f9'];

    var STR = {
        submitted: { en: function (v, r) { return v + ' of ' + r + ' required reports submitted'; },
                     fa: function (v, r) { return faNum(v) + ' \u06af\u0632\u0627\u0631\u0634 \u0627\u0632 ' + faNum(r) + ' \u06af\u0632\u0627\u0631\u0634 \u0627\u0631\u0627\u0626\u0647 \u0634\u062f\u0647'; } },
        onTrack: { en: 'Reports are on track', fa: '\u06af\u0632\u0627\u0631\u0634 \u0637\u0628\u0642 \u0631\u0648\u0627\u0644 \u0627\u0633\u062a' },
        overdue: { en: 'Report is overdue', fa: '\u06af\u0632\u0627\u0631\u0634 \u0639\u0642\u0628 \u0627\u0641\u062a\u0627\u062f\u0647 \u0627\u0633\u062a' },
        notConfigured: { en: 'Report schedule is not configured', fa: '\u062a\u0646\u0638\u06cc\u0645\u0627\u062a \u06af\u0632\u0627\u0631\u0634\u200c\u062f\u0647\u06cc \u06a9\u0627\u0645\u0644 \u0646\u06cc\u0633\u062a' },
        notConfiguredEnv: { en: 'Report custom fields are not configured on this environment', fa: '\u0641\u06cc\u0644\u062f\u0647\u0627\u06cc \u06af\u0632\u0627\u0631\u0634 \u062f\u0631 \u0627\u06cc\u0646 \u0645\u062d\u06cc\u0637 \u067e\u06cc\u06a9\u0631\u0628\u0646\u062f\u06cc \u0646\u0634\u062f\u0647\u200c\u0627\u0646\u062f' },
        totalReports: { en: function (n) { return 'Total submitted reports: ' + n; }, fa: function (n) { return '\u062a\u0639\u062f\u0627\u062f \u06a9\u0644 \u06af\u0632\u0627\u0631\u0634\u200c\u0647\u0627\u06cc \u062b\u0628\u062a \u0634\u062f\u0647: ' + faNum(n); } },
        level: { en: 'Follow-up level', fa: '\u0633\u0637\u062d \u067e\u06cc\u06af\u06cc\u0631\u06cc' },
        windowLbl: { en: 'Reporting window', fa: '\u0628\u0627\u0632\u0647 \u06af\u0632\u0627\u0631\u0634\u200c\u062f\u0647\u06cc' },
        interval: { en: 'Interval', fa: '\u0641\u0627\u0635\u0644\u0647 \u06af\u0632\u0627\u0631\u0634\u200c\u062f\u0647\u06cc' },
        outOfWindow: { en: 'Out-of-window reports', fa: '\u06af\u0632\u0627\u0631\u0634\u200c\u0647\u0627\u06cc \u062e\u0627\u0631\u062c \u0627\u0632 \u0628\u0627\u0632\u0647' },
        missed: { en: 'Missed intervals', fa: '\u06af\u0632\u0627\u0631\u0634\u200c\u0647\u0627\u06cc \u0639\u0642\u0628\u200c\u0627\u0641\u062a\u0627\u062f\u0647' },
        created: { en: 'Issue created', fa: '\u0632\u0645\u0627\u0646 \u0627\u06cc\u062c\u0627\u062f' },
        nextExpected: { en: 'Next expected report', fa: '\u06af\u0632\u0627\u0631\u0634 \u0628\u0639\u062f\u06cc \u0645\u0648\u0631\u062f \u0627\u0646\u062a\u0638\u0627\u0631' },
        submit: { en: 'Submit report', fa: '\u062b\u0628\u062a \u06af\u0632\u0627\u0631\u0634' },
        reply: { en: 'Reply', fa: '\u067e\u0627\u0633\u062e' },
        cancel: { en: 'Cancel', fa: '\u0627\u0646\u0635\u0631\u0627\u0641' },
        send: { en: 'Send', fa: '\u0627\u0631\u0633\u0627\u0644' },
        writeReport: { en: 'Write your report\u2026', fa: '\u0645\u062a\u0646 \u06af\u0632\u0627\u0631\u0634 \u0631\u0627 \u0628\u0646\u0648\u06cc\u0633\u06cc\u062f\u2026' },
        writeReply: { en: 'Write a reply\u2026', fa: '\u067e\u0627\u0633\u062e \u062e\u0648\u062f \u0631\u0627 \u0628\u0646\u0648\u06cc\u0633\u06cc\u062f\u2026' },
        noReports: { en: 'No reports yet.', fa: '\u0647\u0646\u0648\u0632 \u06af\u0632\u0627\u0631\u0634\u06cc \u062b\u0628\u062a \u0646\u0634\u062f\u0647 \u0627\u0633\u062a.' },
        filterNote: { en: 'Report list is filtered by date', fa: '\u0641\u0647\u0631\u0633\u062a \u06af\u0632\u0627\u0631\u0634\u200c\u0647\u0627 \u0628\u0631 \u0627\u0633\u0627\u0633 \u0641\u06cc\u0644\u062a\u0631 \u062a\u0627\u0631\u06cc\u062e \u0646\u0645\u0627\u06cc\u0634 \u062f\u0627\u062f\u0647 \u0634\u062f\u0647 \u0627\u0633\u062a' },
        fToday: { en: 'Today', fa: '\u0627\u0645\u0631\u0648\u0632' },
        f3: { en: 'Last 3 days', fa: '\u06f3 \u0631\u0648\u0632 \u06af\u0630\u0634\u062a\u0647' },
        f7: { en: 'Last 7 days', fa: '\u06cc\u06a9 \u0647\u0641\u062a\u0647 \u06af\u0630\u0634\u062a\u0647' },
        f30: { en: 'Last 30 days', fa: '\u06cc\u06a9 \u0645\u0627\u0647 \u06af\u0630\u0634\u062a\u0647' },
        fAll: { en: 'All', fa: '\u0647\u0645\u0647' },
        stValid: { en: 'Valid report', fa: '\u06af\u0632\u0627\u0631\u0634 \u0645\u0639\u062a\u0628\u0631' },
        stDup: { en: 'Duplicate in interval', fa: '\u062a\u06a9\u0631\u0627\u0631\u06cc \u062f\u0631 \u0647\u0645\u0627\u0646 \u0628\u0627\u0632\u0647' },
        stOut: { en: 'Out of window', fa: '\u062e\u0627\u0631\u062c \u0627\u0632 \u0628\u0627\u0632\u0647' },
        reportsAreClosed: { en: 'Report submission is closed', fa: '\u0645\u0647\u0644\u062a \u062b\u0628\u062a \u06af\u0632\u0627\u0631\u0634 \u0628\u0647 \u067e\u0627\u06cc\u0627\u0646 \u0631\u0633\u06cc\u062f\u0647 \u0627\u0633\u062a' },
        dueMissing: { en: 'Due Date is not set', fa: 'Due Date \u062b\u0628\u062a \u0646\u0634\u062f\u0647 \u0627\u0633\u062a' },
        noFollowUpLevel: { en: 'Follow-up level is not set', fa: '\u0633\u0637\u062d \u067e\u06cc\u06af\u06cc\u0631\u06cc \u062a\u0639\u06cc\u06cc\u0646 \u0646\u0634\u062f\u0647 \u0627\u0633\u062a' },
        complianceUnavailable: { en: 'Schedule compliance is not available for this issue', fa: '\u0648\u0636\u0639\u06cc\u062a \u0627\u0646\u0637\u0628\u0627\u0642 \u0632\u0645\u0627\u0646\u200c\u0628\u0646\u062f\u06cc \u0628\u0631\u0627\u06cc \u0627\u06cc\u0646 \u0622\u06cc\u062a\u0645 \u062f\u0631 \u062f\u0633\u062a\u0631\u0633 \u0646\u06cc\u0633\u062a' },
        submitDisabledNoConfig: { en: 'Report submission is disabled because follow-up level or report settings are not configured.', fa: '\u062b\u0628\u062a \u06af\u0632\u0627\u0631\u0634 \u0641\u0639\u0627\u0644 \u0646\u06cc\u0633\u062a\u060c \u0686\u0648\u0646 \u0633\u0637\u062d \u067e\u06cc\u06af\u06cc\u0631\u06cc \u06cc\u0627 \u062a\u0646\u0638\u06cc\u0645\u0627\u062a \u06af\u0632\u0627\u0631\u0634\u200c\u062f\u0647\u06cc \u06a9\u0627\u0645\u0644 \u0646\u06cc\u0633\u062a.' },
        attach: { en: 'Attach file', fa: '\u067e\u06cc\u0648\u0633\u062a \u0641\u0627\u06cc\u0644' },
        addReaction: { en: 'Add reaction', fa: '\u0627\u0641\u0632\u0648\u062f\u0646 \u0648\u0627\u06a9\u0646\u0634' },
        minRequiredUntilEnd: { en: 'Minimum required reports until end', fa: '\u062d\u062f\u0627\u0642\u0644 \u06af\u0632\u0627\u0631\u0634 \u0645\u0648\u0631\u062f\u0646\u06cc\u0627\u0632 \u062a\u0627 \u067e\u0627\u06cc\u0627\u0646' },
        newestFirst: { en: 'Newest first', fa: '\u062c\u062f\u06cc\u062f\u062a\u0631\u06cc\u0646 \u0627\u0648\u0644' },
        oldestFirst: { en: 'Oldest first', fa: '\u0642\u062f\u06cc\u0645\u06cc\u200c\u062a\u0631\u06cc\u0646 \u0627\u0648\u0644' },
        reportingDeadline: { en: 'Reporting deadline', fa: '\u0645\u0647\u0644\u062a \u06af\u0632\u0627\u0631\u0634\u200c\u062f\u0647\u06cc' },
        deadlineDue: { en: 'until Due Date', fa: '\u062a\u0627 Due Date' },
        deadlineDone: { en: 'until Done', fa: '\u062a\u0627 Done \u0634\u062f\u0646' },
        deadlineEarlier: { en: 'earlier of Due Date or Done', fa: '\u062a\u0627 Due Date \u06cc\u0627 Done \u0634\u062f\u0646\u060c \u0647\u0631\u06a9\u062f\u0627\u0645 \u0632\u0648\u062f\u062a\u0631' }
    };

    function deadlineText(activeUntil) {
        if (activeUntil === 'due') { return t('deadlineDue'); }
        if (activeUntil === 'done') { return t('deadlineDone'); }
        return t('deadlineEarlier');
    }

    // Preset reactions for the quick-add menu.
    var REACTION_PRESETS = ['\uD83D\uDC4D', '\u2705', '\u2764\uFE0F', '\uD83C\uDF89', '\uD83D\uDC40'];

    var LANG = 'en';
    var CAL = 'gregorian';
    // Offset (minutes from UTC) of the effective tz the server used for the
    // reporting-window classification. Report times are rendered in THIS tz so
    // the on-screen time matches the valid/out-of-window badge (never the
    // browser's own tz, which may differ from the Jira/user tz).
    var TZ_OFFSET_MIN = null;

    function t(key) {
        var e = STR[key];
        if (!e) { return key; }
        return e[LANG] != null ? e[LANG] : e.en;
    }
    function faNum(v) {
        if (LANG !== 'fa') { return String(v); }
        return String(v).replace(/[0-9]/g, function (d) { return FA_DIGITS[+d]; });
    }

    // Jalali conversion (Borkowski), for date display only.
    function toJalali(gy, gm, gd) {
        var g_d_m = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334];
        var jy;
        if (gy > 1600) { jy = 979; gy -= 1600; } else { jy = 0; gy -= 621; }
        var gy2 = (gm > 2) ? (gy + 1) : gy;
        var days = (365 * gy) + Math.floor((gy2 + 3) / 4) - Math.floor((gy2 + 99) / 100)
            + Math.floor((gy2 + 399) / 400) - 80 + gd + g_d_m[gm - 1];
        jy += 33 * Math.floor(days / 12053); days %= 12053;
        jy += 4 * Math.floor(days / 1461); days %= 1461;
        if (days > 365) { jy += Math.floor((days - 1) / 365); days = (days - 1) % 365; }
        var jm = (days < 186) ? 1 + Math.floor(days / 31) : 7 + Math.floor((days - 186) / 30);
        var jd = 1 + ((days < 186) ? (days % 31) : ((days - 186) % 30));
        return [jy, jm, jd];
    }
    function pad2(n) { return (n < 10 ? '0' : '') + n; }
    function fmtDateTime(ms) {
        if (!ms) { return '\u2014'; }
        var y, mo, da, hh, mi;
        if (TZ_OFFSET_MIN !== null) {
            // Render in the effective (server/Jira user) tz by shifting to that
            // offset and reading UTC components — independent of the browser tz.
            var s = new Date(Number(ms) + TZ_OFFSET_MIN * 60000);
            y = s.getUTCFullYear(); mo = s.getUTCMonth() + 1; da = s.getUTCDate();
            hh = pad2(s.getUTCHours()); mi = pad2(s.getUTCMinutes());
        } else {
            var d = new Date(Number(ms));
            y = d.getFullYear(); mo = d.getMonth() + 1; da = d.getDate();
            hh = pad2(d.getHours()); mi = pad2(d.getMinutes());
        }
        var datePart;
        if (CAL === 'jalali') {
            var j = toJalali(y, mo, da);
            datePart = j[0] + '/' + pad2(j[1]) + '/' + pad2(j[2]);
        } else {
            datePart = y + '/' + pad2(mo) + '/' + pad2(da);
        }
        return faNum(datePart + ' ' + hh + ':' + mi);
    }
    function fmtMinuteOfDay(min) {
        var h = Math.floor(min / 60), m = min % 60;
        return faNum(pad2(h) + ':' + pad2(m));
    }
    function fmtInterval(mins) {
        if (!mins) { return '\u2014'; }
        if (mins % 60 === 0) {
            return faNum(mins / 60) + (LANG === 'fa' ? ' \u0633\u0627\u0639\u062a' : 'h');
        }
        return faNum(mins) + (LANG === 'fa' ? ' \u062f\u0642\u06cc\u0642\u0647' : 'm');
    }
    function esc(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    // ----- REST -------------------------------------------------------------
    function req(method, path, body, cb) {
        var xhr = new XMLHttpRequest();
        xhr.open(method, BASE + path, true);
        xhr.setRequestHeader('Accept', 'application/json');
        if (body) { xhr.setRequestHeader('Content-Type', 'application/json'); }
        xhr.setRequestHeader('X-Atlassian-Token', 'no-check');
        xhr.onreadystatechange = function () {
            if (xhr.readyState !== 4) { return; }
            var data = null;
            try { data = JSON.parse(xhr.responseText); } catch (e) { data = null; }
            cb(xhr.status, data);
        };
        xhr.send(body ? JSON.stringify(body) : null);
    }

    // Multipart POST for attachments (browser sets the Content-Type + boundary).
    function reqForm(path, formData, cb) {
        var xhr = new XMLHttpRequest();
        xhr.open('POST', BASE + path, true);
        xhr.setRequestHeader('Accept', 'application/json');
        xhr.setRequestHeader('X-Atlassian-Token', 'no-check');
        xhr.onreadystatechange = function () {
            if (xhr.readyState !== 4) { return; }
            var data = null;
            try { data = JSON.parse(xhr.responseText); } catch (e) { data = null; }
            cb(xhr.status, data);
        };
        xhr.send(formData);
    }

    // ----- rendering --------------------------------------------------------
    function render(root, data) {
        var issueKey = root.getAttribute('data-issue-key');
        if (!data || data.featureEnabled === false) {
            root.innerHTML = '';
            return;
        }
        if (typeof data.tzOffsetMinutes === 'number') {
            TZ_OFFSET_MIN = data.tzOffsetMinutes;
        }
        var html = '';
        if (!data.configured) {
            // Graceful state: no follow-up level / fields. Show a clear warning,
            // still list existing report comments, and (if the admin allows it)
            // still let the user submit — the report stays a plain #گزارش comment.
            var notice = data.statusCode === 'NO_FOLLOW_UP_LEVEL' ? t('noFollowUpLevel') : t('notConfiguredEnv');
            html += '<div class="jim-report-notice">' + esc(notice) + '</div>';
            html += '<div class="jim-report-row jim-report-nc">' + esc(t('complianceUnavailable')) + '</div>';
            html += '<div class="jim-report-row">' + esc(t('totalReports')(data.rawCount)) + '</div>';
        } else {
            // summary
            html += '<div class="jim-report-summary">';
            html += '<div class="jim-report-row jim-report-row-primary">' + esc(t('submitted')(data.validCount, data.requiredCount)) + '</div>';
            var complClass = data.compliance === 'overdue' ? 'jim-report-overdue'
                : (data.compliance === 'on_track' ? 'jim-report-ontrack' : 'jim-report-nc');
            var complTxt = data.compliance === 'overdue' ? t('overdue')
                : (data.compliance === 'on_track' ? t('onTrack') : t('notConfigured'));
            html += '<div class="jim-report-row ' + complClass + '">' + esc(complTxt) + '</div>';
            html += '<div class="jim-report-row">' + esc(t('totalReports')(data.rawCount)) + '</div>';
            html += '<div class="jim-report-details">';
            html += detail(t('level'), data.levelLabelFa && LANG === 'fa' ? data.levelLabelFa : (data.levelText || '\u2014'));
            html += detail(t('windowLbl'), fmtMinuteOfDay(data.windowStartMinute) + ' \u2013 ' + fmtMinuteOfDay(data.windowEndMinute));
            if (data.countMode === 'fixed_minimum') {
                html += detail(t('minRequiredUntilEnd'), faNum(data.requiredCount));
                html += detail(t('reportingDeadline'), deadlineText(data.activeUntil));
            } else {
                html += detail(t('interval'), fmtInterval(data.intervalMinutes));
            }
            html += detail(t('outOfWindow'), faNum(data.outOfWindowCount));
            html += detail(t('missed'), faNum(data.missedCount));
            html += detail(t('created'), fmtDateTime(data.issueCreatedMs));
            if (data.nextExpectedMs) { html += detail(t('nextExpected'), fmtDateTime(data.nextExpectedMs)); }
            if (data.dueMissing && (data.activeUntil === 'due' || data.countMode === 'cumulative_due')) {
                html += '<div class="jim-report-detail jim-report-warn">' + esc(t('dueMissing')) + '</div>';
            }
            html += '</div>'; // details
            html += '</div>'; // summary
        }

        // submit
        if (data.canComment && data.submitActive) {
            html += '<div class="jim-report-actions">'
                + '<button type="button" class="aui-button aui-button-primary jim-report-submit-btn">' + esc(t('submit')) + '</button>'
                + '</div>'
                + '<div class="jim-report-composer" hidden>'
                + '<textarea class="jim-report-textarea" rows="3" placeholder="' + esc(t('writeReport')) + '"></textarea>'
                + '<div class="jim-report-composer-actions">'
                + '<label class="aui-button jim-report-attach-btn">' + esc(t('attach'))
                + '<input type="file" class="jim-report-file" style="display:none"/></label>'
                + '<span class="jim-report-file-name"></span>'
                + '<button type="button" class="aui-button jim-report-cancel">' + esc(t('cancel')) + '</button>'
                + '<button type="button" class="aui-button aui-button-primary jim-report-send">' + esc(t('send')) + '</button>'
                + '</div></div>';
        } else if (data.canComment && !data.submitActive) {
            var closedMsg = !data.configured ? t('submitDisabledNoConfig') : t('reportsAreClosed');
            html += '<div class="jim-report-closed">' + esc(closedMsg) + '</div>';
        }

        // filter
        html += '<div class="jim-report-filter">';
        var filters = [['today', t('fToday')], ['3', t('f3')], ['7', t('f7')], ['30', t('f30')], ['all', t('fAll')]];
        for (var i = 0; i < filters.length; i++) {
            var active = (root._filter || 'all') === filters[i][0] ? ' is-active' : '';
            html += '<button type="button" class="jim-report-filter-btn' + active + '" data-filter="' + filters[i][0] + '">' + esc(filters[i][1]) + '</button>';
        }
        html += '</div>';

        // sort
        var sortVal = root._sort || 'newest';
        html += '<div class="jim-report-filter jim-report-sort">';
        var sorts = [['newest', t('newestFirst')], ['oldest', t('oldestFirst')]];
        for (var si = 0; si < sorts.length; si++) {
            var sactive = sortVal === sorts[si][0] ? ' is-active' : '';
            html += '<button type="button" class="jim-report-filter-btn jim-report-sort-btn' + sactive + '" data-sort="' + sorts[si][0] + '">' + esc(sorts[si][1]) + '</button>';
        }
        html += '</div>';

        // list
        var items = sortItems(filterItems(data.items || [], root._filter || 'all'), sortVal);
        if ((root._filter || 'all') !== 'all' && items.length !== (data.items || []).length) {
            html += '<div class="jim-report-filter-note">' + esc(t('filterNote')) + '</div>';
        }
        html += '<div class="jim-report-list">';
        if (!items.length) {
            html += '<div class="jim-report-empty">' + esc(t('noReports')) + '</div>';
        } else {
            for (var k = 0; k < items.length; k++) {
                html += renderItem(items[k], data.canComment);
            }
        }
        html += '</div>';

        root.innerHTML = html;
        bind(root, data, issueKey);
    }

    function detail(label, value) {
        return '<div class="jim-report-detail"><span class="jim-report-detail-label">' + esc(label)
            + '</span><span class="jim-report-detail-value">' + esc(value) + '</span></div>';
    }

    function statusBadge(status) {
        if (status === 0) { return '<span class="jim-report-badge jim-report-badge-valid">' + esc(t('stValid')) + '</span>'; }
        if (status === 1) { return '<span class="jim-report-badge jim-report-badge-dup">' + esc(t('stDup')) + '</span>'; }
        return '<span class="jim-report-badge jim-report-badge-out">' + esc(t('stOut')) + '</span>';
    }

    function renderItem(item, canComment) {
        var author = (item.author && item.author.displayName) || '';
        var avatar = (item.author && item.author.avatarUrl) || '';
        var bodyHtml = item.renderedBody || ('<p>' + esc(item.body || '') + '</p>');
        var h = '<div class="jim-report-item" data-comment-id="' + esc(item.id) + '">';
        h += '<div class="jim-report-item-head">';
        if (avatar) { h += '<img class="jim-report-avatar" src="' + esc(avatar) + '" alt=""/>'; }
        h += '<span class="jim-report-author">' + esc(author) + '</span>';
        h += '<span class="jim-report-time">' + fmtDateTime(item.createdMs) + '</span>';
        h += statusBadge(item.status);
        h += '</div>';
        h += '<div class="jim-report-body">' + bodyHtml + '</div>';
        h += renderReactionBar(item);
        if (canComment) {
            h += '<div class="jim-report-item-actions"><button type="button" class="jim-report-reply-btn">' + esc(t('reply')) + '</button></div>';
            h += '<div class="jim-report-reply-composer" hidden>'
                + '<textarea class="jim-report-reply-textarea" rows="2" placeholder="' + esc(t('writeReply')) + '"></textarea>'
                + '<div class="jim-report-composer-actions">'
                + '<label class="aui-button jim-report-attach-btn">' + esc(t('attach'))
                + '<input type="file" class="jim-report-reply-file" style="display:none"/></label>'
                + '<span class="jim-report-reply-file-name"></span>'
                + '<button type="button" class="aui-button jim-report-reply-cancel">' + esc(t('cancel')) + '</button>'
                + '<button type="button" class="aui-button aui-button-primary jim-report-reply-send">' + esc(t('send')) + '</button>'
                + '</div></div>';
        }
        h += '</div>';
        return h;
    }

    function renderReactionBar(item) {
        var rs = item.reactions || [];
        var h = '<div class="jim-report-reactions" data-comment-id="' + esc(item.id) + '">';
        for (var i = 0; i < rs.length; i++) {
            h += '<button type="button" class="jim-report-react-chip' + (rs[i].mine ? ' mine' : '')
                + '" data-emoji="' + esc(rs[i].emoji) + '">' + esc(rs[i].emoji) + ' ' + faNum(rs[i].count) + '</button>';
        }
        h += '<span class="jim-report-react-addwrap">'
            + '<button type="button" class="jim-report-react-add" title="' + esc(t('addReaction')) + '">\u263A+</button>'
            + '<span class="jim-report-react-menu" hidden>';
        for (var p = 0; p < REACTION_PRESETS.length; p++) {
            h += '<button type="button" class="jim-report-react-preset" data-emoji="' + esc(REACTION_PRESETS[p]) + '">' + esc(REACTION_PRESETS[p]) + '</button>';
        }
        h += '</span></span></div>';
        return h;
    }

    function filterItems(items, filter) {
        if (filter === 'all') { return items; }
        var now = Date.now();
        var span = filter === 'today' ? dayStart(now)
            : (filter === '3' ? now - 3 * 86400000
                : (filter === '7' ? now - 7 * 86400000
                    : now - 30 * 86400000));
        return items.filter(function (it) { return Number(it.createdMs) >= span; });
    }
    function dayStart(ms) { var d = new Date(ms); d.setHours(0, 0, 0, 0); return d.getTime(); }

    // Sort by raw created timestamp (comment id as a stable tie-breaker) — never
    // by the displayed/Jalali date text.
    function sortItems(items, sortVal) {
        var arr = items.slice();
        arr.sort(function (a, b) {
            var av = Number(a.createdMs) || 0;
            var bv = Number(b.createdMs) || 0;
            if (av !== bv) { return sortVal === 'oldest' ? av - bv : bv - av; }
            return sortVal === 'oldest' ? (a.id - b.id) : (b.id - a.id);
        });
        return arr;
    }

    // ----- events -----------------------------------------------------------
    function bind(root, data, issueKey) {
        var submitBtn = root.querySelector('.jim-report-submit-btn');
        var composer = root.querySelector('.jim-report-composer');
        if (submitBtn && composer) {
            submitBtn.addEventListener('click', function () {
                composer.hidden = !composer.hidden;
                if (!composer.hidden) { composer.querySelector('textarea').focus(); }
            });
            composer.querySelector('.jim-report-cancel').addEventListener('click', function () { composer.hidden = true; });
            var fileInput = composer.querySelector('.jim-report-file');
            var fileName = composer.querySelector('.jim-report-file-name');
            if (fileInput && fileName) {
                fileInput.addEventListener('change', function () {
                    fileName.textContent = fileInput.files && fileInput.files[0] ? fileInput.files[0].name : '';
                });
            }
            composer.querySelector('.jim-report-send').addEventListener('click', function () {
                var ta = composer.querySelector('textarea');
                var text = (ta.value || '').trim();
                var file = fileInput && fileInput.files && fileInput.files[0];
                if (!text && !file) { return; }
                setBusy(root, true);
                var done = function (status, resp) {
                    setBusy(root, false);
                    if (status === 200 && resp) { render(root, resp); }
                    else { alert((resp && resp.message) || 'Failed to submit report'); }
                };
                if (file) {
                    var fd = new FormData();
                    fd.append('file', file);
                    fd.append('body', text);
                    reqForm('/' + encodeURIComponent(issueKey) + '/submit', fd, done);
                } else {
                    req('POST', '/' + encodeURIComponent(issueKey) + '/submit', { text: text }, done);
                }
            });
        }
        var filterBtns = root.querySelectorAll('.jim-report-filter-btn[data-filter]');
        for (var i = 0; i < filterBtns.length; i++) {
            filterBtns[i].addEventListener('click', function () {
                root._filter = this.getAttribute('data-filter');
                render(root, data);
            });
        }
        var sortBtns = root.querySelectorAll('.jim-report-sort-btn[data-sort]');
        for (var sj = 0; sj < sortBtns.length; sj++) {
            sortBtns[sj].addEventListener('click', function () {
                root._sort = this.getAttribute('data-sort');
                render(root, data);
            });
        }
        var items = root.querySelectorAll('.jim-report-item');
        for (var k = 0; k < items.length; k++) {
            (function (itemEl) {
                var cid = itemEl.getAttribute('data-comment-id');
                // Reactions (available to any viewer; server re-checks browse).
                bindReactions(root, data, issueKey, itemEl, cid);
                var replyBtn = itemEl.querySelector('.jim-report-reply-btn');
                var rc = itemEl.querySelector('.jim-report-reply-composer');
                if (!replyBtn || !rc) { return; }
                replyBtn.addEventListener('click', function () {
                    rc.hidden = !rc.hidden;
                    if (!rc.hidden) { rc.querySelector('textarea').focus(); }
                });
                rc.querySelector('.jim-report-reply-cancel').addEventListener('click', function () { rc.hidden = true; });
                var rFile = rc.querySelector('.jim-report-reply-file');
                var rFileName = rc.querySelector('.jim-report-reply-file-name');
                if (rFile && rFileName) {
                    rFile.addEventListener('change', function () {
                        rFileName.textContent = rFile.files && rFile.files[0] ? rFile.files[0].name : '';
                    });
                }
                rc.querySelector('.jim-report-reply-send').addEventListener('click', function () {
                    var text = (rc.querySelector('textarea').value || '').trim();
                    var file = rFile && rFile.files && rFile.files[0];
                    if (!text && !file) { return; }
                    setBusy(root, true);
                    var done = function (status, resp) {
                        setBusy(root, false);
                        if (status === 200 && resp) { render(root, resp); }
                        else { alert((resp && resp.message) || 'Failed to post reply'); }
                    };
                    if (file) {
                        var fd = new FormData();
                        fd.append('file', file);
                        fd.append('body', text);
                        reqForm('/' + encodeURIComponent(issueKey) + '/reply/' + encodeURIComponent(cid), fd, done);
                    } else {
                        req('POST', '/' + encodeURIComponent(issueKey) + '/reply/' + encodeURIComponent(cid), { text: text }, done);
                    }
                });
            })(items[k]);
        }
    }

    function bindReactions(root, data, issueKey, itemEl, cid) {
        function toggle(emoji) {
            setBusy(root, true);
            req('POST', '/' + encodeURIComponent(issueKey) + '/react/' + encodeURIComponent(cid), { emoji: emoji }, function (status, resp) {
                setBusy(root, false);
                if (status === 200 && resp && resp.reactions) {
                    // Update this item's reactions in state and re-render.
                    var list = (data && data.items) || [];
                    for (var i = 0; i < list.length; i++) {
                        if (String(list[i].id) === String(cid)) { list[i].reactions = resp.reactions; break; }
                    }
                    render(root, data);
                } else {
                    alert((resp && resp.message) || 'Failed to react');
                }
            });
        }
        var chips = itemEl.querySelectorAll('.jim-report-react-chip');
        for (var i = 0; i < chips.length; i++) {
            (function (chip) {
                chip.addEventListener('click', function () { toggle(chip.getAttribute('data-emoji')); });
            })(chips[i]);
        }
        var addBtn = itemEl.querySelector('.jim-report-react-add');
        var menu = itemEl.querySelector('.jim-report-react-menu');
        if (addBtn && menu) {
            addBtn.addEventListener('click', function () { menu.hidden = !menu.hidden; });
            var presets = menu.querySelectorAll('.jim-report-react-preset');
            for (var p = 0; p < presets.length; p++) {
                (function (b) {
                    b.addEventListener('click', function () { menu.hidden = true; toggle(b.getAttribute('data-emoji')); });
                })(presets[p]);
            }
        }
    }
    function setBusy(root, busy) { root.classList.toggle('jim-report-busy', !!busy); }

    // ----- init -------------------------------------------------------------
    // Idempotent per (element, issueKey): a panel reused for a DIFFERENT issue
    // (Issue Navigator switching) re-initializes so the tab never shows stale
    // data from the previously selected issue; a panel already showing THIS
    // issue is left untouched (no duplicate API calls / handlers).
    function initOne(root) {
        var issueKey = root.getAttribute('data-issue-key');
        if (!issueKey) { return; }
        if (root._jimReportKey === issueKey) { return; }
        root._jimReportKey = issueKey;
        root.setAttribute('data-report-initialized', 'true');
        LANG = root.getAttribute('data-effective-lang') === 'fa' ? 'fa' : 'en';
        CAL = root.getAttribute('data-effective-calendar') === 'jalali' ? 'jalali' : 'gregorian';
        root._filter = 'all';
        root._sort = 'newest';
        loadReport(root, issueKey);
    }
    function loadReport(root, issueKey) {
        root.innerHTML = '<div class="jim-report-loading">\u2026</div>';
        req('GET', '/' + encodeURIComponent(issueKey), null, function (status, data) {
            // Guard against a late response after the panel was reused for
            // another issue (navigator switch): only render if still current.
            if (root._jimReportKey !== issueKey) { return; }
            if (status === 200) { render(root, data); }
            else if (status === 404) { root.innerHTML = ''; }
            else { renderLoadError(root, issueKey, data && data.message); }
        });
    }
    function renderLoadError(root, issueKey, msg) {
        var notice = document.createElement('div');
        notice.className = 'jim-report-notice jim-report-error';
        notice.appendChild(document.createTextNode(
            msg || (LANG === 'fa' ? '\u062e\u0637\u0627 \u062f\u0631 \u0628\u0627\u0631\u06af\u0630\u0627\u0631\u06cc \u06af\u0632\u0627\u0631\u0634' : 'Unable to load report')));
        var retry = document.createElement('button');
        retry.type = 'button';
        retry.className = 'aui-button jim-report-retry';
        retry.textContent = (LANG === 'fa' ? '\u062a\u0644\u0627\u0634 \u0645\u062c\u062f\u062f' : 'Retry');
        retry.addEventListener('click', function () { loadReport(root, issueKey); });
        notice.appendChild(retry);
        root.innerHTML = '';
        root.appendChild(notice);
    }
    function scan() {
        var nodes = document.querySelectorAll('#jim-report-app, .jim-report-app');
        for (var i = 0; i < nodes.length; i++) { initOne(nodes[i]); }
    }
    // Primary lifecycle hooks: Jira KickAss content events fire when the issue
    // view AJAX-loads a tab and when the Issue Navigator swaps the selected
    // issue's detail panel — the canonical signal for "new content is present".
    function bootstrap() {
        scan();
        try {
            if (window.JIRA && JIRA.bind && JIRA.Events && JIRA.Events.NEW_CONTENT_ADDED) {
                JIRA.bind(JIRA.Events.NEW_CONTENT_ADDED, scan);
            }
            if (window.AJS && AJS.$) { AJS.$(document).on('ajaxComplete', scan); }
        } catch (e) { /* ignore */ }
        // Debounced MutationObserver fallback for surfaces that do not emit the
        // Jira events. Debounced so it is never a busy loop.
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
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', bootstrap);
    } else {
        bootstrap();
    }
})();
