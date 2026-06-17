const API_BASE = import.meta.env?.VITE_API_BASE || '/api';

async function parseResponse(response) {
  if (response.status === 204) return null;
  const raw = await response.text();
  return raw ? JSON.parse(raw) : null;
}

export async function request(path, options = {}, auth = null, onAuth = null) {
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
      if (!nextAuth?.token) {
        onAuth(null, { expired: true });
      } else {
        onAuth(nextAuth);
        response = await fetch(`${API_BASE}${path}`, buildOptions(nextAuth.token));
        if (response.status === 401) {
          onAuth(null, { expired: true });
        }
      }
    } else {
      onAuth(null, { expired: true });
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
