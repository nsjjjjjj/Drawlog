import { useState } from 'react';
import { request } from './apiClient.js';

export default function AuthView({ onAuth, notice }) {
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
          {notice && <p className="notice">{notice}</p>}
          {error && <p className="error">{error}</p>}
          <button className="primary" type="submit">{mode === 'login' ? '로그인' : '가입하고 시작'}</button>
        </form>
      </section>
    </main>
  );
}
