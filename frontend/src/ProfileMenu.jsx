import { useEffect, useRef, useState } from 'react';
import { ChevronLeft, Edit3, LogOut, Plus, Save, Settings, Trash2, X } from 'lucide-react';
import { request } from './apiClient.js';
import { resizeProfileImage } from './utils.js';

export default function ProfileMenu({ auth, onAuth, onLogout, onProfileUpdated, onAccountDeleted, onOpenInstallGuide, standaloneMode }) {
  const [open, setOpen] = useState(false);
  const [view, setView] = useState('main');
  const [nickname, setNickname] = useState(auth.nickname || '');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [confirmDelete, setConfirmDelete] = useState(false);
  const fileInputRef = useRef(null);
  const avatarLetter = (auth.nickname || auth.email || '?').slice(0, 1);

  useEffect(() => {
    setNickname(auth.nickname || '');
  }, [auth.nickname]);

  function close() {
    setOpen(false);
    setView('main');
    setMessage('');
    setError('');
    setConfirmDelete(false);
  }

  function mergeUser(user) {
    return {
      ...auth,
      userId: user.userId || user.id || auth.userId,
      email: user.email || auth.email,
      nickname: user.nickname || auth.nickname,
      profileImageUrl: user.profileImageUrl || null,
    };
  }

  async function applyUser(user, nextMessage) {
    onAuth(mergeUser(user));
    setMessage(nextMessage);
    setError('');
    await onProfileUpdated?.();
  }

  async function saveNickname(event) {
    event.preventDefault();
    setError('');
    try {
      const user = await request('/users/me/nickname', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nickname }),
      }, auth, onAuth);
      await applyUser(user, '닉네임을 저장했어요.');
    } catch (err) {
      setError(err.message);
    }
  }

  async function uploadProfileImage(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    setError('');
    try {
      const profileFile = await resizeProfileImage(file);
      const formData = new FormData();
      formData.append('image', profileFile, profileFile.name || 'profile.webp');
      const user = await request('/users/me/profile-image', {
        method: 'PATCH',
        body: formData,
      }, auth, onAuth);
      await applyUser(user, '프로필 사진을 변경했어요.');
    } catch (err) {
      setError(err.message);
    } finally {
      event.target.value = '';
    }
  }

  async function deleteProfileImage() {
    setError('');
    try {
      const user = await request('/users/me/profile-image', { method: 'DELETE' }, auth, onAuth);
      await applyUser(user, '프로필 사진을 삭제했어요.');
    } catch (err) {
      setError(err.message);
    }
  }

  async function deleteAccount() {
    setError('');
    try {
      await request('/users/me', { method: 'DELETE' }, auth, onAuth);
      try {
        await request('/auth/logout', { method: 'POST' });
      } catch {}
      onAccountDeleted();
    } catch (err) {
      setConfirmDelete(false);
      setError(err.message);
    }
  }

  function title() {
    if (view === 'edit') return '프로필 편집';
    if (view === 'account') return '계정 관리';
    return '내 프로필';
  }

  function renderContent() {
    if (view === 'edit') {
      return (
        <>
          <section className="profile-summary">
            <span className="profile-menu-avatar large">
              {auth.profileImageUrl ? <img src={auth.profileImageUrl} alt="내 프로필" decoding="async" /> : avatarLetter}
            </span>
            <strong>{auth.nickname}</strong>
            <span>{auth.email}</span>
          </section>
          <form className="profile-edit-form" onSubmit={saveNickname}>
            <label className="settings-field">
              <span>이름</span>
              <input value={nickname} onChange={(event) => setNickname(event.target.value)} minLength={2} maxLength={80} required />
            </label>
            <button type="submit"><Save size={15} /> 저장</button>
          </form>
          <section className="menu-section">
            <strong>프로필 사진</strong>
            <div className="profile-photo-actions">
              <input ref={fileInputRef} type="file" accept="image/jpeg,image/jpg,image/png,image/webp,image/*" onChange={uploadProfileImage} hidden />
              <button onClick={() => fileInputRef.current?.click()}><Edit3 size={15} /> 사진 변경</button>
              <button className="danger" onClick={deleteProfileImage} disabled={!auth.profileImageUrl}><Trash2 size={15} /> 사진 삭제</button>
            </div>
          </section>
        </>
      );
    }

    if (view === 'account') {
      return (
        <>
          <section className="menu-section">
            <strong>계정</strong>
            <button className="wide-action" onClick={onLogout}><LogOut size={16} /> 로그아웃</button>
            <button className="danger wide-action" onClick={() => setConfirmDelete(true)}><Trash2 size={16} /> 회원탈퇴</button>
          </section>
        </>
      );
    }

    return (
      <div className="group-menu-main">
        {!standaloneMode && (
          <button onClick={() => {
            close();
            onOpenInstallGuide?.();
          }}>
            <Plus size={18} />
            <span>앱 추가 가이드</span>
          </button>
        )}
        <button onClick={() => {
          setView('edit');
          setMessage('');
          setError('');
        }}>
          <Edit3 size={18} />
          <span>프로필 편집</span>
        </button>
        <button onClick={() => {
          setView('account');
          setMessage('');
          setError('');
        }}>
          <Settings size={18} />
          <span>계정 관리</span>
        </button>
      </div>
    );
  }

  return (
    <div className="profile-menu-anchor">
      <button className="profile-trigger" title="내 프로필" onClick={() => setOpen((value) => !value)}>
        <span className="profile-menu-avatar">
          {auth.profileImageUrl ? <img src={auth.profileImageUrl} alt="내 프로필" decoding="async" /> : avatarLetter}
        </span>
      </button>
      {open && (
        <>
          <div className="menu-dismiss-layer profile-menu-dismiss-layer" onClick={close} aria-hidden="true" />
          <section className={`profile-menu-popover ${view !== 'main' ? 'wide' : ''}`}>
            <div className={`profile-menu-head ${view !== 'main' ? 'sub' : 'main'}`}>
              {view !== 'main' && (
                <>
                  <button title="뒤로" onClick={() => {
                    setView('main');
                    setMessage('');
                    setError('');
                  }}><ChevronLeft size={17} /></button>
                  <strong>{title()}</strong>
                </>
              )}
              <button title="닫기" onClick={close}><X size={16} /></button>
            </div>
            {renderContent()}
            {message && <p className="menu-toast">{message}</p>}
            {error && <p className="error">{error}</p>}
          </section>
        </>
      )}
      {confirmDelete && (
        <div className="modal-backdrop profile-confirm-backdrop">
          <section className="delete-confirm-modal">
            <div className="modal-header">
              <h2>회원탈퇴</h2>
              <button title="닫기" onClick={() => setConfirmDelete(false)}><X size={18} /></button>
            </div>
            <p>정말 탈퇴하시겠어요? 그룹에서는 나가지고, 그림과 프로필 사진은 삭제돼요.</p>
            <div className="confirm-actions">
              <button onClick={() => setConfirmDelete(false)}>취소</button>
              <button className="danger" onClick={deleteAccount}>탈퇴</button>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}
