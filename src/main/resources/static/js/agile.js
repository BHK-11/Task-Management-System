(() => {
  const list = document.querySelector('[data-agile-backlog]');
  const form = document.querySelector('[data-agile-order-form]');
  const status = document.querySelector('[data-agile-order-status]');
  if (!list || !form || !status) return;

  let dragged = null;
  list.addEventListener('dragstart', (event) => {
    dragged = event.target.closest('[data-agile-item]');
    if (!dragged) return;
    dragged.classList.add('is-dragging');
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', dragged.dataset.taskId);
  });
  list.addEventListener('dragover', (event) => {
    const target = event.target.closest('[data-agile-item]');
    if (!dragged || !target || target === dragged) return;
    event.preventDefault();
    const after = event.clientY > target.getBoundingClientRect().top + target.offsetHeight / 2;
    list.insertBefore(dragged, after ? target.nextElementSibling : target);
  });
  list.addEventListener('drop', async (event) => {
    if (!dragged || !event.target.closest('[data-agile-item]')) return;
    event.preventDefault();
    const body = new URLSearchParams();
    for (const item of list.querySelectorAll('[data-agile-item]')) body.append('taskIds', item.dataset.taskId);
    const token = form.elements.namedItem('_csrf')?.value;
    if (token) body.append('_csrf', token);
    status.textContent = 'Saving product backlog order…';
    try {
      const response = await fetch(form.action, { method: 'POST', body, credentials: 'same-origin' });
      if (!response.ok) throw new Error('The backlog order could not be saved. Reload and try again.');
      window.location.reload();
    } catch (error) {
      status.textContent = error.message;
      window.setTimeout(() => window.location.reload(), 1500);
    }
  });
  list.addEventListener('dragend', () => {
    dragged?.classList.remove('is-dragging');
    dragged = null;
  });
})();
