/*
 * LifeComposer admin debug console (Milestone 7).
 *
 * One shared driver for every /admin page: layout, navigation, filter bar,
 * server-side pagination, sorting, detail drawer, action modals, loading/empty/
 * error states.
 *
 * Safety rules kept deliberately strict:
 *   - text from the API is written with textContent / createTextNode only, so
 *     stored HTML (usernames, feedback, chat content, JSON) can never execute;
 *   - links are only rendered for http/https URLs and get rel="noopener noreferrer";
 *   - no password is ever placed in the URL, console or persistent DOM state;
 *   - all write actions go through the CSRF-aware fetch wrapper loaded before
 *     this file (/csrf.js).
 *
 * Trust model (review decision, v0.0.6): the administrator is a fully trusted
 * debug/operations role. The console therefore never returns credentials, raw
 * embedding vectors or certificate paths, but an operator MAY see personal
 * profile data needed for debugging — the profile list only masks the student id
 * as a display minimisation, while the detail drawer shows the full value
 * together with the preferences JSON. See AdminController for the same statement
 * on the server side.
 */
(function () {
    'use strict';

    // ------------------------------------------------------------- DOM helpers

    function el(tag, attrs, children) {
        const node = document.createElement(tag);
        if (attrs) {
            Object.keys(attrs).forEach(function (key) {
                const value = attrs[key];
                if (value === null || value === undefined) {
                    return;
                }
                if (key === 'class') {
                    node.className = value;
                } else if (key === 'text') {
                    node.textContent = value;
                } else if (key.indexOf('on') === 0 && typeof value === 'function') {
                    node.addEventListener(key.slice(2), value);
                } else {
                    node.setAttribute(key, value);
                }
            });
        }
        append(node, children);
        return node;
    }

    function append(node, children) {
        if (children === null || children === undefined) {
            return;
        }
        if (Array.isArray(children)) {
            children.forEach(function (child) {
                append(node, child);
            });
            return;
        }
        if (children instanceof Node) {
            node.appendChild(children);
            return;
        }
        node.appendChild(document.createTextNode(String(children)));
    }

    function clear(node) {
        while (node.firstChild) {
            node.removeChild(node.firstChild);
        }
    }

    // -------------------------------------------------------------- formatting

    function isBlank(value) {
        return value === null || value === undefined || value === '';
    }

    function fmtText(value) {
        return isBlank(value) ? '—' : String(value);
    }

    function fmtDate(value) {
        if (isBlank(value)) {
            return '—';
        }
        const raw = String(value);
        if (!/[Zz]|[+-]\d{2}:?\d{2}$/.test(raw)) {
            // Already a server-side wall-clock string; show it unchanged.
            return raw;
        }
        const date = new Date(raw);
        return Number.isNaN(date.getTime()) ? raw : date.toLocaleString('zh-CN', { hour12: false });
    }

    function fmtBool(value) {
        return value ? '是' : '否';
    }

    function badge(label, kind) {
        return el('span', { class: 'badge ' + (kind || 'muted'), text: label });
    }

    function boolBadge(value, trueLabel, falseLabel) {
        return value ? badge(trueLabel || '是', 'ok') : badge(falseLabel || '否', 'muted');
    }

    function looksLikeJson(value) {
        if (typeof value !== 'string') {
            return false;
        }
        const trimmed = value.trim();
        return (trimmed.charAt(0) === '{' && trimmed.slice(-1) === '}') ||
            (trimmed.charAt(0) === '[' && trimmed.slice(-1) === ']');
    }

    function prettyJson(value) {
        try {
            return JSON.stringify(JSON.parse(value), null, 2);
        } catch (ignored) {
            return String(value);
        }
    }

    function safeUrl(raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            const url = new URL(String(raw), window.location.origin);
            if (url.protocol === 'http:' || url.protocol === 'https:') {
                return url.href;
            }
        } catch (ignored) {
            return null;
        }
        return null;
    }

    function linkOrText(raw) {
        const href = safeUrl(raw);
        if (!href) {
            return document.createTextNode(fmtText(raw));
        }
        return el('a', { href: href, target: '_blank', rel: 'noopener noreferrer', text: String(raw) });
    }

    function fmtNumber(value) {
        return isBlank(value) ? '—' : String(value);
    }

    // ------------------------------------------------------------------ toasts

    function notify(message, isError) {
        let box = document.getElementById('adminToastBox');
        if (!box) {
            box = el('div', { id: 'adminToastBox', class: 'toast-box' });
            document.body.appendChild(box);
        }
        const item = el('div', { class: 'toast' + (isError ? ' error' : ''), text: message });
        box.appendChild(item);
        window.setTimeout(function () {
            item.remove();
        }, isError ? 6000 : 3000);
    }

    // --------------------------------------------------------------- API calls

    async function api(url, options) {
        const init = Object.assign({ headers: { Accept: 'application/json' } }, options || {});
        const response = await fetch(url, init);
        const contentType = response.headers.get('Content-Type') || '';
        let payload = null;
        if (contentType.indexOf('application/json') >= 0) {
            payload = await response.json().catch(function () { return null; });
        } else {
            const text = await response.text().catch(function () { return ''; });
            payload = text ? { message: text } : null;
        }
        if (!response.ok) {
            const message = (payload && (payload.message || payload.error)) || ('HTTP ' + response.status);
            const error = new Error(message);
            error.status = response.status;
            throw error;
        }
        return payload;
    }

    function buildQuery(state) {
        const params = new URLSearchParams();
        params.set('page', String(state.page));
        params.set('pageSize', String(state.pageSize));
        if (state.sort) {
            params.set('sort', state.sort);
            params.set('dir', state.dir || 'asc');
        }
        Object.keys(state.filters).forEach(function (key) {
            const value = state.filters[key];
            if (!isBlank(value)) {
                params.set(key, value);
            }
        });
        return params.toString();
    }

    // ------------------------------------------------------------------ modals

    function openModal(spec) {
        return new Promise(function (resolve) {
            const backdrop = el('div', { class: 'modal-backdrop' });
            const errorBox = el('div', { class: 'form-error' });
            const fields = (spec.fields || []).map(function (definition) {
                const input = el('input', {
                    type: definition.type || 'text',
                    placeholder: definition.placeholder || '',
                    autocomplete: definition.type === 'password' ? 'new-password' : 'off'
                });
                if (definition.value) {
                    input.value = definition.value;
                }
                return {
                    def: definition,
                    input: input,
                    node: el('div', { class: 'field' }, [
                        el('label', { text: definition.label }),
                        input
                    ])
                };
            });

            function close(result) {
                // Never leave entered values (possible passwords) in the DOM.
                fields.forEach(function (field) {
                    field.input.value = '';
                });
                backdrop.remove();
                document.removeEventListener('keydown', onKey);
                resolve(result);
            }

            function submit() {
                const values = {};
                for (let i = 0; i < fields.length; i += 1) {
                    const field = fields[i];
                    const value = field.input.value;
                    if (field.def.required && value === '') {
                        errorBox.textContent = field.def.label + '不能为空';
                        field.input.focus();
                        return;
                    }
                    if (field.def.confirmWith && value !== values[field.def.confirmWith]) {
                        errorBox.textContent = '两次输入不一致';
                        field.input.focus();
                        return;
                    }
                    values[field.def.name] = value;
                }
                close(values);
            }

            function onKey(event) {
                if (event.key === 'Escape') {
                    close(null);
                }
            }

            backdrop.appendChild(el('div', { class: 'modal' }, [
                el('h3', { text: spec.title }),
                spec.description ? el('p', { text: spec.description }) : null,
                fields.map(function (field) { return field.node; }),
                errorBox,
                el('div', { class: 'actions' }, [
                    el('button', { text: '取消', onclick: function () { close(null); } }),
                    el('button', { class: 'primary', text: spec.confirmLabel || '确认', onclick: submit })
                ])
            ]));
            document.body.appendChild(backdrop);
            document.addEventListener('keydown', onKey);
            if (fields.length) {
                fields[0].input.focus();
            }
        });
    }

    // ------------------------------------------------------------ detail drawer

    const FIELD_LABELS = {
        id: 'ID', userId: '用户 ID', username: '用户名', userType: '用户类型', role: '角色',
        banned: '已封禁', banEndTime: '封禁结束', avatar: '头像', createdAt: '创建时间',
        updatedAt: '更新时间', hasProfile: '画像', usedToday: '今日已用', dailyLimit: '每日额度',
        remainingToday: '今日剩余', college: '学院', major: '专业', grade: '年级',
        passwordResetRequired: '临时密码待修改', tempPasswordExpiresAt: '临时密码有效期至',
        studentId: '学号', availableTime: '可用时间', skillsJson: '技能(JSON)',
        interestsJson: '兴趣(JSON)', experiencesJson: '经历(JSON)', goalsJson: '目标(JSON)',
        preferencesJson: '偏好(JSON)', hasPreferences: '有偏好配置', type: '类型',
        provider: 'Provider', model: '模型', status: '状态', errorMessage: '错误信息',
        requestJson: '请求(JSON)', responseJson: '响应(JSON)', requestLength: '请求长度',
        responseLength: '响应长度', requestPreview: '请求摘要', responsePreview: '响应摘要',
        content: '内容', contentPreview: '内容摘要', contentLength: '内容长度',
        contentTruncated: '内容已截断', structured: '结构化消息', createTime: '时间',
        resolved: '已解决', resolvedBy: '处理人', resolvedTime: '处理时间', url: 'URL',
        userAgent: 'User-Agent', stackTrace: '堆栈', stackTraceLength: '堆栈长度',
        hasStackTrace: '有堆栈', creditType: '加分类型', category: '类别',
        compLevel: '竞赛级别', compName: '竞赛/活动', awardTier: '奖项', credits: '分值',
        categoryCap: '类别上限', teamFormula: '团队公式', studentCohort: '适用年级',
        docSource: '来源文件', levelsJson: '阶段(JSON)', notes: '备注', notesPreview: '备注摘要',
        notesLength: '备注长度', ruleId: '规则 ID', obtainedDate: '获奖日期',
        verified: '已审核', hasCertificate: '有证明', resourceId: '资源 ID', name: '名称',
        difficulty: '难度', dataQuality: '数据质量', sourceUrl: '来源链接',
        sourceUrlsJson: '多来源(JSON)', sourceFile: '来源文件', courseLink: '课程链接',
        registrationStart: '报名开始', registrationDeadline: '报名截止',
        preparationPeriod: '准备周期', description: '简介', descriptionPreview: '简介摘要',
        stagesJson: '赛程(JSON)', targetMajorsJson: '目标专业(JSON)',
        requiredSkillsJson: '技能需求(JSON)', teamRolesJson: '队伍角色(JSON)',
        bonusPointJson: '保研加分(JSON)', teachesSkillsJson: '培养技能(JSON)',
        notesJson: '备注(JSON)', chunkId: '切片 ID', title: '标题', text: '正文',
        textPreview: '正文摘要', textLength: '正文长度', textTruncated: '正文已截断',
        sourceType: '来源类型', pageOrSection: '页码/章节', relatedResourceId: '关联资源',
        embeddingModel: 'Embedding 模型', embeddingDimensions: '维度', embeddingStatus: 'Embedding 状态',
        embeddingError: 'Embedding 错误', embeddingUpdatedAt: 'Embedding 更新时间',
        embeddingJsonLength: '向量长度(字符)', contentHash: '内容哈希',
        level1Desc: 'L1 描述', level2Desc: 'L2 描述', level3Desc: 'L3 描述',
        skillAliasesJson: '别名(JSON)', skillAliasesPreview: '别名摘要', skillAliasesLength: '别名长度',
        typicalEvidenceJson: '证据(JSON)', typicalEvidencePreview: '证据摘要',
        typicalEvidenceLength: '证据长度', section: '字典节', refKey: '键', refValue: '值',
        refValuePreview: '值摘要', refValueLength: '值长度', note: '备注',
        usageDate: '日期', requestCount: '请求数', maskNote: '说明', studentIdMasked: '学号已掩码'
    };

    const JSON_KEYS = new Set(['skillsJson', 'interestsJson', 'experiencesJson', 'goalsJson',
        'preferencesJson', 'requestJson', 'responseJson', 'levelsJson', 'stagesJson',
        'targetMajorsJson', 'requiredSkillsJson', 'teamRolesJson', 'bonusPointJson',
        'teachesSkillsJson', 'notesJson', 'sourceUrlsJson', 'skillAliasesJson',
        'typicalEvidenceJson', 'refValue', 'content']);

    function labelFor(key) {
        return FIELD_LABELS[key] || key;
    }

    function detailValue(key, value) {
        if (isBlank(value)) {
            return document.createTextNode('—');
        }
        if (key === 'avatar' || key === 'sourceUrl' || key === 'courseLink' || key === 'url') {
            return linkOrText(value);
        }
        if (typeof value === 'boolean') {
            return document.createTextNode(fmtBool(value));
        }
        if (JSON_KEYS.has(key) && looksLikeJson(value)) {
            return el('pre', { class: 'json', text: prettyJson(value) });
        }
        if (typeof value === 'string' && value.length > 120) {
            return el('pre', { class: 'text', text: value });
        }
        return document.createTextNode(String(value));
    }

    function openDrawer(title, entries, extraNodes) {
        const backdrop = el('div', { class: 'drawer-backdrop' });
        const list = el('dl', { class: 'kv' });
        entries.forEach(function (entry) {
            append(list, el('dt', { text: labelFor(entry[0]) }));
            append(list, el('dd', null, detailValue(entry[0], entry[1])));
        });

        function close() {
            backdrop.remove();
            document.removeEventListener('keydown', onKey);
        }
        function onKey(event) {
            if (event.key === 'Escape') {
                close();
            }
        }

        backdrop.appendChild(el('div', { class: 'drawer' }, [
            el('div', { class: 'drawer-header' }, [
                el('h3', { text: title }),
                el('button', { text: '关闭', onclick: close })
            ]),
            extraNodes || null,
            list
        ]));
        document.body.appendChild(backdrop);
        document.addEventListener('keydown', onKey);
        backdrop.addEventListener('click', function (event) {
            if (event.target === backdrop) {
                close();
            }
        });
    }

    // ------------------------------------------------------------ view registry

    const PAGE_SIZES = [25, 50, 100, 200];

    function col(key, label, options) {
        return Object.assign({ key: key, label: label }, options || {});
    }

    const VIEWS = {
        dashboard: {
            title: '运维总览',
            hint: '12 张业务表记录数、账号、今日聊天用量、反馈与 RAG embedding 状态',
            kind: 'dashboard',
            endpoint: '/api/admin/dashboard'
        },
        users: {
            title: '用户与额度',
            hint: '账户状态、角色、画像与当日聊天额度；可直接重置当日额度或下发临时密码',
            endpoint: '/api/admin/users',
            columns: [
                col('id', 'ID', { sort: 'id' }),
                col('username', '用户名', { sort: 'username' }),
                col('role', '角色', { sort: 'type' }),
                col('banned', '状态', { render: function (v) { return v ? badge('已封禁', 'bad') : badge('正常', 'ok'); } }),
                col('passwordResetRequired', '临时密码', {
                    render: function (v, row) {
                        return v
                            ? badge('待修改 ' + fmtDate(row.tempPasswordExpiresAt), 'warn')
                            : badge('未下发', 'muted');
                    }
                }),
                col('hasProfile', '画像', { render: function (v) { return boolBadge(v, '有', '无'); } }),
                col('usedToday', '今日已用'),
                col('remainingToday', '今日剩余'),
                col('createdAt', '创建时间', { sort: 'createdAt', render: fmtDate })
            ],
            filters: [
                { name: 'q', label: '用户名包含', type: 'text' },
                { name: 'type', label: '角色', type: 'select', options: [['', '全部'], ['1', '普通用户'], ['2', '管理员']] },
                { name: 'banned', label: '状态', type: 'select', options: [['', '全部'], ['0', '正常'], ['1', '已封禁']] },
                { name: 'hasProfile', label: '画像', type: 'select', options: [['', '全部'], ['1', '有画像'], ['0', '无画像']] }
            ],
            actions: [
                {
                    label: '重置今日额度',
                    run: async function (row) {
                        const confirmed = window.confirm('确定重置用户 ' + row.username + ' 的今日聊天额度吗？');
                        if (!confirmed) {
                            return null;
                        }
                        await api('/api/users/admin/chat-quota/reset/' + row.id, { method: 'POST' });
                        return '已重置今日聊天额度';
                    }
                },
                {
                    label: '临时密码',
                    run: async function (row) {
                        const values = await openModal({
                            title: '为用户 ' + row.username + ' 下发临时密码',
                            description: '至少 ' + (row.userType === 2 ? 12 : 8) + ' 个字符，不能是 admin/000000/testuser 等弱密码。'
                                + '用户下次登录后必须先改成自己的密码才能使用其他功能，临时密码到期后自动失效；'
                                + '提交后不会回显，请当面告知用户。该用户已登录的会话将立即失效。',
                            confirmLabel: '下发临时密码',
                            fields: [
                                { name: 'newPassword', label: '新临时密码', type: 'password', required: true },
                                { name: 'confirmPassword', label: '再次输入', type: 'password', required: true, confirmWith: 'newPassword' }
                            ]
                        });
                        if (!values) {
                            return null;
                        }
                        const result = await api('/api/users/admin/reset-password/' + row.id, {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ newPassword: values.newPassword })
                        });
                        return (result && result.message)
                            ? result.message + '（有效期至 ' + fmtDate(result.tempPasswordExpiresAt) + '）'
                            : '临时密码已下发（未回显）';
                    }
                },
                {
                    label: function (row) { return row.banned ? '解封' : '封禁'; },
                    run: async function (row) {
                        if (row.banned) {
                            if (!window.confirm('确定解封用户 ' + row.username + ' 吗？')) {
                                return null;
                            }
                            await api('/api/users/' + row.id + '/unban', { method: 'PUT' });
                            return '已解封';
                        }
                        const values = await openModal({
                            title: '封禁用户 ' + row.username,
                            description: '填写 0 表示永久封禁，或使用 1y2m3d4h 形式。',
                            confirmLabel: '封禁',
                            fields: [{ name: 'banTime', label: '封禁时长', type: 'text', value: '0', required: true }]
                        });
                        if (!values) {
                            return null;
                        }
                        await api('/api/users/' + row.id + '/ban', {
                            method: 'PUT',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ banTime: values.banTime })
                        });
                        return '已封禁';
                    }
                },
                {
                    label: function (row) { return row.userType === 2 ? '撤销管理员' : '设为管理员'; },
                    run: async function (row) {
                        const promote = row.userType !== 2;
                        const text = promote ? '确定授予该用户管理员权限吗？' : '确定撤销该用户的管理员权限吗？';
                        if (!window.confirm(text)) {
                            return null;
                        }
                        await api('/api/users/' + row.id + (promote ? '/grant-admin' : '/revoke-admin'), { method: 'PUT' });
                        return promote ? '已授予管理员权限' : '已撤销管理员权限';
                    }
                }
            ]
        },
        profiles: {
            title: '用户画像',
            hint: '学院、专业、年级、技能、兴趣、经历、目标与可用时间；列表掩码学号，详情（可信调试角色）展示完整学号与偏好',
            endpoint: '/api/admin/user-profiles',
            columns: [
                col('userId', '用户 ID', { sort: 'userId' }),
                col('username', '用户名'),
                col('college', '学院', { sort: 'college' }),
                col('major', '专业'),
                col('grade', '年级', { sort: 'grade' }),
                col('studentId', '学号(掩码)'),
                col('availableTime', '可用时间'),
                col('hasPreferences', '偏好配置', { render: function (v) { return boolBadge(v, '有', '无'); } }),
                col('updatedAt', '更新时间', { sort: 'updatedAt', render: fmtDate })
            ],
            filters: [
                { name: 'userId', label: '用户 ID', type: 'number' },
                { name: 'q', label: '关键字', type: 'text' },
                { name: 'college', label: '学院', type: 'text' },
                { name: 'major', label: '专业', type: 'text' },
                { name: 'grade', label: '年级', type: 'text' }
            ],
            detail: function (row) { return '/api/admin/users/' + row.userId + '/profile'; }
        },
        planning: {
            title: 'AI 规划记录',
            hint: 'planning_history：按用户、状态、provider/model、日期筛选；列表只给摘要',
            endpoint: '/api/admin/planning-history',
            columns: [
                col('id', 'ID', { sort: 'id' }),
                col('userId', '用户 ID', { sort: 'userId' }),
                col('username', '用户名'),
                col('type', '类型'),
                col('status', '状态', { sort: 'status', render: function (v) { return badge(fmtText(v), v === 'FAILED' ? 'bad' : (v === 'SUCCESS' ? 'ok' : 'muted')); } }),
                col('provider', 'Provider'),
                col('model', '模型'),
                col('requestLength', '请求长度'),
                col('createdAt', '时间', { sort: 'createdAt', render: fmtDate })
            ],
            filters: [
                { name: 'userId', label: '用户 ID', type: 'number' },
                { name: 'type', label: '类型', type: 'text' },
                { name: 'status', label: '状态', type: 'select', options: [['', '全部'], ['SUCCESS', 'SUCCESS'], ['FAILED', 'FAILED'], ['MOCKED', 'MOCKED']] },
                { name: 'provider', label: 'Provider', type: 'text' },
                { name: 'model', label: '模型', type: 'text' },
                { name: 'dateFrom', label: '开始日期', type: 'date' },
                { name: 'dateTo', label: '结束日期', type: 'date' }
            ],
            detail: function (row) { return '/api/admin/planning-history/' + row.id; }
        },
        chat: {
            title: 'AI 对话记录',
            hint: 'chat_messages：按用户、role、日期筛选，列表仅摘要，完整内容在详情中展示',
            endpoint: '/api/admin/chat-messages',
            columns: [
                col('id', 'ID', { sort: 'id' }),
                col('userId', '用户 ID', { sort: 'userId' }),
                col('username', '用户名'),
                col('role', '角色', { sort: 'role', render: function (v) { return badge(fmtText(v), v === 'tool' ? 'warn' : 'muted'); } }),
                col('structured', '结构化', { render: function (v) { return boolBadge(v, '是', '—'); } }),
                col('contentPreview', '内容摘要', { wrap: true }),
                col('contentLength', '长度'),
                col('createTime', '时间', { sort: 'createTime', render: fmtDate })
            ],
            filters: [
                { name: 'userId', label: '用户 ID', type: 'number' },
                { name: 'role', label: '角色', type: 'select', options: [['', '全部'], ['user', 'user'], ['assistant', 'assistant'], ['tool', 'tool']] },
                { name: 'dateFrom', label: '开始日期', type: 'date' },
                { name: 'dateTo', label: '结束日期', type: 'date' }
            ],
            detail: function (row) { return '/api/admin/chat-messages/' + row.id; }
        },
        feedback: {
            title: '反馈与错误',
            hint: '用户反馈与系统错误；URL/UA/堆栈默认截断，详情中受控展示',
            endpoint: '/api/admin/feedback',
            columns: [
                col('id', 'ID', { sort: 'id' }),
                col('userId', '用户 ID'),
                col('username', '用户名'),
                col('type', '类型', { sort: 'type', render: function (v) { return v === 'system' ? badge('系统错误', 'warn') : badge('用户反馈', 'muted'); } }),
                col('resolved', '状态', { sort: 'resolved', render: function (v) { return v ? badge('已解决', 'ok') : badge('未解决', 'bad'); } }),
                col('hasStackTrace', '堆栈', { render: function (v) { return boolBadge(v, '有', '—'); } }),
                col('contentPreview', '内容摘要', { wrap: true }),
                col('createTime', '时间', { sort: 'createTime', render: fmtDate })
            ],
            filters: [
                { name: 'type', label: '类型', type: 'select', options: [['', '全部'], ['user', '用户反馈'], ['system', '系统错误']] },
                { name: 'resolved', label: '状态', type: 'select', options: [['', '全部'], ['0', '未解决'], ['1', '已解决']] },
                { name: 'userId', label: '用户 ID', type: 'number' },
                { name: 'dateFrom', label: '开始日期', type: 'date' },
                { name: 'dateTo', label: '结束日期', type: 'date' }
            ],
            detail: function (row) { return '/api/admin/feedback/' + row.id; },
            actions: [
                {
                    label: function (row) { return row.resolved ? '标记未解决' : '标记已解决'; },
                    run: async function (row) {
                        const next = !row.resolved;
                        if (!window.confirm('确定将该反馈标记为' + (next ? '已解决' : '未解决') + '吗？')) {
                            return null;
                        }
                        await api('/api/feedback/' + row.id + '/resolve', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ resolved: next })
                        });
                        return next ? '已标记为已解决' : '已标记为未解决';
                    }
                }
            ]
        },
        creditRules: {
            title: '加分规则',
            hint: 'college_credit_rules：全字段分页与来源检查，长备注截断后在详情展开',
            endpoint: '/api/admin/college-credit-rules',
            columns: [
                col('id', 'ID', { sort: 'id' }),
                col('college', '学院', { sort: 'college' }),
                col('creditType', '加分类型', { sort: 'creditType', render: function (v) { return v === 'graduation' ? badge('双创分', 'muted') : badge('保研加分', 'warn'); } }),
                col('category', '类别', { sort: 'category' }),
                col('compLevel', '级别', { sort: 'compLevel' }),
                col('compName', '竞赛/活动'),
                col('awardTier', '奖项'),
                col('credits', '分值', { sort: 'credits' }),
                col('categoryCap', '类别上限'),
                col('docSource', '来源文件'),
                col('notesPreview', '备注摘要', { wrap: true })
            ],
            filters: [
                { name: 'college', label: '学院', type: 'text' },
                { name: 'creditType', label: '加分类型', type: 'select', options: [['', '全部'], ['graduation', '双创分'], ['recommendation', '保研加分']] },
                { name: 'category', label: '类别', type: 'select', options: [['', '全部'], ['competition', 'competition'], ['lecture', 'lecture'], ['course', 'course'], ['project', 'project'], ['paper', 'paper'], ['patent', 'patent'], ['sports', 'sports'], ['arts', 'arts'], ['veteran', 'veteran']] },
                { name: 'compLevel', label: '竞赛级别', type: 'select', options: [['', '全部'], ['S', 'S'], ['A+', 'A+'], ['A', 'A'], ['B+', 'B+'], ['B', 'B'], ['national', 'national'], ['provincial', 'provincial'], ['school', 'school']] },
                { name: 'awardTier', label: '奖项', type: 'select', options: [['', '全部'], ['first', 'first'], ['second', 'second'], ['third', 'third'], ['special', 'special'], ['participation', 'participation']] },
                { name: 'q', label: '关键字', type: 'text' }
            ],
            detail: function (row) { return '/api/admin/college-credit-rules/' + row.id; }
        },
        creditActivities: {
            title: '加分记录',
            hint: 'credit_activities：按用户、规则、审核状态查询；证明只展示是否存在',
            endpoint: '/api/admin/credit-activities',
            columns: [
                col('id', 'ID', { sort: 'id' }),
                col('userId', '用户 ID', { sort: 'userId' }),
                col('username', '用户名'),
                col('creditType', '加分类型', { sort: 'creditType' }),
                col('category', '类别'),
                col('compName', '竞赛/活动'),
                col('compLevel', '级别'),
                col('awardTier', '奖项'),
                col('credits', '分值', { sort: 'credits' }),
                col('obtainedDate', '获奖日期', { sort: 'obtainedDate' }),
                col('verified', '审核', { sort: 'verified', render: function (v) { return v ? badge('已审核', 'ok') : badge('未审核', 'muted'); } }),
                col('hasCertificate', '证明', { render: function (v) { return boolBadge(v, '有', '无'); } })
            ],
            filters: [
                { name: 'userId', label: '用户 ID', type: 'number' },
                { name: 'creditType', label: '加分类型', type: 'select', options: [['', '全部'], ['graduation', '双创分'], ['recommendation', '保研加分']] },
                { name: 'verified', label: '审核状态', type: 'select', options: [['', '全部'], ['1', '已审核'], ['0', '未审核']] }
            ],
            detail: function (row) { return '/api/admin/credit-activities/' + row.id; }
        },
        resources: {
            title: '资源库',
            hint: 'resources：按类型、难度、数据质量、专业方向筛选；JSON 字段安全格式化',
            endpoint: '/api/admin/resources',
            columns: [
                col('id', 'ID', { sort: 'id' }),
                col('resourceId', '资源 ID', { sort: 'resourceId' }),
                col('name', '名称', { sort: 'name' }),
                col('type', '类型', { sort: 'type', render: function (v) { return v === 'course' ? badge('课程', 'muted') : badge('竞赛', 'warn'); } }),
                col('difficulty', '难度', { sort: 'difficulty' }),
                col('dataQuality', '数据质量', { sort: 'dataQuality', render: function (v) { return badge(fmtText(v), v === 'complete' ? 'ok' : (v === 'partial' ? 'warn' : 'bad')); } }),
                col('provider', '开课单位'),
                col('registrationDeadline', '报名截止'),
                col('descriptionPreview', '简介摘要', { wrap: true }),
                col('updatedAt', '数据日期', { sort: 'updatedAt' })
            ],
            filters: [
                { name: 'type', label: '类型', type: 'select', options: [['', '全部'], ['competition', '竞赛'], ['course', '课程']] },
                { name: 'difficulty', label: '难度', type: 'select', options: [['', '全部'], ['easy', 'easy'], ['medium', 'medium'], ['hard', 'hard']] },
                { name: 'dataQuality', label: '数据质量', type: 'select', options: [['', '全部'], ['complete', 'complete'], ['partial', 'partial'], ['needs_review', 'needs_review']] },
                { name: 'provider', label: '开课单位', type: 'text' },
                { name: 'q', label: '关键字', type: 'text' }
            ],
            detail: function (row) { return '/api/admin/resources/' + row.id; }
        },
        rag: {
            title: 'RAG 切片',
            hint: 'rag_chunks：文本/来源/关联与 embedding 状态排查；不返回原始向量',
            endpoint: '/api/admin/rag-chunks',
            columns: [
                col('chunkId', '切片 ID', { sort: 'chunkId' }),
                col('title', '标题', { sort: 'title' }),
                col('sourceType', '来源类型'),
                col('embeddingStatus', 'Embedding', { sort: 'embeddingStatus', render: function (v) { return badge(fmtText(v), v === 'SUCCESS' ? 'ok' : (v === 'FAILED' ? 'bad' : 'muted')); } }),
                col('embeddingModel', '模型'),
                col('embeddingDimensions', '维度'),
                col('embeddingJsonLength', '向量长度'),
                col('relatedResourceId', '关联资源'),
                col('embeddingUpdatedAt', 'Embedding 时间', { sort: 'embeddingUpdatedAt' }),
                col('embeddingError', '错误摘要', { wrap: true })
            ],
            filters: [
                { name: 'embeddingStatus', label: 'Embedding 状态', type: 'select', options: [['', '全部'], ['SUCCESS', 'SUCCESS'], ['FAILED', 'FAILED'], ['PENDING', 'PENDING'], ['SKIPPED', 'SKIPPED']] },
                { name: 'sourceType', label: '来源类型', type: 'select', options: [['', '全部'], ['web', 'web'], ['pdf', 'pdf'], ['json', 'json']] },
                { name: 'relatedResourceId', label: '关联资源 ID', type: 'text' },
                { name: 'q', label: '关键字', type: 'text' }
            ],
            detail: function (row) { return '/api/admin/rag-chunks/' + encodeURIComponent(row.chunkId); }
        },
        capabilityTags: {
            title: '能力标签',
            hint: 'capability_tags：标签、等级描述、别名与典型证据',
            endpoint: '/api/admin/capability-tags',
            columns: [
                col('name', '标签', { sort: 'name' }),
                col('category', '类别', { sort: 'category', render: function (v) { return badge(fmtText(v), v === '技术能力' ? 'warn' : 'muted'); } }),
                col('level1Desc', 'L1', { wrap: true }),
                col('level2Desc', 'L2', { wrap: true }),
                col('level3Desc', 'L3', { wrap: true }),
                col('skillAliasesPreview', '别名摘要', { wrap: true }),
                col('typicalEvidenceLength', '证据长度')
            ],
            filters: [
                { name: 'category', label: '类别', type: 'select', options: [['', '全部'], ['技术能力', '技术能力'], ['通用能力', '通用能力']] },
                { name: 'q', label: '关键字', type: 'text' }
            ],
            detail: function (row) { return '/api/admin/capability-tags/' + encodeURIComponent(row.name); }
        },
        capabilityReference: {
            title: '能力字典',
            hint: 'capability_reference：按 section/ref_key 查询，值与 JSON 安全格式化',
            endpoint: '/api/admin/capability-reference',
            columns: [
                col('id', 'ID', { sort: 'id' }),
                col('section', '字典节', { sort: 'section' }),
                col('refKey', '键', { sort: 'refKey' }),
                col('refValuePreview', '值摘要', { wrap: true }),
                col('refValueLength', '值长度'),
                col('note', '备注', { wrap: true })
            ],
            filters: [
                { name: 'section', label: '字典节', type: 'select', options: [['', '全部'], ['tags_to_merge', 'tags_to_merge'], ['skill_mapping', 'skill_mapping'], ['skill_profiles', 'skill_profiles'], ['role_profiles', 'role_profiles'], ['major_categories', 'major_categories'], ['_meta', '_meta']] },
                { name: 'q', label: '键包含', type: 'text' }
            ],
            detail: function (row) { return '/api/admin/capability-reference/' + row.id; }
        },
        usage: {
            title: '聊天用量',
            hint: 'chat_usage_daily：按用户与日期查询每日请求计数（不可篡改历史）',
            endpoint: '/api/admin/chat-usage',
            columns: [
                col('id', 'ID', { sort: 'id' }),
                col('userId', '用户 ID', { sort: 'userId' }),
                col('username', '用户名'),
                col('usageDate', '日期', { sort: 'usageDate' }),
                col('requestCount', '请求数', { sort: 'requestCount' }),
                col('dailyLimit', '每日额度'),
                col('updatedAt', '更新时间', { render: fmtDate })
            ],
            filters: [
                { name: 'userId', label: '用户 ID', type: 'number' },
                { name: 'usageDate', label: '指定日期', type: 'date' },
                { name: 'dateFrom', label: '开始日期', type: 'date' },
                { name: 'dateTo', label: '结束日期', type: 'date' }
            ]
        }
    };

    const NAV = [
        ['dashboard', '/admin', '总览'],
        ['users', '/admin/user', '用户与额度'],
        ['profiles', '/admin/profile', '用户画像'],
        ['planning', '/admin/planning', 'AI 规划'],
        ['chat', '/admin/chat', 'AI 对话'],
        ['feedback', '/admin/feedback_management', '反馈与错误'],
        ['creditRules', '/admin/credit-rules', '加分规则'],
        ['creditActivities', '/admin/credit-activities', '加分记录'],
        ['resources', '/admin/resources', '资源库'],
        ['rag', '/admin/rag', 'RAG 切片'],
        ['capabilityTags', '/admin/capability-tags', '能力标签'],
        ['capabilityReference', '/admin/capability-reference', '能力字典'],
        ['usage', '/admin/usage', '聊天用量']
    ];

    // -------------------------------------------------------------- rendering

    function renderShell(viewKey, view) {
        const app = document.getElementById('adminApp');
        if (!app) {
            return null;
        }
        clear(app);

        const who = el('span', { class: 'who', text: '…' });
        const header = el('header', { class: 'admin-header' }, [
            el('h1', { text: 'LifeComposer 运维控制台' }),
            el('span', { class: 'spacer' }),
            who,
            el('button', {
                text: '退出登录',
                onclick: async function () {
                    try {
                        await api('/api/users/logout', { method: 'POST' });
                    } catch (ignored) {
                        // Even a failed logout returns to the public site.
                    }
                    window.location.href = '/';
                }
            })
        ]);

        const nav = el('nav', { class: 'admin-nav' }, NAV.map(function (entry) {
            return el('a', {
                href: entry[1],
                text: entry[2],
                class: entry[0] === viewKey ? 'active' : null
            });
        }));

        const main = el('main', { class: 'admin-main' });
        append(app, [header, nav, main]);

        api('/api/users/current').then(function (me) {
            who.textContent = me && me.username ? ('管理员：' + me.username) : '';
        }).catch(function () {
            who.textContent = '';
        });

        return main;
    }

    function renderTitle(main, view) {
        append(main, el('div', { class: 'admin-title' }, [
            el('h2', { text: view.title }),
            view.hint ? el('span', { class: 'hint', text: view.hint }) : null
        ]));
    }

    function renderFilterBar(main, view, state, onApply) {
        if (!view.filters || !view.filters.length) {
            return;
        }
        const bar = el('div', { class: 'filter-bar' });
        view.filters.forEach(function (filter) {
            let input;
            if (filter.type === 'select') {
                input = el('select', null, filter.options.map(function (option) {
                    return el('option', { value: option[0], text: option[1] });
                }));
            } else {
                input = el('input', { type: filter.type || 'text', placeholder: filter.placeholder || '' });
            }
            input.value = state.filters[filter.name] || '';
            input.addEventListener('keydown', function (event) {
                if (event.key === 'Enter') {
                    apply();
                }
            });
            input.dataset.filterName = filter.name;
            append(bar, el('div', { class: 'filter-field' }, [
                el('label', { text: filter.label }),
                input
            ]));
        });

        function apply() {
            const next = {};
            bar.querySelectorAll('[data-filter-name]').forEach(function (input) {
                next[input.dataset.filterName] = input.value.trim();
            });
            onApply(next);
        }

        append(bar, el('button', { class: 'primary', text: '查询', onclick: apply }));
        append(bar, el('button', {
            text: '重置',
            onclick: function () {
                bar.querySelectorAll('[data-filter-name]').forEach(function (input) {
                    input.value = '';
                });
                onApply({});
            }
        }));
        append(main, el('div', { class: 'panel' }, bar));
    }

    function renderStatus(container, message, isError) {
        clear(container);
        container.className = 'status' + (isError ? ' error' : '');
        container.textContent = message;
    }

    function renderDashboard(main, view) {
        const status = el('div', { class: 'status', text: '加载中…' });
        const body = el('div');
        append(main, [status, body]);

        api(view.endpoint).then(function (data) {
            status.remove();
            clear(body);

            const tables = data.tables || {};
            const users = data.users || {};
            const usage = data.chatUsage || {};
            const feedback = data.feedback || {};
            const planning = data.planning || {};

            const cards = [
                ['用户总数', users.total, '管理员 ' + fmtNumber(users.admins) + ' · 封禁 ' + fmtNumber(users.banned) + ' · 有画像 ' + fmtNumber(users.withProfile)],
                ['今日聊天请求', usage.requests, usage.date + ' · 活跃用户 ' + fmtNumber(usage.activeUsers) + ' · 单人日额度 ' + fmtNumber(usage.dailyLimitPerUser)],
                ['未解决反馈', feedback.unresolved, '总计 ' + fmtNumber(feedback.total) + ' · 系统错误未解决 ' + fmtNumber(feedback.unresolvedSystemErrors)],
                ['LLM 失败', planning.failed, '规划记录总计 ' + fmtNumber(planning.total) + '（状态 FAILED）']
            ];
            append(body, el('div', { class: 'card-grid' }, cards.map(function (card) {
                return el('div', { class: 'stat' }, [
                    el('div', { class: 'label', text: card[0] }),
                    el('div', { class: 'value', text: fmtNumber(card[1]) }),
                    el('div', { class: 'sub', text: card[2] })
                ]);
            })));

            append(body, el('h3', { text: '表记录数' }));
            const tableNode = el('table', { class: 'mini-table' }, [
                el('thead', null, el('tr', null, [el('th', { text: '数据表' }), el('th', { text: '记录数' })])),
                el('tbody', null, Object.keys(tables).map(function (name) {
                    return el('tr', null, [el('td', { text: name }), el('td', { text: fmtNumber(tables[name]) })]);
                }))
            ]);
            append(body, el('div', { class: 'panel' }, tableNode));

            append(body, el('h3', { text: 'RAG embedding 状态分布' }));
            append(body, el('div', { class: 'panel' }, el('table', { class: 'mini-table' }, [
                el('thead', null, el('tr', null, [el('th', { text: '状态' }), el('th', { text: '切片数' })])),
                el('tbody', null, (data.embeddingStatus || []).map(function (row) {
                    return el('tr', null, [el('td', { text: fmtText(row.status) }), el('td', { text: fmtNumber(row.count) })]);
                }))
            ])));

            append(body, el('h3', { text: '今日用量 TOP' }));
            append(body, el('div', { class: 'panel' }, el('table', { class: 'mini-table' }, [
                el('thead', null, el('tr', null, [el('th', { text: '用户 ID' }), el('th', { text: '用户名' }), el('th', { text: '请求数' })])),
                el('tbody', null, (usage.topUsersToday || []).map(function (row) {
                    return el('tr', null, [
                        el('td', { text: fmtNumber(row.userId) }),
                        el('td', { text: fmtText(row.username) }),
                        el('td', { text: fmtNumber(row.requestCount) })
                    ]);
                }))
            ])));

            append(body, el('h3', { text: '最近 LLM 失败' }));
            const failures = data.recentLlmFailures || [];
            append(body, el('div', { class: 'panel' }, failures.length
                ? el('table', { class: 'mini-table' }, [
                    el('thead', null, el('tr', null, [
                        el('th', { text: 'ID' }), el('th', { text: '用户' }), el('th', { text: '类型' }),
                        el('th', { text: 'Provider/模型' }), el('th', { text: '时间' }), el('th', { text: '错误' })
                    ])),
                    el('tbody', null, failures.map(function (row) {
                        return el('tr', null, [
                            el('td', { text: fmtNumber(row.id) }),
                            el('td', { text: fmtNumber(row.userId) }),
                            el('td', { text: fmtText(row.type) }),
                            el('td', { text: fmtText(row.provider) + ' / ' + fmtText(row.model) }),
                            el('td', { text: fmtDate(row.createdAt) }),
                            el('td', { text: fmtText(row.errorMessage) })
                        ]);
                    }))
                ])
                : el('div', { class: 'nav-note', text: '暂无失败记录' })));

            append(body, el('div', { class: 'nav-note', text: '生成时间：' + fmtText(data.generatedAt) }));
        }).catch(function (error) {
            renderStatus(status, '加载失败：' + error.message, true);
        });
    }

    function renderTable(main, view, viewKey) {
        const state = { page: 1, pageSize: 50, sort: null, dir: null, filters: {} };

        renderTitle(main, view);
        renderFilterBar(main, view, state, function (filters) {
            state.filters = filters;
            state.page = 1;
            load();
        });

        const status = el('div', { class: 'status', text: '加载中…' });
        const tableWrap = el('div', { class: 'table-wrap' });
        const pager = el('div', { class: 'pager' });
        append(main, el('div', { class: 'panel' }, [status, tableWrap, pager]));

        function buildHeader() {
            const row = el('tr');
            (view.columns || []).forEach(function (column) {
                const sortKey = column.sort;
                const active = sortKey && state.sort === sortKey;
                const th = el('th', {
                    text: column.label + (active ? (state.dir === 'desc' ? ' ▼' : ' ▲') : ''),
                    class: sortKey ? 'sortable' : null,
                    title: sortKey ? '点击排序' : null
                });
                if (sortKey) {
                    th.addEventListener('click', function () {
                        if (state.sort === sortKey) {
                            state.dir = state.dir === 'desc' ? 'asc' : 'desc';
                        } else {
                            state.sort = sortKey;
                            state.dir = 'asc';
                        }
                        state.page = 1;
                        load();
                    });
                }
                row.appendChild(th);
            });
            if (view.actions && view.actions.length) {
                row.appendChild(el('th', { text: '操作' }));
            } else if (view.detail) {
                row.appendChild(el('th', { text: '详情' }));
            }
            return el('thead', null, row);
        }

        function cellContent(column, row) {
            const value = row[column.key];
            let rendered = column.render ? column.render(value, row) : fmtText(value);
            if (rendered === null || rendered === undefined) {
                rendered = '—';
            }
            return rendered;
        }

        async function runAction(action, row) {
            try {
                const message = await action.run(row);
                if (message) {
                    notify(message, false);
                    load();
                }
            } catch (error) {
                notify('操作失败：' + error.message, true);
            }
        }

        function buildRow(row) {
            const tr = el('tr');
            (view.columns || []).forEach(function (column) {
                const td = el('td', { class: column.wrap ? 'wrap' : null });
                const content = cellContent(column, row);
                append(td, content instanceof Node ? content : String(content));
                tr.appendChild(td);
            });

            if (view.actions && view.actions.length) {
                const td = el('td', { class: 'actions' });
                view.actions.forEach(function (action) {
                    const label = typeof action.label === 'function' ? action.label(row) : action.label;
                    td.appendChild(el('button', {
                        class: 'small',
                        text: label,
                        onclick: function () { runAction(action, row); }
                    }));
                });
                tr.appendChild(td);
            } else if (view.detail) {
                const td = el('td', { class: 'actions' });
                td.appendChild(el('button', {
                    class: 'small',
                    text: '详情',
                    onclick: function () { openDetail(view, row); }
                }));
                tr.appendChild(td);
            }
            return tr;
        }

        function renderPager(total, totalPages) {
            clear(pager);
            append(pager, el('button', {
                text: '上一页',
                disabled: state.page <= 1 ? 'disabled' : null,
                onclick: function () { state.page -= 1; load(); }
            }));
            append(pager, el('span', {
                class: 'meta',
                text: '第 ' + state.page + ' / ' + Math.max(1, totalPages) + ' 页 · 共 ' + total + ' 条'
            }));
            append(pager, el('button', {
                text: '下一页',
                disabled: state.page >= totalPages ? 'disabled' : null,
                onclick: function () { state.page += 1; load(); }
            }));
            const sizeSelect = el('select', null, PAGE_SIZES.map(function (size) {
                return el('option', { value: String(size), text: size + ' 条/页' });
            }));
            sizeSelect.value = String(state.pageSize);
            sizeSelect.addEventListener('change', function () {
                state.pageSize = Number(sizeSelect.value);
                state.page = 1;
                load();
            });
            append(pager, sizeSelect);
        }

        async function load() {
            renderStatus(status, '加载中…', false);
            try {
                const query = buildQuery(state);
                const payload = await api(view.endpoint + '?' + query);
                const items = (payload && payload.items) || [];
                clear(tableWrap);
                if (!items.length) {
                    tableWrap.appendChild(el('div', { class: 'status', text: '暂无数据' }));
                } else {
                    tableWrap.appendChild(el('table', { class: 'admin-table' }, [
                        buildHeader(),
                        el('tbody', null, items.map(buildRow))
                    ]));
                }
                status.remove();
                renderPager(payload.total || 0, payload.totalPages || 0);
            } catch (error) {
                clear(tableWrap);
                clear(pager);
                renderStatus(status, '加载失败：' + error.message, true);
            }
        }

        load();
    }

    async function openDetail(view, row) {
        if (!view.detail) {
            return;
        }
        try {
            const data = await api(view.detail(row));
            const entries = Object.keys(data).map(function (key) { return [key, data[key]]; });
            openDrawer(view.title + ' 详情', entries);
        } catch (error) {
            notify('详情加载失败：' + error.message, true);
        }
    }

    // -------------------------------------------------------------------- boot

    function boot() {
        const viewKey = document.body.dataset.adminView;
        const view = VIEWS[viewKey];
        if (!view) {
            const app = document.getElementById('adminApp');
            if (app) {
                clear(app);
                app.appendChild(el('div', { class: 'status error', text: '未知的管理页面：' + fmtText(viewKey) }));
            }
            return;
        }
        const main = renderShell(viewKey, view);
        if (!main) {
            return;
        }
        if (view.kind === 'dashboard') {
            renderTitle(main, view);
            renderDashboard(main, view);
        } else {
            renderTable(main, view, viewKey);
        }
    }

    window.addEventListener('DOMContentLoaded', boot);

    // Exposed for tests/documentation only; contains no privileged behaviour.
    window.LifeComposerAdmin = {
        views: VIEWS,
        formatDate: fmtDate,
        safeUrl: safeUrl,
        prettyJson: prettyJson
    };
})();
