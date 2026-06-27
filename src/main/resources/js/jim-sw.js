/**
 * CorbitChat service worker.
 *
 * Payload-first push handler. Every server-sent push is expected to carry
 * a JSON payload describing the notification (title, body, url, type,
 * conversationId, messageId, ...). The SW never fetches the REST API
 * during a push event - older builds did, which produced cross-origin
 * (origin: null) failures in Chrome when the SW happened to be in an
 * opaque-origin context. If a push arrives without a payload (e.g. a
 * manual DevTools "Push" trigger with no payload), the SW shows a
 * generic fallback notification instead of contacting the server.
 *
 * Clicking the notification focuses an existing Jira tab or opens the
 * chat page; the click handler does not depend on any REST call either.
 */
(function () {
    'use strict';

    var SERVLET_MARKER = '/plugins/servlet/jim/sw.js';
    var swPath = self.location.pathname;
    var contextPath = swPath.slice(0, swPath.length - SERVLET_MARKER.length);
    // Absolute URLs bound to the SW's own origin so they're unambiguous.
    var chatUrl = new URL(contextPath + '/plugins/servlet/jim/chat', self.location.origin).href;

    var FALLBACK_TITLE = 'CorbitChat';
    var FALLBACK_BODY = 'You have a new message';
    var DEFAULT_TAG = 'jim-unread';

    self.addEventListener('install', function () {
        self.skipWaiting();
    });

    self.addEventListener('activate', function (event) {
        try {
            console.log('[CorbitChat SW] activate origin=', self.location.origin,
                ' href=', self.location.href, ' chatUrl=', chatUrl);
        } catch (logError) {
            // best effort
        }
        event.waitUntil(self.clients.claim());
    });

    /**
     * Resolves the notification target URL from a payload field. We accept
     * absolute or relative URLs but always render them on the SW's own
     * origin via new URL() so they can never escape the Jira host.
     */
    function resolveNotificationUrl(rawUrl) {
        if (!rawUrl) {
            return chatUrl;
        }
        try {
            return new URL(String(rawUrl), self.location.origin).href;
        } catch (e) {
            return chatUrl;
        }
    }

    self.addEventListener('push', function (event) {
        var payload = null;
        if (event.data) {
            try {
                payload = event.data.json();
            } catch (parseError) {
                payload = null;
            }
        }

        var title;
        var options;
        if (payload && typeof payload === 'object' && payload.title) {
            // Payload path: use what the server told us. Notification data
            // carries everything the click handler might need.
            title = String(payload.title);
            options = {
                body: payload.body ? String(payload.body) : '',
                tag: payload.tag ? String(payload.tag) : DEFAULT_TAG,
                renotify: true,
                requireInteraction: true,
                data: {
                    url: resolveNotificationUrl(payload.url),
                    type: payload.type || null,
                    conversationId: payload.conversationId || null,
                    messageId: payload.messageId || null
                }
            };
        } else {
            // No payload (e.g. DevTools manual Push, or a vendor-side
            // server fallback): show a generic notification. The SW does
            // NOT contact the server here.
            try {
                console.log('[CorbitChat SW] push without payload - showing generic fallback');
            } catch (logError) {
                // best effort
            }
            title = FALLBACK_TITLE;
            options = {
                body: FALLBACK_BODY,
                tag: DEFAULT_TAG,
                renotify: true,
                requireInteraction: false,
                data: { url: chatUrl, type: null, conversationId: null, messageId: null }
            };
        }

        event.waitUntil(self.registration.showNotification(title, options));
    });

    self.addEventListener('notificationclick', function (event) {
        event.notification.close();
        var targetUrl = (event.notification.data && event.notification.data.url) || chatUrl;
        event.waitUntil(
            self.clients.matchAll({ type: 'window', includeUncontrolled: true })
                .then(function (clientList) {
                    for (var i = 0; i < clientList.length; i++) {
                        var client = clientList[i];
                        if (client.url.indexOf(contextPath + '/plugins/servlet/jim/') !== -1
                                && 'focus' in client) {
                            return client.focus();
                        }
                    }
                    if (self.clients.openWindow) {
                        return self.clients.openWindow(targetUrl);
                    }
                    return undefined;
                })
        );
    });
})();
