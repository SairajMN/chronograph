/* ── State ──────────────────────────────────────────────────────────── */

const state = {
    events: [],
    nodes: new Set(),
    edges: [],
    currentTime: 0,
    selectedNode: null,
};

const API = window.location.origin;

/* ── DOM refs ────────────────────────────────────────────────────────── */

const $ = (id) => document.getElementById(id);
const timeline = $('timeline');
const timelineTrack = $('timeline-track');
const timelineTicks = $('timeline-ticks');
const timelineLabels = $('timeline-labels');
const timelineIndicator = $('timeline-indicator');
const timeInput = $('time-input');
const currentTimeLabel = $('current-time-label');
const graphTimeLabel = $('graph-time-label');
const graphSvg = $('graph-svg');
const graphLoading = $('graph-loading');
const graphEmpty = $('graph-empty');
const nodeCount = $('node-count');
const eventList = $('event-list');
const eventCount = $('event-count');
const nodeList = $('node-list');
const whyNode = $('why-node');
const whyResult = $('why-result');
const impactResult = $('impact-result');
const diffT1 = $('diff-t1');
const diffT2 = $('diff-t2');
const diffResult = $('diff-result');
const toast = $('toast');

/* ── Init ─────────────────────────────────────────────────────────────── */

async function init() {
    await fetchEvents();
    populateTimeline();
    await loadGraph(state.currentTime);
}

/* ── API ──────────────────────────────────────────────────────────────── */

async function fetchEvents() {
    try {
        var res = await fetch(API + '/graph/snapshot?time=' + Number.MAX_SAFE_INTEGER);
        if (!res.ok) return;
        var data = await res.json();
        // We need the event log — fetch replay via snapshot at max time to get all edges,
        // then reconstruct event-like items from the edges
        // Actually we fetch events by hitting /graph/snapshot incrementally or via POST
        // For now, extract nodes and edges from snapshot
        state.edges = data.edges || [];
        var ns = new Set();
        state.edges.forEach(function (e) { ns.add(e.from); ns.add(e.to); });
        state.nodes = ns;
        // Build synthetic events from edges for display
        state.events = state.edges.map(function (e, i) {
            return { ts: e.validFrom, type: 'add', src: e.from, dst: e.to, id: i };
        });
        state.events.sort(function (a, b) { return a.ts - b.ts; });
    } catch (e) { console.warn('fetch events:', e); }
}

async function fetchSnapshot(time) {
    var res = await fetch(API + '/graph/snapshot?time=' + time);
    if (!res.ok) throw new Error('snapshot fetch failed');
    return await res.json();
}

async function fetchWhy(node, time) {
    var res = await fetch(API + '/graph/why?node=' + encodeURIComponent(node) + '&time=' + time);
    if (!res.ok) throw new Error('why fetch failed');
    return await res.json();
}

async function fetchImpact(node, time) {
    var res = await fetch(API + '/graph/impact?node=' + encodeURIComponent(node) + '&time=' + time);
    if (!res.ok) throw new Error('impact fetch failed');
    return await res.json();
}

async function fetchDiff(t1, t2) {
    var res = await fetch(API + '/graph/diff?t1=' + t1 + '&t2=' + t2);
    if (!res.ok) throw new Error('diff fetch failed');
    return await res.json();
}

/* ── Timeline ─────────────────────────────────────────────────────────── */

function populateTimeline() {
    if (state.events.length === 0) return;
    var maxTs = state.events[state.events.length - 1].ts;
    var minTs = Math.max(0, state.events[0].ts - 10);
    var range = maxTs - minTs || 1;

    // Ticks
    timelineTicks.innerHTML = '';
    timelineLabels.innerHTML = '';
    state.events.forEach(function (e) {
        var pct = ((e.ts - minTs) / range) * 100;
        var tick = document.createElement('div');
        tick.className = 'tick-mark';
        tick.style.left = pct + '%';
        timelineTicks.appendChild(tick);

        var label = document.createElement('div');
        label.className = 'tick-label';
        label.textContent = 'T' + state.events.indexOf(e);
        label.style.left = pct + '%';
        timelineLabels.appendChild(label);
    });

    // Set slider attributes
    timeline.setAttribute('aria-valuemin', minTs);
    timeline.setAttribute('aria-valuemax', maxTs);
    timeline.setAttribute('aria-valuenow', state.currentTime);
}

