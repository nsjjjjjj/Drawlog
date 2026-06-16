import { useState } from 'react';
import { Plus, Users, X } from 'lucide-react';
import { request } from './apiClient.js';
import { inviteFromUrl } from './utils.js';

export default function GroupLobby({ auth, groups, onAuth, onRefresh, onSelect, composerOpen, onCloseComposer }) {
  const [name, setName] = useState('');
  const [initialTopic, setInitialTopic] = useState('');
  const [maxMembers, setMaxMembers] = useState(6);
  const [inviteCode, setInviteCode] = useState(inviteFromUrl());
  const [message, setMessage] = useState('');

  async function createGroup(event) {
    event.preventDefault();
    setMessage('');
    try {
      const group = await request('/groups', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, maxMembers, initialTopic: initialTopic || null }),
      }, auth, onAuth);
      setName('');
      setInitialTopic('');
      onCloseComposer();
      await onRefresh();
      onSelect(group.id);
    } catch (err) {
      setMessage(err.message);
    }
  }

  async function joinGroup(event) {
    event.preventDefault();
    setMessage('');
    try {
      const group = await request('/groups/join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ inviteCode }),
      }, auth, onAuth);
      setInviteCode('');
      onCloseComposer();
      window.history.replaceState({}, '', '/');
      await onRefresh();
      onSelect(group.id);
    } catch (err) {
      setMessage(err.message);
    }
  }

  return (
    <section className="group-lobby">
      <div className="lobby-heading">
        <h1>친구들과 하루 한 장</h1>
      </div>
      <div className="lobby-feature-card">
        <div>
          <strong>drawlog</strong>
          <span>주제, 그림, 채팅을 한 방에서 이어가요</span>
        </div>
      </div>
      <div className="group-grid">
        {groups.map((group) => (
          <button className="group-tile" key={group.id} onClick={() => onSelect(group.id)}>
            <div>
              <strong>{group.name}</strong>
              <span>{group.members.length}/{group.maxMembers}명</span>
            </div>
            <div className="member-dots" aria-hidden="true">
              {group.members.slice(0, 5).map((member) => (
                <i key={member.userId}>
                  {member.profileImageUrl ? (
                    <img src={member.profileImageUrl} alt="" loading="lazy" decoding="async" />
                  ) : (
                    member.nickname.slice(0, 1)
                  )}
                </i>
              ))}
            </div>
          </button>
        ))}
      </div>
      {composerOpen && (
        <div className="modal-backdrop lobby-modal-backdrop">
          <section className="lobby-modal">
            <div className="modal-header">
              <h2>그룹 시작하기</h2>
              <button title="닫기" onClick={onCloseComposer}><X size={18} /></button>
            </div>
            <div className="lobby-forms">
              <form onSubmit={createGroup} className="panel compact">
                <div className="panel-title"><Plus size={18} /><h2>그룹 생성</h2></div>
                <input placeholder="새 그룹 이름" value={name} onChange={(event) => setName(event.target.value)} required />
                <div className="member-count-picker">
                  {[2, 4, 6, 8, 10, 12].map((value) => (
                    <button key={value} type="button" className={maxMembers === value ? 'active' : ''} onClick={() => setMaxMembers(value)}>{value}</button>
                  ))}
                </div>
                <input placeholder="첫 주제 직접 설정 (비우면 자동)" value={initialTopic} onChange={(event) => setInitialTopic(event.target.value)} maxLength={120} />
                <button className="primary" type="submit">만들기</button>
              </form>
              <form onSubmit={joinGroup} className="panel compact">
                <div className="panel-title"><Users size={18} /><h2>초대코드 입장</h2></div>
                <input placeholder="초대코드" value={inviteCode} onChange={(event) => setInviteCode(event.target.value.toUpperCase())} required />
                <button className="primary" type="submit">입장하기</button>
              </form>
            </div>
          </section>
        </div>
      )}
      {message && <p className="error">{message}</p>}
    </section>
  );
}
