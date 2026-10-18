/**
 * Board Gallery — vanilla JS, no frameworks.
 *
 * Loads accessible Jira boards from Jira's built-in REST endpoint
 * (`/rest/agile/1.0/board`), which already filters by the current user's
 * permissions. Each card is then enriched with:
 *   - project chip (from the board's underlying filter JQL,
 *     fetched via /rest/greenhopper/1.0/rapidviews/list)
 *   - project lead (from /rest/api/2/project/{key})
 *   - "My tasks: N" badge (from /rest/api/2/search with the board's
 *     own JQL + 'assignee = currentUser()')
 *
 * All sub-resources are fetched same-origin with the user's session, so
 * Jira's permission filters apply automatically: boards/projects/issues
 * the user can't see never reach the page. A 403/404 on any enrichment
 * call is treated as "data unavailable for this user" - the card still
 * renders, just without that line.
 *
 * The main board-open link still goes to /secure/RapidBoard.jspa - the
 * plugin never proxies board content. The project chip and tasks pill
 * are separate links inside the card and stop event propagation so
 * clicking them does not also navigate to the board.
 */
(function () {
    'use strict';

    var BOARD_API_PATH = '/rest/agile/1.0/board';
    var RAPIDVIEW_API_PATH = '/rest/greenhopper/1.0/rapidviews/list';
    var PROJECT_API_PATH = '/rest/api/2/project/';
    var SEARCH_API_PATH = '/rest/api/2/search';
    var PAGE_SIZE = 50;
    // Avatar palette - deterministic per board so it stays stable.
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

    function hashColour(value) {
        var hash = 0;
        var s = String(value || '');
        for (var i = 0; i < s.length; i++) {
            hash = ((hash << 5) - hash) + s.charCodeAt(i);
            hash |= 0;
        }
        return AVATAR_PALETTE[Math.abs(hash) % AVATAR_PALETTE.length];
    }

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
        return contextPath() + '/secure/RapidBoard.jspa?rapidView=' + encodeURIComponent(board.id);
    }

    /**
     * Jira returns project avatar URLs as absolute, based on the
     * configured jira.baseurl. When the user accesses Jira via a
     * different host (proxy, IP, local DNS, etc.) the absolute URL
     * points to the wrong origin and the image either 404s, fails CORS
     * or hits a mixed-content block. We strip the URL down to
     * pathname + search so the browser resolves it against the actual
     * page origin and the session cookie is sent automatically.
     *
     * Pass-through for any URL we can't parse (e.g. data: URIs) and
     * for already-relative URLs.
     */
    function toSameOriginUrl(absUrl) {
        if (!absUrl) {
            return null;
        }
        try {
            var u = new URL(absUrl, window.location.origin);
            return u.pathname + (u.search || '');
        } catch (e) {
            return absUrl;
        }
    }

    function projectUrl(projectKey) {
        return contextPath() + '/browse/' + encodeURIComponent(projectKey);
    }

    /** Opens CorbitChat's project chat page for the given project. */
    function projectChatUrl(projectKey) {
        return contextPath() + '/plugins/servlet/jim/project-chat?projectKey=' +
            encodeURIComponent(projectKey);
    }

    /**
     * Builds the Jira issue-list URL for "tasks assigned to me", scoped
     * to the board's own filter query so we open the same set of issues
     * the board shows (only the ones the user is on). Uses Jira's native
     * /issues/?jql=... navigator - not a custom view.
     */
    function assignedToMeUrl(boardJql) {
        var baseJql = (boardJql || '').replace(/\s+ORDER\s+BY.*/i, '').trim();
        if (!baseJql) {
            baseJql = 'assignee = currentUser()';
        } else {
            baseJql = '(' + baseJql + ') AND assignee = currentUser()';
        }
        return contextPath() + '/issues/?jql=' + encodeURIComponent(baseJql);
    }

    /**
     * Issue navigator filtered to all tasks in the board's project(s).
     * Uses the board's own JQL (minus ORDER BY) so it matches the scope
     * the board actually displays, including any custom filter the
     * board's owner has applied.
     */
    function allTasksUrl(boardJql) {
        var baseJql = (boardJql || '').replace(/\s+ORDER\s+BY.*/i, '').trim();
        if (!baseJql) {
            return contextPath() + '/issues/';
        }
        return contextPath() + '/issues/?jql=' + encodeURIComponent(baseJql);
    }

    /**
     * Parses one or more Jira project keys out of a board filter JQL.
     * Handles 'project = KEY', 'project in (A, B)', 'project = "Key"' and
     * variants with/without spaces. Returns an array of upper-case keys
     * (deduped, order preserved) or an empty array if nothing matched.
     */
    function parseProjectKeysFromJql(jql) {
        if (!jql) {
            return [];
        }
        var keys = [];
        var seen = {};
        var addKey = function (raw) {
            var k = String(raw || '').replace(/['"\s]/g, '').toUpperCase();
            if (k && !seen[k]) {
                seen[k] = true;
                keys.push(k);
            }
        };

        // project in (A, B, "C")
        var inMatch = /project\s+in\s*\(([^)]+)\)/i.exec(jql);
        if (inMatch) {
            inMatch[1].split(',').forEach(addKey);
            return keys;
        }

        // project = KEY or project = "Key"
        var eqMatch = /project\s*=\s*("[^"]+"|'[^']+'|[A-Z][A-Z0-9_]*)/i.exec(jql);
        if (eqMatch) {
            addKey(eqMatch[1]);
        }
        return keys;
    }

    function typeLabel(type) {
        if (!type) return '';
        var t = String(type).toLowerCase();
        if (t === 'scrum') return 'Scrum';
        if (t === 'kanban') return 'Kanban';
        if (t === 'simple') return 'Simple';
        return t.charAt(0).toUpperCase() + t.slice(1);
    }

    // -------- REST fetchers (each one degrades gracefully) ------------

    function fetchAllBoards() {
        var all = [];

        function page(startAt) {
            var url = contextPath() + BOARD_API_PATH +
                '?startAt=' + startAt + '&maxResults=' + PAGE_SIZE;
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

    /**
     * Fetches the GreenHopper rapid-view list to harvest each board's
     * filter JQL. If GreenHopper is unavailable or returns an error we
     * silently return an empty index - cards then degrade to "no project
     * info" instead of breaking.
     */
    function fetchFilterIndex() {
        return fetch(contextPath() + RAPIDVIEW_API_PATH, {
            credentials: 'same-origin',
            headers: { 'Accept': 'application/json' }
        }).then(function (response) {
            if (!response.ok) {
                return { views: [] };
            }
            return response.json();
        }).catch(function () {
            return { views: [] };
        }).then(function (data) {
            var index = {};
            var views = (data && data.views) || [];
            for (var i = 0; i < views.length; i++) {
                var v = views[i];
                var f = v.filter || {};
                index[v.id] = {
                    filterId: f.id || null,
                    query: f.query || ''
                };
            }
            return index;
        });
    }

    /**
     * Fetches /rest/api/2/project/{key} for each unique key (deduped).
     * Returns a {KEY: {name, leadDisplay, leadKey}} map. A failure on
     * any single project leaves that entry undefined so the card simply
     * skips the line.
     */
    function fetchProjectMap(keys) {
        var map = {};
        var unique = [];
        var seen = {};
        for (var i = 0; i < keys.length; i++) {
            var k = keys[i];
            if (k && !seen[k]) {
                seen[k] = true;
                unique.push(k);
            }
        }
        if (!unique.length) {
            return Promise.resolve(map);
        }
        var promises = unique.map(function (key) {
            var url = contextPath() + PROJECT_API_PATH + encodeURIComponent(key);
            return fetch(url, {
                credentials: 'same-origin',
                headers: { 'Accept': 'application/json' }
            }).then(function (response) {
                if (!response.ok) {
                    return null;
                }
                return response.json();
            }).then(function (project) {
                if (!project) {
                    return;
                }
                var lead = project.lead || {};
                // Prefer the largest pre-rendered avatar Jira returns so
                // it renders crisp at our 44x44 card slot. avatarUrls is
                // a {sizeString: url} map (e.g. '48x48': 'https://...').
                var avatarUrls = project.avatarUrls || {};
                var rawAvatar = avatarUrls['48x48']
                    || avatarUrls['32x32']
                    || avatarUrls['24x24']
                    || avatarUrls['16x16']
                    || null;
                map[key] = {
                    name: project.name || key,
                    leadDisplay: lead.displayName || null,
                    leadKey: lead.key || null,
                    avatarUrl: toSameOriginUrl(rawAvatar)
                };
            }).catch(function () {
                // best effort: leave key unset
            });
        });
        return Promise.all(promises).then(function () { return map; });
    }

    /**
     * One JQL search per board to get the "assigned to me" count. We use
     * maxResults=0 so Jira returns just the total without issue payloads
     * (cheap). 'fields=summary' is passed because some Jira versions
     * complain about an empty fields parameter.
     *
     * Returns a {boardId: count} map; boards without a parseable JQL or
     * where the search fails are simply absent from the map.
     */
    function fetchAssignedCounts(boards, filterIndex) {
        var counts = {};
        var promises = [];
        boards.forEach(function (board) {
            var filterInfo = filterIndex[board.id];
            if (!filterInfo || !filterInfo.query) {
                return;
            }
            var baseJql = filterInfo.query.replace(/\s+ORDER\s+BY.*/i, '').trim();
            if (!baseJql) {
                return;
            }
            var jql = '(' + baseJql + ') AND assignee = currentUser()';
            var params = new URLSearchParams();
            params.set('jql', jql);
            params.set('maxResults', '0');
            params.set('fields', 'summary');
            promises.push(
                fetch(contextPath() + SEARCH_API_PATH + '?' + params.toString(), {
                    credentials: 'same-origin',
                    headers: { 'Accept': 'application/json' }
                }).then(function (response) {
                    if (!response.ok) {
                        return;
                    }
                    return response.json();
                }).then(function (result) {
                    if (result && typeof result.total === 'number') {
                        counts[board.id] = result.total;
                    }
                }).catch(function () {
                    // best effort: leave count undefined
                })
            );
        });
        return Promise.all(promises).then(function () { return counts; });
    }

    // ----- Card rendering --------------------------------------------

    function renderProjectLine(board) {
        if (board.multipleProjects) {
            // Link to a JQL search showing issues across all the board's
            // projects so it isn't a dead chip.
            var keys = (board.projectKeys || []).join(', ');
            var jql = keys ? 'project in (' + keys + ')' : '';
            var href = jql ? contextPath() + '/issues/?jql=' + encodeURIComponent(jql) : '#';
            return '<a class="jim-board-card-project-link jim-board-card-project-multi" ' +
                'href="' + escapeHtml(href) + '" data-stop>' +
                '<span class="jim-board-card-project-key">Multi</span>' +
                '<span class="jim-board-card-project-name">' + (keys ? escapeHtml(keys) : 'Multiple projects') + '</span>' +
                '</a>';
        }
        if (!board.primaryProjectKey) {
            return '';
        }
        var key = board.primaryProjectKey;
        var info = board.projectInfo || {};
        var name = info.name || key;
        return '<a class="jim-board-card-project-link" ' +
            'href="' + escapeHtml(projectUrl(key)) + '" ' +
            'title="Open project ' + escapeHtml(name) + '" data-stop>' +
            '<span class="jim-board-card-project-key">' + escapeHtml(key) + '</span>' +
            '<span class="jim-board-card-project-name" dir="auto">' + escapeHtml(name) + '</span>' +
            '</a>';
    }

    function renderLeadLine(board) {
        var info = board.projectInfo || {};
        if (!info.leadDisplay) {
            return '';
        }
        return '<div class="jim-board-card-lead" title="' + escapeHtml(info.leadDisplay) + '">' +
            '<span class="jim-board-card-lead-label">Lead:</span> ' +
            '<span class="jim-board-card-lead-name" dir="auto">' + escapeHtml(info.leadDisplay) + '</span>' +
            '</div>';
    }

    function renderTasksLine(board) {
        if (typeof board.assignedCount !== 'number') {
            return '';
        }
        var jql = (board.boardJql || '').replace(/\s+ORDER\s+BY.*/i, '').trim();
        var href = assignedToMeUrl(jql);
        var label = board.assignedCount + (board.assignedCount === 1 ? ' task' : ' tasks');
        var className = 'jim-board-card-tasks';
        if (board.assignedCount === 0) {
            className += ' jim-board-card-tasks-empty';
        }
        return '<a class="' + className + '" href="' + escapeHtml(href) + '" ' +
            'title="Open my issues in this board" data-stop>' +
            '<span class="jim-board-card-tasks-label">My tasks</span>' +
            '<span class="jim-board-card-tasks-count">' + label + '</span>' +
            '</a>';
    }

    /**
     * Renders the "All project tasks" pill, which opens Jira's native
     * issue navigator with the board's JQL (no assignee filter). Hidden
     * when we couldn't parse a JQL for the board.
     */
    function renderAllTasksLine(board) {
        var jql = (board.boardJql || '').replace(/\s+ORDER\s+BY.*/i, '').trim();
        if (!jql) {
            return '';
        }
        return '<a class="jim-board-card-tasks jim-board-card-tasks-all" ' +
            'href="' + escapeHtml(allTasksUrl(jql)) + '" ' +
            'title="Open all issues in this board" data-stop>' +
            '<span class="jim-board-card-tasks-label">All tasks</span>' +
            '</a>';
    }

    /**
     * Project chat link - opens CorbitChat's project chat for the
     * primary project. Hidden for multi-project boards (we can't pick
     * one safely) and for boards where we couldn't resolve a project.
     */
    function renderProjectChatLine(board) {
        if (board.multipleProjects || !board.primaryProjectKey) {
            return '';
        }
        return '<a class="jim-board-card-chat" ' +
            'href="' + escapeHtml(projectChatUrl(board.primaryProjectKey)) + '" ' +
            'title="Open project chat in CorbitChat" data-stop>' +
            '<span class="jim-board-card-chat-icon" aria-hidden="true">&#128172;</span>' +
            '<span class="jim-board-card-chat-label">Project chat</span>' +
            '</a>';
    }

    /**
     * Project avatar. If the project lookup returned a Jira avatar URL
     * we render an <img> on top of the coloured-initials block; if the
     * image fails to load, onerror removes it and the initials remain
     * visible underneath. No broken images.
     */
    function renderAvatarBlock(board, initials, colour) {
        var info = board.projectInfo || {};
        var imgHtml = '';
        if (info.avatarUrl) {
            imgHtml = '<img class="jim-board-card-avatar-img" alt="" loading="lazy" ' +
                'src="' + escapeHtml(info.avatarUrl) + '" ' +
                'onerror="this.parentNode.removeChild(this);"/>';
        }
        return '<div class="jim-board-card-avatar" style="background:' + colour + ';">' +
               '<span class="jim-board-card-initials" aria-hidden="true">' + escapeHtml(initials) + '</span>' +
               imgHtml +
               '</div>';
    }

    function renderBoardCard(board) {
        var name = board.name || 'Untitled board';
        var type = typeLabel(board.type);
        var typeClass = 'jim-board-card-type-' + (String(board.type || 'unknown').toLowerCase());
        var url = boardUrl(board);
        var initials = initialsOf(name);
        var colour = hashColour(name + '#' + board.id);

        // The main clickable region is the avatar + name. Sub-links
        // (project / tasks) live in a meta row and stop click propagation
        // so they never accidentally trigger the board-open navigation.
        return '' +
            '<div class="jim-board-card" role="listitem" ' +
            'data-board-name="' + escapeHtml(name.toLowerCase()) + '" ' +
            'data-board-project="' + escapeHtml(((board.projectKeys || []).join(' ') + ' ' +
                ((board.projectInfo || {}).name || '')).toLowerCase()) + '" ' +
            'data-board-type="' + escapeHtml(String(board.type || '').toLowerCase()) + '">' +
            '  <a href="' + escapeHtml(url) + '" class="jim-board-card-open" ' +
            'aria-label="Open board ' + escapeHtml(name) + '">' +
            renderAvatarBlock(board, initials, colour) +
            '    <div class="jim-board-card-name" dir="auto" title="' + escapeHtml(name) + '">' +
            escapeHtml(name) + '</div>' +
            '  </a>' +
            (type ? '<span class="jim-board-card-type ' + typeClass + '">' + escapeHtml(type) + '</span>' : '') +
            '  <div class="jim-board-card-meta">' +
            renderProjectLine(board) +
            renderLeadLine(board) +
            '  <div class="jim-board-card-actions">' +
            renderAllTasksLine(board) +
            renderTasksLine(board) +
            renderProjectChatLine(board) +
            '  </div>' +
            '  </div>' +
            '</div>';
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
                var projText = ((b.projectKeys || []).join(' ') + ' ' +
                                ((b.projectInfo || {}).name || '')).toLowerCase();
                if (name.indexOf(query) === -1 && projText.indexOf(query) === -1) {
                    continue;
                }
            }
            filtered.push(b);
        }

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

        // Containment for clicks on project / tasks sub-links so the
        // parent .jim-board-card-open anchor doesn't also fire.
        root.addEventListener('click', function (event) {
            if (event.target && event.target.closest && event.target.closest('[data-stop]')) {
                event.stopPropagation();
            }
        }, true);

        function load() {
            var status = el('jim-board-gallery-status');
            status.textContent = 'Loading boards\u2026';
            status.hidden = false;
            el('jim-board-gallery-grid').innerHTML = '';

            Promise.all([
                fetchAllBoards(),
                fetchFilterIndex()
            ]).then(function (results) {
                var raw = results[0] || [];
                var filterIndex = results[1] || {};

                // Decorate each board with parsed project keys + jql.
                raw.forEach(function (board) {
                    var f = filterIndex[board.id] || {};
                    board.boardJql = f.query || '';
                    board.projectKeys = parseProjectKeysFromJql(board.boardJql);
                    board.primaryProjectKey = board.projectKeys[0] || null;
                    board.multipleProjects = board.projectKeys.length > 1;
                });

                // First render now (so users see boards immediately even
                // before project/task enrichment finishes), then refresh
                // when the extra data arrives.
                boards = raw;
                loaded = true;
                applyFilters(root, boards);

                // Now fetch project info + assigned-to-me counts in
                // parallel. Both are best-effort - failures degrade
                // gracefully per card.
                var allKeys = [];
                raw.forEach(function (b) {
                    if (!b.multipleProjects && b.primaryProjectKey) {
                        allKeys.push(b.primaryProjectKey);
                    }
                });
                return Promise.all([
                    fetchProjectMap(allKeys),
                    fetchAssignedCounts(raw, filterIndex)
                ]).then(function (more) {
                    var projects = more[0] || {};
                    var counts = more[1] || {};
                    raw.forEach(function (b) {
                        if (b.primaryProjectKey && projects[b.primaryProjectKey]) {
                            b.projectInfo = projects[b.primaryProjectKey];
                        }
                        if (typeof counts[b.id] === 'number') {
                            b.assignedCount = counts[b.id];
                        }
                    });
                    applyFilters(root, boards);
                });
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