function setTime(t, snap) {
    state.currentTime = Math.max(0, t);
    if (state.events.length > 0) {
        var maxTs = state.events[state.events.length - 1].ts;
        state.currentTime = Math.min(state.currentTime, maxTs);
    }
    timeInput.value = state.currentTime;
    currentTimeLabel.textContent = state.currentTime;

    // Position indicator
    if (state.events.length > 0) {
        var maxTs = state.events[state.events.length - 1].ts;
        var minTs = Math.max(0, state.events[0].ts - 10);
        var range = maxTs - minTs || 1;
        var pct = ((state.currentTime - minTs) / range) * 100;
        timelineIndicator.style.left = Math.max(0, Math.min(100, pct)) + '%';
    }

    graphTimeLabel.textContent = state.currentTime;

    if (!snap) loadGraph(state.currentTime);
}

function timelinePosition(e) {
    var rect = timeline.getBoundingClientRect();
    var x = (e.clientX || e.touches && e.touches[0].clientX) - rect.left;
    var pct = Math.max(0, Math.min(1, x / rect.width));
    if (state.events.length === 0) return 0;
    var maxTs = state.events[state.events.length - 1].ts;
    var minTs = Math.max(0, state.events[0].ts - 10);
    return minTs + pct * (maxTs - minTs);
}

// Click on timeline
timeline.addEventListener('click', function (e) {
    var t = Math.round(timelinePosition(e));
    setTime(t);
});

// Drag
var dragging = false;
timeline.addEventListener('mousedown', function (e) { dragging = true; });
document.addEventListener('mousemove', function (e) {
    if (!dragging) return;
    var t = Math.round(timelinePosition(e));
    setTime(t);
});
document.addEventListener('mouseup', function () { dragging = false; });

// Touch
timeline.addEventListener('touchstart', function (e) { dragging = true; });
document.addEventListener('touchmove', function (e) {
    if (!dragging) return;
    var t = Math.round(timelinePosition(e));
    setTime(t);
});
document.addEventListener('touchend', function () { dragging = false; });

// Keyboard on timeline
timeline.addEventListener('keydown', function (e) {
    if (e.key === 'ArrowRight') { setTime(state.currentTime + 5); e.preventDefault(); }
    if (e.key === 'ArrowLeft') { setTime(state.currentTime - 5); e.preventDefault(); }
});

// Time input
timeInput.addEventListener('change', function () { setTime(parseInt(this.value) || 0); });

// Prev/next event buttons
$('btn-prev').addEventListener('click', function () {
    var prev = state.events.filter(function (e) { return e.ts < state.currentTime; });
    if (prev.length) setTime(prev[prev.length - 1].ts);
});
$('btn-next').addEventListener('click', function () {
    var next = state.events.filter(function (e) { return e.ts > state.currentTime; });
    if (next.length) setTime(next[0].ts);
});

/* ── Graph rendering ────────────────────────────────────────────────── */

function simpleForceLayout(nodes, edges, width, height) {
    var positions = {};
    var n = nodes.length;
    if (n === 0) return positions;
    if (n === 1) {
        positions[nodes[0]] = { x: width / 2, y: height / 2 };
        return positions;
    }

    // Arrange in a circle initially
    var angleStep = (2 * Math.PI) / n;
    nodes.forEach(function (node, i) {
        positions[node] = { x: width / 2 + Math.cos(angleStep * i) * (width * 0.3), y: height / 2 + Math.sin(angleStep * i) * (height * 0.3), vx: 0, vy: 0 };
    });

    // Simple force iteration
    var iterations = 60;
    var repulsion = 2000;
    var attraction = 0.005;
    var damping = 0.9;

    for (var iter = 0; iter < iterations; iter++) {
        // Repulsion
        for (var i = 0; i < n; i++) {
            for (var j = i + 1; j < n; j++) {
                var a = nodes[i], b = nodes[j];
                var dx = positions[a].x - positions[b].x;
                var dy = positions[a].y - positions[b].y;
                var dist = Math.sqrt(dx * dx + dy * dy) || 1;
                var force = repulsion / (dist * dist);
                positions[a].vx = (positions[a].vx || 0) + (dx / dist) * force;
                positions[a].vy = (positions[a].vy || 0) + (dy / dist) * force;
                positions[b].vx = (positions[b].vx || 0) - (dx / dist) * force;
                positions[b].vy = (positions[b].vy || 0) - (dy / dist) * force;
            }
        }
        // Attraction along edges
        edges.forEach(function (e) {
            var p1 = positions[e.from], p2 = positions[e.to];
            if (!p1 || !p2) return;
            var dx = p2.x - p1.x;
            var dy = p2.y - p1.y;
            p1.vx = (p1.vx || 0) + dx * attraction;
            p1.vy = (p1.vy || 0) + dy * attraction;
            p2.vx = (p2.vx || 0) - dx * attraction;
            p2.vy = (p2.vy || 0) - dy * attraction;
        });
        // Apply + center
        var cx = width / 2, cy = height / 2;
        nodes.forEach(function (node) {
            var p = positions[node];
            p.vx = (p.vx || 0) * damping;
            p.vy = (p.vy || 0) * damping;
            p.x += p.vx;
            p.y += p.vy;
            // Gentle centering force
            p.x += (cx - p.x) * 0.01;
            p.y += (cy - p.y) * 0.01;
            // Clamp
            p.x = Math.max(20, Math.min(width - 20, p.x));
            p.y = Math.max(20, Math.min(height - 20, p.y));
        });
    }
    return positions;
}

