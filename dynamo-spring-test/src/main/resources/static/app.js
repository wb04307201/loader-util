// dynamo-spring demo 前端
// 原生 JS + fetch。Tab 1-4 各自的函数互不依赖，按钮 onclick 直接调用。

// ============== Tab 切换 ==============

document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        const tab = btn.dataset.tab;
        document.querySelectorAll('.tab-btn').forEach(b => b.classList.toggle('active', b === btn));
        document.querySelectorAll('.tab').forEach(s => s.classList.toggle('hidden', s.id !== 'tab-' + tab));
        // 切到 runtime tab 时自动拉一次 session 列表
        if (tab === 'runtime') runtimeListSessions();
    });
});

// ============== 通用工具 ==============

async function call(method, url, body) {
    const opts = {method, headers: {'Content-Type': 'application/json'}};
    if (body !== undefined) opts.body = JSON.stringify(body);
    const r = await fetch(url, opts);
    let payload;
    try {
        payload = await r.json();
    } catch (e) {
        payload = {rawText: '<非 JSON 响应>'};
    }
    if (!r.ok) {
        const err = new Error('HTTP ' + r.status);
        err.payload = payload;
        err.status = r.status;
        throw err;
    }
    return payload;
}

function showResult(preId, data) {
    document.getElementById(preId).textContent = JSON.stringify(data, null, 2);
}

function showError(preId, err) {
    document.getElementById(preId).textContent =
        '[ERROR ' + (err.status || '?') + '] ' + (err.message || '') +
        '\n' + JSON.stringify(err.payload || {}, null, 2);
}

function splitArgs(s) {
    if (!s || !s.trim()) return [];
    return s.split(',').map(x => x.trim());
}

// ============== Tab 1: 动态编译 ==============

async function compileLoad() {
    const source = document.getElementById('compile-source').value;
    try {
        const r = await call('POST', '/api/compile/load', {source});
        showResult('compile-result', r);
        await compileListInstances();
        refreshSelect('compile-invoke-id', r.id);
    } catch (e) {
        showError('compile-result', e);
    }
}

async function compileListInstances() {
    try {
        const list = await call('GET', '/api/compile/instances');
        renderInstanceList('compile-instance-list', list, refreshCompileInvokeSelect);
        refreshCompileInvokeSelect(list);
    } catch (e) {
        showError('compile-result', e);
    }
}

function refreshCompileInvokeSelect(list) {
    const items = list || [];
    refreshSelect('compile-invoke-id', null, items.map(i => ({value: i.id, label: i.id + ' (' + i.className + ')'})));
}

async function compileInvoke() {
    const id = document.getElementById('compile-invoke-id').value;
    const methodName = document.getElementById('compile-invoke-method').value;
    const args = splitArgs(document.getElementById('compile-invoke-args').value);
    if (!id) return showError('compile-invoke-result', {message: '请先选择实例'});
    if (!methodName) return showError('compile-invoke-result', {message: '请填方法名'});
    try {
        const r = await call('POST', '/api/compile/invoke', {id, methodName, args});
        showResult('compile-invoke-result', r);
    } catch (e) {
        showError('compile-invoke-result', e);
    }
}

async function compileRemove() {
    const id = document.getElementById('compile-invoke-id').value;
    if (!id) return showError('compile-invoke-result', {message: '请先选择实例'});
    try {
        const r = await call('DELETE', '/api/compile/instances/' + encodeURIComponent(id));
        showResult('compile-invoke-result', r);
        await compileListInstances();
    } catch (e) {
        showError('compile-invoke-result', e);
    }
}

// ============== Tab 2: AOP ==============

async function aspectProxy() {
    const source = document.getElementById('aspect-source').value;
    const adviceType = document.getElementById('aspect-advice-type').value;
    try {
        const r = await call('POST', '/api/aspect/proxy', {source, adviceType});
        showResult('aspect-result', r);
        await aspectListProxies();
        refreshSelect('aspect-invoke-id', r.id);
    } catch (e) {
        showError('aspect-result', e);
    }
}

async function aspectListProxies() {
    try {
        const list = await call('GET', '/api/aspect/proxies');
        renderInstanceList('aspect-proxy-list', list, refreshAspectInvokeSelect);
        refreshAspectInvokeSelect(list);
    } catch (e) {
        showError('aspect-result', e);
    }
}

function refreshAspectInvokeSelect(list) {
    const items = list || [];
    refreshSelect('aspect-invoke-id', null, items.map(i => ({value: i.id, label: i.id + ' (' + i.className + ')'})));
}

