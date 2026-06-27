(function () {
    'use strict';

    var MESSAGE_POLL_MS = 5000;
    var CONVERSATION_POLL_MS = 10000;
    var SEARCH_DEBOUNCE_MS = 300;
    var MIN_SEARCH_LENGTH = 2;
    var MAX_MESSAGE_LENGTH = 4000;
    var MAX_UPLOAD_SIZE_BYTES = 200 * 1024 * 1024;
    var DELETE_WINDOW_MS = 10 * 60 * 1000;

    var EVENT_CARD_CONFIG = {
        MENTION: {
            label: 'MENTION',
            tone: 'mention',
            title: function (message) {
                if (!message.issueKey && message.body) {
                    // Chat mentions carry the full alarm text in the body.
                    return message.body;
                }
                var actor = message.actorDisplayName || 'Someone';
                var issue = message.issueKey || 'an issue';
                return actor + ' mentioned you in ' + issue;
            }
        },
        ASSIGNMENT: {
            label: 'ASSIGNED',
            tone: 'assignment',
            title: function (message) {
                var actor = message.actorDisplayName || 'Someone';
                var issue = message.issueKey || 'an issue';
                return actor + ' assigned ' + issue + ' to you';
            }
        },
        COMMENT: {
            label: 'COMMENT',
            tone: 'neutral',
            title: function (message) {
                var actor = message.actorDisplayName || 'Someone';
                var issue = message.issueKey || 'an issue';
                return actor + ' commented on ' + issue;
            }
        },
        STATUS_CHANGE: {
            label: 'STATUS',
            tone: 'success',
            title: function (message) {
                if (message.body) {
                    return message.body;
                }
                var issue = message.issueKey || 'an issue';
                return 'Status changed on ' + issue;
            }
        },
        ISSUE_LINK: {
            label: 'ISSUE',
            tone: 'info',
            title: function (message) {
                var actor = message.ownMessage ? 'You' : (message.senderDisplayName || 'Someone');
                return actor + ' shared ' + (message.issueKey || 'an issue');
            }
        }
    };

    var state = {
        licenseBlocked: false,
        conversations: [],
        selectedConversationId: null,
        selectedConversation: null,
        messages: [],
        currentUserKey: null,
        searchTimer: null,
        messagePollTimer: null,
        conversationPollTimer: null,
        sending: false,
        uploading: false,
        selectedFile: null,
        selectedFilePreviewUrl: null,
        loadingConversations: false,
        loadingMessages: false,
        initialLoadComplete: false,
        replyToMessage: null,
        editingMessageId: null,
        deleteConfirmMessageId: null,
        reactionPickerMessageId: null,
        pinnedMessage: null,
        activeTab: 'all',
        groupSelectedMembers: [],
        groupSearchTimer: null,
        membersPanelOpen: false,
        groupMembers: [],
        memberAddSearchTimer: null,
        confirmGroupDanger: false,
        issueSearchTimer: null,
        selectedIssue: null,
        voiceRecorder: null,
        voiceStream: null,
        voiceChunks: [],
        voiceSeconds: 0,
        voiceTimerId: null,
        voiceDiscard: false,
        renderedMessagesHtml: null,
        shouldAutoScroll: false,
        projectMode: false,
        projectKey: null,
        projectLeadName: null,
        initiallyUnreadIds: {},
        unreadSnapshotConversationId: null
    };

    /* Must match the backend whitelist in JimReactionServiceImpl */
    var REACTION_EMOJI = ['\uD83D\uDC4D', '\u2705', '\uD83D\uDE04', '\uD83C\uDF89', '\u2764\uFE0F', '\uD83D\uDC40'];

    var els = {};

    function onReady(callback) {
        if (document.readyState !== 'loading') {
            callback();
        } else {
            document.addEventListener('DOMContentLoaded', callback);
        }
    }

    function setHidden(element, hidden) {
        if (element) {
            element.hidden = hidden;
        }
    }

    function cacheElements() {
        els.app = document.getElementById('jim-messenger-app');
        els.searchInput = document.getElementById('jim-user-search');
        els.searchResults = document.getElementById('jim-user-search-results');
        els.assistantSlot = document.getElementById('jim-assistant-slot');
        els.conversationList = document.getElementById('jim-conversation-list');
        els.sidebarUnreadSummary = document.getElementById('jim-sidebar-unread-summary');
        els.chatEmpty = document.getElementById('jim-chat-empty');
        els.chatActive = document.getElementById('jim-chat-active');
        els.chatPanelShell = document.getElementById('jim-chat-panel-shell')
            || document.querySelector('#jim-messenger-app .jim-main')
            || document.querySelector('#jim-messenger-app .jim-chat-panel');
        els.legacyChatPanel = document.getElementById('jim-chat-panel');
        els.useLegacyLayout = !els.chatActive && !!els.legacyChatPanel;
        els.chatHeader = document.getElementById('jim-chat-header');
        els.messageList = document.getElementById('jim-message-list');
        els.messageInput = document.getElementById('jim-message-input');
        els.sendButton = document.getElementById('jim-send-button');
        els.attachButton = document.getElementById('jim-attach-button');
        els.fileInput = document.getElementById('jim-file-input');
        els.selectedAttachment = document.getElementById('jim-selected-attachment');
        els.selectedAttachmentPreview = document.getElementById('jim-selected-attachment-preview');
        els.selectedAttachmentName = document.getElementById('jim-selected-attachment-name');
        els.selectedAttachmentSize = document.getElementById('jim-selected-attachment-size');
        els.selectedAttachmentRemove = document.getElementById('jim-selected-attachment-remove');
        els.uploadProgress = document.getElementById('jim-upload-progress');
        els.composer = document.getElementById('jim-composer');
        els.composerError = document.getElementById('jim-composer-error');
        els.composerReadonly = document.getElementById('jim-composer-readonly');
        els.composerInner = document.getElementById('jim-composer-inner');
        els.composerControls = document.getElementById('jim-composer-controls');
        els.composerReply = document.getElementById('jim-composer-reply');
        els.composerReplyAuthor = document.getElementById('jim-composer-reply-author');
        els.composerReplyText = document.getElementById('jim-composer-reply-text');
        els.composerReplyCancel = document.getElementById('jim-composer-reply-cancel');
        els.emojiButton = document.getElementById('jim-emoji-button');
        els.emojiPalette = document.getElementById('jim-emoji-palette');
        els.mentionButton = document.getElementById('jim-mention-button');
        els.mentionPalette = document.getElementById('jim-mention-palette');
        els.issueButton = document.getElementById('jim-issue-button');
        els.issuePalette = document.getElementById('jim-issue-palette');
        els.issueSearchInput = document.getElementById('jim-issue-search-input');
        els.issueSearchResults = document.getElementById('jim-issue-search-results');
        els.selectedIssue = document.getElementById('jim-selected-issue');
        els.selectedIssueKey = document.getElementById('jim-selected-issue-key');
        els.selectedIssueSummary = document.getElementById('jim-selected-issue-summary');
        els.selectedIssueRemove = document.getElementById('jim-selected-issue-remove');
        els.voiceButton = document.getElementById('jim-voice-button');
        els.voiceRecording = document.getElementById('jim-voice-recording');
        els.voiceTimer = document.getElementById('jim-voice-timer');
        els.voiceCancel = document.getElementById('jim-voice-cancel');
        els.voiceSend = document.getElementById('jim-voice-send');
        els.newConversationButton = document.getElementById('jim-new-conversation');
        els.pinnedBanner = document.getElementById('jim-pinned-banner');
        els.errorBanner = document.getElementById('jim-error-banner');
        els.sidebarTabs = document.querySelectorAll('#jim-messenger-app .jim-sidebar-tab');
        els.newGroupButton = document.getElementById('jim-new-group');
        els.membersPanel = document.getElementById('jim-members-panel');
        els.messageInfoModal = document.getElementById('jim-message-info-modal');
        els.messageInfoBody = document.getElementById('jim-message-info-body');
        els.messageInfoClose = document.getElementById('jim-message-info-close');
        els.imageLightbox = document.getElementById('jim-image-lightbox');
        els.imageLightboxImg = document.getElementById('jim-image-lightbox-img');
        els.imageLightboxName = document.getElementById('jim-image-lightbox-name');
        els.imageLightboxDownload = document.getElementById('jim-image-lightbox-download');
        els.imageLightboxClose = document.getElementById('jim-image-lightbox-close');
        els.groupModal = document.getElementById('jim-group-modal');
        els.groupName = document.getElementById('jim-group-name');
        els.groupMemberSearch = document.getElementById('jim-group-member-search');
        els.groupMemberResults = document.getElementById('jim-group-member-results');
        els.groupSelectedMembers = document.getElementById('jim-group-selected-members');
        els.groupModalError = document.getElementById('jim-group-modal-error');
        els.groupCancel = document.getElementById('jim-group-cancel');
        els.groupCreate = document.getElementById('jim-group-create');
    }

    var EMOJI_SET = ['\uD83D\uDE00', '\uD83D\uDE04', '\uD83D\uDE02', '\uD83D\uDE0A', '\uD83D\uDE09', '\uD83D\uDE0D',
        '\uD83D\uDC4D', '\uD83D\uDC4C', '\uD83D\uDE4F', '\uD83D\uDCAA', '\uD83C\uDF89', '\u2705',
        '\u274C', '\u2764\uFE0F', '\uD83D\uDD25', '\uD83D\uDC40'];

    function renderEmojiPalette() {
        if (!els.emojiPalette || els.emojiPalette.childNodes.length) {
            return;
        }
        var html = [];
        for (var i = 0; i < EMOJI_SET.length; i++) {
            html.push('<button type="button" class="jim-emoji-option" role="menuitem" data-emoji="' +
                escapeHtml(EMOJI_SET[i]) + '">' + EMOJI_SET[i] + '</button>');
        }
        els.emojiPalette.innerHTML = html.join('');
    }

    function toggleEmojiPalette(forceHide) {
        if (!els.emojiPalette) {
            return;
        }
        if (forceHide || !els.emojiPalette.hidden) {
            setHidden(els.emojiPalette, true);
            return;
        }
        renderEmojiPalette();
        setHidden(els.emojiPalette, false);
    }

    function openGroupModal() {
        if (!els.groupModal) {
            return;
        }
        state.groupSelectedMembers = [];
        els.groupName.value = '';
        els.groupMemberSearch.value = '';
        els.groupMemberResults.innerHTML = '';
        setHidden(els.groupMemberResults, true);
        setHidden(els.groupModalError, true);
        renderGroupSelectedMembers();
        updateGroupCreateButton();
        setHidden(els.groupModal, false);
        els.groupName.focus();
    }

    function closeGroupModal() {
        if (!els.groupModal) {
            return;
        }
        setHidden(els.groupModal, true);
    }

    function updateGroupCreateButton() {
        if (!els.groupCreate) {
            return;
        }
        els.groupCreate.disabled = !els.groupName.value.trim();
    }

    function showGroupModalError(message) {
        if (!els.groupModalError) {
            return;
        }
        if (!message) {
            setHidden(els.groupModalError, true);
            return;
        }
        els.groupModalError.textContent = message;
        setHidden(els.groupModalError, false);
    }

    function renderGroupSelectedMembers() {
        if (!els.groupSelectedMembers) {
            return;
        }
        var chips = [];
        for (var i = 0; i < state.groupSelectedMembers.length; i++) {
            var member = state.groupSelectedMembers[i];
            chips.push('<span class="jim-group-chip">' +
                escapeHtml(member.displayName) +
                '<button type="button" class="jim-group-chip-remove" data-user-key="' +
                escapeHtml(member.userKey) + '" aria-label="Remove ' + escapeHtml(member.displayName) + '">&times;</button>' +
                '</span>');
        }
        els.groupSelectedMembers.innerHTML = chips.join('');

        var removeButtons = els.groupSelectedMembers.querySelectorAll('.jim-group-chip-remove');
        for (var j = 0; j < removeButtons.length; j++) {
            removeButtons[j].addEventListener('click', function (event) {
                var userKey = event.currentTarget.getAttribute('data-user-key');
                state.groupSelectedMembers = state.groupSelectedMembers.filter(function (m) {
                    return m.userKey !== userKey;
                });
                renderGroupSelectedMembers();
            });
        }
    }

    function renderGroupMemberResults(users) {
        var selectedKeys = {};
        for (var i = 0; i < state.groupSelectedMembers.length; i++) {
            selectedKeys[state.groupSelectedMembers[i].userKey] = true;
        }

        var items = [];
        for (var j = 0; j < users.length; j++) {
            var user = users[j];
            if (selectedKeys[user.userKey] || user.userKey === state.currentUserKey) {
                continue;
            }
            items.push('<button type="button" class="jim-search-result jim-group-member-result" data-user-key="' +
                escapeHtml(user.userKey) + '" data-display-name="' + escapeHtml(user.displayName) + '">' +
                renderAvatar(user.avatarUrl, user.displayName, 'jim-avatar-sm') +
                '<span class="jim-search-result-name">' + escapeHtml(user.displayName) + '</span>' +
                '</button>');
        }

        if (!items.length) {
            els.groupMemberResults.innerHTML = '<div class="jim-search-empty">No matching users</div>';
            setHidden(els.groupMemberResults, false);
            return;
        }

        els.groupMemberResults.innerHTML = items.join('');
        setHidden(els.groupMemberResults, false);

        var buttons = els.groupMemberResults.querySelectorAll('.jim-group-member-result');
        for (var k = 0; k < buttons.length; k++) {
            buttons[k].addEventListener('click', function (event) {
                state.groupSelectedMembers.push({
                    userKey: event.currentTarget.getAttribute('data-user-key'),
                    displayName: event.currentTarget.getAttribute('data-display-name')
                });
                els.groupMemberSearch.value = '';
                els.groupMemberResults.innerHTML = '';
                setHidden(els.groupMemberResults, true);
                renderGroupSelectedMembers();
                els.groupMemberSearch.focus();
            });
        }
    }

    function submitCreateGroup() {
        var name = els.groupName.value.trim();
        if (!name) {
            return;
        }
        var memberKeys = state.groupSelectedMembers.map(function (member) {
            return member.userKey;
        });

        els.groupCreate.disabled = true;
        JimApi.createGroup(name, memberKeys).then(function (conversation) {
            closeGroupModal();
            return loadConversations(false).then(function () {
                if (conversation && conversation.id) {
                    setActiveTab('groups');
                    selectConversation(conversation.id);
                }
            });
        }).catch(function (error) {
            logError('createGroup', error);
            showGroupModalError(error && error.message ? error.message : 'Unable to create the group.');
            updateGroupCreateButton();
        });
    }

    function insertAtCursor(textarea, text) {
        var start = typeof textarea.selectionStart === 'number' ? textarea.selectionStart : textarea.value.length;
        var end = typeof textarea.selectionEnd === 'number' ? textarea.selectionEnd : start;
        textarea.value = textarea.value.slice(0, start) + text + textarea.value.slice(end);
        var position = start + text.length;
        try {
            textarea.setSelectionRange(position, position);
        } catch (rangeError) {
            // non-fatal on older browsers
        }
        textarea.focus();
    }

    /* ----- Voice messages ----- */

    var VOICE_MAX_SECONDS = 120;

    function voiceRecordingSupported() {
        return !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia && window.MediaRecorder);
    }

    function pickVoiceMimeType() {
        var candidates = ['audio/webm', 'audio/mp4', 'audio/ogg'];
        if (window.MediaRecorder && typeof window.MediaRecorder.isTypeSupported === 'function') {
            for (var i = 0; i < candidates.length; i++) {
                if (window.MediaRecorder.isTypeSupported(candidates[i])) {
                    return candidates[i];
                }
            }
        }
        return '';
    }

    function formatVoiceTime(totalSeconds) {
        var minutes = Math.floor(totalSeconds / 60);
        var seconds = totalSeconds % 60;
        return minutes + ':' + (seconds < 10 ? '0' : '') + seconds;
    }

    function updateVoiceTimerLabel() {
        if (els.voiceTimer) {
            els.voiceTimer.textContent = formatVoiceTime(state.voiceSeconds);
        }
    }

    function startVoiceRecording() {
        if (state.voiceRecorder || state.uploading || !state.selectedConversationId) {
            return;
        }
        if (!voiceRecordingSupported()) {
            showError('Voice recording is unavailable. The browser requires a secure (HTTPS) connection and microphone support.');
            return;
        }

        navigator.mediaDevices.getUserMedia({ audio: true }).then(function (stream) {
            var mimeType = pickVoiceMimeType();
            var recorder;
            try {
                recorder = mimeType ? new MediaRecorder(stream, { mimeType: mimeType }) : new MediaRecorder(stream);
            } catch (recorderError) {
                recorder = new MediaRecorder(stream);
            }

            state.voiceRecorder = recorder;
            state.voiceStream = stream;
            state.voiceChunks = [];
            state.voiceSeconds = 0;
            state.voiceDiscard = false;

            recorder.ondataavailable = function (event) {
                if (event.data && event.data.size > 0) {
                    state.voiceChunks.push(event.data);
                }
            };
            recorder.onstop = handleVoiceRecorderStop;
            recorder.start();

            updateVoiceTimerLabel();
            state.voiceTimerId = window.setInterval(function () {
                state.voiceSeconds += 1;
                updateVoiceTimerLabel();
                if (state.voiceSeconds >= VOICE_MAX_SECONDS) {
                    finishVoiceRecording();
                }
            }, 1000);

            setHidden(els.voiceRecording, false);
            if (els.voiceButton) {
                els.voiceButton.disabled = true;
            }
        }).catch(function (error) {
            logError('startVoiceRecording', error);
            showError('Microphone access was denied or is unavailable.');
        });
    }

    function stopVoiceStreamTracks() {
        if (state.voiceStream) {
            var tracks = state.voiceStream.getTracks ? state.voiceStream.getTracks() : [];
            for (var i = 0; i < tracks.length; i++) {
                tracks[i].stop();
            }
            state.voiceStream = null;
        }
    }

    function teardownVoiceUi() {
        if (state.voiceTimerId) {
            window.clearInterval(state.voiceTimerId);
            state.voiceTimerId = null;
        }
        setHidden(els.voiceRecording, true);
        if (els.voiceButton) {
            els.voiceButton.disabled = false;
        }
    }

    function cancelVoiceRecording() {
        if (!state.voiceRecorder) {
            teardownVoiceUi();
            return;
        }
        state.voiceDiscard = true;
        try {
            state.voiceRecorder.stop();
        } catch (stopError) {
            logError('cancelVoiceRecording', stopError);
            state.voiceRecorder = null;
            stopVoiceStreamTracks();
            teardownVoiceUi();
        }
    }

    function finishVoiceRecording() {
        if (!state.voiceRecorder) {
            return;
        }
        try {
            state.voiceRecorder.stop();
        } catch (stopError) {
            logError('finishVoiceRecording', stopError);
            state.voiceRecorder = null;
            stopVoiceStreamTracks();
            teardownVoiceUi();
        }
    }

    function handleVoiceRecorderStop() {
        var recorder = state.voiceRecorder;
        state.voiceRecorder = null;
        stopVoiceStreamTracks();
        teardownVoiceUi();

        var chunks = state.voiceChunks;
        state.voiceChunks = [];
        if (state.voiceDiscard || !chunks.length) {
            state.voiceDiscard = false;
            return;
        }

        var mimeType = recorder && recorder.mimeType ? recorder.mimeType : 'audio/webm';
        var baseType = mimeType.split(';')[0].trim().toLowerCase() || 'audio/webm';
        var extension = 'webm';
        if (baseType.indexOf('mp4') >= 0) {
            extension = 'm4a';
        } else if (baseType.indexOf('ogg') >= 0) {
            extension = 'ogg';
        } else if (baseType.indexOf('mpeg') >= 0) {
            extension = 'mp3';
        }

        var blob = new Blob(chunks, { type: baseType });
        var fileName = 'voice-message-' + Date.now() + '.' + extension;
        var file;
        try {
            file = new File([blob], fileName, { type: baseType });
        } catch (fileError) {
            file = blob;
            file.name = fileName;
        }
        uploadVoiceMessage(file);
    }

    function uploadVoiceMessage(file) {
        if (!state.selectedConversationId) {
            return;
        }
        state.uploading = true;
        updateComposerState();
        JimApi.uploadAttachment(state.selectedConversationId, file, null).then(function () {
            state.shouldAutoScroll = true;
            return loadMessages(state.selectedConversationId, false, true);
        }).then(function () {
            return loadConversations(false);
        }).catch(function (error) {
            logError('uploadVoiceMessage', error);
            var detail = error && error.message ? error.message : 'Unable to send the voice message.';
            showComposerError(detail);
            showError(detail);
        }).then(function () {
            state.uploading = false;
            updateComposerState();
        });
    }

    /* ----- Mentions ----- */

    function mentionableMembers() {
        var members = state.groupMembers || [];
        var result = [];
        for (var i = 0; i < members.length; i++) {
            if (members[i] && members[i].displayName) {
                result.push(members[i]);
            }
        }
        return result;
    }

    function renderMessageTextHtml(body) {
        var html = escapeHtml(body);
        // Mentions are processed BEFORE linkify so the literal-string split
        // operates on a tag-free string (otherwise an @name appearing inside
        // an href attribute could be split-replaced and corrupt the URL).
        if (isGroupConversation(state.selectedConversation)) {
            var members = mentionableMembers().slice().sort(function (a, b) {
                return b.displayName.length - a.displayName.length;
            });
            for (var i = 0; i < members.length; i++) {
                var member = members[i];
                var token = '@' + escapeHtml(member.displayName);
                if (html.indexOf(token) < 0) {
                    continue;
                }
                var mentionClass = member.userKey === state.currentUserKey
                    ? 'jim-mention jim-mention-self'
                    : 'jim-mention';
                html = html.split(token).join('<span class="' + mentionClass + '">' + token + '</span>');
            }
        }
        return linkifyHtml(html);
    }

    /**
     * Wraps bare http/https URLs in the already-escaped message HTML with
     * <a> tags. Trailing punctuation (., , ; : ! ? ) ] }) is left outside the
     * link so "see https://example.com." renders without the trailing dot
     * being part of the clickable target. The regex stops at < to avoid
     * crossing into any tags added earlier (e.g. mention spans).
     */
    function linkifyHtml(html) {
        if (!html) {
            return html;
        }
        return html.replace(/https?:\/\/[^\s<]+/g, function (match) {
            var trailing = '';
            var trailingChars = '.,;:!?)]}\'"';
            while (match.length > 0 && trailingChars.indexOf(match.charAt(match.length - 1)) >= 0) {
                trailing = match.charAt(match.length - 1) + trailing;
                match = match.substring(0, match.length - 1);
            }
            if (!match) {
                return trailing;
            }
            return '<a href="' + match + '" target="_blank" rel="noopener noreferrer nofollow" class="jim-link">'
                + match + '</a>' + trailing;
        });
    }

    function toggleMentionPalette(forceHide) {
        if (!els.mentionPalette) {
            return;
        }
        var shouldHide = forceHide || !els.mentionPalette.hidden;
        if (shouldHide) {
            setHidden(els.mentionPalette, true);
            return;
        }
        setHidden(els.emojiPalette, true);
        toggleIssuePalette(true);
        renderMentionPalette();
        setHidden(els.mentionPalette, false);
    }

    function renderMentionPalette() {
        var members = mentionableMembers();
        var items = [];
        for (var i = 0; i < members.length; i++) {
            var member = members[i];
            if (member.userKey === state.currentUserKey) {
                continue;
            }
            items.push('' +
                '<button type="button" class="jim-mention-option" data-mention-name="' + escapeHtml(member.displayName) + '">' +
                renderAvatar(member.avatarUrl, member.displayName, 'jim-avatar-sm') +
                '<span class="jim-mention-option-name">' + escapeHtml(member.displayName) + '</span>' +
                '</button>');
        }
        els.mentionPalette.innerHTML = items.length
            ? items.join('')
            : '<div class="jim-search-empty">No other members to mention</div>';
    }

    /* ----- Issue linking ----- */

    function toggleIssuePalette(forceHide) {
        if (!els.issuePalette) {
            return;
        }
        var shouldHide = forceHide || !els.issuePalette.hidden;
        if (shouldHide) {
            setHidden(els.issuePalette, true);
            return;
        }
        setHidden(els.emojiPalette, true);
        toggleMentionPalette(true);
        if (els.issueSearchInput) {
            els.issueSearchInput.value = '';
        }
        if (els.issueSearchResults) {
            els.issueSearchResults.innerHTML =
                '<div class="jim-search-empty">Type an issue key or text to search.</div>';
        }
        setHidden(els.issuePalette, false);
        if (els.issueSearchInput) {
            els.issueSearchInput.focus();
        }
    }

    function flattenIssuePickerResults(data) {
        var seen = {};
        var issues = [];
        var sections = data && data.sections ? data.sections : [];
        for (var i = 0; i < sections.length; i++) {
            var sectionIssues = sections[i] && sections[i].issues ? sections[i].issues : [];
            for (var j = 0; j < sectionIssues.length; j++) {
                var issue = sectionIssues[j];
                if (issue && issue.key && !seen[issue.key]) {
                    seen[issue.key] = true;
                    issues.push(issue);
                }
            }
        }
        return issues;
    }

    function resolveIssueIconUrl(img) {
        if (!img) {
            return null;
        }
        if (img.indexOf('http') === 0) {
            return img;
        }
        return contextPath() + (img.charAt(0) === '/' ? img : '/' + img);
    }

    function runIssueSearch(query) {
        if (!els.issueSearchResults) {
            return;
        }
        var trimmed = (query || '').trim();
        if (trimmed.length < 2) {
            els.issueSearchResults.innerHTML =
                '<div class="jim-search-empty">Type at least 2 characters to search.</div>';
            return;
        }

        els.issueSearchResults.innerHTML = '<div class="jim-search-empty">Searching...</div>';
        JimApi.searchIssues(trimmed).then(function (data) {
            if (!els.issuePalette || els.issuePalette.hidden) {
                return;
            }
            if (els.issueSearchInput && els.issueSearchInput.value.trim() !== trimmed) {
                return;
            }
            var issues = flattenIssuePickerResults(data);
            if (!issues.length) {
                els.issueSearchResults.innerHTML = '<div class="jim-search-empty">No matching issues.</div>';
                return;
            }
            var items = [];
            for (var i = 0; i < issues.length && i < 15; i++) {
                var issue = issues[i];
                var iconUrl = resolveIssueIconUrl(issue.img);
                var summary = issue.summaryText || issue.summary || '';
                items.push('' +
                    '<button type="button" class="jim-issue-option" data-issue-key="' + escapeHtml(issue.key) + '" data-issue-summary="' + escapeHtml(summary) + '">' +
                    (iconUrl ? '<img class="jim-issue-option-icon" src="' + escapeHtml(iconUrl) + '" alt="" />' : '') +
                    '<span class="jim-issue-option-key">' + escapeHtml(issue.key) + '</span>' +
                    '<span class="jim-issue-option-summary">' + escapeHtml(summary) + '</span>' +
                    '</button>');
            }
            els.issueSearchResults.innerHTML = items.join('');
        }).catch(function (error) {
            logError('searchIssues', error);
            if (els.issueSearchResults) {
                els.issueSearchResults.innerHTML =
                    '<div class="jim-search-empty">Unable to search issues. Try again.</div>';
            }
        });
    }

    function renderSelectedIssueChip() {
        if (!els.selectedIssue) {
            return;
        }
        if (!state.selectedIssue) {
            setHidden(els.selectedIssue, true);
            return;
        }
        if (els.selectedIssueKey) {
            els.selectedIssueKey.textContent = state.selectedIssue.key;
        }
        if (els.selectedIssueSummary) {
            els.selectedIssueSummary.textContent = state.selectedIssue.summary || '';
        }
        setHidden(els.selectedIssue, false);
    }

    function setSelectedIssue(issue) {
        state.selectedIssue = issue;
        if (issue) {
            clearSelectedFile();
        }
        renderSelectedIssueChip();
        updateComposerState();
    }

    function clearSelectedIssue() {
        if (!state.selectedIssue) {
            return;
        }
        state.selectedIssue = null;
        renderSelectedIssueChip();
        updateComposerState();
    }

    function escapeHtml(value) {
        return String(value || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function contextPath() {
        return window.AJS && window.AJS.contextPath ? window.AJS.contextPath() : '';
    }

    function formatFileSize(bytes) {
        var size = Number(bytes || 0);
        if (!size) {
            return '0 B';
        }
        if (size < 1024) {
            return size + ' B';
        }
        if (size < 1024 * 1024) {
            return (size / 1024).toFixed(1) + ' KB';
        }
        return (size / (1024 * 1024)).toFixed(1) + ' MB';
    }

    function resolveAttachmentUrl(url) {
        if (!url) {
            return null;
        }
        if (url.indexOf('http://') === 0 || url.indexOf('https://') === 0) {
            return url;
        }
        return contextPath() + url;
    }

    function clearSelectedFile() {
        if (state.selectedFilePreviewUrl) {
            URL.revokeObjectURL(state.selectedFilePreviewUrl);
        }
        state.selectedFile = null;
        state.selectedFilePreviewUrl = null;
        if (els.fileInput) {
            els.fileInput.value = '';
        }
        renderSelectedAttachment();
        updateComposerState();
    }

    function renderSelectedAttachment() {
        if (!els.selectedAttachment) {
            return;
        }

        if (!state.selectedFile) {
            setHidden(els.selectedAttachment, true);
            if (els.selectedAttachmentPreview) {
                els.selectedAttachmentPreview.innerHTML = '';
                setHidden(els.selectedAttachmentPreview, true);
            }
            return;
        }

        setHidden(els.selectedAttachment, false);
        if (els.selectedAttachmentName) {
            els.selectedAttachmentName.textContent = state.selectedFile.name || 'attachment';
        }
        if (els.selectedAttachmentSize) {
            els.selectedAttachmentSize.textContent = formatFileSize(state.selectedFile.size);
        }

        if (els.selectedAttachmentPreview) {
            if (state.selectedFile.type && state.selectedFile.type.indexOf('image/') === 0 && state.selectedFilePreviewUrl) {
                els.selectedAttachmentPreview.innerHTML =
                    '<img src="' + escapeHtml(state.selectedFilePreviewUrl) + '" alt="" class="jim-selected-attachment-thumb" />';
                setHidden(els.selectedAttachmentPreview, false);
            } else {
                els.selectedAttachmentPreview.innerHTML = '';
                setHidden(els.selectedAttachmentPreview, true);
            }
        }
    }

    function onFileInputChange() {
        if (!els.fileInput || !els.fileInput.files || !els.fileInput.files.length) {
            clearSelectedFile();
            return;
        }
        acceptIncomingFile(els.fileInput.files[0]);
    }

    /**
     * Common entry point used by the attach button, drag-and-drop, and paste:
     * validates the file and stages it as the pending attachment.
     */
    function acceptIncomingFile(file) {
        if (!file) {
            clearSelectedFile();
            return false;
        }
        if (file.size > MAX_UPLOAD_SIZE_BYTES) {
            showComposerError('File exceeds the maximum allowed size of 200 MB.');
            clearSelectedFile();
            return false;
        }
        if (state.selectedFilePreviewUrl) {
            URL.revokeObjectURL(state.selectedFilePreviewUrl);
            state.selectedFilePreviewUrl = null;
        }
        clearSelectedIssue();
        state.selectedFile = file;
        if (file.type && file.type.indexOf('image/') === 0) {
            state.selectedFilePreviewUrl = URL.createObjectURL(file);
        }
        renderSelectedAttachment();
        updateComposerState();
        showComposerError(null);
        focusMessageInput();
        return true;
    }

    function focusMessageInput() {
        if (!els.messageInput) {
            return;
        }
        // Skip when the composer is hidden/read-only so we don't steal focus
        // away from the recipient picker on the empty state.
        if (els.composer && els.composer.classList.contains('jim-composer-is-readonly')) {
            return;
        }
        if (els.messageInput.disabled || els.messageInput.readOnly) {
            return;
        }
        try {
            els.messageInput.focus({ preventScroll: true });
        } catch (e) {
            els.messageInput.focus();
        }
    }

    function resolveIssueUrl(url) {
        if (!url) {
            return null;
        }
        if (url.indexOf('http://') === 0 || url.indexOf('https://') === 0) {
            return url;
        }
        if (url.charAt(0) === '/') {
            return contextPath() + url;
        }
        return url;
    }

    var MESSAGE_ABSOLUTE_TIME_MS = 24 * 60 * 60 * 1000;

    function formatTime(timestamp) {
        if (!timestamp) {
            return '';
        }

        var date = new Date(timestamp);
        if (isNaN(date.getTime())) {
            return '';
        }

        var now = Date.now();
        var diff = now - date.getTime();
        if (diff < 0) {
            diff = 0;
        }

        if (diff < 60000) {
            return 'Just now';
        }
        if (diff < 3600000) {
            return Math.floor(diff / 60000) + 'm ago';
        }
        if (diff < 86400000) {
            return Math.floor(diff / 3600000) + 'h ago';
        }
        if (diff < 604800000) {
            return Math.floor(diff / 86400000) + 'd ago';
        }

        return date.toLocaleString();
    }

    function formatAbsoluteDateTime(timestamp) {
        var date = new Date(timestamp);
        if (isNaN(date.getTime())) {
            return '';
        }

        return new Intl.DateTimeFormat(undefined, {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
            hour: 'numeric',
            minute: '2-digit'
        }).format(date);
    }

    /** Exact wall-clock time: "14:32" today, "yesterday 14:32", or "11 Jun 14:32". */
    function formatExactTime(timestamp) {
        if (!timestamp) {
            return '';
        }
        var date = new Date(timestamp);
        if (isNaN(date.getTime())) {
            return '';
        }
        var clock = ('0' + date.getHours()).slice(-2) + ':' + ('0' + date.getMinutes()).slice(-2);
        var now = new Date();
        if (date.toDateString() === now.toDateString()) {
            return clock;
        }
        var yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1);
        if (date.toDateString() === yesterday.toDateString()) {
            return 'yesterday ' + clock;
        }
        var day = date.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
        return day + ' ' + clock;
    }

    function formatMessageTime(timestamp) {
        if (!timestamp) {
            return '';
        }

        var date = new Date(timestamp);
        if (isNaN(date.getTime())) {
            return '';
        }

        var diff = Date.now() - date.getTime();
        if (diff < 0) {
            diff = 0;
        }

        if (diff <= MESSAGE_ABSOLUTE_TIME_MS) {
            if (diff < 60000) {
                return 'Just now';
            }
            if (diff < 3600000) {
                return Math.floor(diff / 60000) + 'm ago';
            }
            return Math.floor(diff / 3600000) + 'h ago';
        }

        return formatAbsoluteDateTime(timestamp);
    }

    function isSameDay(timestampA, timestampB) {
        if (!timestampA || !timestampB) {
            return false;
        }
        var dateA = new Date(timestampA);
        var dateB = new Date(timestampB);
        return dateA.getFullYear() === dateB.getFullYear()
            && dateA.getMonth() === dateB.getMonth()
            && dateA.getDate() === dateB.getDate();
    }

    function formatDateDivider(timestamp) {
        if (!timestamp) {
            return '';
        }

        var date = new Date(timestamp);
        var today = new Date();
        var todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();
        var messageStart = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
        var diffDays = Math.round((todayStart - messageStart) / 86400000);

        var fullDate = date.toLocaleDateString(undefined, {
            month: 'long',
            day: 'numeric',
            year: 'numeric'
        });

        if (diffDays === 0) {
            return 'Today \u2014 ' + fullDate;
        }
        if (diffDays === 1) {
            return 'Yesterday \u2014 ' + fullDate;
        }

        return date.toLocaleDateString(undefined, {
            weekday: 'short',
            month: 'short',
            day: 'numeric',
            year: 'numeric'
        });
    }

    function renderDateDivider(label) {
        if (!label) {
            return '';
        }
        return '<div class="jim-date-divider" role="separator" aria-label="' + escapeHtml(label) + '">' +
            '<span class="jim-date-divider-text">' + escapeHtml(label) + '</span>' +
            '</div>';
    }

    function logError(context, error) {
        if (window.console && console.error) {
            console.error('[JIM] ' + context, error);
        }
    }

    function showError(message) {
        if (!els.errorBanner) {
            return;
        }
        els.errorBanner.textContent = message || 'Something went wrong. Please try again.';
        setHidden(els.errorBanner, false);
        window.setTimeout(function () {
            setHidden(els.errorBanner, true);
        }, 6000);
    }

    function showComposerError(message) {
        if (!els.composerError) {
            showError(message);
            return;
        }
        if (!message) {
            setHidden(els.composerError, true);
            els.composerError.textContent = '';
            return;
        }
        els.composerError.textContent = message;
        setHidden(els.composerError, false);
    }

    function normalizeMessageBody(value) {
        return String(value || '')
            .replace(/\r/g, '\n')
            .replace(/[ \t\v\f]+/g, ' ')
            .replace(/\n{3,}/g, '\n\n')
            .trim();
    }

    function renderLoadingState(container, message) {
        if (!container) {
            return;
        }
        container.innerHTML = '<div class="jim-loading jim-loading-state" role="status"><span class="jim-loading-spinner" aria-hidden="true"></span>' +
            escapeHtml(message || 'Loading...') + '</div>';
    }

    function renderErrorState(container, message, retryAction) {
        if (!container) {
            return;
        }
        container.innerHTML = '' +
            '<div class="jim-empty-state jim-error-state" role="alert">' +
            escapeHtml(message || 'Something went wrong.') +
            ' <button type="button" class="jim-retry-button">Retry</button>' +
            '</div>';

        var retryButton = container.querySelector('.jim-retry-button');
        if (retryButton && retryAction) {
            retryButton.addEventListener('click', retryAction);
        }
    }

    function scrollMessagesToBottom() {
        if (!els.messageList) {
            return;
        }
        els.messageList.scrollTop = els.messageList.scrollHeight;
    }

    function clearConversationUnread(conversationId) {
        for (var i = 0; i < state.conversations.length; i++) {
            if (state.conversations[i].id === conversationId) {
                state.conversations[i].unreadCount = 0;
                break;
            }
        }
        renderConversationList();
        bindConversationClicks(els.assistantSlot);
        bindConversationClicks(els.conversationList);
    }

    function isSystemConversation(conversation) {
        return !!(conversation && (conversation.isSystem || conversation.type === 'SYSTEM'));
    }

    function isGroupConversation(conversation) {
        return !!(conversation && (conversation.isGroup || conversation.type === 'GROUP'));
    }

    function normalizeEventType(message) {
        if (!message || message.eventType == null || message.eventType === '') {
            return null;
        }
        return String(message.eventType).toUpperCase();
    }

    function isSystemMessage(message) {
        if (!message) {
            return false;
        }
        var eventType = normalizeEventType(message);
        var bodyFormat = message.bodyFormat ? String(message.bodyFormat).toUpperCase() : '';
        var senderType = message.senderType ? String(message.senderType).toUpperCase() : '';
        if (eventType === 'ISSUE_LINK' && senderType === 'USER') {
            return false;
        }
        return senderType === 'SYSTEM'
            || bodyFormat === 'SYSTEM_CARD'
            || (eventType && eventType !== 'NORMAL');
    }

    function sortConversations(conversations) {
        return conversations.slice().sort(function (a, b) {
            return (b.lastMessageAt || 0) - (a.lastMessageAt || 0);
        });
    }

    function getAssistantConversation(conversations) {
        for (var i = 0; i < conversations.length; i++) {
            if (isSystemConversation(conversations[i])) {
                return conversations[i];
            }
        }
        return null;
    }

    function getDirectConversations(conversations) {
        return sortConversations(conversations.filter(function (conversation) {
            return !isSystemConversation(conversation);
        }));
    }

    var AVATAR_COLORS = ['#0c66e4', '#00875a', '#de350b', '#6554c0', '#ff8b00', '#00a3bf', '#403294'];

    function avatarColor(displayName) {
        var name = String(displayName || '?');
        var hash = 0;
        for (var i = 0; i < name.length; i++) {
            hash = (hash * 31 + name.charCodeAt(i)) % 99991;
        }
        return AVATAR_COLORS[hash % AVATAR_COLORS.length];
    }

    function avatarInitials(displayName) {
        var parts = String(displayName || '?').trim().split(/\s+/);
        var first = parts[0] ? parts[0].charAt(0) : '?';
        var second = parts.length > 1 ? parts[parts.length - 1].charAt(0) : '';
        return (first + second).toUpperCase();
    }

    function renderAvatar(avatarUrl, displayName, extraClass) {
        var className = 'jim-avatar jim-conversation-avatar ' + (extraClass || '');
        if (avatarUrl) {
            return '<img class="' + className + '" src="' + escapeHtml(avatarUrl) + '" alt="" />';
        }
        return '<span class="' + className + ' jim-avatar-fallback" style="background:' + avatarColor(displayName) + '" aria-hidden="true">' +
            escapeHtml(avatarInitials(displayName)) + '</span>';
    }

    function renderAvatarWithPresence(avatarUrl, displayName, extraClass, isActive) {
        var badge = isActive
            ? '<span class="jim-presence-badge" title="Active now" aria-label="Active now"></span>'
            : '';
        return '<span class="jim-avatar-wrap">' +
            renderAvatar(avatarUrl, displayName, extraClass) +
            badge +
            '</span>';
    }

    function getTotalUnreadCount() {
        var total = 0;
        for (var i = 0; i < state.conversations.length; i++) {
            total += Number(state.conversations[i].unreadCount) || 0;
        }
        return total;
    }

    function updateNavBadge(total) {
        var link = document.getElementById('jim-messenger');
        if (!link) {
            return;
        }
        var badge = document.getElementById('jim-nav-unread-badge');
        if (!badge) {
            badge = document.createElement('span');
            badge.id = 'jim-nav-unread-badge';
            badge.className = 'jim-nav-unread-badge';
            link.appendChild(badge);
        }
        if (total > 0) {
            badge.textContent = total > 99 ? '99+' : String(total);
            badge.removeAttribute('hidden');
        } else {
            badge.setAttribute('hidden', 'hidden');
        }
    }

    function updateSidebarUnreadSummary() {
        var total = getTotalUnreadCount();
        updateNavBadge(total);
        if (!els.sidebarUnreadSummary) {
            return;
        }
        if (total === 1) {
            els.sidebarUnreadSummary.textContent = '1 unread message';
        } else {
            els.sidebarUnreadSummary.textContent = total + ' unread messages';
        }
    }

    function renderUnreadBadge(count) {
        if (!count || count <= 0) {
            return '';
        }
        return '<span class="jim-unread-badge" aria-label="' + escapeHtml(String(count) + ' unread messages') + '">' +
            escapeHtml(String(count)) + '</span>';
    }

    function renderConversationMetaRight(timeText, unreadCount) {
        var parts = [];
        if (timeText) {
            parts.push('<span class="jim-conversation-time">' + escapeHtml(timeText) + '</span>');
        }
        parts.push(renderUnreadBadge(unreadCount));
        if (!parts.join('').trim()) {
            return '';
        }
        return '<span class="jim-conversation-meta">' + parts.join('') + '</span>';
    }

    function conversationItemAttrs(conversation, selected) {
        var attrs = ' type="button" class="jim-conversation-item jim-conversation-row' +
            (selected ? ' jim-conversation-item-selected is-selected' : '') + '" ' +
            ' data-conversation-id="' + conversation.id + '"';
        if (selected) {
            attrs += ' aria-current="true"';
        }
        return attrs;
    }

    function renderConversationItem(conversation) {
        var selected = conversation.id === state.selectedConversationId;

        var groupMark = isGroupConversation(conversation)
            ? '<span class="jim-conversation-group-mark" aria-hidden="true">&#128101;</span>'
            : '';

        return '' +
            '<button' + conversationItemAttrs(conversation, selected) + '>' +
            renderAvatarWithPresence(
                conversation.avatarUrl,
                conversation.displayName,
                'jim-avatar-sm',
                !!conversation.otherUserActive) +
            '  <div class="jim-conversation-main">' +
            '    <div class="jim-conversation-item-top">' +
            '      <span class="jim-conversation-title" dir="auto">' + groupMark + escapeHtml(conversation.displayName) + '</span>' +
            renderConversationMetaRight(formatTime(conversation.lastMessageAt), conversation.unreadCount) +
            '    </div>' +
            '    <div class="jim-conversation-item-bottom">' +
            '      <span class="jim-conversation-preview" dir="auto">' + escapeHtml(conversation.lastMessagePreview || '') + '</span>' +
            '    </div>' +
            '  </div>' +
            '</button>';
    }

    function renderAssistantItem(conversation) {
        if (!conversation) {
            return '';
        }

        var selected = conversation.id === state.selectedConversationId;
        var attrs = ' type="button" class="jim-assistant-item jim-assistant-conversation jim-conversation-row' +
            (selected ? ' jim-conversation-item-selected is-selected' : '') + '" ' +
            ' data-conversation-id="' + conversation.id + '"';
        if (selected) {
            attrs += ' aria-current="true"';
        }

        return '' +
            '<button' + attrs + '>' +
            '  <div class="jim-assistant-icon jim-conversation-avatar" aria-hidden="true">&#9889;</div>' +
            '  <div class="jim-conversation-main">' +
            '    <div class="jim-conversation-item-top">' +
            '      <span class="jim-conversation-title" dir="auto">' + escapeHtml(conversation.displayName) + '</span>' +
            '      <span class="jim-assistant-badge">BOT</span>' +
            '    </div>' +
            '    <div class="jim-conversation-item-bottom">' +
            '      <span class="jim-conversation-preview" dir="auto">' + escapeHtml(conversation.lastMessagePreview || '') + '</span>' +
            renderConversationMetaRight(formatTime(conversation.lastMessageAt), conversation.unreadCount) +
            '    </div>' +
            '  </div>' +
            '</button>';
    }

    function renderConversationList() {
        if (state.loadingConversations && !state.initialLoadComplete) {
            renderLoadingState(els.conversationList, 'Loading conversations...');
            return;
        }

        var assistant = getAssistantConversation(state.conversations);
        var rows = getDirectConversations(state.conversations);
        var tab = state.activeTab || 'all';

        if (tab === 'personal') {
            rows = rows.filter(function (conversation) {
                return !isGroupConversation(conversation);
            });
        } else if (tab === 'groups') {
            rows = rows.filter(isGroupConversation);
        }

        var showAssistant = tab !== 'groups';
        els.assistantSlot.innerHTML = showAssistant && assistant ? renderAssistantItem(assistant) : '';
        updateSidebarUnreadSummary();

        if (rows.length === 0) {
            els.conversationList.innerHTML =
                '<div class="jim-sidebar-empty">' +
                (tab === 'groups'
                    ? 'No groups yet. Create one with "+ New group".'
                    : 'Search for a teammate to start a conversation.') +
                '</div>';
            return;
        }

        els.conversationList.innerHTML =
            '<div class="jim-sidebar-section-label">' + (tab === 'groups' ? 'Groups' : 'Recent') + '</div>' +
            rows.map(renderConversationItem).join('');
    }

    var ACTIVE_TAB_KEY = 'jim-active-tab';

    function setActiveTab(tab) {
        if (state.activeTab === tab) {
            return;
        }
        state.activeTab = tab;
        try {
            window.localStorage.setItem(ACTIVE_TAB_KEY, tab);
        } catch (storageError) {
            // localStorage unavailable; the tab just won't be remembered.
        }
        for (var i = 0; i < els.sidebarTabs.length; i++) {
            var button = els.sidebarTabs[i];
            var isActive = button.getAttribute('data-tab') === tab;
            button.classList.toggle('is-active', isActive);
            button.setAttribute('aria-selected', isActive ? 'true' : 'false');
        }
        renderConversationList();
        bindConversationClicks(els.assistantSlot);
        bindConversationClicks(els.conversationList);
    }

    function restoreActiveTab() {
        var stored = null;
        try {
            stored = window.localStorage.getItem(ACTIVE_TAB_KEY);
        } catch (storageError) {
            return;
        }
        if (stored === 'personal' || stored === 'groups') {
            setActiveTab(stored);
        }
    }

    function renderHeaderAvatar(conversation) {
        if (isSystemConversation(conversation)) {
            return '' +
                '<div class="jim-chat-header-avatar jim-chat-header-assistant-avatar" aria-hidden="true">' +
                '  <span class="jim-chat-header-assistant-icon">&#9889;</span>' +
                '</div>';
        }

        return '<div class="jim-chat-header-avatar">' +
            renderAvatarWithPresence(
                conversation.avatarUrl,
                conversation.displayName,
                'jim-avatar-md',
                !!conversation.otherUserActive) +
            '</div>';
    }

    function renderChatHeader(conversation) {
        if (!conversation) {
            els.chatHeader.innerHTML = '';
            return;
        }

        var isSystem = isSystemConversation(conversation);
        var isGroup = isGroupConversation(conversation);
        var isActive = !isSystem && !isGroup && !!conversation.otherUserActive;
        var subtitle;
        if (isSystem) {
            subtitle = 'Jira notifications and updates';
        } else if (isGroup) {
            var memberCount = Number(conversation.memberCount) || 0;
            subtitle = memberCount === 1 ? '1 member' : memberCount + ' members';
            if (conversation.isProjectChat) {
                subtitle = 'Project chat \u00b7 ' + subtitle;
            }
        } else {
            subtitle = isActive ? 'Active now' : 'Direct message';
        }
        var presenceDot = isActive
            ? '<span class="jim-presence-dot" aria-hidden="true"></span>'
            : '';
        var badge = isSystem
            ? '<span class="jim-chat-header-badge">BOT</span>'
            : '';
        var actionsHtml = isGroup
            ? '<div class="jim-chat-header-actions">' +
              '<button id="jim-members-button" type="button" class="jim-header-button' +
              (state.membersPanelOpen ? ' is-active' : '') + '">Members</button>' +
              '</div>'
            : '';

        els.chatHeader.innerHTML = '' +
            '<button id="jim-back-button" type="button" class="jim-back-button" aria-label="Back to conversations">&#8592;</button>' +
            renderHeaderAvatar(conversation) +
            '<div class="jim-chat-header-main">' +
            '  <div class="jim-chat-header-title-row">' +
            '    <h2 class="jim-chat-header-title">' + escapeHtml(conversation.displayName) + '</h2>' +
            badge +
            '  </div>' +
            '  <p class="jim-chat-header-subtitle' + (isActive ? ' jim-chat-header-subtitle-active' : '') + '">' +
            presenceDot + escapeHtml(subtitle) + '</p>' +
            '</div>' +
            actionsHtml;

        var membersButton = document.getElementById('jim-members-button');
        if (membersButton) {
            membersButton.addEventListener('click', toggleMembersPanel);
        }
        var backButton = document.getElementById('jim-back-button');
        if (backButton) {
            backButton.addEventListener('click', closeMobileChat);
        }
    }

    function closeMobileChat() {
        if (els.app) {
            els.app.classList.remove('jim-mobile-chat-open');
        }
    }

    function toggleMembersPanel() {
        state.membersPanelOpen = !state.membersPanelOpen;
        state.confirmGroupDanger = false;
        if (state.membersPanelOpen) {
            loadGroupMembers();
        }
        renderMembersPanel();
        renderChatHeader(state.selectedConversation);
    }

    function loadGroupMembers() {
        var conversationId = state.selectedConversationId;
        if (!conversationId) {
            return;
        }
        JimApi.listGroupMembers(conversationId).then(function (response) {
            if (state.selectedConversationId !== conversationId) {
                return;
            }
            state.groupMembers = response.members || [];
            renderMembersPanel();
            if (state.messages.length) {
                renderMessages();
            }
        }).catch(function (error) {
            logError('listGroupMembers', error);
            showError(error && error.message ? error.message : 'Unable to load group members.');
        });
    }

    function renderMembersPanel() {
        if (!els.membersPanel) {
            return;
        }

        var conversation = state.selectedConversation;
        if (!state.membersPanelOpen || !conversation || !isGroupConversation(conversation)) {
            setHidden(els.membersPanel, true);
            els.membersPanel.innerHTML = '';
            return;
        }

        var canManage = !!conversation.canManage;
        var isProjectChat = !!conversation.isProjectChat;
        var ownerLabel = isProjectChat ? 'Project lead' : 'Owner';
        var rows = [];
        for (var i = 0; i < state.groupMembers.length; i++) {
            var member = state.groupMembers[i];
            var isOwner = member.role === 'OWNER';
            var removeButton = canManage && !isOwner
                ? '<button type="button" class="jim-member-remove" data-member-key="' + escapeHtml(member.userKey) +
                  '" aria-label="Remove ' + escapeHtml(member.displayName) + '" title="Remove from group">&times;</button>'
                : '';
            rows.push('<div class="jim-member-row">' +
                renderAvatar(member.avatarUrl, member.displayName, 'jim-avatar-sm') +
                '<span class="jim-member-name">' + escapeHtml(member.displayName) + '</span>' +
                (isOwner ? '<span class="jim-member-role">' + ownerLabel + '</span>' : '') +
                removeButton +
                '</div>');
        }

        var addHtml = canManage
            ? '<div class="jim-member-add">' +
              '<input id="jim-member-add-search" class="jim-search-input" type="search" autocomplete="off" placeholder="Add member..." />' +
              '<div id="jim-member-add-results" class="jim-search-results jim-member-add-results" hidden role="listbox"></div>' +
              '</div>'
            : '';

        var footerHtml;
        if (isProjectChat) {
            // Project chats cannot be deleted and members cannot leave;
            // only the project lead manages membership.
            footerHtml = '<div class="jim-members-panel-footer">' +
                '<div class="jim-members-panel-note">Members are managed by the project lead.</div>' +
                '</div>';
        } else {
            var dangerLabel;
            if (canManage) {
                dangerLabel = state.confirmGroupDanger ? 'Confirm: delete group?' : 'Delete group';
            } else {
                dangerLabel = state.confirmGroupDanger ? 'Confirm: leave group?' : 'Leave group';
            }
            footerHtml = '<div class="jim-members-panel-footer">' +
                '<button type="button" id="jim-group-danger" class="jim-member-danger' +
                (state.confirmGroupDanger ? ' is-confirming' : '') + '">' + dangerLabel + '</button>' +
                '</div>';
        }

        els.membersPanel.innerHTML =
            '<div class="jim-members-panel-header">Members (' + state.groupMembers.length + ')</div>' +
            '<div class="jim-members-list">' + rows.join('') + '</div>' +
            addHtml +
            footerHtml;
        setHidden(els.membersPanel, false);
        bindMembersPanelEvents();
    }

    function bindMembersPanelEvents() {
        var removeButtons = els.membersPanel.querySelectorAll('.jim-member-remove');
        for (var i = 0; i < removeButtons.length; i++) {
            removeButtons[i].addEventListener('click', function (event) {
                var memberKey = event.currentTarget.getAttribute('data-member-key');
                removeGroupMember(memberKey);
            });
        }

        var addSearch = document.getElementById('jim-member-add-search');
        var addResults = document.getElementById('jim-member-add-results');
        if (addSearch && addResults) {
            addSearch.addEventListener('input', function () {
                var query = addSearch.value.trim();
                if (state.memberAddSearchTimer) {
                    window.clearTimeout(state.memberAddSearchTimer);
                }
                if (query.length < 2) {
                    setHidden(addResults, true);
                    addResults.innerHTML = '';
                    return;
                }
                state.memberAddSearchTimer = window.setTimeout(function () {
                    JimApi.searchUsers(query).then(function (response) {
                        renderMemberAddResults(addResults, response.users || response.results || []);
                    }).catch(function (error) {
                        logError('memberAddSearch', error);
                    });
                }, 250);
            });
        }

        var dangerButton = document.getElementById('jim-group-danger');
        if (dangerButton) {
            dangerButton.addEventListener('click', handleGroupDangerAction);
        }
    }

    function renderMemberAddResults(container, users) {
        var existingKeys = {};
        for (var i = 0; i < state.groupMembers.length; i++) {
            existingKeys[state.groupMembers[i].userKey] = true;
        }

        var items = [];
        for (var j = 0; j < users.length; j++) {
            var user = users[j];
            if (existingKeys[user.userKey]) {
                continue;
            }
            items.push('<button type="button" class="jim-search-result jim-member-add-result" data-user-key="' +
                escapeHtml(user.userKey) + '">' +
                renderAvatar(user.avatarUrl, user.displayName, 'jim-avatar-sm') +
                '<span class="jim-search-result-name">' + escapeHtml(user.displayName) + '</span>' +
                '</button>');
        }

        if (!items.length) {
            container.innerHTML = '<div class="jim-search-empty">No matching users</div>';
            setHidden(container, false);
            return;
        }

        container.innerHTML = items.join('');
        setHidden(container, false);

        var buttons = container.querySelectorAll('.jim-member-add-result');
        for (var k = 0; k < buttons.length; k++) {
            buttons[k].addEventListener('click', function (event) {
                var userKey = event.currentTarget.getAttribute('data-user-key');
                addGroupMember(userKey);
            });
        }
    }

    function addGroupMember(userKey) {
        var conversationId = state.selectedConversationId;
        JimApi.addGroupMember(conversationId, userKey).then(function (response) {
            state.groupMembers = response.members || [];
            renderMembersPanel();
            return loadConversations(false);
        }).then(function () {
            return loadMessages(conversationId, false, false);
        }).catch(function (error) {
            logError('addGroupMember', error);
            showError(error && error.message ? error.message : 'Unable to add the member.');
        });
    }

    function removeGroupMember(userKey) {
        var conversationId = state.selectedConversationId;
        JimApi.removeGroupMember(conversationId, userKey).then(function () {
            loadGroupMembers();
            return loadConversations(false);
        }).then(function () {
            return loadMessages(conversationId, false, false);
        }).catch(function (error) {
            logError('removeGroupMember', error);
            showError(error && error.message ? error.message : 'Unable to remove the member.');
        });
    }

    function handleGroupDangerAction() {
        if (!state.confirmGroupDanger) {
            state.confirmGroupDanger = true;
            renderMembersPanel();
            return;
        }

        var conversation = state.selectedConversation;
        var conversationId = state.selectedConversationId;
        state.confirmGroupDanger = false;

        var action = conversation && conversation.canManage
            ? JimApi.deleteGroup(conversationId)
            : JimApi.removeGroupMember(conversationId, state.currentUserKey);

        action.then(function () {
            state.membersPanelOpen = false;
            state.groupMembers = [];
            state.selectedConversationId = null;
            state.selectedConversation = null;
            state.messages = [];
            renderMembersPanel();
            showChatPanel(false);
            return loadConversations(false);
        }).catch(function (error) {
            logError('groupDangerAction', error);
            showError(error && error.message ? error.message : 'Unable to complete the action.');
            renderMembersPanel();
        });
    }

    function pinnedPreviewText(message) {
        var body = message && message.body ? String(message.body).trim() : '';
        if (!body && message && message.attachments && message.attachments.length) {
            body = attachmentPreviewLabel(message.attachments[0]);
        }
        if (body.length > 140) {
            body = body.slice(0, 140) + '...';
        }
        return body;
    }

    function renderPinnedBanner() {
        if (!els.pinnedBanner) {
            return;
        }

        var pinned = state.pinnedMessage;
        if (!pinned) {
            setHidden(els.pinnedBanner, true);
            els.pinnedBanner.innerHTML = '';
            return;
        }

        els.pinnedBanner.innerHTML = '' +
            '<button type="button" class="jim-pinned-banner-body" data-pinned-target="' + pinned.id + '" title="Jump to pinned message">' +
            '  <span class="jim-pinned-banner-icon" aria-hidden="true">&#128204;</span>' +
            '  <span class="jim-pinned-banner-text"><strong>Pinned:</strong> ' + escapeHtml(pinnedPreviewText(pinned)) + '</span>' +
            '</button>' +
            '<button type="button" class="jim-pinned-banner-unpin" data-pinned-unpin="' + pinned.id + '" aria-label="Unpin message" title="Unpin">&times;</button>';
        setHidden(els.pinnedBanner, false);
    }

    function loadPinned(conversationId) {
        return JimApi.getPinnedMessage(conversationId).then(function (response) {
            if (state.selectedConversationId !== conversationId) {
                return;
            }
            state.pinnedMessage = response && response.message ? response.message : null;
            renderPinnedBanner();
        }).catch(function (error) {
            logError('loadPinned', error);
            return null;
        });
    }

    function messageSenderKey(message) {
        if (isSystemMessage(message)) {
            return 'SYSTEM';
        }
        return message.senderUserKey || message.senderType || 'unknown';
    }

    function messagesAreGrouped(previous, current) {
        if (!previous || !current) {
            return false;
        }
        if (isSystemMessage(previous) || isSystemMessage(current)) {
            return false;
        }
        return messageSenderKey(previous) === messageSenderKey(current);
    }

    function renderMessageAttachments(message) {
        var attachments = message.attachments || [];
        if (!attachments.length) {
            return '';
        }

        var html = ['<div class="jim-message-attachments">'];
        for (var i = 0; i < attachments.length; i++) {
            html.push(renderAttachmentItem(attachments[i]));
        }
        html.push('</div>');
        return html.join('');
    }

    function attachmentPreviewLabel(attachment) {
        if (!attachment) {
            return '';
        }
        if (attachment.fileKind === 'IMAGE') {
            return 'Image';
        }
        if (attachment.fileKind === 'AUDIO') {
            return 'Voice message';
        }
        return 'File: ' + (attachment.fileName || 'attachment');
    }

    function renderAttachmentItem(attachment) {
        if (!attachment) {
            return '';
        }

        var fileName = attachment.fileName || 'attachment';
        var downloadUrl = resolveAttachmentUrl(attachment.downloadUrl);
        var previewUrl = resolveAttachmentUrl(attachment.previewUrl);

        if (attachment.fileKind === 'AUDIO' && (previewUrl || downloadUrl)) {
            var audioSrc = previewUrl || downloadUrl;
            return '' +
                '<div class="jim-audio-attachment">' +
                '  <audio class="jim-audio-player" controls preload="metadata" src="' + escapeHtml(audioSrc) + '"></audio>' +
                '  <div class="jim-audio-meta">' +
                '    <span class="jim-audio-label">Voice message</span>' +
                '    <span class="jim-attachment-caption-size">' + escapeHtml(formatFileSize(attachment.fileSize)) + '</span>' +
                '  </div>' +
                '</div>';
        }

        if (attachment.fileKind === 'IMAGE' && previewUrl) {
            return '' +
                '<div class="jim-image-attachment">' +
                '  <a class="jim-image-preview-link" href="' + escapeHtml(previewUrl) + '" target="_blank" rel="noopener noreferrer"' +
                '     data-file-name="' + escapeHtml(fileName) + '"' +
                (downloadUrl ? ' data-download-url="' + escapeHtml(downloadUrl) + '"' : '') +
                '     title="Click to preview">' +
                '    <img class="jim-image-preview" src="' + escapeHtml(previewUrl) + '" alt="' + escapeHtml(fileName) + '" loading="lazy" data-fallback-name="' + escapeHtml(fileName) + '" />' +
                '  </a>' +
                '  <div class="jim-image-actions">' +
                (downloadUrl
                    ? '<a class="jim-image-download aui-button aui-button-link" href="' + escapeHtml(downloadUrl) + '" target="_blank" rel="noopener noreferrer">Download</a>'
                    : '') +
                '    <span class="jim-attachment-caption-size">' + escapeHtml(formatFileSize(attachment.fileSize)) + '</span>' +
                '  </div>' +
                '</div>';
        }

        return '' +
            '<div class="jim-file-card jim-attachment-card">' +
            '  <div class="jim-file-icon" aria-hidden="true">DOC</div>' +
            '  <div class="jim-file-meta">' +
            '    <div class="jim-file-name">' + escapeHtml(fileName) + '</div>' +
            '    <div class="jim-file-size">' + escapeHtml(formatFileSize(attachment.fileSize)) + '</div>' +
            '  </div>' +
            (downloadUrl
                ? '<a class="jim-file-download aui-button aui-button-link" href="' + escapeHtml(downloadUrl) + '" target="_blank" rel="noopener noreferrer">Download</a>'
                : '') +
            '</div>';
    }

    function isOwnMessage(message) {
        return !!(message.ownMessage || (message.senderUserKey && message.senderUserKey === state.currentUserKey));
    }

    function canDeleteMessage(message) {
        if (message.canDelete) {
            return true;
        }
        if (!isOwnMessage(message) || message.deleted) {
            return false;
        }
        var createdAt = Number(message.createdAt || 0);
        return createdAt > 0 && (Date.now() - createdAt) <= DELETE_WINDOW_MS;
    }

    function replyPreviewText(message) {
        if (!message || !message.replyTo) {
            return '';
        }
        if (message.replyTo.deleted) {
            return 'Deleted message';
        }
        if (message.replyTo.bodyPreview) {
            return message.replyTo.bodyPreview;
        }
        if (message.replyTo.attachmentPreview) {
            return message.replyTo.attachmentPreview;
        }
        return '';
    }

    function renderReplyPreview(message) {
        if (!message.replyTo) {
            return '';
        }
        var author = message.replyTo.senderDisplayName || 'User';
        var text = replyPreviewText(message);
        return '' +
            '<button type="button" class="jim-reply-preview" data-reply-target-id="' + message.replyTo.id + '">' +
            '  <span class="jim-reply-preview-author">' + escapeHtml(author) + '</span>' +
            '  <span class="jim-reply-preview-text" dir="auto">' + escapeHtml(text) + '</span>' +
            '</button>';
    }

    function renderMessageStatus(message) {
        if (!isOwnMessage(message) || message.deleted) {
            return '';
        }
        if (message.seenByOther) {
            return '<span class="jim-message-status jim-message-status-seen" title="Seen" aria-label="Seen">&#10003;&#10003;</span>';
        }
        return '<span class="jim-message-status jim-message-status-sent" title="Sent" aria-label="Sent">&#10003;</span>';
    }

    function renderReactions(message) {
        if (message.deleted || isSystemMessage(message)) {
            return '';
        }

        var reactions = message.reactions || [];
        var isPicking = state.reactionPickerMessageId === message.id;
        if (!reactions.length && !isPicking) {
            return '';
        }

        var chips = [];
        for (var i = 0; i < reactions.length; i++) {
            var reaction = reactions[i];
            chips.push('<button type="button" class="jim-reaction-chip' +
                (reaction.reactedByMe ? ' jim-reaction-chip-mine' : '') +
                '" data-action="react-pick" data-message-id="' + message.id +
                '" data-emoji="' + escapeHtml(reaction.emoji) + '" aria-label="Toggle reaction">' +
                escapeHtml(reaction.emoji) +
                ' <span class="jim-reaction-count">' + (Number(reaction.count) || 0) + '</span></button>');
        }

        if (isPicking) {
            chips.push('<span class="jim-reaction-picker">');
            for (var j = 0; j < REACTION_EMOJI.length; j++) {
                chips.push('<button type="button" class="jim-reaction-option" data-action="react-pick" data-message-id="' +
                    message.id + '" data-emoji="' + escapeHtml(REACTION_EMOJI[j]) + '" aria-label="React">' +
                    REACTION_EMOJI[j] + '</button>');
            }
            chips.push('</span>');
        }

        return '<div class="jim-message-reactions">' + chips.join('') + '</div>';
    }

    function renderMessageActions(message) {
        if (message.deleted || isSystemMessage(message)) {
            return '';
        }

        var actions = [];
        actions.push('<button type="button" class="jim-message-action jim-react-action" data-action="react" data-message-id="' + message.id + '">React</button>');
        actions.push('<button type="button" class="jim-message-action jim-reply-action" data-action="reply" data-message-id="' + message.id + '">Reply</button>');
        actions.push('<button type="button" class="jim-message-action jim-pin-action" data-action="' +
            (message.pinned ? 'unpin' : 'pin') + '" data-message-id="' + message.id + '">' +
            (message.pinned ? 'Unpin' : 'Pin') + '</button>');

        if (isOwnMessage(message)) {
            var conversationForInfo = state.selectedConversation;
            if (conversationForInfo && (isGroupConversation(conversationForInfo) || conversationForInfo.isProjectChat)) {
                actions.push('<button type="button" class="jim-message-action jim-info-action" data-action="info" data-message-id="' + message.id + '">Info</button>');
            }
            if (message.canEdit !== false && normalizeEventType(message) !== 'ISSUE_LINK') {
                actions.push('<button type="button" class="jim-message-action jim-edit-action" data-action="edit" data-message-id="' + message.id + '">Edit</button>');
            }
            if (canDeleteMessage(message)) {
                if (state.deleteConfirmMessageId === message.id) {
                    actions.push(
                        '<span class="jim-delete-confirm">' +
                        'Delete? ' +
                        '<button type="button" class="jim-message-action jim-delete-action" data-action="delete-confirm" data-message-id="' + message.id + '">Yes</button>' +
                        '<button type="button" class="jim-message-action" data-action="delete-cancel" data-message-id="' + message.id + '">No</button>' +
                        '</span>'
                    );
                } else {
                    actions.push('<button type="button" class="jim-message-action jim-delete-action" data-action="delete" data-message-id="' + message.id + '">Delete</button>');
                }
            }
        }

        return '<div class="jim-message-actions" role="toolbar" aria-label="Message actions">' + actions.join('') + '</div>';
    }

    // ===== Group message info (per-member read receipts) =====

    function openMessageInfo(messageId) {
        if (!els.messageInfoModal || !state.selectedConversationId) {
            return;
        }
        els.messageInfoBody.innerHTML = '<div class="jim-loading">Loading...</div>';
        setHidden(els.messageInfoModal, false);
        JimApi.getMessageReceipts(state.selectedConversationId, messageId).then(function (response) {
            renderMessageInfo(response && response.receipts ? response.receipts : []);
        }).catch(function (error) {
            logError('messageInfo', error);
            els.messageInfoBody.innerHTML = '<div class="jim-empty-state">' +
                escapeHtml(error && error.message ? error.message : 'Unable to load message info.') + '</div>';
        });
    }

    function closeMessageInfo() {
        if (els.messageInfoModal) {
            setHidden(els.messageInfoModal, true);
        }
    }

    function renderMessageInfo(receipts) {
        var read = [];
        var unread = [];
        for (var i = 0; i < receipts.length; i++) {
            (receipts[i].read ? read : unread).push(receipts[i]);
        }

        var html = [];
        html.push('<div class="jim-receipt-section-label">Read by ' + read.length +
            ' of ' + receipts.length + '</div>');
        if (read.length === 0) {
            html.push('<div class="jim-receipt-empty">Nobody has read this message yet.</div>');
        }
        for (var r = 0; r < read.length; r++) {
            html.push(renderReceiptRow(read[r]));
        }
        if (unread.length > 0) {
            html.push('<div class="jim-receipt-section-label">Not seen yet</div>');
            for (var u = 0; u < unread.length; u++) {
                html.push(renderReceiptRow(unread[u]));
            }
        }
        els.messageInfoBody.innerHTML = html.join('');
    }

    function renderReceiptRow(receipt) {
        var statusHtml = receipt.read
            ? '<span class="jim-receipt-status jim-receipt-status-read">&#10003;&#10003; ' +
              escapeHtml(receipt.readAt ? formatExactTime(receipt.readAt) : 'Read') + '</span>'
            : '<span class="jim-receipt-status">&#10003; Delivered</span>';
        return '' +
            '<div class="jim-receipt-row">' +
            renderAvatar(receipt.avatarUrl, receipt.displayName, 'jim-avatar-sm') +
            '  <span class="jim-receipt-name">' + escapeHtml(receipt.displayName || '') + '</span>' +
            statusHtml +
            '</div>';
    }

    // ===== Image lightbox =====

    function openImageLightbox(link) {
        if (!els.imageLightbox) {
            return;
        }
        var src = link.getAttribute('href');
        if (!src) {
            return;
        }
        els.imageLightboxImg.setAttribute('src', src);
        els.imageLightboxImg.setAttribute('alt', link.getAttribute('data-file-name') || 'Image');
        els.imageLightboxName.textContent = link.getAttribute('data-file-name') || '';
        var downloadUrl = link.getAttribute('data-download-url');
        if (downloadUrl) {
            els.imageLightboxDownload.setAttribute('href', downloadUrl);
            setHidden(els.imageLightboxDownload, false);
        } else {
            setHidden(els.imageLightboxDownload, true);
        }
        setHidden(els.imageLightbox, false);
    }

    function closeImageLightbox() {
        if (!els.imageLightbox || els.imageLightbox.hidden) {
            return;
        }
        setHidden(els.imageLightbox, true);
        // Release the image so a large file is not kept decoded in memory.
        els.imageLightboxImg.setAttribute('src', '');
    }

    function renderEditForm(message) {
        var body = message.body ? String(message.body) : '';
        return '' +
            '<form class="jim-message-edit-form" data-message-id="' + message.id + '">' +
            '  <textarea class="jim-message-edit-textarea" rows="3" maxlength="' + MAX_MESSAGE_LENGTH + '">' + escapeHtml(body) + '</textarea>' +
            '  <div class="jim-message-edit-error" hidden></div>' +
            '  <div class="jim-message-edit-actions">' +
            '    <button type="button" class="aui-button aui-button-link" data-action="edit-cancel" data-message-id="' + message.id + '">Cancel</button>' +
            '    <button type="submit" class="aui-button aui-button-primary" data-action="edit-save" data-message-id="' + message.id + '">Save</button>' +
            '  </div>' +
            '</form>';
    }

    function renderIssueChatCard(message) {
        if (!message || !message.issueKey) {
            return '';
        }

        var url = resolveIssueUrl(message.issueUrl);
        var metaParts = [];
        if (message.issueTypeName) {
            metaParts.push('<span class="jim-issue-chat-card-type">' + escapeHtml(message.issueTypeName) + '</span>');
        }
        if (message.issuePriorityName) {
            metaParts.push('<span class="jim-issue-chat-card-priority">' + escapeHtml(message.issuePriorityName) + '</span>');
        }
        var statusHtml = '';
        if (message.issueStatusName) {
            var category = String(message.issueStatusCategory || 'new').toLowerCase();
            statusHtml = '<span class="jim-issue-chat-card-status jim-issue-status-' + escapeHtml(category) + '">' +
                escapeHtml(String(message.issueStatusName).toUpperCase()) + '</span>';
        }

        var keyHtml = url
            ? '<a class="jim-issue-chat-card-key" href="' + escapeHtml(url) + '" target="_blank" rel="noopener noreferrer">' +
              escapeHtml(message.issueKey) + '</a>'
            : '<span class="jim-issue-chat-card-key">' + escapeHtml(message.issueKey) + '</span>';

        return '' +
            '<div class="jim-issue-chat-card">' +
            ((metaParts.length || statusHtml)
                ? '<div class="jim-issue-chat-card-top">' +
                  '<span class="jim-issue-chat-card-meta">' + metaParts.join('<span class="jim-issue-chat-card-dot">&bull;</span>') + '</span>' +
                  statusHtml +
                  '</div>'
                : '') +
            '<div class="jim-issue-chat-card-main">' +
            keyHtml +
            '<span class="jim-issue-chat-card-summary">' + escapeHtml(message.issueSummary || '') + '</span>' +
            '</div>' +
            (url
                ? '<a class="jim-issue-chat-card-open" href="' + escapeHtml(url) + '" target="_blank" rel="noopener noreferrer">Open issue &#8594;</a>'
                : '') +
            '</div>';
    }

    function renderUserMessageBubble(message, options) {
        var grouped = options && options.grouped;
        var lastInGroup = options && options.lastInGroup;
        var bubbleClass = 'jim-message-bubble' + (grouped ? ' jim-message-bubble-grouped' : '');
        var isEditing = state.editingMessageId === message.id;
        var contentHtml = '';

        if (message.deleted) {
            contentHtml = '<div class="jim-message-deleted">This message was deleted.</div>';
        } else if (isEditing) {
            contentHtml = renderEditForm(message);
        } else {
            var body = message.body ? String(message.body).trim() : '';
            contentHtml = body ? '<div class="jim-message-text" dir="auto">' + renderMessageTextHtml(body) + '</div>' : '';
            if (normalizeEventType(message) === 'ISSUE_LINK') {
                contentHtml += renderIssueChatCard(message);
            }
            contentHtml += renderMessageAttachments(message);
        }

        var editedLabel = message.edited && !message.deleted
            ? '<span class="jim-message-edited">(edited)</span>'
            : '';

        return '' +
            '<div class="jim-message-bubble-wrapper" data-message-id="' + message.id + '">' +
            '  <div class="' + bubbleClass + '">' +
            (!message.deleted && !isEditing ? renderReplyPreview(message) : '') +
            contentHtml +
            (editedLabel ? '<div class="jim-message-meta">' + editedLabel + '</div>' : '') +
            '  </div>' +
            (!isEditing ? renderReactions(message) : '') +
            (!isEditing ? renderMessageActions(message) : '') +
            '</div>';
    }

    function getEventCardConfig(message) {
        var eventType = normalizeEventType(message) || 'SYSTEM';
        return EVENT_CARD_CONFIG[eventType] || {
            label: eventType.charAt(0) + eventType.slice(1).toLowerCase(),
            tone: 'info',
            title: function (m) {
                return m.body || 'Jira Assistant notification';
            }
        };
    }

    function systemCardBodyText(message, titleText) {
        if (!message || !message.body) {
            return '';
        }
        var body = String(message.body).trim();
        if (!body) {
            return '';
        }
        if (titleText && body === titleText) {
            return '';
        }
        return body;
    }

    function renderSystemMessage(message) {
        var config = getEventCardConfig(message);
        var titleText = config.title(message);
        var bodyText = systemCardBodyText(message, titleText);
        var issueUrl = resolveIssueUrl(message.issueUrl);
        var issueLink = '';

        if (issueUrl) {
            issueLink = '<a class="jim-system-card-link jim-issue-link-button aui-button aui-button-link" href="' +
                escapeHtml(issueUrl) + '" target="_blank" rel="noopener noreferrer">Open issue</a>';
        }

        var eventType = normalizeEventType(message) || 'system';
        var isUnread = !!(message.id && state.initiallyUnreadIds[message.id]);
        var unreadBadge = isUnread
            ? '<span class="jim-system-card-new-badge">NEW</span>'
            : '';

        return '' +
            '<div class="jim-message-row jim-message-row-system">' +
            '  <article class="jim-system-card jim-issue-card jim-system-card-tone-' + escapeHtml(config.tone) +
            ' jim-system-card-event-' + escapeHtml(eventType.toLowerCase()) +
            (isUnread ? ' jim-system-card-new' : '') + '">' +
            '    <header class="jim-system-card-header">' +
            '      <span class="jim-system-card-lozenge jim-system-card-lozenge-' + escapeHtml(config.tone) + '">' +
            escapeHtml(config.label) + '</span>' +
            unreadBadge +
            '      <span class="jim-system-card-time">' + escapeHtml(formatMessageTime(message.createdAt)) + '</span>' +
            '    </header>' +
            '    <div class="jim-system-card-body">' +
            '      <div class="jim-system-card-title">' + escapeHtml(titleText) + '</div>' +
            (message.issueKey ? '<div class="jim-system-card-issue-key">' + escapeHtml(message.issueKey) + '</div>' : '') +
            (message.issueSummary ? '<div class="jim-system-card-summary">' + escapeHtml(message.issueSummary) + '</div>' : '') +
            (bodyText ? '<div class="jim-system-card-text">' + escapeHtml(bodyText) + '</div>' : '') +
            '    </div>' +
            (issueLink ? '<footer class="jim-system-card-actions">' + issueLink + '</footer>' : '') +
            '  </article>' +
            '</div>';
    }

    function renderMessageGroups(messages) {
        var html = [];
        var index = 0;
        var previousTimestamp = null;

        while (index < messages.length) {
            var message = messages[index];

            if (!previousTimestamp || !isSameDay(previousTimestamp, message.createdAt)) {
                html.push(renderDateDivider(formatDateDivider(message.createdAt)));
            }
            previousTimestamp = message.createdAt;

            if (normalizeEventType(message) === 'GROUP_EVENT') {
                html.push('<div class="jim-message-row jim-message-row-system">' +
                    '<div class="jim-group-event">' + escapeHtml(message.body || '') + '</div>' +
                    '</div>');
                index++;
                continue;
            }

            if (isSystemMessage(message)) {
                html.push(renderSystemMessage(message));
                index++;
                continue;
            }

            var group = [message];
            var next = index + 1;
            while (next < messages.length && messagesAreGrouped(message, messages[next])) {
                group.push(messages[next]);
                next++;
            }

            var isOwn = isOwnMessage(message);
            var rowClass = 'jim-message-row ' + (isOwn ? 'jim-message-row-own' : 'jim-message-row-other');
            var bubbles = [];

            for (var g = 0; g < group.length; g++) {
                bubbles.push(renderUserMessageBubble(group[g], {
                    grouped: g > 0,
                    showSender: false,
                    lastInGroup: g === group.length - 1
                }));
            }

            var conversation = state.selectedConversation;
            var senderName = isOwn
                ? 'You'
                : (message.senderDisplayName || message.senderUserKey || 'User');
            var headerTime = escapeHtml(formatMessageTime(message.createdAt));
            var headerHtml = isOwn
                ? '<div class="jim-message-group-header jim-message-group-header-own">' +
                  '<span class="jim-message-group-time">' + headerTime + '</span>' +
                  '<span class="jim-message-group-sender">You</span>' +
                  '</div>'
                : '<div class="jim-message-group-header">' +
                  '<span class="jim-message-group-sender">' + escapeHtml(senderName) + '</span>' +
                  '<span class="jim-message-group-time">' + headerTime + '</span>' +
                  '</div>';

            var avatarHtml = '';
            if (!isOwn) {
                var avatarUrl = conversation && !isSystemConversation(conversation) ? conversation.avatarUrl : null;
                avatarHtml = '<div class="jim-message-avatar">' +
                    renderAvatar(avatarUrl, senderName, 'jim-avatar-sm') +
                    '</div>';
            }

            var lastMessage = group[group.length - 1];
            var receiptHtml = '';
            if (isOwn && !lastMessage.deleted) {
                var readTime = lastMessage.seenAt ? formatExactTime(lastMessage.seenAt) : '';
                receiptHtml = lastMessage.seenByOther
                    ? '<div class="jim-message-receipt jim-message-receipt-seen">&#10003;&#10003; Read' +
                      (readTime ? ' ' + escapeHtml(readTime) : '') + '</div>'
                    : '<div class="jim-message-receipt">&#10003; Sent</div>';
            }

            html.push(
                '<div class="' + rowClass + '">' +
                avatarHtml +
                '  <div class="jim-message-group">' + headerHtml + bubbles.join('') + receiptHtml + '</div>' +
                '</div>'
            );

            index = next;
        }

        return html.join('');
    }

    function renderComposerReply() {
        if (!els.composerReply) {
            return;
        }
        if (!state.replyToMessage) {
            setHidden(els.composerReply, true);
            return;
        }

        var preview = state.replyToMessage.deleted
            ? 'Deleted message'
            : (state.replyToMessage.bodyPreview || state.replyToMessage.attachmentPreview || state.replyToMessage.body || '');

        if (els.composerReplyAuthor) {
            els.composerReplyAuthor.textContent = state.replyToMessage.senderDisplayName || 'User';
        }
        if (els.composerReplyText) {
            els.composerReplyText.textContent = String(preview).trim();
        }
        setHidden(els.composerReply, false);
    }

    function clearReplyTarget() {
        state.replyToMessage = null;
        renderComposerReply();
    }

    function setReplyTarget(message) {
        if (!message || message.deleted) {
            return;
        }
        state.replyToMessage = {
            id: message.id,
            senderDisplayName: message.senderDisplayName || message.senderUserKey || 'User',
            body: message.body,
            bodyPreview: message.body,
            attachmentPreview: null,
            deleted: false
        };
        if (message.replyTo) {
            state.replyToMessage.bodyPreview = message.replyTo.bodyPreview;
        }
        var attachments = message.attachments || [];
        if (!state.replyToMessage.bodyPreview && attachments.length) {
            state.replyToMessage.attachmentPreview = attachmentPreviewLabel(attachments[0]);
        }
        state.editingMessageId = null;
        renderComposerReply();
        if (els.messageInput) {
            els.messageInput.focus();
        }
    }

    function scrollToReplyTarget(messageId) {
        if (!els.messageList || !messageId) {
            return;
        }
        var target = els.messageList.querySelector('[data-message-id="' + messageId + '"]');
        if (!target) {
            return;
        }
        target.scrollIntoView({ behavior: 'smooth', block: 'center' });
        target.classList.add('jim-message-highlight');
        window.setTimeout(function () {
            target.classList.remove('jim-message-highlight');
        }, 1500);
    }

    function findMessageById(messageId) {
        for (var i = 0; i < state.messages.length; i++) {
            if (state.messages[i].id === messageId) {
                return state.messages[i];
            }
        }
        return null;
    }

    function bindImageFallbacks() {
        if (!els.messageList) {
            return;
        }
        var images = els.messageList.querySelectorAll('.jim-image-preview');
        for (var i = 0; i < images.length; i++) {
            images[i].addEventListener('error', function (event) {
                var img = event.currentTarget;
                var container = img.closest('.jim-image-attachment');
                if (!container) {
                    return;
                }
                var fileName = img.getAttribute('data-fallback-name') || 'Image';
                container.outerHTML =
                    '<div class="jim-file-card jim-attachment-card">' +
                    '  <div class="jim-file-icon" aria-hidden="true">IMG</div>' +
                    '  <div class="jim-file-meta"><div class="jim-file-name">' + escapeHtml(fileName) + '</div></div>' +
                    '</div>';
            }, { once: true });
        }
    }

    function saveEditedMessage(messageId, body) {
        return JimApi.editMessage(messageId, body).then(function () {
            state.editingMessageId = null;
            return loadMessages(state.selectedConversationId, false, false);
        }).then(function () {
            return loadConversations(false);
        });
    }

    function deleteMessageConfirmed(messageId) {
        return JimApi.deleteMessage(messageId).then(function () {
            state.deleteConfirmMessageId = null;
            if (state.replyToMessage && state.replyToMessage.id === messageId) {
                clearReplyTarget();
            }
            return loadMessages(state.selectedConversationId, false, false);
        }).then(function () {
            return loadConversations(false);
        });
    }

    function bindMessageActions() {
        if (!els.messageList) {
            return;
        }

        els.messageList.addEventListener('click', function (event) {
            var target = event.target;
            if (!target || !target.getAttribute) {
                return;
            }

            var replyPreview = target.closest('.jim-reply-preview');
            if (replyPreview) {
                event.preventDefault();
                var replyTargetId = parseInt(replyPreview.getAttribute('data-reply-target-id'), 10);
                scrollToReplyTarget(replyTargetId);
                return;
            }

            var actionEl = target.closest('[data-action]');
            if (!actionEl) {
                return;
            }
            var action = actionEl.getAttribute('data-action');

            var messageId = parseInt(actionEl.getAttribute('data-message-id'), 10);
            if (!messageId) {
                return;
            }

            if (action === 'react') {
                state.reactionPickerMessageId = state.reactionPickerMessageId === messageId ? null : messageId;
                renderMessages();
                return;
            }
            if (action === 'pin' || action === 'unpin') {
                var pinPromise = action === 'pin'
                    ? JimApi.pinMessage(messageId)
                    : JimApi.unpinMessage(messageId);
                pinPromise.then(function () {
                    return loadPinned(state.selectedConversationId);
                }).then(function () {
                    return loadMessages(state.selectedConversationId, false, false);
                }).catch(function (error) {
                    logError('pinMessage', error);
                    showError(error && error.message ? error.message : 'Unable to update pinned message.');
                });
                return;
            }
            if (action === 'react-pick') {
                var emoji = actionEl.getAttribute('data-emoji');
                state.reactionPickerMessageId = null;
                JimApi.toggleReaction(messageId, emoji).then(function () {
                    return loadMessages(state.selectedConversationId, false, false);
                }).catch(function (error) {
                    logError('toggleReaction', error);
                    showError(error && error.message ? error.message : 'Unable to update reaction.');
                    renderMessages();
                });
                return;
            }
            if (action === 'reply') {
                setReplyTarget(findMessageById(messageId));
                return;
            }
            if (action === 'info') {
                openMessageInfo(messageId);
                return;
            }
            if (action === 'edit') {
                state.editingMessageId = messageId;
                state.deleteConfirmMessageId = null;
                renderMessages();
                return;
            }
            if (action === 'edit-cancel') {
                state.editingMessageId = null;
                renderMessages();
                return;
            }
            if (action === 'delete') {
                state.deleteConfirmMessageId = messageId;
                renderMessages();
                return;
            }
            if (action === 'delete-cancel') {
                state.deleteConfirmMessageId = null;
                renderMessages();
                return;
            }
            if (action === 'delete-confirm') {
                deleteMessageConfirmed(messageId).catch(function (error) {
                    logError('deleteMessage', error);
                    showError(error && error.message ? error.message : 'Unable to delete message.');
                    state.deleteConfirmMessageId = null;
                    renderMessages();
                });
            }
        });

        els.messageList.addEventListener('submit', function (event) {
            var form = event.target;
            if (!form || !form.classList || !form.classList.contains('jim-message-edit-form')) {
                return;
            }
            event.preventDefault();

            var messageId = parseInt(form.getAttribute('data-message-id'), 10);
            var textarea = form.querySelector('.jim-message-edit-textarea');
            var errorEl = form.querySelector('.jim-message-edit-error');
            var body = textarea ? normalizeMessageBody(textarea.value) : '';

            if (!body) {
                if (errorEl) {
                    errorEl.textContent = 'Message cannot be empty.';
                    setHidden(errorEl, false);
                }
                return;
            }

            if (errorEl) {
                setHidden(errorEl, true);
            }

            saveEditedMessage(messageId, body).catch(function (error) {
                logError('editMessage', error);
                if (errorEl) {
                    errorEl.textContent = error && error.message ? error.message : 'Unable to save changes.';
                    setHidden(errorEl, false);
                }
            });
        });
    }

    var messageActionsBound = false;

    function ensureMessageActionsBound() {
        if (!messageActionsBound) {
            bindMessageActions();
            messageActionsBound = true;
        }
    }

    function captureAudioPlayback() {
        var saved = [];
        if (!els.messageList) {
            return saved;
        }
        var players = els.messageList.querySelectorAll('audio.jim-audio-player');
        for (var i = 0; i < players.length; i++) {
            var player = players[i];
            if (player.currentTime > 0 || !player.paused) {
                saved.push({
                    src: player.getAttribute('src'),
                    time: player.currentTime,
                    playing: !player.paused && !player.ended
                });
            }
        }
        return saved;
    }

    function restoreAudioPlayback(saved) {
        if (!saved || !saved.length || !els.messageList) {
            return;
        }
        var players = els.messageList.querySelectorAll('audio.jim-audio-player');
        for (var i = 0; i < saved.length; i++) {
            var entry = saved[i];
            for (var j = 0; j < players.length; j++) {
                if (players[j].getAttribute('src') === entry.src) {
                    restoreAudioElement(players[j], entry.time, entry.playing);
                    break;
                }
            }
        }
    }

    function restoreAudioElement(audio, time, playing) {
        var apply = function () {
            try {
                audio.currentTime = time;
            } catch (seekError) {
                // non-fatal: start from the beginning
            }
            if (playing) {
                var playPromise = audio.play();
                if (playPromise && typeof playPromise.catch === 'function') {
                    playPromise.catch(function () {
                        // autoplay restrictions: user can press play again
                    });
                }
            }
        };
        if (audio.readyState >= 1) {
            apply();
        } else {
            audio.addEventListener('loadedmetadata', apply, { once: true });
        }
    }

    function renderMessages() {
        if (state.loadingMessages) {
            return;
        }

        if (!state.messages.length) {
            state.renderedMessagesHtml = null;
            els.messageList.innerHTML =
                '<div class="jim-message-list-inner"><div class="jim-empty-state">No messages yet. Start the conversation.</div></div>';
            return;
        }

        var html = '<div class="jim-message-list-inner">' + renderMessageGroups(state.messages) + '</div>';
        if (html !== state.renderedMessagesHtml) {
            var savedAudio = captureAudioPlayback();
            els.messageList.innerHTML = html;
            state.renderedMessagesHtml = html;
            bindImageFallbacks();
            restoreAudioPlayback(savedAudio);
        }
        if (state.shouldAutoScroll) {
            scrollMessagesToBottom();
            state.shouldAutoScroll = false;
        }
    }

    function hasComposerText() {
        if (!els.messageInput) {
            return false;
        }
        return normalizeMessageBody(els.messageInput.value).length > 0;
    }

    function hasComposerPayload() {
        return hasComposerText() || !!state.selectedFile || !!state.selectedIssue;
    }

    function updateComposerState() {
        if (!els.messageInput || !els.sendButton) {
            return;
        }

        var readOnly = state.licenseBlocked ||
            (state.selectedConversation && isSystemConversation(state.selectedConversation));
        var busy = state.sending || state.uploading;
        var canSend = hasComposerPayload();

        if (els.composer) {
            els.composer.classList.toggle('jim-composer-is-readonly', !!readOnly);
        }
        setHidden(els.composerReadonly, !readOnly);
        setHidden(els.composerInner, !!readOnly);

        els.messageInput.disabled = !!readOnly || busy;
        els.sendButton.disabled = !!readOnly || busy || !canSend;
        if (els.attachButton) {
            els.attachButton.disabled = !!readOnly || busy;
        }
        if (els.voiceButton && !state.voiceRecorder) {
            els.voiceButton.disabled = !!readOnly || busy;
        }
        if (els.mentionButton) {
            setHidden(els.mentionButton, !isGroupConversation(state.selectedConversation));
            els.mentionButton.disabled = !!readOnly || busy;
        }
        if (els.issueButton) {
            els.issueButton.disabled = !!readOnly || busy;
        }
        if (els.selectedAttachmentRemove) {
            els.selectedAttachmentRemove.disabled = !!readOnly || busy;
        }

        if (state.uploading) {
            els.sendButton.textContent = 'Uploading...';
        } else if (state.sending) {
            els.sendButton.textContent = 'Sending...';
        } else {
            els.sendButton.textContent = 'Send';
        }

        setHidden(els.uploadProgress, !state.uploading);

        if (!readOnly) {
            els.messageInput.placeholder = 'Write a message... (Enter to send, Shift+Enter for new line)';
        }
    }

    function showChatPanel(show) {
        if (els.useLegacyLayout) {
            setHidden(els.chatEmpty, show);
            setHidden(els.legacyChatPanel, !show);
            return;
        }

        if (els.chatPanelShell) {
            els.chatPanelShell.classList.toggle('jim-chat-panel-empty', !show);
        }
        setHidden(els.chatEmpty, show);
        setHidden(els.chatActive, !show);
    }

    function bindConversationClicks(container) {
        if (!container) {
            return;
        }

        var items = container.querySelectorAll('[data-conversation-id]');
        for (var i = 0; i < items.length; i++) {
            items[i].addEventListener('click', function (event) {
                var id = parseInt(event.currentTarget.getAttribute('data-conversation-id'), 10);
                selectConversation(id);
            });
        }
    }

    function findConversationById(id) {
        for (var i = 0; i < state.conversations.length; i++) {
            if (state.conversations[i].id === id) {
                return state.conversations[i];
            }
        }
        return null;
    }

    function loadMessages(conversationId, showLoading, autoScroll) {
        if (autoScroll) {
            state.shouldAutoScroll = true;
        }
        if (showLoading) {
            state.loadingMessages = true;
            state.renderedMessagesHtml = null;
            renderLoadingState(els.messageList, 'Loading messages...');
        }

        return JimApi.listMessages(conversationId, 50, null).then(function (response) {
            if (state.selectedConversationId !== conversationId) {
                return;
            }
            state.messages = response.messages || [];
            if (state.unreadSnapshotConversationId !== conversationId) {
                // Snapshot which messages were unread when the conversation was opened,
                // so "NEW" markers stay visible after the read state syncs.
                state.unreadSnapshotConversationId = conversationId;
                state.initiallyUnreadIds = {};
                for (var u = 0; u < state.messages.length; u++) {
                    var unreadCandidate = state.messages[u];
                    if (unreadCandidate && unreadCandidate.id
                            && !unreadCandidate.readByCurrentUser
                            && !isOwnMessage(unreadCandidate)) {
                        state.initiallyUnreadIds[unreadCandidate.id] = true;
                    }
                }
            }
            state.loadingMessages = false;
            renderMessages();
        }).catch(function (error) {
            state.loadingMessages = false;
            logError('loadMessages', error);
            if (state.selectedConversationId === conversationId) {
                state.renderedMessagesHtml = null;
                var detail = error && error.message ? error.message : 'Unable to load messages.';
                renderErrorState(els.messageList, detail, function () {
                    loadMessages(conversationId, true, true);
                });
            }
            showError(error && error.message ? error.message : 'Unable to load messages.');
        });
    }

    function markRead(conversationId) {
        return JimApi.markConversationRead(conversationId).catch(function (error) {
            logError('markRead', error);
            return null;
        });
    }

    function selectConversation(conversationId) {
        var conversation = findConversationById(conversationId);
        if (!conversation) {
            return;
        }

        state.selectedConversationId = conversationId;
        state.selectedConversation = conversation;
        state.messages = [];
        state.renderedMessagesHtml = null;
        cancelVoiceRecording();
        clearSelectedFile();
        clearSelectedIssue();
        clearReplyTarget();
        state.editingMessageId = null;
        state.deleteConfirmMessageId = null;
        state.reactionPickerMessageId = null;
        state.pinnedMessage = null;
        state.membersPanelOpen = false;
        state.groupMembers = [];
        state.confirmGroupDanger = false;
        toggleMentionPalette(true);
        toggleIssuePalette(true);
        if (isGroupConversation(conversation)) {
            loadGroupMembers();
        }
        showComposerError(null);
        setHidden(els.composer, false);

        if (els.app) {
            els.app.classList.add('jim-mobile-chat-open');
        }
        showChatPanel(true);
        renderConversationList();
        renderChatHeader(conversation);
        renderMembersPanel();
        renderPinnedBanner();
        updateComposerState();

        loadMessages(conversationId, true, true);
        loadPinned(conversationId);
        markRead(conversationId).then(function () {
            clearConversationUnread(conversationId);
            return loadConversations(false);
        });
    }

    function loadProjectConversation() {
        return JimApi.getProjectConversation(state.projectKey).then(function (response) {
            state.loadingConversations = false;
            state.initialLoadComplete = true;
            var conversation = response && response.conversation;
            if (!conversation) {
                showError('The project chat is not available.');
                return;
            }
            state.conversations = [conversation];

            if (!response.isMember && !response.isLead) {
                renderProjectAccessNotice(conversation, response.leadDisplayName);
                return;
            }

            if (state.selectedConversationId !== conversation.id) {
                selectConversation(conversation.id);
            } else {
                state.selectedConversation = conversation;
                renderChatHeader(conversation);
            }
        }).catch(function (error) {
            state.loadingConversations = false;
            logError('loadProjectConversation', error);
            showError(error && error.message ? error.message : 'Unable to load the project chat.');
        });
    }

    function renderProjectAccessNotice(conversation, leadDisplayName) {
        showChatPanel(true);
        if (els.app) {
            els.app.classList.add('jim-mobile-chat-open');
        }
        renderChatHeader(conversation);
        var membersButton = document.getElementById('jim-members-button');
        if (membersButton && membersButton.parentNode) {
            membersButton.parentNode.removeChild(membersButton);
        }
        setHidden(els.composer, true);
        els.messageList.innerHTML =
            '<div class="jim-message-list-inner">' +
            '<div class="jim-project-access-notice">' +
            '<div class="jim-project-access-icon" aria-hidden="true">&#128274;</div>' +
            '<div class="jim-project-access-title">You are not a member of this project chat yet</div>' +
            '<div class="jim-project-access-text">Ask the project lead' +
            (leadDisplayName ? ' (' + escapeHtml(leadDisplayName) + ')' : '') +
            ' to add you to the chat.</div>' +
            '</div></div>';
    }

    function loadConversations(showLoading) {
        if (state.projectMode) {
            return loadProjectConversation();
        }

        if (showLoading) {
            state.loadingConversations = true;
            renderConversationList();
        }

        return JimApi.listConversations().then(function (response) {
            state.conversations = response.conversations || [];
            state.loadingConversations = false;
            state.initialLoadComplete = true;
            renderConversationList();
            bindConversationClicks(els.assistantSlot);
            bindConversationClicks(els.conversationList);

            if (!state.selectedConversationId) {
                var assistant = getAssistantConversation(state.conversations);
                if (assistant) {
                    selectConversation(assistant.id);
                }
            } else {
                state.selectedConversation = findConversationById(state.selectedConversationId);
                if (!state.selectedConversation) {
                    // Conversation disappeared (e.g. removed from a group or group deleted)
                    state.selectedConversationId = null;
                    state.messages = [];
                    state.membersPanelOpen = false;
                    state.groupMembers = [];
                    renderMembersPanel();
                    showChatPanel(false);
                }
                renderChatHeader(state.selectedConversation);
                renderConversationList();
                bindConversationClicks(els.assistantSlot);
                bindConversationClicks(els.conversationList);
            }
        }).catch(function (error) {
            state.loadingConversations = false;
            logError('loadConversations', error);
            var detail = error && error.message ? error.message : 'Unable to load conversations.';
            renderErrorState(els.conversationList, detail, function () {
                loadConversations(true);
            });
            showError(detail);
        });
    }

    function renderSearchResults(users) {
        if (!els.searchResults) {
            return;
        }

        if (!users.length) {
            els.searchResults.innerHTML = '<div class="jim-search-empty">No users found.</div>';
            setHidden(els.searchResults, false);
            return;
        }

        els.searchResults.innerHTML = users.map(function (user) {
            return '' +
                '<button type="button" class="jim-search-result-item" role="option" data-user-key="' + escapeHtml(user.userKey) + '">' +
                renderAvatar(user.avatarUrl, user.displayName, 'jim-avatar-sm') +
                '  <span class="jim-conversation-title">' + escapeHtml(user.displayName) + '</span>' +
                '</button>';
        }).join('');

        var buttons = els.searchResults.querySelectorAll('.jim-search-result-item');
        for (var i = 0; i < buttons.length; i++) {
            buttons[i].addEventListener('click', onSearchResultClick);
        }

        setHidden(els.searchResults, false);
    }

    function onSearchResultClick(event) {
        var userKey = event.currentTarget.getAttribute('data-user-key');
        if (!userKey) {
            return;
        }

        setHidden(els.searchResults, true);
        if (els.searchInput) {
            els.searchInput.value = '';
        }

        JimApi.createDirectConversation(userKey).then(function (conversation) {
            return loadConversations(false).then(function () {
                selectConversation(conversation.id);
            });
        }).catch(function (error) {
            logError('createDirectConversation', error);
            showError(error && error.message ? error.message : 'Unable to start conversation.');
        });
    }

    function onSearchInput() {
        if (!els.searchInput) {
            return;
        }

        var query = els.searchInput.value.trim();

        window.clearTimeout(state.searchTimer);

        if (!els.searchResults) {
            return;
        }

        if (query.length < MIN_SEARCH_LENGTH) {
            setHidden(els.searchResults, true);
            els.searchResults.innerHTML = '';
            if (query.length > 0) {
                els.searchResults.innerHTML = '<div class="jim-search-empty">Type at least 2 characters to search.</div>';
                setHidden(els.searchResults, false);
            }
            return;
        }

        state.searchTimer = window.setTimeout(function () {
            JimApi.searchUsers(query).then(function (response) {
                renderSearchResults(response.users || []);
            }).catch(function (error) {
                logError('searchUsers', error);
                els.searchResults.innerHTML = '<div class="jim-search-empty">Unable to search users. Try again.</div>';
                setHidden(els.searchResults, false);
                showError(error && error.message ? error.message : 'Search failed.');
            });
        }, SEARCH_DEBOUNCE_MS);
    }

    function sendMessage() {
        if (!state.selectedConversationId || state.sending || state.uploading || !els.messageInput) {
            return;
        }

        if (state.selectedConversation && isSystemConversation(state.selectedConversation)) {
            return;
        }

        var body = normalizeMessageBody(els.messageInput.value);
        if (!body && !state.selectedFile && !state.selectedIssue) {
            showComposerError('Add a message or choose a file to send.');
            return;
        }
        if (body.length > MAX_MESSAGE_LENGTH) {
            showComposerError('Message exceeds the maximum length of ' + MAX_MESSAGE_LENGTH + ' characters.');
            return;
        }

        showComposerError(null);

        if (state.selectedFile) {
            state.uploading = true;
            updateComposerState();
            JimApi.uploadAttachment(state.selectedConversationId, state.selectedFile, body || null).then(function () {
                els.messageInput.value = '';
                clearSelectedFile();
                clearReplyTarget();
                state.shouldAutoScroll = true;
                return loadMessages(state.selectedConversationId, false, true);
            }).then(function () {
                return loadConversations(false);
            }).catch(function (error) {
                logError('uploadAttachment', error);
                var detail = error && error.message ? error.message : 'Unable to upload attachment.';
                showComposerError(detail);
                showError(detail);
            }).then(function () {
                state.uploading = false;
                updateComposerState();
                focusMessageInput();
            });
            return;
        }

        if (state.selectedIssue) {
            state.sending = true;
            updateComposerState();
            JimApi.sendIssueLink(state.selectedConversationId, state.selectedIssue.key, body || null).then(function () {
                els.messageInput.value = '';
                clearSelectedIssue();
                clearReplyTarget();
                state.shouldAutoScroll = true;
                return loadMessages(state.selectedConversationId, false, true);
            }).then(function () {
                return loadConversations(false);
            }).catch(function (error) {
                logError('sendIssueLink', error);
                var detail = error && error.message ? error.message : 'Unable to share the issue.';
                showComposerError(detail);
                showError(detail);
            }).then(function () {
                state.sending = false;
                updateComposerState();
                focusMessageInput();
            });
            return;
        }

        state.sending = true;
        updateComposerState();

        var replyToMessageId = state.replyToMessage ? state.replyToMessage.id : null;

        JimApi.sendMessage(state.selectedConversationId, body, replyToMessageId).then(function () {
            els.messageInput.value = '';
            clearReplyTarget();
            updateComposerState();
            state.shouldAutoScroll = true;
            return loadMessages(state.selectedConversationId, false, true);
        }).then(function () {
            return loadConversations(false);
        }).catch(function (error) {
            logError('sendMessage', error);
            var detail = error && error.message ? error.message : 'Unable to send message.';
            showComposerError(detail);
            showError(detail);
        }).then(function () {
            state.sending = false;
            updateComposerState();
            focusMessageInput();
        });
    }

    /**
     * Paste handler for the message input. Files (e.g. clipboard image from a
     * screenshot tool) are staged as the pending attachment; plain text falls
     * through to the browser's default behaviour.
     */
    function onComposerPaste(event) {
        if (!composerAcceptsInput()) {
            return;
        }
        var data = event.clipboardData;
        if (!data) {
            return;
        }
        var file = pickFileFromDataTransfer(data);
        if (file) {
            event.preventDefault();
            acceptIncomingFile(file);
        }
        // Otherwise let the browser paste text normally; updateComposerState
        // already fires from the 'input' event after paste completes.
    }

    /**
     * Drag-and-drop on the chat panel: drop a file/image to stage it as the
     * pending attachment; drop plain text to insert it into the input.
     * No state mutation happens until the actual 'drop' event fires.
     */
    function bindComposerDragAndDrop() {
        if (!els.chatPanelShell) {
            return;
        }
        var shell = els.chatPanelShell;
        var dragDepth = 0;

        function hasUsefulPayload(transfer) {
            if (!transfer) {
                return false;
            }
            if (transfer.types) {
                for (var i = 0; i < transfer.types.length; i++) {
                    var type = transfer.types[i];
                    if (type === 'Files' || type === 'text/plain' || type === 'text/uri-list') {
                        return true;
                    }
                }
            }
            return transfer.files && transfer.files.length > 0;
        }

        function clearOverlay() {
            dragDepth = 0;
            shell.classList.remove('jim-chat-drop-active');
        }

        shell.addEventListener('dragenter', function (event) {
            if (!composerAcceptsInput() || !hasUsefulPayload(event.dataTransfer)) {
                return;
            }
            event.preventDefault();
            dragDepth++;
            shell.classList.add('jim-chat-drop-active');
        });

        shell.addEventListener('dragover', function (event) {
            if (!composerAcceptsInput() || !hasUsefulPayload(event.dataTransfer)) {
                return;
            }
            event.preventDefault();
            if (event.dataTransfer) {
                event.dataTransfer.dropEffect = 'copy';
            }
        });

        shell.addEventListener('dragleave', function (event) {
            if (dragDepth === 0) {
                return;
            }
            dragDepth--;
            if (dragDepth <= 0) {
                clearOverlay();
            }
        });

        shell.addEventListener('drop', function (event) {
            if (!composerAcceptsInput()) {
                clearOverlay();
                return;
            }
            if (!hasUsefulPayload(event.dataTransfer)) {
                clearOverlay();
                return;
            }
            event.preventDefault();
            clearOverlay();

            var file = pickFileFromDataTransfer(event.dataTransfer);
            if (file) {
                acceptIncomingFile(file);
                return;
            }
            var text = event.dataTransfer.getData('text/plain')
                || event.dataTransfer.getData('text/uri-list');
            if (text) {
                insertTextIntoComposer(text);
            }
        });

        // If the user drops outside the shell or releases the mouse elsewhere,
        // make sure the overlay state doesn't get stuck.
        window.addEventListener('dragend', clearOverlay);
        window.addEventListener('drop', function (event) {
            if (!shell.contains(event.target)) {
                clearOverlay();
            }
        });
    }

    function composerAcceptsInput() {
        if (!state.selectedConversationId) {
            return false;
        }
        if (state.selectedConversation && isSystemConversation(state.selectedConversation)) {
            return false;
        }
        if (state.licenseBlocked) {
            return false;
        }
        if (els.composer && els.composer.classList.contains('jim-composer-is-readonly')) {
            return false;
        }
        return true;
    }

    function pickFileFromDataTransfer(transfer) {
        if (!transfer) {
            return null;
        }
        if (transfer.files && transfer.files.length) {
            return transfer.files[0];
        }
        if (transfer.items) {
            for (var i = 0; i < transfer.items.length; i++) {
                if (transfer.items[i].kind === 'file') {
                    var f = transfer.items[i].getAsFile();
                    if (f) {
                        return f;
                    }
                }
            }
        }
        return null;
    }

    function insertTextIntoComposer(text) {
        if (!els.messageInput || !text) {
            return;
        }
        var input = els.messageInput;
        var start = typeof input.selectionStart === 'number' ? input.selectionStart : input.value.length;
        var end = typeof input.selectionEnd === 'number' ? input.selectionEnd : input.value.length;
        var before = input.value.substring(0, start);
        var after = input.value.substring(end);
        var needsLeadingSpace = before.length > 0 && !/\s$/.test(before);
        var insertion = (needsLeadingSpace ? ' ' : '') + text;
        input.value = before + insertion + after;
        var caret = before.length + insertion.length;
        try {
            input.setSelectionRange(caret, caret);
        } catch (e) {
            // setSelectionRange is unsupported on some input types; safe to skip.
        }
        updateComposerState();
        focusMessageInput();
    }

    function startPolling() {
        stopPolling();

        state.messagePollTimer = window.setInterval(function () {
            if (document.hidden || !state.selectedConversationId || state.loadingMessages) {
                return;
            }
            loadMessages(state.selectedConversationId, false, false);
        }, MESSAGE_POLL_MS);

        state.conversationPollTimer = window.setInterval(function () {
            if (document.hidden) {
                return;
            }
            loadConversations(false);
        }, CONVERSATION_POLL_MS);
    }

    function stopPolling() {
        if (state.messagePollTimer) {
            window.clearInterval(state.messagePollTimer);
            state.messagePollTimer = null;
        }
        if (state.conversationPollTimer) {
            window.clearInterval(state.conversationPollTimer);
            state.conversationPollTimer = null;
        }
    }

    function bindEvents() {
        if (els.searchInput) {
            els.searchInput.addEventListener('input', onSearchInput);
        }
        if (els.sendButton) {
            els.sendButton.addEventListener('click', sendMessage);
        }
        if (els.attachButton && els.fileInput) {
            els.attachButton.addEventListener('click', function () {
                els.fileInput.click();
            });
            els.fileInput.addEventListener('change', onFileInputChange);
        }
        if (els.selectedAttachmentRemove) {
            els.selectedAttachmentRemove.addEventListener('click', clearSelectedFile);
        }
        if (els.composerReplyCancel) {
            els.composerReplyCancel.addEventListener('click', clearReplyTarget);
        }
        if (els.newConversationButton && els.searchInput) {
            els.newConversationButton.addEventListener('click', function () {
                els.searchInput.focus();
            });
        }
        for (var t = 0; t < els.sidebarTabs.length; t++) {
            els.sidebarTabs[t].addEventListener('click', function (event) {
                setActiveTab(event.currentTarget.getAttribute('data-tab'));
            });
        }
        if (els.newGroupButton) {
            els.newGroupButton.addEventListener('click', openGroupModal);
        }
        if (els.messageInfoModal) {
            els.messageInfoModal.addEventListener('click', function (event) {
                if (event.target === els.messageInfoModal) {
                    closeMessageInfo();
                }
            });
            els.messageInfoClose.addEventListener('click', closeMessageInfo);
        }
        if (els.imageLightbox) {
            if (els.messageList) {
                els.messageList.addEventListener('click', function (event) {
                    var link = event.target && event.target.closest
                        ? event.target.closest('.jim-image-preview-link')
                        : null;
                    if (link) {
                        event.preventDefault();
                        openImageLightbox(link);
                    }
                });
            }
            els.imageLightbox.addEventListener('click', function (event) {
                if (event.target === els.imageLightbox) {
                    closeImageLightbox();
                }
            });
            els.imageLightboxClose.addEventListener('click', closeImageLightbox);
            document.addEventListener('keydown', function (event) {
                if (event.key === 'Escape') {
                    closeImageLightbox();
                }
            });
        }
        if (els.groupModal) {
            els.groupModal.addEventListener('click', function (event) {
                if (event.target === els.groupModal) {
                    closeGroupModal();
                }
            });
            els.groupCancel.addEventListener('click', closeGroupModal);
            els.groupCreate.addEventListener('click', submitCreateGroup);
            els.groupName.addEventListener('input', updateGroupCreateButton);
            els.groupName.addEventListener('keydown', function (event) {
                if (event.key === 'Enter') {
                    event.preventDefault();
                    submitCreateGroup();
                }
            });
            els.groupMemberSearch.addEventListener('input', function () {
                var query = els.groupMemberSearch.value.trim();
                if (state.groupSearchTimer) {
                    window.clearTimeout(state.groupSearchTimer);
                }
                if (query.length < 2) {
                    els.groupMemberResults.innerHTML = '';
                    setHidden(els.groupMemberResults, true);
                    return;
                }
                state.groupSearchTimer = window.setTimeout(function () {
                    JimApi.searchUsers(query).then(function (response) {
                        renderGroupMemberResults(response.users || []);
                    }).catch(function (error) {
                        logError('groupMemberSearch', error);
                    });
                }, 250);
            });
        }
        if (els.pinnedBanner) {
            els.pinnedBanner.addEventListener('click', function (event) {
                var jump = event.target.closest('[data-pinned-target]');
                if (jump) {
                    scrollToReplyTarget(parseInt(jump.getAttribute('data-pinned-target'), 10));
                    return;
                }
                var unpin = event.target.closest('[data-pinned-unpin]');
                if (unpin) {
                    JimApi.unpinMessage(parseInt(unpin.getAttribute('data-pinned-unpin'), 10)).then(function () {
                        state.pinnedMessage = null;
                        renderPinnedBanner();
                        return loadMessages(state.selectedConversationId, false, false);
                    }).catch(function (error) {
                        logError('unpinMessage', error);
                        showError(error && error.message ? error.message : 'Unable to unpin message.');
                    });
                }
            });
        }
        if (els.emojiButton && els.emojiPalette && els.messageInput) {
            els.emojiButton.addEventListener('click', function (event) {
                event.stopPropagation();
                toggleEmojiPalette(false);
            });
            els.emojiPalette.addEventListener('click', function (event) {
                var option = event.target.closest('.jim-emoji-option');
                if (!option) {
                    return;
                }
                insertAtCursor(els.messageInput, option.getAttribute('data-emoji') || '');
                toggleEmojiPalette(true);
                updateComposerState();
            });
        }
        if (els.mentionButton && els.mentionPalette && els.messageInput) {
            els.mentionButton.addEventListener('click', function (event) {
                event.stopPropagation();
                toggleMentionPalette(false);
            });
            els.mentionPalette.addEventListener('click', function (event) {
                var option = event.target.closest('.jim-mention-option');
                if (!option) {
                    return;
                }
                insertAtCursor(els.messageInput, '@' + (option.getAttribute('data-mention-name') || '') + ' ');
                toggleMentionPalette(true);
                updateComposerState();
            });
        }
        if (els.issueButton && els.issuePalette) {
            els.issueButton.addEventListener('click', function (event) {
                event.stopPropagation();
                toggleIssuePalette(false);
            });
            if (els.issueSearchInput) {
                els.issueSearchInput.addEventListener('input', function () {
                    var query = els.issueSearchInput.value;
                    if (state.issueSearchTimer) {
                        window.clearTimeout(state.issueSearchTimer);
                    }
                    state.issueSearchTimer = window.setTimeout(function () {
                        runIssueSearch(query);
                    }, 300);
                });
            }
            if (els.issueSearchResults) {
                els.issueSearchResults.addEventListener('click', function (event) {
                    var option = event.target.closest('.jim-issue-option');
                    if (!option) {
                        return;
                    }
                    setSelectedIssue({
                        key: option.getAttribute('data-issue-key'),
                        summary: option.getAttribute('data-issue-summary') || ''
                    });
                    toggleIssuePalette(true);
                    if (els.messageInput) {
                        els.messageInput.focus();
                    }
                });
            }
            if (els.selectedIssueRemove) {
                els.selectedIssueRemove.addEventListener('click', clearSelectedIssue);
            }
        }
        if (els.voiceButton) {
            els.voiceButton.addEventListener('click', startVoiceRecording);
        }
        if (els.voiceCancel) {
            els.voiceCancel.addEventListener('click', cancelVoiceRecording);
        }
        if (els.voiceSend) {
            els.voiceSend.addEventListener('click', finishVoiceRecording);
        }
        if (els.messageInput) {
            els.messageInput.addEventListener('input', updateComposerState);
            els.messageInput.addEventListener('keydown', function (event) {
                if (event.key === 'Enter' && !event.shiftKey) {
                    event.preventDefault();
                    sendMessage();
                }
            });
            els.messageInput.addEventListener('paste', onComposerPaste);
        }

        bindComposerDragAndDrop();

        document.addEventListener('click', function (event) {
            if (els.searchResults && els.searchInput
                    && !els.searchResults.contains(event.target) && event.target !== els.searchInput) {
                setHidden(els.searchResults, true);
            }
            if (els.emojiPalette && !els.emojiPalette.hidden
                    && !els.emojiPalette.contains(event.target) && event.target !== els.emojiButton) {
                toggleEmojiPalette(true);
            }
            if (els.mentionPalette && !els.mentionPalette.hidden
                    && !els.mentionPalette.contains(event.target) && event.target !== els.mentionButton) {
                toggleMentionPalette(true);
            }
            if (els.issuePalette && !els.issuePalette.hidden
                    && !els.issuePalette.contains(event.target) && event.target !== els.issueButton) {
                toggleIssuePalette(true);
            }
        });
    }

    // ===== Web Push notifications =====

    function pushSupported() {
        return window.isSecureContext !== false
            && 'serviceWorker' in navigator
            && 'PushManager' in window
            && typeof window.Notification !== 'undefined';
    }

    function urlBase64ToUint8Array(base64String) {
        var padding = new Array((4 - (base64String.length % 4)) % 4 + 1).join('=');
        var base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
        var rawData = window.atob(base64);
        var outputArray = new Uint8Array(rawData.length);
        for (var i = 0; i < rawData.length; i++) {
            outputArray[i] = rawData.charCodeAt(i);
        }
        return outputArray;
    }

    function subscribeForPush() {
        return navigator.serviceWorker.register(
                contextPath() + '/plugins/servlet/jim/sw.js',
                { updateViaCache: 'none' })
            .then(function (registration) {
                // Force an update check so a redeployed service worker
                // replaces the old one immediately.
                try {
                    registration.update();
                } catch (updateError) {
                    // Best effort; an old SW will still update eventually.
                }
                return navigator.serviceWorker.ready;
            })
            .then(function (registration) {
                return registration.pushManager.getSubscription().then(function (existing) {
                    if (existing) {
                        return existing;
                    }
                    return JimApi.getPushConfig().then(function (config) {
                        return registration.pushManager.subscribe({
                            userVisibleOnly: true,
                            applicationServerKey: urlBase64ToUint8Array(config.publicKey)
                        });
                    });
                });
            })
            .then(function (subscription) {
                var payload = subscription && subscription.toJSON ? subscription.toJSON() : subscription;
                return JimApi.savePushSubscription(payload);
            });
    }

    var PUSH_DISABLED_KEY = 'jim-push-disabled';

    function pushDisabledByUser() {
        try {
            return window.localStorage.getItem(PUSH_DISABLED_KEY) === '1';
        } catch (storageError) {
            return false;
        }
    }

    function setPushDisabled(disabled) {
        try {
            if (disabled) {
                window.localStorage.setItem(PUSH_DISABLED_KEY, '1');
            } else {
                window.localStorage.removeItem(PUSH_DISABLED_KEY);
            }
        } catch (storageError) {
            // localStorage unavailable; the flag is best effort.
        }
    }

    function pushEnabled() {
        return window.Notification.permission === 'granted' && !pushDisabledByUser();
    }

    function unsubscribeFromPush() {
        return navigator.serviceWorker.getRegistration(contextPath() + '/plugins/servlet/jim/sw.js')
            .then(function (registration) {
                return registration ? registration.pushManager.getSubscription() : null;
            })
            .then(function (subscription) {
                if (!subscription) {
                    return null;
                }
                var endpoint = subscription.endpoint;
                return subscription.unsubscribe().then(function () {
                    return JimApi.deletePushSubscription(endpoint);
                });
            });
    }

    function refreshPushButton(button) {
        if (window.Notification.permission === 'denied') {
            button.hidden = true;
            return;
        }
        button.hidden = false;
        var enabled = pushEnabled();
        button.innerHTML = enabled ? '&#128277;' : '&#128276;';
        var label = enabled ? 'Disable notifications' : 'Enable notifications';
        button.title = label;
        button.setAttribute('aria-label', label);
    }

    function onPushButtonClick(button) {
        if (pushEnabled()) {
            setPushDisabled(true);
            unsubscribeFromPush().catch(function (error) {
                logError('push.unsubscribe', error);
            });
            refreshPushButton(button);
            return;
        }
        setPushDisabled(false);
        window.Notification.requestPermission().then(function (permission) {
            refreshPushButton(button);
            if (permission !== 'granted') {
                return;
            }
            subscribeForPush().catch(function (error) {
                logError('push.subscribe', error);
            });
        });
    }

    function setupPushNotifications() {
        if (!pushSupported()) {
            return;
        }
        if (pushEnabled()) {
            // Keep the subscription fresh on every chat visit.
            subscribeForPush().catch(function (error) {
                logError('push.subscribe', error);
            });
        }
        var button = document.getElementById('jim-push-enable');
        if (!button || state.projectMode) {
            return;
        }
        refreshPushButton(button);
        button.addEventListener('click', function () {
            onPushButtonClick(button);
        });
    }

    // ===== Marketplace license =====

    function showLicenseBanner(message, blocking) {
        if (!els.app || document.getElementById('jim-license-banner')) {
            return;
        }
        var banner = document.createElement('div');
        banner.id = 'jim-license-banner';
        banner.className = 'jim-license-banner' + (blocking ? ' jim-license-banner-blocking' : '');
        banner.setAttribute('role', 'alert');
        var icon = document.createElement('span');
        icon.className = 'jim-license-banner-icon';
        icon.setAttribute('aria-hidden', 'true');
        icon.textContent = '\u26A0';
        var text = document.createElement('span');
        text.className = 'jim-license-banner-text';
        text.textContent = message;
        banner.appendChild(icon);
        banner.appendChild(text);
        // The app shell is a flex row (sidebar | main), so the banner lives at
        // the top of the main chat column.
        var main = document.getElementById('jim-chat-panel-shell')
            || els.app.querySelector('.jim-main')
            || els.app;
        main.insertBefore(banner, main.firstChild);
    }

    function applyLicenseLock() {
        state.licenseBlocked = true;
        if (els.composerReadonly) {
            els.composerReadonly.textContent = 'Messaging is disabled \u2014 the CorbitChat license is missing or expired.';
        }
        if (els.searchInput) {
            els.searchInput.disabled = true;
            els.searchInput.placeholder = 'License required';
        }
        if (els.newConversationButton) {
            els.newConversationButton.disabled = true;
        }
        updateComposerState();
    }

    function checkLicense() {
        if (!window.JimApi || typeof window.JimApi.getLicenseStatus !== 'function') {
            return;
        }
        window.JimApi.getLicenseStatus().then(function (status) {
            if (status && status.licensed === false) {
                showLicenseBanner('CorbitChat license is missing or expired. Messaging is disabled until a valid license is installed via Manage apps. Existing conversations stay readable.', true);
                applyLicenseLock();
            }
        }).catch(function () {
            // Never break the chat UI because the license endpoint failed.
            showLicenseBanner('CorbitChat could not verify its license. If problems persist, contact your Jira administrator.', false);
        });
    }

    function init() {
        cacheElements();
        if (!els.app) {
            logError('init', new Error('Messenger root element #jim-messenger-app was not found.'));
            return;
        }

        if (!els.conversationList || !els.messageList) {
            logError('init', new Error('Messenger layout is incomplete. Try a hard refresh (Ctrl+Shift+R).'));
            if (els.conversationList) {
                renderErrorState(els.conversationList, 'Chat UI failed to load. Please refresh the page.', function () {
                    window.location.reload();
                });
            }
            return;
        }

        state.currentUserKey = els.app.getAttribute('data-user-key') || null;
        state.projectKey = els.app.getAttribute('data-project-key') || null;
        state.projectMode = !!state.projectKey;
        state.projectLeadName = els.app.getAttribute('data-project-lead-name') || null;

        bindEvents();
        ensureMessageActionsBound();
        updateComposerState();
        checkLicense();
        setupPushNotifications();
        if (!state.projectMode) {
            restoreActiveTab();
        }
        loadConversations(true).then(startPolling);
    }

    onReady(init);
})();
