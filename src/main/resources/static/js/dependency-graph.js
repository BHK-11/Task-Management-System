(() => {
    const graph = document.querySelector('[data-dependency-graph]');
    if (!graph) return;
    const svg = graph.querySelector('[data-dependency-graph-svg]');
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    function draw() {
        const rect = graph.getBoundingClientRect();
        const width = Math.max(graph.scrollWidth, rect.width);
        const height = graph.scrollHeight;
        svg.setAttribute('width', String(width));
        svg.setAttribute('height', String(height));
        svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
        svg.querySelectorAll('path[data-link]').forEach(path => path.remove());
        const nodes = new Map([...graph.querySelectorAll('[data-graph-node]')].map(node => [node.dataset.taskId, node]));
        for (const edge of document.querySelectorAll('[data-graph-edge]')) {
            const from = nodes.get(edge.dataset.predecessor);
            const to = nodes.get(edge.dataset.successor);
            if (!from || !to) continue;
            const a = from.getBoundingClientRect();
            const b = to.getBoundingClientRect();
            const x1 = a.right - rect.left, y1 = (a.top + a.bottom) / 2 - rect.top;
            const x2 = b.left - rect.left, y2 = (b.top + b.bottom) / 2 - rect.top;
            const bend = Math.max(30, Math.abs(x2 - x1) * 0.42);
            const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
            path.dataset.link = 'true';
            path.setAttribute('d', `M ${x1} ${y1} C ${x1 + bend} ${y1}, ${x2 - bend} ${y2}, ${x2} ${y2}`);
            if (edge.dataset.critical === 'true') path.classList.add('is-critical');
            svg.append(path);
        }
    }

    if (!reducedMotion && window.Motion && typeof window.Motion.animate === 'function')
        graph.querySelectorAll('[data-graph-node]').forEach((node, index) =>
            window.Motion.animate(node, { opacity: [0, 1], y: [4, 0] },
                { duration: 0.2, delay: Math.min(index * 0.012, 0.24), ease: 'easeOut' }));

    draw();
    if ('ResizeObserver' in window) new ResizeObserver(draw).observe(graph);
    else window.addEventListener('resize', draw, { passive: true });
})();
