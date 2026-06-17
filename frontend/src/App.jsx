import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Bell,
  BookOpen,
  Brush,
  CalendarDays,
  Check,
  ChevronLeft,
  ChevronRight,
  Edit3,
  Eraser,
  Eye,
  Highlighter,
  KeyRound,
  Lock,
  LogOut,
  MessageCircle,
  Plus,
  Quote,
  Redo2,
  Save,
  Settings,
  Trash2,
  Undo2,
  Users,
  Vote,
  X,
} from 'lucide-react';

const API_BASE = import.meta.env.VITE_API_BASE || '/api';
const CANVAS_HISTORY_LIMIT = 30;
const CANVAS_INPUT_STYLE = {
  touchAction: 'none',
  userSelect: 'none',
  WebkitUserSelect: 'none',
  WebkitTouchCallout: 'none',
  pointerEvents: 'auto',
};

function todayString() {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}

function shiftDate(date, days) {
  const next = new Date(`${date}T00:00:00`);
  next.setDate(next.getDate() + days);
  const offset = next.getTimezoneOffset() * 60000;
  return new Date(next.getTime() - offset).toISOString().slice(0, 10);
}

function tomorrowString() {
  return shiftDate(todayString(), 1);
}

function formatDateLabel(date) {
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short',
  }).format(new Date(`${date}T00:00:00`));
}

function monthKey(date) {
  return date.slice(0, 7);
}

function shiftMonth(month, delta) {
  const [year, monthIndex] = month.split('-').map(Number);
  const next = new Date(year, monthIndex - 1 + delta, 1);
  return `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, '0')}`;
}

function monthDates(month) {
  const [year, monthIndex] = month.split('-').map(Number);
  const lastDay = new Date(year, monthIndex, 0).getDate();
  const firstWeekday = new Date(year, monthIndex - 1, 1).getDay();
  const dates = Array.from({ length: firstWeekday }, () => null);
  for (let day = 1; day <= lastDay; day += 1) {
    dates.push(`${year}-${String(monthIndex).padStart(2, '0')}-${String(day).padStart(2, '0')}`);
  }
  return dates;
}

function inviteFromUrl() {
  const pathMatch = window.location.pathname.match(/^\/invite\/([^/]+)/);
  return pathMatch?.[1] || new URLSearchParams(window.location.search).get('invite') || '';
}

async function parseResponse(response) {
  if (response.status === 204) return null;
  const raw = await response.text();
  return raw ? JSON.parse(raw) : null;
}

async function request(path, options = {}, auth = null, onAuth = null) {
  const token = typeof auth === 'string' ? auth : auth?.token;
  const buildOptions = (accessToken) => {
    const headers = { ...(options.headers || {}) };
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
    return { ...options, headers, credentials: 'include' };
  };

  let response = await fetch(`${API_BASE}${path}`, buildOptions(token));
  if (response.status === 401 && onAuth) {
    const refreshed = await fetch(`${API_BASE}/auth/refresh`, { method: 'POST', credentials: 'include' });
    if (refreshed.ok) {
      const nextAuth = await parseResponse(refreshed);
      onAuth(nextAuth);
      response = await fetch(`${API_BASE}${path}`, buildOptions(nextAuth.token));
    }
  }
  if (!response.ok) {
    const raw = await response.text();
    let message = response.statusText;
    if (raw) {
      try {
        const body = JSON.parse(raw);
        message = body.message || body.code || raw;
      } catch {
        message = raw;
      }
    }
    throw new Error(message || '요청에 실패했습니다.');
  }
  return parseResponse(response);
}

function drawingImageUrl(drawing) {
  return drawing?.imageUrl || drawing?.imagePath || drawing?.thumbnailUrl || '';
}

function quoteImageUrl(quote) {
  return quote?.imageUrl || quote?.imagePath || quote?.thumbnailUrl || '';
}

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

function AuthView({ onAuth }) {
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({ nickname: '', email: '', password: '' });
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
          <h1>친구들과 하루 한 장</h1>
        </div>
        <div className="segmented">
          <button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>로그인</button>
          <button type="button" className={mode === 'signup' ? 'active' : ''} onClick={() => setMode('signup')}>회원가입</button>
        </div>
        <form onSubmit={submit} className="stack">
          {mode === 'signup' && (
            <label>
              닉네임
              <input value={form.nickname} onChange={(event) => setForm({ ...form, nickname: event.target.value })} required minLength={2} />
            </label>
          )}
          <label>
            이메일
            <input type="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} required />
          </label>
          <label>
            비밀번호
            <input type="password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} required minLength={6} />
          </label>
          {error && <p className="error">{error}</p>}
          <button className="primary" type="submit">{mode === 'login' ? '로그인' : '가입하고 시작'}</button>
        </form>
      </section>
    </main>
  );
}