async function loadGraph(time) {
    graphLoading.classList.remove('hidden');
    graphEmpty.classList.add('hidden');

    try {
        var data = await fetchSnapshot(time);
        var edges = data.edges || [];
        var ns = new Set();
        edges.forEach(function (e) { ns.add(e.from); ns.add(e.to); });
        var nodes = Array.from(ns);

        var svg = graphSvg;
        var rect = svg.parentElement.getBoundingClientRect();
        var width = Math.max(rect.width - 2, 200);
        var height = Math.max(rect.height - 2, 200);
        svg.setAttribute('viewBox', '0 0 ' + width + ' ' + height);

        // Defs for arrowhead
        var defs = svg.querySelector('defs') || (function () {
            var d = document.createElementNS('http://www.w3.org/2000/svg', 'defs');
            svg.appendChild(d);
            return d;
        })();
        defs.innerHTML = '<marker id="arrowhead" viewBox="0 0 10 10" refX="18" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M 0 0 L 10 5 L 0 10 z" fill="#363B48"/></marker>';

        // Compute layout
        var positions = simpleForceLayout(nodes, edges, width, height);

        // Draw edges
        var edgeG = svg.querySelector('.edge-group') || (function () {
            var g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
            g.classList.add('edge-group');
            svg.appendChild(g);
            return g;
        })();
        edgeG.innerHTML = '';
        edges.forEach(function (e) {
            var p1 = positions[e.from], p2 = positions[e.to];
            if (!p1 || !p2) return;
            var line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
            line.setAttribute('x1', p1.x);
            line.setAttribute('y1', p1.y);
            line.setAttribute('x2', p2.x);
            line.setAttribute('y2', p2.y);
            line.classList.add('graph-edge'); // style via class
            var g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
            g.classList.add('graph-edge');
            g.appendChild(line);
            edgeG.appendChild(g);
        });

        // Draw nodes
        var nodeG = svg.querySelector('.node-group') || (function () {
            var g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
            g.classList.add('node-group');
            svg.appendChild(g);
            return g;
        })();
        nodeG.innerHTML = '';
        nodes.forEach(function (node) {
            var p = positions[node];
            if (!p) return;
            var g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
            g.classList.add('graph-node');
            if (node === state.selectedNode) g.classList.add('selected');
            g.dataset.node = node;

            var circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
            circle.setAttribute('cx', p.x);
            circle.setAttribute('cy', p.y);
            circle.setAttribute('r', 16);
            g.appendChild(circle);

            var text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
            text.setAttribute('x', p.x);
            text.setAttribute('y', p.y);
            text.textContent = node;
            g.appendChild(text);

            // Click to select and show impact
            g.addEventListener('click', function () {
                selectNode(node);
            });

            nodeG.appendChild(g);
        });

        // Update counts
        nodeCount.textContent = nodes.length + ' nodes, ' + edges.length + ' edges';
        graphLoading.classList.add('hidden');
        if (nodes.length === 0) graphEmpty.classList.remove('hidden');

        // Update node datalist
        nodeList.innerHTML = nodes.map(function (n) { return '<option value="' + n + '">'; }).join('');
    } catch (e) {
        console.warn('loadGraph:', e);
        graphLoading.classList.add('hidden');
    }
}

function selectNode(node) {
    state.selectedNode = node;
    // Deselect all, select this one
    document.querySelectorAll('.graph-node.selected').forEach(function (el) { el.classList.remove('selected'); });
    document.querySelectorAll('.graph-node').forEach(function (el) {
        if (el.dataset.node === node) el.classList.add('selected');
    });
    whyNode.value = node;
    showWhy(node);
    showImpact(node);
}

/* ── Why ──────────────────────────────────────────────────────────────── */

