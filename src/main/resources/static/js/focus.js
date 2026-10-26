(() => {
  const timer = document.querySelector('[data-focus-timer]');
  const clock = document.querySelector('[data-focus-countdown]');
  const form = document.querySelector('[data-focus-complete-form]');
  const complete = document.querySelector('[data-focus-complete-button]');
  if (!timer || !clock || !form || !complete) return;

  const format = (seconds) => {
    const safe = Math.max(0, Math.floor(seconds));
    return `${String(Math.floor(safe / 60)).padStart(2, '0')}:${String(safe % 60).padStart(2, '0')}`;
  };
  let remaining = Math.max(0, Number(timer.dataset.remaining || 0));
  complete.disabled = true;
  clock.textContent = format(remaining);
  if (timer.dataset.running !== 'true') return;

  let submitted = false;
  const expiresAt = Date.now() + remaining * 1000;
  const tick = () => {
    remaining = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000));
    clock.textContent = format(remaining);
    if (remaining === 0) {
      complete.disabled = false;
      if (!submitted) {
        submitted = true;
        form.requestSubmit();
      }
    }
  };
  tick();
  window.setInterval(tick, 250);
})();