async function aspectInvoke() {
    const id = document.getElementById('aspect-invoke-id').value;
    const methodName = document.getElementById('aspect-invoke-method').value;
    const args = splitArgs(document.getElementById('aspect-invoke-args').value);
    if (!id) return showError('aspect-invoke-result', {message: '请先选择代理'});
    if (!methodName) return showError('aspect-invoke-result', {message: '请填方法名'});
    try {
        const r = await call('POST', '/api/aspect/invoke', {id, methodName, args});
        showResult('aspect-invoke-result', r);
    } catch (e) {
        showError('aspect-invoke-result', e);
    }
}

async function aspectLogs() {
    const id = document.getElementById('aspect-invoke-id').value;
    if (!id) return showError('aspect-invoke-result', {message: '请先选择代理'});
    try {
        const r = await call('GET', '/api/aspect/proxies/' + encodeURIComponent(id) + '/logs');
        showResult('aspect-invoke-result', r);
    } catch (e) {
        showError('aspect-invoke-result', e);
    }
}

async function aspectRemove() {
    const id = document.getElementById('aspect-invoke-id').value;
    if (!id) return showError('aspect-invoke-result', {message: '请先选择代理'});
    try {
        const r = await call('DELETE', '/api/aspect/proxies/' + encodeURIComponent(id));
        showResult('aspect-invoke-result', r);
        await aspectListProxies();
    } catch (e) {
        showError('aspect-invoke-result', e);
    }
}

// ============== Tab 3: 动态 Controller ==============

async function beanRegister() {
    const beanName = document.getElementById('bean-name').value;
    const source = document.getElementById('bean-source').value;
    try {
        const r = await call('POST', '/api/bean/register', {beanName, source});
        showResult('bean-result', r);
        await beanList();
    } catch (e) {
        showError('bean-result', e);
    }
}

async function beanList() {
    try {
        const r = await call('GET', '/api/bean/list');
        showResult('bean-result', r);
        renderBeanList(r.controllers || []);
    } catch (e) {
        showError('bean-result', e);
    }
}

function renderBeanList(controllers) {
    const ul = document.getElementById('bean-list');
    ul.innerHTML = '';
    const routesUl = document.getElementById('bean-routes');
    routesUl.innerHTML = '';
    controllers.forEach(c => {
        const li = document.createElement('li');
        li.innerHTML = '<strong>' + c.beanName + '</strong> · ' + c.className +
            ' <button class="mini" onclick="beanUnregister(\'' + c.beanName + '\')">注销</button>';
        ul.appendChild(li);
        (c.routes || []).forEach(rt => {
            const rli = document.createElement('li');
            const url = rt.path;
            rli.innerHTML =
                '<a href="' + url + '" target="_blank">' + rt.method + ' ' + url + '</a>' +
                ' <button class="mini" onclick="fetchAndShow(\'' + url + '\', \'bean-result\')">GET 试一下</button>';
            routesUl.appendChild(rli);
        });
    });
}

async function beanUnregister(beanName) {
    try {
        const r = await call('POST', '/api/bean/unregister', {beanName});
        showResult('bean-result', r);
        await beanList();
    } catch (e) {
        showError('bean-result', e);
    }
}

async function fetchAndShow(url, preId) {
    try {
        const r = await fetch(url);
        const text = await r.text();
        document.getElementById(preId).textContent =
            'GET ' + url + '\nHTTP ' + r.status + '\n\n' + text;
    } catch (e) {
        showError(preId, {message: e.message, payload: {url}});
    }
}

// ============== Tab 4: DynamicRuntime ==============

async function runtimeCreateSession() {
    try {
        const r = await call('POST', '/api/runtime/sessions');
        showResult('runtime-state', r);
        await runtimeListSessions();
        refreshSelect('runtime-session-id', r.sessionId);
    } catch (e) {
        showError('runtime-state', e);
    }
}

async function runtimeListSessions() {
    try {
        const list = await call('GET', '/api/runtime/sessions');
        refreshSelect('runtime-session-id', null, list.map(id => ({value: id, label: id})));
        const sid = document.getElementById('runtime-session-id').value;
        if (sid) await runtimeGetState();
    } catch (e) {
        showError('runtime-state', e);
    }
}

async function runtimeCloseSession() {
    const id = document.getElementById('runtime-session-id').value;
    if (!id) return showError('runtime-state', {message: '请先选择 session'});
    try {
        const r = await call('DELETE', '/api/runtime/sessions/' + encodeURIComponent(id));
        showResult('runtime-state', r);
        await runtimeListSessions();
    } catch (e) {
        showError('runtime-state', e);
    }
}

async function runtimeGetState() {
    const id = document.getElementById('runtime-session-id').value;
    if (!id) return;
    try {
        const r = await call('GET', '/api/runtime/sessions/' + encodeURIComponent(id) + '/state');
        showResult('runtime-state', r);
        refreshSelect('runtime-class-id', null, (r.classes || []).map(c => ({value: c.id, label: c.id + ' (' + c.className + ')'})));
        refreshSelect('runtime-instance-id', null, (r.instances || []).map(i => ({value: i.id, label: i.id + ' (' + i.className + ')'})));
    } catch (e) {
        showError('runtime-state', e);
    }
}

