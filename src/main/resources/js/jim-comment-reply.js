/**
 * CorbitChat - Jira issue comment "Reply" capability.
 *
 * Design (v2 - inline composer):
 *
 *   - Every native Jira comment gets a "Reply" link in its action
 *     toolbar.
 *   - Clicking Reply opens an INLINE composer right below the parent
 *     comment, with:
 *       * a clear "Replying to <author>" header (with a cancel X),
 *       * a quoted preview of the parent comment,
 *       * an empty textarea (autofocused),
 *       * Cancel and Send Reply buttons.
 *   - Send Reply POSTs a new comment to Jira's standard REST endpoint
 *     /rest/api/2/issue/{key}/comment with body assembled as:
 *
 *         [~parentAuthor]
 *
 *         {quote}<parent excerpt>{quote}
 *
 *         <user text>
 *
 *     i.e. a real Jira wiki-recognized mention + a real wiki quote.
 *   - On success the page is reloaded with the new comment focused so
 *     the comment list refreshes via Jira's own server-side render and
 *     scrolls straight to the reply. This is intentional: it is far
 *     more reliable across all Jira DC issue-view variants than trying
 *     to surgically inject the new comment HTML.
 *
 * Why an inline composer instead of driving Jira's own wiki editor?
 *
 *   The previous version tried to prefill Jira's `#comment` textarea.
 *   Jira's wiki editor is a Visual/Source overlay on top of the
 *   textarea, so setting `.value` only takes effect when the user
 *   happens to be in Source mode - in Visual mode the rich layer
 *   silently discards the change. That made the previous behavior
 *   unreliable. An inline composer that POSTs via REST avoids the
 *   wiki-editor entirely and guarantees the saved comment is a real
 *   Jira comment with a real Jira mention.
 *
 * Outcome guarantees (all already proven in the AO database):
 *
 *   - The saved reply is a native Jira comment (POST /rest/api/2/issue/
 *     /{key}/comment), so native Jira permissions, visibility and edit
 *     rules apply unchanged.
 *   - The body contains a real `[~username]` mention, so Jira's own
 *     mention machinery emits the standard email / in-app notification
 *     (subject to the user's notification settings and the issue's
 *     notification scheme).
 *   - CorbitChat's existing CommentCreatedEvent listener picks up the
 *     comment, parses the mention via JimMentionParser and posts a
 *     Jira Assistant entry into the mentioned user's bot conversation
 *     - the entry already includes issue key, actor name, body preview
 *     and a link back to the issue.
 *   - Refresh-safe: the reply relationship lives inside the comment
 *     body itself ({quote}+mention), so it is visible after refresh,
 *     after reopening the issue later, in mobile, in exports - this
 *     script just sugars it with an "In reply to <user>" badge above
 *     such comments.
 */
