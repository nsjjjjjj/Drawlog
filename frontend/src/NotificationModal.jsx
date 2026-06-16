import { useEffect, useState } from 'react';
import { Bell, Check, X } from 'lucide-react';
import { request } from './apiClient.js';

export default function NotificationModal({ auth, onAuth, onClose }) {
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