async function runtimeCompile() {
    const id = document.getElementById('runtime-session-id').value;
    const source = document.getElementById('runtime-source').value;
    if (!id) return showError('runtime-compile-result', {message: '请先选 session'});
    try {
        const r = await call('POST', '/api/runtime/sessions/' + encodeURIComponent(id) + '/compile', {source});
        showResult('runtime-compile-result', r);
        await runtimeGetState();
        refreshSelect('runtime-class-id', r.classId);
    } catch (e) {
        showError('runtime-compile-result', e);
    }
}

async function runtimeInstantiate() {
    const id = document.getElementById('runtime-session-id').value;
    const classId = document.getElementById('runtime-class-id').value;
    if (!id) return showError('runtime-invoke-result', {message: '请先选 session'});
    if (!classId) return showError('runtime-invoke-result', {message: '请先选 classId'});
    try {
        const r = await call('POST', '/api/runtime/sessions/' + encodeURIComponent(id) + '/instantiate', {classId});
        showResult('runtime-invoke-result', r);
        await runtimeGetState();
        refreshSelect('runtime-instance-id', r.instanceId);
    } catch (e) {
        showError('runtime-invoke-result', e);
    }
}

async function runtimeInvoke() {
    const id = document.getElementById('runtime-session-id').value;
    const instanceId = document.getElementById('runtime-instance-id').value;
    const methodName = document.getElementById('runtime-invoke-method').value;
    if (!id) return showError('runtime-invoke-result', {message: '请先选 session'});
    if (!instanceId) return showError('runtime-invoke-result', {message: '请先选 instanceId'});
    if (!methodName) return showError('runtime-invoke-result', {message: '请填方法名'});
    try {
        const r = await call('POST', '/api/runtime/sessions/' + encodeURIComponent(id) + '/invoke',
            {instanceId, methodName, args: []});
        showResult('runtime-invoke-result', r);
    } catch (e) {
        showError('runtime-invoke-result', e);
    }
}

async function runtimeRegisterController() {
    const id = document.getElementById('runtime-session-id').value;
    const beanName = document.getElementById('runtime-bean-name').value;
    const source = document.getElementById('runtime-controller-source').value;
    if (!id) return showError('runtime-register-result', {message: '请先选 session'});
    try {
        const r = await call('POST', '/api/runtime/sessions/' + encodeURIComponent(id) + '/registerController',
            {beanName, source});
        showResult('runtime-register-result', r);
        await runtimeGetState();
    } catch (e) {
        showError('runtime-register-result', e);
    }
}

async function runtimeUnregisterController() {
    const id = document.getElementById('runtime-session-id').value;
    const beanName = document.getElementById('runtime-unreg-name').value;
    if (!id) return showError('runtime-unreg-result', {message: '请先选 session'});
    if (!beanName) return showError('runtime-unreg-result', {message: '请填 beanName'});
    try {
        const r = await call('POST', '/api/runtime/sessions/' + encodeURIComponent(id) + '/unregisterController',
            {beanName});
        showResult('runtime-unreg-result', r);
        await runtimeGetState();
    } catch (e) {
        showError('runtime-unreg-result', e);
    }
}

// session select 变化时自动刷新 state
document.addEventListener('DOMContentLoaded', () => {
    const sel = document.getElementById('runtime-session-id');
    if (sel) sel.addEventListener('change', runtimeGetState);
});

// ============== 通用 DOM 工具 ==============

function refreshSelect(id, selectedValue, options) {
    const sel = document.getElementById(id);
    if (!sel) return;
    if (!options) {
        // 只设置选中值（保持现有 options）
        if (selectedValue !== null && selectedValue !== undefined) sel.value = selectedValue;
        return;
    }
    sel.innerHTML = '';
    options.forEach(o => {
        const opt = document.createElement('option');
        opt.value = o.value;
        opt.textContent = o.label;
        sel.appendChild(opt);
    });
    if (selectedValue !== null && selectedValue !== undefined) sel.value = selectedValue;
}

function renderInstanceList(ulId, items, onChange) {
    const ul = document.getElementById(ulId);
    ul.innerHTML = '';
    if (!items || items.length === 0) {
        const li = document.createElement('li');
        li.textContent = '（暂无）';
        li.style.color = '#8c959f';
        ul.appendChild(li);
        return;
    }
    items.forEach(it => {
        const li = document.createElement('li');
        li.textContent = it.id + ' · ' + it.className;
        ul.appendChild(li);
    });
    if (typeof onChange === 'function') onChange(items);
}
