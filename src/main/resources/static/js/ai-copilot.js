(() => {
  const plan = document.querySelector(".copilot-plan[data-plan-status]");
  if (plan && ["QUEUED", "RUNNING"].includes(plan.dataset.planStatus)) {
    window.setTimeout(() => window.location.reload(), 2500);
  }

  const field = document.getElementById("copilot-instruction");
  const start = document.getElementById("copilot-voice-start");
  const stop = document.getElementById("copilot-voice-stop");
  const language = document.getElementById("copilot-voice-language");
  const status = document.getElementById("copilot-voice-status");
  if (!field || !start || !stop || !language || !status) return;

  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognition) {
    start.disabled = true;
    status.textContent = "Speech recognition is unavailable here. Type or paste your instruction.";
    return;
  }

  let recognition;
  let committed = "";
  start.addEventListener("click", () => {
    recognition = new SpeechRecognition();
    recognition.lang = language.value;
    recognition.continuous = true;
    recognition.interimResults = true;
    committed = field.value.trim();
    recognition.onstart = () => {
      start.disabled = true;
      stop.disabled = false;
      status.textContent = "Listening. Review the transcript before submitting.";
    };
    recognition.onresult = (event) => {
      let interim = "";
      for (let index = event.resultIndex; index < event.results.length; index += 1) {
        const phrase = event.results[index][0].transcript.trim();
        if (event.results[index].isFinal && phrase) committed = `${committed}${committed ? " " : ""}${phrase}`;
        else if (phrase) interim += `${phrase} `;
      }
      field.value = `${committed}${committed && interim ? " " : ""}${interim}`;
      if (field.value.length > 20000) {
        status.textContent = "Transcript exceeds 20,000 characters. Stop and trim it before submitting.";
        recognition.stop();
      } else {
        status.textContent = interim ? `Listening: ${interim}` : "Transcript updated. You can edit it before submitting.";
      }
    };
    recognition.onerror = (event) => {
      status.textContent = event.error === "not-allowed"
        ? "Microphone permission was denied. You can still type or paste."
        : `Voice input stopped (${event.error}). You can continue by typing.`;
    };
    recognition.onend = () => {
      start.disabled = false;
      stop.disabled = true;
      status.textContent = "Voice input stopped. Review the editable transcript.";
    };
    try { recognition.start(); }
    catch { status.textContent = "Voice input could not start. Type or paste your instruction."; }
  });

  stop.addEventListener("click", () => recognition?.stop());
})();
