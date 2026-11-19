# 🇵🇰 Pakistan Railways – Online Ticket Booking System

Full-stack demo built with **complete Java OOP** (3 files) + **Next.js** (4 source files)  
Theme: **Dark Green + White Gradient** with effective 3D / glass UI.

---

## Features

### Backend (Java – pure OOP)
- 3 Java source files only
- Full OOP models (User, Train, Station, Coach, Booking…)
- SQL database (H2 in-memory for zero-config demo)
- Distance-based dynamic fare calculation (different for every train + coach class)
- Exactly **3 coaches** per train (Economy / AC Standard / AC Business)
- Seat availability tracking
- Authentication (signup, login, forgot password)
- Booking + QR data generation
- Pure Java `HttpServer` (no Spring/frameworks)

### Frontend (Next.js – only 4 source files)
1. `app/layout.js`
2. `app/page.js` (Dashboard + complete booking flow)
3. `app/login/page.js` (Login / Signup / Forgot)
4. `app/globals.css` (Dark green gradient + 3D styles)

- Public can **view** trains & dashboard
- **Login compulsory** to book seats / confirm ticket
- Train selection → Station selection → Live fare update → Seat map → Confirm → Digital ticket + QR
- Beautiful 3D cards, glassmorphism, step indicators

### Data (Pakistan Railways inspired)
**6 Trains**
- Green Line Express (5UP)
- Tezgam (7UP)
- Khyber Mail (1UP)
- Karachi Express (15UP)
- Awam Express (13UP)
- Fareed Express (37UP)

**7 Major Stations** (with realistic distances from Karachi)
Karachi Cantt → Hyderabad → Rohri → Multan → Lahore → Rawalpindi → Peshawar

**Fares** = `baseRatePerKm × distance × coachMultiplier` (rounded to realistic PKR)

---

## Project Structure

```
pakistan-railways/
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/pakrail/
│       ├── Models.java              ← All domain entities (OOP)
│       ├── DatabaseAndServices.java ← DB + Auth + Fare + Booking services
│       └── RailwayServer.java       ← REST API (HttpServer)
├── frontend/
│   ├── package.json
│   ├── next.config.js
│   └── app/
│       ├── layout.js
│       ├── page.js                  ← Dashboard + Booking
│       ├── login/page.js            ← Auth pages
│       └── globals.css              ← Theme
├── sql/
│   └── schema.sql                   ← Reference schema + seed
└── README.md
```

---

## How to Run

### 1. Backend (Java 17+)

```bash
cd backend
mvn clean package -DskipTests
java -jar target/pakistan-railways-api-1.0.0.jar
```

API runs at **http://localhost:8080**

Endpoints:
- `GET  /api/trains`
- `GET  /api/stations`
- `POST /api/fare`
- `GET  /api/seats?coachId=&date=`
- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/auth/forgot`
- `POST /api/bookings` (requires token)
- `GET  /api/bookings` (user history)

### 2. Frontend

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:3000**

---

## Booking Flow

1. **Dashboard** – Welcome slogan + 3D stats + train cards
2. **Select Train** (6 trains)
3. **Select From / To Station** → Fare auto-updates instantly
4. **Choose Coach** (A Economy / B AC Standard / C AC Business)
5. **Select Seats** (visual map, max seats from railway-style capacity)
6. **Enter passenger details** → Review
7. **Confirm** → Digital ticket with **QR code**

Without login you can browse everything but the booking steps show a lock overlay.

---

## Tech Notes

- Java: pure OOP, no frameworks, H2 for easy demo (schema also works with MySQL)
- Next.js 14 App Router, client components, localStorage session
- QR generated via public API (no extra npm dependency)
- CORS enabled on backend
- Password hashed with SHA-256 (demo level)

---

## Slogan

> **“Ride the Rails of Pakistan – Safe, Swift, Seamless”**

Enjoy building & customizing! 🚂💚
```