function GroupLobby({ auth, groups, onAuth, onRefresh, onSelect, composerOpen, onCloseComposer }) {
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
                <i key={member.userId}>{member.nickname.slice(0, 1)}</i>
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

function NotificationModal({ auth, onAuth, onClose }) {
  const [notifications, setNotifications] = useState([]);
  const [settings, setSettings] = useState(null);
  const [error, setError] = useState('');

  async function load() {
    try {
      const [items, nextSettings] = await Promise.all([
        request('/notifications', {}, auth, onAuth),
        request('/notification-settings', {}, auth, onAuth),
      ]);
      setNotifications(items || []);
      setSettings(nextSettings);
      setError('');
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function readAll() {
    await request('/notifications/read-all', { method: 'PATCH' }, auth, onAuth);
    await load();
  }

  async function updateAll(enabled) {
    const next = await request('/notification-settings', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ allEnabled: enabled }),
    }, auth, onAuth);
    setSettings(next);
  }

  async function updateGroup(groupId, enabled) {
    await request(`/groups/${groupId}/notification-setting`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabled }),
    }, auth, onAuth);
    await load();
  }

  return (
    <div className="modal-backdrop">
      <section className="notification-modal">
        <div className="modal-header">
          <h2>알림</h2>
          <button title="닫기" onClick={onClose}><X size={18} /></button>
        </div>
        <div className="notification-actions">
          <button onClick={readAll}><Check size={16} /> 모두 읽음</button>
          <button className={settings?.allEnabled ? 'active' : ''} onClick={() => updateAll(!settings?.allEnabled)}>
            <Bell size={16} /> 전체 {settings?.allEnabled ? 'ON' : 'OFF'}
          </button>
        </div>
        {settings?.allEnabled ? (
          <div className="settings-list">
            {settings?.groups?.map((group) => (
              <button key={group.groupId} onClick={() => updateGroup(group.groupId, !group.enabled)}>
                <span>{group.groupName}</span>
                <strong>{group.enabled ? 'ON' : 'OFF'}</strong>
              </button>
            ))}
          </div>
        ) : (
          <p className="muted notification-muted">전체 알림이 꺼져 있어요.</p>
        )}
        <div className="notification-list">
          {notifications.length === 0 && <p className="muted">아직 알림이 없어요.</p>}
          {notifications.map((item) => (
            <article key={item.id} className={item.readAt ? '' : 'unread'}>
              <strong>{item.title}</strong>
              <p>{item.message}</p>
              <span>{new Date(item.createdAt).toLocaleString('ko-KR')}</span>
            </article>
          ))}
        </div>
        {error && <p className="error">{error}</p>}
      </section>
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

function DrawingModal({ auth, onAuth, groupId, existingDrawing, onClose, onSaved }) {
  const canvasRef = useRef(null);
  const canvasSizeRef = useRef({ width: 0, height: 0 });
  const drawingRef = useRef(false);
  const lastPointRef = useRef(null);
  const undoRef = useRef([]);
  const redoRef = useRef([]);
  const pointerRef = useRef(null);
  const [tool, setTool] = useState('pen');
  const [color, setColor] = useState('#202124');
  const [size, setSize] = useState(8);
  const [saving, setSaving] = useState(false);
  const [historyTick, setHistoryTick] = useState(0);

  function context() {
    const canvas = canvasRef.current;
    return canvas?.getContext('2d') || null;
  }

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    const frame = window.requestAnimationFrame(() => {
      if (cancelled || !canvasRef.current) return;
      setupCanvas();
    });
    return () => {
      cancelled = true;
      window.cancelAnimationFrame(frame);
    };
  }, [existingDrawing?.id]);

  function preventCanvasEvent(event) {
    const targetEvent = event?.nativeEvent || event;
    if (targetEvent?.cancelable !== false) event.preventDefault?.();
  }

  function setupCanvas() {
    const canvas = canvasRef.current;
    const rect = canvas.getBoundingClientRect();
    const fallbackSize = Math.min(Math.max(window.innerWidth - 40, 320), 680);
    const width = Math.max(Math.round(rect.width || fallbackSize), 320);
    const height = width;
    const ratio = window.devicePixelRatio || 1;
    canvasSizeRef.current = { width, height };
    canvas.width = Math.floor(width * ratio);
    canvas.height = Math.floor(height * ratio);
    const ctx = context();
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    fillWhite();
    undoRef.current = [];
    redoRef.current = [];
    setHistoryTick((value) => value + 1);

    const imageUrl = drawingImageUrl(existingDrawing);
    if (!imageUrl) return;
    const image = new Image();
    image.crossOrigin = 'anonymous';
    image.onload = () => {
      if (!canvasRef.current) return;
      fillWhite();
      context().drawImage(image, 0, 0, canvasSizeRef.current.width, canvasSizeRef.current.height);
    };
    image.onerror = () => fillWhite();
    image.src = imageUrl;
  }

  function fillWhite() {
    const ctx = context();
    if (!ctx) return;
    ctx.save();
    ctx.globalCompositeOperation = 'source-over';
    ctx.globalAlpha = 1;
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, canvasSizeRef.current.width, canvasSizeRef.current.height);
    ctx.restore();
  }

  function snapshot() {
    return canvasRef.current?.toDataURL('image/png') || null;
  }

  function pushUndoSnapshot() {
    const current = snapshot();
    if (!current) return;
    undoRef.current.push(current);
    if (undoRef.current.length > CANVAS_HISTORY_LIMIT) undoRef.current.shift();
    redoRef.current = [];
    setHistoryTick((value) => value + 1);
  }

  function restore(dataUrl) {
    if (!dataUrl) return;
    const image = new Image();
    image.onload = () => {
      if (!canvasRef.current) return;
      fillWhite();
      context().drawImage(image, 0, 0, canvasSizeRef.current.width, canvasSizeRef.current.height);
    };
    image.src = dataUrl;
  }

  function point(event) {
    const canvas = canvasRef.current;
    if (!canvas || event.clientX == null || event.clientY == null) return null;
    const rect = canvas.getBoundingClientRect();
    const width = canvasSizeRef.current.width || rect.width || 1;
    const height = canvasSizeRef.current.height || rect.height || 1;
    return {
      x: Math.max(0, Math.min(width, event.clientX - rect.left)),
      y: Math.max(0, Math.min(height, event.clientY - rect.top)),
    };
  }

  function applyBrush(ctx) {
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.globalCompositeOperation = tool === 'eraser' ? 'destination-out' : 'source-over';
    ctx.globalAlpha = tool === 'highlighter' ? 0.32 : 1;
    ctx.strokeStyle = tool === 'eraser' ? '#000000' : color;
    ctx.fillStyle = ctx.strokeStyle;
    ctx.lineWidth = tool === 'highlighter' ? Math.max(size * 1.8, 2) : size;
  }

  function drawDot(at) {
    const ctx = context();
    if (!ctx || !at) return;
    ctx.save();
    applyBrush(ctx);
    ctx.beginPath();
    ctx.arc(at.x, at.y, Math.max((tool === 'highlighter' ? size * 1.8 : size) / 2, 1), 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  function drawTo(next) {
    if (!drawingRef.current || !lastPointRef.current || !next) return;
    const ctx = context();
    if (!ctx) return;
    const previous = lastPointRef.current;
    ctx.save();
    applyBrush(ctx);
    ctx.beginPath();
    ctx.moveTo(previous.x, previous.y);
    ctx.lineTo(next.x, next.y);
    ctx.stroke();
    ctx.restore();
    lastPointRef.current = next;
  }

  function pointerEvents(event) {
    const native = event.nativeEvent || event;
    if (typeof native.getCoalescedEvents !== 'function') return [event.clientX == null ? native : event];
    const events = native.getCoalescedEvents();
    return events.length ? events : [native];
  }

  function startDrawing(event) {
    preventCanvasEvent(event);
    if (event.button !== undefined && event.button !== 0) return;
    const startPoint = point(event);
    if (!startPoint) return;
    pointerRef.current = event.pointerId ?? null;
    try {
      if (event.pointerId != null) event.currentTarget.setPointerCapture(event.pointerId);
    } catch {}
    pushUndoSnapshot();
    drawingRef.current = true;
    lastPointRef.current = startPoint;
    drawDot(startPoint);
  }

  function moveDrawing(event) {
    preventCanvasEvent(event);
    if (!drawingRef.current) return;
    if (pointerRef.current != null && event.pointerId !== pointerRef.current) return;
    pointerEvents(event).forEach((item) => drawTo(point(item)));
  }

  function stopDrawing(event) {
    preventCanvasEvent(event);
    if (!drawingRef.current) return;
    if (pointerRef.current != null && event.pointerId !== pointerRef.current) return;
    drawTo(point(event));
    drawingRef.current = false;
    lastPointRef.current = null;
    try {
      if (event.pointerId != null) event.currentTarget.releasePointerCapture(event.pointerId);
    } catch {}
    pointerRef.current = null;
  }

  function firstTouch(event) {
    return event.touches?.[0] || event.changedTouches?.[0] || null;
  }

  function touchEvent(touch, event) {
    return {
      clientX: touch.clientX,
      clientY: touch.clientY,
      currentTarget: canvasRef.current,
      preventDefault: () => event.preventDefault(),
      nativeEvent: event.nativeEvent || event,
    };
  }

  function startTouch(event) {
    if ('PointerEvent' in window) return;
    const touch = firstTouch(event);
    if (touch) startDrawing(touchEvent(touch, event));
  }

  function moveTouch(event) {
    if ('PointerEvent' in window) return;
    const touch = firstTouch(event);
    if (touch) moveDrawing(touchEvent(touch, event));
  }

  function stopTouch(event) {
    if ('PointerEvent' in window) return;
    const touch = firstTouch(event);
    if (touch) stopDrawing(touchEvent(touch, event));
  }

  function undo() {
    const previous = undoRef.current.pop();
    if (!previous) return;
    const current = snapshot();
    if (current) redoRef.current.push(current);
    restore(previous);
    setHistoryTick((value) => value + 1);
  }

  function redo() {
    const next = redoRef.current.pop();
    if (!next) return;
    const current = snapshot();
    if (current) undoRef.current.push(current);
    restore(next);
    setHistoryTick((value) => value + 1);
  }

  function clear() {
    pushUndoSnapshot();
    fillWhite();
  }

  async function canvasBlob() {
    const source = canvasRef.current;
    if (!source) return null;
    const output = document.createElement('canvas');
    output.width = source.width;
    output.height = source.height;
    const ctx = output.getContext('2d');
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, output.width, output.height);
    ctx.drawImage(source, 0, 0);
    const toBlob = (type, quality) => new Promise((resolve) => output.toBlob(resolve, type, quality));
    return (await toBlob('image/webp', 0.92)) || (await toBlob('image/png'));
  }

  async function save() {
    setSaving(true);
    try {
      const blob = await canvasBlob();
      if (!blob) throw new Error('이미지를 만들지 못했습니다.');
      const formData = new FormData();
      formData.append('image', blob, blob.type === 'image/png' ? 'drawing.png' : 'drawing.webp');
      await request(`/groups/${groupId}/drawings/today`, {
        method: existingDrawing ? 'PUT' : 'POST',
        body: formData,
      }, auth, onAuth);
      onSaved();
      onClose();
    } catch (err) {
      alert(err.message || '저장에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  }

  const canUndo = undoRef.current.length > 0;
  const canRedo = redoRef.current.length > 0;
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
          <button title="형광펜" className={tool === 'highlighter' ? 'active' : ''} onClick={() => setTool('highlighter')}><Highlighter size={18} /></button>
          <button title="지우개" className={tool === 'eraser' ? 'active' : ''} onClick={() => setTool('eraser')}><Eraser size={18} /></button>
          <button title="뒤로" disabled={!canUndo} onClick={undo}><Undo2 size={18} /></button>
          <button title="앞으로" disabled={!canRedo} onClick={redo}><Redo2 size={18} /></button>
          <input title="색상" type="color" value={color} onChange={(event) => setColor(event.target.value)} />
          <input title="굵기" type="range" min="2" max="36" value={size} onChange={(event) => setSize(Number(event.target.value))} />
          <span className="size-chip">{size}px</span>
          <button title="전체 지우기" onClick={clear}><Trash2 size={18} /></button>
          <button className="submit" title="저장" onClick={save} disabled={saving}><Save size={18} /> {saving ? '저장 중' : '저장'}</button>
        </div>
        <div className="canvas-viewport">
          <canvas
            ref={canvasRef}
            className="drawing-canvas"
            style={CANVAS_INPUT_STYLE}
            onPointerDown={startDrawing}
            onPointerMove={moveDrawing}
            onPointerUp={stopDrawing}
            onPointerCancel={stopDrawing}
            onTouchStart={startTouch}
            onTouchMove={moveTouch}
            onTouchEnd={stopTouch}
            onTouchCancel={stopTouch}
            onMouseDown={(event) => {
              if ('PointerEvent' in window) return;
              startDrawing(event);
            }}
            onMouseMove={(event) => {
              if ('PointerEvent' in window) return;
              moveDrawing(event);
            }}
            onMouseUp={(event) => {
              if ('PointerEvent' in window) return;
              stopDrawing(event);
            }}
            onContextMenu={preventCanvasEvent}
          />
        </div>
      </section>
    </div>
  );
}

function ChatPanel({ auth, onAuth, groupId, quoteTarget, onClearQuote }) {
  const [messages, setMessages] = useState([]);
  const [content, setContent] = useState('');
  const [error, setError] = useState('');
  const bottomRef = useRef(null);

  async function loadMessages({ quiet = false } = {}) {
    try {
      const data = await request(`/groups/${groupId}/chats?size=50`, {}, auth, onAuth);
      setMessages(data?.messages || []);
      if (!quiet) setError('');
    } catch (err) {
      if (!quiet) setError(err.message);
    }
  }

  useEffect(() => {
    loadMessages();
    const timer = window.setInterval(() => loadMessages({ quiet: true }), 5000);
    return () => window.clearInterval(timer);
  }, [groupId, auth.token]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages.length]);

  async function send(event) {
    event.preventDefault();
    if (!content.trim()) return;
    try {
      await request(`/groups/${groupId}/chats`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          type: quoteTarget ? 'DRAWING_QUOTE' : 'TEXT',
          content,
          drawingId: quoteTarget?.id || null,
        }),
      }, auth, onAuth);
      setContent('');
      onClearQuote();
      await loadMessages({ quiet: true });
    } catch (err) {
      setError(err.message);
    }
  }

  async function remove(messageId) {
    await request(`/groups/${groupId}/chats/${messageId}`, { method: 'DELETE' }, auth, onAuth);
    await loadMessages({ quiet: true });
  }

  const visibleMessages = messages.filter((message) => !message.deletedAt);

  return (
    <section className="chat-panel">
      <div className="chat-list">
        {visibleMessages.length === 0 && <p className="muted empty-chat">아직 대화가 없어요.</p>}
        {visibleMessages.map((message) => {
          const mine = message.userId === auth.userId;
          const quoted = Boolean(message.quote);
          return (
            <div className={`chat-row ${mine ? 'mine' : ''}`} key={message.id}>
              <div className={`chat-bubble ${quoted ? 'with-quote' : 'text-only'}`}>
                {!mine && <strong>{message.username}</strong>}
                {message.quote && (
                  <div className="chat-quote-preview">
                    <img src={quoteImageUrl(message.quote)} alt={`${message.quote.username} 그림`} />
                    <div>
                      <span>{message.quote.username}의 그림</span>
                      <p>{message.quote.topicText}</p>
                    </div>
                  </div>
                )}
                <p>{message.content}</p>
                <div className="chat-meta">
                  <span>{new Date(message.createdAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}</span>
                  {mine && !message.deletedAt && <button onClick={() => remove(message.id)}><Trash2 size={14} /> 삭제</button>}
                </div>
              </div>
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>
      {quoteTarget && (
        <div className="composer-context">
          <Quote size={16} />
          <span>{quoteTarget.nickname}님의 그림에 말하는 중</span>
          <button title="취소" onClick={onClearQuote}><X size={15} /></button>
        </div>
      )}
      <form className="chat-composer" onSubmit={send}>
        <input placeholder="메시지 입력" value={content} onChange={(event) => setContent(event.target.value)} maxLength={1000} />
        <button className="primary" type="submit">전송</button>
      </form>
      {error && <p className="error">{error}</p>}
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
        <img src={drawingImageUrl(drawing)} alt={`${drawing.nickname}의 그림`} />
        <button className="quote-wide" onClick={() => {
          onQuote(drawing);
          onClose();
        }}><Quote size={17} /> 채팅에 인용</button>
      </section>
    </div>
  );
}

function GroupRoom({ auth, onAuth, group, onBack, onRefreshGroups, onLeftGroup }) {
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
  }, [group.name]);

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

  const myMember = feed?.members?.find((member) => member.userId === auth.userId);
  const myDrawing = myMember?.drawing || null;
  const topic = feed?.dailyTopic;
  const hasFeedRecords = Boolean(feed?.members?.some((member) => member.drawing));
  const hasSelectableDates = selectableDates.length > 0;
  const showEmptyFeed = (recordDatesLoaded && !hasSelectableDates) || Boolean(feed && !hasFeedRecords);
  const emptyFeedMessage = hasSelectableDates ? '아직 아무도 그림을 올리지 않았어요.' : '아직 기록이 없어요. 오늘 첫 그림을 남겨보세요.';

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

  async function renameGroup(event) {
    event.preventDefault();
    setOwnerMessage('');
    try {
      await request(`/groups/${group.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: groupName }),
      }, auth, onAuth);
      setOwnerMessage('그룹 이름을 바꿨어요.');
      await onRefreshGroups();
    } catch (err) {
      setOwnerMessage(err.message);
    }
  }

  async function transferOwner(member) {
    const ok = window.confirm(`${member.nickname}님에게 방장을 위임할까요?`);
    if (!ok) return;
    await request(`/groups/${group.id}/transfer-owner`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ targetUserId: member.userId }),
    }, auth, onAuth);
    await refreshAll();
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

  async function deleteGroup() {
    const confirmName = window.prompt('그룹 삭제는 MVP에서 보류되어 있지만, 서버 정책 확인을 위해 그룹명을 입력해볼 수 있어요.');
    if (!confirmName) return;
    try {
      await request(`/groups/${group.id}`, {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ confirmName }),
      }, auth, onAuth);
      onLeftGroup();
    } catch (err) {
      setOwnerMessage(err.message);
    }
  }

  function renderGroupMenuContent() {
    const members = feed?.members || [];

    if (groupMenuView === 'invite') {
      return (
        <>
          <div className="group-menu-subhead">
            <button title="뒤로" onClick={() => openMenuView('main')}><ChevronLeft size={17} /></button>
            <strong>초대코드</strong>
          </div>
          <button className="invite-copy-card" onClick={() => copyText(inviteLink, '초대링크를 복사했어요')}>
            <span>초대링크</span>
            <strong>{inviteLink}</strong>
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
          <div className="group-menu-subhead">
            <button title="뒤로" onClick={() => openMenuView('main')}><ChevronLeft size={17} /></button>
            <strong>멤버</strong>
          </div>
          <div className="menu-member-list">
            {members.map((member) => {
              const owner = member.role === 'OWNER';
              return (
                <article key={member.userId}>
                  <div>
                    <strong>{member.nickname}</strong>
                    <span>{owner ? '방장' : '멤버'}</span>
                  </div>
                  {group.owner && !owner && (
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
          <div className="group-menu-subhead">
            <button title="뒤로" onClick={() => openMenuView('main')}><ChevronLeft size={17} /></button>
            <strong>설정</strong>
          </div>
          {group.owner && (
            <section className="menu-section">
              <strong>그룹 관리</strong>
              <form onSubmit={renameGroup} className="owner-name-row compact">
                <input value={groupName} onChange={(event) => setGroupName(event.target.value)} minLength={2} maxLength={80} required />
                <button type="submit">이름 변경</button>
              </form>
              <button type="button" className="danger wide-action" onClick={deleteGroup}>그룹 삭제</button>
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
        <button onClick={() => {
          setRoomView('feed');
          closeGroupMenu();
        }}>
          <BookOpen size={18} />
          <span>기록</span>
        </button>
        <button onClick={() => openMenuView('invite')}>
          <KeyRound size={18} />
          <span>초대코드</span>
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
                <div className="group-menu-card-head">
                  <strong>{group.name}</strong>
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
          <section className="room-dashboard">
            <div className={`activity-column ${feed?.feedLocked ? 'locked' : ''}`}>
              {showEmptyFeed ? (
                <section className="empty-feed-state">
                  <CalendarDays size={24} />
                  <strong>{emptyFeedMessage}</strong>
                </section>
              ) : (
                <>
                  {feed?.feedLocked && (
                    <div className="feed-lock">
                      <Lock size={22} />
                      <strong>오늘 피드는 내 그림을 올린 뒤 열려요.</strong>
                    </div>
                  )}
                  <section className="member-board">
                    {feed?.members?.map((member) => {
                      const owner = member.role === 'OWNER';
                      return (
                        <article
                          className={`member-tile ${member.drawing ? 'done' : ''} ${member.userId === auth.userId ? 'mine' : ''}`}
                          key={member.userId}
                        >
                          <button className="member-tile-main" onClick={() => openMember(member)}>
                            <span>{member.nickname}</span>
                            {owner && <strong className="owner-badge">방장</strong>}
                            {member.drawing ? (
                              <img src={drawingImageUrl(member.drawing)} alt={`${member.nickname} 미리보기`} />
                            ) : (
                              <small>{member.userId === auth.userId && isToday ? '그리기' : feed?.feedLocked ? '잠김' : '아직 없음'}</small>
                            )}
                            <em>{member.userId === auth.userId && isToday ? <Edit3 size={15} /> : member.drawing ? <Eye size={15} /> : null}</em>
                          </button>
                          {member.drawing && (
                            <button className="quote-button" onClick={() => {
                              setChatQuote(member.drawing);
                              setRoomView('chat');
                            }}><Quote size={14} /> 인용</button>
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
                      <img src={drawingImageUrl(myDrawing)} alt="내 그림 미리보기" />
                      <button onClick={() => {
                        setChatQuote(myDrawing);
                        setRoomView('chat');
                      }}><Quote size={15} /> 인용</button>
                      {isToday ? <button onClick={() => setEditorOpen(true)}>수정하기</button> : <button onClick={() => setViewerDrawing(myDrawing)}>크게보기</button>}
                    </section>
                  )}
                </>
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

export default function App() {
  const [auth, setAuthState] = useState(() => {
    const raw = localStorage.getItem('drawlog.auth');
    return raw ? JSON.parse(raw) : null;
  });
  const [groups, setGroups] = useState([]);
  const [selectedGroupId, setSelectedGroupId] = useState(() => localStorage.getItem('drawlog.groupId'));
  const [lobbyComposerOpen, setLobbyComposerOpen] = useState(() => Boolean(inviteFromUrl()));
  const [notificationsOpen, setNotificationsOpen] = useState(false);

  function setAuth(nextAuth) {
    setAuthState(nextAuth);
    if (nextAuth) {
      localStorage.setItem('drawlog.auth', JSON.stringify(nextAuth));
    } else {
      localStorage.removeItem('drawlog.auth');
    }
  }

  useEffect(() => {
    if (auth) return;
    request('/auth/refresh', { method: 'POST' })
      .then((nextAuth) => {
        if (nextAuth) setAuth(nextAuth);
      })
      .catch(() => {});
  }, []);

  async function loadGroups() {
    if (!auth?.token) return;
    const data = await request('/groups', {}, auth, setAuth);
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

  async function logout() {
    try {
      await request('/auth/logout', { method: 'POST' }, auth, setAuth);
    } finally {
      setAuth(null);
      setSelectedGroupId(null);
      localStorage.removeItem('drawlog.groupId');
    }
  }

  const selectedGroup = useMemo(
    () => groups.find((group) => String(group.id) === String(selectedGroupId)),
    [groups, selectedGroupId],
  );

  if (!auth) return <AuthView onAuth={setAuth} />;

  if (!selectedGroup) {
    return (
      <main className="app-shell lobby-shell">
        <header className="topbar">
          <div>
            <p className="brand-word">DRAWLOG</p>
          </div>
          <div className="lobby-actions">
            <button title="그룹 만들기" onClick={() => setLobbyComposerOpen(true)}><Plus size={28} /></button>
            <button title="알림" onClick={() => setNotificationsOpen(true)}><Bell size={25} /></button>
            <button title="로그아웃" onClick={logout}><LogOut size={22} /></button>
          </div>
        </header>
        <GroupLobby
          auth={auth}
          onAuth={setAuth}
          groups={groups}
          onRefresh={loadGroups}
          onSelect={selectGroup}
          composerOpen={lobbyComposerOpen}
          onCloseComposer={() => setLobbyComposerOpen(false)}
        />
        {notificationsOpen && <NotificationModal auth={auth} onAuth={setAuth} onClose={() => setNotificationsOpen(false)} />}
      </main>
    );
  }

  return (
    <GroupRoom
      auth={auth}
      onAuth={setAuth}
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
