(() => {
  const storageKey = 'taskos-theme';
  const validModes = ['system', 'light', 'dark'];
  const readMode = () => {
    const accountMode = document.documentElement.dataset.accountTheme?.toLowerCase();
    if (validModes.includes(accountMode)) return accountMode;
    try {
      const saved = localStorage.getItem(storageKey);
      return validModes.includes(saved) ? saved : 'system';
    } catch {
      return 'system';
    }
  };
  const systemDark = window.matchMedia('(prefers-color-scheme: dark)');
  const applyTheme = (mode) => {
    const resolved = mode === 'system' ? (systemDark.matches ? 'dark' : 'light') : mode;
    document.documentElement.dataset.themeMode = mode;
    document.documentElement.dataset.theme = resolved;
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', resolved === 'dark' ? '#09111e' : '#f8f9fc');
  };

  applyTheme(readMode());

  document.addEventListener('DOMContentLoaded', () => {
    const selects = document.querySelectorAll('[data-theme-select]');
    const syncSelects = (mode) => selects.forEach((select) => { select.value = mode; });
    let mode = readMode();
    syncSelects(mode);
    selects.forEach((select) => select.addEventListener('change', () => {
      mode = validModes.includes(select.value) ? select.value : 'system';
      try { localStorage.setItem(storageKey, mode); } catch { /* Theme still applies for this page. */ }
      applyTheme(mode);
      syncSelects(mode);
    }));
    systemDark.addEventListener('change', () => { if (mode === 'system') applyTheme('system'); });

    const experienceToggle = document.querySelector('[data-experience-mode-toggle]');
    if (experienceToggle) {
      const experienceKey = 'taskos-experience-mode';
      let savedMode = null;
      try { savedMode = localStorage.getItem(experienceKey); } catch { /* The default view still works. */ }
      const activeAdvancedLink = document.querySelector('[data-advanced-nav].active');
      const setExperienceMode = (experienceMode, persist = false) => {
        document.body.dataset.experienceMode = experienceMode;
        experienceToggle.setAttribute('aria-pressed', String(experienceMode === 'advanced'));
        experienceToggle.setAttribute('aria-label', `Switch to ${experienceMode === 'advanced' ? 'simple' : 'advanced'} mode`);
        experienceToggle.textContent = experienceMode === 'advanced' ? 'Advanced' : 'Simple';
        if (persist) {
          try { localStorage.setItem(experienceKey, experienceMode); } catch { /* The mode still applies for this page. */ }
        }
      };
      const initialMode = savedMode === 'simple' || savedMode === 'advanced'
        ? savedMode
        : activeAdvancedLink ? 'advanced' : 'simple';
      setExperienceMode(initialMode);
      experienceToggle.addEventListener('click', () => {
        setExperienceMode(document.body.dataset.experienceMode === 'advanced' ? 'simple' : 'advanced', true);
      });
    }

    const commandDialog = document.querySelector('[data-command-dialog]');
    const commandInput = document.querySelector('[data-command-input]');
    if (commandDialog && commandInput) {
      const commandOpeners = document.querySelectorAll('[data-command-open]');
      const commandItems = [...commandDialog.querySelectorAll('[data-command-item]')];
      const taskSearch = commandDialog.querySelector('[data-command-task-search]');
      const projectSearch = commandDialog.querySelector('[data-command-project-search]');
      const searchHint = commandDialog.querySelector('[data-command-search-hint]');
      const resultStatus = commandDialog.querySelector('[data-command-status]');
      const accessibleItems = () => commandItems.filter((item) => !item.hidden
        && !(document.body.dataset.experienceMode === 'simple'
          && item.hasAttribute('data-advanced-nav') && !item.classList.contains('active')));
      const updateCommands = () => {
        const query = commandInput.value.trim();
        const normalized = query.toLocaleLowerCase();
        commandItems.forEach((item) => {
          item.hidden = Boolean(normalized) && !item.dataset.commandSearch.includes(normalized);
        });
        const hasQuery = Boolean(query);
        [taskSearch, projectSearch].forEach((link) => {
          if (!link) return;
          link.hidden = !hasQuery;
          if (hasQuery) link.href = `${link.hasAttribute('data-command-task-search') ? '/tasks' : '/projects'}?q=${encodeURIComponent(query)}`;
        });
        const taskLabel = commandDialog.querySelector('[data-command-task-label]');
        const projectLabel = commandDialog.querySelector('[data-command-project-label]');
        if (taskLabel) taskLabel.textContent = hasQuery ? `Tasks matching “${query}”` : '';
        if (projectLabel) projectLabel.textContent = hasQuery ? `Projects matching “${query}”` : '';
        if (searchHint) searchHint.textContent = hasQuery
          ? 'Choose a result type to search your records with your existing permissions.'
          : 'Task and project results use your existing access permissions.';
        const matches = accessibleItems();
        if (resultStatus) resultStatus.textContent = hasQuery
          ? `${matches.length} matching page${matches.length === 1 ? '' : 's'}; task and project search is also available.`
          : 'Type to filter pages, or search tasks and projects.';
      };
      const openCommands = () => {
        if (commandDialog.open) return;
        commandInput.value = '';
        updateCommands();
        commandDialog.showModal();
        commandInput.focus();
      };
      commandOpeners.forEach((button) => button.addEventListener('click', openCommands));
      commandDialog.querySelector('[data-command-close]')?.addEventListener('click', () => commandDialog.close());
      commandDialog.addEventListener('click', (event) => {
        if (event.target === commandDialog) commandDialog.close();
      });
      commandInput.addEventListener('input', updateCommands);
      commandInput.addEventListener('keydown', (event) => {
        const matches = accessibleItems();
        if (event.key === 'ArrowDown') {
          event.preventDefault();
          (matches[0] || (commandInput.value.trim() ? taskSearch : null))?.focus();
        } else if (event.key === 'Enter') {
          const query = commandInput.value.trim().toLocaleLowerCase();
          const page = query ? matches.find((item) => item.dataset.commandSearch.includes(query)) : null;
          const destination = page || (query ? taskSearch : null);
          if (destination) {
            event.preventDefault();
            window.location.assign(destination.href);
          }
        }
      });
      document.addEventListener('keydown', (event) => {
        if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
          event.preventDefault();
          if (commandDialog.open) commandDialog.close();
          else openCommands();
        }
      });
    }

    document.querySelectorAll('[data-password-toggle]').forEach((button) => {
      const input = document.getElementById(button.getAttribute('aria-controls'));
      if (!input) return;
      button.addEventListener('click', () => {
        const show = input.type === 'password';
        input.type = show ? 'text' : 'password';
        button.setAttribute('aria-pressed', String(show));
        button.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
        button.textContent = show ? 'Hide' : 'Show';
      });
    });

    document.querySelectorAll('[data-auth-form]').forEach((form) => {
      form.addEventListener('submit', () => {
        if (!form.reportValidity()) return;
        form.setAttribute('aria-busy', 'true');
        const button = form.querySelector('[data-submit-button]');
        const label = button?.querySelector('[data-submit-label]');
        if (button && label) {
          label.textContent = label.dataset.workingLabel || label.textContent;
          button.disabled = true;
        }
      });
    });

    document.querySelector('[aria-invalid="true"]')?.focus();

    document.querySelectorAll('form[data-confirm]').forEach((form) => {
      form.addEventListener('submit', (event) => {
        if (!window.confirm(form.dataset.confirm || 'Continue with this action?')) event.preventDefault();
      });
    });
    document.querySelector('[data-bulk-form]')?.addEventListener('submit', (event) => {
      const form = event.currentTarget;
      const action = form.querySelector('[name="action"]')?.value;
      if (action === 'DELETE' && !window.confirm('Permanently delete the selected tasks and their subtasks? This cannot be undone.')) {
        event.preventDefault();
      }
    });

    const toggle = document.querySelector('[data-sidebar-toggle]');
    const moreToggle = document.querySelector('[data-more-toggle]');
    const sidebarToggles = [toggle, moreToggle].filter(Boolean);
    const sidebar = document.getElementById('workspace-sidebar');
    const backdrop = document.querySelector('[data-sidebar-backdrop]');
    if (!sidebarToggles.length || !sidebar || !backdrop) return;
    const sidebarControls = [...sidebar.querySelectorAll('a[href], button:not([disabled]), select:not([disabled])')];
    let wasOpen = false;
    let lastToggle = toggle;
    const syncSidebarAccessibility = () => {
      const hidden = window.matchMedia('(max-width: 1020px)').matches && !sidebar.classList.contains('is-open');
      sidebar.inert = hidden;
      if (hidden) {
        sidebar.setAttribute('aria-hidden', 'true');
        sidebarControls.forEach((control) => control.setAttribute('tabindex', '-1'));
      } else {
        sidebar.removeAttribute('aria-hidden');
        sidebarControls.forEach((control) => control.removeAttribute('tabindex'));
      }
    };
    const setOpen = (open, trigger = lastToggle) => {
      if (open) lastToggle = trigger;
      sidebar.classList.toggle('is-open', open);
      syncSidebarAccessibility();
      backdrop.hidden = !open;
      sidebarToggles.forEach((button) => {
        button.setAttribute('aria-expanded', String(open));
        button.setAttribute('aria-label', button === moreToggle
          ? (open ? 'Close more navigation' : 'More navigation')
          : (open ? 'Close navigation' : 'Open navigation'));
      });
      document.body.classList.toggle('nav-drawer-open', open);
      if (open) sidebar.querySelector('a[href]')?.focus();
      else if (wasOpen) (lastToggle || toggle)?.focus();
      wasOpen = open;
    };
    toggle?.addEventListener('click', () => setOpen(!sidebar.classList.contains('is-open'), toggle));
    moreToggle?.addEventListener('click', () => setOpen(!sidebar.classList.contains('is-open'), moreToggle));
    backdrop.addEventListener('click', () => setOpen(false));
    document.addEventListener('keydown', (event) => {
      if (!sidebar.classList.contains('is-open')) return;
      if (event.key === 'Escape') { setOpen(false); return; }
      if (event.key !== 'Tab') return;
      const focusable = [...sidebar.querySelectorAll('a[href], button:not([disabled]), select:not([disabled])')];
      if (focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    });
    sidebar.querySelectorAll('a[href]').forEach((link) => link.addEventListener('click', () => {
      if (window.matchMedia('(max-width: 1020px)').matches) setOpen(false);
    }));
    syncSidebarAccessibility();
    window.addEventListener('resize', () => {
      const drawerLayout = window.matchMedia('(max-width: 1020px)').matches;
      if (!drawerLayout && sidebar.classList.contains('is-open')) {
        if (!lastToggle?.getClientRects().length) lastToggle = sidebar.querySelector('a.active') || sidebar.querySelector('a[href]');
        setOpen(false);
      }
      syncSidebarAccessibility();
    });
  });
})();
