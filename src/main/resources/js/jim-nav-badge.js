/**
 * CorbitChat top-navigation unread badge.
 *
 * Loaded on all Jira pages (atl.general context). Polls a lightweight
 * unread-count endpoint and renders a red badge on the "Chat" item in
 * the Jira top navigation bar (anchor id "jim-messenger").
 */
(function () {
    'use strict';

    var POLL_INTERVAL_MS = 15000;
    var BADGE_ID = 'jim-nav-unread-badge';

    function findNavLink() {
        return document.getElementById('jim-messenger');
    }

    function contextPath() {
        if (window.AJS && typeof window.AJS.contextPath === 'function') {
            return window.AJS.contextPath();
        }
        var meta = document.querySelector('meta[name="ajs-context-path"]');
        return meta ? (meta.getAttribute('content') || '') : '';
    }

    function ensureBadge(link) {
        var badge = document.getElementById(BADGE_ID);
        if (!badge) {
            badge = document.createElement('span');
            badge.id = BADGE_ID;
            badge.className = 'jim-nav-unread-badge';
            badge.setAttribute('hidden', 'hidden');
            link.appendChild(badge);
        }
        return badge;
    }

    function applyCount(count) {
        var link = findNavLink();
        if (!link) {
            return;
        }
        var badge = ensureBadge(link);
        if (count > 0) {
            badge.textContent = count > 99 ? '99+' : String(count);
            badge.removeAttribute('hidden');
        } else {
            badge.setAttribute('hidden', 'hidden');
        }
    }

    // ===== Desktop notifications while a Jira tab is open =====

    var LAST_NOTIFIED_KEY = 'jim-last-notified-count';
    var LAST_NOTIFIED_AT_KEY = 'jim-last-notified-at';
    var NOTIFY_COOLDOWN_MS = 30000;
    var lastSeenCount = null;

    function chatUrl() {
        return contextPath() + '/plugins/servlet/jim/chat';
    }

    function onChatPage() {
        return window.location.pathname.indexOf('/plugins/servlet/jim/') !== -1;
    }

    function pushDisabledByUser() {
        try {
            return window.localStorage.getItem('jim-push-disabled') === '1';
        } catch (storageError) {
            return false;
        }
    }

    function showUnreadNotification(count) {
        var body = count === 1 ? 'You have 1 unread message' : 'You have ' + count + ' unread messages';
        var options = { body: body, tag: 'jim-unread', renotify: true, data: { url: chatUrl() } };
        if (navigator.serviceWorker && navigator.serviceWorker.getRegistration) {
            navigator.serviceWorker.getRegistration(contextPath() + '/plugins/servlet/jim/sw.js')
                .then(function (registration) {
                    if (registration) {
                        // SW path works on Android too; click handling lives in the SW.
                        return registration.showNotification('CorbitChat', options);
                    }
                    showPageNotification(body);
                    return undefined;
                })
                .catch(function () {
                    showPageNotification(body);
                });
        } else {
            showPageNotification(body);
        }
    }

    function showPageNotification(body) {
        try {
            var notification = new Notification('CorbitChat', { body: body, tag: 'jim-unread', renotify: true });
            notification.onclick = function () {
                window.focus();
                window.location.href = chatUrl();
                notification.close();
            };
        } catch (error) {
            // Notification constructor is unavailable (e.g. Android Chrome); skip.
        }
    }

    function maybeNotify(count) {
        if (typeof window.Notification === 'undefined'
                || window.Notification.permission !== 'granted'
                || pushDisabledByUser()
                || onChatPage()
                || count <= 0) {
            lastSeenCount = count;
            return;
        }
        if (lastSeenCount === null) {
            // First poll after page load: don't notify about pre-existing unread.
            lastSeenCount = count;
            return;
        }
        if (count <= lastSeenCount) {
            lastSeenCount = count;
            return;
        }
        lastSeenCount = count;

        // Cross-tab dedupe: only one Jira tab should fire the notification.
        try {
            var now = Date.now();
            var lastAt = Number(window.localStorage.getItem(LAST_NOTIFIED_AT_KEY)) || 0;
            var lastCount = Number(window.localStorage.getItem(LAST_NOTIFIED_KEY)) || 0;
            if (now - lastAt < NOTIFY_COOLDOWN_MS && count <= lastCount) {
                return;
            }
            window.localStorage.setItem(LAST_NOTIFIED_AT_KEY, String(now));
            window.localStorage.setItem(LAST_NOTIFIED_KEY, String(count));
        } catch (storageError) {
            // localStorage unavailable; notify anyway.
        }
        skipIfPushSubscribed(function () {
            showUnreadNotification(count);
        });
    }

    /**
     * When this browser has an active Web Push subscription, the push channel
     * already delivers richer per-message notifications; the generic polled
     * notification would be a duplicate.
     */
    function skipIfPushSubscribed(notifyCallback) {
        if (!navigator.serviceWorker || !navigator.serviceWorker.getRegistration) {
            notifyCallback();
            return;
        }
        navigator.serviceWorker.getRegistration(contextPath() + '/plugins/servlet/jim/sw.js')
            .then(function (registration) {
                if (!registration || !registration.pushManager) {
                    return null;
                }
                return registration.pushManager.getSubscription();
            })
            .then(function (subscription) {
                if (!subscription) {
                    notifyCallback();
                }
            })
            .catch(function () {
                notifyCallback();
            });
    }

    function refresh() {
        if (!findNavLink()) {
            return;
        }
        var xhr = new XMLHttpRequest();
        xhr.open('GET', contextPath() + '/rest/jim/1.0/conversations/unread-count', true);
        xhr.setRequestHeader('Accept', 'application/json');
        xhr.onload = function () {
            if (xhr.status !== 200) {
                return;
            }
            try {
                var data = JSON.parse(xhr.responseText);
                var count = Number(data.unreadCount) || 0;
                applyCount(count);
                maybeNotify(count);
            } catch (parseError) {
                // ignore malformed responses; badge keeps its last state
            }
        };
        xhr.send();
    }

    function start() {
        if (!findNavLink()) {
            return;
        }
        refresh();
        window.setInterval(refresh, POLL_INTERVAL_MS);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', start);
    } else {
        start();
    }
})();