(function () {
    'use strict';

    if (window.__jimCommentReplyLoaded) {
        return;
    }
    window.__jimCommentReplyLoaded = true;

    var VERSION = '1.0.0-internal-replies5';
    var BTN_CLASS = 'jim-comment-reply-btn';
    var COMPOSER_CLASS = 'jim-comment-reply-composer';
    var BADGE_CLASS = 'jim-comment-reply-badge';
    var INSTALLED_FLAG = 'jimReplyInstalled';
    var BADGE_FLAG = 'jimReplyBadged';
    var EXCERPT_MAX = 240;

    // Verbose logging is enabled by ?jimReplyDebug=1 in the URL or by
    // localStorage.jimReplyDebug = '1'. A single concise startup
    // banner is always logged so the operator can confirm the JS is
    // actually loading on a given page.
    var DEBUG = false;
    try {
        if (/[?&]jimReplyDebug=1/.test(window.location.search || '')) DEBUG = true;
        if (window.localStorage && window.localStorage.getItem('jimReplyDebug') === '1') DEBUG = true;
    } catch (e) { /* ignore - private mode, etc. */ }
    function log() {
        if (!DEBUG || !window.console) return;
        try { console.log.apply(console, ['[CorbitChat reply]'].concat([].slice.call(arguments))); }
        catch (e) { /* ignore */ }
    }
    function info(msg) {
        if (!window.console) return;
        try { console.info('[CorbitChat reply ' + VERSION + '] ' + msg); }
        catch (e) { /* ignore */ }
    }

    // Counters surfaced through window.JimCommentReply for ops debugging.
    var stats = {
        version: VERSION,
        observedAt: null,
        commentsSeen: 0,
        buttonsInjected: 0,
        badgesInjected: 0,
        composersOpened: 0,
        repliesSent: 0,
        repliesFailed: 0,
        authorLookupSuccess: 0,
        authorLookupFailed: 0
    };

    // -----------------------------------------------------------------
    // Context resolution from the page (meta tags Jira always emits).
    // -----------------------------------------------------------------
    function metaContent(name) {
        var el = document.querySelector('meta[name="' + name + '"]');
        return el ? (el.getAttribute('content') || '') : '';
    }
    function queryParam(name) {
        try {
            var m = new RegExp('[?&]' + name + '=([^&#]+)').exec(window.location.search || '');
            return m ? decodeURIComponent(m[1]) : '';
        } catch (e) { return ''; }
    }
    /**
     * Resolve the parent comment's issue context. Returns {key, id}
     * where at least one is populated. Jira's REST comment endpoint
     * accepts either form (`/rest/api/2/issue/<KEY>/comment` or
     * `/rest/api/2/issue/<NUMERIC_ID>/comment`).
     *
     * This is robust across Jira DC issue-view variants:
     *   - /browse/<KEY>          - standalone issue page (meta tag works)
     *   - /projects/.../issues/  - project-centric navigator (meta tag
     *                              is often empty / lags - we read the
     *                              comment block's own Edit/Delete/Pin
     *                              elements which always carry the
     *                              issue id and key).
     *   - /issues/?selectedIssue - global issue navigator (read from
     *                              the URL query string).
     */
    function issueRefForComment(commentEl) {
        var key = '';
        var id  = '';

        // 1. Meta tag (works on /browse/<KEY>).
        key = metaContent('ajs-issue-key') || key;

        // 2. The comment block's own Pin custom element carries both.
        //    Jira renders:
        //      <jira-comment-pins data-commentid=".." data-issueid=".."
        //                         data-issuekey=".." data-pinned=".."/>
        if (commentEl) {
            var pin = commentEl.querySelector('jira-comment-pins, [data-issuekey], [data-issueid]');
            if (pin) {
                key = key || pin.getAttribute('data-issuekey') || '';
                id  = id  || pin.getAttribute('data-issueid')  || '';
            }
        }

        // 3. The comment block's Edit / Delete links carry the issue
        //    numeric id as a query parameter:
        //      .../secure/EditComment!default.jspa?id=<ISSUE_ID>&commentId=N
        if (commentEl && !id) {
            var link = commentEl.querySelector(
                '.edit-comment[href*="id="], .delete-comment[href*="id="],' +
                ' a[href*="EditComment"], a[href*="DeleteComment"]'
            );
            if (link) {
                var m = /[?&]id=(\d+)/.exec(link.getAttribute('href') || '');
                if (m) id = m[1];
            }
        }

        // 4. Ancestor data-issue-key (some issue-view variants set this
        //    on the surrounding container, e.g. .issue-container).
        if (!key && commentEl) {
            var node = commentEl;
            while (node && node !== document) {
                var k2 = node.getAttribute && node.getAttribute('data-issue-key');
                if (k2) { key = k2; break; }
                node = node.parentNode;
            }
        }

        // 5. URL fallbacks: /browse/<KEY> path, ?selectedIssue=<KEY>,
        //    ?issueKey=<KEY> query string.
        if (!key) {
            var pm = /\/browse\/([A-Z][A-Z0-9_]*-\d+)/.exec(window.location.pathname || '');
            if (pm) key = pm[1];
        }
        if (!key) {
            key = queryParam('selectedIssue') || queryParam('issueKey') || '';
        }

        // 6. Body-level data attribute (rare but cheap to check).
        if (!key && document.body) {
            key = document.body.getAttribute('data-issue-key') || key;
            id  = id || document.body.getAttribute('data-issue-id') || '';
        }

        if (!key && !id) return null;
        return { key: key, id: id };
    }
    function issueKey() {
        // Back-compat helper used outside the click path. The click
        // path uses the more specific issueRefForComment().
        var ref = issueRefForComment(null);
        return ref ? (ref.key || ref.id || '') : '';
    }
    function contextPath() {
        if (window.AJS && AJS.contextPath) {
            try { return AJS.contextPath() || ''; } catch (e) { /* ignore */ }
        }
        var b = document.querySelector('base');
        return b ? (b.getAttribute('href') || '').replace(/\/$/, '') : '';
    }
    function currentUsername() {
        return metaContent('ajs-remote-user');
    }
    function atlToken() {
        // Jira's standard XSRF token for state-changing requests. The
        // REST endpoint accepts the X-Atlassian-Token: no-check header
        // for AJAX calls; if the token meta is present we send both.
        var el = document.getElementById('atlassian-token')
            || document.querySelector('meta[name="atlassian-token"]');
        return el ? (el.getAttribute('content') || '') : '';
    }

    // -----------------------------------------------------------------
    // DOM helpers around a native Jira comment block.
    //   The Jira template (atlassian-jira/.../system-comment-issue-page-view.vm)
    //   renders each comment as:
    //     <div id="comment-{id}" class="issue-data-block activity-comment twixi-block ...">
    //       <div class="twixi-wrap verbose actionContainer">
    //         <div class="action-head">
    //           <div class="action-details">{author + date}</div>
    //         </div>
    //         <div class="action-body flooded">{rendered body}</div>
    //         <div class="action-links action-comment-actions">
    //           <a class="edit-comment ...">Edit</a>
    //           <a class="delete-comment ...">Delete</a>
    //         </div>
    //       </div>
    //     </div>
    // -----------------------------------------------------------------
    function commentIdOf(el) {
        if (!el) return null;
        var m = /comment-(\d+)/.exec(el.id || '');
        return m ? m[1] : null;
    }
    /**
     * Find the author of a native Jira comment block. Tolerant of many
     * DOM variants we have encountered across Jira DC issue-view
     * surfaces (standalone /browse, project-centric navigator, global
     * navigator, Service Desk agent view, etc.).
     *
     * Returns {username, displayName} or null. Caller must handle null
     * gracefully - the Reply button itself still injects without an
     * author (it just opens the composer with no auto-mention).
     */
    function isJunkRelValue(rel) {
        return !rel || /^(nofollow|noopener|noreferrer|external|tag|alternate)$/i.test(rel);
    }
    function parseAuthorFromAnchor(a) {
        if (!a) return null;
        var name = a.getAttribute('data-username') || '';
        if (!name) {
            var rel = a.getAttribute('rel') || '';
            if (!isJunkRelValue(rel)) name = rel;
        }
        if (!name) {
            var href = a.getAttribute('href') || '';
            var m = /[?&]name=([^&#]+)/.exec(href);
            if (m) {
                try { name = decodeURIComponent(m[1]); } catch (e) { name = m[1]; }
            }
        }
        var display = (a.textContent || '').trim();
        if (!name && !display) return null;
        return { username: (name || '').trim(), displayName: display || name };
    }
    function authorOfComment(commentEl) {
        if (!commentEl) return null;
        var head = commentEl.querySelector('.action-details') || commentEl.querySelector('.action-head') || commentEl;

        // 1) Canonical user-hover anchor.
        var a = head.querySelector('a.user-hover[rel]');
        var parsed = parseAuthorFromAnchor(a);
        if (parsed && parsed.username) return parsed;

        // 2) Any anchor with data-username.
        a = head.querySelector('a[data-username]');
        parsed = parseAuthorFromAnchor(a);
        if (parsed && parsed.username) return parsed;

        // 3) Any anchor pointing at ViewProfile.jspa - the username is
        // in the name= query param.
        a = head.querySelector('a[href*="ViewProfile.jspa"], a[href*="ViewProfile!"], a[href*="people/"]');
        parsed = parseAuthorFromAnchor(a);
        if (parsed && parsed.username) return parsed;

        // 4) Any anchor with a non-junk rel.
        var anchors = head.querySelectorAll('a[rel]');
        for (var i = 0; i < anchors.length; i++) {
            if (!isJunkRelValue(anchors[i].getAttribute('rel'))) {
                parsed = parseAuthorFromAnchor(anchors[i]);
                if (parsed && parsed.username) return parsed;
            }
        }

        // 5) Last resort - return display name only so the composer
        // can show "Replying to <Name>" even if we can't auto-mention.
        var anyLink = head.querySelector('a');
        if (anyLink) {
            var dn = (anyLink.textContent || '').trim();
            if (dn) return { username: '', displayName: dn };
        }
        return null;
    }
    function rawBodyTextOfComment(commentEl) {
        if (!commentEl) return '';
        var body = commentEl.querySelector('.action-body');
        return body ? (body.textContent || '').trim() : '';
    }
    function actionToolbarOf(commentEl) {
        // The action toolbar can be `.action-links.action-comment-actions`
        // (modern Jira DC) or `.actions`/`ul.action-links` (legacy).
        return commentEl
            ? commentEl.querySelector('.action-links.action-comment-actions, .action-links, .actions')
            : null;
    }

    function trimTo(text, limit) {
        var clean = String(text || '').replace(/[\r\n]+/g, ' ').replace(/\s{2,}/g, ' ').trim();
        if (clean.length <= limit) return clean;
        return clean.substring(0, limit - 1).trim() + '\u2026';
    }

    // -----------------------------------------------------------------
    // Inline composer: rendered right after the parent comment block.
    // Only one composer is open at a time across the whole page.
    // -----------------------------------------------------------------
    function closeAnyOpenComposer() {
        var existing = document.querySelector('.' + COMPOSER_CLASS);
        if (existing) {
            // Drop any staged attachments that were uploaded but never
            // referenced from a saved comment. We delete them from the
            // issue so we don't leave orphan attachments behind when
            // the user cancels the reply.
            cleanupStagedAttachments(existing, /*reason=*/'composer-closed');
            if (existing.parentNode) {
                existing.parentNode.removeChild(existing);
            }
        }
        // Re-enable all Reply buttons.
        var btns = document.querySelectorAll('.' + BTN_CLASS);
        for (var i = 0; i < btns.length; i++) {
            btns[i].classList.remove(BTN_CLASS + '--disabled');
            btns[i].removeAttribute('aria-disabled');
        }
    }

    // -----------------------------------------------------------------
    // Reply-composer attachment support.
    //
    // Each open composer maintains a list of "staged" attachments on
    // composer._stagedAttachments. An attachment is uploaded straight
    // to the issue via Jira's standard
    // `POST /rest/api/2/issue/<keyOrId>/attachments` endpoint as soon
    // as the user selects it (so the user sees per-file success /
    // failure before they hit Send). On submit we prepend the
    // appropriate wiki markup to the comment body so Jira's renderer
    // shows the attachments inline (images become thumbnails; other
    // files become clickable links). If the user removes a chip OR
    // cancels the composer we DELETE the orphaned attachments so we
    // do not leave stray files on the issue.
    // -----------------------------------------------------------------
    var IMAGE_TYPE_RE = /^image\/(png|jpe?g|gif|webp|bmp|svg\+xml|tiff?)$/i;
    var IMAGE_EXT_RE  = /\.(png|jpe?g|gif|webp|bmp|svg|tif|tiff)$/i;

    function isImageAttachment(att) {
        if (!att) return false;
        var mime = att.mimeType || '';
        var name = att.filename || '';
        return IMAGE_TYPE_RE.test(mime) || IMAGE_EXT_RE.test(name);
    }

    /**
     * Wiki markup token for an attachment as a Jira renderer will
     * resolve it inside the comment body. Images become inline
     * thumbnails so the user sees them rendered; other files become
     * clickable attachment links.
     */
    function wikiForAttachment(att) {
        if (!att || !att.filename) return '';
        var safeName = att.filename.replace(/[!\[\]\|\\]/g, '_');
        return isImageAttachment(att)
            ? '!' + safeName + '|thumbnail!'
            : '[^' + safeName + ']';
    }

    /**
     * Synchronously DELETEs each staged attachment from the issue.
     * Best-effort: failures are logged but never block the close /
     * cancel flow. We do not await; the request is fire-and-forget.
     */
    function cleanupStagedAttachments(composer, reason) {
        var list = composer && composer._stagedAttachments;
        if (!list || !list.length) return;
        for (var i = 0; i < list.length; i++) {
            var att = list[i];
            if (!att || !att.id) continue;
            var url = contextPath() + '/rest/api/2/attachment/' + encodeURIComponent(att.id);
            try {
                var xhr = new XMLHttpRequest();
                xhr.open('DELETE', url, true);
                xhr.setRequestHeader('X-Atlassian-Token', 'no-check');
                xhr.withCredentials = true;
                xhr.send();
                log('cleanup attachment', att.id, 'reason=' + reason);
            } catch (e) { /* swallow */ }
        }
        composer._stagedAttachments = [];
    }

    /**
     * Renders the chip strip for currently-staged attachments. Called
     * after every add / remove. Each chip shows the filename and a
     * small (×) button that removes the attachment and DELETEs it
     * from the issue.
     */
    function renderAttachmentChips(composer) {
        var strip = composer.querySelector('.' + COMPOSER_CLASS + '-attachments');
        if (!strip) return;
        strip.innerHTML = '';
        var list = composer._stagedAttachments || [];
        if (!list.length) {
            strip.setAttribute('hidden', 'hidden');
            return;
        }
        strip.removeAttribute('hidden');
        for (var i = 0; i < list.length; i++) {
            (function (att) {
                var chip = document.createElement('span');
                chip.className = COMPOSER_CLASS + '-attachment-chip';
                if (att.uploading) {
                    chip.classList.add(COMPOSER_CLASS + '-attachment-chip-uploading');
                }
                if (att.error) {
                    chip.classList.add(COMPOSER_CLASS + '-attachment-chip-error');
                }
                var icon = document.createElement('span');
                icon.className = COMPOSER_CLASS + '-attachment-icon';
                icon.setAttribute('aria-hidden', 'true');
                icon.textContent = isImageAttachment(att) ? '\uD83D\uDDBC' : '\uD83D\uDCCE';
                var name = document.createElement('span');
                name.className = COMPOSER_CLASS + '-attachment-name';
                name.textContent = att.filename + (att.uploading ? ' (uploading\u2026)' : '');
                var remove = document.createElement('button');
                remove.type = 'button';
                remove.className = COMPOSER_CLASS + '-attachment-remove';
                remove.setAttribute('aria-label', 'Remove attachment');
                remove.innerHTML = '\u00D7';
                remove.addEventListener('click', function () {
                    removeStagedAttachment(composer, att);
                });
                chip.appendChild(icon);
                chip.appendChild(name);
                chip.appendChild(remove);
                if (att.error) {
                    var errSpan = document.createElement('span');
                    errSpan.className = COMPOSER_CLASS + '-attachment-error';
                    errSpan.textContent = att.error;
                    chip.appendChild(errSpan);
                }
                strip.appendChild(chip);
            })(list[i]);
        }
    }

    function removeStagedAttachment(composer, att) {
        var list = composer._stagedAttachments || [];
        var idx = list.indexOf(att);
        if (idx >= 0) list.splice(idx, 1);
        renderAttachmentChips(composer);
        if (att && att.id) {
            // DELETE the file from the issue so we don't leak it.
            var url = contextPath() + '/rest/api/2/attachment/' + encodeURIComponent(att.id);
            try {
                var xhr = new XMLHttpRequest();
                xhr.open('DELETE', url, true);
                xhr.setRequestHeader('X-Atlassian-Token', 'no-check');
                xhr.withCredentials = true;
                xhr.send();
            } catch (e) { /* swallow */ }
        }
    }

    function uploadAttachmentForReply(composer, file, commentEl) {
        if (!file) return;
        var ref = issueRefForComment(commentEl);
        var keyOrId = ref ? (ref.key || ref.id || '') : '';
        if (!keyOrId) {
            setStatus(composer.querySelector('.' + COMPOSER_CLASS + '-status'),
                'Cannot detect this issue\u2019s key for the upload.', 'error');
            return;
        }
        composer._stagedAttachments = composer._stagedAttachments || [];
        var staged = {
            id: null,
            filename: file.name,
            mimeType: file.type || '',
            uploading: true,
            error: null
        };
        composer._stagedAttachments.push(staged);
        renderAttachmentChips(composer);

        var form = new FormData();
        form.append('file', file, file.name);
        var url = contextPath() + '/rest/api/2/issue/' + encodeURIComponent(keyOrId) + '/attachments';
        var xhr = new XMLHttpRequest();
        xhr.open('POST', url, true);
        xhr.setRequestHeader('Accept', 'application/json');
        xhr.setRequestHeader('X-Atlassian-Token', 'no-check');
        xhr.withCredentials = true;
        xhr.onreadystatechange = function () {
            if (xhr.readyState !== 4) return;
            staged.uploading = false;
            if (xhr.status === 200 || xhr.status === 201) {
                try {
                    var resp = JSON.parse(xhr.responseText);
                    var att = Array.isArray(resp) && resp.length ? resp[0] : resp;
                    if (att && att.id) {
                        staged.id = att.id;
                        // Server may have normalized the filename.
                        staged.filename = att.filename || staged.filename;
                        staged.mimeType = att.mimeType || staged.mimeType;
                    } else {
                        staged.error = 'Upload failed';
                    }
                } catch (e) {
                    staged.error = 'Upload failed: invalid response';
                }
            } else {
                var detail = '';
                try {
                    var err = JSON.parse(xhr.responseText);
                    if (err && err.errorMessages && err.errorMessages.length) {
                        detail = err.errorMessages.join(' ');
                    }
                } catch (e) { /* ignore */ }
                staged.error = 'Upload failed (' + xhr.status + ')'
                    + (detail ? ': ' + detail : '');
            }
            renderAttachmentChips(composer);
        };
        try { xhr.send(form); }
        catch (e) {
            staged.uploading = false;
            staged.error = 'Could not start upload: ' + (e && e.message ? e.message : 'unknown');
            renderAttachmentChips(composer);
        }
    }

    function openComposer(commentEl, author, parentExcerpt) {
        closeAnyOpenComposer();

        var me = currentUsername();
        var isSelf = !!author.username && author.username === me;

        var composer = document.createElement('div');
        composer.className = COMPOSER_CLASS;

        var header = document.createElement('div');
        header.className = COMPOSER_CLASS + '-header';
        var headerText = document.createElement('span');
        headerText.className = COMPOSER_CLASS + '-header-text';
        headerText.innerHTML =
            '<span class="' + COMPOSER_CLASS + '-header-arrow" aria-hidden="true">\u21B3</span> ' +
            'Replying to <strong></strong>' +
            (isSelf ? ' <em>(your own comment)</em>' : '');
        headerText.querySelector('strong').textContent = author.displayName;
        var cancelX = document.createElement('button');
        cancelX.type = 'button';
        cancelX.className = COMPOSER_CLASS + '-close';
        cancelX.setAttribute('aria-label', 'Cancel reply');
        cancelX.innerHTML = '\u00D7';
        cancelX.addEventListener('click', closeAnyOpenComposer);
        header.appendChild(headerText);
        header.appendChild(cancelX);

        var quote = document.createElement('blockquote');
        quote.className = COMPOSER_CLASS + '-quote';
        quote.textContent = parentExcerpt || '(parent comment)';

        var textarea = document.createElement('textarea');
        textarea.className = COMPOSER_CLASS + '-textarea';
        textarea.rows = 4;
        textarea.placeholder = 'Type your reply\u2026';

        var status = document.createElement('div');
        status.className = COMPOSER_CLASS + '-status';
        status.setAttribute('aria-live', 'polite');

        // Attachment strip (hidden until the user adds at least one
        // file). Renders one chip per staged attachment with a (×)
        // button to remove it; uploads happen as soon as the file is
        // chosen so the user sees per-file success / failure.
        var attachmentsStrip = document.createElement('div');
        attachmentsStrip.className = COMPOSER_CLASS + '-attachments';
        attachmentsStrip.setAttribute('hidden', 'hidden');

        var fileInput = document.createElement('input');
        fileInput.type = 'file';
        fileInput.multiple = true;
        fileInput.className = COMPOSER_CLASS + '-file-input';
        fileInput.setAttribute('aria-hidden', 'true');
        fileInput.tabIndex = -1;
        fileInput.addEventListener('change', function () {
            if (!fileInput.files || !fileInput.files.length) return;
            for (var i = 0; i < fileInput.files.length; i++) {
                uploadAttachmentForReply(composer, fileInput.files[i], commentEl);
            }
            // Reset so re-selecting the same file later still fires.
            fileInput.value = '';
        });

        var actions = document.createElement('div');
        actions.className = COMPOSER_CLASS + '-actions';

        // Attach button on the left side of the action row so the user
        // can pick files without leaving the reply composer.
        var attachBtn = document.createElement('button');
        attachBtn.type = 'button';
        attachBtn.className = 'aui-button aui-button-link ' + COMPOSER_CLASS + '-attach';
        attachBtn.setAttribute('title', 'Attach file');
        // SVG paperclip icon - matches the visual weight of the AUI
        // toolbar icons used in Jira's native comment editor.
        attachBtn.innerHTML =
            '<svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true" focusable="false" style="vertical-align:-2px;margin-right:4px">' +
            '<path fill="currentColor" d="M16.5 6v10.5a4.5 4.5 0 1 1-9 0V5a3 3 0 1 1 6 0v10.5a1.5 1.5 0 1 1-3 0V6h1.5v9.5a.5.5 0 1 0 1 0V5a2 2 0 1 0-4 0v11.5a3.5 3.5 0 1 0 7 0V6z"/>' +
            '</svg>' +
            'Attach';
        attachBtn.addEventListener('click', function () {
            fileInput.click();
        });

        var cancelBtn = document.createElement('button');
        cancelBtn.type = 'button';
        cancelBtn.className = 'aui-button ' + COMPOSER_CLASS + '-cancel';
        cancelBtn.textContent = 'Cancel';
        cancelBtn.addEventListener('click', closeAnyOpenComposer);

        var sendBtn = document.createElement('button');
        sendBtn.type = 'button';
        sendBtn.className = 'aui-button aui-button-primary ' + COMPOSER_CLASS + '-send';
        sendBtn.textContent = 'Send Reply';
        sendBtn.addEventListener('click', function () {
            submitReply({
                commentEl: commentEl,
                author: author,
                parentExcerpt: parentExcerpt,
                isSelf: isSelf,
                textarea: textarea,
                sendBtn: sendBtn,
                cancelBtn: cancelBtn,
                status: status,
                composer: composer
            });
        });

        // attachBtn aligned left; cancel / send on the right side via
        // CSS (margin-left:auto on cancel).
        actions.appendChild(attachBtn);
        actions.appendChild(cancelBtn);
        actions.appendChild(sendBtn);

        composer.appendChild(header);
        composer.appendChild(quote);
        composer.appendChild(textarea);
        composer.appendChild(attachmentsStrip);
        composer.appendChild(fileInput);
        composer.appendChild(status);
        composer.appendChild(actions);

        // Drag-and-drop file support over the entire composer panel.
        // We intercept on the panel root so dropping a file anywhere
        // inside the chip strip / textarea / etc. all add it.
        var dragCounter = 0;
        function setDragActive(active) {
            composer.classList.toggle(COMPOSER_CLASS + '-drag-active', !!active);
        }
        composer.addEventListener('dragenter', function (e) {
            if (!e.dataTransfer || !Array.prototype.indexOf.call(e.dataTransfer.types || [], 'Files') >= 0) return;
            e.preventDefault();
            dragCounter++;
            setDragActive(true);
        });
        composer.addEventListener('dragover', function (e) {
            if (e.dataTransfer && e.dataTransfer.types && Array.prototype.indexOf.call(e.dataTransfer.types, 'Files') !== -1) {
                e.preventDefault();
                e.dataTransfer.dropEffect = 'copy';
            }
        });
        composer.addEventListener('dragleave', function () {
            dragCounter = Math.max(0, dragCounter - 1);
            if (dragCounter === 0) setDragActive(false);
        });
        composer.addEventListener('drop', function (e) {
            if (!e.dataTransfer || !e.dataTransfer.files || !e.dataTransfer.files.length) return;
            e.preventDefault();
            dragCounter = 0;
            setDragActive(false);
            var files = e.dataTransfer.files;
            for (var i = 0; i < files.length; i++) {
                uploadAttachmentForReply(composer, files[i], commentEl);
            }
        });

        // Track the staged-attachments list on the composer element so
        // closeAnyOpenComposer() and submitReply() can both find it.
        composer._stagedAttachments = [];

        // Insert immediately after the parent comment block.
        commentEl.parentNode.insertBefore(composer, commentEl.nextSibling);

        // Disable all Reply buttons while composer is open (single composer).
        var btns = document.querySelectorAll('.' + BTN_CLASS);
        for (var i = 0; i < btns.length; i++) {
            btns[i].classList.add(BTN_CLASS + '--disabled');
            btns[i].setAttribute('aria-disabled', 'true');
        }

        // Focus the textarea, scrolling into view if needed.
        window.setTimeout(function () {
            textarea.focus();
            try { textarea.scrollIntoView({ block: 'center', behavior: 'smooth' }); } catch (e) { /* ignore */ }
        }, 30);

        // Ctrl+Enter to submit, Esc to cancel.
        textarea.addEventListener('keydown', function (e) {
            if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
                e.preventDefault();
                sendBtn.click();
            } else if (e.key === 'Escape') {
                e.preventDefault();
                closeAnyOpenComposer();
            }
        });
    }

    function setStatus(status, message, kind) {
        status.textContent = message || '';
        status.className = COMPOSER_CLASS + '-status' +
            (kind ? ' ' + COMPOSER_CLASS + '-status--' + kind : '');
    }

    function submitReply(ctx) {
        var userText = (ctx.textarea.value || '').trim();
        var stagedAttachments = (ctx.composer && ctx.composer._stagedAttachments) || [];
        var goodAttachments = [];
        var hasUploading = false;
        for (var i = 0; i < stagedAttachments.length; i++) {
            var a = stagedAttachments[i];
            if (a.uploading) hasUploading = true;
            if (!a.error && a.id) goodAttachments.push(a);
        }
        if (hasUploading) {
            setStatus(ctx.status, 'Please wait for the attachment upload to finish.', 'error');
            return;
        }
        if (!userText && !goodAttachments.length) {
            setStatus(ctx.status, 'Please enter a reply or attach a file before sending.', 'error');
            ctx.textarea.focus();
            return;
        }
        // Resolve the issue context from the parent comment's own DOM
        // (works on /browse/<KEY> AND project-centric / global issue
        // navigator views where meta[name=ajs-issue-key] is unreliable).
        var ref = issueRefForComment(ctx.commentEl);
        var keyOrId = ref ? (ref.key || ref.id || '') : '';
        if (!keyOrId) {
            setStatus(ctx.status, 'Cannot detect this issue\u2019s key. Refresh and try again.', 'error');
            return;
        }
        var mention = (!ctx.isSelf && ctx.author.username)
            ? '[~' + ctx.author.username + ']\n\n'
            : '';
        var excerpt = trimTo(ctx.parentExcerpt, EXCERPT_MAX);
        var quoted = excerpt ? '{quote}' + excerpt + '{quote}\n\n' : '';
        // Attachment wiki markup is appended AFTER the user's reply
        // text so the text remains the first thing the reader sees;
        // images become inline thumbnails, other files become
        // clickable attachment links rendered by Jira itself.
        var attachmentLines = '';
        if (goodAttachments.length) {
            var parts = [];
            for (var k = 0; k < goodAttachments.length; k++) {
                var token = wikiForAttachment(goodAttachments[k]);
                if (token) parts.push(token);
            }
            if (parts.length) {
                attachmentLines = '\n\n' + parts.join('\n');
            }
        }
        var body = mention + quoted + (userText || '') + attachmentLines;

        ctx.sendBtn.disabled = true;
        ctx.cancelBtn.disabled = true;
        ctx.sendBtn.textContent = 'Sending\u2026';
        setStatus(ctx.status, '');

        var url = contextPath() + '/rest/api/2/issue/' + encodeURIComponent(keyOrId) + '/comment';
        var xhr = new XMLHttpRequest();
        xhr.open('POST', url, true);
        xhr.setRequestHeader('Content-Type', 'application/json');
        xhr.setRequestHeader('Accept', 'application/json');
        xhr.setRequestHeader('X-Atlassian-Token', 'no-check');
        xhr.withCredentials = true;
        xhr.onreadystatechange = function () {
            if (xhr.readyState !== 4) return;
            log('POST result', xhr.status, xhr.responseText && xhr.responseText.substring(0, 200));
            if (xhr.status === 201 || xhr.status === 200) {
                onReplySuccess(ctx, xhr);
            } else {
                onReplyFailure(ctx, xhr);
            }
        };
        try {
            xhr.send(JSON.stringify({ body: body }));
        } catch (e) {
            ctx.sendBtn.disabled = false;
            ctx.cancelBtn.disabled = false;
            ctx.sendBtn.textContent = 'Send Reply';
            setStatus(ctx.status, 'Could not send: ' + (e && e.message ? e.message : 'unknown error'), 'error');
        }
    }

    function onReplySuccess(ctx, xhr) {
        var created = null;
        try { created = JSON.parse(xhr.responseText); } catch (e) { /* ignore */ }
        var newId = created && created.id ? String(created.id) : '';
        if (!newId) {
            // We got a 2xx but no usable body - degrade gracefully.
            softReloadFallback(ctx);
            return;
        }
        setStatus(ctx.status, 'Reply sent.', 'ok');
        // Fetch the new comment WITH rendered HTML so we can drop it
        // into the activity feed without a full page reload.
        var ref = issueRefForComment(ctx.commentEl);
        var keyOrId = ref ? (ref.key || ref.id || '') : '';
        var url = contextPath() + '/rest/api/2/issue/' + encodeURIComponent(keyOrId)
            + '/comment/' + encodeURIComponent(newId) + '?expand=renderedBody';
        var fx = new XMLHttpRequest();
        fx.open('GET', url, true);
        fx.setRequestHeader('Accept', 'application/json');
        fx.withCredentials = true;
        fx.onreadystatechange = function () {
            if (fx.readyState !== 4) return;
            var fullComment = null;
            if (fx.status === 200) {
                try { fullComment = JSON.parse(fx.responseText); } catch (e) { /* ignore */ }
            }
            if (!fullComment) {
                // GET failed - synthesize from the POST response so the
                // user still sees their reply inline. The body will use
                // raw wiki source since we don't have the rendered HTML,
                // but it's better than no insertion at all.
                fullComment = created;
            }
            try {
                injectRenderedComment(fullComment, ctx);
            } catch (e) {
                log('inject failed', e && e.message);
                softReloadFallback(ctx);
            }
        };
        try { fx.send(); }
        catch (e) {
            log('GET rendered failed', e && e.message);
            softReloadFallback(ctx);
        }
    }

    /**
     * Hard fallback if in-place insertion can't proceed: reload with
     * focus on the new comment. Used only for unexpected failures.
     */
    function softReloadFallback(ctx) {
        setStatus(ctx.status, 'Reply sent. Refreshing\u2026', 'ok');
        window.setTimeout(function () {
            try { window.location.reload(); }
            catch (e) { /* swallow */ }
        }, 350);
    }

    function escapeText(s) {
        return String(s == null ? '' : s);
    }

    /**
     * Build a Jira-template-shaped activity-comment block in-memory
     * from a `/rest/api/2/issue/<>/comment/<>?expand=renderedBody` JSON
     * payload. This mirrors enough of Jira's own
     * `system-comment-issue-page-view.vm` template that the inserted
     * block visually matches a native comment and our own
     * MutationObserver picks it up to inject the Reply link + the
     * "In reply to" badge automatically.
     *
     * What we deliberately do NOT replicate:
     *   - <jira-comment-pins>     (pin / unpin custom element)
     *   - <jira-comment-reactions>(emoji reactions custom element)
     * Both are progressive enhancements wired up at page-load time by
     * their own plugins; the user can refresh the page to get them on
     * the new comment, but Reply / Edit / Delete all work immediately.
     */
    function buildCommentDom(comment) {
        if (!comment || !comment.id) return null;

        var ctxPath = contextPath();
        var author = comment.author || comment.updateAuthor || {};
        var aName = author.name || '';
        var aDisplay = author.displayName || aName || '?';
        var aAvatar = '';
        if (author.avatarUrls) {
            aAvatar = author.avatarUrls['48x48']
                || author.avatarUrls['32x32']
                || author.avatarUrls['24x24']
                || '';
        }

        // Extract the issue numeric id from the comment's self link
        // (e.g. ".../rest/api/2/issue/10215/comment/11600") - needed
        // for the Edit / Delete URLs.
        var issueId = '';
        var selfMatch = /\/issue\/(\d+)\/comment\//.exec(comment.self || '');
        if (selfMatch) issueId = selfMatch[1];

        var renderedBody = comment.renderedBody;
        if (!renderedBody) {
            // No rendered HTML available - fall back to a literal
            // <pre> so we never execute attacker-controlled markup.
            var pre = document.createElement('pre');
            pre.textContent = comment.body || '';
            renderedBody = pre.outerHTML;
        }

        var commentId = String(comment.id);

        var block = document.createElement('div');
        block.id = 'comment-' + commentId;
        block.className = 'issue-data-block activity-comment twixi-block expanded jim-comment-reply-just-added';

        var verbose = document.createElement('div');
        verbose.className = 'twixi-wrap verbose actionContainer';

        var head = document.createElement('div');
        head.className = 'action-head';
        var details = document.createElement('div');
        details.className = 'action-details';

        if (aAvatar) {
            var img = document.createElement('img');
            img.className = 'user-avatar';
            img.alt = '';
            img.src = aAvatar;
            img.width = 24;
            img.height = 24;
            details.appendChild(img);
            details.appendChild(document.createTextNode(' '));
        }

        var userLink = document.createElement('a');
        userLink.className = 'user-hover';
        if (aName) {
            userLink.setAttribute('rel', aName);
            userLink.setAttribute('data-username', aName);
            userLink.href = ctxPath + '/secure/ViewProfile.jspa?name=' + encodeURIComponent(aName);
        } else {
            userLink.href = '#';
        }
        userLink.textContent = aDisplay;
        details.appendChild(userLink);
        details.appendChild(document.createTextNode(' added a comment - '));

        var time = document.createElement('time');
        time.className = 'livestamp';
        var iso = comment.created || comment.updated || '';
        if (iso) {
            time.setAttribute('datetime', iso);
            // Show a humane snapshot in case Jira's livestamp script
            // doesn't pick the new element up.
            try { time.textContent = new Date(iso).toLocaleString(); }
            catch (e) { time.textContent = iso; }
        } else {
            time.textContent = 'just now';
        }
        details.appendChild(time);
        head.appendChild(details);
        verbose.appendChild(head);

        var body = document.createElement('div');
        body.className = 'action-body flooded';
        // renderedBody is HTML produced by Jira's wiki renderer; it is
        // server-side sanitized (the same content is shown on the next
        // page refresh anyway), so we can inject it as HTML.
        body.innerHTML = renderedBody;
        verbose.appendChild(body);

        var links = document.createElement('div');
        links.className = 'action-links action-comment-actions';
        if (issueId) {
            var editA = document.createElement('a');
            editA.id = 'edit_comment_' + commentId;
            editA.className = 'edit-comment issue-comment-action';
            editA.href = ctxPath + '/secure/EditComment!default.jspa?id='
                + encodeURIComponent(issueId) + '&commentId=' + encodeURIComponent(commentId);
            editA.title = 'Edit';
            editA.textContent = 'Edit';
            links.appendChild(editA);

            var sep1 = document.createElement('span');
            sep1.className = 'action-links__divider';
            links.appendChild(sep1);

            var delA = document.createElement('a');
            delA.id = 'delete_comment_' + commentId;
            delA.className = 'delete-comment issue-comment-action';
            delA.href = ctxPath + '/secure/DeleteComment!default.jspa?id='
                + encodeURIComponent(issueId) + '&commentId=' + encodeURIComponent(commentId);
            delA.title = 'Delete';
            delA.textContent = 'Delete';
            links.appendChild(delA);
        }
        verbose.appendChild(links);
        block.appendChild(verbose);

        return block;
    }

    /**
     * Place the new comment block where the composer just sat (i.e.
     * directly after the parent comment) and tear the composer down.
     * Inserting next to the parent gives the user immediate, spatial
     * feedback - their reply appears exactly where they typed it,
     * matching every other reply-style UX they've seen elsewhere.
     */
    function injectRenderedComment(commentJson, ctx) {
        var block = buildCommentDom(commentJson);
        if (!block) {
            softReloadFallback(ctx);
            return;
        }
        var anchor = document.querySelector('.' + COMPOSER_CLASS);
        var parentEl = ctx.commentEl;

        if (anchor && anchor.parentNode) {
            anchor.parentNode.insertBefore(block, anchor);
            anchor.parentNode.removeChild(anchor);
        } else if (parentEl && parentEl.parentNode) {
            parentEl.parentNode.insertBefore(block, parentEl.nextSibling);
        } else {
            // Last resort: dump the new comment into the activity feed.
            var feed = document.getElementById('issue_actions_container')
                || document.querySelector('.issue-data-block')
                || document.body;
            feed.appendChild(block);
        }

        // Re-enable the other Reply buttons that we disabled while the
        // composer was open.
        var btns = document.querySelectorAll('.' + BTN_CLASS);
        for (var i = 0; i < btns.length; i++) {
            btns[i].classList.remove(BTN_CLASS + '--disabled');
            btns[i].removeAttribute('aria-disabled');
        }

        // Bring our own decoration to the freshly-inserted block right
        // away (the MutationObserver also handles this, but doing it
        // synchronously avoids a one-tick flicker).
        try {
            injectReplyButton(block);
        } catch (e) { /* ignore */ }

        // Briefly highlight then fade.
        window.setTimeout(function () {
            try { block.scrollIntoView({ behavior: 'smooth', block: 'center' }); }
            catch (e) { /* ignore */ }
        }, 30);
        window.setTimeout(function () {
            block.classList.remove('jim-comment-reply-just-added');
        }, 2200);
    }

    function onReplyFailure(ctx, xhr) {
        ctx.sendBtn.disabled = false;
        ctx.cancelBtn.disabled = false;
        ctx.sendBtn.textContent = 'Send Reply';
        var msg = 'Reply failed (HTTP ' + xhr.status + ').';
        try {
            var parsed = JSON.parse(xhr.responseText);
            if (parsed && parsed.errorMessages && parsed.errorMessages.length) {
                msg = parsed.errorMessages.join(' ');
            } else if (parsed && parsed.errors) {
                var firstField = Object.keys(parsed.errors)[0];
                if (firstField) msg = firstField + ': ' + parsed.errors[firstField];
            }
        } catch (e) { /* ignore */ }
        setStatus(ctx.status, msg, 'error');
    }

    // -----------------------------------------------------------------
    // Reply button injection.
    // -----------------------------------------------------------------
    function injectReplyButton(commentEl) {
        if (!commentEl || commentEl.dataset[INSTALLED_FLAG] === '1') return;
        stats.commentsSeen++;
        var author = authorOfComment(commentEl);
        if (author && author.username) stats.authorLookupSuccess++;
        else stats.authorLookupFailed++;

        var toolbar = actionToolbarOf(commentEl);
        if (!toolbar) {
            // Without a toolbar there is nowhere to put the link. Do
            // not mark as installed so the next mutation can retry if
            // the toolbar appears later.
            log('no toolbar yet for', commentEl.id);
            return;
        }

        // Mark as installed only once we have a place to anchor.
        commentEl.dataset[INSTALLED_FLAG] = '1';

        // Build the button. We always inject it, even when the author
        // lookup failed - in that case the composer opens with no
        // auto-mention, but the user can still reply (with quote).
        var divider = document.createElement('span');
        divider.className = 'action-links__divider';

        var btn = document.createElement('a');
        btn.href = '#';
        btn.className = BTN_CLASS + ' issue-comment-action';
        btn.textContent = 'Reply';
        var titleAuthor = author ? author.displayName : 'this comment';
        btn.setAttribute('title', 'Reply to ' + titleAuthor);
        btn.addEventListener('click', function (event) {
            event.preventDefault();
            if (btn.classList.contains(BTN_CLASS + '--disabled')) return;
            stats.composersOpened++;
            var excerpt = trimTo(rawBodyTextOfComment(commentEl), EXCERPT_MAX);
            openComposer(commentEl, author || { username: '', displayName: '?' }, excerpt);
        });

        // Insert AT THE START of the toolbar so Reply leads.
        if (toolbar.firstChild) {
            toolbar.insertBefore(divider, toolbar.firstChild);
            toolbar.insertBefore(btn, toolbar.firstChild);
        } else {
            toolbar.appendChild(btn);
            toolbar.appendChild(divider);
        }
        stats.buttonsInjected++;
        log('reply button injected on', commentEl.id,
            'author=', author ? author.username : '(none)',
            'toolbar=', toolbar.className);

        injectReplyBadge(commentEl);
    }

    /**
     * Renders an "In reply to <user>" badge above a comment whose body
     * begins with our reply marker (a Jira mention link immediately
     * followed by a blockquote). Works for replies created by this
     * plugin or any user who manually quoted+mentioned someone.
     */
    function injectReplyBadge(commentEl) {
        if (!commentEl || commentEl.dataset[BADGE_FLAG] === '1') return;
        var body = commentEl.querySelector('.action-body');
        if (!body) return;
        // Find the first user-mention link in the body.
        var firstAnchor = body.querySelector('a.user-hover, a[data-username]');
        if (!firstAnchor) return;
        // The mention must be one of the FIRST children of .action-body
        // (within the first paragraph) to count as a reply marker - we
        // don't want every comment that mentions someone mid-text to be
        // labelled "In reply to".
        var firstBlock = body.firstElementChild;
        if (!firstBlock || !firstBlock.contains(firstAnchor)) return;
        // The next sibling must be a blockquote (rendered from {quote}).
        var nextEl = firstBlock.nextElementSibling;
        if (!nextEl || nextEl.tagName.toLowerCase() !== 'blockquote') return;

        var name = firstAnchor.getAttribute('rel') || firstAnchor.getAttribute('data-username') || '';
        var display = (firstAnchor.textContent || name).trim();
        if (!display) return;

        var badge = document.createElement('div');
        badge.className = BADGE_CLASS;
        badge.innerHTML =
            '<span class="' + BADGE_CLASS + '-arrow" aria-hidden="true">\u21B3</span>' +
            '<span class="' + BADGE_CLASS + '-label">In reply to</span>' +
            '<strong class="' + BADGE_CLASS + '-name"></strong>';
        badge.querySelector('strong').textContent = display;
        body.insertBefore(badge, body.firstChild);
        commentEl.dataset[BADGE_FLAG] = '1';
    }

    function scanForComments(root) {
        var nodes = (root || document).querySelectorAll('.activity-comment');
        for (var i = 0; i < nodes.length; i++) injectReplyButton(nodes[i]);
    }

    function startObserver() {
        scanForComments(document);
        stats.observedAt = new Date().toISOString();
        if (typeof window.MutationObserver === 'undefined') {
            log('MutationObserver unavailable - relying on periodic scan only');
            return;
        }
        var observer = new MutationObserver(function (mutations) {
            for (var i = 0; i < mutations.length; i++) {
                var m = mutations[i];
                if (!m.addedNodes || !m.addedNodes.length) continue;
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
        });
        observer.observe(document.body, { childList: true, subtree: true });
        log('observer started');
    }

    /**
     * Periodic backstop. The MutationObserver is the primary signal,
     * but in some Jira surfaces (Service Desk agent view + SLA panel,
     * Tempo timesheets, etc.) the comment activity sub-tree is swapped
     * in via mechanisms that occasionally don't surface as mutations
     * we observe. A cheap periodic rescan guarantees the Reply link
     * eventually appears. We taper the polling so it doesn't run
     * forever: every 750ms for the first 30s, then every 5s, then
     * stop after 5 minutes. Each scan that finds no new comments is
     * effectively a no-op.
     */
    function startBackstop() {
        var startedAt = Date.now();
        var fastPhaseUntil = startedAt + 30000;     // 30s
        var slowPhaseUntil = startedAt + 5 * 60000; // 5min
        function tick() {
            var now = Date.now();
            if (now > slowPhaseUntil) return;
            scanForComments(document);
            var delay = (now < fastPhaseUntil) ? 750 : 5000;
            window.setTimeout(tick, delay);
        }
        window.setTimeout(tick, 750);
    }

    /**
     * Some Jira surfaces (Service Desk, KickAss issue view's tab
     * switcher) emit AJS events when the activity feed re-renders.
     * Hook into them so we rescan immediately on those signals.
     */
    function bindAjsHooks() {
        if (!window.AJS || !AJS.$) return;
        try {
            AJS.$(document).on('ajaxStop', function () {
                scanForComments(document);
            });
        } catch (e) { /* ignore */ }
        // Jira KickAss event for new HTML injected anywhere on the page.
        if (window.JIRA && JIRA.bind && JIRA.Events && JIRA.Events.NEW_CONTENT_ADDED) {
            try {
                JIRA.bind(JIRA.Events.NEW_CONTENT_ADDED, function () {
                    scanForComments(document);
                });
            } catch (e) { /* ignore */ }
        }
    }

    /**
     * Diagnostic surface exposed on window so an operator can
     * confirm the JS is loaded and inspect the live counters from
     * the browser DevTools console:
     *
     *   window.JimCommentReply.diagnose()
     *
     * Prints version, comment counts, button counts, issue context,
     * and the configured WRM URLs. Useful when the user reports
     * "Reply button isn't appearing" on an unfamiliar Jira surface.
     */
    function buildDiagnose() {
        return {
            version: VERSION,
            stats: stats,
            debugEnabled: DEBUG,
            issueRef: issueRefForComment(null),
            url: window.location.href,
            ajsAvailable: !!(window.AJS && AJS.$),
            jiraAvailable: !!(window.JIRA && JIRA.Events),
            counts: {
                activityComment:   document.querySelectorAll('.activity-comment').length,
                actionLinksToolbar: document.querySelectorAll('.activity-comment .action-links').length,
                replyButtons:      document.querySelectorAll('.activity-comment .' + BTN_CLASS).length,
                replyBadges:       document.querySelectorAll('.activity-comment .' + BADGE_CLASS).length,
                composers:         document.querySelectorAll('.' + COMPOSER_CLASS).length
            },
            metaIssueKey:   metaContent('ajs-issue-key'),
            metaRemoteUser: metaContent('ajs-remote-user')
        };
    }
    window.JimCommentReply = {
        version: VERSION,
        diagnose: function () {
            var d = buildDiagnose();
            if (window.console) { try { console.table(d.counts); console.log(d); } catch (e) { /* ignore */ } }
            return d;
        },
        scan: function () { scanForComments(document); return buildDiagnose(); },
        enableDebug: function () {
            DEBUG = true;
            try { window.localStorage.setItem('jimReplyDebug', '1'); } catch (e) { /* ignore */ }
            info('debug logging enabled');
            return true;
        },
        disableDebug: function () {
            DEBUG = false;
            try { window.localStorage.removeItem('jimReplyDebug'); } catch (e) { /* ignore */ }
            return true;
        }
    };

    function init() {
        info('script loaded; url=' + window.location.pathname);
        startObserver();
        bindAjsHooks();
        startBackstop();
        // Best-effort: if the AJS jQuery is available, also re-scan
        // each time the user clicks an activity-tab header.
        if (window.AJS && AJS.$) {
            try {
                AJS.$(document).on(
                    'click',
                    '#activitymodule-tabs a, .menu-section a, .aui-tabs .menu-item a',
                    function () { window.setTimeout(function () { scanForComments(document); }, 250); }
                );
            } catch (e) { /* ignore */ }
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
