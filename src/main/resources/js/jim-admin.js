/* CorbitChat admin console (vanilla JS, no frameworks). */
(function () {
    'use strict';

    var BASE = '/rest/jim/1.0/admin';

    function contextPath() {
        if (window.AJS && typeof window.AJS.contextPath === 'function') {
            return window.AJS.contextPath();
        }
        var meta = document.querySelector('meta[name="ajs-context-path"]');
        return meta ? meta.getAttribute('content') : '';
    }

    function request(method, path, body) {
        return new Promise(function (resolve, reject) {
            var xhr = new XMLHttpRequest();
            xhr.open(method, contextPath() + BASE + path, true);
            xhr.setRequestHeader('Accept', 'application/json');
            // Jira's XSRF filter rejects same-origin PUT/POST without this
            // header; without it every admin save returns 403 Forbidden.
            xhr.setRequestHeader('X-Atlassian-Token', 'no-check');
            if (body !== undefined) {
                xhr.setRequestHeader('Content-Type', 'application/json');
            }
            xhr.onreadystatechange = function () {
                if (xhr.readyState !== 4) {
                    return;
                }
                var parsed = null;
                try {
                    parsed = xhr.responseText ? JSON.parse(xhr.responseText) : null;
                } catch (e) {
                    parsed = null;
                }
                if (xhr.status >= 200 && xhr.status < 300) {
                    resolve(parsed);
                } else {
                    var message = parsed && parsed.message ? parsed.message : 'Request failed (' + xhr.status + ')';
                    reject(new Error(message));
                }
            };
            xhr.send(body !== undefined ? JSON.stringify(body) : null);
        });
    }

    /** GET against any Jira REST path (used for the native user/group pickers). */
    function jiraGet(path) {
        return new Promise(function (resolve, reject) {
            var xhr = new XMLHttpRequest();
            xhr.open('GET', contextPath() + path, true);
            xhr.setRequestHeader('Accept', 'application/json');
            xhr.onreadystatechange = function () {
                if (xhr.readyState !== 4) {
                    return;
                }
                if (xhr.status >= 200 && xhr.status < 300) {
                    try {
                        resolve(xhr.responseText ? JSON.parse(xhr.responseText) : null);
                    } catch (e) {
                        reject(new Error('Invalid response'));
                    }
                } else {
                    reject(new Error('Request failed (' + xhr.status + ')'));
                }
            };
            xhr.send(null);
        });
    }

    function el(id) {
        return document.getElementById(id);
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function showMessage(text, isError) {
        var box = el('jim-admin-message');
        box.textContent = text;
        box.classList.toggle('is-error', !!isError);
        box.classList.toggle('is-success', !isError);
        box.hidden = false;
        clearTimeout(showMessage.timer);
        showMessage.timer = setTimeout(function () { box.hidden = true; }, 6000);
    }

    function formatTime(timestamp) {
        if (!timestamp) {
            return '';
        }
        var d = new Date(Number(timestamp));
        return isNaN(d.getTime()) ? '' : d.toLocaleString();
    }

    // ===== Tabs =====

    function bindTabs() {
        var tabs = document.querySelectorAll('.jim-admin-tab');
        for (var i = 0; i < tabs.length; i++) {
            tabs[i].addEventListener('click', function () {
                var name = this.getAttribute('data-tab');
                var allTabs = document.querySelectorAll('.jim-admin-tab');
                var panels = document.querySelectorAll('.jim-admin-panel');
                for (var t = 0; t < allTabs.length; t++) {
                    var active = allTabs[t].getAttribute('data-tab') === name;
                    allTabs[t].classList.toggle('is-active', active);
                    allTabs[t].setAttribute('aria-selected', active ? 'true' : 'false');
                }
                for (var p = 0; p < panels.length; p++) {
                    panels[p].classList.toggle('is-active', panels[p].getAttribute('data-panel') === name);
                }
            });
        }
    }

    // ===== Settings =====

    var SETTINGS_CHECKBOXES = ['enableWebPush', 'aggregateNotifications', 'mentionNotifications',
        'assignmentNotifications', 'attachmentsEnabled', 'imagePreviewEnabled',
        'webPersianUiDefault', 'webJalaliDatesDefault'];

    function applySettingsToForm(settings) {
        for (var i = 0; i < SETTINGS_CHECKBOXES.length; i++) {
            var key = SETTINGS_CHECKBOXES[i];
            var box = el('jim-set-' + key);
            if (box) {
                box.checked = settings[key] === true;
            }
        }
        var level = el('jim-set-notificationDetailLevel');
        if (level) {
            level.value = settings.notificationDetailLevel || 'FULL_MESSAGE';
        }
        var size = el('jim-set-maxAttachmentSizeMb');
        if (size) {
            size.value = settings.maxAttachmentSizeMb;
        }
        var ext = el('jim-set-allowedExtensions');
        if (ext) {
            ext.value = settings.allowedExtensions || '';
        }
        var jiraCal = el('jim-set-jiraWideCalendar');
        if (jiraCal) {
            jiraCal.value = settings.jiraWideJalaliEnabled === true ? 'jalali' : 'gregorian';
        }
        var radios = document.querySelectorAll('input[name="jim-chat-mode"]');
        for (var r = 0; r < radios.length; r++) {
            radios[r].checked = radios[r].value === settings.chatMode;
        }
        var brandTitle = el('jim-set-brandingTitle');
        if (brandTitle) {
            brandTitle.value = settings.brandingTitle && settings.brandingTitle !== 'CorbitChat'
                ? settings.brandingTitle
                : (settings.brandingTitle || '');
        }
        var brandLogo = el('jim-set-brandingLogoUrl');
        if (brandLogo) {
            brandLogo.value = settings.brandingLogoUrl || '';
        }
        renderBrandingPreview();
        applyReportSettingsToForm(settings);
    }

    var REPORT_LEVEL_CODES = ['normal', 'high', 'special', 'managerial'];

    // Generated metrics (key, English, Persian, expected type, Persian description).
    // Single source of truth for the mapping table; mirrors JimReportMetrics on the server.
    var REPORT_METRICS = [
        ['validReportCount', 'Valid report count', 'تعداد گزارش معتبر', 'Number', 'تعداد گزارش‌های معتبر ثبت‌شده'],
        ['requiredReportCount', 'Required report count', 'تعداد گزارش موردنیاز', 'Number', 'تعداد گزارش موردنیاز طبق زمان‌بندی'],
        ['totalRawReportCount', 'Total raw reports', 'تعداد کل گزارش‌ها', 'Number', 'تعداد کل کامنت‌های گزارش (بدون فیلتر)'],
        ['duplicateReportCount', 'Duplicate reports', 'گزارش‌های تکراری', 'Number', 'گزارش‌های تکراری در یک بازه'],
        ['outOfWindowReportCount', 'Out-of-window reports', 'گزارش‌های خارج از بازه', 'Number', 'گزارش‌های خارج از ساعت مجاز'],
        ['missedReportCount', 'Missed intervals', 'بازه‌های ازدست‌رفته', 'Number', 'بازه‌هایی که گزارش ثبت نشده'],
        ['requiredReportCountSoFar', 'Required so far', 'موردنیاز تاکنون', 'Number', 'تعداد موردنیاز تا این لحظه'],
        ['validReportCountSoFar', 'Valid so far', 'معتبر تاکنون', 'Number', 'تعداد معتبر تا این لحظه'],
        ['reportCoveragePercent', 'Coverage %', 'درصد پوشش', 'Number', 'درصد پوشش گزارش‌ها'],
        ['firstReportAt', 'First report at', 'زمان اولین گزارش', 'Date', 'زمان اولین گزارش ثبت‌شده'],
        ['lastReportAt', 'Last report at', 'زمان آخرین گزارش', 'Date', 'زمان آخرین گزارش'],
        ['lastValidReportAt', 'Last valid report at', 'زمان آخرین گزارش معتبر', 'Date', 'زمان آخرین گزارش معتبر'],
        ['nextExpectedReportAt', 'Next expected at', 'زمان گزارش بعدی', 'Date', 'زمان مورد انتظار گزارش بعدی'],
        ['reportingEndsAt', 'Reporting ends at', 'پایان مهلت گزارش', 'Date', 'زمان پایان مهلت گزارش‌دهی'],
        ['reportMetricsUpdatedAt', 'Metrics updated at', 'زمان بروزرسانی متریک', 'Date', 'آخرین زمان بروزرسانی متریک‌ها'],
        ['reportComplianceStatus', 'Compliance status', 'وضعیت انطباق', 'Text/Select', 'وضعیت کلی انطباق گزارش‌دهی'],
        ['reportingActiveState', 'Active state', 'وضعیت فعال بودن', 'Text/Select', 'فعال/غیرفعال بودن گزارش‌دهی'],
        ['reportingInactiveReason', 'Inactive reason', 'دلیل غیرفعال بودن', 'Text', 'علت غیرفعال شدن گزارش‌دهی'],
        ['effectiveFollowUpLevel', 'Effective level', 'سطح پیگیری مؤثر', 'Text/Select', 'سطح پیگیری اعمال‌شده'],
        ['effectiveReportInterval', 'Effective interval', 'فاصله مؤثر', 'Text', 'فاصله زمانی مؤثر گزارش‌دهی'],
        ['effectiveReportingWindow', 'Effective window', 'بازه مؤثر', 'Text', 'بازه ساعتی مؤثر گزارش‌دهی'],
        ['reportCountMode', 'Count mode', 'حالت شمارش', 'Text/Select', 'حالت شمارش موردنیاز'],
        ['reportLifecycleMode', 'Lifecycle mode', 'حالت چرخه', 'Text/Select', 'شرط پایان چرخه گزارش‌دهی'],
        ['lastReportCommentId', 'Last comment id', 'شناسه آخرین کامنت', 'Text', 'شناسه کامنت آخرین گزارش'],
        ['lastReportSummary', 'Last report summary', 'خلاصه آخرین گزارش', 'Text', 'خلاصه متن آخرین گزارش'],
        ['lastReporter', 'Last reporter', 'آخرین گزارش‌دهنده', 'User/Text', 'کاربر آخرین گزارش‌دهنده'],
        ['lastValidReporter', 'Last valid reporter', 'آخرین گزارش‌دهنده معتبر', 'User/Text', 'کاربر آخرین گزارش معتبر'],
        ['reportingControlState', 'Control state', 'وضعیت کنترل', 'Text/Select', 'وضعیت چرخه: فعال/بسته/…'],
        ['reportingActive', 'Reporting active', 'گزارش‌دهی فعال', 'Text', 'آیا گزارش‌دهی فعال است'],
        ['reportingCloseCondition', 'Close condition', 'شرط بسته‌شدن', 'Text', 'شرطی که گزارش‌دهی را بست'],
        ['reportingCloseReason', 'Close reason', 'دلیل بسته‌شدن', 'Text', 'توضیح دلیل بسته‌شدن'],
        ['reportingClosedAt', 'Closed at', 'زمان بسته‌شدن', 'Date', 'زمان بسته‌شدن گزارش‌دهی'],
        ['reportingStartAt', 'Reporting start at', 'زمان شروع', 'Date', 'زمان شروع گزارش‌دهی'],
        ['reportingEndAt', 'Reporting end at', 'زمان پایان', 'Date', 'زمان پایان مؤثر گزارش‌دهی'],
        ['nextReportDueAt', 'Next due at', 'موعد بعدی', 'Date', 'موعد گزارش بعدی'],
        ['currentWindowStartAt', 'Current window start', 'شروع بازه جاری', 'Date', 'زمان شروع بازه جاری'],
        ['currentWindowStatus', 'Current window status', 'وضعیت بازه جاری', 'Text/Select', 'وضعیت بازه فعلی'],
        ['reportCompliancePercent', 'Compliance %', 'درصد انطباق', 'Number', 'درصد انطباق (معتبر/موردنیاز تاکنون)'],
        ['satisfiedWindowCount', 'Satisfied windows', 'بازه‌های موفق', 'Number', 'تعداد بازه‌های دارای گزارش معتبر'],
        ['missedWindowCount', 'Missed windows', 'بازه‌های ازدست‌رفته', 'Number', 'تعداد بازه‌های بدون گزارش'],
        ['lateWindowCount', 'Late windows', 'بازه‌های با تأخیر', 'Number', 'تعداد بازه‌های دارای تأخیر'],
        ['openWindowCount', 'Open windows', 'بازه‌های باز', 'Number', 'تعداد بازه‌های باز فعلی'],
        ['futureWindowCount', 'Future windows', 'بازه‌های آینده', 'Number', 'تعداد بازه‌های آینده'],
        ['overdueOpenCount', 'Overdue open', 'باز و معوق', 'Number', 'بازه‌های باز و عقب‌افتاده'],
        ['maxLateMinutes', 'Max late minutes', 'بیشترین تأخیر (دقیقه)', 'Number', 'بیشترین تأخیر بر حسب دقیقه'],
        ['avgLateMinutes', 'Avg late minutes', 'میانگین تأخیر (دقیقه)', 'Number', 'میانگین تأخیر بر حسب دقیقه'],
        ['minutesSinceLastValidReport', 'Minutes since last valid', 'دقیقه از آخرین معتبر', 'Number', 'دقایق گذشته از آخرین گزارش معتبر'],
        ['currentlyOverdueMinutes', 'Currently overdue minutes', 'دقایق معوق فعلی', 'Number', 'دقایق عقب‌افتادگی بازه جاری'],
        ['totalRequiredReportsUntilEnd', 'Total required until end', 'تعداد کل گزارش‌های مورد نیاز تا پایان', 'Number', 'مجموع گزارش‌های موردنیاز تا پایان دوره کنترل'],
        ['currentMissedReportStreak', 'Current missed streak', 'تعداد گزارش‌های عقب‌افتاده از آخرین گزارش معتبر', 'Number', 'تعداد گزارش‌های پشت‌سرهم ازدست‌رفته از آخرین گزارش معتبر'],
        ['maxMissedReportStreak', 'Max missed streak', 'بیشترین زنجیره گزارش‌های عقب‌افتاده', 'Number', 'بیشترین تعداد گزارش‌های پشت‌سرهم ازدست‌رفته'],
        ['todayRequiredCount', 'Today required', 'موردنیاز امروز', 'Number', 'تعداد موردنیاز امروز'],
        ['todayValidCount', 'Today valid', 'معتبر امروز', 'Number', 'تعداد معتبر امروز'],
        ['todayMissedCount', 'Today missed', 'ازدست‌رفته امروز', 'Number', 'تعداد ازدست‌رفته امروز'],
        ['reportedToday', 'Reported today', 'گزارش امروز', 'Text', 'آیا امروز گزارش ثبت شده'],
        ['responsibleUser', 'Responsible user', 'کاربر مسئول', 'User/Text', 'کاربر مسئول (assignee)'],
        ['reportLevelMissing', 'Level missing', 'نبود سطح', 'Text', 'سطح پیگیری تنظیم نشده'],
        ['reportConfigError', 'Config error', 'خطای تنظیمات', 'Text', 'خطای پیکربندی گزارش'],
        ['calcDurationMs', 'Calc duration (ms)', 'مدت محاسبه (ms)', 'Number', 'مدت‌زمان آخرین محاسبه'],
        ['calculationVersion', 'Calc version', 'نسخه محاسبه', 'Number', 'نسخه الگوریتم محاسبه']
    ];

    function buildReportMetricRows() {
        var tbody = el('jim-report-metric-rows');
        if (!tbody || tbody.getAttribute('data-built') === '1') { return; }
        tbody.setAttribute('data-built', '1');
        var rows = '';
        for (var i = 0; i < REPORT_METRICS.length; i++) {
            var m = REPORT_METRICS[i];
            rows += '<tr><td><code>' + m[0] + '</code></td><td dir="rtl">' + m[2] + '</td><td>' + m[3]
                + '</td><td dir="rtl" style="font-size:12px;color:#626f86;">' + (m[4] || '')
                + '</td><td><input type="text" id="jim-set-reportMap_' + m[0]
                + '" class="text" style="max-width:200px;" placeholder="Disabled"/></td></tr>';
        }
        tbody.innerHTML = rows;
    }

    function applyReportSettingsToForm(settings) {
        var enabled = el('jim-set-reportComplianceEnabled');
        if (!enabled) {
            return; // Report Schedule tab not present in this build
        }
        buildReportMetricRows();
        enabled.checked = settings.reportComplianceEnabled === true;
        var allowWithout = el('jim-set-reportAllowWithoutConfig');
        if (allowWithout) { allowWithout.checked = settings.reportAllowWithoutConfig === true; }
        var reopen = el('jim-set-reportReopenReactivation');
        if (reopen) { reopen.checked = settings.reportReopenReactivation === true; }
        var applEnabled = el('jim-set-reportApplicabilityEnabled');
        if (applEnabled) { applEnabled.checked = settings.reportApplicabilityEnabled === true; }
        var applNonDone = el('jim-set-reportApplicabilityNonDoneOnly');
        if (applNonDone) { applNonDone.checked = settings.reportApplicabilityNonDoneOnly !== false; }
        setVal('jim-set-reportEffectiveFromDate', settings.reportEffectiveFromDate || '');
        setVal('jim-set-reportApplicabilityDateMode', settings.reportApplicabilityDateMode || 'created_on_or_after');
        setVal('jim-set-reportApplicabilityMissingDuePolicy', settings.reportApplicabilityMissingDuePolicy || 'exclude_missing_due');
        // Working calendar (board vs plugin)
        setVal('jim-set-reportWorkingCalendarSource', settings.reportWorkingCalendarSource || 'time_tracking_fallback');
        setVal('jim-set-reportDefaultBoardId', (settings.reportDefaultBoardId && settings.reportDefaultBoardId > 0) ? settings.reportDefaultBoardId : '');
        setVal('jim-set-reportProjectBoardMapping', settings.reportProjectBoardMapping || '');
        setVal('jim-set-reportWorkingDaysCsv', settings.reportWorkingDaysCsv || '');
        setVal('jim-set-reportHolidaysCsv', settings.reportHolidaysCsv || '');
        var wdEnabled = el('jim-set-reportWorkingDaysEnabled');
        if (wdEnabled) { wdEnabled.checked = settings.reportWorkingDaysEnabled !== false; }
        for (var mi = 0; mi < REPORT_METRICS.length; mi++) {
            var mk = REPORT_METRICS[mi][0];
            setVal('jim-set-reportMap_' + mk, settings['reportMap_' + mk] || '');
        }
        setVal('jim-set-reportMarker', settings.reportMarker || '');
        setVal('jim-set-reportWindowStart', settings.reportWindowStart || '08:00');
        setVal('jim-set-reportWindowEnd', settings.reportWindowEnd || '21:00');
        setVal('jim-set-reportTimezone', settings.reportTimezone || '');
        setVal('jim-set-reportLevelFieldId', settings.reportLevelFieldId || '');
        setVal('jim-set-reportValidCountFieldId', settings.reportValidCountFieldId || '');
        setVal('jim-set-reportRequiredCountFieldId', settings.reportRequiredCountFieldId || '');
        for (var i = 0; i < REPORT_LEVEL_CODES.length; i++) {
            var c = REPORT_LEVEL_CODES[i];
            setVal('jim-set-reportInterval_' + c, settings['reportInterval_' + c] != null ? settings['reportInterval_' + c] : 0);
            setVal('jim-set-reportIntervalUnit_' + c, settings['reportIntervalUnit_' + c] || 'hours');
            setVal('jim-set-reportStart_' + c, settings['reportStart_' + c] || '');
            setVal('jim-set-reportEnd_' + c, settings['reportEnd_' + c] || '');
            setVal('jim-set-reportActiveUntil_' + c, settings['reportActiveUntil_' + c] || 'earlier');
            setVal('jim-set-reportCountMode_' + c, settings['reportCountMode_' + c] || 'daily');
            setVal('jim-set-reportMinCount_' + c, settings['reportMinCount_' + c] != null ? settings['reportMinCount_' + c] : 1);
        }
    }

    function setVal(id, value) {
        var e = el(id);
        if (e) {
            e.value = value;
        }
    }

    function renderBrandingPreview() {
        var titleInput = el('jim-set-brandingTitle');
        var logoInput = el('jim-set-brandingLogoUrl');
        var titleEl = el('jim-branding-preview-title');
        var logoEl = el('jim-branding-preview-logo');
        var fallbackEl = el('jim-branding-preview-logo-fallback');
        if (!titleEl || !logoEl || !fallbackEl) {
            return;
        }
        var titleValue = (titleInput && titleInput.value || '').trim();
        titleEl.textContent = titleValue || 'CorbitChat';
        var logoValue = (logoInput && logoInput.value || '').trim();
        if (logoValue) {
            var lower = logoValue.toLowerCase();
            var safeScheme = logoValue.charAt(0) === '/'
                || lower.indexOf('http://') === 0
                || lower.indexOf('https://') === 0
                || lower.indexOf('data:image/') === 0;
            if (safeScheme) {
                logoEl.src = logoValue;
                logoEl.hidden = false;
                fallbackEl.hidden = true;
                logoEl.onerror = function () {
                    logoEl.hidden = true;
                    fallbackEl.hidden = false;
                };
                return;
            }
        }
        logoEl.removeAttribute('src');
        logoEl.hidden = true;
        fallbackEl.hidden = false;
    }

    function loadSettings() {
        return request('GET', '/settings').then(applySettingsToForm).catch(function (error) {
            showMessage(error.message, true);
        });
    }

    function saveSettings(changes, successText) {
        return request('PUT', '/settings', changes).then(function (settings) {
            applySettingsToForm(settings);
            showMessage(successText || 'Settings saved.');
            loadOverview();
        }).catch(function (error) {
            showMessage(error.message, true);
        });
    }

    function bindSettingsActions() {
        el('jim-save-chat-mode').addEventListener('click', function () {
            var selected = document.querySelector('input[name="jim-chat-mode"]:checked');
            if (!selected) {
                showMessage('Select a chat mode first.', true);
                return;
            }
            saveSettings({ chatMode: selected.value }, 'Chat mode saved.');
        });

        el('jim-save-notifications').addEventListener('click', function () {
            saveSettings({
                enableWebPush: el('jim-set-enableWebPush').checked,
                notificationDetailLevel: el('jim-set-notificationDetailLevel').value,
                aggregateNotifications: el('jim-set-aggregateNotifications').checked,
                mentionNotifications: el('jim-set-mentionNotifications').checked,
                assignmentNotifications: el('jim-set-assignmentNotifications').checked
            }, 'Notification settings saved.');
        });

        var saveLoc = el('jim-save-localization');
        if (saveLoc) {
            saveLoc.addEventListener('click', function () {
                saveSettings({
                    webPersianUiDefault: el('jim-set-webPersianUiDefault').checked,
                    webJalaliDatesDefault: el('jim-set-webJalaliDatesDefault').checked
                }, 'Localization settings saved.');
            });
        }

        var saveReport = el('jim-save-report-schedule');
        if (saveReport) {
            saveReport.addEventListener('click', function () {
                var changes = {
                    reportComplianceEnabled: el('jim-set-reportComplianceEnabled').checked,
                    reportMarker: (el('jim-set-reportMarker').value || '').trim(),
                    reportWindowStart: (el('jim-set-reportWindowStart').value || '').trim(),
                    reportWindowEnd: (el('jim-set-reportWindowEnd').value || '').trim(),
                    reportTimezone: (el('jim-set-reportTimezone').value || '').trim(),
                    reportLevelFieldId: (el('jim-set-reportLevelFieldId').value || '').trim(),
                    reportValidCountFieldId: (el('jim-set-reportValidCountFieldId').value || '').trim(),
                    reportRequiredCountFieldId: (el('jim-set-reportRequiredCountFieldId').value || '').trim()
                };
                for (var i = 0; i < REPORT_LEVEL_CODES.length; i++) {
                    var c = REPORT_LEVEL_CODES[i];
                    changes['reportInterval_' + c] = (el('jim-set-reportInterval_' + c).value || '0').trim();
                    changes['reportIntervalUnit_' + c] = el('jim-set-reportIntervalUnit_' + c).value;
                    changes['reportStart_' + c] = (el('jim-set-reportStart_' + c).value || '').trim();
                    changes['reportEnd_' + c] = (el('jim-set-reportEnd_' + c).value || '').trim();
                    changes['reportActiveUntil_' + c] = el('jim-set-reportActiveUntil_' + c).value;
                    changes['reportCountMode_' + c] = el('jim-set-reportCountMode_' + c).value;
                    changes['reportMinCount_' + c] = (el('jim-set-reportMinCount_' + c).value || '1').trim();
                }
                var allowWithout = el('jim-set-reportAllowWithoutConfig');
                if (allowWithout) { changes.reportAllowWithoutConfig = allowWithout.checked; }
                var reopen = el('jim-set-reportReopenReactivation');
                if (reopen) { changes.reportReopenReactivation = reopen.checked; }
                var applEnabled = el('jim-set-reportApplicabilityEnabled');
                if (applEnabled) {
                    changes.reportApplicabilityEnabled = applEnabled.checked;
                    changes.reportEffectiveFromDate = (el('jim-set-reportEffectiveFromDate').value || '').trim();
                    changes.reportApplicabilityDateMode = el('jim-set-reportApplicabilityDateMode').value;
                    changes.reportApplicabilityNonDoneOnly = el('jim-set-reportApplicabilityNonDoneOnly').checked;
                    changes.reportApplicabilityMissingDuePolicy = el('jim-set-reportApplicabilityMissingDuePolicy').value;
                }
                for (var mi = 0; mi < REPORT_METRICS.length; mi++) {
                    var mk = REPORT_METRICS[mi][0];
                    var inp = el('jim-set-reportMap_' + mk);
                    if (inp) { changes['reportMap_' + mk] = (inp.value || '').trim(); }
                }
                // Working calendar settings
                var calSrc = el('jim-set-reportWorkingCalendarSource');
                if (calSrc) { changes.reportWorkingCalendarSource = calSrc.value; }
                var defBoard = el('jim-set-reportDefaultBoardId');
                if (defBoard) { changes.reportDefaultBoardId = (defBoard.value || '').trim(); }
                var projMap = el('jim-set-reportProjectBoardMapping');
                if (projMap) { changes.reportProjectBoardMapping = (projMap.value || '').trim(); }
                var wdCsv = el('jim-set-reportWorkingDaysCsv');
                if (wdCsv) { changes.reportWorkingDaysCsv = (wdCsv.value || '').trim(); }
                var holCsv = el('jim-set-reportHolidaysCsv');
                if (holCsv) { changes.reportHolidaysCsv = (holCsv.value || '').trim(); }
                var wdEn = el('jim-set-reportWorkingDaysEnabled');
                if (wdEn) { changes.reportWorkingDaysEnabled = wdEn.checked; }
                saveSettings(changes, 'Report schedule saved.');
            });
        }

        var calDiag = el('jim-report-calendar-diag');
        if (calDiag) {
            calDiag.addEventListener('click', function () {
                var out = el('jim-report-calendar-diag-out');
                if (out) { out.textContent = 'در حال خواندن… / reading…'; }
                request('GET', '/reporting/diagnostics').then(function (res) {
                    if (!out) { return; }
                    var w = (res && res.workingDays) || {};
                    var lines = [];
                    lines.push('فعال / enabled: ' + w.enabled);
                    lines.push('منبع انتخابی / selected source: ' + w.selectedSource);
                    lines.push('منبع مؤثر / resolved source: ' + w.resolvedSource);
                    lines.push('روزهای کاری (1=یکشنبه..7=شنبه): ' + JSON.stringify(w.workingDaysOfWeek));
                    lines.push('تعداد تعطیلات / holidays: ' + w.holidayCount);
                    lines.push('برد / board: ' + (w.boardId != null ? w.boardId : '-') + (w.boardName ? ' (' + w.boardName + ')' : ''));
                    lines.push('منطقه زمانی برد / board tz: ' + (w.boardTimezone || '-'));
                    lines.push('دلیل fallback: ' + (w.fallbackReason || '-'));
                    lines.push('قابلیت خواندن برد / board readable: ' + w.boardCalendarReadable);
                    out.textContent = lines.join('\n');
                }).catch(function (err) {
                    if (out) { out.textContent = 'خطا / error: ' + (err && err.message ? err.message : err); }
                });
            });
        }

        var applPreview = el('jim-report-applicability-preview');
        if (applPreview) {
            applPreview.addEventListener('click', function () {
                var out = el('jim-report-applicability-impact');
                var q = '?effectiveFrom=' + encodeURIComponent((el('jim-set-reportEffectiveFromDate').value || '').trim())
                    + '&mode=' + encodeURIComponent(el('jim-set-reportApplicabilityDateMode').value)
                    + '&nonDoneOnly=' + (el('jim-set-reportApplicabilityNonDoneOnly').checked ? 'true' : 'false')
                    + '&missingDuePolicy=' + encodeURIComponent(el('jim-set-reportApplicabilityMissingDuePolicy').value);
                if (out) { out.textContent = 'در حال محاسبه… / computing…'; }
                request('GET', '/report-applicability/impact' + q).then(function (res) {
                    if (!out) { return; }
                    var c = res.counts || {};
                    var l = res.ledger || {};
                    var lines = [];
                    lines.push('کل تسک‌های کاندید / total candidate: ' + c.totalCandidate);
                    lines.push('مشمول / in scope: ' + c.inScope + '   |   خارج از محدوده / out of scope: ' + c.outOfScope);
                    if (c.excludedDone != null) { lines.push('خارج (Done): ' + c.excludedDone); }
                    if (c.excludedByCreatedDate != null) { lines.push('خارج (تاریخ ایجاد): ' + c.excludedByCreatedDate); }
                    if (c.excludedByDueDate != null) { lines.push('خارج (تاریخ سررسید): ' + c.excludedByDueDate); }
                    if (c.excludedMissingDue != null) { lines.push('خارج (بدون سررسید): ' + c.excludedMissingDue); }
                    lines.push('لجر ACTIVE که خارج از محدوده می‌شود: ' + (l.activeWouldBecomeOutOfScope != null ? l.activeWouldBecomeOutOfScope : '-')
                        + ' (اسکن‌شده: ' + (l.activeScanned != null ? l.activeScanned : '-') + ')');
                    lines.push('لجر CLOSED (همه سطوح): ' + (l.closedLedgerCountAllLevels != null ? l.closedLedgerCountAllLevels : '-'));
                    if (c.note) { lines.push('note: ' + c.note); }
                    out.textContent = lines.join('\n');
                }).catch(function (err) {
                    if (out) { out.textContent = 'خطا / error: ' + (err && err.message ? err.message : err); }
                });
            });
        }

        // ---- ChatGPT Workspace Agent integration ----
        (function wireWorkspaceAgent() {
            function waReq(method, path, body) {
                return new Promise(function (resolve, reject) {
                    var xhr = new XMLHttpRequest();
                    xhr.open(method, contextPath() + '/rest/jim/1.0/workspace-agent' + path, true);
                    xhr.setRequestHeader('Accept', 'application/json');
                    xhr.setRequestHeader('X-Atlassian-Token', 'no-check');
                    if (body !== undefined) { xhr.setRequestHeader('Content-Type', 'application/json'); }
                    xhr.onreadystatechange = function () {
                        if (xhr.readyState !== 4) { return; }
                        var parsed = null;
                        try { parsed = xhr.responseText ? JSON.parse(xhr.responseText) : null; } catch (e) { parsed = null; }
                        if (xhr.status >= 200 && xhr.status < 300) { resolve(parsed); }
                        else { reject(new Error(parsed && parsed.message ? parsed.message : 'Request failed (' + xhr.status + ')')); }
                    };
                    xhr.send(body !== undefined ? JSON.stringify(body) : null);
                });
            }
            var statusBox = el('jim-workspace-agent-status');
            function applyStatus(s) {
                if (!s) { return; }
                if (el('jim-set-workspaceAgentEnabled')) { el('jim-set-workspaceAgentEnabled').checked = s.enabled === true; }
                if (el('jim-set-workspaceAgentChannelId')) { el('jim-set-workspaceAgentChannelId').value = s.agentChannelConfigured ? (el('jim-set-workspaceAgentChannelId').value || '') : (el('jim-set-workspaceAgentChannelId').value || ''); }
                if (el('jim-set-workspaceAgentTenantId')) { el('jim-set-workspaceAgentTenantId').value = s.tenantId || 'default'; }
                if (el('jim-set-workspaceAgentGroupMention')) { el('jim-set-workspaceAgentGroupMention').checked = s.groupMentionMode === true; }
                if (el('jim-set-workspaceAgentProjectAllowlist')) { el('jim-set-workspaceAgentProjectAllowlist').value = s.projectAllowlist || ''; }
                if (statusBox) {
                    statusBox.textContent =
                        'enabled=' + s.enabled + ' | effective=' + s.effectiveEnabled
                        + '\ntokenConfigured=' + s.tokenConfigured + ' | channel=' + s.agentChannelConfigured + ' | triggerUrl=' + s.triggerUrlConfigured
                        + '\nassistantUser=' + s.assistantUser + ' valid=' + s.assistantUserValid
                        + '\nqueueDepth=' + s.queueDepth + ' pending=' + s.pendingCount + ' running=' + s.runningCount
                        + ' failed=' + s.failedCount + ' deadLetter=' + s.deadLetterCount + ' succeeded=' + s.succeededCount
                        + '\nlastSuccessAt=' + s.lastTriggerSuccessAt + ' lastFailureAt=' + s.lastTriggerFailureAt
                        + (s.lastFailureSummary ? '\nlastFailure=' + s.lastFailureSummary : '');
                }
            }
            function refresh() { waReq('GET', '/status').then(applyStatus).catch(function () {}); }
            if (el('jim-workspace-agent-save')) {
                el('jim-workspace-agent-save').addEventListener('click', function () {
                    waReq('PUT', '/config', {
                        enabled: el('jim-set-workspaceAgentEnabled').checked,
                        channelId: (el('jim-set-workspaceAgentChannelId').value || '').trim(),
                        triggerUrl: (el('jim-set-workspaceAgentTriggerUrl').value || '').trim(),
                        tenantId: (el('jim-set-workspaceAgentTenantId').value || '').trim(),
                        groupMentionMode: el('jim-set-workspaceAgentGroupMention').checked,
                        projectAllowlist: (el('jim-set-workspaceAgentProjectAllowlist').value || '').trim()
                    }).then(function (s) { applyStatus(s); showMessage('Workspace Agent settings saved.'); })
                      .catch(function (err) { showMessage('Error: ' + err.message, true); });
                });
            }
            if (el('jim-workspace-agent-token-save')) {
                el('jim-workspace-agent-token-save').addEventListener('click', function () {
                    var t = (el('jim-workspace-agent-token').value || '').trim();
                    if (!t) { showMessage('Enter a token first.', true); return; }
                    waReq('POST', '/token', { token: t }).then(function () {
                        el('jim-workspace-agent-token').value = '';
                        showMessage('Token saved (write-only).'); refresh();
                    }).catch(function (err) { showMessage('Error: ' + err.message, true); });
                });
            }
            if (el('jim-workspace-agent-token-revoke')) {
                el('jim-workspace-agent-token-revoke').addEventListener('click', function () {
                    waReq('DELETE', '/token').then(function () { showMessage('Token revoked.'); refresh(); })
                      .catch(function (err) { showMessage('Error: ' + err.message, true); });
                });
            }
            if (el('jim-workspace-agent-test')) {
                el('jim-workspace-agent-test').addEventListener('click', function () {
                    waReq('POST', '/test', {}).then(function (r) {
                        showMessage(r && r.ok ? 'Test trigger OK' + (r.runId ? ' (run ' + r.runId + ')' : '') : 'Test failed: ' + (r && r.error ? r.error : 'unknown'), !(r && r.ok));
                        refresh();
                    }).catch(function (err) { showMessage('Error: ' + err.message, true); });
                });
            }
            if (el('jim-workspace-agent-scan')) {
                el('jim-workspace-agent-scan').addEventListener('click', function () {
                    waReq('POST', '/scan').then(function (r) { showMessage('Fallback scan enqueued: ' + (r ? r.enqueued : '?')); refresh(); })
                      .catch(function (err) { showMessage('Error: ' + err.message, true); });
                });
            }
            refresh();
        })();

        var saveJiraDates = el('jim-save-jira-dates');
        if (saveJiraDates) {
            saveJiraDates.addEventListener('click', function () {
                var sel = el('jim-set-jiraWideCalendar');
                var jalali = !!sel && sel.value === 'jalali';
                saveSettings({
                    jiraWideJalaliEnabled: jalali
                }, 'Jira date display saved. Reload Jira pages to apply the change.');
            });
        }

        el('jim-save-attachments').addEventListener('click', function () {
            saveSettings({
                attachmentsEnabled: el('jim-set-attachmentsEnabled').checked,
                maxAttachmentSizeMb: el('jim-set-maxAttachmentSizeMb').value,
                allowedExtensions: el('jim-set-allowedExtensions').value,
                imagePreviewEnabled: el('jim-set-imagePreviewEnabled').checked
            }, 'Attachment settings saved.');
        });

        var brandTitle = el('jim-set-brandingTitle');
        var brandLogo = el('jim-set-brandingLogoUrl');
        var brandLogoFile = el('jim-branding-logo-file');
        var brandLogoClear = el('jim-branding-logo-clear');
        if (brandTitle) {
            brandTitle.addEventListener('input', renderBrandingPreview);
        }
        if (brandLogo) {
            brandLogo.addEventListener('input', renderBrandingPreview);
        }
        if (brandLogoFile) {
            brandLogoFile.addEventListener('change', function () {
                var file = brandLogoFile.files && brandLogoFile.files[0];
                if (!file) {
                    return;
                }
                if (!/^image\//.test(file.type)) {
                    showMessage('Please choose an image file (PNG, JPG, GIF, or WebP).', true);
                    brandLogoFile.value = '';
                    return;
                }
                var MAX_BYTES = 300 * 1024;
                if (file.size > MAX_BYTES) {
                    showMessage('Image is too large (max 300 KB).', true);
                    brandLogoFile.value = '';
                    return;
                }
                var reader = new FileReader();
                reader.onload = function () {
                    var dataUrl = String(reader.result || '');
                    if (dataUrl.indexOf('data:image/') !== 0) {
                        showMessage('Could not read this image file.', true);
                        return;
                    }
                    if (brandLogo) {
                        brandLogo.value = dataUrl;
                    }
                    renderBrandingPreview();
                    showMessage('Logo loaded. Click "Save branding" to apply.');
                };
                reader.onerror = function () {
                    showMessage('Could not read this image file.', true);
                };
                reader.readAsDataURL(file);
            });
        }
        if (brandLogoClear) {
            brandLogoClear.addEventListener('click', function () {
                if (brandLogoFile) { brandLogoFile.value = ''; }
                if (brandLogo) { brandLogo.value = ''; }
                renderBrandingPreview();
            });
        }
        var brandSave = el('jim-save-branding');
        if (brandSave) {
            brandSave.addEventListener('click', function () {
                saveSettings({
                    brandingTitle: (brandTitle ? brandTitle.value : '').trim(),
                    brandingLogoUrl: (brandLogo ? brandLogo.value : '').trim()
                }, 'Branding saved. Reload the chat page to see it applied.');
            });
        }
        var brandReset = el('jim-reset-branding');
        if (brandReset) {
            brandReset.addEventListener('click', function () {
                if (brandTitle) { brandTitle.value = ''; }
                if (brandLogo) { brandLogo.value = ''; }
                renderBrandingPreview();
                saveSettings({ brandingTitle: '', brandingLogoUrl: '' }, 'Branding reset to defaults.');
            });
        }

        el('jim-test-notification').addEventListener('click', function () {
            request('POST', '/test-notification').then(function (result) {
                showMessage('Test notification sent to ' + result.subscriptions + ' subscription(s). Check your browser.');
            }).catch(function (error) {
                showMessage(error.message, true);
            });
        });
    }

    // ===== Policies =====

    function renderPolicyRows(policies) {
        var tbody = el('jim-policy-rows');
        if (!policies.length) {
            tbody.innerHTML = '<tr><td colspan="9" class="jim-admin-empty">No policies defined. In restricted mode, nobody can chat until an ALLOW policy is added.</td></tr>';
            return;
        }
        var html = [];
        for (var i = 0; i < policies.length; i++) {
            var p = policies[i];
            var rowClasses = (p.enabled ? '' : 'jim-policy-disabled') +
                (editingPolicyId === p.id ? ' jim-policy-editing' : '');
            html.push('<tr class="' + rowClasses + '">' +
                '<td>' + escapeHtml(p.sourceType) + ': <strong>' + escapeHtml(p.sourceValue) + '</strong></td>' +
                '<td>' + escapeHtml(p.targetType) + (p.targetValue ? ': <strong>' + escapeHtml(p.targetValue) + '</strong>' : '') + '</td>' +
                '<td><span class="jim-policy-action jim-policy-' + (p.action === 'DENY' ? 'deny' : 'allow') + '">' + escapeHtml(p.action) + '</span></td>' +
                '<td>' + (p.canSearch ? '&#10003;' : '&#8211;') + '</td>' +
                '<td>' + (p.canStartChat ? '&#10003;' : '&#8211;') + '</td>' +
                '<td>' + (p.canReceiveChat ? '&#10003;' : '&#8211;') + '</td>' +
                '<td>' + (p.enabled ? 'Yes' : 'No') + '</td>' +
                '<td>' + escapeHtml(p.priority) + '</td>' +
                '<td>' +
                '<button type="button" class="aui-button aui-button-link" data-policy-edit="' + p.id + '">Edit</button> ' +
                '<button type="button" class="aui-button aui-button-link" data-policy-toggle="' + p.id + '" data-policy-enabled="' + p.enabled + '">' + (p.enabled ? 'Disable' : 'Enable') + '</button> ' +
                '<button type="button" class="aui-button aui-button-link jim-policy-delete" data-policy-delete="' + p.id + '">Delete</button>' +
                '</td>' +
                '</tr>');
        }
        tbody.innerHTML = html.join('');
    }

    var cachedPolicies = [];

    function loadPolicies() {
        return request('GET', '/policies').then(function (response) {
            cachedPolicies = response.policies || [];
            renderPolicyRows(cachedPolicies);
        }).catch(function (error) {
            el('jim-policy-rows').innerHTML = '<tr><td colspan="9">' + escapeHtml(error.message) + '</td></tr>';
        });
    }

    function findPolicy(id) {
        for (var i = 0; i < cachedPolicies.length; i++) {
            if (cachedPolicies[i].id === id) {
                return cachedPolicies[i];
            }
        }
        return null;
    }

    // ===== Policy edit mode (reuses the add-policy form) =====

    var editingPolicyId = null;

    /**
     * Sets a type select + picker input pair. Dispatching 'change' lets the
     * attached typeahead picker update its enabled/placeholder state (which
     * clears the input), so the value is applied afterwards.
     */
    function setTypeAndValue(selectId, inputId, type, value) {
        var select = el(selectId);
        select.value = type;
        select.dispatchEvent(new Event('change'));
        el(inputId).value = value || '';
    }

    function enterPolicyEditMode(policy) {
        editingPolicyId = policy.id;
        setTypeAndValue('jim-pol-source-type', 'jim-pol-source-value', policy.sourceType, policy.sourceValue);
        setTypeAndValue('jim-pol-target-type', 'jim-pol-target-value', policy.targetType, policy.targetValue);
        el('jim-pol-action').value = policy.action;
        el('jim-pol-can-search').checked = !!policy.canSearch;
        el('jim-pol-can-start').checked = !!policy.canStartChat;
        el('jim-pol-can-receive').checked = !!policy.canReceiveChat;
        el('jim-pol-priority').value = policy.priority;
        el('jim-policy-form-title').textContent = 'Edit policy';
        el('jim-pol-add').textContent = 'Save changes';
        el('jim-pol-cancel-edit').hidden = false;
        renderPolicyRows(cachedPolicies);
        el('jim-policy-form').scrollIntoView({ behavior: 'smooth', block: 'center' });
        el('jim-pol-source-value').focus();
    }

    function exitPolicyEditMode() {
        editingPolicyId = null;
        setTypeAndValue('jim-pol-source-type', 'jim-pol-source-value', 'USER', '');
        setTypeAndValue('jim-pol-target-type', 'jim-pol-target-value', 'ANY', '');
        el('jim-pol-action').value = 'ALLOW';
        el('jim-pol-can-search').checked = true;
        el('jim-pol-can-start').checked = true;
        el('jim-pol-can-receive').checked = true;
        el('jim-pol-priority').value = 0;
        el('jim-policy-form-title').textContent = 'Add policy';
        el('jim-pol-add').textContent = 'Add policy';
        el('jim-pol-cancel-edit').hidden = true;
        renderPolicyRows(cachedPolicies);
    }

    // ===== User/group typeahead pickers (Jira native picker REST) =====

    function searchUsers(query) {
        return jiraGet('/rest/api/2/user/picker?maxResults=10&query=' + encodeURIComponent(query))
            .then(function (response) {
                var users = (response && response.users) || [];
                var items = [];
                for (var i = 0; i < users.length; i++) {
                    items.push({
                        value: users[i].name,
                        label: users[i].displayName,
                        detail: users[i].name,
                        avatarUrl: users[i].avatarUrl || null
                    });
                }
                return items;
            });
    }

    function searchGroups(query) {
        return jiraGet('/rest/api/2/groups/picker?maxResults=10&query=' + encodeURIComponent(query))
            .then(function (response) {
                var groups = (response && response.groups) || [];
                var items = [];
                for (var i = 0; i < groups.length; i++) {
                    items.push({ value: groups[i].name, label: groups[i].name, detail: null, avatarUrl: null });
                }
                return items;
            });
    }

    /**
     * Turns a text input into a typeahead picker. The adjacent type select
     * decides whether users or groups are searched (ANY disables the input).
     */
    function attachPicker(input, typeSelect) {
        var wrapper = document.createElement('span');
        wrapper.className = 'jim-admin-picker';
        input.parentNode.insertBefore(wrapper, input);
        wrapper.appendChild(input);

        var dropdown = document.createElement('div');
        dropdown.className = 'jim-admin-picker-dropdown';
        dropdown.hidden = true;
        wrapper.appendChild(dropdown);

        var debounceTimer = null;
        var requestSeq = 0;
        input.setAttribute('autocomplete', 'off');

        function hide() {
            dropdown.hidden = true;
        }

        function applyTypeState() {
            var isAny = typeSelect.value === 'ANY';
            input.disabled = isAny;
            if (isAny) {
                input.value = '';
                hide();
            }
            input.placeholder = typeSelect.value === 'GROUP'
                ? 'Type to search groups...'
                : (isAny ? 'any user' : 'Type to search users...');
        }

        function render(items) {
            if (!items.length) {
                dropdown.innerHTML = '<div class="jim-admin-picker-empty">No matches</div>';
                dropdown.hidden = false;
                return;
            }
            var html = [];
            for (var i = 0; i < items.length; i++) {
                var item = items[i];
                html.push('<button type="button" class="jim-admin-picker-item" data-value="' + escapeHtml(item.value) + '">' +
                    (item.avatarUrl
                        ? '<img class="jim-admin-picker-avatar" src="' + escapeHtml(item.avatarUrl) + '" alt=""/>'
                        : '<span class="jim-admin-picker-avatar jim-admin-picker-avatar-group" aria-hidden="true">' +
                          (typeSelect.value === 'GROUP' ? '&#128101;' : '&#128100;') + '</span>') +
                    '<span class="jim-admin-picker-label">' + escapeHtml(item.label) + '</span>' +
                    (item.detail && item.detail !== item.label
                        ? '<span class="jim-admin-picker-detail">' + escapeHtml(item.detail) + '</span>'
                        : '') +
                    '</button>');
            }
            dropdown.innerHTML = html.join('');
            dropdown.hidden = false;
        }

        function search() {
            var query = input.value.trim();
            if (query.length < 1) {
                hide();
                return;
            }
            var seq = ++requestSeq;
            var searcher = typeSelect.value === 'GROUP' ? searchGroups : searchUsers;
            searcher(query).then(function (items) {
                if (seq === requestSeq && document.activeElement === input) {
                    render(items);
                }
            }).catch(hide);
        }

        input.addEventListener('input', function () {
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(search, 250);
        });
        input.addEventListener('focus', function () {
            if (input.value.trim()) {
                search();
            }
        });
        input.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') {
                hide();
            }
        });
        input.addEventListener('blur', function () {
            // Delay so a click on a dropdown item still registers.
            setTimeout(hide, 200);
        });
        dropdown.addEventListener('mousedown', function (event) {
            var item = event.target && event.target.closest
                ? event.target.closest('.jim-admin-picker-item')
                : null;
            if (item) {
                event.preventDefault();
                input.value = item.getAttribute('data-value');
                hide();
            }
        });
        typeSelect.addEventListener('change', function () {
            input.value = '';
            hide();
            applyTypeState();
        });
        applyTypeState();
    }

    function bindPolicyActions() {
        attachPicker(el('jim-pol-source-value'), el('jim-pol-source-type'));
        attachPicker(el('jim-pol-target-value'), el('jim-pol-target-type'));

        el('jim-pol-add').addEventListener('click', function () {
            var editing = editingPolicyId !== null ? findPolicy(editingPolicyId) : null;
            var payload = {
                sourceType: el('jim-pol-source-type').value,
                sourceValue: el('jim-pol-source-value').value,
                targetType: el('jim-pol-target-type').value,
                targetValue: el('jim-pol-target-value').value,
                action: el('jim-pol-action').value,
                canSearch: el('jim-pol-can-search').checked,
                canStartChat: el('jim-pol-can-start').checked,
                canReceiveChat: el('jim-pol-can-receive').checked,
                enabled: editing ? editing.enabled : true,
                priority: parseInt(el('jim-pol-priority').value, 10) || 0
            };
            var call = editing
                ? request('PUT', '/policies/' + editing.id, payload)
                : request('POST', '/policies', payload);
            call.then(function () {
                showMessage(editing ? 'Policy updated.' : 'Policy added.');
                return loadPolicies();
            }).then(function () {
                exitPolicyEditMode();
                loadOverview();
            }).catch(function (error) {
                showMessage(error.message, true);
            });
        });

        el('jim-pol-cancel-edit').addEventListener('click', function () {
            exitPolicyEditMode();
        });

        el('jim-policy-rows').addEventListener('click', function (event) {
            var target = event.target;
            var editId = target.getAttribute && target.getAttribute('data-policy-edit');
            var deleteId = target.getAttribute && target.getAttribute('data-policy-delete');
            var toggleId = target.getAttribute && target.getAttribute('data-policy-toggle');
            if (editId) {
                var editPolicy = findPolicy(parseInt(editId, 10));
                if (editPolicy) {
                    enterPolicyEditMode(editPolicy);
                }
                return;
            }
            if (deleteId) {
                if (!window.confirm('Delete this policy?')) {
                    return;
                }
                request('DELETE', '/policies/' + deleteId).then(function () {
                    if (editingPolicyId === parseInt(deleteId, 10)) {
                        exitPolicyEditMode();
                    }
                    showMessage('Policy deleted.');
                    loadPolicies();
                    loadOverview();
                }).catch(function (error) {
                    showMessage(error.message, true);
                });
                return;
            }
            if (toggleId) {
                var policy = findPolicy(parseInt(toggleId, 10));
                if (!policy) {
                    return;
                }
                var update = {
                    sourceType: policy.sourceType,
                    sourceValue: policy.sourceValue,
                    targetType: policy.targetType,
                    targetValue: policy.targetValue,
                    action: policy.action,
                    canSearch: policy.canSearch,
                    canStartChat: policy.canStartChat,
                    canReceiveChat: policy.canReceiveChat,
                    enabled: !policy.enabled,
                    priority: policy.priority
                };
                request('PUT', '/policies/' + toggleId, update).then(function () {
                    showMessage('Policy updated.');
                    loadPolicies();
                }).catch(function (error) {
                    showMessage(error.message, true);
                });
            }
        });
    }

    // ===== Mobile feature access rules (Sprint 04G) =====

    var cachedMobileRules = [];
    var editingMobileRuleId = null;

    function mobileFeatureCheckboxes() {
        return document.querySelectorAll('.jim-mob-feature');
    }

    function readSelectedFeatures() {
        var boxes = mobileFeatureCheckboxes();
        var out = [];
        for (var i = 0; i < boxes.length; i++) {
            if (boxes[i].checked) {
                out.push(boxes[i].value);
            }
        }
        return out;
    }

    function setSelectedFeatures(features) {
        var set = {};
        (features || []).forEach(function (f) { set[f] = true; });
        var boxes = mobileFeatureCheckboxes();
        for (var i = 0; i < boxes.length; i++) {
            boxes[i].checked = !!set[boxes[i].value];
        }
    }

    function renderMobileRuleRows(rules) {
        var tbody = el('jim-mobile-rule-rows');
        if (!tbody) {
            return;
        }
        if (!rules.length) {
            tbody.innerHTML = '<tr><td colspan="5" class="jim-admin-empty">No rules. Every user currently has all mobile features.</td></tr>';
            return;
        }
        var html = [];
        for (var i = 0; i < rules.length; i++) {
            var r = rules[i];
            var rowClasses = (r.enabled ? '' : 'jim-policy-disabled') +
                (editingMobileRuleId === r.id ? ' jim-policy-editing' : '');
            var featureText = (r.features && r.features.length)
                ? escapeHtml(r.features.join(', '))
                : '<em>none (no features)</em>';
            html.push('<tr class="' + rowClasses + '">' +
                '<td>' + escapeHtml(r.subjectType) + ': <strong>' + escapeHtml(r.subjectValue) + '</strong></td>' +
                '<td>' + featureText + '</td>' +
                '<td>' + (r.enabled ? 'Yes' : 'No') + '</td>' +
                '<td>' + escapeHtml(r.priority) + '</td>' +
                '<td>' +
                '<button type="button" class="aui-button aui-button-link" data-mob-edit="' + r.id + '">Edit</button> ' +
                '<button type="button" class="aui-button aui-button-link" data-mob-toggle="' + r.id + '">' + (r.enabled ? 'Disable' : 'Enable') + '</button> ' +
                '<button type="button" class="aui-button aui-button-link jim-policy-delete" data-mob-delete="' + r.id + '">Delete</button>' +
                '</td>' +
                '</tr>');
        }
        tbody.innerHTML = html.join('');
    }

    function loadMobileRules() {
        return request('GET', '/mobile-feature-rules').then(function (response) {
            cachedMobileRules = response.rules || [];
            renderMobileRuleRows(cachedMobileRules);
        }).catch(function (error) {
            var tbody = el('jim-mobile-rule-rows');
            if (tbody) {
                tbody.innerHTML = '<tr><td colspan="5">' + escapeHtml(error.message) + '</td></tr>';
            }
        });
    }

    function findMobileRule(id) {
        for (var i = 0; i < cachedMobileRules.length; i++) {
            if (cachedMobileRules[i].id === id) {
                return cachedMobileRules[i];
            }
        }
        return null;
    }

    function enterMobileEditMode(rule) {
        editingMobileRuleId = rule.id;
        setTypeAndValue('jim-mob-subject-type', 'jim-mob-subject-value', rule.subjectType, rule.subjectValue);
        el('jim-mob-priority').value = rule.priority;
        setSelectedFeatures(rule.features);
        el('jim-mobile-form-title').textContent = 'Edit mobile access rule';
        el('jim-mob-add').textContent = 'Save changes';
        el('jim-mob-cancel-edit').hidden = false;
        renderMobileRuleRows(cachedMobileRules);
        el('jim-mobile-form').scrollIntoView({ behavior: 'smooth', block: 'center' });
        el('jim-mob-subject-value').focus();
    }

    function exitMobileEditMode() {
        editingMobileRuleId = null;
        setTypeAndValue('jim-mob-subject-type', 'jim-mob-subject-value', 'USER', '');
        el('jim-mob-priority').value = 0;
        setSelectedFeatures(['dashboard', 'chat', 'boards', 'projects', 'tasks', 'issueDetail']);
        el('jim-mobile-form-title').textContent = 'Add mobile access rule';
        el('jim-mob-add').textContent = 'Add rule';
        el('jim-mob-cancel-edit').hidden = true;
        renderMobileRuleRows(cachedMobileRules);
    }

    function bindMobileRuleActions() {
        if (!el('jim-mob-add')) {
            return;
        }
        attachPicker(el('jim-mob-subject-value'), el('jim-mob-subject-type'));

        el('jim-mob-add').addEventListener('click', function () {
            var editing = editingMobileRuleId !== null ? findMobileRule(editingMobileRuleId) : null;
            var payload = {
                subjectType: el('jim-mob-subject-type').value,
                subjectValue: el('jim-mob-subject-value').value,
                features: readSelectedFeatures(),
                enabled: editing ? editing.enabled : true,
                priority: parseInt(el('jim-mob-priority').value, 10) || 0
            };
            var call = editing
                ? request('PUT', '/mobile-feature-rules/' + editing.id, payload)
                : request('POST', '/mobile-feature-rules', payload);
            call.then(function () {
                showMessage(editing ? 'Mobile access rule updated.' : 'Mobile access rule added.');
                return loadMobileRules();
            }).then(function () {
                exitMobileEditMode();
                loadOverview();
            }).catch(function (error) {
                showMessage(error.message, true);
            });
        });

        el('jim-mob-cancel-edit').addEventListener('click', function () {
            exitMobileEditMode();
        });

        el('jim-mobile-rule-rows').addEventListener('click', function (event) {
            var target = event.target;
            var editId = target.getAttribute && target.getAttribute('data-mob-edit');
            var deleteId = target.getAttribute && target.getAttribute('data-mob-delete');
            var toggleId = target.getAttribute && target.getAttribute('data-mob-toggle');
            if (editId) {
                var editRule = findMobileRule(parseInt(editId, 10));
                if (editRule) {
                    enterMobileEditMode(editRule);
                }
                return;
            }
            if (deleteId) {
                if (!window.confirm('Delete this mobile access rule?')) {
                    return;
                }
                request('DELETE', '/mobile-feature-rules/' + deleteId).then(function () {
                    if (editingMobileRuleId === parseInt(deleteId, 10)) {
                        exitMobileEditMode();
                    }
                    showMessage('Mobile access rule deleted.');
                    loadMobileRules();
                    loadOverview();
                }).catch(function (error) {
                    showMessage(error.message, true);
                });
                return;
            }
            if (toggleId) {
                var rule = findMobileRule(parseInt(toggleId, 10));
                if (!rule) {
                    return;
                }
                var update = {
                    subjectType: rule.subjectType,
                    subjectValue: rule.subjectValue,
                    features: rule.features || [],
                    enabled: !rule.enabled,
                    priority: rule.priority
                };
                request('PUT', '/mobile-feature-rules/' + toggleId, update).then(function () {
                    showMessage('Mobile access rule updated.');
                    loadMobileRules();
                }).catch(function (error) {
                    showMessage(error.message, true);
                });
            }
        });
    }

    // ===== Diagnostics + overview =====

    var DIAG_LABELS = {
        pluginVersion: 'Plugin version',
        restHealthy: 'REST API healthy',
        aoHealthy: 'Active Objects healthy',
        jiraBaseUrl: 'Jira base URL',
        requestBaseUrl: 'Current request base URL',
        httpsDetected: 'HTTPS detected',
        vapidConfigured: 'VAPID keys configured',
        pushEnabled: 'Web push enabled',
        pushSubscriptionCount: 'Push subscriptions',
        failedPushCount: 'Failed pushes (since start)',
        chatMode: 'Chat mode',
        policyCount: 'Access policies'
    };

    function renderDiagnostics(diag) {
        var rows = [];
        for (var key in DIAG_LABELS) {
            if (!Object.prototype.hasOwnProperty.call(DIAG_LABELS, key)) {
                continue;
            }
            var value = diag[key];
            var display;
            if (value === true) {
                display = '<span class="jim-diag-ok">Yes</span>';
            } else if (value === false) {
                display = '<span class="jim-diag-bad">No</span>';
            } else {
                display = escapeHtml(value);
            }
            rows.push('<tr><td class="jim-diag-label">' + escapeHtml(DIAG_LABELS[key]) + '</td><td>' + display + '</td></tr>');
        }
        el('jim-diag-rows').innerHTML = rows.join('');
    }

    function renderOverview(diag) {
        var values = {
            chatMode: diag.chatMode,
            pushEnabled: diag.pushEnabled ? 'Enabled' : 'Disabled',
            attachmentsEnabled: null,
            policyCount: diag.policyCount,
            pushSubscriptionCount: diag.pushSubscriptionCount,
            pluginVersion: diag.pluginVersion
        };
        var nodes = document.querySelectorAll('[data-overview]');
        for (var i = 0; i < nodes.length; i++) {
            var key = nodes[i].getAttribute('data-overview');
            if (values[key] !== null && values[key] !== undefined) {
                nodes[i].textContent = values[key];
            }
        }
        request('GET', '/settings').then(function (settings) {
            var node = document.querySelector('[data-overview="attachmentsEnabled"]');
            if (node) {
                node.textContent = settings.attachmentsEnabled ? 'Enabled' : 'Disabled';
            }
        });
    }

    function loadDiagnostics() {
        return request('GET', '/diagnostics').then(function (diag) {
            renderDiagnostics(diag);
            renderOverview(diag);
        }).catch(function (error) {
            el('jim-diag-rows').innerHTML = '<tr><td colspan="2">' + escapeHtml(error.message) + '</td></tr>';
        });
    }

    // ===== License =====

    var LICENSE_LABELS = {
        state: 'Status',
        licensed: 'Licensed',
        present: 'License installed',
        valid: 'License valid',
        dataCenter: 'Data Center license',
        evaluation: 'Evaluation license',
        errorKey: 'Error code',
        errorMessage: 'Error',
        pluginKey: 'Plugin key',
        expiryDate: 'Expiry date'
    };

    function renderLicense(license) {
        var rows = [];
        for (var key in LICENSE_LABELS) {
            if (!Object.prototype.hasOwnProperty.call(license, key)) {
                continue;
            }
            var value = license[key];
            var display;
            if (value === null || value === undefined) {
                display = '<span class="jim-diag-muted">' + (key === 'dataCenter' ? 'Unknown' : '&#8211;') + '</span>';
            } else if (value === true) {
                display = '<span class="jim-diag-ok">Yes</span>';
            } else if (value === false) {
                display = '<span class="jim-diag-bad">No</span>';
            } else {
                display = escapeHtml(value);
            }
            rows.push('<tr><td class="jim-diag-label">' + escapeHtml(LICENSE_LABELS[key]) + '</td><td>' + display + '</td></tr>');
        }
        el('jim-license-rows').innerHTML = rows.join('');

        var banner = el('jim-license-state-banner');
        if (banner) {
            if (license.licensed) {
                banner.className = 'jim-admin-license-banner jim-admin-license-ok';
                banner.textContent = 'CorbitChat is licensed and fully functional.';
            } else {
                banner.className = 'jim-admin-license-banner jim-admin-license-bad';
                banner.textContent = 'CorbitChat is not licensed (' + (license.state || 'INVALID') +
                    '). Messaging is blocked for all users. Install a valid license under Manage apps \u2192 CorbitChat.';
            }
            banner.hidden = false;
        }

        var card = el('jim-overview-license');
        if (card) {
            card.textContent = license.licensed ? 'Valid' : (license.state || 'Invalid');
            card.style.color = license.licensed ? '' : '#bf2600';
        }
    }

    function loadLicense() {
        return request('GET', '/license').then(function (license) {
            renderLicense(license);
        }).catch(function (error) {
            el('jim-license-rows').innerHTML = '<tr><td colspan="2">' + escapeHtml(error.message) + '</td></tr>';
            var card = el('jim-overview-license');
            if (card) {
                card.textContent = 'Unknown';
            }
        });
    }

    function loadOverview() {
        loadDiagnostics();
        loadLicense();
        request('GET', '/audit').then(function (response) {
            var entries = response.entries || [];
            if (!entries.length) {
                el('jim-admin-audit-list').innerHTML = '<div class="jim-admin-empty">No admin changes recorded yet.</div>';
                return;
            }
            var html = [];
            for (var i = 0; i < entries.length; i++) {
                var entry = entries[i];
                html.push('<div class="jim-admin-audit-entry">' +
                    '<span class="jim-admin-audit-time">' + escapeHtml(formatTime(entry.createdAt)) + '</span>' +
                    '<span class="jim-admin-audit-user">' + escapeHtml(entry.userDisplayName || entry.userKey) + '</span>' +
                    '<span class="jim-admin-audit-action">' + escapeHtml(entry.action) + '</span>' +
                    '<span class="jim-admin-audit-details">' + escapeHtml(entry.details || '') + '</span>' +
                    '</div>');
            }
            el('jim-admin-audit-list').innerHTML = html.join('');
        }).catch(function (error) {
            el('jim-admin-audit-list').innerHTML = '<div class="jim-admin-empty">' + escapeHtml(error.message) + '</div>';
        });
    }

    // ===== Schedule-approval fields (mapping + provisioning) =====

    function approvalBadge(ok, naText) {
        if (ok === null || ok === undefined) {
            return '<span class="jim-diag-muted">' + escapeHtml(naText || '\u2013') + '</span>';
        }
        return ok
            ? '<span class="jim-diag-ok">\u2713</span>'
            : '<span class="jim-diag-bad">\u2717</span>';
    }

    function renderApprovalDiag(data) {
        var tbody = el('jim-approval-diag-rows');
        if (!tbody) { return; }
        var fields = (data && data.fields) || [];
        if (!fields.length) {
            tbody.innerHTML = '<tr><td colspan="11">No approval fields defined.</td></tr>';
        } else {
            var rows = [];
            for (var i = 0; i < fields.length; i++) {
                var f = fields[i];
                var nameCell = escapeHtml(f.nameEn) + ' <span class="jim-admin-hint" dir="rtl">' + escapeHtml(f.nameFa) + '</span>'
                    + '<br><span class="jim-admin-hint">expects: ' + escapeHtml(f.expectedType) + '</span>';
                var optionsCell = f.optionsRequired ? approvalBadge(f.optionsOk) : approvalBadge(null, 'n/a');
                var fallbackCell = f.notConfigured
                    ? '<span class="jim-diag-bad">not configured</span>'
                    : (f.usingFallback ? '<span class="jim-diag-warn">fallback</span>' : '<span class="jim-diag-ok">configured</span>');
                rows.push('<tr>'
                    + '<td>' + nameCell + '</td>'
                    + '<td><code>' + escapeHtml(f.logicalKey) + '</code></td>'
                    + '<td><input type="text" class="text" style="max-width:170px;" id="jim-approval-map-' + escapeHtml(f.logicalKey)
                        + '" value="' + escapeHtml(f.configuredFieldId || '') + '" placeholder="' + escapeHtml(f.fallbackFieldId || 'customfield_') + '"/></td>'
                    + '<td>' + (f.effectiveFieldId ? '<code>' + escapeHtml(f.effectiveFieldId) + '</code>' : '<span class="jim-diag-muted">\u2013</span>') + '</td>'
                    + '<td>' + approvalBadge(f.exists) + '</td>'
                    + '<td>' + approvalBadge(f.typeOk) + '</td>'
                    + '<td>' + optionsCell + '</td>'
                    + '<td>' + approvalBadge(f.contextOk) + '</td>'
                    + '<td>' + approvalBadge(f.screenOk) + '</td>'
                    + '<td>' + fallbackCell + '</td>'
                    + '<td><button type="button" class="aui-button aui-button-link" data-approval-map="' + escapeHtml(f.logicalKey) + '">Save</button></td>'
                    + '</tr>');
            }
            tbody.innerHTML = rows.join('');
        }
        var status = el('jim-approval-status');
        if (status) {
            status.innerHTML = data && data.minimallyConfigured
                ? '<span class="jim-diag-ok">\u2713 Approval workflow is configured on this instance (status + approver + proposed date all resolve).</span>'
                : '<span class="jim-diag-bad">\u2717 Approval workflow is NOT fully configured \u2014 map existing fields or provision the missing ones below.</span>';
        }
    }

    function loadApprovalFields() {
        if (!el('jim-approval-diag-rows')) {
            return; // tab not present in this build
        }
        return request('GET', '/approval-fields/diagnostics').then(renderApprovalDiag).catch(function (error) {
            el('jim-approval-diag-rows').innerHTML = '<tr><td colspan="11">' + escapeHtml(error.message) + '</td></tr>';
        });
    }

    function saveApprovalMapping(logicalKey) {
        var input = el('jim-approval-map-' + logicalKey);
        var value = input ? input.value.trim() : '';
        request('PUT', '/approval-fields/mapping', { logicalKey: logicalKey, fieldId: value }).then(function (data) {
            renderApprovalDiag(data);
            showMessage(value ? ('Mapped ' + logicalKey + ' \u2192 ' + value) : ('Cleared mapping for ' + logicalKey + ' (using production fallback).'));
        }).catch(function (error) {
            showMessage(error.message, true);
        });
    }

    function bindApprovalActions() {
        var refresh = el('jim-approval-refresh');
        if (!refresh) {
            return; // tab not present
        }
        refresh.addEventListener('click', loadApprovalFields);

        var tbody = el('jim-approval-diag-rows');
        if (tbody) {
            tbody.addEventListener('click', function (event) {
                var btn = event.target && event.target.closest ? event.target.closest('[data-approval-map]') : null;
                if (btn) {
                    saveApprovalMapping(btn.getAttribute('data-approval-map'));
                }
            });
        }

        el('jim-approval-provision').addEventListener('click', function () {
            var addToScreens = el('jim-approval-add-screens') && el('jim-approval-add-screens').checked;
            var screenIds = [];
            var raw = el('jim-approval-screen-ids') ? el('jim-approval-screen-ids').value : '';
            var parts = String(raw || '').split(',');
            for (var i = 0; i < parts.length; i++) {
                var t = parts[i].trim();
                if (t && /^\d+$/.test(t)) {
                    screenIds.push(Number(t));
                }
            }
            if (!window.confirm('Provision missing schedule-approval fields on this instance? This creates Jira custom fields and is safe to re-run.')) {
                return;
            }
            var provisionBtn = this;
            provisionBtn.setAttribute('disabled', 'disabled');
            request('POST', '/approval-fields/provision', { addToScreens: !!addToScreens, screenIds: screenIds }).then(function (result) {
                if (result && result.diagnostics) {
                    renderApprovalDiag({ fields: result.diagnostics, minimallyConfigured: result.minimallyConfigured });
                } else {
                    loadApprovalFields();
                }
                var s = result && result.summary ? result.summary : {};
                if (result && result.locked === false) {
                    showMessage('Provisioning is already running on another node. Try again shortly.', true);
                } else {
                    showMessage('Provisioning done \u2014 created ' + (s.created || 0) + ', mapped ' + (s.mapped || 0)
                        + ', already present ' + (s.exists || 0) + ', failed ' + (s.failed || 0) + '.', (s.failed || 0) > 0);
                }
            }).catch(function (error) {
                showMessage(error.message, true);
            }).then(function () {
                provisionBtn.removeAttribute('disabled');
            });
        });
    }

    function init() {
        if (!el('jim-admin-page') && !document.getElementById('jim-admin-message')) {
            return;
        }
        bindTabs();
        bindSettingsActions();
        bindPolicyActions();
        bindMobileRuleActions();
        bindApprovalActions();
        el('jim-diag-refresh').addEventListener('click', loadDiagnostics);
        el('jim-license-refresh').addEventListener('click', loadLicense);
        loadSettings();
        loadPolicies();
        loadMobileRules();
        loadApprovalFields();
        loadOverview();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
