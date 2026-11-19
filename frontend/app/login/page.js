'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';

const API = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export default function LoginPage() {
  const router = useRouter();
  const [tab, setTab] = useState('login'); // login | signup | forgot
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Login
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  // Signup
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [signupEmail, setSignupEmail] = useState('');
  const [signupPass, setSignupPass] = useState('');
  const [confirmPass, setConfirmPass] = useState('');

  // Forgot
  const [forgotEmail, setForgotEmail] = useState('');

  const handleLogin = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setLoading(true);
    try {
      const res = await fetch(`${API}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });
      const data = await res.json();
      if (data.success && data.token) {
        localStorage.setItem('pr_token', data.token);
        localStorage.setItem('pr_user', JSON.stringify(data.user));
        setSuccess('Login successful! Redirecting…');
        setTimeout(() => router.push('/'), 800);
      } else {
        setError(data.message || 'Login failed');
      }
    } catch (err) {
      setError('Cannot reach server. Is the Java backend running on port 8080?');
    } finally {
      setLoading(false);
    }
  };

  const handleSignup = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    if (signupPass !== confirmPass) {
      setError('Passwords do not match');
      return;
    }
    if (signupPass.length < 6) {
      setError('Password must be at least 6 characters');
      return;
    }
    setLoading(true);
    try {
      const res = await fetch(`${API}/api/auth/signup`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          fullName,
          email: signupEmail,
          password: signupPass,
          phone,
        }),
      });
      const data = await res.json();
      if (data.success && data.token) {
        localStorage.setItem('pr_token', data.token);
        localStorage.setItem('pr_user', JSON.stringify(data.user));
        setSuccess('Account created! Redirecting…');
        setTimeout(() => router.push('/'), 800);
      } else {
        setError(data.message || 'Signup failed');
      }
    } catch (err) {
      setError('Cannot reach server. Is the Java backend running?');
    } finally {
      setLoading(false);
    }
  };

  const handleForgot = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setLoading(true);
    try {
      const res = await fetch(`${API}/api/auth/forgot`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: forgotEmail }),
      });
      const data = await res.json();
      if (data.success) {
        setSuccess(data.message);
      } else {
        setError(data.message || 'Request failed');
      }
    } catch {
      setError('Cannot reach server.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <nav className="navbar">
        <Link href="/" className="logo">
          <div className="logo-icon">🚂</div>
          <span>Pakistan Railways</span>
        </Link>
        <Link href="/" className="btn btn-ghost">← Back to Dashboard</Link>
      </nav>

      <div className="auth-page">
        <div className="card-3d auth-card">
          <h2>
            {tab === 'login' && 'Welcome Back'}
            {tab === 'signup' && 'Create Account'}
            {tab === 'forgot' && 'Reset Password'}
          </h2>
          <p className="sub">
            {tab === 'login' && 'Sign in to book tickets and manage journeys'}
            {tab === 'signup' && 'Join Pakistan Railways digital booking'}
            {tab === 'forgot' && 'We will send a reset link (demo mode)'}
          </p>

          {tab !== 'forgot' && (
            <div className="auth-tabs">
              <button
                className={`auth-tab ${tab === 'login' ? 'active' : ''}`}
                onClick={() => { setTab('login'); setError(''); setSuccess(''); }}
              >
                Sign In
              </button>
              <button
                className={`auth-tab ${tab === 'signup' ? 'active' : ''}`}
                onClick={() => { setTab('signup'); setError(''); setSuccess(''); }}
              >
                Sign Up
              </button>
            </div>
          )}

          {error && <div className="error-msg">{error}</div>}
          {success && <div className="success-msg">{success}</div>}

          {/* LOGIN FORM */}
          {tab === 'login' && (
            <form onSubmit={handleLogin}>
              <div className="form-group">
                <label>Email</label>
                <input
                  type="email"
                  required
                  value={email}
                  onChange={e => setEmail(e.target.value)}
                  placeholder="you@example.com"
                />
              </div>
              <div className="form-group">
                <label>Password</label>
                <input
                  type="password"
                  required
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  placeholder="••••••••"
                />
              </div>
              <button type="submit" className="btn btn-primary" style={{ width: '100%' }} disabled={loading}>
                {loading ? 'Signing in…' : 'Sign In'}
              </button>
              <p className="text-center mt-2" style={{ fontSize: '0.9rem' }}>
                <button
                  type="button"
                  style={{ background: 'none', border: 'none', color: 'var(--green-300)', cursor: 'pointer' }}
                  onClick={() => { setTab('forgot'); setError(''); setSuccess(''); }}
                >
                  Forgot password?
                </button>
              </p>
            </form>
          )}

          {/* SIGNUP FORM */}
          {tab === 'signup' && (
            <form onSubmit={handleSignup}>
              <div className="form-group">
                <label>Full Name *</label>
                <input
                  required
                  value={fullName}
                  onChange={e => setFullName(e.target.value)}
                  placeholder="As per CNIC"
                />
              </div>
              <div className="form-group">
                <label>Email *</label>
                <input
                  type="email"
                  required
                  value={signupEmail}
                  onChange={e => setSignupEmail(e.target.value)}
                  placeholder="you@example.com"
                />
              </div>
              <div className="form-group">
                <label>Phone</label>
                <input
                  value={phone}
                  onChange={e => setPhone(e.target.value)}
                  placeholder="03XX-XXXXXXX"
                />
              </div>
              <div className="form-group">
                <label>Password * (min 6)</label>
                <input
                  type="password"
                  required
                  value={signupPass}
                  onChange={e => setSignupPass(e.target.value)}
                  placeholder="••••••••"
                />
              </div>
              <div className="form-group">
                <label>Confirm Password *</label>
                <input
                  type="password"
                  required
                  value={confirmPass}
                  onChange={e => setConfirmPass(e.target.value)}
                  placeholder="••••••••"
                />
              </div>
              <button type="submit" className="btn btn-primary" style={{ width: '100%' }} disabled={loading}>
                {loading ? 'Creating…' : 'Create Account'}
              </button>
            </form>
          )}

          {/* FORGOT FORM */}
          {tab === 'forgot' && (
            <form onSubmit={handleForgot}>
              <div className="form-group">
                <label>Registered Email</label>
                <input
                  type="email"
                  required
                  value={forgotEmail}
                  onChange={e => setForgotEmail(e.target.value)}
                  placeholder="you@example.com"
                />
              </div>
              <button type="submit" className="btn btn-primary" style={{ width: '100%' }} disabled={loading}>
                {loading ? 'Sending…' : 'Send Reset Link'}
              </button>
              <p className="text-center mt-2">
                <button
                  type="button"
                  style={{ background: 'none', border: 'none', color: 'var(--green-300)', cursor: 'pointer' }}
                  onClick={() => { setTab('login'); setError(''); setSuccess(''); }}
                >
                  ← Back to Sign In
                </button>
              </p>
            </form>
          )}
        </div>
      </div>
    </>
  );
}