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
        'assignmentNotifications', 'attachmentsEnabled', 'imagePreviewEnabled'];

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

    function init() {
        if (!el('jim-admin-page') && !document.getElementById('jim-admin-message')) {
            return;
        }
        bindTabs();
        bindSettingsActions();
        bindPolicyActions();
        bindMobileRuleActions();
        el('jim-diag-refresh').addEventListener('click', loadDiagnostics);
        el('jim-license-refresh').addEventListener('click', loadLicense);
        loadSettings();
        loadPolicies();
        loadMobileRules();
        loadOverview();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
