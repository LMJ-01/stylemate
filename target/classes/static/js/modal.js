// /static/js/modal.js (최신 교체본)
// ✅ 댓글 작성 / 수정 / 삭제 / 좋아요 모두 실시간 반영

const CSRF_TOKEN  = document.querySelector('meta[name="_csrf"]')?.content;
const CSRF_HEADER = document.querySelector('meta[name="_csrf_header"]')?.content;

// ==================== 공통 유틸 ====================
function withCsrf(headers = {}, method = 'GET') {
  const h = { ...headers };
  if (CSRF_TOKEN && CSRF_HEADER && method.toUpperCase() !== 'GET') h[CSRF_HEADER] = CSRF_TOKEN;
  return h;
}

function handleHttp(res) {
  if (!res.ok) {
    if (res.status === 401 || res.status === 403) throw new Error('권한이 없습니다. 다시 로그인 해주세요.');
    throw new Error('요청 실패');
  }
  return res;
}

function formatTimeSince(dateString) {
  if (!dateString) return '방금 전';
  const date = new Date(dateString);
  if (isNaN(date)) return '방금 전';
  const now = new Date();
  const diff = Math.floor((now - date) / 60000);
  if (diff < 1) return '방금 전';
  if (diff < 60) return `${diff}분 전`;
  const hours = Math.floor(diff / 60);
  if (hours < 24) return `${hours}시간 전`;
  return `${Math.floor(hours / 24)}일 전`;
}

// ==================== 좋아요 ====================
window.likeFeed = function (feedId) {
  fetch(`/feed/${feedId}/like`, {
    method: 'POST',
    headers: withCsrf({ 'Accept': 'application/json' }, 'POST'),
    credentials: 'same-origin'
  })
    .then(handleHttp)
    .then(r => r.json())
    .then(data => {
      const countEl = document.getElementById(`like-count-${feedId}`);
      if (countEl) countEl.textContent = data.likeCount;

      const btn = document.getElementById(`like-btn-${feedId}`);
      if (btn && typeof data.liked === 'boolean') btn.classList.toggle('text-pink-600', data.liked);
    })
    .catch(err => alert(err.message || '좋아요 처리 실패'));
};

// ==================== 댓글 수 카운트 반영 ====================
function updateCounts(feedId, delta) {
  const cardCount = document.getElementById(`comment-count-${feedId}`);
  if (cardCount) {
    const n = parseInt(cardCount.textContent || '0', 10) + delta;
    cardCount.textContent = Math.max(0, n);
  }

  const modalCount = document.getElementById(`modal-comment-count-${feedId}`);
  if (modalCount) {
    const n = parseInt(modalCount.textContent || '0', 10) + delta;
    modalCount.textContent = Math.max(0, n);
  }
}

// ==================== 댓글 DOM 생성 ====================
function createCommentElement(comment, feedId, currentUserId) {
  const id = comment.commentId || comment.id;
  const div = document.createElement('div');
  div.id = `comment-${id}`;
  div.className = 'text-sm text-gray-800 border-b py-2 relative group extra-comment';

  div.innerHTML = `
    <strong>${comment.nickname}</strong>:
    <span id="content-${id}">${comment.content}</span>
    <div class="text-xs text-gray-500 mt-1">${formatTimeSince(comment.createdAt)}</div>

    <!-- 수정 폼 -->
    <form id="edit-form-${id}" class="flex mt-1 space-x-2 hidden" onsubmit="return false">
      <input type="text" id="edit-content-${id}" value="${comment.content}"
        class="flex-1 border px-2 py-1 text-xs rounded" required>
      <button type="button" class="save-edit-btn bg-blue-500 text-white px-2 text-xs rounded hover:bg-blue-600"
        data-feed-id="${feedId}" data-comment-id="${id}">저장</button>
      <button type="button" class="cancel-edit-btn text-gray-500 text-xs hover:underline"
        data-comment-id="${id}">취소</button>
    </form>

    <!-- 수정/삭제 버튼 -->
    <div class="absolute top-0 right-0 hidden group-hover:flex space-x-1">
      <button type="button" class="edit-btn text-blue-500 text-xs hover:underline"
        data-comment-id="${id}" data-user-id="${comment.userId}">수정</button>
      <button type="button" class="delete-btn text-red-500 text-xs hover:underline"
        data-feed-id="${feedId}" data-comment-id="${id}" data-user-id="${comment.userId}">삭제</button>
    </div>
  `;

  // 본인 댓글만 수정/삭제 버튼 표시
  if (!currentUserId || parseInt(comment.userId) !== parseInt(currentUserId)) {
    div.querySelector('.edit-btn')?.remove();
    div.querySelector('.delete-btn')?.remove();
  }

  return div;
}

