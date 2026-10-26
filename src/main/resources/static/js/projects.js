(() => {
  let draggedCard = null;
  const status = document.querySelector('[data-board-status]');
  if (!status) return;

  document.addEventListener('dragstart', (event) => {
    const card = event.target.closest('[data-project-card]');
    if (!card?.querySelector('[data-project-move-form]')) return;
    draggedCard = card;
    card.classList.add('is-dragging');
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', card.dataset.taskId);
  });

  document.addEventListener('dragend', () => {
    draggedCard?.classList.remove('is-dragging');
    draggedCard = null;
    document.querySelectorAll('.is-drop-target').forEach((zone) => zone.classList.remove('is-drop-target'));
  });

  document.addEventListener('dragover', (event) => {
    const zone = event.target.closest('[data-project-dropzone]');
    if (!zone || !draggedCard) return;
    event.preventDefault();
    zone.classList.add('is-drop-target');
  });

  document.addEventListener('dragleave', (event) => {
    const zone = event.target.closest('[data-project-dropzone]');
    if (zone && !zone.contains(event.relatedTarget)) zone.classList.remove('is-drop-target');
  });

  document.addEventListener('drop', (event) => {
    const zone = event.target.closest('[data-project-dropzone]');
    if (!zone || !draggedCard) return;
    event.preventDefault();
    const column = zone.closest('[data-column-id]');
    const targetCard = event.target.closest('[data-project-card]');
    const cards = [...zone.querySelectorAll('[data-project-card]')].filter((card) => card !== draggedCard);
    const position = targetCard && targetCard !== draggedCard ? cards.indexOf(targetCard) : cards.length;
    const form = draggedCard.querySelector('[data-project-move-form]');
    form.elements.namedItem('columnId').value = column.dataset.columnId;
    form.elements.namedItem('position').value = Math.max(0, position);
    status.textContent = 'Saving board position…';
    form.requestSubmit();
  });
})();
