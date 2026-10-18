(function (global) {
    'use strict';

    function apiBase() {
        return (global.AJS && global.AJS.contextPath ? global.AJS.contextPath() : '') + '/rest/jim/1.0';
    }

    function request(method, path, body) {
        var options = {
            method: method,
            credentials: 'same-origin',
            headers: {
                'Accept': 'application/json',
                'X-Atlassian-Token': 'no-check'
            }
        };

        if (body !== undefined) {
            options.headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(body);
        }

        return fetch(apiBase() + path, options).then(function (response) {
            if (response.status === 204) {
                return null;
            }

            return response.text().then(function (text) {
                var payload = null;
                if (text) {
                    try {
                        payload = JSON.parse(text);
                    } catch (parseError) {
                        var htmlError = new Error('The server returned an unexpected response. Please refresh and try again.');
                        htmlError.status = response.status;
                        htmlError.nonJson = true;
                        throw htmlError;
                    }
                }

                if (!response.ok) {
                    var message = 'Request failed';
                    if (response.status === 401 || response.status === 403) {
                        message = 'You do not have permission to perform this action.';
                    } else if (payload) {
                        if (payload.message) {
                            message = payload.message;
                        } else if (payload.error) {
                            message = payload.error;
                        }
                        if (payload.debugExceptionMessage) {
                            message += ' Backend error: ' + payload.debugExceptionMessage;
                        }
                    }
                    var error = new Error(message);
                    error.status = response.status;
                    error.payload = payload;
                    throw error;
                }
                return payload;
            });
        });
    }

    global.JimApi = {
        getLicenseStatus: function () {
            return request('GET', '/license/status');
        },

        listConversations: function () {
            return request('GET', '/conversations');
        },

        createDirectConversation: function (targetUserKey) {
            return request('POST', '/conversations/direct', { targetUserKey: targetUserKey });
        },

        listSharedContent: function (conversationId, type, cursor, limit) {
            var query = '?type=' + encodeURIComponent(type || 'files') +
                '&limit=' + encodeURIComponent(limit || 25);
            if (cursor) {
                query += '&cursor=' + encodeURIComponent(cursor);
            }
            return request('GET', '/conversations/' + conversationId + '/shared' + query);
        },

        searchMessages: function (query, conversationId, limit, cursor, type) {
            var q = '?q=' + encodeURIComponent(query || '');
            if (conversationId) { q += '&conversationId=' + encodeURIComponent(conversationId); }
            if (type) { q += '&type=' + encodeURIComponent(type); }
            if (limit) { q += '&limit=' + encodeURIComponent(limit); }
            if (cursor) { q += '&cursor=' + encodeURIComponent(cursor); }
            return request('GET', '/conversations/search' + q);
        },

        messagesAround: function (conversationId, messageId, before, after) {
            var q = '?before=' + encodeURIComponent(before || 25) +
                '&after=' + encodeURIComponent(after || 25);
            return request('GET', '/conversations/' + conversationId + '/messages/around/' + messageId + q);
        },

        listMessages: function (conversationId, limit, beforeMessageId) {
            var query = '?limit=' + encodeURIComponent(limit || 50);
            if (beforeMessageId) {
                query += '&beforeMessageId=' + encodeURIComponent(beforeMessageId);
            }
            return request('GET', '/conversations/' + conversationId + '/messages' + query);
        },

        sendMessage: function (conversationId, body, replyToMessageId) {
            var payload = { body: body };
            if (replyToMessageId) {
                payload.replyToMessageId = replyToMessageId;
            }
            return request('POST', '/conversations/' + conversationId + '/messages', payload);
        },

        sendIssueLink: function (conversationId, issueKey, body) {
            var payload = { issueKey: issueKey };
            if (body) {
                payload.body = body;
            }
            return request('POST', '/conversations/' + conversationId + '/messages', payload);
        },

        searchIssues: function (query) {
            var base = global.AJS && global.AJS.contextPath ? global.AJS.contextPath() : '';
            return fetch(base + '/rest/api/2/issue/picker?showSubTasks=true&currentJQL=&query=' + encodeURIComponent(query), {
                method: 'GET',
                credentials: 'same-origin',
                headers: { 'Accept': 'application/json' }
            }).then(function (response) {
                if (!response.ok) {
                    throw new Error('Issue search failed (' + response.status + ')');
                }
                return response.json();
            });
        },

        editMessage: function (messageId, body) {
            return request('PUT', '/messages/' + messageId, { body: body });
        },

        deleteMessage: function (messageId) {
            return request('DELETE', '/messages/' + messageId);
        },

        // Sprint 12: "Delete for me" — hides a message for the current user only
        // (never hard-deletes; stays visible to others). Single + bulk.
        hideMessage: function (messageId) {
            return request('POST', '/messages/' + messageId + '/hide');
        },

        hideMessages: function (messageIds) {
            return request('POST', '/messages/hide', { ids: messageIds });
        },

        // Sprint 12 polish: ephemeral typing/recording/uploading activity (TTL).
        postActivity: function (conversationId, activity, clear) {
            return request('POST', '/conversations/' + conversationId + '/activity',
                clear ? { clear: true } : { activity: activity || 'typing' });
        },

        toggleReaction: function (messageId, emoji) {
            return request('POST', '/messages/' + messageId + '/reactions', { emoji: emoji });
        },

        pinMessage: function (messageId) {
            return request('POST', '/messages/' + messageId + '/pin');
        },

        unpinMessage: function (messageId) {
            return request('DELETE', '/messages/' + messageId + '/pin');
        },

        markMessageActioned: function (messageId) {
            return request('POST', '/messages/' + messageId + '/action');
        },

        // Web chat localization preferences (Persian UI / Jalali dates).
        getWebPreferences: function () {
            return request('GET', '/web-preferences');
        },
        saveWebPreferences: function (language, calendar) {
            return request('PUT', '/web-preferences', { language: language, calendar: calendar });
        },

        // Schedule-approval decision (approve/reject). Reason required on reject.
        scheduleApprovalDecision: function (messageId, action, reason) {
            return request('POST', '/messages/' + messageId + '/schedule-approval',
                { action: action, reason: reason || '' });
        },

        getPinnedMessage: function (conversationId) {
            return request('GET', '/conversations/' + conversationId + '/pinned');
        },

        createGroup: function (name, memberUserKeys) {
            return request('POST', '/groups', { name: name, memberUserKeys: memberUserKeys || [] });
        },

        deleteGroup: function (conversationId) {
            return request('DELETE', '/groups/' + conversationId);
        },

        listGroupMembers: function (conversationId) {
            return request('GET', '/groups/' + conversationId + '/members');
        },

        getProjectConversation: function (projectKey) {
            return request('GET', '/projects/' + encodeURIComponent(projectKey) + '/conversation');
        },

        getMessageReceipts: function (conversationId, messageId) {
            return request('GET', '/conversations/' + conversationId + '/messages/' + messageId + '/receipts');
        },

        getPushConfig: function () {
            return request('GET', '/push/config');
        },

        savePushSubscription: function (subscription) {
            return request('POST', '/push/subscriptions', subscription);
        },

        deletePushSubscription: function (endpoint) {
            return request('DELETE', '/push/subscriptions?endpoint=' + encodeURIComponent(endpoint));
        },

        addGroupMember: function (conversationId, userKey) {
            return request('POST', '/groups/' + conversationId + '/members', { userKey: userKey });
        },

        removeGroupMember: function (conversationId, userKey) {
            return request('DELETE', '/groups/' + conversationId + '/members/' + encodeURIComponent(userKey));
        },

        // Phase 1 (group admin & avatars): promote/demote a member (owner only).
        setMemberRole: function (conversationId, userKey, role) {
            return request('POST', '/groups/' + conversationId + '/members/' + encodeURIComponent(userKey) + '/role', { role: role });
        },

        // Rename a group/project chat (owner/admin).
        renameGroup: function (conversationId, name) {
            return request('POST', '/groups/' + conversationId + '/rename', { name: name });
        },

        // Phase 8C: set the group send policy (owner/admin). Returns the updated conversation.
        setGroupSendPolicy: function (conversationId, sendPolicy) {
            return request('POST', '/groups/' + conversationId + '/send-policy', { sendPolicy: sendPolicy });
        },

        // Phase 8C: allow/disallow a non-admin member to send (owner/admin). Returns { members }.
        setGroupAllowedSender: function (conversationId, userKey, allowed) {
            return request('POST', '/groups/' + conversationId + '/allowed-senders', { userKey: userKey, allowed: !!allowed });
        },

        // Reset (remove) the group/project custom avatar (owner/admin).
        deleteConversationAvatar: function (conversationId) {
            return request('DELETE', '/conversations/' + conversationId + '/avatar');
        },

        // Upload a group/project custom avatar (owner/admin). Multipart, mirrors
        // uploadAttachment: no JSON Content-Type so the browser sets the boundary.
        uploadConversationAvatar: function (conversationId, file) {
            var formData = new FormData();
            formData.append('file', file);

            return fetch(apiBase() + '/conversations/' + conversationId + '/avatar', {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Accept': 'application/json',
                    'X-Atlassian-Token': 'no-check'
                },
                body: formData
            }).then(function (response) {
                return response.text().then(function (text) {
                    var payload = null;
                    if (text) {
                        try {
                            payload = JSON.parse(text);
                        } catch (parseError) {
                            var htmlError = new Error('The server returned an unexpected response. Please refresh and try again.');
                            htmlError.status = response.status;
                            htmlError.nonJson = true;
                            throw htmlError;
                        }
                    }

                    if (!response.ok) {
                        var message = 'Upload failed';
                        if (response.status === 401 || response.status === 403) {
                            message = 'You do not have permission to change this photo.';
                        } else if (payload) {
                            if (payload.message) {
                                message = payload.message;
                            } else if (payload.error) {
                                message = payload.error;
                            }
                        }
                        var error = new Error(message);
                        error.status = response.status;
                        error.payload = payload;
                        throw error;
                    }
                    return payload;
                });
            });
        },

        uploadAttachment: function (conversationId, file, body) {
            var formData = new FormData();
            formData.append('file', file);
            if (body) {
                formData.append('body', body);
            }

            return fetch(apiBase() + '/conversations/' + conversationId + '/attachments', {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Accept': 'application/json',
                    'X-Atlassian-Token': 'no-check'
                },
                body: formData
            }).then(function (response) {
                return response.text().then(function (text) {
                    var payload = null;
                    if (text) {
                        try {
                            payload = JSON.parse(text);
                        } catch (parseError) {
                            var htmlError = new Error('The server returned an unexpected response. Please refresh and try again.');
                            htmlError.status = response.status;
                            htmlError.nonJson = true;
                            throw htmlError;
                        }
                    }

                    if (!response.ok) {
                        var message = 'Upload failed';
                        if (response.status === 401 || response.status === 403) {
                            message = 'You do not have permission to upload this file.';
                        } else if (payload) {
                            if (payload.message) {
                                message = payload.message;
                            } else if (payload.error) {
                                message = payload.error;
                            }
                        }
                        var error = new Error(message);
                        error.status = response.status;
                        error.payload = payload;
                        throw error;
                    }
                    return payload;
                });
            });
        },

        markConversationRead: function (conversationId) {
            return request('POST', '/conversations/' + conversationId + '/read');
        },

        searchUsers: function (query) {
            return request('GET', '/users/search?query=' + encodeURIComponent(query));
        }
    };
})(window);
