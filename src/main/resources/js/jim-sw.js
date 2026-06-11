/**
 * CorbitChat service worker.
 *
 * Receives payload-less Web Push messages, fetches the unread summary from
 * the REST API (session cookie is sent automatically for same-origin
 * requests) and shows an OS notification. Clicking the notification focuses
 * an existing Jira tab or opens the chat page.
 */
(function () {
    'use strict';

    // The SW is served from <context>/plugins/servlet/jim/sw.js
    var SERVLET_MARKER = '/plugins/servlet/jim/sw.js';
    var swPath = self.location.pathname;
    var contextPath = swPath.slice(0, swPath.length - SERVLET_MARKER.length);
    var summaryUrl = contextPath + '/rest/jim/1.0/push/summary';
    var chatUrl = contextPath + '/plugins/servlet/jim/chat';

    self.addEventListener('install', function () {
        self.skipWaiting();
    });

    self.addEventListener('activate', function (event) {
        event.waitUntil(self.clients.claim());
    });

    self.addEventListener('push', function (event) {
        // Encrypted payload pushes carry the notification content directly:
        // show it immediately without any network round-trip.
        var payload = null;
        if (event.data) {
            try {
                payload = event.data.json();
            } catch (parseError) {
                payload = null;
            }
        }
        if (payload && payload.title) {
            event.waitUntil(self.registration.showNotification(payload.title, {
                body: payload.body || '',
                tag: payload.tag || 'jim-unread',
                renotify: true,
                requireInteraction: true,
                data: { url: payload.url || chatUrl }
            }));
            return;
        }

        event.waitUntil(
            fetch(summaryUrl, {
                credentials: 'same-origin',
                headers: { 'Accept': 'application/json' }
            })
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error('summary failed: ' + response.status);
                    }
                    return response.json();
                })
                .then(function (summary) {
                    if (!summary || Number(summary.unreadCount) === 0) {
                        // Already read in another session; stay quiet.
                        return undefined;
                    }
                    return self.registration.showNotification(summary.title || 'CorbitChat', {
                        body: summary.body || 'You have new messages',
                        tag: 'jim-unread',
                        renotify: true,
                        data: { url: chatUrl }
                    });
                })
                .catch(function () {
                    return self.registration.showNotification('CorbitChat', {
                        body: 'You have new messages',
                        tag: 'jim-unread',
                        renotify: true,
                        data: { url: chatUrl }
                    });
                })
        );
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
