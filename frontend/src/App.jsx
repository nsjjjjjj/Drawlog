import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Brush,
  ChevronLeft,
  Copy,
  Edit3,
  Eraser,
  Eye,
  CalendarDays,
  ChevronRight,
  LogOut,
  Plus,
  Redo2,
  Save,
  Trash2,
  Undo2,
  Users,
  X,
  ZoomIn,
  ZoomOut,
} from 'lucide-react';

const API_BASE = import.meta.env.VITE_API_BASE || '/api';

function todayString() {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}

async function request(path, options = {}, token) {
  const headers = { ...(options.headers || {}) };
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  if (!response.ok) {
    const raw = await response.text();
    let message = response.statusText;
    if (raw) {
      try {
        const body = JSON.parse(raw);
        message = body.message || body.error || raw;
      } catch {
        message = raw;
      }
    }
    throw new Error(message || '요청에 실패했습니다.');
  }
  if (response.status === 204) return null;
  const raw = await response.text();
  return raw ? JSON.parse(raw) : null;
}

function inviteFromUrl() {
  return new URLSearchParams(window.location.search).get('invite') || '';
}

function shiftDate(date, days) {
  const next = new Date(`${date}T00:00:00`);
  next.setDate(next.getDate() + days);
  const offset = next.getTimezoneOffset() * 60000;
  return new Date(next.getTime() - offset).toISOString().slice(0, 10);
}

function formatDateLabel(date) {
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short',
  }).format(new Date(`${date}T00:00:00`));
}

function DateNavigator({ date, onChange }) {
  const inputRef = useRef(null);

  function openCalendar() {
    const input = inputRef.current;
    if (!input) return;
    if (typeof input.showPicker === 'function') {
      input.showPicker();
    } else {
      input.focus();
      input.click();
    }
  }

  return (
    <div className="date-nav">
      <button title="이전 날짜" onClick={() => onChange(shiftDate(date, -1))}><ChevronLeft size={18} /></button>
      <button className="date-display" onClick={openCalendar}>
        <CalendarDays size={17} />
        <span>{formatDateLabel(date)}</span>
      </button>
      <button title="다음 날짜" onClick={() => onChange(shiftDate(date, 1))}><ChevronRight size={18} /></button>
      <button className="today-button" onClick={() => onChange(todayString())}>오늘</button>
      <input ref={inputRef} type="date" value={date} onChange={(e) => onChange(e.target.value)} aria-label="날짜 선택" />
    </div>
  );
}

