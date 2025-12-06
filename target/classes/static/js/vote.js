// /static/js/vote.js
(function () {
  /* ================= CSRF ================= */
  const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
  const withCsrf = (headers = {}, method = 'GET') => {
    const h = { ...headers };
    if (method !== 'GET' && csrfToken && csrfHeader) h[csrfHeader] = csrfToken;
    return h;
  };

  /* ================= Util ================= */
  const timers = new Map();
  const toInt  = (v, d = 0) => { const n = Number(v); return Number.isFinite(n) ? Math.trunc(n) : d; };
  const clamp  = (n, min, max) => Math.max(min, Math.min(max, n));

  function $els(feedId) {
    const box = document.querySelector(`.vote-box[data-feed-id="${feedId}"]`);
    const q = (sel) => box ? box.querySelector(sel) : null;
    return {
      box,
      countA: q('[data-part="countA"]'),
      countB: q('[data-part="countB"]'),
      total : q('[data-part="total"]'),
      barA  : q('[data-part="barA"]'),
      barB  : q('[data-part="barB"]'),
      state : q('[data-part="state"]'),
      cd    : q('[data-part="countdown"]'),
      cardA : q('[data-part="cardA"]'),
      cardB : q('[data-part="cardB"]'),
      btnA  : q('[data-part="btnA"]'),
      btnB  : q('[data-part="btnB"]'),
    };
  }

  function setButtons(feedId, enabled) {
    const { btnA, btnB } = $els(feedId);
    [btnA, btnB].forEach(b => {
      if (!b) return;
      b.disabled = !enabled;
      b.classList.toggle('opacity-50', !enabled);
      b.classList.toggle('cursor-not-allowed', !enabled);
    });
  }

  function highlightChoice(feedId, myChoice) {
    const { cardA, cardB } = $els(feedId);
    [cardA, cardB].forEach(c => c && c.classList.remove('ring-2','ring-blue-500','bg-blue-50'));
    const choice = (myChoice === 1 || myChoice === 'A') ? 1 : (myChoice === 2 || myChoice === 'B') ? 2 : null;
    if (choice === 1 && cardA) cardA.classList.add('ring-2','ring-blue-500','bg-blue-50');
    if (choice === 2 && cardB) cardB.classList.add('ring-2','ring-blue-500','bg-blue-50');
  }

  // visible=true일 때만 숫자; 아니면 항상 ??
  function updateBarsAndCounts(feedId, a, b, visible) {
    const { countA, countB, total, barA, barB } = $els(feedId);
    const A = toInt(a, 0), B = toInt(b, 0), sum = A + B;
    const ap = sum > 0 ? Math.round((A * 100) / sum) : 0;
    const bp = sum > 0 ? 100 - ap : 0;

    if (!visible) {
      if (countA) countA.textContent = '??';
      if (countB) countB.textContent = '??';
      if (total)  total.textContent  = '총 ??표';
      if (barA)   barA.style.width   = '0%';
      if (barB)   barB.style.width   = '0%';
    } else {
      if (countA) countA.textContent = String(A);
      if (countB) countB.textContent = String(B);
      if (total)  total.textContent  = `총 ${sum}표`;
      if (barA)   barA.style.width   = `${clamp(ap, 0, 100)}%`;
      if (barB)   barB.style.width   = `${clamp(bp, 0, 100)}%`;
    }
  }

  function fetchSummary(feedId) {
    return fetch(`/api/votes/${feedId}`, { headers: { 'Accept': 'application/json' } })
      .then(r => r.ok ? r.json() : Promise.reject(r));
  }

  /* ============== Countdown/Status ============== */
  function formatRemain(ms) {
    if (ms <= 0) return '00:00:00';
    const s = Math.floor(ms / 1000);
    const h = String(Math.floor(s / 3600)).padStart(2,'0');
    const m = String(Math.floor((s % 3600) / 60)).padStart(2,'0');
    const ss= String(s % 60).padStart(2,'0');
    return `${h}:${m}:${ss}`;
  }

  // 상태 텍스트 즉시 반영 유틸 (API가 status 내려주면 바로 씀)
  function setStateText(feedId, status) {
    const { state } = $els(feedId);
    if (!state) return;
    state.textContent = `상태: ${status || '-'}`;
  }

  function startCountdown(feedId, startISO, endISO) {
    const { state, cd } = $els(feedId);
    if (!state || !cd || !startISO || !endISO) return;

    if (timers.has(feedId)) { clearInterval(timers.get(feedId)); timers.delete(feedId); }

    const start = new Date(startISO);
    const end   = new Date(endISO);

    const tick = () => {
      const now = new Date();

      if (now < start) {
        setStateText(feedId, '투표대기');
        cd.textContent = `시작까지 ${formatRemain(start - now)}`;
        setButtons(feedId, false);
        // 마감 전에는 항상 마스킹(visible=false 유지)
      } else if (now >= start && now < end) {
        setStateText(feedId, '투표중');
        cd.textContent = `마감까지 ${formatRemain(end - now)}`;
        setButtons(feedId, true);
        // 진행 중에도 항상 마스킹
      } else {
        setStateText(feedId, '투표마감');
        cd.textContent = '';
        setButtons(feedId, false);
        // 마감 직후 재조회 → visible=true 내려오면 숫자 공개
        fetchSummary(feedId)
          .then(s => {
            updateBarsAndCounts(feedId, s.countA, s.countB, Boolean(s.visible));
            if (s.myChoice != null) highlightChoice(feedId, s.myChoice);
          })
          .catch(()=>{});

        clearInterval(timers.get(feedId));
        timers.delete(feedId);
      }
    };

    tick();
    timers.set(feedId, setInterval(tick, 1000));
  }

  /* ============== Init ============== */
  function initBox(feedId) {
    fetchSummary(feedId)
      .then(s => {
        // 1) 숫자/막대 (visible=false → ??)
        updateBarsAndCounts(feedId, s.countA, s.countB, Boolean(s.visible));

        // 2) 내 선택 하이라이트
        if (s.myChoice != null) highlightChoice(feedId, s.myChoice);

        // 3) 상태 텍스트 즉시 표시 (API가 status 제공)
        if (s.status) setStateText(feedId, s.status);

        // 4) 카운트다운 시작 (API 시간이 우선, 없으면 data-* 폴백)
        const box = document.querySelector(`.vote-box[data-feed-id="${feedId}"]`);
        const startISO = s.startAt || box?.getAttribute('data-start-iso');
        const endISO   = s.endAt   || box?.getAttribute('data-end-iso');
        if (startISO && endISO) startCountdown(feedId, startISO, endISO);
      })
      .catch(()=>{ /* 초기 실패는 무시 */ });
  }

  /* ============== Vote ============== */
  window.submitVote = function submitVote(feedId, optionId) {
    fetch(`/api/votes/${feedId}/${optionId}`, {
      method: 'POST',
      headers: withCsrf({ 'Accept': 'application/json' }, 'POST'),
      credentials: 'same-origin'
    })
    .then(r => r.ok ? r.json() : r.json().then(j => Promise.reject(j)))
    .then(s => {
      updateBarsAndCounts(feedId, s.countA, s.countB, Boolean(s.visible));
      if (s.myChoice != null) highlightChoice(feedId, s.myChoice);
      // 상태/카운트다운은 주기 갱신/타이머가 담당
    })
    .catch(j => alert((j && j.reason) ? j.reason : '투표 실패'));
  };

  /* ============== Boot ============== */
  window.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.vote-box[data-feed-id]').forEach(box => {
      const feedId = box.getAttribute('data-feed-id');
      if (!feedId) return;

      initBox(feedId);

      // 10초마다 요약 재조회 → 마감되면 visible=true로 자동 공개
      setInterval(() => {
        fetchSummary(feedId)
          .then(s => {
            updateBarsAndCounts(feedId, s.countA, s.countB, Boolean(s.visible));
            if (s.myChoice != null) highlightChoice(feedId, s.myChoice);
            if (s.status) setStateText(feedId, s.status);
          })
          .catch(()=>{});
      }, 10000);
    });
  });
})();
