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
  const toInt  = (v, d = 0) => {
    const n = Number(v);
    return Number.isFinite(n) ? Math.trunc(n) : d;
  };
  const clamp  = (n, min, max) => Math.max(min, Math.min(max, n));
  const isEnded = (endISO) => {
    if (!endISO) return false;
    const end = new Date(endISO);
    return !isNaN(end) && new Date() >= end;
  };

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
    [cardA, cardB].forEach(c => c && c.classList.remove('ring-2', 'ring-blue-500', 'bg-blue-50'));

    const choice =
      (myChoice === 1 || myChoice === 'A') ? 1 :
      (myChoice === 2 || myChoice === 'B') ? 2 : null;

    if (choice === 1 && cardA) cardA.classList.add('ring-2', 'ring-blue-500', 'bg-blue-50');
    if (choice === 2 && cardB) cardB.classList.add('ring-2', 'ring-blue-500', 'bg-blue-50');
  }

  // visible=true면 결과 공개, false면 숫자/막대 숨김
  function updateBarsAndCounts(feedId, a, b, visible) {
    const { countA, countB, total, barA, barB } = $els(feedId);
    const A = toInt(a, 0);
    const B = toInt(b, 0);
    const sum = A + B;
    const ap = sum > 0 ? Math.round((A * 100) / sum) : 0;
    const bp = sum > 0 ? 100 - ap : 0;

    if (!visible) {
      if (countA) countA.textContent = '??';
      if (countB) countB.textContent = '??';
      if (total)  total.textContent  = '총 ??명';
      if (barA)   barA.style.width   = '0%';
      if (barB)   barB.style.width   = '0%';
    } else {
      if (countA) countA.textContent = String(A);
      if (countB) countB.textContent = String(B);
      if (total)  total.textContent  = `총 ${sum}명`;
      if (barA)   barA.style.width   = `${clamp(ap, 0, 100)}%`;
      if (barB)   barB.style.width   = `${clamp(bp, 0, 100)}%`;
    }
  }

  function fetchSummary(feedId) {
    return fetch(`/api/votes/${feedId}`, {
      headers: { 'Accept': 'application/json' }
    }).then(r => r.ok ? r.json() : Promise.reject(r));
  }

  /* ============== Countdown/Status ============== */
  function formatRemain(ms) {
    if (ms <= 0) return '00:00:00';
    const s  = Math.floor(ms / 1000);
    const h  = String(Math.floor(s / 3600)).padStart(2, '0');
    const m  = String(Math.floor((s % 3600) / 60)).padStart(2, '0');
    const ss = String(s % 60).padStart(2, '0');
    return `${h}:${m}:${ss}`;
  }

  // 상태 텍스트 즉시 반영 (API가 status 내려주면 바로 사용)
  function setStateText(feedId, status) {
    const { state } = $els(feedId);
    if (!state) return;
    state.textContent = `상태: ${status || '-'}`;
  }

  function startCountdown(feedId, startISO, endISO) {
    const { state, cd } = $els(feedId);
    if (!state || !cd || !startISO || !endISO) return;

    if (timers.has(feedId)) {
      clearInterval(timers.get(feedId));
      timers.delete(feedId);
    }

    const start = new Date(startISO);
    const end   = new Date(endISO);

    const tick = () => {
      const now = new Date();

      if (now < start) {
        setStateText(feedId, '대기 중');
        cd.textContent = `시작까지 ${formatRemain(start - now)}`;
        setButtons(feedId, false);
        // 시작 전에는 결과 숨기고 버튼 비활성화
      } else if (now >= start && now < end) {
        setStateText(feedId, '진행 중');
        cd.textContent = `마감까지 ${formatRemain(end - now)}`;
        setButtons(feedId, true);
        // 진행 중일 때 버튼 활성화
      } else {
        setStateText(feedId, '마감');
        cd.textContent = '';
        setButtons(feedId, false);

        // 마감 직후 결과 공개
        fetchSummary(feedId)
          .then(s => {
            updateBarsAndCounts(feedId, s.countA, s.countB, true);
            if (s.myChoice != null) highlightChoice(feedId, s.myChoice);
          })
          .catch(() => {});

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
      // 1) box / endISO 한 번만 선언
      const box = document.querySelector(`.vote-box[data-feed-id="${feedId}"]`);
      const endISO = s.endAt || box?.getAttribute('data-end-iso');

      // 마감 여부 따라 visible 결정
      const closed = isEnded(endISO) || String(s.status || '').includes('마감');
      const visibleFlag = closed ? true : Boolean(s.visible);
      updateBarsAndCounts(feedId, s.countA, s.countB, visibleFlag);

      // 내가 선택한 옵션 하이라이트
      if (s.myChoice != null) highlightChoice(feedId, s.myChoice);

      // 상태 텍스트 표시
      if (s.status) setStateText(feedId, s.status);

      // 카운트다운 시작 (endISO는 위에서 선언한 거 재사용)
      const startISO = s.startAt || box?.getAttribute('data-start-iso');
      if (startISO && endISO) startCountdown(feedId, startISO, endISO);
    })
    .catch(() => {
      // 초기 조회 실패는 조용히 무시
    });
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
        const box = document.querySelector(`.vote-box[data-feed-id="${feedId}"]`);
        const endISO = s.endAt || box?.getAttribute('data-end-iso');
        const closed = isEnded(endISO) || String(s.status || '').includes('마감');
        const visibleFlag = closed ? true : Boolean(s.visible);
        updateBarsAndCounts(feedId, s.countA, s.countB, visibleFlag);
        if (s.myChoice != null) highlightChoice(feedId, s.myChoice);
        // 상태/카운트다운은 주기 갱신/타이머에서 처리
      })
      .catch(j => alert((j && j.reason) ? j.reason : '투표에 실패했습니다.'));
  };

  /* ============== Boot ============== */
  window.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.vote-box[data-feed-id]').forEach(box => {
      const feedId = box.getAttribute('data-feed-id');
      if (!feedId) return;

      initBox(feedId);

      // 10초마다 요약 재조회 (마감 후 자동 결과 공개용)
      setInterval(() => {
        fetchSummary(feedId)
          .then(s => {
            const endISO2 = s.endAt || box?.getAttribute('data-end-iso');
            const closed = isEnded(endISO2) || String(s.status || '').includes('마감');
            const visibleFlag = closed ? true : Boolean(s.visible);
            updateBarsAndCounts(feedId, s.countA, s.countB, visibleFlag);
            if (s.myChoice != null) highlightChoice(feedId, s.myChoice);
            if (s.status) setStateText(feedId, s.status);
          })
          .catch(() => {});
      }, 10000);
    });
  });
})();
