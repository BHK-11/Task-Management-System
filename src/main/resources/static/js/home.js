(() => {
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  if (!reducedMotion && window.gsap && window.ScrollTrigger) {
    window.gsap.registerPlugin(window.ScrollTrigger);
    const media = window.gsap.matchMedia();
    media.add('(prefers-reduced-motion: no-preference)', () => {
      window.gsap.utils.toArray('[data-story-reveal]:not([data-parallax])').forEach((element) => {
        window.gsap.fromTo(element, { autoAlpha: 0, y: 18 }, {
          autoAlpha: 1,
          y: 0,
          duration: 0.65,
          ease: 'power2.out',
          scrollTrigger: { trigger: element, start: 'top 88%', once: true }
        });
      });

      const hero = document.querySelector('.cinema-hero');
      const orb = document.querySelector('[data-parallax]');
      if (hero && orb && !window.matchMedia('(max-width: 760px)').matches) {
        window.gsap.to(orb, {
          y: 24,
          ease: 'none',
          scrollTrigger: { trigger: hero, start: 'top top', end: 'bottom top', scrub: 0.6 }
        });
      }
    });
  }

  const planButtons = [...document.querySelectorAll('[data-plan-step]')];
  const planPreview = document.querySelector('[data-plan-preview]');
  if (planButtons.length && planPreview) {
    const label = planPreview.querySelector('[data-plan-label]');
    const title = planPreview.querySelector('[data-plan-title]');
    const copy = planPreview.querySelector('[data-plan-copy]');
    planButtons.forEach((button, index) => button.addEventListener('click', () => {
      planButtons.forEach((step) => {
        step.classList.toggle('is-active', step === button);
        step.setAttribute('aria-pressed', String(step === button));
      });
      const stepName = button.textContent.trim().split(/\s+/).slice(1).join(' ').toUpperCase();
      label.textContent = String(index + 1).padStart(2, '0') + ' / ' + stepName;
      title.textContent = button.dataset.title;
      copy.textContent = button.dataset.copy;
      if (!reducedMotion && window.Motion && typeof window.Motion.animate === 'function') {
        window.Motion.animate(planPreview, { opacity: [0.68, 1], y: [7, 0] }, { duration: 0.22, ease: 'easeOut' });
      }
    }));
  }

  // shortcut: transcript stays in this page, upgrade when TASK OS adds an authenticated voice-to-task flow.
  const startButton = document.querySelector('[data-voice-start]');
  const voiceStatus = document.querySelector('[data-voice-status]');
  const voiceTranscript = document.querySelector('[data-voice-transcript]');
  const waveform = document.querySelector('[data-waveform]');
  const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!startButton || !voiceStatus || !voiceTranscript) return;
  if (!Recognition) {
    startButton.disabled = true;
    startButton.textContent = 'Voice input unavailable';
    voiceStatus.textContent = 'This browser does not support the speech demo. The example draft is still available.';
    return;
  }

  let recognition = null;
  let listening = false;
  let finalMessage = '';
  let transcript = '';

  const resetVoiceControl = () => {
    listening = false;
    startButton.setAttribute('aria-pressed', 'false');
    startButton.textContent = 'Start voice demo';
    waveform?.classList.remove('is-listening');
  };

  startButton.addEventListener('click', () => {
    if (listening) {
      finalMessage = 'Listening stopped. The draft has not been saved.';
      recognition.stop();
      return;
    }

    try {
      recognition = new Recognition();
      recognition.lang = document.documentElement.lang || 'en-US';
      recognition.continuous = false;
      recognition.interimResults = true;
      transcript = '';
      finalMessage = '';
      listening = true;
      startButton.setAttribute('aria-pressed', 'true');
      startButton.textContent = 'Stop listening';
      waveform?.classList.add('is-listening');
      voiceStatus.textContent = 'Listening. Speak a short draft, or press stop.';
      recognition.onresult = (event) => {
        transcript = Array.from(event.results)
          .map((result) => result[0]?.transcript || '')
          .join(' ')
          .trim()
          .slice(0, 500);
        if (transcript) voiceTranscript.textContent = 'Draft preview: “' + transcript + '”';
      };
      recognition.onerror = (event) => {
        finalMessage = event.error === 'not-allowed'
          ? 'Microphone access was not allowed. You can keep using TASK OS without voice.'
          : event.error === 'no-speech'
            ? 'No speech was detected. Press start to try again.'
            : 'Voice input is unavailable right now. No task was created.';
      };
      recognition.onend = () => {
        resetVoiceControl();
        voiceStatus.textContent = finalMessage || (transcript
          ? 'Draft captured in this page. It has not been saved.'
          : 'No words captured. Press start to try again.');
      };
      recognition.start();
    } catch {
      resetVoiceControl();
      voiceStatus.textContent = 'Voice input could not start. No task was created.';
    }
  });

  window.addEventListener('pagehide', () => {
    if (listening) recognition?.abort();
  });
})();