function AuthView({ onAuth }) {
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({ username: '', email: '', password: '' });
  const [error, setError] = useState('');

  async function submit(event) {
    event.preventDefault();
    setError('');
    try {
      const payload = mode === 'signup' ? form : { email: form.email, password: form.password };
      const data = await request(`/auth/${mode}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      onAuth(data);
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-panel">
        <div>
          <p className="eyebrow">Drawlog</p>
          <h1>친구들과 하루에 한 장씩 남기는 그림 로그</h1>
        </div>
        <div className="segmented">
          <button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>로그인</button>
          <button type="button" className={mode === 'signup' ? 'active' : ''} onClick={() => setMode('signup')}>회원가입</button>
        </div>
        <form onSubmit={submit} className="stack">
          {mode === 'signup' && (
            <label>
              이름
              <input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} required minLength={2} />
            </label>
          )}
          <label>
            이메일
            <input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
          </label>
          <label>
            비밀번호
            <input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required minLength={6} />
          </label>
          {error && <p className="error">{error}</p>}
          <button className="primary" type="submit">{mode === 'login' ? '로그인' : '가입하고 시작'}</button>
        </form>
      </section>
    </main>
  );
}

function GroupLobby({ token, groups, onRefresh, onSelect }) {
  const [name, setName] = useState('');
  const [initialTopic, setInitialTopic] = useState('');
  const [inviteCode, setInviteCode] = useState(inviteFromUrl());
  const [message, setMessage] = useState('');

  async function createGroup(event) {
    event.preventDefault();
    setMessage('');
    try {
      const group = await request('/groups', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, initialTopic: initialTopic || null }),
      }, token);
      setName('');
      setInitialTopic('');
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
      }, token);
      setInviteCode('');
      window.history.replaceState({}, '', window.location.pathname);
      await onRefresh();
      onSelect(group.id);
    } catch (err) {
      setMessage(err.message);
    }
  }

  return (
    <section className="group-lobby">
      <div className="lobby-heading">
        <p className="eyebrow">Groups</p>
        <h1>어느 친구방에 기록할까요?</h1>
      </div>
      <div className="group-grid">
        {groups.map((group) => (
          <button className="group-tile" key={group.id} onClick={() => onSelect(group.id)}>
            <strong>{group.name}</strong>
            <span>{group.members.length}명</span>
          </button>
        ))}
      </div>
      <div className="lobby-forms">
        <form onSubmit={createGroup} className="panel compact">
          <div className="panel-title"><Plus size={18} /><h2>그룹 생성</h2></div>
          <input placeholder="새 그룹 이름" value={name} onChange={(e) => setName(e.target.value)} required />
          <input placeholder="첫 주제 직접 설정 (비우면 자동)" value={initialTopic} onChange={(e) => setInitialTopic(e.target.value)} maxLength={120} />
          <button className="primary" type="submit">만들기</button>
        </form>
        <form onSubmit={joinGroup} className="panel compact">
          <div className="panel-title"><Users size={18} /><h2>초대코드 입장</h2></div>
          <input placeholder="초대코드" value={inviteCode} onChange={(e) => setInviteCode(e.target.value.toUpperCase())} required />
          <button className="primary" type="submit">입장하기</button>
        </form>
      </div>
      {message && <p className="error">{message}</p>}
    </section>
  );
}

function SuggestionEditor({ token, groupId, refreshKey }) {
  const [suggestion, setSuggestion] = useState(null);
  const [text, setText] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!groupId) return;
    request(`/topics/suggestions/mine?groupId=${groupId}`, {}, token)
      .then((data) => {
        setSuggestion(data);
        setText(data?.text || '');
      })
      .catch((err) => setMessage(err.message));
  }, [token, groupId, refreshKey]);

  async function save(event) {
    event.preventDefault();
    setMessage('');
    try {
      const data = await request(suggestion ? `/topics/suggestions/${suggestion.id}` : '/topics/suggestions', {
        method: suggestion ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ groupId, text }),
      }, token);
      setSuggestion(data);
      setText(data.text);
      setMessage(suggestion ? '내일 주제를 수정했어요.' : '내일 주제를 제안했어요.');
    } catch (err) {
      setMessage(err.message);
    }
  }

  async function remove() {
    if (!suggestion) return;
    setMessage('');
    try {
      await request(`/topics/suggestions/${suggestion.id}`, { method: 'DELETE' }, token);
      setSuggestion(null);
      setText('');
      setMessage('내일 주제 제안을 삭제했어요.');
    } catch (err) {
      setMessage(err.message);
    }
  }

  return (
    <form onSubmit={save} className="suggestion-row">
      <input placeholder="내일 주제 제안" value={text} onChange={(e) => setText(e.target.value)} required maxLength={120} />
      <button title={suggestion ? '수정' : '제안'} type="submit"><Edit3 size={17} /></button>
      {suggestion && <button title="삭제" type="button" onClick={remove}><Trash2 size={17} /></button>}
      {message && <p className="notice">{message}</p>}
    </form>
  );
}

function DrawingModal({ token, groupId, existingDrawing, onClose, onSaved }) {
  const canvasRef = useRef(null);
  const canvasSize = useRef({ width: 0, height: 0 });
  const drawingRef = useRef(false);
  const lastPoint = useRef(null);
  const undoStack = useRef([]);
  const redoStack = useRef([]);
  const [tool, setTool] = useState('pen');
  const [brushMode, setBrushMode] = useState('ink');
  const [color, setColor] = useState('#202124');
  const [size, setSize] = useState(8);
  const [zoom, setZoom] = useState(1);
  const [historyTick, setHistoryTick] = useState(0);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, []);

  useEffect(() => {
    const canvas = canvasRef.current;
    const rect = canvas.getBoundingClientRect();
    const ratio = window.devicePixelRatio || 1;
    canvasSize.current = { width: rect.width, height: rect.height };
    canvas.width = Math.floor(rect.width * ratio);
    canvas.height = Math.floor(rect.height * ratio);
    const ctx = canvas.getContext('2d');
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, rect.width, rect.height);
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    if (existingDrawing?.imageUrl) {
      const image = new Image();
      image.crossOrigin = 'anonymous';
      image.onload = () => {
        ctx.drawImage(image, 0, 0, canvasSize.current.width, canvasSize.current.height);
        pushHistory();
      };
      image.src = existingDrawing.imageUrl;
    } else {
      pushHistory();
    }
  }, [existingDrawing?.id]);

  function canvasRect() {
    return canvasRef.current.getBoundingClientRect();
  }

  function pushHistory() {
    const canvas = canvasRef.current;
    undoStack.current.push(canvas.toDataURL('image/png'));
    if (undoStack.current.length > 40) undoStack.current.shift();
    redoStack.current = [];
    setHistoryTick((n) => n + 1);
  }

  function restore(dataUrl) {
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    const image = new Image();
    image.onload = () => {
      ctx.globalCompositeOperation = 'source-over';
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(0, 0, canvasSize.current.width, canvasSize.current.height);
      ctx.drawImage(image, 0, 0, canvasSize.current.width, canvasSize.current.height);
    };
    image.src = dataUrl;
  }

  function undo() {
    if (undoStack.current.length <= 1) return;
    const current = undoStack.current.pop();
    redoStack.current.push(current);
    restore(undoStack.current[undoStack.current.length - 1]);
    setHistoryTick((n) => n + 1);
  }

  function redo() {
    const next = redoStack.current.pop();
    if (!next) return;
    undoStack.current.push(next);
    restore(next);
    setHistoryTick((n) => n + 1);
  }

  function point(event) {
    const rect = canvasRect();
    return { x: (event.clientX - rect.left) / zoom, y: (event.clientY - rect.top) / zoom };
  }

  function start(event) {
    drawingRef.current = true;
    lastPoint.current = point(event);
    canvasRef.current.setPointerCapture(event.pointerId);
  }

  function move(event) {
    if (!drawingRef.current) return;
    const ctx = canvasRef.current.getContext('2d');
    const next = point(event);
    ctx.globalCompositeOperation = tool === 'eraser' ? 'destination-out' : 'source-over';
    ctx.strokeStyle = color;
    ctx.globalAlpha = tool === 'pen' && brushMode === 'marker' ? 0.32 : 1;
    ctx.lineWidth = size;
    const mid = {
      x: (lastPoint.current.x + next.x) / 2,
      y: (lastPoint.current.y + next.y) / 2,
    };
    ctx.beginPath();
    ctx.moveTo(lastPoint.current.x, lastPoint.current.y);
    ctx.quadraticCurveTo(lastPoint.current.x, lastPoint.current.y, mid.x, mid.y);
    ctx.stroke();
    ctx.globalAlpha = 1;
    lastPoint.current = next;
  }

  function stop(event) {
    if (!drawingRef.current) return;
    drawingRef.current = false;
    lastPoint.current = null;
    pushHistory();
    try {
      canvasRef.current.releasePointerCapture(event.pointerId);
    } catch {}
  }

  function clear() {
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    ctx.globalCompositeOperation = 'source-over';
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, canvasSize.current.width, canvasSize.current.height);
    pushHistory();
  }

  async function save() {
    setSaving(true);
    try {
      const blob = await new Promise((resolve) => canvasRef.current.toBlob(resolve, 'image/webp', 0.92));
      const formData = new FormData();
      formData.append('groupId', groupId);
      formData.append('file', blob, 'drawing.webp');
      const response = await fetch(`${API_BASE}/drawings`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: formData,
      });
      if (!response.ok) throw new Error(await response.text());
      onSaved();
      onClose();
    } catch (err) {
      alert(err.message || '저장에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  }

  async function remove() {
    if (!existingDrawing) return;
    try {
      await request(`/drawings/${existingDrawing.id}`, { method: 'DELETE' }, token);
      onSaved();
      onClose();
    } catch (err) {
      alert(err.message);
    }
  }

  const canUndo = undoStack.current.length > 1;
  const canRedo = redoStack.current.length > 0;
  void historyTick;

  return (
    <div className="modal-backdrop">
      <section className="draw-modal">
        <div className="modal-header">
          <h2>{existingDrawing ? '내 그림 수정' : '내 그림 그리기'}</h2>
          <button title="닫기" onClick={onClose}><X size={18} /></button>
        </div>
        <div className="toolbar">
          <button title="펜" className={tool === 'pen' ? 'active' : ''} onClick={() => setTool('pen')}><Brush size={18} /></button>
          <button title="지우개" className={tool === 'eraser' ? 'active' : ''} onClick={() => setTool('eraser')}><Eraser size={18} /></button>
          <button title="일반 펜" className={brushMode === 'ink' ? 'active' : ''} onClick={() => { setTool('pen'); setBrushMode('ink'); }}>펜</button>
          <button title="형광펜" className={brushMode === 'marker' ? 'active' : ''} onClick={() => { setTool('pen'); setBrushMode('marker'); }}>형광</button>
          <button title="뒤로" disabled={!canUndo} onClick={undo}><Undo2 size={18} /></button>
          <button title="앞으로" disabled={!canRedo} onClick={redo}><Redo2 size={18} /></button>
          <button title="축소" onClick={() => setZoom((value) => Math.max(0.75, Number((value - 0.25).toFixed(2))))}><ZoomOut size={18} /></button>
          <span className="size-chip">{Math.round(zoom * 100)}%</span>
          <button title="확대" onClick={() => setZoom((value) => Math.min(2, Number((value + 0.25).toFixed(2))))}><ZoomIn size={18} /></button>
          <input title="색상" type="color" value={color} onChange={(e) => setColor(e.target.value)} />
          <input title="굵기" type="range" min="2" max="32" value={size} onChange={(e) => setSize(Number(e.target.value))} />
          <span className="size-chip">{size}px</span>
          <button title="전체 지우기" onClick={clear}><Trash2 size={18} /></button>
          {existingDrawing && <button title="그림 삭제" onClick={remove}>삭제</button>}
          <button className="submit" title="저장" onClick={save} disabled={saving}><Save size={18} /> {saving ? '저장 중' : '저장'}</button>
        </div>
        <div className="canvas-viewport">
          <canvas
            ref={canvasRef}
            className="drawing-canvas"
            style={{ width: `${zoom * 100}%`, height: `calc(${zoom} * min(62vh, 620px))` }}
            onPointerDown={start}
            onPointerMove={move}
            onPointerUp={stop}
            onPointerCancel={stop}
            onPointerLeave={stop}
          />
        </div>
      </section>
    </div>
  );
}

function DrawingViewer({ drawing, onClose }) {
  if (!drawing) return null;
  return (
    <div className="modal-backdrop">
      <section className="viewer-modal">
        <div className="modal-header">
          <div>
            <h2>{drawing.username}</h2>
            <p className="muted">{new Date(drawing.createdAt).toLocaleString()}</p>
          </div>
          <button title="닫기" onClick={onClose}><X size={18} /></button>
        </div>
        <img src={drawing.imageUrl} alt={`${drawing.username}의 그림`} />
      </section>
    </div>
  );
}

function GroupRoom({ auth, group, onBack, onRefreshGroups, onLeftGroup }) {
  const [topic, setTopic] = useState(null);
  const [feed, setFeed] = useState(null);
  const [date, setDate] = useState(todayString());
  const [refreshKey, setRefreshKey] = useState(0);
  const [editorOpen, setEditorOpen] = useState(false);
  const [viewerDrawing, setViewerDrawing] = useState(null);
  const [groupName, setGroupName] = useState(group.name);
  const [ownerMessage, setOwnerMessage] = useState('');
  const inviteLink = `${window.location.origin}?invite=${group.inviteCode}`;
  const isToday = date === todayString();

  useEffect(() => {
    request(`/topics/today?groupId=${group.id}`, {}, auth.token).then(setTopic).catch(() => setTopic(null));
  }, [auth.token, group.id, refreshKey]);

  useEffect(() => {
    setGroupName(group.name);
  }, [group.name]);

  useEffect(() => {
    request(`/feed?groupId=${group.id}&date=${date}`, {}, auth.token).then(setFeed).catch(() => setFeed(null));
  }, [auth.token, group.id, date, refreshKey]);

  const myMember = feed?.members?.find((member) => member.userId === auth.userId);
  const myDrawing = myMember?.drawing || null;

  async function copyInvite() {
    await navigator.clipboard.writeText(inviteLink);
  }

  async function leaveGroup() {
    const ok = window.confirm(`${group.name} 그룹에서 나갈까요? 마지막 멤버라면 그룹의 주제, 제안, 그림 데이터가 모두 삭제됩니다.`);
    if (!ok) return;
    try {
      const result = await request(`/groups/${group.id}/membership`, { method: 'DELETE' }, auth.token);
      onLeftGroup(result?.groupDeleted);
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

  function refreshAll() {
    setRefreshKey((n) => n + 1);
    onRefreshGroups();
  }

  async function renameGroup(event) {
    event.preventDefault();
    setOwnerMessage('');
    try {
      await request(`/groups/${group.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: groupName }),
      }, auth.token);
      setOwnerMessage('그룹 이름을 바꿨어요.');
      await onRefreshGroups();
    } catch (err) {
      setOwnerMessage(err.message);
    }
  }

  async function regenerateInvite() {
    setOwnerMessage('');
    try {
      await request(`/groups/${group.id}/invite-code`, { method: 'POST' }, auth.token);
      setOwnerMessage('초대코드를 새로 만들었어요.');
      await onRefreshGroups();
    } catch (err) {
      setOwnerMessage(err.message);
    }
  }

  async function removeMember(member) {
    const ok = window.confirm(`${member.username}님을 그룹에서 내보낼까요?`);
    if (!ok) return;
    setOwnerMessage('');
    try {
      await request(`/groups/${group.id}/members/${member.userId}`, { method: 'DELETE' }, auth.token);
      setOwnerMessage('멤버를 내보냈어요.');
      refreshAll();
    } catch (err) {
      setOwnerMessage(err.message);
    }
  }

  return (
    <main className="room-shell">
      <header className="room-header">
        <button className="ghost" onClick={onBack}><ChevronLeft size={18} /> 그룹 선택</button>
        <button className="ghost" onClick={() => {
          localStorage.removeItem('drawlog.auth');
          window.location.reload();
        }}><LogOut size={18} /> 로그아웃</button>
      </header>
      <section className="topic-hero">
        <p className="eyebrow">{group.name}</p>
        <h1>{topic?.text || '오늘의 주제를 불러오는 중'}</h1>
        <div className="hero-meta">
          <DateNavigator date={date} onChange={setDate} />
          <span>{date === topic?.date ? '오늘의 주제' : `${date} 피드`}</span>
        </div>
      </section>
      <section className="invite-band">
        <div>
          <strong>초대코드 {group.inviteCode}</strong>
          <span>{inviteLink}</span>
        </div>
        <div className="invite-actions">
          <button onClick={copyInvite}><Copy size={17} /> 복사</button>
          <button className="danger" onClick={leaveGroup}><LogOut size={17} /> 그룹 나가기</button>
        </div>
      </section>
      <SuggestionEditor token={auth.token} groupId={group.id} refreshKey={refreshKey} />
      {group.owner && (
        <section className="owner-panel">
          <div className="panel-title"><Users size={18} /><h2>그룹 관리</h2></div>
          <form onSubmit={renameGroup} className="owner-name-row">
            <input value={groupName} onChange={(e) => setGroupName(e.target.value)} minLength={2} maxLength={80} required />
            <button type="submit">이름 변경</button>
            <button type="button" onClick={regenerateInvite}>초대코드 재발급</button>
          </form>
          <div className="owner-member-list">
            {feed?.members?.map((member) => (
              <div key={member.userId}>
                <span>{member.username}{member.owner ? ' · 방장' : ''}</span>
                {!member.owner && <button onClick={() => removeMember(member)}>내보내기</button>}
              </div>
            ))}
          </div>
          {ownerMessage && <p className={ownerMessage.includes('못') || ownerMessage.includes('없') ? 'error' : 'notice'}>{ownerMessage}</p>}
        </section>
      )}
      <section className="member-board">
        {feed?.members?.map((member) => (
          <button
            className={`member-tile ${member.drawing ? 'done' : ''} ${member.userId === auth.userId ? 'mine' : ''}`}
            key={member.userId}
            onClick={() => openMember(member)}
          >
            <span>{member.username}</span>
            {member.owner && <strong className="owner-badge">방장</strong>}
            {member.drawing ? (
              <img src={member.drawing.imageUrl} alt={`${member.username} 미리보기`} />
            ) : (
              <small>{member.userId === auth.userId && isToday ? '그리기' : '아직 없음'}</small>
            )}
            <em>{member.userId === auth.userId && isToday ? <Edit3 size={15} /> : member.drawing ? <Eye size={15} /> : null}</em>
          </button>
        ))}
      </section>
      {myDrawing && (
        <section className="my-preview">
          <div>
            <p className="eyebrow">Saved</p>
            <h2>내 그림 미리보기</h2>
          </div>
          <img src={myDrawing.imageUrl} alt="내 그림 미리보기" />
          {isToday ? <button onClick={() => setEditorOpen(true)}>수정하기</button> : <button onClick={() => setViewerDrawing(myDrawing)}>크게보기</button>}
        </section>
      )}
      {isToday && !myDrawing && <button className="floating-draw" onClick={() => setEditorOpen(true)}><Edit3 size={18} /> 내 그림 그리기</button>}
      {editorOpen && (
        <DrawingModal
          token={auth.token}
          groupId={group.id}
          existingDrawing={myDrawing}
          onClose={() => setEditorOpen(false)}
          onSaved={refreshAll}
        />
      )}
      <DrawingViewer drawing={viewerDrawing} onClose={() => setViewerDrawing(null)} />
    </main>
  );
}