async function showWhy(node, time) {
    time = time !== undefined ? time : state.currentTime;
    whyResult.innerHTML = '<div class="loading-overlay" style="position:static;background:transparent;padding:12px;">tracing…</div>';
    try {
        var data = await fetchWhy(node, time);
        var path = data.path || [];
        var html = '<div class="cause-label">root cause</div>';
        html += '<div class="root-cause-value">' + escapeHtml(data.root_cause || '—') + '</div>';
        html += '<div class="causal-path" style="margin-top:8px;">';
        path.forEach(function (n, i) {
            if (i > 0) html += '<div class="causal-step"><span class="causal-step-num">' + (i + 1) + '</span><span class="causal-step-arrow">→</span>';
            else html += '<div class="causal-step"><span class="causal-step-num">' + (i + 1) + '</span>';
            html += '<span class="causal-step-node' + (i === 0 ? ' root' : i === path.length - 1 ? ' target' : '') + '">' + escapeHtml(n) + '</span></div>';
        });
        html += '</div>';
        whyResult.innerHTML = html;
    } catch (e) {
        whyResult.innerHTML = '<div class="empty-state small"><p>No path found between these nodes at this time.</p></div>';
    }
}

$('btn-why').addEventListener('click', function () {
    var node = whyNode.value.trim();
    if (!node) return;
    showWhy(node);
});

/* ── Impact ───────────────────────────────────────────────────────────── */

async function showImpact(node, time) {
    time = time !== undefined ? time : state.currentTime;
    impactResult.innerHTML = '<div class="empty-state small"><p>loading…</p></div>';
    try {
        var data = await fetchImpact(node, time);
        var nodes = data.impacted_nodes || [];
        impactResult.innerHTML = '<div class="impact-count">' + nodes.length + ' node' + (nodes.length !== 1 ? 's' : '') + ' impacted</div>';
        if (nodes.length === 0) {
            impactResult.innerHTML += '<div class="empty-state small"><p>No downstream impact from this node.</p></div>';
        } else {
            impactResult.innerHTML += nodes.map(function (n) { return '<span class="impact-node">' + escapeHtml(n) + '</span>'; }).join('');
        }
    } catch (e) {
        impactResult.innerHTML = '<div class="empty-state small"><p>Could not load impact data.</p></div>';
    }
}

/* ── Diff ─────────────────────────────────────────────────────────────── */

async function showDiff(t1, t2) {
    diffResult.innerHTML = '<div class="empty-state small"><p>computing…</p></div>';
    try {
        var data = await fetchDiff(t1, t2);
        var added = data.edges_added || [];
        var removed = data.edges_removed || [];
        var html = '';
        html += '<div class="diff-section"><div class="diff-label">added (' + added.length + ')</div>';
        if (added.length === 0) html += '<div class="diff-none">none</div>';
        else html += added.map(function (e) { return '<div class="diff-edge diff-added">' + escapeHtml(e.from) + ' → ' + escapeHtml(e.to) + '</div>'; }).join('');
        html += '</div>';
        html += '<div class="diff-section"><div class="diff-label">removed (' + removed.length + ')</div>';
        if (removed.length === 0) html += '<div class="diff-none">none</div>';
        else html += removed.map(function (e) { return '<div class="diff-edge diff-removed">' + escapeHtml(e.from) + ' → ' + escapeHtml(e.to) + '</div>'; }).join('');
        html += '</div>';
        diffResult.innerHTML = html;
    } catch (e) {
        diffResult.innerHTML = '<div class="empty-state small"><p>Could not compute diff.</p></div>';
    }
}

$('btn-diff').addEventListener('click', function () {
    var t1 = parseInt(diffT1.value) || 0;
    var t2 = parseInt(diffT2.value) || 0;
    showDiff(t1, t2);
});

/* ── Event feed ───────────────────────────────────────────────────────── */

function renderEventFeed() {
    if (state.events.length === 0) return;
    var html = state.events.map(function (e) {
        return '<div class="event-item">' +
            '<span class="event-ts">T' + e.ts + '</span>' +
            '<span class="event-type">' + escapeHtml(e.type) + '</span>' +
            '<span class="event-src">' + escapeHtml(e.src) + '</span>' +
            '<span class="event-arrow">→</span>' +
            '<span class="event-dst">' + escapeHtml(e.dst) + '</span>' +
            '</div>';
    }).join('');
    eventList.innerHTML = html;
    eventCount.textContent = state.events.length + ' events';
}

/* ── Helpers ──────────────────────────────────────────────────────────── */

function escapeHtml(s) {
    if (s == null) return '';
    return String(s).replace(/&/g, '&').replace(/</g, '<').replace(/>/g, '>').replace(/"/g, '"');
}

function showToast(msg) {
    toast.textContent = msg;
    toast.classList.remove('hidden');
    setTimeout(function () { toast.classList.add('hidden'); }, 3000);
}

/* ── Start ────────────────────────────────────────────────────────────── */

init().then(function () {
    renderEventFeed();
    if (state.events.length > 0) {
        setTime(state.events[state.events.length - 1].ts);
    }
});