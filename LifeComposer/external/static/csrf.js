/**
 * Milestone 5: same-origin CSRF helper.
 *
 * The server exposes a readable XSRF-TOKEN cookie and GET /api/csrf. This file
 * wraps window.fetch so every non-safe request automatically carries the
 * X-XSRF-TOKEN header. Safe methods are untouched.
 */
(function () {
    'use strict';

    var originalFetch = window.fetch.bind(window);

    function getCookie(name) {
        var prefix = name + '=';
        var parts = document.cookie ? document.cookie.split(';') : [];
        for (var i = 0; i < parts.length; i++) {
            var part = parts[i].trim();
            if (part.indexOf(prefix) === 0) {
                return decodeURIComponent(part.substring(prefix.length));
            }
        }
        return null;
    }

    async function ensureToken() {
        var token = getCookie('XSRF-TOKEN');
        if (token) {
            return token;
        }
        try {
            var response = await originalFetch('/api/csrf', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Accept': 'application/json' }
            });
            if (response.ok) {
                var data = await response.json().catch(function () { return {}; });
                token = data.token || getCookie('XSRF-TOKEN');
            }
        } catch (ignored) {
            // fall through to cookie read below
        }
        return token || getCookie('XSRF-TOKEN');
    }

    window.fetch = async function (input, init) {
        var options = init || {};
        options.credentials = options.credentials || 'include';

        var method = (options.method || (typeof input !== 'string' && input.method) || 'GET').toUpperCase();
        if (['GET', 'HEAD', 'OPTIONS'].indexOf(method) === -1) {
            var token = await ensureToken();
            if (token) {
                if (options.headers instanceof Headers) {
                    options.headers.set('X-XSRF-TOKEN', token);
                } else {
                    options.headers = Object.assign({}, options.headers || {}, { 'X-XSRF-TOKEN': token });
                }
            }
        }
        return originalFetch(input, options);
    };

    window.LifeComposerCsrf = {
        getCookie: getCookie,
        ensureToken: ensureToken
    };
})();
