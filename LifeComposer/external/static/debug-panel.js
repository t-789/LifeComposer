/**
 * v0.1 M7: right-hand debug monitor for chat_test.
 *
 * All dynamic content is rendered with textContent. The module can be reused by
 * the v1 page without the chat_test DOM structure.
 */
(function () {
    'use strict';

    var targets = {};
    var toolRows = {};
    var toolStartTimes = {};

    function init(options) {
        targets = options || {};
        setStatus('就绪');
    }

    function setStatus(text) {
        if (targets.status) targets.status.textContent = text;
    }

    function setText(target, text) {
        if (target) target.textContent = text === null || text === undefined ? '' : String(text);
    }

    function clear(element) {
        if (element) element.innerHTML = '';
    }

    async function refreshProfile() {
        if (!targets.profile) return;
        try {
            var response = await fetch('/api/profiles/me', { credentials: 'include' });
            if (response.status === 404) {
                clear(targets.profile);
                appendProfileLine('画像', '尚未填写');
                return;
            }
            if (!response.ok) {
                setText(targets.profile, '画像读取失败');
                return;
            }
            var profile = await response.json();
            clear(targets.profile);
            appendProfileLine('学院', profile.college);
            appendProfileLine('专业', profile.major);
            appendProfileLine('年级', profile.grade);
            appendProfileLine('每周投入', profile.availableTime);
            appendProfileLine('技能', profile.skillsJson);
            appendProfileLine('兴趣', profile.interestsJson);
            appendProfileLine('经历', profile.experiencesJson);
            appendProfileLine('目标', profile.goals);
            appendProfileLine('版本', profile.version);
        } catch (e) {
            setText(targets.profile, '画像读取失败');
        }
    }

    function appendProfileLine(label, value) {
        if (!targets.profile) return;
        var row = document.createElement('div');
        row.className = 'debug-line';
        var key = document.createElement('span');
        key.className = 'debug-key';
        key.textContent = label + '：';
        var val = document.createElement('span');
        val.textContent = value === null || value === undefined || value === '' ? '—' : String(value);
        row.appendChild(key);
        row.appendChild(val);
        targets.profile.appendChild(row);
    }

    async function refreshCandidates() {
        if (!targets.candidates) return;
        try {
            var response = await fetch('/api/profiles/change-candidates', { credentials: 'include' });
            clear(targets.candidates);
            if (!response.ok) {
                appendCandidateLine('候选读取失败');
                return;
            }
            var proposals = await response.json();
            if (!proposals.length) {
                appendCandidateLine('暂无待确认候选');
                return;
            }
            for (var i = 0; i < proposals.length; i++) {
                var p = proposals[i];
                appendCandidateLine(p.field + '：' + p.status + '（' + (p.expiresAt || '') + '）');
            }
        } catch (e) {
            appendCandidateLine('候选读取失败');
        }
    }

    async function refreshCapabilities() {
        if (!targets.capabilities) return;
        try {
            var response = await fetch('/api/profiles/me/capabilities', { credentials: 'include' });
            clear(targets.capabilities);
            if (!response.ok) {
                appendLine(targets.capabilities, '能力标签读取失败');
                return;
            }
            var states = await response.json();
            if (!states.length) {
                appendLine(targets.capabilities, '暂无归一后的能力标签');
                return;
            }
            for (var i = 0; i < states.length; i++) {
                var state = states[i];
                var evidence = (state.evidence && state.evidence.length) ? ' · 证据：' + state.evidence.join('；') : '';
                appendLine(targets.capabilities, state.tag + ' · ' + (state.level || 'unknown')
                    + ' · ' + (state.source || 'unknown') + evidence);
            }
        } catch (e) {
            appendLine(targets.capabilities, '能力标签读取失败');
        }
    }

    function formatBreakdown(item) {
        var breakdown = item.scoreBreakdown || {};
        return '明细 技能=' + numberOrDash(breakdown.skillMatch)
            + ' 时间=' + numberOrDash(breakdown.timeFit)
            + ' 目标=' + numberOrDash(breakdown.goalRelevance)
            + ' 难度=' + numberOrDash(breakdown.difficultyFit)
            + ' 准备=' + numberOrDash(breakdown.preparationFit);
    }

    function numberOrDash(value) {
        return value === undefined || value === null ? '-' : Number(value).toFixed(2);
    }

    async function refreshRecommendations() {
        if (!targets.recommendations) return;
        try {
            var response = await fetch('/api/growth-directions/recommendations', { credentials: 'include' });
            clear(targets.recommendations);
            if (!response.ok) {
                appendLine(targets.recommendations, '推荐读取失败');
                return;
            }
            var recommendations = await response.json();
            if (!recommendations.length) {
                appendLine(targets.recommendations, '暂无推荐');
                return;
            }
            for (var i = 0; i < recommendations.length; i++) {
                var item = recommendations[i];
                appendLine(targets.recommendations, item.name + ' · ' + item.classification
                    + ' · 总分 ' + Number(item.score).toFixed(3)
                    + (item.missingTags && item.missingTags.length ? ' · 缺 ' + item.missingTags.join('、') : ''));
                appendLine(targets.recommendations, formatBreakdown(item));
                if (item.timeNote) appendLine(targets.recommendations, '时间依据：' + item.timeNote);
                if (item.followUpQuestions && item.followUpQuestions.length) {
                    appendLine(targets.recommendations, '待补充：' + item.followUpQuestions.join('；'));
                }
            }
            refreshPathSummary(recommendations[0].directionId);
        } catch (e) {
            appendLine(targets.recommendations, '推荐读取失败');
        }
    }

    async function refreshPathSummary(directionId) {
        if (!targets.pathSummary || !directionId) return;
        try {
            var response = await fetch('/api/growth-directions/' + encodeURIComponent(directionId) + '/path',
                { credentials: 'include' });
            clear(targets.pathSummary);
            if (!response.ok) {
                appendLine(targets.pathSummary, '路径摘要读取失败');
                return;
            }
            var plan = await response.json();
            appendLine(targets.pathSummary, plan.name + ' · 当前基础：' + (plan.currentLevel || '未建立'));
            appendLine(targets.pathSummary, '预期投入：' + plan.expectedInvestment);
            if (plan.gapTasks && plan.gapTasks.length) {
                appendLine(targets.pathSummary, '补足任务：' + plan.gapTasks.map(function (t) { return t.tag; }).join('、'));
            }
            if (plan.practiceTasks && plan.practiceTasks.length) {
                appendLine(targets.pathSummary, '实践任务：' + plan.practiceTasks.map(function (t) { return t.name; }).join('、'));
            }
            var resources = plan.resources || [];
            for (var i = 0; i < resources.length; i++) {
                var resource = resources[i];
                var warning = resource.dataQuality === 'needs_review' ? ' ⚠ needs_review（待人工复核）' : '';
                appendLine(targets.pathSummary, '资源：' + resource.name + '（' + resource.type
                    + (resource.sourceUrl ? ' · ' + resource.sourceUrl : '') + '）' + warning);
            }
        } catch (e) {
            appendLine(targets.pathSummary, '路径摘要读取失败');
        }
    }

    function appendLine(target, text) {
        if (!target) return;
        var row = document.createElement('div');
        row.className = 'debug-line';
        row.textContent = text;
        target.appendChild(row);
    }

    function appendCandidateLine(text) {
        if (!targets.candidates) return;
        var row = document.createElement('div');
        row.className = 'debug-line';
        row.textContent = text;
        targets.candidates.appendChild(row);
    }

    function appendToolCall(callId, name, description, argumentsJson) {
        if (!targets.tools) return;
        var key = callId || name;
        toolStartTimes[key] = Date.now();
        var row = document.createElement('div');
        row.className = 'debug-line tool-call';
        row.textContent = '[' + new Date().toLocaleTimeString() + '] → ' + (description || name) + ' ' + (argumentsJson || '');
        row.setAttribute('data-call-id', callId || '');
        toolRows[key] = row;
        targets.tools.appendChild(row);
    }

    function appendToolResult(callId, name, ok, resultJson) {
        if (!targets.tools) return;
        var key = callId || name;
        var elapsed = toolStartTimes[key] ? (Date.now() - toolStartTimes[key]) : null;
        delete toolStartTimes[key];
        var row = document.createElement('div');
        row.className = 'debug-line tool-result ' + (ok ? 'ok' : 'error');
        row.textContent = '[' + new Date().toLocaleTimeString() + '] ← ' + name
            + (ok ? ' ✓' : ' ✗')
            + (elapsed !== null ? '（' + elapsed + 'ms）' : '')
            + ' ' + summarizeResult(resultJson, ok);
        targets.tools.appendChild(row);
        renderRagFromResult(name, resultJson);
    }

    function summarizeResult(resultJson, ok) {
        if (!resultJson) return '';
        try {
            var parsed = JSON.parse(resultJson);
            if (!ok) {
                return '错误：' + (parsed.errorCode || parsed.error || 'UNKNOWN')
                    + (parsed.message ? ' · ' + parsed.message : '');
            }
            if (parsed && parsed.data) {
                if (typeof parsed.data === 'string') return parsed.data;
                if (Array.isArray(parsed.data.items)) {
                    return 'items=' + parsed.data.items.length;
                }
                return JSON.stringify(parsed.data).slice(0, 220);
            }
            if (parsed && parsed.message) return parsed.message;
        } catch (ignored) { }
        return String(resultJson).slice(0, 220);
    }

    function renderRagFromResult(name, resultJson) {
        if (!targets.rag || !resultJson) return;
        try {
            var parsed = JSON.parse(resultJson);
            var data = parsed && parsed.data;
            if (!data) return;
            var hits = Array.isArray(data.items) ? data.items : (Array.isArray(data.hits) ? data.hits : null);
            if (!hits) return;
            clear(targets.rag);
            if (!hits.length) {
                appendLine(targets.rag, 'RAG 无命中');
                return;
            }
            for (var i = 0; i < hits.length; i++) {
                var hit = hits[i];
                var source = hit.sourceUrl || hit.sourceFile || hit.title || hit.chunkId || hit.relatedResourceId || '命中';
                var row = document.createElement('div');
                row.className = 'debug-line';
                row.textContent = (i + 1) + '. ' + source
                    + (hit.pageOrSection ? ' · ' + hit.pageOrSection : '')
                    + (hit.similarity !== undefined ? ' · 相似度 ' + Number(hit.similarity).toFixed(3) : '')
                    + (hit.title && hit.sourceUrl ? ' · ' + hit.title : '');
                targets.rag.appendChild(row);
            }
        } catch (ignored) { }
    }

    function appendEvent(kind, text) {
        if (!targets.events) return;
        var row = document.createElement('div');
        row.className = 'debug-line ' + kind;
        row.textContent = text;
        targets.events.appendChild(row);
    }

    window.LifeComposerDebug = {
        init: init,
        setStatus: setStatus,
        refreshProfile: refreshProfile,
        refreshCandidates: refreshCandidates,
        refreshCapabilities: refreshCapabilities,
        refreshRecommendations: refreshRecommendations,
        refreshPathSummary: refreshPathSummary,
        appendToolCall: appendToolCall,
        appendToolResult: appendToolResult,
        appendEvent: appendEvent
    };
})();
