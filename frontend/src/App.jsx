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
  const [authNotice, setAuthNotice] = useState('');
  const [installGuideOpen, setInstallGuideOpen] = useState(false);
  const [autoInstallGuideShown, setAutoInstallGuideShown] = useState(false);
  const [standaloneMode, setStandaloneMode] = useState(() => isStandaloneMode());
  const [deferredInstallPrompt, setDeferredInstallPrompt] = useState(null);

  useEffect(() => {
    // Drop stale mobile PWA caches from previous local installs.
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.getRegistrations()
        .then((registrations) => registrations.forEach((registration) => registration.unregister()))
        .catch(() => {});
    }
    if ('caches' in window) {
      caches.keys()
        .then((keys) => keys.forEach((key) => caches.delete(key)))
        .catch(() => {});
    }
  }, []);

  useEffect(() => {
    const updateStandaloneMode = () => setStandaloneMode(isStandaloneMode());
    const media = window.matchMedia?.('(display-mode: standalone)');
    updateStandaloneMode();
    media?.addEventListener?.('change', updateStandaloneMode);
    media?.addListener?.(updateStandaloneMode);
    return () => {
      media?.removeEventListener?.('change', updateStandaloneMode);
      media?.removeListener?.(updateStandaloneMode);
    };
  }, []);

  useEffect(() => {
    function handleBeforeInstallPrompt(event) {
      event.preventDefault();
      setDeferredInstallPrompt(event);
    }

    function handleAppInstalled() {
      setDeferredInstallPrompt(null);
      setInstallGuideOpen(false);
      setStandaloneMode(isStandaloneMode());
    }

    window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
    window.addEventListener('appinstalled', handleAppInstalled);
    return () => {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
      window.removeEventListener('appinstalled', handleAppInstalled);
    };
  }, []);

  useEffect(() => {
    if (!auth) {
      setAutoInstallGuideShown(false);
      setInstallGuideOpen(false);
    }
  }, [auth]);

  useEffect(() => {
    if (!auth || standaloneMode || autoInstallGuideShown) return;
    if (localStorage.getItem(INSTALL_GUIDE_DISMISSED_KEY) === 'true') return;
    const timer = window.setTimeout(() => {
      setAutoInstallGuideShown(true);
      setInstallGuideOpen(true);
    }, selectedGroupId ? 600 : 900);
    return () => window.clearTimeout(timer);
  }, [auth, selectedGroupId, standaloneMode, autoInstallGuideShown]);

  function setAuth(nextAuth, options = {}) {
    setAuthState(nextAuth);
    if (nextAuth) {
      localStorage.setItem('drawlog.auth', JSON.stringify(nextAuth));
      setAuthNotice('');
    } else {
      localStorage.removeItem('drawlog.auth');
      setAuthNotice(options.expired ? '로그인이 만료되었습니다. 다시 로그인해주세요.' : '');
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
      clearSession();
    }
  }

  function clearSession() {
    setAuth(null);
    setSelectedGroupId(null);
    localStorage.removeItem('drawlog.groupId');
  }

  function closeInstallGuide() {
    setInstallGuideOpen(false);
  }

  function dismissInstallGuide() {
    localStorage.setItem(INSTALL_GUIDE_DISMISSED_KEY, 'true');
    setInstallGuideOpen(false);
  }

  function openInstallGuide() {
    setInstallGuideOpen(true);
  }

  async function installPwa() {
    const promptEvent = deferredInstallPrompt;
    if (!promptEvent?.prompt) return;
    try {
      await promptEvent.prompt();
      await promptEvent.userChoice;
    } catch {
      // Browser install prompts can fail silently when unavailable.
    } finally {
      setDeferredInstallPrompt(null);
      setInstallGuideOpen(false);
    }
  }

  const selectedGroup = useMemo(
    () => groups.find((group) => String(group.id) === String(selectedGroupId)),
    [groups, selectedGroupId],
  );
  const installGuideModal = installGuideOpen ? (
    <InstallGuideModal
      hasPrompt={Boolean(deferredInstallPrompt)}
      onInstall={installPwa}
      onClose={closeInstallGuide}
      onDismiss={dismissInstallGuide}
    />
  ) : null;

  if (!auth) return <AuthView onAuth={setAuth} notice={authNotice} />;

  if (!selectedGroup) {
    return (
      <>
        <main className="app-shell lobby-shell">
          <header className="topbar">
            <div>
              <p className="brand-word">DRAWLOG</p>
            </div>
            <div className="lobby-actions">
              <button title="그룹 만들기" onClick={() => setLobbyComposerOpen(true)}><Plus size={28} /></button>
              <button title="알림" onClick={() => setNotificationsOpen(true)}><Bell size={25} /></button>
              <ProfileMenu
                auth={auth}
                onAuth={setAuth}
                onLogout={logout}
                onProfileUpdated={loadGroups}
                onAccountDeleted={clearSession}
                onOpenInstallGuide={openInstallGuide}
                standaloneMode={standaloneMode}
              />
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
        {installGuideModal}
      </>
    );
  }

  return (
    <>
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
      {installGuideModal}
    </>
  );
}
