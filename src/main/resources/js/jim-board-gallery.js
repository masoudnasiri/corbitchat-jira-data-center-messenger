/**
 * Board Gallery — vanilla JS, no frameworks.
 *
 * Loads accessible Jira boards from Jira's built-in REST endpoint
 * (`/rest/agile/1.0/board`), which already filters by the current user's
 * permissions. Renders them as visual cards with type filter and a
 * client-side search. Clicking a card navigates to the real Jira board.
 */
(function () {
    'use strict';

    var BOARD_API_PATH = '/rest/agile/1.0/board';
    var PAGE_SIZE = 50;
    // Avatar colour palette - deterministic per board so the same board
    // always gets the same colour.
    var AVATAR_PALETTE = [
        '#0c66e4', '#6554c0', '#00b8d9', '#36b37e', '#ff8b00',
        '#de350b', '#5243aa', '#0747a6', '#008da6', '#bf2600'
    ];

    function contextPath() {
        if (window.AJS && typeof window.AJS.contextPath === 'function') {
            return window.AJS.contextPath();
        }
        var meta = document.querySelector('meta[name="ajs-context-path"]');
        return meta ? meta.getAttribute('content') : '';
    }

    function el(id) {
        return document.getElementById(id);
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    /** Stable hash of a string for picking a deterministic palette colour. */
    function hashColour(value) {
        var hash = 0;
        var s = String(value || '');
        for (var i = 0; i < s.length; i++) {
            hash = ((hash << 5) - hash) + s.charCodeAt(i);
            hash |= 0;
        }
        return AVATAR_PALETTE[Math.abs(hash) % AVATAR_PALETTE.length];
    }

    /** Two-letter initials from the board name. */
    function initialsOf(name) {
        var parts = String(name || '').trim().split(/\s+/);
        if (!parts.length || !parts[0]) {
            return '?';
        }
        if (parts.length === 1) {
            return parts[0].substring(0, 2).toUpperCase();
        }
        return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
    }

    function boardUrl(board) {
        // Standard Jira DC URL for opening a Rapid Board. The plugin
        // never renders board contents itself; the user lands on Jira's
        // real board page.
        return contextPath() + '/secure/RapidBoard.jspa?rapidView=' + encodeURIComponent(board.id);
    }

    function typeLabel(type) {
        if (!type) return '';
        var t = String(type).toLowerCase();
        if (t === 'scrum') return 'Scrum';
        if (t === 'kanban') return 'Kanban';
        if (t === 'simple') return 'Simple';
        return t.charAt(0).toUpperCase() + t.slice(1);
    }

    function renderBoardCard(board) {
        var name = board.name || 'Untitled board';
        var type = typeLabel(board.type);
        var typeClass = 'jim-board-card-type-' + (String(board.type || 'unknown').toLowerCase());
        var location = board.location || {};
        var projectKey = location.projectKey || '';
        var projectName = location.projectName || location.displayName || '';
        var url = boardUrl(board);
        var initials = initialsOf(name);
        var colour = hashColour(name + '#' + board.id);

        var projectHtml = '';
        if (projectKey || projectName) {
            projectHtml =
                '<div class="jim-board-card-project">' +
                (projectKey ? '<span class="jim-board-card-project-key">' + escapeHtml(projectKey) + '</span>' : '') +
                (projectName ? '<span class="jim-board-card-project-name" dir="auto">' + escapeHtml(projectName) + '</span>' : '') +
                '</div>';
        }

        return '' +
            '<a href="' + escapeHtml(url) + '" class="jim-board-card" role="listitem" ' +
            'data-board-name="' + escapeHtml(name.toLowerCase()) + '" ' +
            'data-board-project="' + escapeHtml((projectKey + ' ' + projectName).toLowerCase()) + '" ' +
            'data-board-type="' + escapeHtml(String(board.type || '').toLowerCase()) + '">' +
            '  <div class="jim-board-card-avatar" style="background:' + colour + ';">' +
            '    <span class="jim-board-card-initials" aria-hidden="true">' + escapeHtml(initials) + '</span>' +
            '  </div>' +
            '  <div class="jim-board-card-body">' +
            '    <div class="jim-board-card-name" dir="auto" title="' + escapeHtml(name) + '">' +
            escapeHtml(name) + '</div>' +
            projectHtml +
            '  </div>' +
            (type ? '<span class="jim-board-card-type ' + typeClass + '">' + escapeHtml(type) + '</span>' : '') +
            '</a>';
    }

    /**
     * Fetches every page of /rest/agile/1.0/board until isLast is true (or
     * the response shape says we're done). Returns a single concatenated
     * array. The endpoint already filters by the current user's
     * permissions so we never see boards they shouldn't.
     */
    function fetchAllBoards() {
        var all = [];

        function page(startAt) {
            var url = contextPath() + BOARD_API_PATH +
                '?startAt=' + startAt +
                '&maxResults=' + PAGE_SIZE;
            return fetch(url, {
                credentials: 'same-origin',
                headers: { 'Accept': 'application/json' }
            }).then(function (response) {
                if (!response.ok) {
                    throw new Error('Jira returned HTTP ' + response.status);
                }
                return response.json();
            }).then(function (data) {
                var batch = (data && data.values) || [];
                for (var i = 0; i < batch.length; i++) {
                    all.push(batch[i]);
                }
                var isLast = data && (data.isLast === true || batch.length < PAGE_SIZE);
                if (isLast) {
                    return all;
                }
                return page(startAt + batch.length);
            });
        }

        return page(0);
    }

    function getActiveFilter(root) {
        var active = root.querySelector('.jim-board-gallery-tab.is-active');
        return active ? (active.getAttribute('data-filter') || 'all') : 'all';
    }

    function applyFilters(root, boards) {
        var grid = el('jim-board-gallery-grid');
        var status = el('jim-board-gallery-status');
        var count = el('jim-board-gallery-count');
        var search = el('jim-board-gallery-search');
        var query = (search.value || '').trim().toLowerCase();
        var filter = getActiveFilter(root);

        var filtered = [];
        for (var i = 0; i < boards.length; i++) {
            var b = boards[i];
            if (filter !== 'all' && String(b.type || '').toLowerCase() !== filter) {
                continue;
            }
            if (query) {
                var name = (b.name || '').toLowerCase();
                var loc = b.location || {};
                var proj = ((loc.projectKey || '') + ' ' + (loc.projectName || loc.displayName || '')).toLowerCase();
                if (name.indexOf(query) === -1 && proj.indexOf(query) === -1) {
                    continue;
                }
            }
            filtered.push(b);
        }

        // Sort by name (case-insensitive)
        filtered.sort(function (a, b) {
            return String(a.name || '').toLowerCase().localeCompare(String(b.name || '').toLowerCase());
        });

        if (count) {
            count.textContent = filtered.length === boards.length
                ? boards.length + ' board' + (boards.length === 1 ? '' : 's')
                : filtered.length + ' of ' + boards.length;
        }

        if (!filtered.length) {
            grid.innerHTML = '';
            if (boards.length === 0) {
                renderTemplate('jim-board-gallery-empty-template', status);
            } else {
                status.innerHTML = '<div class="jim-board-gallery-empty-text">' +
                    'No boards match your search or filter.</div>';
            }
            status.hidden = false;
            return;
        }

        status.hidden = true;
        var html = [];
        for (var j = 0; j < filtered.length; j++) {
            html.push(renderBoardCard(filtered[j]));
        }
        grid.innerHTML = html.join('');
    }

    function renderTemplate(id, target) {
        var tpl = el(id);
        if (!tpl) {
            return;
        }
        target.innerHTML = '';
        target.appendChild(tpl.content.cloneNode(true));
    }

    function renderError(detail, onRetry) {
        var status = el('jim-board-gallery-status');
        var grid = el('jim-board-gallery-grid');
        var tpl = el('jim-board-gallery-error-template');
        grid.innerHTML = '';
        if (!tpl) {
            status.textContent = detail || 'Could not load boards.';
            status.hidden = false;
            return;
        }
        status.innerHTML = '';
        status.appendChild(tpl.content.cloneNode(true));
        var detailEl = status.querySelector('[data-board-error-detail]');
        if (detailEl && detail) {
            detailEl.textContent = detail;
        }
        var retryBtn = status.querySelector('[data-board-error-retry]');
        if (retryBtn && typeof onRetry === 'function') {
            retryBtn.addEventListener('click', onRetry);
        }
        status.hidden = false;
    }

    function init() {
        var root = el('jim-board-gallery');
        if (!root) {
            return;
        }
        var search = el('jim-board-gallery-search');
        var tabs = root.querySelectorAll('.jim-board-gallery-tab');
        var boards = [];
        var loaded = false;

        function load() {
            var status = el('jim-board-gallery-status');
            status.textContent = 'Loading boards\u2026';
            status.hidden = false;
            el('jim-board-gallery-grid').innerHTML = '';
            fetchAllBoards().then(function (result) {
                boards = result || [];
                loaded = true;
                applyFilters(root, boards);
            }).catch(function (error) {
                renderError(error && error.message ? error.message : 'Unknown error.', load);
            });
        }

        if (search) {
            var debounce;
            search.addEventListener('input', function () {
                clearTimeout(debounce);
                debounce = setTimeout(function () {
                    if (loaded) {
                        applyFilters(root, boards);
                    }
                }, 120);
            });
        }
        for (var i = 0; i < tabs.length; i++) {
            tabs[i].addEventListener('click', function (event) {
                var clicked = event.currentTarget;
                for (var t = 0; t < tabs.length; t++) {
                    tabs[t].classList.toggle('is-active', tabs[t] === clicked);
                }
                if (loaded) {
                    applyFilters(root, boards);
                }
            });
        }

        load();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
