(() => {
  const workspaces = [...document.querySelectorAll('[data-subtask-workspace]')];
  if (!workspaces.length) return;

  const updateProgress = () => workspaces.forEach((workspace) => {
    const checks = [...workspace.querySelectorAll('[data-subtask-checkbox]')];
    const completed = checks.filter((check) => check.checked).length;
    const total = checks.length;
    const count = workspace.querySelector('[data-subtask-count]');
    const progress = workspace.querySelector('[data-subtask-progress]');
    if (count) count.textContent = `${completed} / ${total}`;
    if (progress) {
      progress.setAttribute('aria-valuemax', String(total));
      progress.setAttribute('aria-valuenow', String(completed));
      const fill = progress.querySelector('[data-progress-fill]');
      if (fill) fill.style.width = `${total ? Math.round(completed * 100 / total) : 0}%`;
    }
    const detail = workspace.closest('[data-task-workspace-detail]');
    const taskId = detail?.dataset.taskId;
    if (taskId) document.querySelectorAll('[data-parent-task-id]').forEach((item) => {
      if (item.dataset.parentTaskId !== taskId) return;
      const label = item.querySelector('[data-subtask-progress-label]');
      if (label) label.textContent = `${completed} of ${total} subtasks complete`;
    });
  });

  const showError = (message) => workspaces.forEach((workspace) => {
    const error = workspace.querySelector('[data-subtask-error]');
    if (!error) return;
    error.textContent = message;
    error.hidden = false;
  });

  workspaces.forEach((workspace) => {
    workspace.querySelectorAll('[data-subtask-toggle]').forEach((form) => {
      const checkbox = form.querySelector('[data-subtask-checkbox]');
      const status = form.querySelector('[name="status"]');
      if (!checkbox || !status) return;
      checkbox.addEventListener('change', async () => {
        if (form.dataset.busy === 'true') return;
        const next = checkbox.checked;
        const rowId = form.closest('[data-subtask-id]')?.dataset.subtaskId;
        const prior = !next;
        form.dataset.busy = 'true';
        status.value = next ? 'COMPLETED' : 'TODO';
        document.querySelectorAll('[data-subtask-id]').forEach((row) => {
          if (row.dataset.subtaskId === rowId) {
            row.classList.toggle('is-complete', next);
            const twin = row.querySelector('[data-subtask-checkbox]');
            if (twin && twin !== checkbox) twin.checked = next;
          }
        });
        updateProgress();
        workspaces.forEach((item) => {
          const error = item.querySelector('[data-subtask-error]');
          if (error) { error.hidden = true; error.textContent = ''; }
        });
        try {
          const response = await fetch(form.action, {
            method: 'POST',
            headers: { 'X-Requested-With': 'XMLHttpRequest', Accept: 'application/json' },
            body: new URLSearchParams(new FormData(form))
          });
          const result = await response.json().catch(() => ({}));
          if (!response.ok) throw new Error(result.message || 'This subtask could not be updated. Your change was rolled back.');
          updateProgress();
        } catch (error) {
          document.querySelectorAll('[data-subtask-id]').forEach((row) => {
            if (row.dataset.subtaskId === rowId) {
              row.classList.toggle('is-complete', prior);
              const twin = row.querySelector('[data-subtask-checkbox]');
              if (twin) twin.checked = prior;
            }
          });
          updateProgress();
          showError(error.message || 'This subtask could not be updated. Your change was rolled back.');
          checkbox.focus();
        } finally {
          form.dataset.busy = 'false';
        }
      });
    });
    workspace.querySelector('[data-expand-subtasks]')?.addEventListener('click', () => {
      workspace.querySelectorAll('.task-subtask-edit').forEach((item) => { item.open = true; });
    });
    workspace.querySelector('[data-collapse-subtasks]')?.addEventListener('click', () => {
      workspace.querySelectorAll('.task-subtask-edit').forEach((item) => { item.open = false; });
    });
  });
})();
