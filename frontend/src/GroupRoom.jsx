import { useEffect, useMemo, useState } from 'react';
import { CalendarDays, ChevronLeft, ChevronRight, Edit3, KeyRound, Lock, LogOut, MessageCircle, Plus, Quote, Settings, Trash2, Users, Vote, X } from 'lucide-react';
import { request } from './apiClient.js';
import ChatPanel from './ChatPanel.jsx';
import DrawingModal from './DrawingModal.jsx';
import { canManageMember, visibleMembers } from './groupMembers.js';
import { drawingImageUrl, formatDateLabel, monthDates, monthKey, shiftMonth, todayString, tomorrowString } from './utils.js';

function DateNavigator({ date, onChange, selectableDates = [] }) {
  const [pickerOpen, setPickerOpen] = useState(false);
  const [visibleMonth, setVisibleMonth] = useState(monthKey(date));
  const today = todayString();
  const availableDates = useMemo(() => (
    Array.from(new Set(selectableDates))
      .filter((recordDate) => recordDate <= today)
      .sort()
  ), [selectableDates, today]);
  const recordDateSet = useMemo(() => new Set(availableDates), [availableDates]);
  const previousDate = useMemo(() => {
    const before = availableDates.filter((recordDate) => recordDate < date);
    return before.length ? before[before.length - 1] : null;
  }, [availableDates, date]);
  const nextDate = useMemo(() => availableDates.find((recordDate) => recordDate > date) || null, [availableDates, date]);

  useEffect(() => {
    setVisibleMonth(monthKey(date));
  }, [date]);

  function changeDate(nextRecordDate) {
    if (!nextRecordDate || !recordDateSet.has(nextRecordDate)) return;
    onChange(nextRecordDate);
  }

  function selectFromPicker(nextDate) {
    if (!nextDate || nextDate > today || !recordDateSet.has(nextDate)) return;
    onChange(nextDate);
    setPickerOpen(false);
  }

  return (
    <div className="date-nav">
      <button title="이전 기록 날짜" disabled={!previousDate} onClick={() => changeDate(previousDate)}><ChevronLeft size={18} /></button>
      <button className="date-display" onClick={() => setPickerOpen((open) => !open)}>
        <CalendarDays size={17} />
        <span>{formatDateLabel(date)}</span>
      </button>
      <button title="다음 기록 날짜" disabled={!nextDate} onClick={() => changeDate(nextDate)}><ChevronRight size={18} /></button>
      {pickerOpen && (
        <section className="date-picker-card">
          <div className="date-picker-head">
            <button title="이전 달" onClick={() => setVisibleMonth((value) => shiftMonth(value, -1))}><ChevronLeft size={16} /></button>
            <strong>{visibleMonth}</strong>
            <button title="다음 달" disabled={visibleMonth >= monthKey(today)} onClick={() => setVisibleMonth((value) => shiftMonth(value, 1))}><ChevronRight size={16} /></button>
          </div>
          <div className="date-picker-weekdays">
            {['일', '월', '화', '수', '목', '금', '토'].map((day) => <span key={day}>{day}</span>)}
          </div>
          <div className="date-picker-grid">
            {monthDates(visibleMonth).map((day, index) => {
              const selectable = day && day <= today && recordDateSet.has(day);
              return day ? (
                <button
                  key={day}
                  className={day === date ? 'selected' : ''}
                  disabled={!selectable}
                  onClick={() => selectFromPicker(day)}
                >
                  {Number(day.slice(-2))}
                </button>
              ) : (
                <span key={`blank-${index}`} />
              );
            })}
          </div>
          {availableDates.length === 0 && <p>아직 선택 가능한 기록 날짜가 없어요.</p>}
        </section>
      )}
    </div>
  );
}

