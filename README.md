# University of Ruhuna — Faculty of Engineering
# Smart Energy Monitoring and Analytics System (Node.js & MongoDB)

[![Node.js 18+](https://img.shields.io/badge/Node.js-18%2B-green.svg)](https://nodejs.org/)
[![Express.js](https://img.shields.io/badge/Framework-Express%204.19-lightgrey.svg)](https://expressjs.com/)
[![MongoDB 3.4+](https://img.shields.io/badge/Database-MongoDB%203.4%2B-green.svg)](https://www.mongodb.com/)
[![Mongoose ODM](https://img.shields.io/badge/ODM-Mongoose%205.13-red.svg)](https://mongoosejs.com/)
[![Automated Tests](https://img.shields.io/badge/Node%20Tests-186%2F186%20Passed-brightgreen.svg)](package.json)
[![Academic License](https://img.shields.io/badge/License-Academic%20Project-purple.svg)]()

A centralized, real-time three-phase electrical energy monitoring, anomaly detection, and analytics web portal developed for the **Faculty of Engineering, University of Ruhuna** (Hapugala, Galle, Sri Lanka).

This project is an independent **Node.js + Express + MongoDB** application engineered for university electrical facilities management, telemetry supervision, and academic energy auditing.

---

## ⚡ 1. Project Overview & Engineering Objectives

The system provides centralized SCADA-like supervision of electrical consumption, power quality, and operational costs across the academic and administrative complexes of the Faculty of Engineering, University of Ruhuna.

### Specific Engineering Objectives:
1. **Real-Time Telemetry Supervision**: Continuously supervise balanced three-phase electrical telemetry ($400\text{ V Line-to-Line}$, $50.0\text{ Hz}$). Six core campus buildings are seeded idempotently, with administrative capabilities to dynamically add, edit, and safely manage additional facilities.
2. **Three-Phase AC Electrical Modeling**: Full AC power triangle calculations ($S = \sqrt{3} V_L I_L$, $P = S \times \text{PF}$, $Q = \sqrt{S^2 - P^2}$) and discrete Riemann trapezoidal energy integration with missing telemetry gap protection ($>60\text{s}$ sensor blackout guard).
3. **Automated Anomaly & Safety Alerting**: Real-time state-machine alert engine continuously tracking 8 critical abnormal electrical conditions: Undervoltage ($<380\text{V}$), Overvoltage ($>420\text{V}$), Low Power Factor ($<0.85$), Frequency Excursions ($<49.5\text{Hz}$ or $>50.5\text{Hz}$), Feeder High Load ($>90\%$), Feeder Overload ($>100\%$), and Sensor Offline ($>60\text{s}$). Duplicate alerts are suppressed and auto-cleared when conditions normalize.
4. **Institutional Demonstration Tariff & Efficiency Analysis**: Two-part industrial/commercial tariff modeling (energy charge, peak demand charge, and fixed monthly charge) with automated after-hours consumption tracking (08:00–17:00 weekdays).
5. **Role-Based Access Control (RBAC) & Security**: Cryptographic session authentication with MongoDB session store (`connect-mongo`), `bcryptjs` password hashing, and custom double-submit cookie CSRF defense.
6. **Formal Audit Reporting & Export**: 16-parameter energy audit summaries, raw CSV telemetry streaming, and official university-branded printable PDF reports.
7. **Academic Transparency**: All simulated telemetry is explicitly tagged `SIMULATED` across the UI, API responses, CSV downloads, and audit reports.

---

## 🛠️ 2. Technology Stack

### Backend Architecture
- **Runtime Environment**: Node.js (v18+)
- **Application Framework**: Express.js (v4.19+)
- **Database Engine**: MongoDB Server (v3.4+)
- **Object Data Modeling (ODM)**: Mongoose (v5.13+)
- **Templating Engine**: Nunjucks (v3.2+) with Jinja2 compatibility hooks
- **Session Management**: `express-session` with `connect-mongo` (v3.2+)
- **Password Security**: `bcryptjs` (v2.4+) with salted SHA-512 derivation
- **CSRF Defense**: Cryptographic double-submit cookie & request-body verification middleware

### Frontend Presentation Layer
- **HTML5 & Vanilla CSS3**: Dark mode engineering aesthetic (`#0b1121`), glassmorphism, responsive CSS Grid / Flexbox, and `@media print` styling
- **Vanilla JavaScript (ES6+)**: Fetch API, 5-second asynchronous polling, dynamic DOM updates, and client-side form validation
- **Chart.js (v4.4+)**: Real-time sliding-window line charts and departmental comparison bar charts

---

## 🏗️ 3. System Architecture

```text
  +-------------------------------------------------------------------------+
  |              Node.js Three-Phase Telemetry Simulator                    |
  |                      (node_backend/simulator.js)                       |
  |  - Balanced 400V / 50Hz Generator                                       |
  |  - Diurnal Load Curves (08:00-17:00 Working Peak vs Night Baseline)     |
  |  - Apparent (kVA), Active (kW), Reactive (kVAr) & Incremental kWh       |
  |  - Automated Alert Evaluation via alertEngine.js                        |
  |  - Controlled Anomaly Injection (--anomaly LOW_PF, LOW_VOLTAGE, etc.)   |
  +-------------------------------------------------------------------------+
                                       │
                                       ▼ (Direct Mongoose Inserts)
  +-------------------------------------------------------------------------+
  |                  MongoDB Database (ruhuna_energy)                       |
  |                       Compatible with MongoDB 3.4+                      |
  |  - buildings       : 6 Core Campus Complexes + Dynamic Admin Facilities |
  |  - users           : Administrator / Viewer with bcrypt hashed passwords|
  |  - energy_readings : Timestamped 3-phase records, marked SIMULATED      |
  |  - alerts          : Active / Cleared State-Machine Anomaly Log         |
  |  - tariffs         : Configurable Demonstration Institutional Tariff    |
  |  - faculty_settings: Operating Hours & Faculty Schedule Parameters      |
  |  - sessions        : MongoStore Server-Side Encrypted Sessions          |
  +-------------------------------------------------------------------------+
                      ▲                                     ▲
                      │ (Reads Telemetry)                   │ (Writes / Ack)
  +-------------------------------------------------------------------------+
  |                   Node.js Express Web Backend                           |
  |             (node_backend/server.js, node_backend/app.js)               |
  |  - MongoStore Session Management (SESSION_SECRET)                       |
  |  - Role-Based Access Control (requireAuth, requireAdmin)                |
  |  - CSRF Token Generation & Dual-Validation Middleware                   |
  |  - Analytics Service & Report Computation (analyticsService.js)        |
  |  - REST APIs: /api/energy/live, /api/energy/analytics, /api/alerts,     |
  |               /api/energy/cost, /api/reports/summary, CSV export        |
  +-------------------------------------------------------------------------+
                                       │
                                       ▼ (JSON / Nunjucks Server-Side Render)
  +-------------------------------------------------------------------------+
  |                       Web Browser User Interface                        |
  |  - /dashboard        : Live Substation Monitor & 5s Chart.js Polling    |
  |  - /analytics        : Historical Electrical Hub & Load Factor Analysis |
  |  - /alerts           : Safety Alert Engine & One-Click Acknowledgment   |
  |  - /cost-efficiency  : Demonstration Tariff Calculator & Rankings       |
  |  - /reports          : 16-Metric Audit Summaries & CSV Export           |
  |  - /reports/printable: Official University-Formatted Printable Audit    |
  +-------------------------------------------------------------------------+
```

---

## 📁 4. Project Directory Structure

```text
Ruhuna Smart Energy System NodeJS/
├── docs/                               # Engineering documentation & academic viva notes
│   ├── DATABASE_SCHEMA.md              # MongoDB collections, Mongoose schemas & indexes
│   ├── DEMO_CHECKLIST.md               # Step-by-step viva & demonstration checklist
│   ├── DEMO_MODE.md                    # Anomaly injection commands & demo guide
│   ├── EE4207_FINAL_PROJECT_REPORT.md  # Comprehensive academic project report
│   ├── FORMULAS.md                     # Electrical formulations & derivations
│   ├── LIMITATIONS_AND_FUTURE_WORK.md  # Real IoT hardware roadmap & future expansion
│   ├── PROJECT_STRUCTURE.md            # In-depth architectural layout & component map
│   ├── SYSTEM_ARCHITECTURE.md          # Multi-tier SCADA architecture details
│   └── VIVA_NOTES.md                   # Viva defense guide & anticipated questions
│
├── frontend/                           # Presentation layer assets and templates
│   ├── static/
│   │   ├── css/
│   │   │   └── style.css               # Dark theme stylesheet & @media print rules
│   │   └── js/
│   │       ├── alerts.js               # Anomaly supervision & RBAC acknowledgment
│   │       ├── analytics.js            # Historical analytics, date presets & charts
│   │       ├── auth.js                 # Login validation & password visibility toggle
│   │       ├── cost_efficiency.js      # Two-part tariff cost calculator & bar charts
│   │       ├── dashboard.js            # 5s live polling, KPI cards & live canvas
│   │       └── reports.js              # 16-parameter audit reports & CSV downloads
│   └── templates/                      # Server-side Nunjucks HTML templates
│       ├── alerts.html                 # Safety violations center & alert logs
│       ├── analytics.html              # Historical telemetry & load factor views
│       ├── cost_efficiency.html        # Energy cost & department efficiency views
│       ├── dashboard.html              # Primary live substation monitoring dashboard
│       ├── login.html                  # University-branded login portal
│       ├── printable_report.html       # Official printable energy audit report
│       └── reports.html                # Energy audit center & CSV export
│
├── node_backend/                       # Core Node.js / Express application logic
│   ├── app.js                          # Express application configuration & middleware
│   ├── create_user.js                  # CLI tool to provision Administrator / Viewer
│   ├── init_db.js                      # Idempotent campus building database seeder
│   ├── server.js                       # HTTP server entrypoint (listens on PORT 5000)
│   ├── simulator.js                    # Three-phase telemetry generator & alert evaluator
│   ├── config/
│   │   └── db.js                       # Mongoose MongoDB connection & error handlers
│   ├── middleware/
│   │   ├── auth.js                     # requireAuth & requireAdmin RBAC guards
│   │   └── csrf.js                     # Double-submit cookie CSRF defense
│   ├── models/
│   │   ├── Alert.js                    # Alert model & active state-machine tracking
│   │   ├── Building.js                 # Building model (code, rated power, is_core)
│   │   ├── EnergyReading.js            # Telemetry model (V, I, PF, S, P, Q, kWh)
│   │   ├── FacultySetting.js           # Faculty operating hours configuration
│   │   ├── index.js                    # Unified models export
│   │   ├── Tariff.js                   # Demonstration institutional tariff schema
│   │   └── User.js                     # User model with bcrypt password hashing
│   ├── routes/
│   │   ├── alerts.js                   # /api/alerts & acknowledgment endpoints
│   │   ├── auth.js                     # /login, /logout, /api/auth/me endpoints
│   │   ├── buildings.js                # /api/buildings CRUD & deletion safety endpoints
│   │   ├── energy.js                   # /api/energy/live, /history, /analytics, /cost
│   │   ├── pages.js                    # Server-side rendered views (/dashboard, etc.)
│   │   └── reports.js                  # /api/reports/summary, /reports/printable, CSV
│   ├── services/
│   │   ├── alertEngine.js              # Real-time electrical threshold checking engine
│   │   └── analyticsService.js         # AC electrical math, trapezoidal integration & KPIs
│   └── tests/                          # Automated JavaScript test suites (Node.js test runner)
│       ├── phase2.test.js              # Models, indexes & DB initialization tests
│       ├── phase3a.test.js             # Authentication, sessions & RBAC tests
│       ├── phase3b.test.js             # Building CRUD & deletion safeguards tests
│       ├── phase4.test.js              # Simulator math & alert engine anomaly tests
│       ├── phase5.test.js              # Historical analytics, costs, & reports tests
│       ├── phase6a.test.js             # Nunjucks template rendering & routing tests
│       ├── phase6b.test.js             # Live dashboard & building management tests
│       └── phase6c.test.js             # Frontend integration, reports, & tariff tests
│
├── .env.example                        # Template environment variables
├── .gitignore                          # Git ignore rules for Node.js / MongoDB
├── package.json                        # Node.js project manifest & npm scripts
├── package-lock.json                   # Deterministic dependency lockfile
└── README.md                           # Master project documentation
```

---

## 🚀 5. Getting Started

### Prerequisites
- **Node.js**: v18.0.0 or higher
- **npm**: v9.0.0 or higher
- **MongoDB Server**: v3.4 or higher running on `mongodb://127.0.0.1:27017`

### Step 1: Install Dependencies
```powershell
npm.cmd install
```

### Step 2: Configure Environment Variables
Copy `.env.example` to create `.env`:
```powershell
Copy-Item .env.example .env
```
Ensure your MongoDB connection string in `.env` is configured (defaults to `mongodb://127.0.0.1:27017/ruhuna_energy`).

### Step 3: Initialize MongoDB Collections
Seed the 6 core campus buildings (idempotent, safe to run repeatedly without duplicate creation):
```powershell
npm.cmd run init-db
```

### Step 4: Create User Accounts
Create an **Administrator** or **Viewer** account:
```powershell
npm.cmd run create-user
```
Or run non-interactively:
```powershell
node node_backend/create_user.js --email admin@eng.ruh.ac.lk --name "Faculty Admin" --role Administrator --password adminpassword
node node_backend/create_user.js --email viewer@eng.ruh.ac.lk --name "Faculty Viewer" --role Viewer --password viewerpassword
```

### Step 5: Start the Telemetry Simulator
Open a terminal and start the continuous three-phase simulator:
```powershell
npm.cmd run simulator
```
*Tip: To run a single telemetry cycle:*
```powershell
node node_backend/simulator.js --once
```

### Step 6: Start the Web Application
Open a second terminal and start the Express web server:
```powershell
npm.cmd start
```
The server will bind to `http://localhost:5000`.

### Step 7: Open the Portal
Navigate to:
```text
http://localhost:5000
```
Log in using your provisioned Administrator or Viewer credentials.

---

## 🧪 6. Automated Testing

The project includes **8 automated test suites** built on Node's native test runner (`node:test` and `node:assert`). All tests run sequentially against an isolated MongoDB test environment (`ruhuna_energy_test`) to verify complete system behavior.

### Run All 184 Automated Tests:
```powershell
npm.cmd test
```

### Individual Test Suites:
| Test Command | Test Suite Description | Test Count |
|---|---|:---:|
| `npm.cmd run test:phase2` | MongoDB Connection, Building/User Models, Idempotent Seeding | 16 |
| `npm.cmd run test:phase3a` | Authentication, RBAC Security, CSRF Defense, Zero Secret Leakage | 16 |
| `npm.cmd run test:phase3b` | Building CRUD, Unique Indexes, Dependency Integrity & Deletion Safeguards | 31 |
| `npm.cmd run test:phase4` | Three-Phase AC Formulas, Simulator Cycles, Alert Engine Anomalies | 23 |
| `npm.cmd run test:phase5` | Historical Analytics, Load Factor, Tariff Cost & 16-Metric Audit | 22 |
| `npm.cmd run test:phase6a` | Nunjucks Template Rendering, Static Serving, Unauth Protection & Login Alerts | 28 |
| `npm.cmd run test:phase6b` | Live Dashboard Contract, Role-Based UI Controls, Safe Building Deletion | 25 |
| `npm.cmd run test:phase6c` | Analytics, Alerts, Cost/Efficiency & Reports Frontend Integration | 25 |
| **Total** | **Full System Automated Verification** | **186 Passed** |

---

## 📊 7. REST API Endpoints Reference

### Authentication & Sessions
- `POST /login` — Authenticate user and issue encrypted session cookie + CSRF token
- `POST /logout` — Destroy session and clear session cookie
- `GET /api/auth/me` — Retrieve active user session profile and CSRF token

### Building Management
- `GET /api/buildings` — List all campus buildings (Authenticated)
- `POST /api/buildings/create` — Register new facility (Administrator + CSRF)
- `POST /api/buildings/:id/update` — Update facility details (Administrator + CSRF)
- `GET /api/buildings/:id/delete-preview` — Inspect building dependencies before deletion (Administrator)
- `POST /api/buildings/:id/delete` — Safely delete non-core facility without dependencies (Administrator + CSRF)
- `POST /api/buildings/:id/delete-simulated` — Purge demonstration facility & its simulated readings (Administrator + CSRF)

### Telemetry & Live Supervision
- `GET /api/energy/live` — 5-second live telemetry payload for all active campus buildings
- `GET /api/energy/history` — Historical time-series telemetry filtered by building and time window
- `GET /api/energy/analytics` — Campus-wide or single-building operational KPIs & load factors
- `GET /api/energy/cost` — Two-part tariff cost analysis and breakdown
- `GET /api/energy/efficiency` — Operating efficiency indicators & after-hours consumption
- `GET /api/energy/efficiency/comparison` — Departmental efficiency comparative rankings

### Alerts & Safety Supervision
- `GET /api/alerts` — Active and historical electrical alerts with filter options
- `POST /api/alerts/:id/acknowledge` — Acknowledge an active electrical safety alert (Administrator + CSRF)

### Audit Reports & Exports
- `GET /api/reports/summary` — Full 16-parameter energy audit summary report
- `GET /api/reports/export/csv` — Stream raw telemetry CSV export (labeled SIMULATED)
- `POST /api/config/tariff` — Configure demonstration institutional tariff rates (Administrator + CSRF)
- `GET /reports/printable` — Official university-branded printable PDF audit report view

---

## 💡 8. Electrical Engineering Calculations & Formulas

| Metric | Formula | Unit |
|---|---|---|
| **Apparent Power ($S$)** | $S = \frac{\sqrt{3} \times V_L \times I_L}{1000}$ | $\text{kVA}$ |
| **Active Power ($P$)** | $P = S \times \text{PF}$ | $\text{kW}$ |
| **Reactive Power ($Q$)** | $Q = \sqrt{S^2 - P^2} = S \times \sqrt{1 - \text{PF}^2}$ | $\text{kVAr}$ |
| **Discrete Energy ($\Delta E$)** | $\Delta E = \frac{P_1 + P_2}{2} \times \frac{\Delta t}{3600}$ | $\text{kWh}$ |
| **Load Factor ($\text{LF}$)** | $\text{LF} = \frac{P_{\text{avg}}}{P_{\text{peak}}}$ | Dimensionless $[0.0, 1.0]$ |
| **Two-Part Energy Cost** | $\text{Cost} = (E \times \text{Rate}_E) + (P_{\text{peak}} \times \text{Rate}_D) + \text{Fixed Charge}$ | $\text{LKR}$ |

*For complete mathematical derivations and edge-case handling rules, see [FORMULAS.md](docs/FORMULAS.md).*

---

## 📜 9. Academic Integrity & Verification

This system was created as an academic engineering project for the Faculty of Engineering, University of Ruhuna.
- **Pure Node.js + Express + MongoDB stack**: Zero dependencies on Python, Flask, or SQLite.
- **Honest Simulation Transparency**: Simulated data is explicitly marked `SIMULATED` across all UI views, JSON payloads, CSV downloads, and formal audit summaries.
- **Hardware Integration Roadmap**: Designed with clean service interfaces to easily transition from the simulation engine to physical Schneider PM5350 / Eastron SDM630 Modbus-TCP and MQTT smart meters (see [LIMITATIONS_AND_FUTURE_WORK.md](docs/LIMITATIONS_AND_FUTURE_WORK.md)).

---

## 📄 License

Academic and Educational Use — Faculty of Engineering, University of Ruhuna, Sri Lanka.