export default function App() {
  const [auth, setAuth] = useState(() => {
    const raw = localStorage.getItem('drawlog.auth');
    return raw ? JSON.parse(raw) : null;
  });
  const [groups, setGroups] = useState([]);
  const [selectedGroupId, setSelectedGroupId] = useState(() => localStorage.getItem('drawlog.groupId'));

  useEffect(() => {
    if (!auth) return;
    localStorage.setItem('drawlog.auth', JSON.stringify(auth));
  }, [auth]);

  async function loadGroups() {
    if (!auth?.token) return;
    const data = await request('/groups', {}, auth.token);
    setGroups(data || []);
    if (selectedGroupId && !data?.some((group) => String(group.id) === String(selectedGroupId))) {
      setSelectedGroupId(null);
      localStorage.removeItem('drawlog.groupId');
    }
  }

  useEffect(() => {
    loadGroups().catch(() => setGroups([]));
  }, [auth?.token]);

  function selectGroup(groupId) {
    setSelectedGroupId(String(groupId));
    localStorage.setItem('drawlog.groupId', String(groupId));
  }

  const selectedGroup = useMemo(
    () => groups.find((group) => String(group.id) === String(selectedGroupId)),
    [groups, selectedGroupId],
  );

  if (!auth) return <AuthView onAuth={setAuth} />;

  if (!selectedGroup) {
    return (
      <main className="app-shell">
        <header className="topbar">
          <div>
            <p className="eyebrow">Drawlog</p>
            <h1>{auth.username}님의 그룹</h1>
          </div>
          <button className="ghost" onClick={() => {
            localStorage.removeItem('drawlog.auth');
            setAuth(null);
          }}><LogOut size={18} /> 로그아웃</button>
        </header>
        <GroupLobby token={auth.token} groups={groups} onRefresh={loadGroups} onSelect={selectGroup} />
      </main>
    );
  }

  return (
    <GroupRoom
      auth={auth}
      group={selectedGroup}
      onBack={() => {
        setSelectedGroupId(null);
        localStorage.removeItem('drawlog.groupId');
      }}
      onRefreshGroups={loadGroups}
      onLeftGroup={async () => {
        setSelectedGroupId(null);
        localStorage.removeItem('drawlog.groupId');
        await loadGroups();
      }}
    />
  );
}