function SuggestionPanel({ auth, onAuth, groupId, refreshKey }) {
  const [suggestions, setSuggestions] = useState([]);
  const [myVote, setMyVote] = useState(null);
  const [text, setText] = useState('');
  const [message, setMessage] = useState('');
  const targetDate = tomorrowString();
  const mine = suggestions.find((suggestion) => suggestion.mine);

  async function load() {
    const [items, voteInfo] = await Promise.all([
      request(`/groups/${groupId}/topics/suggestions?targetDate=${targetDate}`, {}, auth, onAuth),
      request(`/groups/${groupId}/topics/my-vote?targetDate=${targetDate}`, {}, auth, onAuth),
    ]);
    setSuggestions(items || []);
    setMyVote(voteInfo?.suggestionId || null);
    setText((items || []).find((suggestion) => suggestion.mine)?.text || '');
  }

  useEffect(() => {
    if (!groupId) return;
    load().catch((err) => setMessage(err.message));
  }, [groupId, refreshKey]);

  async function save(event) {
    event.preventDefault();
    setMessage('');
    try {
      const path = mine
        ? `/groups/${groupId}/topics/suggestions/${mine.id}`
        : `/groups/${groupId}/topics/suggestions`;
      await request(path, {
        method: mine ? 'PATCH' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ targetDate, text }),
      }, auth, onAuth);
      setMessage(mine ? '주제를 수정했어요.' : '주제를 제안했어요.');
      await load();
    } catch (err) {
      setMessage(err.message);
    }
  }

  async function remove() {
    if (!mine) return;
    setMessage('');
    try {
      await request(`/groups/${groupId}/topics/suggestions/${mine.id}`, { method: 'DELETE' }, auth, onAuth);
      setText('');
      setMessage('주제 제안을 삭제했어요.');
      await load();
    } catch (err) {
      setMessage(err.message);
    }
  }

  async function vote(suggestionId) {
    await request(`/groups/${groupId}/topics/suggestions/${suggestionId}/vote`, { method: 'POST' }, auth, onAuth);
    await load();
  }

  return (
    <section className="suggestion-panel">
      <form onSubmit={save} className="suggestion-row">
        <input placeholder="내일 주제 제안" value={text} onChange={(event) => setText(event.target.value)} required maxLength={120} />
        <button title={mine ? '수정' : '제안'} type="submit"><Edit3 size={17} /></button>
        {mine && <button title="삭제" type="button" disabled={!mine.editable} onClick={remove}><Trash2 size={17} /></button>}
      </form>
      <div className="candidate-list">
        {suggestions.map((suggestion) => (
          <button key={suggestion.id} className={myVote === suggestion.id ? 'voted' : ''} onClick={() => vote(suggestion.id)}>
            <Vote size={16} />
            <span>{suggestion.text}</span>
            <strong>{suggestion.voteCount}</strong>
          </button>
        ))}
      </div>
      {message && <p className={message.includes('했습니다') || message.includes('없') ? 'error' : 'notice'}>{message}</p>}
    </section>
  );
}

function DrawingViewer({ drawing, onClose, onQuote }) {
  if (!drawing) return null;
  return (
    <div className="modal-backdrop">
      <section className="viewer-modal">
        <div className="modal-header">
          <div>
            <h2>{drawing.nickname}</h2>
            <p className="muted">{new Date(drawing.submittedAt).toLocaleString('ko-KR')}</p>
          </div>
          <button title="닫기" onClick={onClose}><X size={18} /></button>
        </div>
        <img src={drawingImageUrl(drawing)} alt={`${drawing.nickname}의 그림`} decoding="async" />
        <button className="quote-wide" onClick={() => {
          onQuote(drawing);
          onClose();
        }}><Quote size={17} /> 채팅에 인용</button>
      </section>
    </div>
  );
}