// ==================== 모달 이벤트 등록 (수정/삭제) ====================
window.registerModalEvents = function (feedId) {
  const wrapper = document.getElementById('modal-wrapper');
  if (!wrapper) return;
  if (wrapper.__boundFor === String(feedId)) return; // 중복 방지
  wrapper.__boundFor = String(feedId);

  const currentUserId = wrapper.dataset.userId ? parseInt(wrapper.dataset.userId) : null;

  wrapper.addEventListener('click', (e) => {
    const t = e.target;

    // --- 댓글 수정 버튼 ---
    const editBtn = t.closest('.edit-btn');
    if (editBtn) {
      const id = editBtn.dataset.commentId;
      document.getElementById(`content-${id}`).style.display = 'none';
      document.getElementById(`edit-form-${id}`).classList.remove('hidden');
      return;
    }

    // --- 수정 취소 ---
    const cancelBtn = t.closest('.cancel-edit-btn');
    if (cancelBtn) {
      const id = cancelBtn.dataset.commentId;
      document.getElementById(`edit-form-${id}`).classList.add('hidden');
      document.getElementById(`content-${id}`).style.display = '';
      return;
    }

    // --- 수정 저장 ---
    const saveBtn = t.closest('.save-edit-btn');
    if (saveBtn) {
      const cId = saveBtn.dataset.commentId;
      const newContent = document.getElementById(`edit-content-${cId}`).value.trim();
      if (!newContent) return;

      fetch(`/feed/${feedId}/comment/${cId}/edit`, {
        method: 'POST',
        headers: withCsrf({ 'Content-Type': 'application/json', 'Accept': 'application/json' }, 'POST'),
        body: JSON.stringify({ content: newContent }),
        credentials: 'same-origin'
      })
        .then(handleHttp)
        .then(r => r.json())
        .then(data => {
          if (data.success) {
            document.getElementById(`content-${cId}`).textContent = data.content;
            document.getElementById(`edit-form-${cId}`).classList.add('hidden');
            document.getElementById(`content-${cId}`).style.display = '';
          } else {
            alert('댓글 수정 실패');
          }
        })
        .catch(err => alert(err.message || '댓글 수정 중 오류'));
      return;
    }

    // --- 댓글 삭제 ---
    const delBtn = t.closest('.delete-btn');
    if (delBtn) {
      const cId = delBtn.dataset.commentId;
      if (!confirm('댓글을 삭제하시겠습니까?')) return;

      fetch(`/feed/${feedId}/comment/${cId}/delete`, {
        method: 'POST',
        headers: withCsrf({ 'Accept': 'application/json' }, 'POST'),
        credentials: 'same-origin'
      })
        .then(handleHttp)
        .then(r => r.json())
        .then(({ success }) => {
          if (success) {
            document.getElementById(`comment-${cId}`)?.remove();
            updateCounts(feedId, -1);
          }
        })
        .catch(err => alert(err.message || '댓글 삭제 중 오류'));
      return;
    }
  });
};

// ==================== 댓글 작성 ====================
window.submitComment = function (feedId) {
  const input = document.getElementById(`comment-input-${feedId}`);
  const content = (input?.value || '').trim();
  if (!content) return;

  fetch(`/feed/${feedId}/comment`, {
    method: 'POST',
    headers: withCsrf({ 'Content-Type': 'application/json', 'Accept': 'application/json' }, 'POST'),
    body: JSON.stringify({ content }),
    credentials: 'same-origin'
  })
    .then(handleHttp)
    .then(r => r.json())
    .then(data => {
      if (!data.success) {
        alert('댓글 작성 실패');
        return;
      }

      const wrapper = document.getElementById('modal-wrapper');
      const currentUserId = wrapper ? parseInt(wrapper.dataset.userId) : null;
      const list = document.getElementById('comment-list');

      if (list) {
        const div = createCommentElement(data, feedId, currentUserId);
        list.insertBefore(div, list.firstChild);
      }
      if (input) input.value = '';

      updateCounts(feedId, +1);
    })
    .catch(err => alert(err.message || '댓글 작성 중 오류'));
};
