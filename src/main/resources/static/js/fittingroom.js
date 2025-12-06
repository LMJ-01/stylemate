(function () {
  // ===== 공용 유틸 =====
  const $  = (sel, p = document) => p.querySelector(sel);
  const $$ = (sel, p = document) => Array.from(p.querySelectorAll(sel));
  const byId = (id) => document.getElementById(id);
  const BLANK =
    'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==';
  const proxy = (u) => (!u ? '' : `/img/proxy?url=${encodeURIComponent(u)}`);
  const CROP_API_URL = 'http://127.0.0.1:5001/crop'; // 🔥 배경제거 서버

  // 네이버 검색용 카테고리 한글 라벨
  const CATEGORY_LABEL_KO = {
    top: '상의',
    bottom: '하의',
    outer: '아우터',
    shoes: '신발',
    accessory: '악세서리',
  };

  // 🔥 메인카테고리 → 서브카테고리 옵션
  const SUBCATEGORY_OPTIONS = {
    top: [
      { value: '',            label: '(전체)' },
      { value: 'short_sleeve',label: '반팔' },
      { value: 'long_sleeve', label: '긴팔' },
      { value: 'hoodie',      label: '후드티' },
      { value: 'shirt',       label: '셔츠' },
      { value: 'sweatshirt',  label: '맨투맨' },
    ],
    bottom: [
      { value: '',            label: '(전체)' },
      { value: 'short_pants', label: '반바지' },
      { value: 'long_pants',  label: '긴바지' },
      { value: 'training',    label: '트레이닝' },
      { value: 'jeans',       label: '청바지' },
    ],
    outer: [
      { value: '',             label: '(전체)' },
      { value: 'windbreaker',  label: '바람막이' },
      { value: 'padding',      label: '패딩' },
      { value: 'light_padding',label: '경량패딩' },
      { value: 'coat',         label: '코트' },
      { value: 'jacket',       label: '자켓' },
    ],
    shoes: [
      { value: '',         label: '(전체)' },
      { value: 'running',  label: '러닝화' },
      { value: 'sneakers', label: '스니커즈' },
      { value: 'slipper',  label: '슬리퍼' },
      { value: 'boots',    label: '부츠' },
    ],
    accessory: [
      { value: '',     label: '(전체)' },
      { value: 'cap',  label: '모자' },
      { value: 'bag',  label: '가방' },
      { value: 'socks',label: '양말' },
      { value: 'etc',  label: '기타' },
    ],
  };

  // 🔥 서브카테고리 → 네이버 검색용 키워드 매핑
  const SUBCATEGORY_QUERY_KEYWORD = {
    // top
    short_sleeve:  '반팔 티셔츠',
    long_sleeve:   '긴팔 티셔츠',
    hoodie:        '후드티',
    shirt:         '셔츠',
    sweatshirt:    '맨투맨',
    // bottom
    short_pants:   '반바지',
    long_pants:    '긴바지',
    training:      '트레이닝 바지',
    jeans:         '청바지',
    // outer
    windbreaker:   '바람막이',
    padding:       '패딩',
    light_padding: '경량패딩',
    coat:          '코트',
    jacket:        '자켓',
    // shoes
    running:       '러닝화',
    sneakers:      '스니커즈',
    slipper:       '슬리퍼',
    boots:         '부츠',
    // accessory
    cap:           '모자',
    bag:           '가방',
    socks:         '양말',
    etc:           '패션 악세서리',
  };

  // 전역 네임스페이스
window.FittingRoom = {
  equip,
  equipFromDataset,
  equipFromDatasetWithCrop,
};


  // 레이어/엔드포인트/유저
  let layers = {};
  let API = { random: '', filterAdvanced: '', save: '', searchImages: '' };
  let userId = null;

  // ❤️ 찜(좋아요) 상태 저장용 (key: category|imageUrl)
  const favorites = new Map();

  // CSRF
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
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
      : { r: 230, g: 203, b: 179 }; // 기본 피부색
  }

  function toHex({ r, g, b }) {
    return (
      '#' +
      [r, g, b]
        .map((v) => clamp(v, 0, 255).toString(16).padStart(2, '0'))
        .join('')
    );
  }

  // ----- 공용: 안전한 이미지 URL -----
// 맨 위쪽 util 부분에 있는 safeImg만 남기고 아래 중복 정의는 삭제해도 됨
function safeImg(u) {
  if (!u) return BLANK;
  // data: 로 시작하는 건 그대로 사용
  if (u.startsWith('data:')) return u;

  // 🔥 http(s) 외부 주소는 전부 우리 프록시를 태운다
  if (u.startsWith('http://') || u.startsWith('https://')) {
    return proxy(u);   // -> /img/proxy?url=...
  }

  // 그 외 (상대경로 등)는 그대로
  return u;
}

// ----- 배경제거 서버까지 거쳐서 입히기 -----
async function equipFromDatasetWithCrop(card) {
  const d = card.dataset;
  const category = (d.category || getCurrentCategory() || 'top').toLowerCase();

  let imageUrl = d.croppedImage || d.image || d.thumb || '';
  if (!imageUrl) {
    console.warn('[equipFromDatasetWithCrop] imageUrl 없음');
    return;
  }

  // 아직 크롭 안 했고 외부 이미지인 경우
  if (!d.croppedImage && imageUrl.startsWith('http') && !imageUrl.startsWith(location.origin)) {
    try {
      const res = await fetch(CROP_API_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ imageUrl: imageUrl }),   // 🔥 여기만 수정!
      });

      if (res.ok) {
        const data = await res.json();
        if (data && data.success && data.pngBase64) {
          const base64Url = "data:image/png;base64," + data.pngBase64;
          imageUrl = base64Url;
          d.croppedImage = base64Url;  // 다음부터 재사용
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
        (userId ? `/user/profile/${userId}/fittingroom/random` : '/fittingroom/random'),
      filterAdvanced:
        endpointsEl?.dataset.filterAdvanced ||
        (userId
          ? `/user/profile/${userId}/fittingroom/filter/advanced`
          : '/fittingroom/filter/advanced'),
      save:
        endpointsEl?.dataset.save ||
        (userId ? `/user/profile/${userId}/fittingroom/save` : '/fittingroom/save'),
      searchImages: endpointsEl?.dataset.searchImages || '/api/images/search',
    };

    console.log('[FittingRoom] API 설정:', API);

    // 레이어 바인딩
    layers = {
      face: byId('layer-face'),
      top: byId('layer-top'),
      bottom: byId('layer-bottom'),
      outer: byId('layer-outer'),
      shoes: byId('layer-shoes'),
      accessory: byId('layer-accessory'),
    };

    // 초기 서버 렌더링된 카드들에 favKey 부여
    $$('#itemListWrapper [data-role="item-card"]').forEach((card) => {
      const favKey = makeFavKeyFromDataset(card.dataset);
      card.dataset.favKey = favKey;
    });

    // 현재 카테고리 표시 동기화 + 서브카테고리 옵션 연동
    const filterCategorySel = byId('filter-category');
    const filterSubSel = byId('filter-subcategory');

    function handleCategoryChange() {
      const cat = filterCategorySel.value || 'top';
      updateCurrentCategory(cat);
      switchTab(cat);
      updateSubcategoryOptions(cat); // 🔥 메인 카테고리 바뀔 때 서브 옵션 갱신
    }

    filterCategorySel?.addEventListener('change', handleCategoryChange);

    // 탭 버튼 클릭 시 카테고리 변경
    $$('.item-tab').forEach((btn) => {
      btn.addEventListener('click', () => {
        const cat = btn.dataset.category || 'top';
        if (filterCategorySel) filterCategorySel.value = cat;
        handleCategoryChange();
      });
    });
    
     

    // ===== 리스트 영역 클릭 이벤트 위임 =====
    const wrapper = byId('itemListWrapper');
    if (wrapper) {
      wrapper.addEventListener('click', (e) => {
        // 1) 하트 클릭
        const heart = e.target.closest('.like-btn');
        if (heart) {
          e.stopPropagation();
          toggleLike(heart);
          return;
        }

        // 2) 카드 클릭 -> 배경제거 후 아바타에 입히기
        const card = e.target.closest('[data-role="item-card"]');
        if (card) {
          equipFromDatasetWithCrop(card);
          return;
        }
      });
    }

    // ===== 찜 목록 영역 클릭 이벤트 위임 =====
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

    // 버튼 핸들러
    byId('btn-random-global')?.addEventListener('click', randomGlobal);
    byId('btn-random-by-category')?.addEventListener('click', randomByCategory);
    byId('btn-clear-category')?.addEventListener('click', clearCurrentCategory);
    byId('btn-clear-all')?.addEventListener('click', clearAll);
    byId('btn-apply-filters')?.addEventListener('click', applyFilters);
    byId('btn-clear-filters')?.addEventListener('click', clearFilters);
    byId('btn-save-set')?.addEventListener('click', saveSet);
    byId('btn-download')?.addEventListener('click', downloadImage);

    // 찜 목록 전체 삭제
    byId('btn-clear-wishlist')?.addEventListener('click', () => {
      favorites.clear();
      $$('.like-btn').forEach((h) => {
        h.classList.remove('liked');
        h.textContent = '♡';
      });
      renderFavorites();
    });

    // 초기 카테고리
    const initialCat = filterCategorySel?.value || 'top';
    updateSubcategoryOptions(initialCat); // 🔥 처음 로딩 시도 서브 옵션
    updateCurrentCategory(initialCat);
    switchTab(initialCat);

    // 아바타 개인화 적용 (avatar.json)
    initAvatarBase();

    // 초기 서버 렌더링 아이템 개수 표시
    syncInitialCount(initialCat);

    // 초기 찜 목록 렌더
    renderFavorites();
  }

  // 🔥 메인 카테고리에 따라 서브카테고리 옵션 채우기
  function updateSubcategoryOptions(mainCat) {
    const sel = byId('filter-subcategory');
    if (!sel) return;

    const options = SUBCATEGORY_OPTIONS[mainCat] || [{ value: '', label: '(전체)' }];
    sel.innerHTML = '';
    options.forEach((opt) => {
      const o = document.createElement('option');
      o.value = opt.value;
      o.textContent = opt.label;
      sel.appendChild(o);
    });
  }

  // ===== 찜 key 생성 =====
  function makeFavKeyFromDataset(d) {
    const cat = (d.category || '').toLowerCase();
    const img = d.image || d.imageUrl || d.imageurl || '';
    return `${cat}|${img}`;
  }

  function makeFavKeyFromItem(item, fallbackCategory) {
    const cat = String(item.category || fallbackCategory || '').toLowerCase();
    const img = item.imageUrl || item.thumbUrl || '';
    return `${cat}|${img}`;
  }

  // 하트 동기화 (아이템 목록 + 찜 목록 모두)
  function syncHearts(key, liked) {
    $$('[data-fav-key]').forEach((card) => {
      if (card.dataset.favKey === key) {
        const heart = card.querySelector('.like-btn');
        if (!heart) return;
        heart.classList.toggle('liked', liked);
        heart.textContent = liked ? '❤' : '♡';
      }
    });
  }

  // ===== 하트 토글 =====
  function toggleLike(heartEl) {
    const card = heartEl.closest('[data-role="item-card"]');
    if (!card) return;

    const key = card.dataset.favKey || makeFavKeyFromDataset(card.dataset);
    if (!key) return;

    const nowLiked = !favorites.has(key);
    if (nowLiked) {
      favorites.set(key, {
        category: card.dataset.category,
        imageUrl: card.dataset.image || card.dataset.imageUrl,
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
  
  // ===== 아바타 체형에 맞춰 옷 레이아웃 보정 =====
function applyClothesLayoutForAvatar(stage) {
  if (!stage) return;

  // avatar.json 에서 넣어둔 값들 읽기
  const hScale = parseFloat(stage.style.getPropertyValue('--heightScale') || '1') || 1;   // 0.85 ~ 1.15
  const wScale = parseFloat(stage.style.getPropertyValue('--weightScale') || '1') || 1;   // 0.85 ~ 1.3
  const legH   = parseFloat(stage.style.getPropertyValue('--legHeight')    || '1') || 1;   // 다리 비율
  const body   = (stage.dataset.bodyShape || 'regular').toLowerCase();                    // slim / regular / plus

  // === 1) Y 위치 보정 (위아래) ===
  // 기준값: top 34%, bottom 56%, shoes 82% (HTML의 CSS랑 맞춰져 있음)
  // heightScale, legHeight 에 따라 위/아래로 px 단위 이동
  let topOffsetY    = -(hScale - 1) * 20 * 10;      // 키 클수록 상의 약간 위로
  let bottomOffsetY = (legH   - 1) * 22 * 10;       // 다리 길수록 바지 아래로
  let shoesOffsetY  = (legH   - 1) * 25 * 10;       // 다리 길수록 신발도 아래로
  let outerOffsetY  = topOffsetY * 0.9;             // 아우터는 상의랑 비슷하게

  // === 2) 스케일 보정 (옷 폭/크기) ===
  let topScale    = 1 + (wScale - 1) * 0.6;
  let bottomScale = 1 + (wScale - 1) * 0.5;
  let shoesScale  = 1 + (wScale - 1) * 0.2;
  let outerScale  = topScale * 1.03;    // 아우터는 상의보다 살짝 크게

  // 체형별 추가 보정
  if (body === 'slim') {
    topScale    -= 0.05;
    bottomScale -= 0.05;
  } else if (body === 'plus') {
    topScale    += 0.08;
    bottomScale += 0.08;
  }

  // === 3) CSS 변수로 stage에 주입 (HTML의 style과 연결됨) ===
  stage.style.setProperty('--topOffsetY',    `${topOffsetY.toFixed(1)}px`);
  stage.style.setProperty('--bottomOffsetY', `${bottomOffsetY.toFixed(1)}px`);
  stage.style.setProperty('--shoesOffsetY',  `${shoesOffsetY.toFixed(1)}px`);
  stage.style.setProperty('--outerOffsetY',  `${outerOffsetY.toFixed(1)}px`);

  stage.style.setProperty('--topScale',    topScale.toFixed(2));
  stage.style.setProperty('--bottomScale', bottomScale.toFixed(2));
  stage.style.setProperty('--shoesScale',  shoesScale.toFixed(2));
  stage.style.setProperty('--outerScale',  outerScale.toFixed(2));
}


   // ===== 아바타 개인화 (avatar.json 기준) =====
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
    if (!res.ok) throw new Error('avatar.json 요청 실패');

    const a = await res.json();
    console.log('[avatarBase] avatar.json:', a);

    // ---- ① 값 꺼내기 ----
    const heightCm = toNum(a.heightCm) || 175;
    const weightKg = toNum(a.weightKg) || 70;
    const body = String(a.bodyShape || 'regular').toLowerCase();
    const toneBrightness = clamp(toNum(a.toneBrightness) || 1.0, 0.85, 1.15);
    const skinHex = a.skinToneHex || a.skinTone || '#e6cbb3';

    // 키/몸무게 스케일
    let hScale = a.heightScale != null ? toNum(a.heightScale) : heightCm / 175;
    let wScale = a.weightScale != null ? toNum(a.weightScale) : weightKg / 70;

    hScale = clamp(hScale, 0.85, 1.15);
    wScale = clamp(wScale, 0.85, 1.30);

    // 어깨/머리
    const shoulderScale = clamp(toNum(a.shoulderScale) || 1.0, 0.9, 1.3);
    const headScale = clamp(toNum(a.headScale) || 1.0, 0.85, 1.2);

    // 체형에 따른 두께
    let bodyThickness = 1.0;
    switch (body) {
      case 'slim':  bodyThickness = 0.9;  break;
      case 'plus':  bodyThickness = 1.15; break;
      default:      bodyThickness = 1.0;
    }

    const bodyHeight = hScale;
    const legHeight = clamp(0.9 + (hScale - 1) * 1.2, 0.8, 1.3);

    // ---- ② 피부색 + 밝기 ----
    const baseRgb = hexToRgb(skinHex);
    const brightSkin = toHex({
      r: Math.round(baseRgb.r * toneBrightness),
      g: Math.round(baseRgb.g * toneBrightness),
      b: Math.round(baseRgb.b * toneBrightness),
    });

    // CSS 변수 세팅
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

    // ---- ③ SVG 파츠도 직접 조정 (편집 화면 느낌) ----
const svg = document.getElementById('avatar-svg');
if (svg) {
  // 1) 피부색: .avatar-skin 모두에 스타일로 직접 적용
  svg.querySelectorAll('.avatar-skin').forEach((el) => {
    el.style.fill = brightSkin;   // 🔥 이 줄이 핵심
  });

  // 2) 나머지 체형 관련 파츠 조정
  const head = svg.querySelector('#head');
  const torso = svg.querySelector('#torso');
  const shoulders = svg.querySelector('#shoulders');
  const armL = svg.querySelector('#armL');
  const armR = svg.querySelector('#armR');
  const legL = svg.querySelector('#legL');
  const legR = svg.querySelector('#legR');

  // 기본 값
  let torsoRx = 60, torsoRy = 95, legRx = 28, legRy = 95, armRx = 22, armRy = 60;
  let shoulderW = 150;
  switch (body) {
    case 'slim':
      torsoRx = 54; legRx = 24; armRx = 19; shoulderW = 140;
      break;
    case 'regular':
      torsoRx = 60; legRx = 28; armRx = 22; shoulderW = 150;
      break;
    case 'plus':
      torsoRx = 70; legRx = 33; armRx = 25; shoulderW = 165;
      break;
  }

  // 머리 크기
  if (head) head.setAttribute('r', String(45 * headScale));

  // 몸통
  if (torso) {
    torso.setAttribute('rx', String(torsoRx * wScale));
    torso.setAttribute('ry', String(torsoRy * hScale));
  }

  // 어깨
  if (shoulders) {
    shoulders.setAttribute('x', String(-(shoulderW * shoulderScale) / 2));
    shoulders.setAttribute('width', String(shoulderW * shoulderScale));
  }

  // 팔/다리 두께
  if (armL) armL.setAttribute('rx', String(armRx * wScale));
  if (armR) armR.setAttribute('rx', String(armRx * wScale));
  if (legL) legL.setAttribute('rx', String(legRx * wScale));
  if (legR) legR.setAttribute('rx', String(legRx * wScale));
}


    // 옷 레이아웃 튜닝 훅
    if (stage) {
      applyClothesLayoutForAvatar(stage);
    }
  } catch (e) {
    console.warn('[avatarBase] 적용 실패:', e);
  }
}


  // ===== 랜덤 (DB 기준) =====
  async function randomGlobal() {
    try {
      const r = await fetch(API.random, { method: 'GET', cache: 'no-store' });
      if (!r.ok) throw new Error('랜덤 요청 실패');
      const it = await r.json();
      if (!it) {
        alert('내부 DB에 등록된 옷이 없습니다.');
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

  async function randomByCategory() {
    const cat = byId('filter-category')?.value?.trim();
    if (!cat) return alert('먼저 카테고리를 선택하세요.');
    try {
      const u = new URL(API.filterAdvanced, location.origin);
      u.searchParams.set('category', cat);
      const r = await fetch(u.toString(), { method: 'GET', cache: 'no-store' });
      if (!r.ok) throw new Error('필터 요청 실패');
      const items = await r.json();
      if (!Array.isArray(items) || items.length === 0) {
        return alert(
          '내부 DB에 해당 카테고리 아이템이 없습니다.(네이버 검색은 필터 버튼을 눌러 주세요)'
        );
      }
      const pick = items[Math.floor(Math.random() * items.length)] || {};
      pick.imageUrl = safeImg(pick.imageUrl);
      pick.thumbUrl = safeImg(pick.thumbUrl);
      equip(pick);
    } catch (e) {
      console.error(e);
      alert('카테고리 랜덤 중 오류가 발생했습니다.');
    }
  }

  // 선택한 카테고리만 비우기 (상의/하의/아우터/신발/악세만)
function clearCurrentCategory() {
  const cat = byId('filter-category')?.value?.trim();
  if (!cat) return alert('비울 카테고리를 선택하세요.');

  const valid = ['top', 'bottom', 'outer', 'shoes', 'accessory'];
  if (!valid.includes(cat)) return;

  const layer = layers[cat];
  if (layer) {
    layer.removeAttribute('src');  // src 아예 제거
    layer.alt = '';
  }
}

// 전체 옷만 초기화 (얼굴/아바타는 유지)
function clearAll() {
  ['top', 'bottom', 'outer', 'shoes', 'accessory'].forEach((key) => {
    const l = layers[key];
    if (!l) return;
    l.removeAttribute('src');
    l.alt = '';
  });
}


  // ===== 필터 폼 값 수집 =====
  function collectFilterParams() {
    const obj = {};
    const get = (id) => byId(id)?.value?.trim();
    const cat = get('filter-category');
    const sub = get('filter-subcategory'); // 🔥 서브카테고리
    const color = get('filter-color');
    const brand = get('filter-brand');
    const gender = get('filter-gender');
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

  // 서브카테 value → 한글 라벨 얻기
  function subcategoryLabel(value) {
    if (!value) return '';
    const sel = byId('filter-subcategory');
    const opt = sel?.querySelector(`option[value="${value}"]`);
    return opt?.textContent?.trim() || '';
  }

  // 한글 네이버 검색 쿼리 만들기
  function buildNaverQuery(params) {
    const parts = [];
    const catKo = CATEGORY_LABEL_KO[params.category || 'top'];

    if (params.gender === 'male') parts.push('남성');
    if (params.gender === 'female') parts.push('여성');
    if (params.gender === 'unisex') parts.push('공용');

    if (catKo) parts.push(catKo);

    // 🔥 세부 카테고리 키워드 우선 사용
    if (params.subCategory) {
      const subKey = SUBCATEGORY_QUERY_KEYWORD[params.subCategory];
      if (subKey) {
        parts.push(subKey);
      } else {
        const subLabel = subcategoryLabel(params.subCategory);
        if (subLabel) parts.push(subLabel);
      }
    }

    if (params.color) parts.push(params.color);
    if (params.brand) parts.push(params.brand);

    return parts.join(' ').trim();
  }

  // ===== 내부 DB + 네이버 이미지 검색 후 렌더 =====
  async function applyFilters() {
    const params = collectFilterParams();
    const category = params.category || 'top';

    const wrapper = byId('itemListWrapper');
    const emptyEl = byId('itemListEmpty');
    const targetList = byId(`itemList-${category}`);

    if (targetList) {
      targetList.innerHTML =
        '<div class="col-span-full text-center text-gray-500 py-8">아이템을 불러오는 중입니다...</div>';
      targetList.classList.remove('hidden');
    }
    if (emptyEl) emptyEl.classList.add('hidden');

    try {
      // 1) 내부 DB 필터
      const dbPromise = API.filterAdvanced
        ? (async () => {
            const u = new URL(API.filterAdvanced, location.origin);
            Object.entries(params).forEach(([k, v]) => {
              if (v !== undefined && v !== null && String(v).trim() !== '') {
                u.searchParams.set(k, String(v).trim());
              }
            });
            console.log('[FittingRoom] DB 필터 요청:', u.toString());
            const r = await fetch(u.toString(), { method: 'GET', cache: 'no-store' });
            console.log('[FittingRoom] DB 필터 응답 status:', r.status);
            return r.ok ? r.json() : [];
          })()
        : Promise.resolve([]);

      // 2) 네이버 이미지 검색
      const q = buildNaverQuery(params);
      const navPromise =
        API.searchImages && q
          ? (async () => {
              const uImg = new URL(API.searchImages, location.origin);
              uImg.searchParams.set('query', q);
              uImg.searchParams.set('display', '24');
              console.log('[FittingRoom] 네이버 검색 요청:', q, '=>', uImg.toString());
              const r = await fetch(uImg.toString(), {
                method: 'GET',
                cache: 'no-store',
              });
              console.log('[FittingRoom] 네이버 응답 status:', r.status);
              return r.ok ? r.json() : [];
            })()
          : Promise.resolve([]);

      const [dbRaw, navRaw] = await Promise.all([dbPromise, navPromise]);

      // 1) 내부 DB 결과
      const dbItems = Array.isArray(dbRaw) ? dbRaw : [];

      // 2) 네이버 결과: 배열이든 {items:[..]}든 대응
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
          croppedImage: null, // DB에는 크롭 없음
          source: 'DB',
        }));

      const mappedNaverItems = navArray
        .filter((it) => it)
        .map((it) => {
          const imageUrl = it.imageUrl || it.link || it.thumbnail || it.thumbnailUrl;
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
            thumbUrl: it.thumbUrl || it.thumbnail || it.thumbnailUrl || imageUrl,
            croppedImage,
            source: 'NAVER',
          };
        })
        .filter((it) => it.imageUrl);

      console.log(
        '[FittingRoom] 필터 결과 - DB:',
        mappedDbItems.length,
        '네이버:',
        mappedNaverItems.length
      );

      const allItems = [...mappedDbItems, ...mappedNaverItems];
      renderGrid(allItems, category);

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
    byId('filter-color').value = '';
    byId('filter-brand').value = '';
    byId('filter-gender').value = '';
    byId('filter-price').value = '';
    updateCurrentCategory('top');
    updateSubcategoryOptions('top');
    switchTab('top');
  }

  // ===== 아이템 그리드 렌더 (itemList-카테고리 기준) =====
  function renderGrid(items, category) {
    const wrapper = byId('itemListWrapper');
    if (!wrapper) return;

    const emptyEl = byId('itemListEmpty');
    const lists = $$('.item-list', wrapper);
    lists.forEach((list) => {
      list.classList.add('hidden');
      list.innerHTML = '';
    });
    const valid = (Array.isArray(items) ? items : []).filter((it) => !!it.imageUrl);

    const targetId = `itemList-${category}`;
    let target = byId(targetId) || lists[0];

    if (!target) return;

    if (valid.length === 0) {
      target.classList.remove('hidden');
      if (emptyEl) emptyEl.classList.remove('hidden');
      const countEl = byId('itemCount');
      if (countEl) countEl.textContent = '(0개)';
      return;
    }

    if (emptyEl) emptyEl.classList.add('hidden');

    valid.forEach((c) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.setAttribute('data-role', 'item-card');
      btn.className =
        'item-card flex flex-col items-center gap-1 border rounded-lg p-1 text-[10px] hover:border-gray-900 transition';

      btn.dataset.id = c.id != null ? String(c.id) : '';
      btn.dataset.name = c.name || '';
      btn.dataset.brand = c.brand || '';
      btn.dataset.category = c.category || category;
      btn.dataset.color = c.color || '';
      btn.dataset.price = c.price != null ? String(c.price) : '0';
      btn.dataset.image = c.imageUrl || '';
      btn.dataset.thumb = c.thumbUrl || '';
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
        img.src = BLANK;
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
          ? `₩${Number(c.price || 0).toLocaleString()}`
          : c.source === 'NAVER'
          ? '네이버 이미지'
          : '';
      priceRow.appendChild(priceEl);

      const heartEl = document.createElement('span');
      heartEl.className = 'like-btn text-lg';
      if (isLiked) {
        heartEl.classList.add('liked');
        heartEl.textContent = '❤';
      } else {
        heartEl.textContent = '♡';
      }
      priceRow.appendChild(heartEl);

      textWrap.appendChild(priceRow);

      const sourceEl = document.createElement('p');
      sourceEl.className = 'text-[9px] text-gray-400';
      sourceEl.textContent = c.source === 'NAVER' ? '네이버' : '내부 DB';
      textWrap.appendChild(sourceEl);

      btn.appendChild(imgWrap);
      btn.appendChild(textWrap);
      target.appendChild(btn);
    });

    target.classList.remove('hidden');

    const countEl = byId('itemCount');
    if (countEl) countEl.textContent = `(${valid.length}개)`;
  }

  // ===== 찜 목록 렌더 =====
  function renderFavorites() {
    const wrapper = byId('wishlistWrapper');
    const emptyEl = byId('wishlistEmpty');
    if (!wrapper) return;

    wrapper.querySelectorAll('[data-role="item-card"]').forEach((el) => el.remove());

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

      btn.dataset.favKey = key;
      btn.dataset.category = item.category ||'';
      btn.dataset.image = item.imageUrl || '';
      btn.dataset.name = item.name || '';
      btn.dataset.brand = item.brand || '';
      btn.dataset.color = item.color || '';
      btn.dataset.price = item.price != null ? String(item.price) : '0';

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
        img.src = BLANK;
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
        item.price != null ? `₩${Number(item.price || 0).toLocaleString()}` : '';
      priceRow.appendChild(priceEl);

      const heartEl = document.createElement('span');
      heartEl.className = 'like-btn text-lg liked';
      heartEl.textContent = '❤';
      priceRow.appendChild(heartEl);

      textWrap.appendChild(priceRow);

      btn.appendChild(imgWrap);
      btn.appendChild(textWrap);

      wrapper.appendChild(btn);
    });
  }


// 현재 선택된 카테고리 가져오기
function getCurrentCategory() {
  return byId('filter-category')?.value || 'top';
}

// ----- 실제로 아바타에 옷 입히기 -----
function equip(item) {
  const category = (item.category || getCurrentCategory() || 'top').toLowerCase();
  const layer = layers[category];
  if (!layer) {
    console.warn('[equip] 알 수 없는 카테고리:', category);
    return;
  }

  const url = safeImg(item.croppedImage || item.imageUrl || item.thumbUrl);
  layer.src = url || BLANK;
  layer.alt = item.name || '';
}

// 서버 렌더링 카드(내부 DB용)에서 바로 입히기 (배경제거 없이)
function equipFromDataset(card) {
  const d = card.dataset;
  equip({
    category: d.category,
    name: d.name,
    imageUrl: d.croppedImage || d.image || d.thumb || d.imageUrl,
  });
}


  // ===== 저장 =====
  async function saveSet() {
    const name = byId('set-name')?.value?.trim() || '';
    const payload = {
      name,
      topImage: layers.top?.src || null,
      bottomImage: layers.bottom?.src || null,
      outerImage: layers.outer?.src || null,
      shoesImage: layers.shoes?.src || null,
      accessoryImage: layers.accessory?.src || null,
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
      alert(text || '저장 실패');
    } catch (e) {
      console.error(e);
      alert('저장 중 오류가 발생했습니다.');
    }
  }

  // ===== 다운로드 (외부 이미지 대응/CORS) =====
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
      alert('이미지 저장 중 오류가 발생했습니다. 외부 이미지의 CORS 제한일 수 있어요.');
    }
  }

  document.addEventListener('DOMContentLoaded', init);
})();
