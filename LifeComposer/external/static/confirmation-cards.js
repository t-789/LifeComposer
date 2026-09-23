/**
 * v0.1 M2/M7: renders chat-proposed profile change cards above the input bar.
 *
 * Only textContent is used for dynamic values, so model output can never become
 * executable HTML. Confirmation always goes through the independent
 * /api/profiles/change-candidates/{id}/decision endpoint.
 */
(function () {
    'use strict';

    var FIELD_LABELS = {
        availableTime: '每周可投入时间',
        skillsJson: '技能',
        interestsJson: '兴趣',
        experiencesJson: '经历',
        goals: '目标'
    };

    var state = {
        area: null,
        onDecided: null,
        rendered: {}
    };

    function labelFor(field) {
        return FIELD_LABELS[field] || field;
    }

    function prettyValue(value) {
        if (value === null || value === undefined || value === '') return '（空）';
        var text = String(value);
        try {
            var parsed = JSON.parse(text);
            if (Array.isArray(parsed)) return parsed.join('、');
            if (typeof parsed === 'object') return JSON.stringify(parsed);
        } catch (ignored) { }
        return text;
    }

    function init(options) {
        state.area = options.area;
        state.onDecided = options.onDecided || null;
    }

    function clearAll() {
        state.rendered = {};
        if (state.area) state.area.innerHTML = '';
    }

    function addProposals(proposals) {
        if (!state.area || !proposals) return;
        var list = Array.isArray(proposals) ? proposals : [proposals];
        for (var i = 0; i < list.length; i++) {
            var proposal = list[i];
            if (proposal && proposal.candidateId && !state.rendered[proposal.candidateId]) {
                state.rendered[proposal.candidateId] = true;
                state.area.appendChild(buildCard(proposal));
            }
        }
    }

    function buildCard(proposal) {
        var card = document.createElement('div');
        card.className = 'confirm-card';
        card.setAttribute('data-candidate-id', proposal.candidateId);

        var title = document.createElement('div');
        title.className = 'confirm-card-title';
        title.textContent = '画像变更待确认：' + labelFor(proposal.field);
        card.appendChild(title);

        var change = document.createElement('div');
        change.className = 'confirm-card-change';
        change.textContent = prettyValue(proposal.oldValue) + '  →  ' + prettyValue(proposal.newValue);
        card.appendChild(change);

        if (proposal.rationale) {
            var rationale = document.createElement('div');
            rationale.className = 'confirm-card-rationale';
            rationale.textContent = '提议依据：' + proposal.rationale;
            card.appendChild(rationale);
        }

        var meta = document.createElement('div');
        meta.className = 'confirm-card-meta';
        meta.textContent = '状态：' + (proposal.status || 'PENDING_CONFIRMATION')
            + (proposal.expiresAt ? ' · 有效期至 ' + proposal.expiresAt : '');
        card.appendChild(meta);

        var reason = document.createElement('input');
        reason.type = 'text';
        reason.className = 'confirm-card-reason';
        reason.placeholder = '拒绝理由（可选）';
        reason.setAttribute('data-role', 'reason');
        card.appendChild(reason);

        var actions = document.createElement('div');
        actions.className = 'confirm-card-actions';

        var confirmBtn = document.createElement('button');
        confirmBtn.type = 'button';
        confirmBtn.className = 'confirm-btn';
        confirmBtn.textContent = '确认写入';
        confirmBtn.onclick = function () { decide(card, proposal.candidateId, 'CONFIRM', ''); };
        actions.appendChild(confirmBtn);

        var rejectBtn = document.createElement('button');
        rejectBtn.type = 'button';
        rejectBtn.className = 'reject-btn';
        rejectBtn.textContent = '拒绝';
        rejectBtn.onclick = function () { decide(card, proposal.candidateId, 'REJECT', reason.value); };
        actions.appendChild(rejectBtn);

        card.appendChild(actions);

        var result = document.createElement('div');
        result.className = 'confirm-card-result';
        result.setAttribute('data-role', 'result');
        card.appendChild(result);
        return card;
    }

    async function decide(card, candidateId, decision, reason) {
        setCardStatus(card, '处理中…');
        try {
            var response = await fetch('/api/profiles/change-candidates/' + encodeURIComponent(candidateId) + '/decision', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ decision: decision, reason: reason || null })
            });
            var body = await response.json().catch(function () { return {}; });
            if (!response.ok) {
                setCardStatus(card, '失败：' + (body.message || body.error || response.status));
                return;
            }
            // The agent continuation belongs in the conversation, not inside the
            // confirmation card. Remove the card after the callback so the
            // decision is visible exactly once, in the chat stream.
            try {
                if (state.onDecided) state.onDecided(body);
            } finally {
                removeCard(card, candidateId);
            }
        } catch (e) {
            setCardStatus(card, '网络错误，请重试');
        }
    }

    function removeCard(card, candidateId) {
        if (candidateId) {
            delete state.rendered[candidateId];
        }
        if (card && card.parentNode) {
            card.parentNode.removeChild(card);
        }
    }

    function setCardStatus(card, text) {
        var result = card.querySelector('[data-role="result"]');
        if (result) result.textContent = text;
    }

    async function refresh() {
        if (!state.area) return;
        try {
            var response = await fetch('/api/profiles/change-candidates', { credentials: 'include' });
            if (!response.ok) return;
            var proposals = await response.json();
            clearAll();
            addProposals(proposals);
        } catch (e) {
            // Debug surface only; ignore transient failures.
        }
    }

    window.LifeComposerConfirmation = {
        init: init,
        addProposals: addProposals,
        refresh: refresh,
        clearAll: clearAll
    };
})();
