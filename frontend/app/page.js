'use client';

import { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';

const API = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const TICKETS_KEY = 'pr_saved_tickets';

/* ---------- saved ticket helpers (localStorage) ---------- */
function readSavedTickets() {
  if (typeof window === 'undefined') return [];
  try {
    const list = JSON.parse(localStorage.getItem(TICKETS_KEY) || '[]');
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

function persistSavedTickets(list) {
  try {
    localStorage.setItem(TICKETS_KEY, JSON.stringify(list));
  } catch {
    /* storage disabled or full */
  }
}

function mergeTickets(primary, secondary) {
  const map = new Map();
  [...secondary, ...primary].forEach(t => {
    if (t && t.bookingCode) map.set(t.bookingCode, t);
  });
  return Array.from(map.values()).sort((a, b) =>
    String(b.createdAt || '').localeCompare(String(a.createdAt || ''))
  );
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function ticketQrUrl(ticket) {
  return `https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodeURIComponent(
    ticket.qrData || ticket.bookingCode || ''
  )}`;
}

/* Printable / downloadable ticket document */
function buildTicketHtml(ticket) {
  const rows = [
    ['Passenger', ticket.passengerName],
    ['CNIC', ticket.passengerCnic || '—'],
    ['Train', `${ticket.trainName} (${ticket.trainNumber})`],
    ['From', ticket.fromStation],
    ['To', ticket.toStation],
    ['Travel Date', ticket.travelDate],
    ['Coach / Class', `${ticket.coachCode} – ${ticket.classType}`],
    ['Seats', ticket.seatNumbers],
    ['Status', ticket.status],
  ]
    .map(([label, value]) => `<tr><td class="k">${escapeHtml(label)}</td><td class="v">${escapeHtml(value)}</td></tr>`)
    .join('');

  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>E-Ticket ${escapeHtml(ticket.bookingCode)} – Pakistan Railways</title>
<style>
  * { box-sizing: border-box; }
  body { margin: 0; padding: 32px 16px; font-family: 'Segoe UI', system-ui, sans-serif;
         background: #032015; color: #fff; display: grid; place-items: center; }
  .ticket { width: 100%; max-width: 520px; border-radius: 20px; overflow: hidden;
            background: linear-gradient(160deg, #0f5c45 0%, #0a3d2e 50%, #032015 100%);
            border: 1px solid #1a9973; box-shadow: 0 24px 60px rgba(0,0,0,.55); padding: 28px; }
  .head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px;
          padding-bottom: 16px; border-bottom: 1px dashed rgba(255,255,255,.25); }
  .brand { font-size: 12px; letter-spacing: 2px; color: rgba(255,255,255,.65); }
  .title { font-size: 22px; font-weight: 800; margin-top: 4px; }
  .code { font-family: monospace; font-size: 16px; color: #6ee7c5; letter-spacing: 1px; }
  table { width: 100%; border-collapse: collapse; margin-top: 16px; }
  td { padding: 7px 0; font-size: 14px; vertical-align: top; }
  td.k { color: rgba(255,255,255,.55); width: 42%; }
  td.v { font-weight: 600; text-align: right; }
  .fare { color: #6ee7c5; font-size: 18px; }
  .qr { text-align: center; margin-top: 20px; padding-top: 18px; border-top: 1px dashed rgba(255,255,255,.25); }
  .qr img { width: 180px; height: 180px; background: #fff; padding: 8px; border-radius: 12px; }
  .note { margin-top: 14px; font-size: 11px; color: rgba(255,255,255,.5); line-height: 1.6; text-align: center; }
  @media print {
    body { background: #fff; padding: 0; }
    .ticket { box-shadow: none; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  }
</style>
</head>
<body>
  <div class="ticket">
    <div class="head">
      <div>
        <div class="brand">PAKISTAN RAILWAYS</div>
        <div class="title">E-Ticket</div>
      </div>
      <div class="code">${escapeHtml(ticket.bookingCode)}</div>
    </div>
    <table>
      ${rows}
      <tr><td class="k">Fare Paid</td><td class="v fare">Rs ${Number(ticket.totalFare || 0).toLocaleString()}</td></tr>
    </table>
    <div class="qr">
      <img src="${ticketQrUrl(ticket)}" alt="Ticket QR code" />
      <div class="note">Scan at station · Carry a valid CNIC · Booking ${escapeHtml(ticket.bookingCode)}</div>
    </div>
    <div class="note">
      Ride the Rails of Pakistan – Safe, Swift, Seamless<br />
      Downloaded ${escapeHtml(new Date().toLocaleString())}
    </div>
  </div>
</body>
</html>`;
}

export default function HomePage() {
  const router = useRouter();
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [trains, setTrains] = useState([]);
  const [stations, setStations] = useState([]);
  const [loading, setLoading] = useState(true);

  // Booking state machine
  const [step, setStep] = useState(1); // 1=train 2=stations 3=seats 4=confirm 5=ticket
  const [selectedTrain, setSelectedTrain] = useState(null);
  const [fromStation, setFromStation] = useState('');
  const [toStation, setToStation] = useState('');
  const [selectedCoach, setSelectedCoach] = useState(null);
  const [travelDate, setTravelDate] = useState('');
  const [fareInfo, setFareInfo] = useState(null);
  const [bookedSeats, setBookedSeats] = useState([]);
  const [selectedSeats, setSelectedSeats] = useState([]);
  const [passengerName, setPassengerName] = useState('');
  const [passengerCnic, setPassengerCnic] = useState('');
  const [bookingResult, setBookingResult] = useState(null);
  const [savedTickets, setSavedTickets] = useState([]);
  const [toast, setToast] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Load auth + data
  useEffect(() => {
    const t = localStorage.getItem('pr_token');
    const u = localStorage.getItem('pr_user');
    if (t && u) {
      setToken(t);
      setUser(JSON.parse(u));
    }
    // default travel date = tomorrow
    const tmr = new Date();
    tmr.setDate(tmr.getDate() + 1);
    setTravelDate(tmr.toISOString().slice(0, 10));

    Promise.all([
      fetch(`${API}/api/trains`).then(r => r.json()),
      fetch(`${API}/api/stations`).then(r => r.json()),
    ])
      .then(([tr, st]) => {
        setTrains(tr);
        setStations(st);
      })
      .catch(() => setError('Backend not reachable. Start the Java server on port 8080.'))
      .finally(() => setLoading(false));
  }, []);

  /* ---------- saved tickets ---------- */
  useEffect(() => {
    setSavedTickets(readSavedTickets());
  }, []);

  // Merge server-side bookings (survives browser storage clearing while API is up)
  useEffect(() => {
    if (!token) return;
    fetch(`${API}/api/bookings`, { headers: { 'X-Token': token } })
      .then(r => (r.ok ? r.json() : []))
      .then(list => {
        if (!Array.isArray(list) || !list.length) return;
        const merged = mergeTickets(list, readSavedTickets());
        persistSavedTickets(merged);
        setSavedTickets(merged);
      })
      .catch(() => {});
  }, [token]);

  useEffect(() => {
    if (!toast) return;
    const id = setTimeout(() => setToast(''), 2800);
    return () => clearTimeout(id);
  }, [toast]);

  const saveTicket = useCallback((ticket, { silent = false } = {}) => {
    if (!ticket?.bookingCode) return;
    const merged = mergeTickets([ticket], readSavedTickets());
    persistSavedTickets(merged);
    setSavedTickets(merged);
    if (!silent) setToast('Ticket saved to My Tickets');
  }, []);

  const isSaved = (ticket) =>
    !!ticket && savedTickets.some(t => t.bookingCode === ticket.bookingCode);

  const downloadTicket = (ticket) => {
    if (!ticket?.bookingCode) return;
    const blob = new Blob([buildTicketHtml(ticket)], { type: 'text/html;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `PakistanRailways-Ticket-${ticket.bookingCode}.html`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    setToast('Ticket downloaded');
  };

  const viewTicket = (ticket) => {
    setBookingResult(ticket);
    setStep(5);
    setError('');
    requestAnimationFrame(() => {
      document.querySelector('.booking-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  };

  const logout = () => {
    if (token) fetch(`${API}/api/auth/logout`, { headers: { 'X-Token': token } });
    localStorage.removeItem('pr_token');
    localStorage.removeItem('pr_user');
    setToken(null);
    setUser(null);
  };

  // Live fare calculation
  const calcFare = useCallback(async () => {
    if (!selectedTrain || !fromStation || !toStation || !selectedCoach) {
      setFareInfo(null);
      return;
    }
    try {
      const res = await fetch(`${API}/api/fare`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          trainId: selectedTrain.id,
          fromStationId: Number(fromStation),
          toStationId: Number(toStation),
          coachId: selectedCoach.id,
          seatCount: Math.max(1, selectedSeats.length || 1),
        }),
      });
      const data = await res.json();
      if (data.success) setFareInfo(data);
      else setFareInfo(null);
    } catch {
      setFareInfo(null);
    }
  }, [selectedTrain, fromStation, toStation, selectedCoach, selectedSeats.length]);

  useEffect(() => {
    calcFare();
  }, [calcFare]);

  // Load booked seats when coach / date changes
  useEffect(() => {
    if (!selectedCoach || !travelDate) return;
    fetch(`${API}/api/seats?coachId=${selectedCoach.id}&date=${travelDate}`)
      .then(r => r.json())
      .then(d => setBookedSeats(d.booked || []))
      .catch(() => setBookedSeats([]));
  }, [selectedCoach, travelDate]);

  const selectTrain = (t) => {
    setSelectedTrain(t);
    setSelectedCoach(t.coaches?.[0] || null);
    setSelectedSeats([]);
    setStep(2);
  };

  const toggleSeat = (num) => {
    if (bookedSeats.includes(num)) return;
    setSelectedSeats(prev =>
      prev.includes(num) ? prev.filter(s => s !== num) : [...prev, num].sort((a, b) => a - b)
    );
  };

  const goToConfirm = () => {
    if (!selectedSeats.length) {
      setError('Please select at least one seat');
      return;
    }
    if (!passengerName.trim()) {
      setError('Passenger name is required');
      return;
    }
    setError('');
    setStep(4);
  };

  const confirmBooking = async () => {
    if (!token) {
      setError('Please login to complete booking');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      const res = await fetch(`${API}/api/bookings`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Token': token,
        },
        body: JSON.stringify({
          trainId: selectedTrain.id,
          fromStationId: Number(fromStation),
          toStationId: Number(toStation),
          coachId: selectedCoach.id,
          seatNumbers: selectedSeats.join(','),
          passengerName: passengerName.trim(),
          passengerCnic: passengerCnic.trim(),
          travelDate,
        }),
      });
      const data = await res.json();
      if (data.bookingCode || data.id) {
        setBookingResult(data);
        setStep(5);
        saveTicket(data, { silent: true });
        setToast('Booking confirmed — ticket saved to My Tickets');
      } else {
        setError(data.message || 'Booking failed');
      }
    } catch (e) {
      setError('Network error: ' + e.message);
    } finally {
      setSubmitting(false);
    }
  };

  const resetBooking = () => {
    setStep(1);
    setSelectedTrain(null);
    setFromStation('');
    setToStation('');
    setSelectedCoach(null);
    setSelectedSeats([]);
    setPassengerName('');
    setPassengerCnic('');
    setFareInfo(null);
    setBookingResult(null);
    setError('');
  };

  const stationName = (id) => stations.find(s => s.id === Number(id))?.name || '';

  // Saved tickets belong to the logged-in user (server bookings carry userId)
  const myTickets = user
    ? savedTickets.filter(t => t.userId == null || t.userId === user.id)
    : [];

  return (
    <>
      {/* NAVBAR */}
      <nav className="navbar">
        <div className="logo">
          <div className="logo-icon">🚂</div>
          <span>Pakistan Railways</span>
        </div>
        <div className="nav-links">
          {user ? (
            <>
              <a href="#my-tickets" className="nav-ticket-link">
                🎟️ My Tickets{myTickets.length ? ` (${myTickets.length})` : ''}
              </a>
              <span style={{ color: 'rgba(255,255,255,0.7)', fontSize: '0.9rem' }}>
                Hi, {user.fullName?.split(' ')[0]}
              </span>
              <button className="btn btn-ghost" onClick={logout}>Logout</button>
            </>
          ) : (
            <Link href="/login" className="btn btn-primary">Login / Sign Up</Link>
          )}
        </div>
      </nav>

      {/* HERO / DASHBOARD */}
      <section className="hero">
        <div className="hero-media" aria-hidden="true" />
        <div className="hero-content">
          <p className="slogan">CONNECTING PAKISTAN</p>
          <h1>Welcome to Pakistan Railways</h1>
          <p className="hero-sub">
            Book your journey across the nation with real distance-based fares,
            live seat selection and instant digital tickets — all in one modern experience.
          </p>
        </div>
      </section>

      <div className="stats-row">
        <div className="card-3d stat-card">
          <span className="num">6</span>
          <span className="label">Premium Trains</span>
        </div>
        <div className="card-3d stat-card">
          <span className="num">7</span>
          <span className="label">Major Stations</span>
        </div>
        <div className="card-3d stat-card">
          <span className="num">3</span>
          <span className="label">Coaches / Train</span>
        </div>
        <div className="card-3d stat-card">
          <span className="num">24/7</span>
          <span className="label">Online Booking</span>
        </div>
      </div>

      {/* BOOKING SECTION */}
      <section className="booking-section">
        <h2 className="section-title">
          <span>🎟️</span> Book Your Ticket
        </h2>

        {/* Steps indicator */}
        <div className="steps">
          {['Select Train', 'Stations & Fare', 'Choose Seats', 'Confirm', 'Ticket'].map((label, i) => (
            <div
              key={label}
              className={`step-pill ${step === i + 1 ? 'active' : ''} ${step > i + 1 ? 'done' : ''}`}
            >
              {i + 1}. {label}
            </div>
          ))}
        </div>

        {error && <div className="error-msg">{error}</div>}

        {loading ? (
          <div className="text-center" style={{ padding: '3rem' }}>
            <div className="spinner" style={{ margin: '0 auto' }} />
            <p className="mt-2" style={{ color: 'rgba(255,255,255,0.6)' }}>Loading trains & stations…</p>
          </div>
        ) : (
          <>
            {/* STEP 1 – Train Selection */}
            {step === 1 && (
              <div className="train-grid">
                {trains.map(t => (
                  <div
                    key={t.id}
                    className={`card-3d train-card ${selectedTrain?.id === t.id ? 'selected' : ''}`}
                    onClick={() => selectTrain(t)}
                  >
                    <span className="badge">{t.number}</span>
                    <h3 style={{ fontSize: '1.2rem', marginBottom: '0.4rem' }}>{t.name}</h3>
                    <p style={{ color: 'rgba(255,255,255,0.65)', fontSize: '0.9rem', marginBottom: '0.8rem' }}>
                      {t.description}
                    </p>
                    <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                      {t.coaches?.map(c => (
                        <span
                          key={c.id}
                          style={{
                            fontSize: '0.75rem',
                            padding: '0.2rem 0.5rem',
                            borderRadius: '6px',
                            background: 'rgba(45,212,168,0.15)',
                            color: 'var(--green-300)',
                          }}
                        >
                          {c.classType}
                        </span>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* STEP 2 – Stations + Coach + Live Fare */}
            {step === 2 && selectedTrain && (
              <div className={`card-3d ${!user ? 'lock-overlay' : ''}`}>
                <h3 style={{ marginBottom: '1.25rem' }}>
                  {selectedTrain.name} <small style={{ color: 'var(--green-300)' }}>({selectedTrain.number})</small>
                </h3>

                <div className="form-row">
                  <div className="form-group">
                    <label>From Station</label>
                    <select value={fromStation} onChange={e => setFromStation(e.target.value)}>
                      <option value="">Select departure</option>
                      {stations.map(s => (
                        <option key={s.id} value={s.id}>{s.name} ({s.code})</option>
                      ))}
                    </select>
                  </div>
                  <div className="form-group">
                    <label>To Station</label>
                    <select value={toStation} onChange={e => setToStation(e.target.value)}>
                      <option value="">Select arrival</option>
                      {stations.map(s => (
                        <option key={s.id} value={s.id}>{s.name} ({s.code})</option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="form-group">
                  <label>Travel Date</label>
                  <input
                    type="date"
                    value={travelDate}
                    min={new Date().toISOString().slice(0, 10)}
                    onChange={e => setTravelDate(e.target.value)}
                  />
                </div>

                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, color: 'var(--green-300)', marginBottom: '0.5rem' }}>
                  Select Coach / Class
                </label>
                <div className="coach-tabs">
                  {selectedTrain.coaches?.map(c => (
                    <div
                      key={c.id}
                      className={`coach-tab ${selectedCoach?.id === c.id ? 'active' : ''}`}
                      onClick={() => { setSelectedCoach(c); setSelectedSeats([]); }}
                    >
                      Coach {c.coachCode}
                      <small>{c.classType} · {c.totalSeats} seats</small>
                    </div>
                  ))}
                </div>

                {fareInfo && (
                  <div className="fare-box">
                    <div>
                      <div className="meta">
                        {fareInfo.distanceKm} km · {fareInfo.classType} · {selectedSeats.length || 1} seat(s)
                      </div>
                      <div className="amount">Rs {Number(fareInfo.totalFare).toLocaleString()}</div>
                    </div>
                    <div className="meta">
                      Rs {Number(fareInfo.farePerSeat).toLocaleString()} / seat
                    </div>
                  </div>
                )}

                <div className="flex gap-2 mt-2">
                  <button className="btn btn-ghost" onClick={() => setStep(1)}>← Back</button>
                  <button
                    className="btn btn-primary"
                    disabled={!fromStation || !toStation || !selectedCoach || fromStation === toStation}
                    onClick={() => { setError(''); setStep(3); }}
                  >
                    Next: Choose Seats →
                  </button>
                </div>
              </div>
            )}

            {/* STEP 3 – Seat Selection */}
            {step === 3 && selectedCoach && (
              <div className={`card-3d ${!user ? 'lock-overlay' : ''}`}>
                <h3 style={{ marginBottom: '0.5rem' }}>
                  Coach {selectedCoach.coachCode} – {selectedCoach.classType}
                </h3>
                <p style={{ color: 'rgba(255,255,255,0.6)', marginBottom: '1rem', fontSize: '0.9rem' }}>
                  {stationName(fromStation)} → {stationName(toStation)} · {travelDate}
                </p>

                <div className="seat-map">
                  {Array.from({ length: selectedCoach.totalSeats }, (_, i) => i + 1).map(num => (
                    <div
                      key={num}
                      className={`seat ${bookedSeats.includes(num) ? 'booked' : ''} ${selectedSeats.includes(num) ? 'selected' : ''}`}
                      onClick={() => toggleSeat(num)}
                    >
                      {num}
                    </div>
                  ))}
                </div>

                <p style={{ fontSize: '0.85rem', color: 'rgba(255,255,255,0.55)', marginBottom: '1rem' }}>
                  Selected: {selectedSeats.length ? selectedSeats.join(', ') : 'None'}
                </p>

                {fareInfo && (
                  <div className="fare-box">
                    <div className="amount">Rs {Number(fareInfo.totalFare).toLocaleString()}</div>
                    <div className="meta">{selectedSeats.length || 1} × Rs {Number(fareInfo.farePerSeat).toLocaleString()}</div>
                  </div>
                )}

                <div className="form-row">
                  <div className="form-group">
                    <label>Passenger Full Name *</label>
                    <input
                      value={passengerName}
                      onChange={e => setPassengerName(e.target.value)}
                      placeholder="As per CNIC"
                    />
                  </div>
                  <div className="form-group">
                    <label>CNIC (optional)</label>
                    <input
                      value={passengerCnic}
                      onChange={e => setPassengerCnic(e.target.value)}
                      placeholder="XXXXX-XXXXXXX-X"
                    />
                  </div>
                </div>

                <div className="flex gap-2">
                  <button className="btn btn-ghost" onClick={() => setStep(2)}>← Back</button>
                  <button className="btn btn-primary" onClick={goToConfirm}>
                    Review Booking →
                  </button>
                </div>
              </div>
            )}

            {/* STEP 4 – Confirm */}
            {step === 4 && (
              <div className="card-3d">
                <h3 style={{ marginBottom: '1.25rem' }}>Confirm Your Booking</h3>
                <div className="ticket-row"><span className="label">Train</span><span className="value">{selectedTrain.name} ({selectedTrain.number})</span></div>
                <div className="ticket-row"><span className="label">Route</span><span className="value">{stationName(fromStation)} → {stationName(toStation)}</span></div>
                <div className="ticket-row"><span className="label">Date</span><span className="value">{travelDate}</span></div>
                <div className="ticket-row"><span className="label">Coach / Class</span><span className="value">{selectedCoach.coachCode} – {selectedCoach.classType}</span></div>
                <div className="ticket-row"><span className="label">Seats</span><span className="value">{selectedSeats.join(', ')}</span></div>
                <div className="ticket-row"><span className="label">Passenger</span><span className="value">{passengerName}</span></div>
                <div className="ticket-row"><span className="label">Total Fare</span><span className="value" style={{ color: 'var(--green-300)', fontSize: '1.2rem' }}>Rs {Number(fareInfo?.totalFare || 0).toLocaleString()}</span></div>

                <div className="flex gap-2 mt-2">
                  <button className="btn btn-ghost" onClick={() => setStep(3)}>← Back</button>
                  <button className="btn btn-primary" disabled={submitting} onClick={confirmBooking}>
                    {submitting ? 'Booking…' : 'Confirm & Generate Ticket'}
                  </button>
                </div>
              </div>
            )}

            {/* STEP 5 – Ticket + QR */}
            {step === 5 && bookingResult && (
              <div className="ticket">
                <div className="ticket-header">
                  <div>
                    <div style={{ fontSize: '0.8rem', color: 'rgba(255,255,255,0.6)' }}>PAKISTAN RAILWAYS</div>
                    <div style={{ fontWeight: 700, fontSize: '1.3rem' }}>E-Ticket</div>
                  </div>
                  <div className="ticket-code">{bookingResult.bookingCode}</div>
                </div>

                <div className="ticket-row"><span className="label">Passenger</span><span className="value">{bookingResult.passengerName}</span></div>
                <div className="ticket-row"><span className="label">Train</span><span className="value">{bookingResult.trainName} ({bookingResult.trainNumber})</span></div>
                <div className="ticket-row"><span className="label">From</span><span className="value">{bookingResult.fromStation}</span></div>
                <div className="ticket-row"><span className="label">To</span><span className="value">{bookingResult.toStation}</span></div>
                <div className="ticket-row"><span className="label">Date</span><span className="value">{bookingResult.travelDate}</span></div>
                <div className="ticket-row"><span className="label">Coach / Class</span><span className="value">{bookingResult.coachCode} – {bookingResult.classType}</span></div>
                <div className="ticket-row"><span className="label">Seats</span><span className="value">{bookingResult.seatNumbers}</span></div>
                <div className="ticket-row"><span className="label">Fare Paid</span><span className="value" style={{ color: 'var(--green-300)' }}>Rs {Number(bookingResult.totalFare).toLocaleString()}</span></div>
                <div className="ticket-row"><span className="label">Status</span><span className="value" style={{ color: '#6ee7c5' }}>{bookingResult.status}</span></div>

                <div className="qr-wrap">
                  <img
                    src={`https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encodeURIComponent(bookingResult.qrData || bookingResult.bookingCode)}`}
                    alt="Ticket QR Code"
                    width={180}
                    height={180}
                  />
                  <p style={{ marginTop: '0.75rem', fontSize: '0.8rem', color: 'rgba(255,255,255,0.55)' }}>
                    Scan at station / keep this digital ticket
                  </p>
                </div>

                <div className="ticket-actions">
                  <button className="btn btn-outline" onClick={() => downloadTicket(bookingResult)}>
                    ⬇️ Download Ticket
                  </button>
                  <button
                    className="btn btn-ghost"
                    onClick={() => saveTicket(bookingResult)}
                    disabled={isSaved(bookingResult)}
                  >
                    {isSaved(bookingResult) ? '✓ Saved' : '💾 Save Ticket'}
                  </button>
                  <button className="btn btn-primary" onClick={resetBooking}>Book Another Ticket</button>
                </div>
              </div>
            )}
          </>
        )}
      </section>

      {/* MY SAVED TICKETS */}
      {user && (
        <section className="booking-section" id="my-tickets">
          <h2 className="section-title">
            <span>🗂️</span> My Saved Tickets
            {myTickets.length > 0 && <span className="count-badge">{myTickets.length}</span>}
          </h2>

          {myTickets.length === 0 ? (
            <div className="card-3d empty-tickets">
              <div style={{ fontSize: '2rem' }}>🎫</div>
              <p>No saved tickets yet.</p>
              <p style={{ color: 'rgba(255,255,255,0.55)', fontSize: '0.9rem' }}>
                Confirm a booking and your ticket will be saved here for download anytime.
              </p>
            </div>
          ) : (
            <div className="saved-grid">
              {myTickets.map(t => (
                <div key={t.bookingCode} className="card-3d saved-card">
                  <div className="saved-top">
                    <div>
                      <div className="saved-code">{t.bookingCode}</div>
                      <div className="saved-train">{t.trainName} <small>({t.trainNumber})</small></div>
                    </div>
                    <span className={`status-chip ${String(t.status).toLowerCase() === 'confirmed' ? 'ok' : ''}`}>
                      {t.status}
                    </span>
                  </div>

                  <div className="saved-route">{t.fromStation} → {t.toStation}</div>
                  <div className="saved-meta">
                    {t.travelDate} · Coach {t.coachCode} ({t.classType}) · Seats {t.seatNumbers}
                  </div>

                  <div className="saved-footer">
                    <span className="saved-fare">Rs {Number(t.totalFare).toLocaleString()}</span>
                    <div className="flex gap-2">
                      <button className="btn btn-ghost" onClick={() => viewTicket(t)}>View</button>
                      <button className="btn btn-outline" onClick={() => downloadTicket(t)}>⬇ Download</button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      <footer className="footer">
        © 2026 Pakistan Railways Booking Demo · Built with Java OOP + Next.js · For educational purposes
      </footer>

      {toast && <div className="toast">{toast}</div>}
    </>
  );
}