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
