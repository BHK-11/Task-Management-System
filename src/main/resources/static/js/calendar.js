(() => {
  const page = document.querySelector('[data-calendar-page]');
  if (!page) return;

  const announce = (message) => {
    let status = page.querySelector('[data-calendar-message]');
    if (!status) {
      status = document.createElement('p');
      status.className = 'task-flash task-flash-error';
      status.setAttribute('role', 'status');
      status.dataset.calendarMessage = '';
      page.prepend(status);
    }
    status.textContent = message;
  };

  const move = async (block, targetDate) => {
    const id = block.dataset.calendarBlock;
    const start = block.dataset.start;
    const end = block.dataset.end;
    if (!id || !start || !end) return;
    const oldDate = start.slice(0, 10);
    const oldMidnight = new Date(`${oldDate}T00:00:00Z`);
    const newMidnight = new Date(`${targetDate}T00:00:00Z`);
    const dayDelta = Math.round((newMidnight - oldMidnight) / 86400000);
    if (!dayDelta) return;

    const movedWallTime = (value) => {
      const date = new Date(`${value.slice(0, 10)}T00:00:00Z`);
      date.setUTCDate(date.getUTCDate() + dayDelta);
      return `${date.toISOString().slice(0, 10)}T${value.slice(11, 16)}`;
    };
    const newStart = movedWallTime(start);
    const oldDuration = (Date.parse(`${end}:00Z`) - Date.parse(`${start}:00Z`)) / 60000;
    const endDate = new Date(`${newStart}:00Z`);
    endDate.setUTCMinutes(endDate.getUTCMinutes() + oldDuration);
    const newEnd = endDate.toISOString().slice(0, 16);

    const csrf = document.querySelector('meta[name="_csrf"]')?.content
      || document.querySelector('input[name="_csrf"]')?.value;
    const payload = new URLSearchParams({ startAt: newStart, endAt: newEnd, version: block.dataset.version || '0' });
    if (csrf) payload.set('_csrf', csrf);
    block.setAttribute('aria-busy', 'true');
    try {
      const response = await fetch(`/time/blocks/${encodeURIComponent(id)}/move`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
        body: payload,
      });
      if (!response.ok) throw new Error('The move could not be saved. Reload the calendar and try again.');
      window.location.assign(response.url);
    } catch (error) {
      block.removeAttribute('aria-busy');
      announce(error instanceof Error ? error.message : 'The move could not be saved.');
    }
  };

  page.querySelectorAll('[data-calendar-block][draggable="true"]').forEach((block) => {
    block.addEventListener('dragstart', (event) => {
      event.dataTransfer?.setData('text/plain', block.dataset.calendarBlock || '');
      if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
    });
  });
  page.querySelectorAll('[data-calendar-date]').forEach((day) => {
    day.addEventListener('dragover', (event) => { event.preventDefault(); day.classList.add('is-drop-target'); });
    day.addEventListener('dragleave', () => day.classList.remove('is-drop-target'));
    day.addEventListener('drop', (event) => {
      event.preventDefault(); day.classList.remove('is-drop-target');
      const id = event.dataTransfer?.getData('text/plain');
      const block = id ? page.querySelector(`[data-calendar-block="${CSS.escape(id)}"]`) : null;
      if (block) move(block, day.dataset.calendarDate);
    });
  });

  const startInput = page.querySelector('#block-start');
  const endInput = page.querySelector('#block-end');
  const titleInput = page.querySelector('#block-title');
  const blockForm = startInput?.form;
  page.querySelectorAll('[data-calendar-create-slot]').forEach((button) => {
    button.addEventListener('click', () => {
      const date = button.closest('[data-calendar-date]')?.dataset.calendarDate;
      const hour = Number(button.dataset.hour);
      if (!date || !startInput || !endInput || !blockForm) return;
      const pad = (value) => String(value).padStart(2, '0');
      startInput.value = `${date}T${pad(hour)}:00`;
      endInput.value = `${date}T${pad((hour + 1) % 24)}:00`;
      if (hour === 23) endInput.value = `${date}T23:59`;
      blockForm.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth', block: 'center' });
      titleInput?.focus({ preventScroll: true });
    });
  });
})();
