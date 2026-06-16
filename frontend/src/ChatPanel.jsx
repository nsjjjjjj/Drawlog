import { useEffect, useRef, useState } from 'react';
import { Quote, Trash2, X } from 'lucide-react';
import { request } from './apiClient.js';
import { messagePreview, quoteImageUrl } from './utils.js';

export default function ChatPanel({ auth, onAuth, groupId, quoteTarget, onClearQuote }) {
  const [messages, setMessages] = useState([]);
  const [content, setContent] = useState('');
  const [replyTarget, setReplyTarget] = useState(null);
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

  useEffect(() => {
    if (quoteTarget) setReplyTarget(null);
  }, [quoteTarget?.id]);

  function quoteMessage(message) {
    setReplyTarget({
      id: message.id,
      username: message.username,
      content: message.content,
    });
    onClearQuote();
  }

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
          replyToMessageId: replyTarget?.id || null,
        }),
      }, auth, onAuth);
      setContent('');
      onClearQuote();
      setReplyTarget(null);
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
          const replied = Boolean(message.replyTo);
          const bubbleClass = ['chat-bubble'];
          if (quoted) bubbleClass.push('with-quote');
          if (replied) bubbleClass.push('with-reply');
          if (!quoted && !replied) bubbleClass.push('text-only');
          return (
            <div className={`chat-row ${mine ? 'mine' : 'theirs'}`} key={message.id}>
              {!mine && (
                <span className="chat-avatar">
                  {message.profileImageUrl ? (
                    <img src={message.profileImageUrl} alt={`${message.username} 프로필`} loading="lazy" decoding="async" />
                  ) : (
                    (message.username || '?').slice(0, 1)
                  )}
                </span>
              )}
              <div className="chat-message-stack">
                {!mine && <span className="chat-sender-name">{message.username}</span>}
                <div className={bubbleClass.join(' ')}>
                  {message.quote && (
                    <div className="chat-quote-preview">
                      <img src={quoteImageUrl(message.quote)} alt={`${message.quote.username} 그림`} loading="lazy" decoding="async" />
                      <div>
                        <span>{message.quote.username}의 그림</span>
                        <p>{message.quote.topicText}</p>
                      </div>
                    </div>
                  )}
                  {message.replyTo && (
                    <div className="chat-reply-preview">
                      <span>{message.replyTo.username}</span>
                      <p>{messagePreview(message.replyTo.content)}</p>
                    </div>
                  )}
                  <p>{message.content}</p>
                </div>
                <div className="chat-meta">
                  <span>{new Date(message.createdAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}</span>
                  {!message.deletedAt && <button onClick={() => quoteMessage(message)}><Quote size={14} /> 인용</button>}
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
      {replyTarget && (
        <div className="composer-context reply-context">
          <Quote size={16} />
          <div>
            <strong>{replyTarget.username}</strong>
            <span>{messagePreview(replyTarget.content)}</span>
          </div>
          <button title="취소" onClick={() => setReplyTarget(null)}><X size={15} /></button>
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
