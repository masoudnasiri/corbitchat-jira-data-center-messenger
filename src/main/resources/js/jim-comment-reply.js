/**
 * CorbitChat - Jira issue comment "Reply" capability.
 *
 * This script runs on every Jira issue view page (web-resource context
 * atl.jira.view.issue) and:
 *
 *   1. Adds a "Reply" link to every comment's action toolbar in the
 *      issue activity feed.
 *   2. On click, opens Jira's native comment editor and pre-fills it
 *      with a real Jira mention ([~username]) of the parent comment's
 *      author plus a wiki {quote}...{quote} block containing a short
 *      excerpt of the parent comment.
 *   3. The user types their reply BELOW the quoted block and submits
 *      through Jira's normal "Save" button.
 *
 * What this guarantees, by relying entirely on native Jira:
 *
 *   * The reply IS a native Jira comment (no shadow storage). Native
 *     comment permissions, visibility restrictions and edit/delete
 *     rules apply unchanged.
 *   * Because the saved comment contains a real [~username] mention,
 *     Jira's built-in mention machinery fires the standard email /
 *     notification to the mentioned user automatically. We do not
 *     duplicate that pipeline.
 *   * The CorbitChat plugin already listens to CommentCreatedEvent and
 *     parses [~username] mentions via JimMentionParser, so a Jira
 *     Assistant entry appears in the mentioned user's assistant
 *     conversation automatically.
 *   * Because the quoted parent text is embedded in the reply body,
 *     the reply relationship persists in Jira's own data model. No
 *     plugin-side AO entity is needed. The relationship is visible
 *     after page refresh, when the issue is reopened later, in
 *     activity exports, in Jira mobile, etc.
 *   * On top of that, this script also injects a small "in reply to
 *     <user>" badge above every comment whose body starts with the
 *     reply marker - cleaner visual cue than the bare wiki quote.
 *
 * The script is intentionally defensive: if Jira's DOM differs from
 * what we expect on a given install, we degrade silently rather than
 * throw. Reply is purely additive - the native Jira comment UI is
 * never modified or replaced.
 */
