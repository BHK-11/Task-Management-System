(() => {
    const chart = document.querySelector('[data-gantt-chart]');
    if (!chart) return;

    const scroll = chart.closest('[data-gantt-scroll]');
    const body = chart.querySelector('[data-gantt-body]');
    const svg = chart.querySelector('[data-dependency-layer]');
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const dayWidths = { day: 26, week: 7, month: 2.4 };
    const dayCount = Number(chart.dataset.axisDays);
    let scale = 'week';
    let dependencyLinesVisible = true;
    let activeDrag = null;

    function datePlusDays(value, days) {
        const date = new Date(`${value}T00:00:00Z`);
        date.setUTCDate(date.getUTCDate() + days);
        return date.toISOString().slice(0, 10);
    }

    chart.querySelectorAll('[data-gantt-bar]').forEach(bar => {
        bar.addEventListener('pointerdown', event => {
            if (event.button !== 0 || !bar.closest('.gantt-lane')) return;
            activeDrag = { bar, pointerId: event.pointerId, startX: event.clientX, deltaX: 0, moved: false };
            bar.setPointerCapture(event.pointerId);
        });
        bar.addEventListener('pointermove', event => {
            if (!activeDrag || activeDrag.bar !== bar || activeDrag.pointerId !== event.pointerId) return;
            activeDrag.deltaX = event.clientX - activeDrag.startX;
            if (!activeDrag.moved && Math.abs(activeDrag.deltaX) < 5) return;
            activeDrag.moved = true;
            bar.classList.add('is-dragging');
            bar.style.translate = `${activeDrag.deltaX}px 0`;
        });
        bar.addEventListener('pointerup', event => {
            if (!activeDrag || activeDrag.bar !== bar || activeDrag.pointerId !== event.pointerId) return;
            const drag = activeDrag;
            activeDrag = null;
            bar.classList.remove('is-dragging');
            bar.style.translate = '';
            if (!drag.moved) return;
            event.preventDefault();
            bar.dataset.ignoreClick = 'true';
            window.setTimeout(() => delete bar.dataset.ignoreClick, 300);
            const dayWidth = Number.parseFloat(getComputedStyle(chart).getPropertyValue('--day-width'));
            const deltaDays = Math.round(drag.deltaX / dayWidth);
            if (!deltaDays) return;
            const form = bar.closest('[data-gantt-row]').querySelector('[data-gantt-reschedule]');
            if (!form) return;
            form.querySelector('[name="plannedStart"]').value = datePlusDays(bar.dataset.start, deltaDays);
            form.requestSubmit();
        });
        bar.addEventListener('pointercancel', () => {
            if (!activeDrag || activeDrag.bar !== bar) return;
            activeDrag = null;
            bar.classList.remove('is-dragging');
            bar.style.translate = '';
        });
        bar.addEventListener('click', event => {
            if (bar.dataset.ignoreClick === 'true') event.preventDefault();
        });
    });

    function resizePlot() {
        const labelWidth = chart.querySelector('.gantt-task-column').getBoundingClientRect().width;
        const plotWidth = Math.min(40000, Math.max(640, dayCount * dayWidths[scale]));
        chart.style.setProperty('--plot-width', `${plotWidth}px`);
        chart.style.setProperty('--day-width', `${plotWidth / dayCount}px`);
        if (svg) {
            svg.setAttribute('width', String(labelWidth + plotWidth));
            svg.setAttribute('height', String(body.scrollHeight));
            svg.setAttribute('viewBox', `0 0 ${labelWidth + plotWidth} ${body.scrollHeight}`);
            drawDependencyLines();
        }
    }

    function drawDependencyLines() {
        if (!svg || !body) return;
        svg.querySelectorAll('path[data-link]').forEach(path => path.remove());
        if (!dependencyLinesVisible) return;
        const bodyRect = body.getBoundingClientRect();
        const bars = new Map([...chart.querySelectorAll('[data-gantt-bar]')].map(bar => [bar.dataset.taskId, bar]));
        for (const edge of chart.querySelectorAll('[data-gantt-edge]')) {
            const predecessor = bars.get(edge.dataset.predecessor);
            const successor = bars.get(edge.dataset.successor);
            if (!predecessor || !successor) continue;

            const fromFinish = edge.dataset.type[0] === 'F';
            const toFinish = edge.dataset.type[1] === 'F';
            const fromRect = predecessor.getBoundingClientRect();
            const toRect = successor.getBoundingClientRect();
            const x1 = (fromFinish ? fromRect.right : fromRect.left) - bodyRect.left;
            const y1 = (fromRect.top + fromRect.bottom) / 2 - bodyRect.top;
            const x2 = (toFinish ? toRect.right : toRect.left) - bodyRect.left;
            const y2 = (toRect.top + toRect.bottom) / 2 - bodyRect.top;
            const bend = Math.max(14, Math.min(44, Math.abs(x2 - x1) / 2));
            const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
            path.dataset.link = 'true';
            path.setAttribute('d', `M ${x1} ${y1} C ${x1 + bend} ${y1}, ${x2 - bend} ${y2}, ${x2} ${y2}`);
            path.setAttribute('marker-end', edge.dataset.critical === 'true' ? 'url(#gantt-arrow-critical)' : 'url(#gantt-arrow)');
            if (edge.dataset.critical === 'true') path.classList.add('is-critical');
            svg.append(path);
        }
    }

    document.querySelectorAll('[data-gantt-scale]').forEach(button => {
        button.addEventListener('click', () => {
            scale = button.dataset.ganttScale;
            document.querySelectorAll('[data-gantt-scale]').forEach(item =>
                item.setAttribute('aria-pressed', String(item === button)));
            resizePlot();
        });
    });

    const toggle = document.querySelector('[data-toggle-dependencies]');
    if (toggle) toggle.addEventListener('click', () => {
        dependencyLinesVisible = !dependencyLinesVisible;
        toggle.setAttribute('aria-pressed', String(dependencyLinesVisible));
        toggle.textContent = dependencyLinesVisible ? 'Hide dependency lines' : 'Show dependency lines';
        drawDependencyLines();
    });

    if (!reducedMotion && window.Motion && typeof window.Motion.animate === 'function') {
        chart.querySelectorAll('[data-gantt-bar]').forEach((bar, index) => {
            window.Motion.animate(bar, { opacity: [0, 1], y: [3, 0] },
                { duration: 0.2, delay: Math.min(index * 0.012, 0.24), ease: 'easeOut' });
        });
    }

    resizePlot();
    if ('ResizeObserver' in window) new ResizeObserver(resizePlot).observe(scroll);
    else window.addEventListener('resize', resizePlot, { passive: true });
})();
