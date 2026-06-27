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
    // Build absolute URLs against the SW's own origin so the fetch is
    // unambiguously same-origin. Using new URL() (rather than string
    // concatenation) makes the resolved origin explicit and removes any
    // chance that a stale SW computes a stringy path that resolves
    // somewhere unexpected.
    var summaryUrl = new URL(contextPath + '/rest/jim/1.0/push/summary', self.location.origin).href;
    var chatUrl = new URL(contextPath + '/plugins/servlet/jim/chat', self.location.origin).href;

    self.addEventListener('install', function () {
        self.skipWaiting();
    });

    self.addEventListener('activate', function (event) {
        // One-shot diagnostic line so origin / URL issues are visible in
        // Chrome DevTools -> Application -> Service Workers -> Console.
        try {
            console.log('[CorbitChat SW] activate origin=', self.location.origin,
                ' href=', self.location.href, ' summaryUrl=', summaryUrl);
        } catch (logError) {
            // best effort
        }
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
            // Explicit same-origin Request so any URL-resolution mistake
            // surfaces as an error here instead of being silently treated
            // as cross-origin by the browser. The credentials field still
            // sends the Jira session cookie automatically.
            fetch(new Request(summaryUrl, {
                method: 'GET',
                credentials: 'same-origin',
                mode: 'same-origin',
                cache: 'no-cache',
                headers: { 'Accept': 'application/json' }
            }))
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
                .catch(function (fetchError) {
                    try {
                        console.warn('[CorbitChat SW] /push/summary fetch failed: ',
                            fetchError && fetchError.message,
                            ' swOrigin=', self.location.origin,
                            ' url=', summaryUrl);
                    } catch (logError) {
                        // best effort
                    }
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
