import { useEffect, useMemo, useState } from 'react';
import { Bell, Plus } from 'lucide-react';
import { request } from './apiClient.js';
import AuthView from './AuthView.jsx';
import GroupLobby from './Lobby.jsx';
import GroupRoom from './GroupRoom.jsx';
import InstallGuideModal from './InstallGuide.jsx';
import NotificationModal from './NotificationModal.jsx';
import ProfileMenu from './ProfileMenu.jsx';
import { INSTALL_GUIDE_DISMISSED_KEY, inviteFromUrl, isStandaloneMode } from './utils.js';

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