(function () {
    'use strict';

    if (window.__jimCommentReplyLoaded) {
        return; // Guard against duplicate web-resource loads.
    }
    window.__jimCommentReplyLoaded = true;

    var REPLY_BUTTON_CLASS = 'jim-comment-reply-btn';
    var REPLY_BADGE_CLASS = 'jim-comment-reply-badge';
    var INSTALLED_FLAG = 'jimReplyInstalled';
    // Quote / mention markers must match Jira's wiki renderer exactly.
    // [~username] - native mention; {quote}...{quote} - native blockquote.
    var QUOTE_PATTERN = /^\s*\[\~([A-Za-z0-9._\-@+]+)\]\s*\{quote\}([\s\S]*?)\{quote\}/m;
    var EXCERPT_MAX = 200;

    function currentUsername() {
        var meta = document.querySelector('meta[name="ajs-remote-user"]');
        return meta ? (meta.getAttribute('content') || '') : '';
    }

    function trimTo(text, limit) {
        var clean = String(text || '').replace(/\s+/g, ' ').trim();
        if (clean.length <= limit) {
            return clean;
        }
        return clean.substring(0, limit - 1).trim() + '\u2026';
    }

    function authorOfComment(commentEl) {
        // Jira renders the author as <a class="user-hover" rel="username">
        // and/or <a class="user-avatar" data-username="...">. Try a few
        // stable selectors before falling back to data attributes.
        if (!commentEl) return null;
        var link = commentEl.querySelector(
            '.action-details a.user-hover[rel],' +
            ' .action-details a[data-username],' +
            ' .activity-comment-author a[rel]'
        );
        if (link) {
            var name = link.getAttribute('rel') || link.getAttribute('data-username');
            var display = (link.textContent || '').trim();
            return {
                username: (name || '').trim(),
                displayName: display || (name || '')
            };
        }
        return null;
    }

    function bodyTextOfComment(commentEl) {
        if (!commentEl) return '';
        var body = commentEl.querySelector('.action-body, .twixi-block .action-body, .activity-comment-body');
        if (!body) return '';
        // textContent strips Jira's rendered HTML (links, mentions, etc.)
        // so we don't carry rich-text back into the quote.
        return body.textContent || '';
    }

    function commentIdOf(commentEl) {
        if (!commentEl) return null;
        // Jira DC renders <div id="comment-<id>" class="activity-comment">
        var raw = commentEl.id || '';
        var m = /comment-(\d+)/.exec(raw);
        if (m) return m[1];
        var data = commentEl.getAttribute('data-id') || commentEl.getAttribute('rel');
        return data || null;
    }

    /**
     * Returns Jira's main comment editor textarea. The native add-comment
     * button (#footer-comment-button) is clicked first if the editor is
     * not currently visible. Returns null if we couldn't make it appear.
     */
    function openCommentEditor() {
        var textarea = document.getElementById('comment');
        if (textarea && textarea.offsetParent !== null) {
            return textarea;
        }
        var footerBtn = document.getElementById('footer-comment-button')
            || document.querySelector('#commentadd, a[href*="AddComment"]');
        if (footerBtn) {
            try { footerBtn.click(); } catch (e) { /* ignore */ }
        }
        // Editor may take a tick to appear. Caller polls.
        return null;
    }

    function whenCommentEditorReady(callback, timeoutMs) {
        var deadline = Date.now() + (timeoutMs || 2500);
        function poll() {
            var ta = document.getElementById('comment');
            if (ta && ta.offsetParent !== null) {
                callback(ta);
                return;
            }
            if (Date.now() > deadline) {
                callback(null);
                return;
            }
            window.setTimeout(poll, 60);
        }
        poll();
    }

    /**
     * Pre-fills the editor with a native Jira mention + quote block.
     * Inserts ABOVE any existing draft text the user has already typed.
     * The cursor is left after the quote so the user can type their
     * reply straight away.
     */
    function prefillReply(textarea, author, parentBody) {
        var mention = '[~' + author.username + ']';
        var excerpt = trimTo(parentBody, EXCERPT_MAX);
        var quoted = excerpt
            ? '{quote}' + excerpt + '{quote}'
            : '{quote}(parent comment){quote}';
        var prefill = mention + '\n\n' + quoted + '\n\n';

        var existing = textarea.value || '';
        if (existing.indexOf(prefill) === 0) {
            // already prefilled (user double-clicked Reply); just focus
            textarea.focus();
            try { textarea.setSelectionRange(prefill.length, prefill.length); } catch (e) { /* ignore */ }
            return;
        }
        textarea.value = prefill + existing;
        textarea.dispatchEvent(new Event('input', { bubbles: true }));
        textarea.focus();
        try {
            textarea.setSelectionRange(prefill.length, prefill.length);
            textarea.scrollIntoView({ behavior: 'smooth', block: 'center' });
        } catch (e) { /* ignore */ }

        // Persist a hint that this draft is a reply, so navigating away
        // and back recovers the context. Best-effort - sessionStorage
        // may be unavailable in private mode.
        try {
            window.sessionStorage.setItem('jim-comment-reply-author', author.username);
        } catch (e) { /* ignore */ }
    }

    function onReplyClick(event, commentEl) {
        event.preventDefault();
        var author = authorOfComment(commentEl);
        if (!author || !author.username) {
            return;
        }
        var me = currentUsername();
        // Self-reply: keep the quote but skip the @-mention so we don't
        // notify the user about their own comment. Use a placeholder so
        // the layout still reads "in reply to".
        var effectiveAuthor = (author.username && author.username === me)
            ? { username: '', displayName: author.displayName }
            : author;
        var parentBody = bodyTextOfComment(commentEl);

        openCommentEditor();
        whenCommentEditorReady(function (textarea) {
            if (!textarea) {
                return;
            }
            if (effectiveAuthor.username) {
                prefillReply(textarea, effectiveAuthor, parentBody);
            } else {
                // Self-reply: just quote, no mention.
                var excerpt = trimTo(parentBody, EXCERPT_MAX);
                var quoted = excerpt
                    ? '{quote}' + excerpt + '{quote}\n\n'
                    : '{quote}(parent comment){quote}\n\n';
                if (textarea.value.indexOf(quoted) !== 0) {
                    textarea.value = quoted + (textarea.value || '');
                    textarea.dispatchEvent(new Event('input', { bubbles: true }));
                }
                textarea.focus();
                try { textarea.setSelectionRange(quoted.length, quoted.length); } catch (e) { /* ignore */ }
            }
        });
    }

    /**
     * Adds a "Reply" anchor to a comment's existing action list, next to
     * Edit / Delete. Skips if already added (re-render safety).
     */
    function injectReplyButton(commentEl) {
        if (!commentEl || commentEl.dataset[INSTALLED_FLAG] === '1') {
            return;
        }
        var author = authorOfComment(commentEl);
        if (!author || !author.username) {
            // Can't reply to a comment with no resolvable author.
            commentEl.dataset[INSTALLED_FLAG] = '1';
            return;
        }
        var actions = commentEl.querySelector('.actions, .action-links, .activity-actions');
        if (!actions) {
            return;
        }

        var li;
        if (actions.tagName === 'UL') {
            li = document.createElement('li');
            li.className = REPLY_BUTTON_CLASS + '-item';
            actions.appendChild(li);
        } else {
            li = actions;
        }

        var btn = document.createElement('a');
        btn.href = '#';
        btn.className = REPLY_BUTTON_CLASS;
        btn.textContent = 'Reply';
        btn.setAttribute('title', 'Reply to ' + author.displayName);
        btn.addEventListener('click', function (event) {
            onReplyClick(event, commentEl);
        });
        if (li !== actions) {
            li.appendChild(btn);
        } else {
            // Bare container; insert a separator span before the button
            if (actions.lastElementChild) {
                actions.appendChild(document.createTextNode(' '));
            }
            actions.appendChild(btn);
        }
        commentEl.dataset[INSTALLED_FLAG] = '1';

        // Also try to detect "in reply to" pattern at the start of THIS
        // comment's rendered body and decorate it with a badge so the
        // user immediately sees the relationship for replies created via
        // this plugin (or by any user manually quoting + mentioning).
        injectReplyBadge(commentEl);
    }

    /**
     * Looks at the comment body text (rendered or raw) for our mention +
     * quote prefix, and inserts a small "in reply to <username>" header
     * above the action body so the relationship is visible without the
     * user having to read the wiki-rendered quote.
     */
    function injectReplyBadge(commentEl) {
        var body = commentEl.querySelector('.action-body');
        if (!body || body.querySelector('.' + REPLY_BADGE_CLASS)) {
            return;
        }
        // The first child node of the rendered body is typically a <p>
        // containing the mention <a class="user-hover"> + a {quote}
        // turned into <blockquote>. We detect by structure.
        var firstP = body.querySelector(':scope > p:first-child');
        var firstQuote = body.querySelector(':scope > blockquote:first-of-type, :scope > p:first-child + blockquote');
        if (!firstP || !firstQuote) {
            return;
        }
        var mentionLink = firstP.querySelector('a.user-hover[rel], a[data-username]');
        if (!mentionLink) {
            return;
        }
        var mentionedName = mentionLink.getAttribute('rel') || mentionLink.getAttribute('data-username') || '';
        var mentionedDisplay = (mentionLink.textContent || mentionedName).trim();
        if (!mentionedDisplay) {
            return;
        }

        var badge = document.createElement('div');
        badge.className = REPLY_BADGE_CLASS;
        badge.innerHTML = '<span class="' + REPLY_BADGE_CLASS + '-icon" aria-hidden="true">\u21B3</span> ' +
            '<span class="' + REPLY_BADGE_CLASS + '-label">In reply to</span> ' +
            '<strong class="' + REPLY_BADGE_CLASS + '-name"></strong>';
        badge.querySelector('strong').textContent = mentionedDisplay;
        body.insertBefore(badge, body.firstChild);
    }

    /**
     * Find every comment under the activity feed and inject the button.
     * Used on initial render and on each MutationObserver tick. Idempotent.
     */
    function scanForComments(root) {
        var nodes = (root || document).querySelectorAll('.activity-comment');
        for (var i = 0; i < nodes.length; i++) {
            injectReplyButton(nodes[i]);
        }
    }

    /**
     * Jira loads issue activity (and re-renders it after add/edit/delete)
     * via AJAX, so we watch the issue actions container with a
     * MutationObserver to attach Reply to comments as they appear.
     */
    function startObserver() {
        var container = document.getElementById('issue_actions_container')
            || document.querySelector('.issue-data-block .issue-actions')
            || document.body;
        if (!container) {
            return;
        }
        scanForComments(container);
        if (typeof window.MutationObserver === 'undefined') {
            return;
        }
        var observer = new MutationObserver(function (mutations) {
            for (var i = 0; i < mutations.length; i++) {
                var m = mutations[i];
                if (m.addedNodes && m.addedNodes.length) {
                    for (var j = 0; j < m.addedNodes.length; j++) {
                        var node = m.addedNodes[j];
                        if (node.nodeType !== 1) continue;
                        if (node.classList && node.classList.contains('activity-comment')) {
                            injectReplyButton(node);
                        } else if (node.querySelectorAll) {
                            scanForComments(node);
                        }
                    }
                }
            }
        });
        observer.observe(container, { childList: true, subtree: true });
    }

    function init() {
        // Only run on issue pages. Jira sets a body class for that.
        if (document.body && document.body.classList &&
                !document.body.classList.contains('jira-view-issue-page') &&
                !document.querySelector('#issue_actions_container, .issue-body, .issue-container')) {
            // Try anyway - we'll early-return inside scanForComments
            // if there are no .activity-comment elements.
        }
        startObserver();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
