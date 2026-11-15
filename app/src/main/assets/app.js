(() => {
  "use strict";

  const I18N = {
    ar: {
      appTitle: "مختبر النهايات اللانهائي",
      appSubtitle: "تمارين متجددة بملايير الاحتمالات • تحقق رمزي فوري",
      langSwitch: "Français (BIOF)",
      logout: "تغيير الحساب",
      loginHeading: "مرحباً بك في فضاء التدريب على النهايات",
      loginDesc:
        "خوارزمية ذكية تولّد في كل مرة تمريناً جديداً غير مكرر في التعميل، المرافق، الجذور النونية، إضافة وطرح الصفر، والنهايات الدورية (sin, cos, tan).",
      usernameLabel: "اسم التلميذ (Username)",
      usernamePlaceholder: "مثال: youssef_pc",
      loginSubmit: "ابدأ التدريب",
      recentUsers: "حسابات استُعملت على هذا الجهاز:",
      feat1Title: "ملايير الاحتمالات",
      feat1Desc: "تركيب ديناميكي للبسط والمقام والفخاخ الرياضية بدون تكرار.",
      feat2Title: "تحقق ذكي بالمعادلات",
      feat2Desc: "يقبل 0.5 و 1/2 و √2/2 و 1/√2 كإجابات صحيحة متكافئة.",
      feat3Title: "تلميحات وحل مفصل",
      feat3Desc: "إرشاد تدريجي ثم تصحيح كامل باللغتين العربية والفرنسية.",
      techLabel: "التقنية",
      diffLabel: "المستوى",
      newExercise: "تمرين جديد",
      seedPlaceholder: "أدخل كود التمرين مثل 2BAC-ADD0-L2-...",
      loadSeed: "تحميل الكود",
      promptText: "احسب النهاية التالية:",
      answerLabel: "إجابتك (مثال: 1/2 أو sqrt(3)/2 أو +inf أو -inf)",
      submitBtn: "تحقق من الجواب",
      livePreview: "معاينة:",
      hintBtn: "تلميح",
      solutionBtn: "عرض الحل المفصل",
      solutionTitle: "الحل المفصل خطوة بخطوة",
      solutionFinal: "النتيجة:",
      statsHeading: "لوحة التقدم",
      lblSolved: "تمارين محلولة",
      lblAccuracy: "نسبة الدقة",
      lblStreak: "السلسلة 🔥",
      masteryHeading: "إتقان التقنيات",
      historyHeading: "آخر التمارين",
      noHistory: "لم تبدأ أي تمرين بعد.",
      copiedSeed: "تم النسخ!",
      loading: "جاري توليد التمرين...",
      techniques: {
        adaptive: "الوضع الذكي التكيفي (حسب مستواك)",
        poly_factor: "التعميل والحدوديات",
        sqrt_conjugate: "المرافق التربيعي وفخاخ الإشارة",
        nth_root: "الجذور النونية وتغيير المتغير",
        add_sub_zero: "تقنية إضافة وطرح الصفر (0)",
        trig: "النهايات المثلثية / الدورية (sin, cos, tan)",
        hybrid: "تحدي هجين (تقنيتان معاً)",
      },
      difficulties: {
        "0": "كل المستويات (تلقائي)",
        "1": "المستوى 1 — أساسي",
        "2": "المستوى 2 — متوسط / فخاخ الباك",
        "3": "المستوى 3 — تحدي / فروض محروسة",
      },
      diffBadges: { 1: "★☆☆ أساسي", 2: "★★☆ متوسط", 3: "★★★ تحدي" },
      statusOk: "صحيح",
      statusFail: "خطأ",
      statusPending: "قيد الحل",
    },
    fr: {
      appTitle: "Labo des Limites — 2BAC PC",
      appSubtitle: "Exercices infinis non répétitifs • Vérification symbolique CAS",
      langSwitch: "العربية (AR)",
      logout: "Changer de compte",
      loginHeading: "Bienvenue sur l'espace d'entraînement aux limites",
      loginDesc:
        "Un algorithme génératif qui crée à chaque fois un exercice inédit : factorisation, conjugué, racines n-ièmes et ajout/retranchement de zéro.",
      usernameLabel: "Nom d'élève (Username)",
      usernamePlaceholder: "Ex : youssef_pc",
      loginSubmit: "Commencer",
      recentUsers: "Comptes récents sur cet appareil :",
      feat1Title: "Des milliards de variantes",
      feat1Desc: "Composition dynamique numérateur/dénominateur sans répétition.",
      feat2Title: "Correction symbolique",
      feat2Desc: "Accepte 0.5, 1/2, sqrt(2)/2 et 1/sqrt(2) comme équivalents.",
      feat3Title: "Indices & correction détaillée",
      feat3Desc: "Aide progressive puis solution complète en arabe ou en français.",
      techLabel: "Technique",
      diffLabel: "Niveau",
      newExercise: "Nouvel exercice",
      seedPlaceholder: "Entrer un code ex : 2BAC-ADD0-L2-...",
      loadSeed: "Charger le code",
      promptText: "Calculer la limite suivante :",
      answerLabel: "Votre réponse (ex : 1/2, sqrt(3)/2, +inf ou -inf)",
      submitBtn: "Vérifier",
      livePreview: "Aperçu :",
      hintBtn: "Indice",
      solutionBtn: "Voir la correction",
      solutionTitle: "Correction détaillée étape par étape",
      solutionFinal: "Résultat :",
      statsHeading: "Progression",
      lblSolved: "Résolus",
      lblAccuracy: "Précision",
      lblStreak: "Série 🔥",
      masteryHeading: "Maîtrise par technique",
      historyHeading: "Exercices récents",
      noHistory: "Aucun exercice pour le moment.",
      copiedSeed: "Copié !",
      loading: "Génération en cours...",
      techniques: {
        adaptive: "Mode adaptatif intelligent",
        poly_factor: "Factorisation & Polynômes",
        sqrt_conjugate: "Expression conjuguée (carrée)",
        nth_root: "Racines n-ièmes & changement de variable",
        add_sub_zero: "Ajout et retranchement (0)",
        trig: "Limites trigonométriques (sin, cos, tan)",
        hybrid: "Hybride multi-techniques",
      },
      difficulties: {
        "0": "Tous les niveaux (auto)",
        "1": "Niveau 1 — Basique",
        "2": "Niveau 2 — Intermédiaire (Bac)",
        "3": "Niveau 3 — Défi (Devoir surveillé)",
      },
      diffBadges: { 1: "★☆☆ Basique", 2: "★★☆ Intermédiaire", 3: "★★★ Défi" },
      statusOk: "Exact",
      statusFail: "Incorrect",
      statusPending: "En cours",
    },
  };

  const state = {
    lang: localStorage.getItem("bac_limits_lang") || "ar",
    username: null,
    displayName: null,
    stats: null,
    exercise: null,
    hintsShown: [],
    lastGrade: null,
    solution: null,
    copyTimer: null,
  };

  const $ = (id) => document.getElementById(id);

  function t() {
    return I18N[state.lang];
  }

  function fallbackCopyText(text) {
    const active = document.activeElement;
    const ta = document.createElement("textarea");
    ta.value = text;
    ta.setAttribute("readonly", "");
    ta.style.position = "fixed";
    ta.style.top = "0";
    ta.style.left = "-9999px";
    ta.style.opacity = "0";
    document.body.appendChild(ta);
    ta.focus({ preventScroll: true });
    ta.select();
    ta.setSelectionRange(0, text.length);
    let ok = false;
    try {
      ok = document.execCommand("copy");
    } catch (_) {
      ok = false;
    }
    document.body.removeChild(ta);
    if (active && typeof active.focus === "function") {
      try {
        active.focus({ preventScroll: true });
      } catch (_) {}
    }
    return ok;
  }

  async function copyTextToClipboard(text) {
    // Plain HTTP origins lack a secure context for navigator.clipboard; run
    // execCommand synchronously while user activation is active.
    if (navigator.clipboard && window.isSecureContext) {
      try {
        await navigator.clipboard.writeText(text);
        return true;
      } catch (_) {}
    }
    return fallbackCopyText(text);
  }

  function showCopiedSeedFeedback() {
    if (!state.exercise) return;
    if (state.copyTimer) {
      clearTimeout(state.copyTimer);
      state.copyTimer = null;
    }
    const seedEl = $("ex-seed-text");
    const btn = $("copy-seed-btn");
    seedEl.textContent = t().copiedSeed;
    seedEl.dir = state.lang === "ar" ? "rtl" : "ltr";
    btn.classList.add("copied");
    state.copyTimer = setTimeout(() => {
      state.copyTimer = null;
      btn.classList.remove("copied");
      seedEl.removeAttribute("dir");
      if (state.exercise) {
        seedEl.textContent = state.exercise.seed;
      }
    }, 1400);
  }

  function renderDisplayMath(el, latex) {
    if (!el) return;
    if (window.katex) {
      try {
        window.katex.render(latex, el, { displayMode: true, throwOnError: false });
        return;
      } catch (_) {}
    }
    el.textContent = latex;
  }

  function renderInlineMath(el, latex) {
    if (!el) return;
    if (window.katex) {
      try {
        window.katex.render(latex, el, { displayMode: false, throwOnError: false });
        return;
      } catch (_) {}
    }
    el.textContent = latex;
  }

  function renderMixedProse(el, text) {
    el.textContent = text;
    if (window.renderMathInElement) {
      try {
        window.renderMathInElement(el, {
          delimiters: [
            { left: "$$", right: "$$", display: true },
            { left: "$", right: "$", display: false },
          ],
          throwOnError: false,
        });
      } catch (_) {}
    }
  }

  function asciiToPreviewLatex(raw) {
    let s = raw.trim();
    if (!s) return "";
    s = s.replace(/\s+/g, "");
    s = s.replace(/\+inf|\+oo|infinity|∞/gi, "+\\infty");
    s = s.replace(/-inf|-oo/gi, "-\\infty");
    s = s.replace(/\binf\b|\boo\b/gi, "\\infty");
    s = s.replace(/root\(([^,]+),([^)]+)\)/g, (_, inner, n) => `\\sqrt[${n}]{${inner}}`);
    s = s.replace(/sqrt\(([^)]+)\)/g, (_, inner) => `\\sqrt{${inner}}`);
    s = s.replace(/\*\*(\d+)/g, (_, exp) => `^{${exp}}`);
    s = s.replace(/\^\(([^)]+)\)/g, (_, exp) => `^{${exp}}`);
    s = s.replace(/\\?pi\b|π/gi, "\\pi ");
    s = s.replace(/\b(sin|cos|tan)\(/g, "\\$1(");
    // Simple top-level fraction preview a/b
    if (s.includes("/") && !s.includes("\\frac")) {
      const parts = s.split("/");
      if (parts.length === 2 && parts[0] && parts[1]) {
        s = `\\frac{${parts[0]}}{${parts[1]}}`;
      }
    }
    s = s.replace(/\*/g, "\\cdot ");
    return s;
  }

  function applyLanguageToStaticUI() {
    const tr = t();
    document.documentElement.lang = state.lang;
    document.documentElement.dir = state.lang === "ar" ? "rtl" : "ltr";

    $("app-title").textContent = tr.appTitle;
    $("app-subtitle").textContent = tr.appSubtitle;
    $("lang-toggle-btn").textContent = tr.langSwitch;
    $("logout-btn").textContent = tr.logout;

    $("login-heading").textContent = tr.loginHeading;
    $("login-desc").textContent = tr.loginDesc;
    $("username-label").textContent = tr.usernameLabel;
    $("username-input").placeholder = tr.usernamePlaceholder;
    $("login-submit-btn").textContent = tr.loginSubmit;
    $("recent-users-label").textContent = tr.recentUsers;
    $("feat-1-title").textContent = tr.feat1Title;
    $("feat-1-desc").textContent = tr.feat1Desc;
    $("feat-2-title").textContent = tr.feat2Title;
    $("feat-2-desc").textContent = tr.feat2Desc;
    $("feat-3-title").textContent = tr.feat3Title;
    $("feat-3-desc").textContent = tr.feat3Desc;

    $("tech-label").textContent = tr.techLabel;
    $("diff-label").textContent = tr.diffLabel;
    $("new-exercise-btn").textContent = tr.newExercise;
    $("seed-input").placeholder = tr.seedPlaceholder;
    $("load-seed-btn").textContent = tr.loadSeed;
    $("ex-prompt-text").textContent = tr.promptText;
    $("answer-label").textContent = tr.answerLabel;
    $("submit-btn").textContent = tr.submitBtn;
    $("live-preview-label").textContent = tr.livePreview;
    $("solution-btn").textContent = tr.solutionBtn;
    $("solution-title").textContent = tr.solutionTitle;
    $("solution-final-label").textContent = tr.solutionFinal;

    $("stats-heading").textContent = tr.statsHeading;
    $("lbl-solved").textContent = tr.lblSolved;
    $("lbl-accuracy").textContent = tr.lblAccuracy;
    $("lbl-streak").textContent = tr.lblStreak;
    $("mastery-heading").textContent = tr.masteryHeading;
    $("history-heading").textContent = tr.historyHeading;

    populateSelects();
    renderExerciseArea();
    renderStats();
    renderDisplayMath(
      $("hero-formula-preview"),
      r_heroFormula()
    );
  }

  function r_heroFormula() {
    return "\\lim_{x \\to 1} \\frac{\\sqrt{x+3} - \\sqrt[3]{2x+6}}{x - 1} = \\frac{1}{12}";
  }

  function populateSelects() {
    const tr = t();
    const techSel = $("technique-select");
    const prevTech = techSel.value || "adaptive";
    techSel.innerHTML = "";
    Object.entries(tr.techniques).forEach(([val, label]) => {
      const opt = document.createElement("option");
      opt.value = val;
      opt.textContent = label;
      techSel.appendChild(opt);
    });
    techSel.value = prevTech;

    const diffSel = $("difficulty-select");
    const prevDiff = diffSel.value || "0";
    diffSel.innerHTML = "";
    Object.entries(tr.difficulties).forEach(([val, label]) => {
      const opt = document.createElement("option");
      opt.value = val;
      opt.textContent = label;
      diffSel.appendChild(opt);
    });
    diffSel.value = prevDiff;
  }

  const API_PREFIX = /^\/2bac(%20|\s|-)?limits\b/i.test(window.location.pathname)
    ? "/api/bac-limits"
    : "/api";

  function apiUrl(url) {
    if (url.startsWith("/api/")) {
      return `${API_PREFIX}${url.slice(4)}`;
    }
    return url;
  }

  async function apiJson(url, method = "GET", body = null) {
    const opts = { method, headers: {} };
    if (body !== null) {
      opts.headers["Content-Type"] = "application/json";
      opts.body = JSON.stringify(body);
    }
    const res = await fetch(apiUrl(url), opts);
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      const msg = Array.isArray(data.detail)
        ? data.detail.map((d) => d.msg || String(d)).join(", ")
        : data.detail || `HTTP ${res.status}`;
      throw new Error(msg);
    }
    return data;
  }

  function saveRecentUsername(name) {
    const list = JSON.parse(localStorage.getItem("bac_limits_recent_users") || "[]");
    const filtered = list.filter((u) => u.toLowerCase() !== name.toLowerCase());
    filtered.unshift(name);
    localStorage.setItem("bac_limits_recent_users", JSON.stringify(filtered.slice(0, 5)));
  }

  function renderRecentUserChips() {
    const list = JSON.parse(localStorage.getItem("bac_limits_recent_users") || "[]");
    const wrap = $("recent-users-wrap");
    const chips = $("recent-users-chips");
    chips.innerHTML = "";
    if (!list.length) {
      wrap.classList.add("hidden");
      return;
    }
    wrap.classList.remove("hidden");
    list.forEach((name) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "user-chip";
      btn.textContent = name;
      btn.addEventListener("click", () => loginUser(name));
      chips.appendChild(btn);
    });
  }

  async function loginUser(rawName) {
    const errEl = $("login-error");
    errEl.classList.add("hidden");
    try {
      const stats = await apiJson("/api/login", "POST", {
        username: rawName,
        lang: state.lang,
      });
      state.username = stats.username;
      state.displayName = stats.display_name;
      state.stats = stats;
      localStorage.setItem("bac_limits_username", stats.display_name);
      saveRecentUsername(stats.display_name);

      $("user-display-name").textContent = stats.display_name;
      $("user-pill").classList.remove("hidden");
      $("login-view").classList.add("hidden");
      $("workspace-view").classList.remove("hidden");

      renderStats();
      const resume = await apiJson(`/api/student/${encodeURIComponent(state.username)}/resume`);
      if (resume.has_exercise && resume.exercise) {
        applyRestoredExercise(resume);
      } else {
        await loadNextExercise();
      }
    } catch (err) {
      errEl.textContent = err.message;
      errEl.classList.remove("hidden");
    }
  }

  function applyRestoredExercise(resume) {
    state.exercise = resume.exercise;
    state.hintsShown = resume.hints || [];
    state.lastGrade = resume.last_grade || null;
    state.solution = resume.solution || null;
    $("answer-input").value = resume.student_answer || "";
    updateLivePreview();
    renderExerciseArea();
  }

  function logoutUser() {
    state.username = null;
    state.displayName = null;
    state.stats = null;
    state.exercise = null;
    state.hintsShown = [];
    state.lastGrade = null;
    state.solution = null;
    $("answer-input").value = "";
    localStorage.removeItem("bac_limits_username");
    $("user-pill").classList.add("hidden");
    $("workspace-view").classList.add("hidden");
    $("login-view").classList.remove("hidden");
    renderRecentUserChips();
  }

  async function loadNextExercise(explicitSeed = null) {
    if (!state.username) return;
    const loader = $("ex-loading");
    loader.textContent = t().loading;
    loader.classList.remove("hidden");
    $("feedback-box").classList.add("hidden");

    try {
      const ex = await apiJson("/api/exercises/next", "POST", {
        username: state.username,
        technique: $("technique-select").value,
        difficulty: Number($("difficulty-select").value || "0"),
        seed: explicitSeed,
      });
      state.exercise = ex;
      state.hintsShown = [];
      state.lastGrade = null;
      state.solution = null;
      $("answer-input").value = "";
      $("live-preview-wrap").classList.add("hidden");
      renderExerciseArea();
      await refreshStats();
    } catch (err) {
      showFeedback(err.message, false);
    } finally {
      loader.classList.add("hidden");
    }
  }

  async function refreshStats() {
    if (!state.username) return;
    try {
      state.stats = await apiJson(`/api/student/${encodeURIComponent(state.username)}/stats`);
      renderStats();
    } catch (_) {}
  }

  function renderExerciseArea() {
    const tr = t();
    const ex = state.exercise;
    if (!ex) return;

    $("ex-tech-badge").textContent = tr.techniques[ex.technique] || ex.technique;
    $("ex-diff-badge").textContent = tr.diffBadges[ex.difficulty] || `L${ex.difficulty}`;
    if (state.copyTimer) {
      clearTimeout(state.copyTimer);
      state.copyTimer = null;
    }
    $("copy-seed-btn").classList.remove("copied");
    $("ex-seed-text").removeAttribute("dir");
    $("ex-seed-text").textContent = ex.seed;
    renderDisplayMath($("ex-formula"), ex.full_limit_latex);

    // Hint button state
    const hintBtn = $("hint-btn");
    const shownCount = state.hintsShown.length;
    hintBtn.textContent = `${tr.hintBtn} (${shownCount}/${ex.total_hints})`;
    hintBtn.disabled = shownCount >= ex.total_hints;

    const hintsEl = $("hints-list");
    hintsEl.innerHTML = "";
    if (shownCount > 0) {
      hintsEl.classList.remove("hidden");
      state.hintsShown.forEach((h, idx) => {
        const div = document.createElement("div");
        div.className = "hint-item";
        renderMixedProse(div, `${idx + 1}. ${h[state.lang]}`);
        hintsEl.appendChild(div);
      });
    } else {
      hintsEl.classList.add("hidden");
    }

    // Feedback
    if (state.lastGrade) {
      showFeedback(state.lastGrade.message[state.lang], state.lastGrade.is_correct);
    } else {
      $("feedback-box").classList.add("hidden");
    }

    // Solution steps
    const solBox = $("solution-box");
    const stepsEl = $("solution-steps");
    stepsEl.innerHTML = "";
    if (state.solution) {
      solBox.classList.remove("hidden");
      renderInlineMath($("solution-final-math"), state.solution.answer_latex);
      state.solution.steps.forEach((step, idx) => {
        const card = document.createElement("div");
        card.className = "step-card";

        const titleEl = document.createElement("div");
        titleEl.className = "step-title";
        titleEl.textContent = `${idx + 1}. ${step.title[state.lang]}`;

        const explEl = document.createElement("div");
        explEl.className = "step-expl";
        renderMixedProse(explEl, step.explanation[state.lang]);

        card.appendChild(titleEl);
        card.appendChild(explEl);

        if (step.latex) {
          const mathEl = document.createElement("div");
          mathEl.className = "step-math math-box";
          renderDisplayMath(mathEl, step.latex);
          card.appendChild(mathEl);
        }
        stepsEl.appendChild(card);
      });
    } else {
      solBox.classList.add("hidden");
    }
  }

  function showFeedback(message, isCorrect) {
    const fb = $("feedback-box");
    fb.textContent = message;
    fb.className = `feedback-box ${isCorrect ? "feedback-correct" : "feedback-wrong"}`;
    fb.classList.remove("hidden");
  }

  function renderStats() {
    const stats = state.stats;
    if (!stats) return;
    const tr = t();

    $("stat-solved").textContent = String(stats.total_solved);
    $("stat-accuracy").textContent = `${stats.accuracy}%`;
    $("stat-streak").textContent = String(stats.current_streak);

    const barsEl = $("mastery-bars");
    barsEl.innerHTML = "";
    stats.mastery_by_technique.forEach((item) => {
      const pct = Math.round(item.mastery * 100);
      const wrap = document.createElement("div");
      const head = document.createElement("div");
      head.className = "mastery-row-head";
      const label = document.createElement("span");
      label.textContent = tr.techniques[item.technique] || item.technique;
      const val = document.createElement("span");
      val.textContent = `${pct}% (${item.correct}/${item.attempts})`;
      head.appendChild(label);
      head.appendChild(val);

      const track = document.createElement("div");
      track.className = "progress-track";
      const fill = document.createElement("div");
      fill.className = "progress-fill";
      fill.style.width = `${pct}%`;
      track.appendChild(fill);

      wrap.appendChild(head);
      wrap.appendChild(track);
      barsEl.appendChild(wrap);
    });

    const histEl = $("history-list");
    histEl.innerHTML = "";
    if (!stats.recent_history.length) {
      const p = document.createElement("p");
      p.className = "muted-label";
      p.textContent = tr.noHistory;
      histEl.appendChild(p);
      return;
    }

    stats.recent_history.forEach((h) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "history-item";
      btn.addEventListener("click", () => loadNextExercise(h.seed));

      const top = document.createElement("div");
      top.className = "history-top";
      const seedSpan = document.createElement("span");
      seedSpan.dir = "ltr";
      seedSpan.textContent = h.seed;

      const statusSpan = document.createElement("span");
      if (h.is_correct === true) {
        statusSpan.className = "history-status-ok";
        statusSpan.textContent = tr.statusOk;
      } else if (h.is_correct === false) {
        statusSpan.className = "history-status-fail";
        statusSpan.textContent = tr.statusFail;
      } else {
        statusSpan.className = "history-status-pending";
        statusSpan.textContent = tr.statusPending;
      }

      top.appendChild(seedSpan);
      top.appendChild(statusSpan);

      const mathDiv = document.createElement("div");
      mathDiv.className = "history-math math-box";
      renderInlineMath(mathDiv, h.full_limit_latex);

      btn.appendChild(top);
      btn.appendChild(mathDiv);
      histEl.appendChild(btn);
    });
  }

  async function requestNextHint() {
    if (!state.exercise || !state.username) return;
    const idx = state.hintsShown.length;
    if (idx >= state.exercise.total_hints) return;
    const res = await apiJson("/api/exercises/hint", "POST", {
      username: state.username,
      seed: state.exercise.seed,
      hint_index: idx,
    });
    state.hintsShown.push(res.hint);
    renderExerciseArea();
  }

  async function revealSolution() {
    if (!state.exercise || !state.username) return;
    const sol = await apiJson("/api/exercises/solution", "POST", {
      username: state.username,
      seed: state.exercise.seed,
    });
    state.solution = sol;
    renderExerciseArea();
    await refreshStats();
  }

  async function submitAnswer(e) {
    e.preventDefault();
    if (!state.exercise || !state.username) return;
    const raw = $("answer-input").value.trim();
    if (!raw) return;

    const btn = $("submit-btn");
    btn.disabled = true;
    try {
      const res = await apiJson("/api/exercises/submit", "POST", {
        username: state.username,
        seed: state.exercise.seed,
        answer: raw,
      });
      state.lastGrade = res.grade;
      state.stats = res.stats;
      if (res.solution) {
        state.solution = res.solution;
      }
      renderExerciseArea();
      renderStats();
    } catch (err) {
      showFeedback(err.message, false);
    } finally {
      btn.disabled = false;
    }
  }

  function setupKeypad() {
    const input = $("answer-input");
    document.querySelectorAll(".key-btn[data-insert]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const text = btn.dataset.insert || "";
        const offset = Number(btn.dataset.cursorOffset || "0");
        const start = input.selectionStart ?? input.value.length;
        const end = input.selectionEnd ?? input.value.length;
        input.value = input.value.slice(0, start) + text + input.value.slice(end);
        const pos = Math.max(0, start + text.length + offset);
        input.focus();
        input.setSelectionRange(pos, pos);
        updateLivePreview();
      });
    });

    $("clear-answer-btn").addEventListener("click", () => {
      input.value = "";
      input.focus();
      updateLivePreview();
    });

    input.addEventListener("input", updateLivePreview);
  }

  function updateLivePreview() {
    const raw = $("answer-input").value.trim();
    const wrap = $("live-preview-wrap");
    if (!raw) {
      wrap.classList.add("hidden");
      return;
    }
    const previewTex = asciiToPreviewLatex(raw);
    wrap.classList.remove("hidden");
    renderInlineMath($("live-preview-math"), previewTex);
  }

  function init() {
    applyLanguageToStaticUI();
    renderRecentUserChips();
    setupKeypad();

    $("lang-toggle-btn").addEventListener("click", async () => {
      state.lang = state.lang === "ar" ? "fr" : "ar";
      localStorage.setItem("bac_limits_lang", state.lang);
      applyLanguageToStaticUI();
      if (state.username) {
        apiJson(`/api/student/${encodeURIComponent(state.username)}/lang`, "POST", {
          lang: state.lang,
        }).catch(() => {});
      }
    });

    $("login-form").addEventListener("submit", (e) => {
      e.preventDefault();
      loginUser($("username-input").value);
    });

    $("logout-btn").addEventListener("click", logoutUser);
    $("new-exercise-btn").addEventListener("click", () => loadNextExercise());
    $("load-seed-btn").addEventListener("click", () => {
      const seed = $("seed-input").value.trim();
      if (seed) loadNextExercise(seed);
    });

    $("copy-seed-btn").addEventListener("click", async () => {
      if (!state.exercise) return;
      const seed = state.exercise.seed;
      const copied = await copyTextToClipboard(seed);
      if (copied) {
        showCopiedSeedFeedback();
        return;
      }
      // Last-resort manual copy aid if browser policy blocks programmatic copy
      const seedInput = $("seed-input");
      seedInput.value = seed;
      seedInput.focus();
      seedInput.select();
    });

    $("answer-form").addEventListener("submit", submitAnswer);
    $("hint-btn").addEventListener("click", requestNextHint);
    $("solution-btn").addEventListener("click", revealSolution);

    // Wait briefly for deferred KaTeX script, then re-render any mounted formulas
    window.addEventListener("load", () => {
      renderDisplayMath($("hero-formula-preview"), r_heroFormula());
      renderExerciseArea();
      renderStats();
    });

    const savedUser = localStorage.getItem("bac_limits_username");
    if (savedUser) {
      loginUser(savedUser);
    }
  }

  document.addEventListener("DOMContentLoaded", init);
})();
