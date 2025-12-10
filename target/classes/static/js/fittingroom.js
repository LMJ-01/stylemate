(function () {
  'use strict';

  // ===== 공용 유틸 =====
  const $  = (sel, p = document) => p.querySelector(sel);
  const $$ = (sel, p = document) => Array.from(p.querySelectorAll(sel));
  const byId = (id) => document.getElementById(id);

  const BLANK =
    'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==';

  const proxy = (u) => (!u ? '' : `/img/proxy?url=${encodeURIComponent(u)}`);

  // 배경 제거 서버 (Flask crop 서버)
  const CROP_API_URL = 'http://127.0.0.1:5001/crop';

  // 카테고리 한글 라벨
  const CATEGORY_LABEL_KO = {
    top: '상의',
    bottom: '하의',
    outer: '아우터',
    shoes: '신발',
    accessory: '악세서리',
  };

  // 메인 카테고리 → 서브카테고리 옵션
  const SUBCATEGORY_OPTIONS = {
    top: [
      { value: '', label: '(전체)' },
      { value: 'short_sleeve', label: '반팔' },
      { value: 'long_sleeve', label: '긴팔' },
      { value: 'hoodie', label: '후드티' },
      { value: 'shirt', label: '셔츠' },
      { value: 'sweatshirt', label: '맨투맨' },
    ],
    bottom: [
      { value: '', label: '(전체)' },
      { value: 'short_pants', label: '반바지' },
      { value: 'long_pants', label: '긴바지' },
      { value: 'training', label: '트레이닝 바지' },
      { value: 'jeans', label: '청바지' },
    ],
    outer: [
      { value: '', label: '(전체)' },
      { value: 'windbreaker', label: '바람막이' },
      { value: 'padding', label: '패딩' },
      { value: 'light_padding', label: '경량 패딩' },
      { value: 'coat', label: '코트' },
      { value: 'jacket', label: '자켓' },
    ],
    shoes: [
      { value: '', label: '(전체)' },
      { value: 'running', label: '러닝화' },
      { value: 'sneakers', label: '스니커즈' },
      { value: 'slipper', label: '슬리퍼' },
      { value: 'boots', label: '부츠' },
    ],
    accessory: [
      { value: '', label: '(전체)' },
      { value: 'cap', label: '모자' },
      { value: 'bag', label: '가방' },
      { value: 'socks', label: '양말' },
      { value: 'etc', label: '기타 악세서리' },
    ],
  };

  // 서브카테고리 → 네이버 이미지 검색용 키워드
  const SUBCATEGORY_QUERY_KEYWORD = {
    // top
    short_sleeve: '반팔 티셔츠',
    long_sleeve: '긴팔 티셔츠',
    hoodie: '후드티',
    shirt: '셔츠',
    sweatshirt: '맨투맨',
    // bottom
    short_pants: '반바지',
    long_pants: '긴바지',
    training: '트레이닝 바지',
    jeans: '청바지',
    // outer
    windbreaker: '바람막이',
    padding: '패딩',
    light_padding: '경량 패딩',
    coat: '코트',
    jacket: '자켓',
    // shoes
    running: '러닝화',
    sneakers: '스니커즈',
    slipper: '슬리퍼',
    boots: '부츠',
    // accessory
    cap: '모자',
    bag: '가방',
    socks: '양말',
    etc: '패션 악세서리',
  };

  // 외부에서 호출 가능한 API (다른 스크립트에서 사용)
  window.FittingRoom = {
    equip,
    equipFromDataset,
    equipFromDatasetWithCrop,
  };

  // 레이어 & API & 유저
  let layers = {};
  let API = { random: '', filterAdvanced: '', save: '', searchImages: '' };
  let userId = null;
  // 검색 결과 캐시 & 페이지 상태 (카테고리별)
  const listCache = {};
  const pageState = {};
  const PAGE_SIZE = 24;

  // 위시리스트 상태 (key: category|imageUrl)
  const favorites = new Map();

  // CSRF
  const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

  // ===== 숫자/색상 유틸 =====
  function clamp(v, min, max) {
    return Math.max(min, Math.min(max, v));
  }

  function toNum(x) {
    const n = Number(x);
    return Number.isFinite(n) ? n : NaN;
  }

  function hexToRgb(hex) {
    const m = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex || '');
    return m
      ? {
          r: parseInt(m[1], 16),
          g: parseInt(m[2], 16),
          b: parseInt(m[3], 16),
        }
      : { r: 230, g: 203, b: 179 }; // 기본 피부톤
  }

  function toHex({ r, g, b }) {
    return (
      '#' +
      [r, g, b]
        .map((v) => clamp(v, 0, 255).toString(16).padStart(2, '0'))
        .join('')
    );
  }

  // ===== 이미지 URL 안전 처리 =====
  function safeImg(u) {
    if (!u) return BLANK;
    // data: URL이면 그대로 사용
    if (u.startsWith('data:')) return u;

    // 외부 http(s)도 일단 원본 사용 (proxy 502 대비)
    return u;
  }

  // ===== 데이터셋 + 배경 크롭 후 equip =====
  async function equipFromDatasetWithCrop(card) {
    const d = card.dataset;
    const category = (d.category || getCurrentCategory() || 'top').toLowerCase();

    let imageUrl = safeImg(d.croppedImage || d.image || d.thumb || '');
    if (!imageUrl) {
      console.warn('[equipFromDatasetWithCrop] imageUrl 없음');
      return;
    }

    // 이미 crop된 이미지가 없으면 crop 서버에 요청
    if (!d.croppedImage) {
      const raw = d.croppedImage || d.image || d.thumb || '';
      const absolute = raw.startsWith('http')
        ? raw
        : new URL(raw, location.origin).toString();

      try {
        const res = await fetch(CROP_API_URL, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ imageUrl: absolute }),
        });

        if (res.ok) {
          const data = await res.json();
          if (data && data.success && data.pngBase64) {
            const base64Url = 'data:image/png;base64,' + data.pngBase64;
            imageUrl = base64Url;
            d.croppedImage = base64Url;
          }
        }
      } catch (e) {
        console.warn('[equipFromDatasetWithCrop] crop 실패, 원본 사용:', e);
      }
    }

    equip({
      category,
      name: d.name,
      imageUrl,
      croppedImage: d.croppedImage || null,
    });
  }

  // ===== 초기화 =====
  function init() {
    const endpointsEl = byId('api-endpoints');
    const metaEl = byId('fit-meta');

    userId = metaEl?.dataset.userId || null;

    API = {
      random:
        endpointsEl?.dataset.random ||
        (userId
          ? `/user/profile/${userId}/fittingroom/random`
          : '/fittingroom/random'),
      filterAdvanced:
        endpointsEl?.dataset.filterAdvanced ||
        (userId
          ? `/user/profile/${userId}/fittingroom/filter/advanced`
          : '/fittingroom/filter/advanced'),
      save:
        endpointsEl?.dataset.save ||
        (userId
          ? `/user/profile/${userId}/fittingroom/save`
          : '/fittingroom/save'),
      searchImages: endpointsEl?.dataset.searchImages || '/api/images/search',
    };

    console.log('[FittingRoom] API 설정:', API);

    // 아바타 레이어 노드
    layers = {
      face: byId('layer-face'),
      top: byId('layer-top'),
      bottom: byId('layer-bottom'),
      outer: byId('layer-outer'),
      shoes: byId('layer-shoes'),
      accessory: byId('layer-accessory'),
    };

    // 기존 리스트 카드에 favKey 세팅
    $$('#itemListWrapper [data-role="item-card"]').forEach((card) => {
      const favKey = makeFavKeyFromDataset(card.dataset);
      card.dataset.favKey = favKey;
    });

    // 필터 카테고리 변경 → 탭/서브카테고리 연동
    const filterCategorySel = byId('filter-category');

    function handleCategoryChange() {
      const cat = filterCategorySel.value || 'top';
      updateCurrentCategory(cat);
      switchTab(cat);
      updateSubcategoryOptions(cat);
    }

    filterCategorySel?.addEventListener('change', handleCategoryChange);

    // 상단 탭 클릭 시 필터 카테고리도 같이 변경
    $$('.item-tab').forEach((btn) => {
      btn.addEventListener('click', () => {
        const cat = btn.dataset.category || 'top';
        if (filterCategorySel) filterCategorySel.value = cat;
        handleCategoryChange();
      });
    });

    // 아이템 리스트 영역 클릭 (카드 equip / 하트 토글)
    const wrapper = byId('itemListWrapper');
    if (wrapper) {
      wrapper.addEventListener('click', (e) => {
        const heart = e.target.closest('.like-btn');
        if (heart) {
          e.stopPropagation();
          toggleLike(heart);
          return;
        }

        const card = e.target.closest('[data-role="item-card"]');
        if (card) {
          equipFromDatasetWithCrop(card);
          return;
        }
      });
    }

    // 위시리스트 영역 클릭
    const favWrapper = byId('wishlistWrapper');
    if (favWrapper) {
      favWrapper.addEventListener('click', (e) => {
        const heart = e.target.closest('.like-btn');
        if (heart) {
          e.stopPropagation();
          toggleLike(heart);
          return;
        }
        const card = e.target.closest('[data-role="item-card"]');
        if (card) {
          equipFromDatasetWithCrop(card);
          return;
        }
      });
    }

    // 버튼 이벤트 바인딩
    byId('btn-random-global')?.addEventListener('click', () =>
      alert('랜덤 추천 기능이 비활성화되었습니다.')
    );
    byId('btn-random-by-category')?.addEventListener('click', () =>
      alert('카테고리 랜덤 추천 기능이 비활성화되었습니다.')
    );
    byId('btn-clear-category')?.addEventListener('click', clearCurrentCategory);
    byId('btn-clear-all')?.addEventListener('click', clearAll);
    byId('btn-apply-filters')?.addEventListener('click', applyFilters);
    byId('btn-clear-filters')?.addEventListener('click', clearFilters);
    byId('btn-save-set')?.addEventListener('click', saveSet);
    byId('btn-download')?.addEventListener('click', downloadImage);
    byId('face-upload')?.addEventListener('change', (e) => {
      const file = e.target.files?.[0];
      if (file) handleFaceUpload(file);
    });
    byId('btn-face-clear')?.addEventListener('click', clearFace);

    // 위시리스트 전체 삭제
    byId('btn-clear-wishlist')?.addEventListener('click', () => {
      favorites.clear();
      $$('.like-btn').forEach((h) => {
        h.classList.remove('liked');
        h.textContent = '♡';
      });
      renderFavorites();
    });

    // 초기 카테고리/서브카테고리 설정
    const initialCat = filterCategorySel?.value || 'top';
    updateSubcategoryOptions(initialCat);
    updateCurrentCategory(initialCat);
    switchTab(initialCat);

    // 아바타 기본값 설정 (avatar.json 기준)
    initAvatarBase();

    // 초기 아이템 개수 표시
    syncInitialCount(initialCat);

    // 초기 위시리스트 렌더링
    renderFavorites();
  }

  // 메인 카테고리 변경 시 서브카테고리 옵션 채우기
  function updateSubcategoryOptions(mainCat) {
    const sel = byId('filter-subcategory');
    if (!sel) return;

    const options =
      SUBCATEGORY_OPTIONS[mainCat] || [{ value: '', label: '(전체)' }];

    sel.innerHTML = '';
    options.forEach((opt) => {
      const o = document.createElement('option');
      o.value = opt.value;
      o.textContent = opt.label;
      sel.appendChild(o);
    });
  }

  // ===== 위시 키 생성 =====
  function makeFavKeyFromDataset(d) {
    const cat = (d.category || '').toLowerCase();
    const img = d.image || d.imageUrl || d.thumb || '';
    return `${cat}|${img}`;
  }

  function makeFavKeyFromItem(item, fallbackCategory) {
    const cat = String(item.category || fallbackCategory || '').toLowerCase();
    const img = item.imageUrl || item.thumbUrl || '';
    return `${cat}|${img}`;
  }

  // 여러 카드 하트 모양 동기화
  function syncHearts(key, liked) {
    $$('[data-fav-key]').forEach((card) => {
      if (card.dataset.favKey === key) {
        const heart = card.querySelector('.like-btn');
        if (!heart) return;
        heart.classList.toggle('liked', liked);
        heart.textContent = liked ? '♥' : '♡';
      }
    });
  }

  // ===== 위시리스트 토글 =====
  function toggleLike(heartEl) {
    const card = heartEl.closest('[data-role="item-card"]');
    if (!card) return;

    const key = card.dataset.favKey || makeFavKeyFromDataset(card.dataset);
    if (!key) return;

    const nowLiked = !favorites.has(key);
    if (nowLiked) {
      favorites.set(key, {
        category: card.dataset.category,
        imageUrl:
          card.dataset.croppedImage ||
          card.dataset.image ||
          card.dataset.imageUrl ||
          '',
        name: card.dataset.name,
        brand: card.dataset.brand,
        color: card.dataset.color,
        price: card.dataset.price ? Number(card.dataset.price) : null,
      });
    } else {
      favorites.delete(key);
    }

    syncHearts(key, nowLiked);
    renderFavorites();
  }

  function updateCurrentCategory(cat) {
    const span = byId('current-category');
    if (span) span.textContent = cat || '(전체)';
    const select = byId('filter-category');
    if (select) select.value = cat;
  }

  function switchTab(cat) {
    const tabs = $$('.item-tab');
    tabs.forEach((t) => {
      if (t.dataset.category === cat) {
        t.classList.remove('bg-gray-100', 'text-gray-700');
        t.classList.add('bg-gray-900', 'text-white');
      } else {
        t.classList.add('bg-gray-100', 'text-gray-700');
        t.classList.remove('bg-gray-900', 'text-white');
      }
    });

    const wrapper = byId('itemListWrapper');
    if (!wrapper) return;
    const lists = $$('.item-list', wrapper);
    lists.forEach((list) => {
      if (list.id === `itemList-${cat}`) {
        list.classList.remove('hidden');
      } else {
        list.classList.add('hidden');
      }
    });
  }

  function syncInitialCount(cat) {
    const list = byId(`itemList-${cat}`);
    const count = list ? list.querySelectorAll('[data-role="item-card"]').length : 0;
    const countEl = byId('itemCount');
    if (countEl) countEl.textContent = `(${count}개)`;
  }

  // ===== 아바타 비율에 맞게 옷 레이아웃 조정 =====
  function applyClothesLayoutForAvatar(stage) {
    if (!stage) return;

    const stageW = stage.clientWidth || 300;
    const stageH = stage.clientHeight || 500;

    const hScale =
      parseFloat(stage.style.getPropertyValue('--heightScale') || '1') || 1;
    const wScale =
      parseFloat(stage.style.getPropertyValue('--weightScale') || '1') || 1;
    const legH =
      parseFloat(stage.style.getPropertyValue('--legHeight') || '1') || 1;
    const body = (stage.dataset.bodyShape || 'regular').toLowerCase();
    const shoulderScale =
      parseFloat(stage.style.getPropertyValue('--shoulderScale') || '1') || 1;

    // === 기준 앵커(고정 비율) ===
    const shoulderY = 0.26 * stageH;
    const waistY    = 0.50 * stageH;
    const hipY      = 0.62 * stageH;
    const footY     = 0.93 * stageH;

    // === Y 위치: 의류별 기준점 ===
    const topYPct    = ((shoulderY + 0.20 * stageH) / stageH) * 100;
    const outerYPct  = topYPct;
    const bottomYPct = ((hipY + 0.16 * stageH) / stageH) * 100;
    const shoesYPct  = ((footY - 0.04 * stageH) / stageH) * 100;
    const accessoryYPct = 30;

    // === 스케일 ===
    let topScale    = 1.05 + (wScale - 1) * 0.45;
    let bottomScale = 1.20 + (wScale - 1) * 0.65;
    let shoesScale  = 1.05 + (wScale - 1) * 0.25;
    let outerScale  = topScale * 1.02;

    if (body === 'slim') {
      topScale    -= 0.05;
      bottomScale -= 0.05;
    } else if (body === 'plus') {
      topScale    += 0.08;
      bottomScale += 0.08;
    }

    // === 너비(px) 기준 -> % 변환 ===
    const baseTorsoWidth = stageW * 0.58;
    const baseOuterWidth = stageW * 0.62;
    const baseLegWidth   = stageW * 0.60;
    const baseFootWidth  = stageW * 0.38;
    const baseAccessoryWidth = stageW * 0.30;

    const topWidthPx =
      baseTorsoWidth * clamp(1 + (wScale - 1) * 0.8 + (shoulderScale - 1) * 0.55, 0.95, 1.5);
    const outerWidthPx =
      baseOuterWidth * clamp(1 + (wScale - 1) * 0.8 + (shoulderScale - 1) * 0.6, 0.85, 1.4);
    const bottomWidthPx =
      baseLegWidth * clamp(1 + (wScale - 1) * 0.8, 0.95, 1.4);
    const shoesWidthPx =
      baseFootWidth * clamp(1 + (wScale - 1) * 0.3, 0.9, 1.4);
    const accessoryWidthPx =
      baseAccessoryWidth * clamp(1 + (wScale - 1) * 0.2, 0.8, 1.2);

    const toPct = (px) => ((px / stageW) * 100).toFixed(1);

    stage.style.setProperty('--topY', `${topYPct.toFixed(2)}%`);
    stage.style.setProperty('--outerY', `${outerYPct.toFixed(2)}%`);
    stage.style.setProperty('--bottomY', `${bottomYPct.toFixed(2)}%`);
    stage.style.setProperty('--shoesY', `${shoesYPct.toFixed(2)}%`);
    stage.style.setProperty('--accessoryY', `${accessoryYPct}%`);

    stage.style.setProperty('--topScale', topScale.toFixed(2));
    stage.style.setProperty('--bottomScale', bottomScale.toFixed(2));
    stage.style.setProperty('--shoesScale', shoesScale.toFixed(2));
    stage.style.setProperty('--outerScale', outerScale.toFixed(2));

    stage.style.setProperty('--topWidth', `${toPct(topWidthPx)}%`);
    stage.style.setProperty('--outerWidth', `${toPct(outerWidthPx)}%`);
    stage.style.setProperty('--bottomWidth', `${toPct(bottomWidthPx)}%`);
    stage.style.setProperty('--shoesWidth', `${toPct(shoesWidthPx)}%`);
    stage.style.setProperty('--accessoryWidth', `${toPct(accessoryWidthPx)}%`);
  }

  // ===== avatar.json 기반 아바타 초기화 =====
  async function initAvatarBase() {
    const base = byId('avatarBase');
    if (!base) return;

    const stage = base.closest('.avatar-stage');
    const inner = stage?.querySelector('.avatar-inner');

    const url = base.dataset.avatarUrl;
    if (!url) {
      console.warn('[avatarBase] data-avatar-url 없음');
      return;
    }

    try {
      const res = await fetch(url, { cache: 'no-store' });
      if (!res.ok) throw new Error('avatar.json 불러오기 실패');

      const a = await res.json();
      console.log('[avatarBase] avatar.json:', a);

      const heightCm = toNum(a.heightCm) || 175;
      const weightKg = toNum(a.weightKg) || 70;
      const body = String(a.bodyShape || 'regular').toLowerCase();
      const toneBrightness = clamp(
        toNum(a.toneBrightness) || 1.0,
        0.85,
        1.15
      );
      const skinHex = a.skinToneHex || a.skinTone || '#e6cbb3';

      let hScale =
        a.heightScale != null ? toNum(a.heightScale) : heightCm / 175;
      let wScale =
        a.weightScale != null ? toNum(a.weightScale) : weightKg / 70;

      hScale = clamp(hScale, 0.85, 1.15);
      wScale = clamp(wScale, 0.85, 1.3);

      const shoulderScale = clamp(toNum(a.shoulderScale) || 1.0, 0.9, 1.3);
      const headScale     = clamp(toNum(a.headScale) || 1.0, 0.85, 1.2);

      let bodyThickness = 1.0;
      switch (body) {
        case 'slim':
          bodyThickness = 0.9;
          break;
        case 'plus':
          bodyThickness = 1.15;
          break;
        default:
          bodyThickness = 1.0;
      }

      const bodyHeight = hScale;
      const legHeight = clamp(0.9 + (hScale - 1) * 1.2, 0.8, 1.3);

      const baseRgb = hexToRgb(skinHex);
      const brightSkin = toHex({
        r: Math.round(baseRgb.r * toneBrightness),
        g: Math.round(baseRgb.g * toneBrightness),
        b: Math.round(baseRgb.b * toneBrightness),
      });

      if (stage) {
        stage.style.setProperty('--skin', brightSkin);
        stage.style.setProperty('--heightScale', hScale.toFixed(2));
        stage.style.setProperty('--weightScale', wScale.toFixed(2));
        stage.style.setProperty('--bodyThickness', bodyThickness.toFixed(2));
        stage.style.setProperty('--bodyHeight', bodyHeight.toFixed(2));
        stage.style.setProperty('--legHeight', legHeight.toFixed(2));
        stage.style.setProperty('--shoulderScale', shoulderScale.toFixed(2));
        stage.style.setProperty('--headScale', headScale.toFixed(2));
        stage.style.setProperty('--toneBrightness', toneBrightness.toFixed(2));

        stage.dataset.bodyShape = body;
        stage.dataset.heightCm = String(heightCm);
        stage.dataset.weightKg = String(weightKg);
      }

      if (inner) {
        inner.style.setProperty('--heightScale', hScale.toFixed(2));
        inner.style.setProperty('--weightScale', wScale.toFixed(2));
      }

      const svg = document.getElementById('avatar-svg');
      if (svg) {
        svg.querySelectorAll('.avatar-skin').forEach((el) => {
          el.style.fill = brightSkin;
        });

        const head      = svg.querySelector('#head');
        const torso     = svg.querySelector('#torso');
        const shoulders = svg.querySelector('#shoulders');
        const armL      = svg.querySelector('#armL');
        const armR      = svg.querySelector('#armR');
        const legL      = svg.querySelector('#legL');
        const legR      = svg.querySelector('#legR');

        let torsoRx = 60,
          torsoRy = 95,
          legRx = 28,
          legRy = 95,
          armRx = 22,
          armRy = 60;
        let shoulderW = 150;

        switch (body) {
          case 'slim':
            torsoRx = 54;
            legRx = 24;
            armRx = 19;
            shoulderW = 140;
            break;
          case 'regular':
            torsoRx = 60;
            legRx = 28;
            armRx = 22;
            shoulderW = 150;
            break;
          case 'plus':
            torsoRx = 70;
            legRx = 33;
            armRx = 25;
            shoulderW = 165;
            break;
        }

        if (head) head.setAttribute('r', String(45 * headScale));

        if (torso) {
          torso.setAttribute('rx', String(torsoRx * wScale));
          torso.setAttribute('ry', String(torsoRy * hScale));
        }

        if (shoulders) {
          shoulders.setAttribute(
            'x',
            String(-(shoulderW * shoulderScale) / 2)
          );
          shoulders.setAttribute(
            'width',
            String(shoulderW * shoulderScale)
          );
        }

        if (armL) armL.setAttribute('rx', String(armRx * wScale));
        if (armR) armR.setAttribute('rx', String(armRx * wScale));
        if (legL) legL.setAttribute('rx', String(legRx * wScale));
        if (legR) legR.setAttribute('rx', String(legRx * wScale));
      }

      if (stage) {
        applyClothesLayoutForAvatar(stage);
      }
    } catch (e) {
      console.warn('[avatarBase] 초기화 실패:', e);
    }
  }

  // ===== 전체 랜덤 추천 (DB) - 현재 버튼에서 비활성화 알림만 사용 중 =====
  async function randomGlobal() {
    try {
      const r = await fetch(API.random, { method: 'GET', cache: 'no-store' });
      if (!r.ok) throw new Error('랜덤 추천 불러오기 실패');
      const it = await r.json();
      if (!it) {
        alert('현재 DB에 저장된 코디 아이템이 없습니다.');
        return;
      }
      it.imageUrl = safeImg(it.imageUrl);
      it.thumbUrl = safeImg(it.thumbUrl);
      equip(it);
    } catch (e) {
      console.error(e);
      alert('랜덤 추천 중 오류가 발생했습니다.');
    }
  }

  // ===== 카테고리별 랜덤 추천 (DB) - 현재 버튼에서 비활성화 알림만 사용 중 =====
  async function randomByCategory() {
    const cat = byId('filter-category')?.value?.trim();
    if (!cat) return alert('먼저 카테고리를 선택해주세요.');

    try {
      const u = new URL(API.filterAdvanced, location.origin);
      u.searchParams.set('category', cat);
      const r = await fetch(u.toString(), { method: 'GET', cache: 'no-store' });
      if (!r.ok) throw new Error('필터 요청 실패');
      const items = await r.json();
      if (!Array.isArray(items) || items.length === 0) {
        return alert(
          '해당 카테고리에는 DB에 저장된 아이템이 없습니다.\n필터를 조정하거나 다시 시도해 주세요.'
        );
      }
      const pick = items[Math.floor(Math.random() * items.length)] || {};
      pick.imageUrl = safeImg(pick.imageUrl);
      pick.thumbUrl = safeImg(pick.thumbUrl);
      equip(pick);
    } catch (e) {
      console.error(e);
      alert('카테고리 랜덤 추천 중 오류가 발생했습니다.');
    }
  }

  // ===== 카테고리/전체 지우기 =====
  function clearCurrentCategory() {
    const cat = byId('filter-category')?.value?.trim();
    if (!cat) return alert('지울 카테고리를 먼저 선택해주세요.');

    const valid = ['top', 'bottom', 'outer', 'shoes', 'accessory'];
    if (!valid.includes(cat)) return;

    const layer = layers[cat];
    if (layer) {
      layer.removeAttribute('src');
      layer.alt = '';
    }
  }

  function clearAll() {
    ['top', 'bottom', 'outer', 'shoes', 'accessory'].forEach((key) => {
      const l = layers[key];
      if (!l) return;
      l.removeAttribute('src');
      l.alt = '';
    });
  }

  function setHeadVisible(visible) {
    const svgHead = document.getElementById('head-group');
    const baseHead = document.getElementById('head');
    const opacity = visible ? '1' : '0';
    if (svgHead) svgHead.style.opacity = opacity;
    if (baseHead) baseHead.style.opacity = opacity;
  }

  // ===== 얼굴 업로드/제거 =====
  function handleFaceUpload(file) {
    if (!file || !layers.face) return;
    const maxSize = 2 * 1024 * 1024; // 2MB
    if (file.size > maxSize) {
      alert('이미지 용량이 2MB를 초과합니다.');
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      layers.face.src = reader.result;
      layers.face.alt = '사용자 얼굴';
      setHeadVisible(false); // 기본 아바타 얼굴 숨기기
    };
    reader.readAsDataURL(file);
  }

  function clearFace() {
    if (layers.face) {
      layers.face.removeAttribute('src');
      layers.face.alt = '';
    }
    const input = byId('face-upload');
    if (input) input.value = '';
    setHeadVisible(true); // 기본 아바타 얼굴 다시 보이기
  }

  // ===== 필터 파라미터 수집 =====
  function collectFilterParams() {
    const obj = {};
    const get = (id) => byId(id)?.value?.trim();
    const cat     = get('filter-category');
    const sub     = get('filter-subcategory');
    const color   = get('filter-color');
    const brand   = get('filter-brand');
    const gender  = get('filter-gender');
    const maxPriceRaw = get('filter-price');

    if (cat) obj.category = cat;
    if (sub) obj.subCategory = sub;
    if (color) obj.color = color;
    if (brand) obj.brand = brand;
    if (gender) obj.gender = gender;

    const p = parseInt(maxPriceRaw || '', 10);
    if (Number.isFinite(p) && p >= 0) obj.maxPrice = p;
    return obj;
  }

  // 서브카테고리 value → 라벨 텍스트
  function subcategoryLabel(value) {
    if (!value) return '';
    const sel = byId('filter-subcategory');
    const opt = sel?.querySelector(`option[value="${value}"]`);
    return opt?.textContent?.trim() || '';
  }

  // 네이버 이미지 검색용 query 조합 (사람 신체 텍스트 필터는 후처리에서 처리)
  // primary: 자연스러운 키워드 조합, fallback: 더 단순한 쿼리
  function buildNaverQuery(params) {
    const parts = [];
    const catKo = CATEGORY_LABEL_KO[params.category || 'top'];

    // 검색어 우선순위: 브랜드 > 색상 > 서브카테고리 > 메인카테고리 > 성별
    if (params.brand) parts.push(params.brand);
    if (params.color) parts.push(params.color);

    if (params.subCategory) {
      const subKey = SUBCATEGORY_QUERY_KEYWORD[params.subCategory];
      if (subKey) {
        parts.push(subKey);
      } else {
        const subLabel = subcategoryLabel(params.subCategory);
        if (subLabel) parts.push(subLabel);
      }
    }

    if (catKo) parts.push(catKo);

    if (params.gender === 'male')   parts.push('남성');
    if (params.gender === 'female') parts.push('여성');
    if (params.gender === 'unisex') parts.push('공용');

    const base = parts.join(' ').trim();
    const primary = base || '의류';
    const fallback = base || '옷';
    return { primary, fallback };
  }

  // ===== 필터 적용 (DB + 네이버 이미지) =====
  async function applyFilters() {
    const params   = collectFilterParams();
    const category = params.category || 'top';

    const wrapper    = byId('itemListWrapper');
    const emptyEl    = byId('itemListEmpty');
    const targetList = byId(`itemList-${category}`);

    if (targetList) {
      targetList.innerHTML =
        '<div class="col-span-full text-center text-gray-500 py-8">코디 아이템을 불러오는 중입니다...</div>';
      targetList.classList.remove('hidden');
    }
    if (emptyEl) emptyEl.classList.add('hidden');

    try {
      // 1) DB 필터
      const dbPromise = API.filterAdvanced
        ? (async () => {
            const u = new URL(API.filterAdvanced, location.origin);
            Object.entries(params).forEach(([k, v]) => {
              if (v !== undefined && v !== null && String(v).trim() !== '') {
                u.searchParams.set(k, String(v).trim());
              }
            });
            console.log('[FittingRoom] DB 필터 요청:', u.toString());
            const r = await fetch(u.toString(), {
              method: 'GET',
              cache: 'no-store',
            });
            console.log('[FittingRoom] DB 응답 status:', r.status);
            return r.ok ? r.json() : [];
          })()
        : Promise.resolve([]);

      // 2) 네이버 이미지 검색 (0개일 때는 fallback 쿼리 재시도)
      const { primary: qPrimary, fallback: qFallback } = buildNaverQuery(params);
      const navPromise =
        API.searchImages && qPrimary
          ? (async () => {
              const fetchNaver = async (q) => {
                const uImg = new URL(API.searchImages, location.origin);
                uImg.searchParams.set('query', q);
                uImg.searchParams.set('display', '80');
                console.log('[FittingRoom] 이미지 검색 요청:', q, '=>', uImg.toString());
                const r = await fetch(uImg.toString(), {
                  method: 'GET',
                  cache: 'no-store',
                });
                console.log('[FittingRoom] 이미지 검색 응답 status:', r.status);
                return r.ok ? r.json() : [];
              };

              let result = await fetchNaver(qPrimary);
              // 0개일 때 fallback으로 한 번 더 시도
              const arr = Array.isArray(result)
                ? result
                : Array.isArray(result.items)
                ? result.items
                : Array.isArray(result.result)
                ? result.result
                : [];
              if (arr.length === 0 && qFallback) {
                result = await fetchNaver(qFallback);
              }
              return result;
            })()
          : Promise.resolve([]);

      const [dbRaw, navRaw] = await Promise.all([dbPromise, navPromise]);

      const dbItems = Array.isArray(dbRaw) ? dbRaw : [];

      let navArray = [];
      if (Array.isArray(navRaw)) {
        navArray = navRaw;
      } else if (navRaw && Array.isArray(navRaw.items)) {
        navArray = navRaw.items;
      } else if (navRaw && Array.isArray(navRaw.result)) {
        navArray = navRaw.result;
      }

      const mappedDbItems = dbItems
        .filter((it) => it && it.imageUrl)
        .map((it) => ({
          id: it.id,
          name: it.name,
          brand: it.brand,
          category: it.category || category,
          color: it.color,
          price: it.price,
          imageUrl: it.imageUrl,
          thumbUrl: it.thumbUrl || it.imageUrl,
          croppedImage: null,
          source: 'DB',
        }));

      const badWords = [
        '모델', '착용', '착샷', '착장', '룩북', '패션쇼',
        '얼굴', '사람',
        'face', 'people', 'person', 'human', 'body'
      ];
      const mappedNaverItems = navArray
        .filter((it) => it)
        .map((it) => {
          const imageUrl =
            it.imageUrl || it.link || it.thumbnail || it.thumbnailUrl;
          const titleRaw = it.title || it.name || '';
          const title = titleRaw.replace(/<[^>]+>/g, '');
          const croppedImage = it.croppedImage || null;

          return {
            id: it.id,
            name: title,
            brand: params.brand || it.brand || '',
            category,
            color: params.color || it.color || '',
            price: it.price != null ? it.price : null,
            imageUrl,
            thumbUrl:
              it.thumbUrl || it.thumbnail || it.thumbnailUrl || imageUrl,
            croppedImage,
            source: 'NAVER',
          };
        })
        .filter((it) => it.imageUrl)
        .filter((it) => {
          const text = (it.name || '').toLowerCase();
          return !badWords.some((w) => text.includes(w));
        });

      // 필터 후 0개라면 필터를 완화해(부정어 무시) 원본 그대로 사용
      const finalNavItems = mappedNaverItems.length > 0 ? mappedNaverItems : navArray;

      console.log(
        '[FittingRoom] 필터 결과 - DB:',
        mappedDbItems.length,
        '개, 네이버(필터 후):',
        mappedNaverItems.length,
        '개, 네이버(최종):',
        finalNavItems.length,
        '개'
      );

      const allItems = [...mappedDbItems, ...finalNavItems];
      renderGrid(allItems, category, 1, PAGE_SIZE);

      updateCurrentCategory(category);
      switchTab(category);
    } catch (e) {
      console.error(e);
      if (targetList) {
        targetList.innerHTML =
          '<div class="col-span-full text-center text-red-500 py-8">검색 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.</div>';
      }
    }
  }

  function clearFilters() {
    byId('filter-category').value = 'top';
    const subSel = byId('filter-subcategory');
    if (subSel) subSel.selectedIndex = 0;
    byId('filter-color').value  = '';
    byId('filter-brand').value  = '';
    byId('filter-gender').value = '';
    byId('filter-price').value  = '';
    updateCurrentCategory('top');
    updateSubcategoryOptions('top');
    switchTab('top');
  }

  // ===== 그리드 렌더링 (페이징) =====
  function renderGrid(items, category, page = 1, pageSize = PAGE_SIZE) {
    const wrapper = byId('itemListWrapper');
    if (!wrapper) return;

    const emptyEl = byId('itemListEmpty');
    const lists = $$('.item-list', wrapper);
    lists.forEach((list) => {
      list.classList.add('hidden');
      list.innerHTML = '';
    });

    const valid = (Array.isArray(items) ? items : []).filter(
      (it) => !!it.imageUrl
    );
    // 캐시 저장 & 페이지 상태
    listCache[category] = valid;
    const total = valid.length;
    const totalPages = Math.max(1, Math.ceil(total / pageSize));
    const safePage = Math.min(Math.max(page, 1), totalPages);
    pageState[category] = safePage;
    const startIdx = (safePage - 1) * pageSize;
    const endIdx = startIdx + pageSize;
    const pageItems = valid.slice(startIdx, endIdx);

    const targetId = `itemList-${category}`;
    let target = byId(targetId) || lists[0];

    if (!target) return;

    if (total === 0) {
      target.classList.remove('hidden');
      if (emptyEl) emptyEl.classList.remove('hidden');
      const countEl = byId('itemCount');
      if (countEl) countEl.textContent = '(0개)';
      return;
    }

    if (emptyEl) emptyEl.classList.add('hidden');

    pageItems.forEach((c) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.setAttribute('data-role', 'item-card');
      btn.className =
        'item-card flex flex-col items-center gap-1 border rounded-lg p-1 text-[10px] hover:border-gray-900 transition';

      btn.dataset.id       = c.id != null ? String(c.id) : '';
      btn.dataset.name     = c.name || '';
      btn.dataset.brand    = c.brand || '';
      btn.dataset.category = c.category || category;
      btn.dataset.color    = c.color || '';
      btn.dataset.price    = c.price != null ? String(c.price) : '0';
      btn.dataset.image    = c.imageUrl || '';
      btn.dataset.thumb    = c.thumbUrl || '';
      btn.dataset.croppedImage = c.croppedImage || '';

      const favKey = makeFavKeyFromItem(c, category);
      btn.dataset.favKey = favKey;
      const isLiked = favorites.has(favKey);

      const imgWrap = document.createElement('div');
      imgWrap.className =
        'w-full aspect-[3/4] overflow-hidden rounded bg-gray-50 flex items-center justify-center';

      const img = document.createElement('img');
      img.referrerPolicy = 'no-referrer';
      img.src = safeImg(c.croppedImage || c.imageUrl);
      img.alt = c.name || '';
      img.className = 'w-full h-full object-contain';
      img.loading = 'lazy';
      img.onerror = () => {
        if (!img.dataset.retried && c.imageUrl && c.imageUrl.startsWith('http')) {
          img.dataset.retried = '1';
          img.src = c.imageUrl; // proxy 실패 시 원본 재시도
        } else {
          img.src = BLANK;
        }
      };
      imgWrap.appendChild(img);

      const textWrap = document.createElement('div');
      textWrap.className = 'w-full text-left space-y-0.5';

      const brandEl = document.createElement('p');
      brandEl.className = 'truncate font-medium';
      brandEl.textContent = c.brand || (c.source === 'NAVER' ? 'NAVER' : '');
      textWrap.appendChild(brandEl);

      const nameEl = document.createElement('p');
      nameEl.className = 'truncate text-[10px] text-gray-500';
      nameEl.textContent = c.name || '';
      textWrap.appendChild(nameEl);

      const priceRow = document.createElement('div');
      priceRow.className = 'flex items-center justify-between mt-1';

      const priceEl = document.createElement('p');
      priceEl.className = 'text-[10px] font-semibold text-gray-900';
      priceEl.textContent =
        c.price != null
          ? `${Number(c.price || 0).toLocaleString()}`
          : c.source === 'NAVER'
          ? '네이버 이미지'
          : '';
      priceRow.appendChild(priceEl);

      const heartEl = document.createElement('span');
      heartEl.className = 'like-btn text-lg';
      if (isLiked) {
        heartEl.classList.add('liked');
        heartEl.textContent = '♥';
      } else {
        heartEl.textContent = '♡';
      }
      priceRow.appendChild(heartEl);

      textWrap.appendChild(priceRow);

      const sourceEl = document.createElement('p');
      sourceEl.className = 'text-[9px] text-gray-400';
      sourceEl.textContent =
        c.source === 'NAVER' ? '네이버 이미지' : '내 DB';
      textWrap.appendChild(sourceEl);

      btn.appendChild(imgWrap);
      btn.appendChild(textWrap);
      target.appendChild(btn);
    });

    target.classList.remove('hidden');

    const countEl = byId('itemCount');
    if (countEl) countEl.textContent = `(${total}개)`;

    // 페이지네이션
    const existingPager = target.querySelector('.pager');
    if (existingPager) existingPager.remove();
    if (totalPages > 1) {
      const pager = document.createElement('div');
      pager.className = 'pager col-span-full mt-3 flex flex-wrap gap-2 justify-center items-center text-xs';

      const makeBtn = (label, disabled, cb) => {
        const b = document.createElement('button');
        b.type = 'button';
        b.textContent = label;
        b.disabled = disabled;
        b.className =
          'px-2 py-1 rounded border ' +
          (disabled ? 'text-gray-400 border-gray-200 cursor-not-allowed' : 'text-gray-700 border-gray-300 hover:bg-gray-100');
        if (!disabled) b.addEventListener('click', cb);
        return b;
      };

      pager.appendChild(makeBtn('이전', safePage === 1, () => renderGrid(listCache[category], category, safePage - 1, pageSize)));

      for (let p = 1; p <= totalPages; p++) {
        const b = makeBtn(String(p), p === safePage, () => renderGrid(listCache[category], category, p, pageSize));
        if (p === safePage) {
          b.classList.remove('border-gray-300', 'text-gray-700');
          b.classList.add('bg-gray-900', 'text-white', 'border-gray-900');
        }
        pager.appendChild(b);
      }

      pager.appendChild(makeBtn('다음', safePage === totalPages, () => renderGrid(listCache[category], category, safePage + 1, pageSize)));

      target.appendChild(pager);
    }
  }

  // ===== 위시리스트 렌더링 =====
  function renderFavorites() {
    const wrapper = byId('wishlistWrapper');
    const emptyEl = byId('wishlistEmpty');
    if (!wrapper) return;

    wrapper
      .querySelectorAll('[data-role="item-card"]')
      .forEach((el) => el.remove());

    const arr = Array.from(favorites.entries());
    const size = arr.length;

    if (size === 0) {
      if (emptyEl) emptyEl.classList.remove('hidden');
      return;
    }
    if (emptyEl) emptyEl.classList.add('hidden');

    arr.forEach(([key, item]) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.setAttribute('data-role', 'item-card');
      btn.className =
        'item-card flex flex-col items-center gap-1 border rounded-lg p-1 text-[10px] hover:border-gray-900 transition';

      btn.dataset.favKey   = key;
      btn.dataset.category = item.category || '';
      btn.dataset.image    = item.imageUrl || '';
      btn.dataset.name     = item.name || '';
      btn.dataset.brand    = item.brand || '';
      btn.dataset.color    = item.color || '';
      btn.dataset.price    = item.price != null ? String(item.price) : '0';

      const imgWrap = document.createElement('div');
      imgWrap.className =
        'w-full aspect-[3/4] overflow-hidden rounded bg-gray-50 flex items-center justify-center';

      const img = document.createElement('img');
      img.referrerPolicy = 'no-referrer';
      img.src = safeImg(item.imageUrl);
      img.alt = item.name || '';
      img.className = 'w-full h-full object-contain';
      img.loading = 'lazy';
      img.onerror = () => {
        if (!img.dataset.retried && item.imageUrl && item.imageUrl.startsWith('http')) {
          img.dataset.retried = '1';
          img.src = item.imageUrl;
        } else {
          img.src = BLANK;
        }
      };
      imgWrap.appendChild(img);

      const textWrap = document.createElement('div');
      textWrap.className = 'w-full text-left space-y-0.5';

      const brandEl = document.createElement('p');
      brandEl.className = 'truncate font-medium';
      brandEl.textContent = item.brand || '';
      textWrap.appendChild(brandEl);

      const nameEl = document.createElement('p');
      nameEl.className = 'truncate text-[10px] text-gray-500';
      nameEl.textContent = item.name || '';
      textWrap.appendChild(nameEl);

      const priceRow = document.createElement('div');
      priceRow.className = 'flex items-center justify-between mt-1';

      const priceEl = document.createElement('p');
      priceEl.className = 'text-[10px] font-semibold text-gray-900';
      priceEl.textContent =
        item.price != null
          ? `${Number(item.price || 0).toLocaleString()}`
          : '';
      priceRow.appendChild(priceEl);

      const heartEl = document.createElement('span');
      heartEl.className = 'like-btn text-lg liked';
      heartEl.textContent = '♥';
      priceRow.appendChild(heartEl);

      textWrap.appendChild(priceRow);

      btn.appendChild(imgWrap);
      btn.appendChild(textWrap);

      wrapper.appendChild(btn);
    });
  }

  // ===== 현재 카테고리 =====
  function getCurrentCategory() {
    return byId('filter-category')?.value || 'top';
  }

  // ===== 실제로 아바타에 옷 입히기 =====
  function equip(item) {
    const category = (item.category || getCurrentCategory() || 'top').toLowerCase();
    const layer = layers[category];
    if (!layer) {
      console.warn('[equip] 존재하지 않는 카테고리:', category);
      return;
    }

    const url = safeImg(item.croppedImage || item.imageUrl || item.thumbUrl);
    layer.src = url || BLANK;
    layer.alt = item.name || '';
  }

  // dataset만 받아서 equip
  function equipFromDataset(card) {
    const d = card.dataset;
    equip({
      category: d.category,
      name: d.name,
      imageUrl: d.croppedImage || d.image || d.thumb || d.imageUrl,
    });
  }

  // ===== 코디 세트 저장 (DB) =====
  async function saveSet() {
    const name = byId('set-name')?.value?.trim() || '';
    const payload = {
      name,
      topImage:       layers.top?.src || null,
      bottomImage:    layers.bottom?.src || null,
      outerImage:     layers.outer?.src || null,
      shoesImage:     layers.shoes?.src || null,
      accessoryImage: layers.accessory?.src || null,
      faceImage:      layers.face?.src || null,
    };

    try {
      const res = await fetch(API.save, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(csrfHeader && csrfToken ? { [csrfHeader]: csrfToken } : {}),
        },
        body: JSON.stringify(payload),
      });

      if (res.ok) {
        const redirectUrl = userId
          ? `/user/profile/${userId}/fittingroom/saved`
          : `/fittingroom/saved`;
        window.location.href = redirectUrl;
        return;
      }

      const text = await res.text();
      console.error('[saveSet] 실패', res.status, text);
      alert(text || '저장 중 오류가 발생했습니다.');
    } catch (e) {
      console.error('[saveSet] 예외', e);
      alert('코디 저장 중 오류가 발생했습니다. (콘솔 로그 확인)');
    }
  }

  // ===== 아바타 이미지 다운로드 (html2canvas) =====
  async function downloadImage() {
    const stage = $('.avatar-stage');
    if (!stage) return;

    $$('img.layer', stage).forEach((img) => {
      if (img && !img.crossOrigin) img.crossOrigin = 'anonymous';
      if (img && !img.referrerPolicy) img.referrerPolicy = 'no-referrer';
    });

    try {
      const canvas = await html2canvas(stage, {
        useCORS: true,
        allowTaint: false,
        backgroundColor: null,
        scale: 2,
      });
      const a = document.createElement('a');
      a.download = 'fittingroom_' + Date.now() + '.png';
      a.href = canvas.toDataURL('image/png');
      a.click();
    } catch (e) {
      console.error(e);
      alert(
        '이미지 다운로드 중 오류가 발생했습니다.\n일부 외부 이미지의 CORS 설정을 확인해주세요.'
      );
    }
  }

  document.addEventListener('DOMContentLoaded', init);
})();
