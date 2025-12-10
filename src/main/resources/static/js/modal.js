// modal.js - comment/like handling for feed modal

const CSRF_TOKEN = document.querySelector('meta[name="_csrf"]')?.content;
const CSRF_HEADER = document.querySelector('meta[name="_csrf_header"]')?.content;

function withCsrf(headers = {}, method = 'GET') {
  const h = { ...headers };
  if (CSRF_TOKEN && CSRF_HEADER && method.toUpperCase() !== 'GET') {
    h[CSRF_HEADER] = CSRF_TOKEN;
  }
  return h;
}

function handleHttp(res) {
  if (!res.ok) {
    if (res.status === 401 || res.status === 403) {
      throw new Error('권한이 없습니다. 다시 로그인해 주세요.');
    }
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

// 좋아요
window.likeFeed = function (feedId) {
  fetch(`/feed/${feedId}/like`, {
    method: 'POST',
    headers: withCsrf({ Accept: 'application/json' }, 'POST'),
    credentials: 'same-origin'
  })
    .then(handleHttp)
    .then((r) => r.json())
    .then((data) => {
      // 목록/모달 모두 동기화
      document
        .querySelectorAll(`#like-count-${feedId}, #modal-like-count-${feedId}`)
        .forEach((el) => (el.textContent = data.likeCount));
      document
        .querySelectorAll(`#like-btn-${feedId}, #modal-like-btn-${feedId}`)
        .forEach((btn) => {
          btn.classList.toggle('text-pink-600', !!data.liked);
          // 버튼 내부 텍스트가 ♥ 형태면 색만 토글, 숫자는 span이 담당
        });
    })
    .catch((err) => alert(err.message || '좋아요 처리 실패'));
};

// 댓글 카운트 동기화
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

// 댓글 DOM 생성
function createCommentElement(comment, feedId, currentUserId) {
  const id = comment.commentId || comment.id;
  const div = document.createElement('div');
  div.id = `comment-${id}`;
  div.className = 'text-sm text-gray-800 border-b py-2 relative group extra-comment';
  div.dataset.userId = comment.userId || '';

  div.innerHTML = `
    <strong>${comment.nickname || ''}</strong>:
    <span id="content-${id}">${comment.content || ''}</span>
    <div class="text-xs text-gray-500 mt-1">${formatTimeSince(comment.createdAt)}</div>
    <div class="absolute top-0 right-0 hidden group-hover:flex space-x-1">
      <button type="button" class="edit-btn text-blue-500 text-xs hover:underline" data-comment-id="${id}" data-user-id="${comment.userId}">수정</button>
      <button type="button" class="delete-btn text-red-500 text-xs hover:underline" data-feed-id="${feedId}" data-comment-id="${id}" data-user-id="${comment.userId}">삭제</button>
    </div>
  `;

  if (!currentUserId || parseInt(comment.userId) !== parseInt(currentUserId)) {
    div.querySelector('.edit-btn')?.remove();
    div.querySelector('.delete-btn')?.remove();
  }

  return div;
}

// 모달 이벤트 (수정/삭제)
window.registerModalEvents = function (feedId) {
  const wrapper = document.getElementById('modal-wrapper');
  if (!wrapper) return;

  const currentUserId = wrapper.dataset.userId ? parseInt(wrapper.dataset.userId) : null;

  wrapper.onclick = (e) => {
    const t = e.target;

    // 수정 (인라인 폼 생성)
    const editBtn = t.closest('.edit-btn');
    if (editBtn) {
      const cId = editBtn.dataset.commentId;
      const contentEl = document.getElementById(`content-${cId}`);
      const container = document.getElementById(`comment-${cId}`);
      if (!contentEl || !container) return;
      if (container.querySelector('.inline-edit-form')) return; // 이미 편집 중

      const current = (contentEl.textContent || '').trim();
      contentEl.style.display = 'none';

      const form = document.createElement('form');
      form.className = 'inline-edit-form flex mt-1 space-x-2';
      form.onsubmit = (ev) => ev.preventDefault();

      const input = document.createElement('input');
      input.type = 'text';
      input.value = current;
      input.className = 'flex-1 border px-2 py-1 text-xs rounded';
      input.required = true;

      const saveBtn = document.createElement('button');
      saveBtn.type = 'button';
      saveBtn.textContent = '저장';
      saveBtn.className = 'bg-blue-500 text-white px-2 text-xs rounded hover:bg-blue-600';

      const cancelBtn = document.createElement('button');
      cancelBtn.type = 'button';
      cancelBtn.textContent = '취소';
      cancelBtn.className = 'text-gray-500 text-xs hover:underline';

      form.appendChild(input);
      form.appendChild(saveBtn);
      form.appendChild(cancelBtn);
      contentEl.insertAdjacentElement('afterend', form);

      // 입력창에 바로 포커스 주고 커서 맨 뒤로
      requestAnimationFrame(() => {
        input.focus();
        const len = input.value.length;
        try {
          input.setSelectionRange(len, len);
        } catch (_) {}
      });

      cancelBtn.onclick = () => {
        form.remove();
        contentEl.style.display = '';
      };

      saveBtn.onclick = () => {
        const newContent = (input.value || '').trim();
        if (!newContent || newContent === current) {
          form.remove();
          contentEl.style.display = '';
          return;
        }
        fetch(`/feed/${feedId}/comment/${cId}/edit`, {
          method: 'POST',
          headers: withCsrf({ 'Content-Type': 'application/json', Accept: 'application/json' }, 'POST'),
          body: JSON.stringify({ content: newContent }),
          credentials: 'same-origin'
        })
          .then(handleHttp)
          .then((r) => r.json())
          .then((data) => {
            if (data.success) {
              contentEl.textContent = data.content || newContent;
            } else {
              alert('댓글 수정 실패');
            }
          })
          .catch((err) => alert(err.message || '댓글 수정 오류'))
          .finally(() => {
            form.remove();
            contentEl.style.display = '';
          });
      };
      return;
    }

    // 삭제
    const delBtn = t.closest('.delete-btn');
    if (delBtn) {
      const cId = delBtn.dataset.commentId;
      if (!confirm('댓글을 삭제할까요?')) return;

      fetch(`/feed/${feedId}/comment/${cId}/delete`, {
        method: 'POST',
        headers: withCsrf({ Accept: 'application/json' }, 'POST'),
        credentials: 'same-origin'
      })
        .then(handleHttp)
        .then((r) => r.json())
        .then(({ success }) => {
          if (success) {
            document.getElementById(`comment-${cId}`)?.remove();
            updateCounts(feedId, -1);
          }
        })
        .catch((err) => alert(err.message || '댓글 삭제 오류'));
      return;
    }
  };
};

// 댓글 작성
window.submitComment = function (feedId) {
  const input = document.getElementById(`comment-input-${feedId}`);
  const content = (input?.value || '').trim();
  if (!content) return;

  fetch(`/feed/${feedId}/comment`, {
    method: 'POST',
    headers: withCsrf({ 'Content-Type': 'application/json', Accept: 'application/json' }, 'POST'),
    body: JSON.stringify({ content }),
    credentials: 'same-origin'
  })
    .then(handleHttp)
    .then((r) => r.json())
    .then((data) => {
      if (!data.success) {
        alert('댓글 작성 실패');
        return;
      }

      const wrapper = document.getElementById('modal-wrapper');
      const currentUserId = wrapper ? parseInt(wrapper.dataset.userId) : null;
      // 모달 내 댓글 리스트 찾기 (동적으로 삽입된 id 대응)
      const list =
        document.getElementById(`comment-list-${feedId}`) ||
        document.getElementById('comment-list') ||
        document.querySelector('#comment-container [id^="comment-list-"]');

      if (list) {
        const div = createCommentElement(data, feedId, currentUserId);
        list.insertBefore(div, list.firstChild);
      }
      if (input) input.value = '';

      updateCounts(feedId, +1);
    })
    .catch((err) => alert(err.message || '댓글 작성 오류'));
};