export default function GroupRoom({ auth, onAuth, group, onBack, onRefreshGroups, onLeftGroup }) {
  const [feed, setFeed] = useState(null);
  const [date, setDate] = useState(todayString());
  const [recordDates, setRecordDates] = useState([]);
  const [recordDatesLoaded, setRecordDatesLoaded] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [editorOpen, setEditorOpen] = useState(false);
  const [viewerDrawing, setViewerDrawing] = useState(null);
  const [chatQuote, setChatQuote] = useState(null);
  const [roomView, setRoomView] = useState('feed');
  const [groupMenuOpen, setGroupMenuOpen] = useState(false);
  const [groupMenuView, setGroupMenuView] = useState('main');
  const [menuNotice, setMenuNotice] = useState('');
  const [groupName, setGroupName] = useState(group.name);
  const [groupMaxMembers, setGroupMaxMembers] = useState(group.maxMembers || 6);
  const [ownerMessage, setOwnerMessage] = useState('');
  const inviteLink = `${window.location.origin}/invite/${group.inviteCode}`;
  const isToday = date === todayString();
  const selectableDates = useMemo(() => (
    Array.from(new Set(recordDates))
      .filter((recordDate) => recordDate <= todayString())
      .sort()
  ), [recordDates]);

  useEffect(() => {
    setGroupName(group.name);
    setGroupMaxMembers(group.maxMembers || 6);
  }, [group.name, group.maxMembers]);

  useEffect(() => {
    setDate(todayString());
    setFeed(null);
    setRecordDates([]);
    setRecordDatesLoaded(false);
  }, [group.id]);

  useEffect(() => {
    if (!groupMenuOpen) {
      setGroupMenuView('main');
      setMenuNotice('');
      setOwnerMessage('');
    }
  }, [groupMenuOpen]);

  useEffect(() => {
    if (!menuNotice) return undefined;
    const timer = window.setTimeout(() => setMenuNotice(''), 2400);
    return () => window.clearTimeout(timer);
  }, [menuNotice]);

  async function loadFeed() {
    if (!recordDatesLoaded || selectableDates.length === 0 || !selectableDates.includes(date)) {
      setFeed(null);
      return;
    }
    const data = await request(`/groups/${group.id}/feed?date=${date}`, {}, auth, onAuth);
    setFeed(data);
  }

  async function loadRecordDates() {
    setRecordDatesLoaded(false);
    try {
      const data = await request(`/groups/${group.id}/feed/dates`, {}, auth, onAuth);
      setRecordDates(data?.dates || []);
    } finally {
      setRecordDatesLoaded(true);
    }
  }

  useEffect(() => {
    loadFeed().catch(() => setFeed(null));
  }, [auth.token, group.id, date, refreshKey, recordDatesLoaded, selectableDates]);

  useEffect(() => {
    loadRecordDates().catch(() => {
      setRecordDates([]);
      setRecordDatesLoaded(true);
    });
  }, [auth.token, group.id, refreshKey]);

  useEffect(() => {
    if (!recordDatesLoaded || selectableDates.length === 0 || selectableDates.includes(date)) return;
    setDate(selectableDates[selectableDates.length - 1]);
  }, [date, recordDatesLoaded, selectableDates]);

  const myMember = feed?.members?.find((member) => String(member.userId) === String(auth.userId));
  const myDrawing = myMember?.drawing || null;
  const mySubmitted = Boolean(feed?.submitted || myDrawing);
  const topic = feed?.dailyTopic;
  const isFeedBlind = Boolean(isToday && feed?.feedLocked && !mySubmitted);
  const feedMembers = visibleMembers(feed?.members?.length ? feed.members : group.members || []);
  const memberSlots = useMemo(() => {
    const sortedMembers = visibleMembers(feedMembers);
    const slotCount = Math.min(12, Math.max(2, group.maxMembers || groupMaxMembers || 2, sortedMembers.length));
    return Array.from({ length: slotCount }, (_, index) => ({
      id: sortedMembers[index]?.userId ? `member-${sortedMembers[index].userId}` : `invite-${index}`,
      member: sortedMembers[index] || null,
    }));
  }, [feedMembers, group.maxMembers, groupMaxMembers]);

  async function leaveGroup() {
    const ok = window.confirm(`${group.name} 그룹에서 나갈까요? 방장은 먼저 다른 멤버에게 위임해야 합니다.`);
    if (!ok) return;
    try {
      await request(`/groups/${group.id}/leave`, { method: 'POST' }, auth, onAuth);
      onLeftGroup();
    } catch (err) {
      alert(err.message || '그룹에서 나가지 못했습니다.');
    }
  }

  function openMember(member) {
    if (member.userId === auth.userId && isToday) {
      setEditorOpen(true);
    } else if (member.drawing) {
      setViewerDrawing(member.drawing);
    }
  }

  async function refreshAll() {
    setRefreshKey((value) => value + 1);
    await onRefreshGroups();
  }

  function openGroupMenu(view = 'main') {
    setGroupMenuView(view);
    setMenuNotice('');
    setGroupMenuOpen(true);
  }

  function closeGroupMenu() {
    setGroupMenuOpen(false);
  }

  function openMenuView(view) {
    setGroupMenuView(view);
    setMenuNotice('');
    setOwnerMessage('');
  }

  async function copyText(text, message) {
    try {
      let copied = false;
      if (navigator.clipboard?.writeText) {
        try {
          await navigator.clipboard.writeText(text);
          copied = true;
        } catch {}
      }
      if (!copied) {
        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        textarea.setAttribute('readonly', '');
        document.body.appendChild(textarea);
        textarea.focus();
        textarea.select();
        textarea.setSelectionRange(0, text.length);
        copied = document.execCommand('copy');
        document.body.removeChild(textarea);
      }
      if (!copied) throw new Error('copy failed');
      setMenuNotice(message);
    } catch {
      setMenuNotice('복사하지 못했어요. 길게 눌러 직접 복사해 주세요.');
    }
  }

  async function shareInvite() {
    const payload = {
      title: 'Drawlog 그룹 초대',
      text: '함께 하루로그를 기록해요.',
      url: inviteLink,
    };
    if (navigator.share) {
      try {
        await navigator.share(payload);
        setMenuNotice('초대 링크를 공유했어요.');
        return;
      } catch (err) {
        if (err?.name === 'AbortError') return;
      }
    }
    await copyText(inviteLink, '초대 링크가 복사되었습니다.');
  }

  async function saveGroupSettings(event) {
    event.preventDefault();
    setOwnerMessage('');
    try {
      await request(`/groups/${group.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: groupName, maxMembers: groupMaxMembers }),
      }, auth, onAuth);
      setOwnerMessage('그룹 설정을 저장했어요.');
      await onRefreshGroups();
    } catch (err) {
      setOwnerMessage(err.message);
    }
  }

  async function transferOwner(member) {
    const ok = window.confirm(`${member.nickname}님에게 방장을 위임할까요?`);
    if (!ok) return;
    setOwnerMessage('');
    try {
      await request(`/groups/${group.id}/transfer-owner`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ targetUserId: member.userId }),
      }, auth, onAuth);
      setOwnerMessage('방장을 위임했어요.');
      await refreshAll();
    } catch (err) {
      setOwnerMessage(err.message);
    }
  }

  async function removeMember(member) {
    const ok = window.confirm(`${member.nickname}님을 그룹에서 내보낼까요?`);
    if (!ok) return;
    setOwnerMessage('');
    try {
      await request(`/groups/${group.id}/members/${member.userId}`, { method: 'DELETE' }, auth, onAuth);
      setOwnerMessage('멤버를 내보냈어요.');
      await refreshAll();
    } catch (err) {
      setOwnerMessage(err.message);
    }
  }

  function menuTitle() {
    if (groupMenuView === 'invite') return '초대코드';
    if (groupMenuView === 'members') return '멤버';
    if (groupMenuView === 'settings') return '설정';
    return '그룹 메뉴';
  }

  function renderGroupMenuContent() {
    const sourceMembers = group.members?.length ? group.members : feed?.members || [];
    const members = visibleMembers(sourceMembers);
    const currentMembership = members.find((member) => String(member.userId) === String(auth.userId));
    const isGroupOwner = Boolean(group.owner || currentMembership?.role === 'OWNER');
    const currentMemberCount = members.length;

    if (groupMenuView === 'invite') {
      return (
        <>
          <button className="invite-share-card" onClick={shareInvite}>
            <Plus size={18} />
            <div>
              <strong>친구 초대</strong>
              <span>초대 링크 공유하기</span>
            </div>
          </button>
          <button className="invite-code-chip" onClick={() => copyText(group.inviteCode, '초대코드를 복사했어요')}>
            <KeyRound size={16} />
            <strong>{group.inviteCode}</strong>
          </button>
          {menuNotice && <p className="menu-toast">{menuNotice}</p>}
        </>
      );
    }

    if (groupMenuView === 'members') {
      return (
        <>
          <div className="menu-member-list clean">
            {members.map((member) => {
              const owner = member.role === 'OWNER';
              return (
                <article className="member-card" key={member.userId}>
                  <div className="member-card-main">
                    <span className="member-avatar">
                      {member.profileImageUrl ? (
                        <img src={member.profileImageUrl} alt={`${member.nickname} 프로필`} loading="lazy" decoding="async" />
                      ) : (
                        member.nickname.slice(0, 1)
                      )}
                    </span>
                    <div>
                      <strong>{member.nickname}</strong>
                      {owner && <span>방장</span>}
                    </div>
                  </div>
                  {canManageMember(isGroupOwner, auth.userId, member) && (
                    <div className="member-actions">
                      <button onClick={() => transferOwner(member)}>위임</button>
                      <button className="danger" onClick={() => removeMember(member)}>내보내기</button>
                    </div>
                  )}
                </article>
              );
            })}
          </div>
          {ownerMessage && <p className={ownerMessage.includes('못') || ownerMessage.includes('보류') ? 'error' : 'notice'}>{ownerMessage}</p>}
        </>
      );
    }

    if (groupMenuView === 'settings') {
      return (
        <>
          {isGroupOwner && (
            <section className="menu-section">
              <strong>설정</strong>
              <form onSubmit={saveGroupSettings} className="owner-name-row compact">
                <label className="settings-field">
                  <span>그룹 이름</span>
                  <input value={groupName} onChange={(event) => setGroupName(event.target.value)} minLength={2} maxLength={80} required />
                </label>
                <div className="member-count-setting">
                  <span>최대 인원</span>
                  <div className="member-count-picker compact">
                    {[2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map((value) => (
                      <button
                        key={value}
                        type="button"
                        className={groupMaxMembers === value ? 'active' : ''}
                        disabled={value < currentMemberCount}
                        onClick={() => setGroupMaxMembers(value)}
                      >
                        {value}
                      </button>
                    ))}
                  </div>
                </div>
                <button type="submit">저장</button>
              </form>
            </section>
          )}
          <section className="menu-section">
            <strong>내 계정</strong>
            <button className="danger wide-action" onClick={leaveGroup}><LogOut size={16} /> 그룹 나가기</button>
          </section>
          {ownerMessage && <p className={ownerMessage.includes('못') || ownerMessage.includes('보류') ? 'error' : 'notice'}>{ownerMessage}</p>}
        </>
      );
    }

    return (
      <div className="group-menu-main">
        <button onClick={() => openMenuView('invite')}>
          <KeyRound size={18} />
          <span>초대코드 / 친구 초대</span>
        </button>
        <button onClick={() => openMenuView('members')}>
          <Users size={18} />
          <span>멤버</span>
        </button>
        <button onClick={() => openMenuView('settings')}>
          <Settings size={18} />
          <span>설정</span>
        </button>
      </div>
    );
  }

  return (
    <main className={`room-shell setlog-room ${roomView !== 'feed' ? 'chat-mode' : ''}`}>
      <header className="room-header setlog-room-header">
        <button
          className="circle-button"
          title={roomView !== 'feed' ? '그룹 메인' : '그룹 선택'}
          onClick={() => (roomView !== 'feed' ? setRoomView('feed') : onBack())}
        >
          <ChevronLeft size={30} />
        </button>
        <div className="group-menu-anchor">
          <button className="group-pill" onClick={() => (groupMenuOpen ? closeGroupMenu() : openGroupMenu())}>
            <span>{group.name}</span>
            <ChevronRight size={22} />
          </button>
          {groupMenuOpen && (
            <>
              <button className="menu-dismiss-layer" aria-label="메뉴 닫기" onClick={closeGroupMenu} />
              <section className={`group-menu-popover ${groupMenuView !== 'main' ? 'wide' : ''}`}>
                <div className={`group-menu-card-head ${groupMenuView !== 'main' ? 'sub' : ''}`}>
                  {groupMenuView === 'main' ? (
                    <span className="menu-head-spacer" aria-hidden="true" />
                  ) : (
                    <button title="뒤로" onClick={() => openMenuView('main')}><ChevronLeft size={17} /></button>
                  )}
                  <strong>{menuTitle()}</strong>
                  <button title="닫기" onClick={closeGroupMenu}><X size={16} /></button>
                </div>
                {renderGroupMenuContent()}
              </section>
            </>
          )}
        </div>
        <div className="room-header-actions">
          {roomView === 'feed' && (
            <button className="chat-entry-button" title="채팅" onClick={() => setRoomView('chat')}>
              <MessageCircle size={20} />
              <span>채팅</span>
            </button>
          )}
        </div>
      </header>
      {roomView === 'chat' ? (
        <section className="room-chat-screen">
          <ChatPanel
            auth={auth}
            onAuth={onAuth}
            groupId={group.id}
            quoteTarget={chatQuote}
            onClearQuote={() => setChatQuote(null)}
          />
        </section>
      ) : roomView === 'topics' ? (
        <section className="room-topic-screen">
          <section className="topic-vote-screen-card">
            <div className="topic-screen-title">
              <p className="eyebrow">Tomorrow</p>
              <h1>내일 주제 추천/투표</h1>
            </div>
            <SuggestionPanel auth={auth} onAuth={onAuth} groupId={group.id} refreshKey={refreshKey} />
          </section>
        </section>
      ) : (
        <>
          <section className="room-tools">
            <DateNavigator date={date} onChange={setDate} selectableDates={selectableDates} />
          </section>
          <section className="topic-hub-card">
            <div className="topic-hub-main">
              <p>{isToday ? '오늘의 주제' : '그날의 주제'}</p>
              <h1>{topic?.text || (isToday ? '주제를 불러오는 중' : '기록 없음')}</h1>
            </div>
            <button type="button" className="topic-hub-action" onClick={() => setRoomView('topics')}>
              <span>
                <small>Tomorrow</small>
                <strong>내일 주제 추천/투표</strong>
              </span>
              <ChevronRight size={18} />
            </button>
          </section>
          {menuNotice && <p className="menu-toast room-menu-toast">{menuNotice}</p>}
          <section className="room-dashboard">
            <div className={`activity-column ${isFeedBlind ? 'locked' : ''}`}>
                  <section className="member-board">
                    {memberSlots.map((slot) => {
                      const member = slot.member;
                      if (!member) {
                        return (
                          <article className="member-tile invite-slot" key={slot.id}>
                            <button className="member-tile-main invite-slot-main" onClick={shareInvite}>
                              <span className="invite-plus">+</span>
                              <strong>친구 초대</strong>
                            </button>
                          </article>
                        );
                      }
                      const hasVisibleDrawing = Boolean(member.drawing);
                      const memberSubmitted = Boolean(member.submitted || member.drawing);
                      const mine = String(member.userId) === String(auth.userId);
                      const blind = Boolean(isFeedBlind && !mine && memberSubmitted);
                      const emptyMessage = mine && isToday && !mySubmitted ? '그림을 추가해 주세요' : '아직 그림이 없어요';
                      return (
                        <article
                          className={`member-tile ${hasVisibleDrawing ? 'done' : ''} ${blind ? 'blind' : ''} ${mine ? 'mine' : ''}`}
                          key={slot.id}
                        >
                          <button className="member-tile-main" onClick={() => openMember(member)}>
                            {member.drawing && (
                              <img className="slot-bg-image" src={drawingImageUrl(member.drawing)} alt={`${member.nickname} 미리보기`} loading="lazy" decoding="async" />
                            )}
                            <div className="member-slot-head">
                              <span className="slot-avatar">
                                {member.profileImageUrl ? (
                                  <img src={member.profileImageUrl} alt={`${member.nickname} 프로필`} loading="lazy" decoding="async" />
                                ) : (
                                  member.nickname.slice(0, 1)
                                )}
                              </span>
                              <div className="member-slot-copy">
                                <strong>{member.nickname}</strong>
                              </div>
                            </div>
                            {blind && (
                              <div className="slot-blind-state">
                                <Lock size={18} />
                                <span>내 그림을 올리면 볼 수 있어요</span>
                              </div>
                            )}
                            {!member.drawing && !blind && (
                              <div className="slot-empty-state">
                                <Edit3 size={18} />
                                <span>{emptyMessage}</span>
                              </div>
                            )}
                          </button>
                          {member.drawing && !blind && (
                            <button className="quote-button" onClick={() => {
                              setChatQuote(member.drawing);
                              setRoomView('chat');
                            }} title="채팅에 인용" aria-label="채팅에 인용"><Quote size={30} /></button>
                          )}
                        </article>
                      );
                    })}
                  </section>
                  {myDrawing && (
                    <section className="my-preview">
                      <div>
                        <p className="eyebrow">Saved</p>
                        <h2>내 그림 미리보기</h2>
                      </div>
                      <img src={drawingImageUrl(myDrawing)} alt="내 그림 미리보기" loading="lazy" decoding="async" />
                      <button onClick={() => {
                        setChatQuote(myDrawing);
                        setRoomView('chat');
                      }}><Quote size={15} /> 인용</button>
                      {isToday ? <button onClick={() => setEditorOpen(true)}>수정하기</button> : <button onClick={() => setViewerDrawing(myDrawing)}>크게보기</button>}
                    </section>
                  )}
            </div>
          </section>
        </>
      )}
      {roomView === 'feed' && isToday && !myDrawing && <button className="floating-draw" onClick={() => setEditorOpen(true)}><Edit3 size={18} /> 내 그림 그리기</button>}
      {editorOpen && (
        <DrawingModal
          auth={auth}
          onAuth={onAuth}
          groupId={group.id}
          existingDrawing={myDrawing}
          onClose={() => setEditorOpen(false)}
          onSaved={refreshAll}
        />
      )}
      <DrawingViewer drawing={viewerDrawing} onClose={() => setViewerDrawing(null)} onQuote={(drawing) => {
        setChatQuote(drawing);
        setRoomView('chat');
      }} />
    </main>
  );
}
