(() => {
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (!reduced && window.Motion && typeof window.Motion.animate === 'function') {
    document.querySelectorAll('.time-panel, .time-summary-card, .focus-session-card').forEach((item, index) => {
      window.Motion.animate(item, { opacity: [0, 1], y: [5, 0] }, { duration: 0.24, delay: Math.min(index * 0.025, 0.16), ease: 'easeOut' });
    });
  }

  const format = (seconds) => {
    const safe = Math.max(0, Math.floor(seconds));
    const hours = Math.floor(safe / 3600);
    const minutes = Math.floor((safe % 3600) / 60);
    const remainder = safe % 60;
    return hours > 0
      ? `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(remainder).padStart(2, '0')}`
      : `${String(minutes).padStart(2, '0')}:${String(remainder).padStart(2, '0')}`;
  };

  document.querySelectorAll('[data-stopwatch]').forEach((clock) => {
    let seconds = Number(clock.dataset.seconds || 0);
    clock.textContent = format(seconds);
    if (clock.dataset.running !== 'true') return;
    const started = Date.now();
    window.setInterval(() => {
      clock.textContent = format(seconds + Math.floor((Date.now() - started) / 1000));
    }, 1000);
  });
})();
