/**
 * v0.1 M7: reusable SSE client for /api/chat/stream.
 *
 * The parser is intentionally page-agnostic: chat_test and the future v1 chat
 * page both consume named events through one code path.
 */
(function () {
    'use strict';

    function parseEvent(rawEvent) {
        var lines = String(rawEvent || '').split('\n');
        var eventName = 'message';
        var dataLines = [];
        for (var i = 0; i < lines.length; i++) {
            var line = lines[i];
            if (line.indexOf('event:') === 0) {
                eventName = line.slice(6).trim();
            } else if (line.indexOf('data:') === 0) {
                dataLines.push(line.slice(5).trim());
            }
        }
        var data = {};
        if (dataLines.length) {
            var payload = dataLines.join('\n');
            try {
                data = JSON.parse(payload);
            } catch (e) {
                data = { raw: payload };
            }
        }
        return { name: eventName, data: data };
    }

    async function streamChat(body, handlers) {
        handlers = handlers || {};
        var response;
        try {
            response = await fetch('/api/chat/stream', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
                credentials: 'include',
                body: JSON.stringify(body || {})
            });
        } catch (e) {
            if (handlers.onNetworkError) handlers.onNetworkError(e);
            return;
        }

        if (!response.ok) {
            var errorData = null;
            try {
                errorData = await response.json();
            } catch (ignored) {
                errorData = null;
            }
            if (handlers.onHttpError) handlers.onHttpError(response.status, errorData);
            return;
        }
        if (!response.body) {
            if (handlers.onUnsupported) handlers.onUnsupported();
            return;
        }

        var reader = response.body.getReader();
        var decoder = new TextDecoder('utf-8');
        var buffer = '';
        try {
            while (true) {
                var chunk = await reader.read();
                if (chunk.done) break;
                buffer += decoder.decode(chunk.value, { stream: true });
                buffer = buffer.replace(/\r\n/g, '\n');
                var separatorIndex;
                while ((separatorIndex = buffer.indexOf('\n\n')) >= 0) {
                    var rawEvent = buffer.slice(0, separatorIndex);
                    buffer = buffer.slice(separatorIndex + 2);
                    if (rawEvent.trim() && handlers.onEvent) {
                        var parsed = parseEvent(rawEvent);
                        handlers.onEvent(parsed.name, parsed.data);
                    }
                }
            }
            if (buffer.trim() && handlers.onEvent) {
                var tail = parseEvent(buffer);
                handlers.onEvent(tail.name, tail.data);
            }
        } catch (e) {
            if (handlers.onStreamError) handlers.onStreamError(e);
        }
    }

    window.LifeComposerSse = {
        streamChat: streamChat,
        parseEvent: parseEvent
    };
})();
