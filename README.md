# Smart Energy Monitoring and Analytics System
## Faculty of Engineering, University of Ruhuna

[![Node.js](https://img.shields.io/badge/Node.js-18%2B-339933?logo=node.js&logoColor=white)](https://nodejs.org/)
[![Express.js](https://img.shields.io/badge/Express.js-4.19-000000?logo=express&logoColor=white)](https://expressjs.com/)
[![MongoDB](https://img.shields.io/badge/MongoDB%20Atlas-4.4%2B-47A248?logo=mongodb&logoColor=white)](https://www.mongodb.com/atlas)
[![Mongoose](https://img.shields.io/badge/Mongoose-5.13-880000)](https://mongoosejs.com/)
[![Render](https://img.shields.io/badge/Deploy-Render-46E3B7?logo=render&logoColor=black)](https://render.com)
[![Vercel](https://img.shields.io/badge/Deploy-Vercel-000000?logo=vercel&logoColor=white)](https://vercel.com)
[![Automated Tests](https://img.shields.io/badge/Tests-187%20Passed-brightgreen)](package.json)
[![Course](https://img.shields.io/badge/Module-EE4207-blue)](docs/EE4207_FINAL_PROJECT_REPORT.md)

---

### Project Metadata

| Attribute | Details |
|---|---|
| **Course Module** | EE4207 — Web Application Development |
| **Project Title** | Smart Energy Monitoring and Analytics System |
| **Author** | K.A.W.R.K.B. Kohona |
| **Student Registration Index** | EG/2023/5685 |
| **Department** | Department of Electrical and Information Engineering |
| **Faculty** | Faculty of Engineering |
| **University** | University of Ruhuna (Hapugala, Galle, Sri Lanka) |
| **Academic Year** | 2025/2026 (Semester 4) |

---

### Deployment & Live Access

| Resource | URL / Configuration |
|---|---|
| **Live Production Deployment (Render)** | [https://smart-energy-system-zuqh.onrender.com](https://smart-energy-system-zuqh.onrender.com) |
| **Edge Routing & Proxy (Vercel)** | Configured via `vercel.json` rewrites to Render backend |
| **Source Code Repository** | [https://github.com/rusirukanchana50/Smart-Energy-System.git](https://github.com/rusirukanchana50/Smart-Energy-System.git) |

#### Pre-Configured Test Credentials

The system automatically initializes default accounts at startup with salted `bcrypt` password hashing:

| Account Role | Email | Password | Access Privileges |
|---|---|---|---|
| **Administrator** | `admin@eng.ruh.ac.lk` | `Admin@1234` | Full access: Facility CRUD, alert acknowledgment, tariff adjustments, simulation purging |
| **Viewer** | `viewer@eng.ruh.ac.lk` | `Viewer@1234` | Read-only access: Live monitoring, historical analytics, audit reports, CSV downloads |

---

## 1. Project Abstract & Objectives

The **Smart Energy Monitoring and Analytics System** is a full-stack engineering software solution developed to address the limitations of conventional bulk utility billing in educational institutions. In traditional setups, university administrations receive a single aggregate monthly electricity bill from the Ceylon Electricity Board (CEB), which provides no real-time departmental visibility, power quality tracking, or immediate notification of abnormal electrical conditions.

This system provides continuous three-phase electrical telemetry monitoring, automated safety anomaly detection across 8 critical electrical conditions, discrete numerical energy integration, two-part institutional electricity cost estimation, and formal academic audit reporting.

### Key Engineering Objectives:
1. **Three-Phase Electrical Modeling**: Calculate Apparent Power ($S$), Active Power ($P$), Reactive Power ($Q$), and displacement Power Factor ($\text{PF}$) according to standard AC power equations ($400\text{ V}$ Line-to-Line, $50\text{ Hz}$).
2. **Discrete Numerical Energy Integration**: Implement numerical trapezoidal integration across contiguous time samples with gap protection ($>60\text{s}$) to avoid artificial energy inflation during telemetry interruptions.
3. **Automated Safety Anomaly Engine**: Evaluate 8 electrical threshold conditions (undervoltage, overvoltage, low power factor, frequency excursions, high load, feeder overload, and sensor offline states) with duplicate suppression and auto-clearing logic.
4. **Institutional Tariff & Cost Estimation**: Model commercial two-part tariffs incorporating active energy consumption charges ($\text{LKR/kWh}$), peak demand charges ($\text{LKR/kW}$), and monthly fixed charges, alongside after-hours consumption tracking (08:00–17:00 weekdays).
5. **Secure Facility Management (CRUD)**: Enable administrators to register, modify, and safely delete facilities, with dependency checks preventing accidental data corruption.
6. **Academic Transparency**: All generated telemetry readings are explicitly labeled `SIMULATED` across user interfaces, API payloads, and exported audit documents to maintain scientific integrity.

---

## 2. System Architecture & Technologies

The system is constructed with a modular multi-tier architecture separating data acquisition, persistent storage, business logic, and presentation:

```
+-------------------------------------------------------------------------+
|                  Three-Phase Telemetry Simulator                        |
|                    (node_backend/simulator.js)                          |
|  - Balanced 400 V / 50 Hz three-phase generator                         |
|  - Diurnal academic load profiles (lecture/lab hours vs night baseload) |
|  - Real-time alert evaluation via alertEngine.js                        |
|  - Anomaly injection for testing (--anomaly LOW_PF, OVERLOAD, etc.)     |
+-------------------------------------------------------------------------+
                                    │
                                    ▼ (Mongoose Document Writes)
+-------------------------------------------------------------------------+
|                Database Layer (MongoDB Atlas / Local)                   |
|  - buildings       : Facility records, rated power capacities           |
|  - energy_readings : Time-series electrical telemetry (V, I, PF, P, Q)  |
|  - alerts          : Active and cleared threshold violations            |
|  - tariffs         : Configurable demonstration institutional tariff    |
|  - faculty_settings: Standard university operating schedules            |
|  - sessions        : Encrypted user sessions (connect-mongo)            |
+-------------------------------------------------------------------------+
                                    │
                                    ▼ (REST API / Server Rendering)
+-------------------------------------------------------------------------+
|                   Application Server (Express.js)                       |
|  - Session management and RBAC middleware (Administrator / Viewer)      |
|  - Cryptographic double-submit cookie CSRF protection                   |
|  - Numerical aggregation and load factor derivations                    |
|  - Nunjucks template rendering with server-side layout composition      |
+-------------------------------------------------------------------------+
                                    │
                                    ▼ (HTTP / JSON / HTML)
+-------------------------------------------------------------------------+
|                       Client User Interface                             |
|  - /dashboard        : Live substation monitoring (5s polling)          |
|  - /analytics        : Historical telemetry and multi-building comparison|
|  - /alerts           : Safety violations center & alert acknowledgment  |
|  - /cost-efficiency  : Two-part tariff cost calculator and rankings     |
|  - /reports          : 16-parameter audit reports & CSV downloads       |
|  - /reports/printable: Print-optimized official PDF report layout       |
+-------------------------------------------------------------------------+
```

### Technology Stack Specifications

- **Backend Runtime**: Node.js (v18+ LTS)
- **Web Application Framework**: Express.js (v4.19+)
- **Database Engine**: MongoDB Atlas (Cloud Production) / MongoDB Community Server (Local Development)
- **Object-Document Mapper (ODM)**: Mongoose (v5.13+)
- **Template Engine**: Nunjucks (v3.2+) with custom filters and Jinja2 compatibility hooks
- **Session Store**: `express-session` with `connect-mongo` for server-side persistence
- **Password Security**: `bcryptjs` with salt rounds (cost factor 10)
- **CSRF Defense**: Cryptographic double-submit cookie with origin validation (`req.headers.origin` and `Referer`)
- **Frontend Presentation**: Semantic HTML5, Vanilla CSS3 (Custom Properties, Grid, Flexbox, `@media print`), Vanilla ES6+ JavaScript
- **Data Visualization**: Chart.js (v4.4+) for real-time and comparative time-series graphs
- **Hosting Targets**:
  - **Render**: Node.js Web Service (host of application server and continuous telemetry simulator)
  - **Vercel**: Edge routing proxy directing traffic to the backend host

---

## 3. Electrical Engineering Formulations

The analytical module calculates standard power engineering quantities for balanced three-phase AC circuits:

| Quantity | Mathematical Equation | System Unit |
|---|---|---|
| **Apparent Power ($S$)** | $S = \frac{\sqrt{3} \times V_L \times I_L}{1000}$ | $\text{kVA}$ |
| **Active Power ($P$)** | $P = S \times \text{PF} = \frac{\sqrt{3} \times V_L \times I_L \times \cos\theta}{1000}$ | $\text{kW}$ |
| **Reactive Power ($Q$)** | $Q = \sqrt{S^2 - P^2} = S \times \sqrt{1 - \text{PF}^2}$ | $\text{kVAr}$ |
| **Discrete Energy ($\Delta E$)** | $\Delta E_i = \left(\frac{P_{i-1} + P_i}{2}\right) \times \left(\frac{\Delta t_i}{3600}\right)$ | $\text{kWh}$ |
| **Coincident Campus Peak** | $P_{\text{peak}} = \max\left(\sum_{k=1}^{M} P_{k}(t)\right)$ | $\text{kW}$ |
| **Campus Average Demand** | $P_{\text{avg}} = \frac{1}{T} \sum_{t=1}^{T} P_{\text{campus}}(t) \le P_{\text{peak}}$ | $\text{kW}$ |
| **Load Factor ($\text{LF}$)** | $\text{LF} = \frac{P_{\text{avg}}}{P_{\text{peak}}} \quad (0.0 \le \text{LF} \le 1.0)$ | Dimensionless |
| **Two-Part Cost Model** | $\text{Cost} = (E \times R_E) + \left[ (P_{\text{peak}} \times R_D + R_F) \times \frac{\text{Days}}{30} \right]$ | $\text{LKR}$ |

*For full mathematical derivations and edge-case boundary proofs, see [FORMULAS.md](docs/FORMULAS.md).*

---

## 4. Electrical Anomaly Detection Matrix

The alert engine continuously evaluates incoming telemetry against statutory and operational thresholds:

| Condition | Threshold Trigger | Severity | Action / Operational Consequence |
|---|---|---|---|
| **Severe Undervoltage** | $V_L < 380.0\text{ V}$ ($-5.0\%$ of $400\text{ V}$) | `CRITICAL` | Risk of motor overheating and controller resets |
| **Grid Overvoltage** | $V_L > 420.0\text{ V}$ ($+5.0\%$ of $400\text{ V}$) | `CRITICAL` | Insulation breakdown and capacitor degradation |
| **Low Power Factor** | $\text{PF} < 0.850$ | `WARNING` | CEB statutory billing penalties; high reactive losses |
| **High Frequency** | $f > 50.50\text{ Hz}$ | `WARNING` | Grid over-frequency event |
| **Low Frequency** | $f < 49.50\text{ Hz}$ | `WARNING` | Grid under-frequency event |
| **High Feeder Load** | $P > 90\%$ of rated capacity | `WARNING` | Approaching thermal design limit of feeder cables |
| **Feeder Overload** | $P > 100\%$ of rated capacity | `CRITICAL` | Imminent circuit breaker trip hazard |
| **Telemetry Offline** | $\Delta t > 60.0\text{ s}$ elapsed silence | `WARNING` | Sensor blackout or communications loss |

---

## 5. Repository Directory Structure

```text
Ruhuna Smart Energy System NodeJS/
├── docs/                               # Academic and technical documentation
│   ├── DATABASE_SCHEMA.md              # MongoDB collections, Mongoose schemas & indexes
│   ├── DEMO_CHECKLIST.md               # Step-by-step viva & demonstration guide
│   ├── DEMO_MODE.md                    # Anomaly injection commands & test procedure
│   ├── EE4207_FINAL_PROJECT_REPORT.md  # Comprehensive academic project report
│   ├── FORMULAS.md                     # Electrical derivations & numerical integration
│   ├── LIMITATIONS_AND_FUTURE_WORK.md  # Physical IoT hardware migration roadmap
│   ├── PROJECT_STRUCTURE.md            # Detailed structural and component overview
│   ├── SYSTEM_ARCHITECTURE.md          # Multi-tier SCADA architecture details
│   └── VIVA_NOTES.md                   # Technical Q&A guide for project evaluation
│
├── frontend/                           # Presentation layer
│   ├── static/
│   │   ├── css/
│   │   │   └── style.css               # Dark theme stylesheet & @media print styles
│   │   └── js/
│   │       ├── alerts.js               # Anomaly handling & acknowledgment logic
│   │       ├── analytics.js            # Historical analytics & Chart.js controllers
│   │       ├── auth.js                 # Login validation & password visibility toggles
│   │       ├── cost_efficiency.js      # Two-part tariff calculations & rankings
│   │       ├── dashboard.js            # 5-second asynchronous telemetry polling
│   │       └── reports.js              # 16-parameter audit reports & CSV downloads
│   └── templates/                      # Server-side rendered Nunjucks HTML templates
│       ├── alerts.html                 # Safety violations center
│       ├── analytics.html              # Historical telemetry & comparative views
│       ├── cost_efficiency.html        # Departmental cost & efficiency analytics
│       ├── dashboard.html              # Main substation monitoring dashboard
│       ├── login.html                  # University-branded login portal
│       ├── printable_report.html       # Print-optimized audit document layout
│       └── reports.html                # Energy audit center & CSV export
│
├── node_backend/                       # Application logic
│   ├── app.js                          # Express application configuration & middleware
│   ├── create_user.js                  # CLI utility for provisioning users
│   ├── init_db.js                      # Idempotent campus building database seeder
│   ├── server.js                       # Server entrypoint and background simulator worker
│   ├── simulator.js                    # Three-phase telemetry generator & alert evaluator
│   ├── config/
│   │   └── db.js                       # Mongoose MongoDB connection & lifecycle handlers
│   ├── middleware/
│   │   ├── auth.js                     # Authentication and RBAC guards
│   │   └── csrf.js                     # Double-submit cookie & origin verification
│   ├── models/                         # Mongoose document schemas
│   │   ├── Alert.js                    # Electrical alerts collection
│   │   ├── Building.js                 # Campus facilities collection
│   │   ├── EnergyReading.js            # Telemetry readings collection
│   │   ├── FacultySetting.js           # Operational schedule settings
│   │   ├── index.js                    # Model registry export
│   │   ├── Tariff.js                   # Demonstration institutional tariff collection
│   │   └── User.js                     # User accounts with bcrypt hashing
│   ├── routes/                         # Express route definitions
│   │   ├── alerts.js                   # Alert retrieval and acknowledgment endpoints
│   │   ├── auth.js                     # Authentication and session lifecycle
│   │   ├── buildings.js                # Building CRUD and dependency-safe deletion
│   │   ├── energy.js                   # Telemetry, history, analytics, and cost APIs
│   │   ├── pages.js                    # Page render controllers
│   │   └── reports.js                  # Audit summaries and CSV streaming
│   ├── services/
│   │   ├── alertEngine.js              # Threshold evaluation and alert state machine
│   │   └── analyticsService.js         # AC formulas, integration, and KPI derivations
│   └── tests/                          # Automated Node.js test suites
│       ├── phase2.test.js              # Model validation, indexes, and initializers
│       ├── phase3a.test.js             # Authentication, RBAC, and CSRF protection
│       ├── phase3b.test.js             # Building CRUD and deletion safeguards
│       ├── phase4.test.js              # AC formulas, simulator, and alert engine
│       ├── phase5.test.js              # Analytics, coincident peak, and tariff tests
│       ├── phase6a.test.js             # Routing, template rendering, and access control
│       ├── phase6b.test.js             # Live dashboard and building management
│       └── phase6c.test.js             # Analytics, alerts, and report frontend integration
│
├── .env.example                        # Template environment variables
├── package.json                        # Node.js project manifest & scripts
├── vercel.json                         # Vercel proxy rewrite configuration
└── README.md                           # Master documentation
```

---

## 6. Local Installation & Development Setup

### Prerequisites
- **Node.js**: v18.0.0 or higher
- **npm**: v9.0.0 or higher
- **MongoDB**: Local MongoDB Server (v4.4+) running on `mongodb://127.0.0.1:27017` or a MongoDB Atlas connection URI

### Step 1: Install Dependencies
```powershell
npm install
```

### Step 2: Configure Environment Variables
Copy `.env.example` to create `.env`:
```powershell
Copy-Item .env.example .env
```
Ensure `MONGODB_URI` points to your active database instance (e.g., `mongodb://127.0.0.1:27017/ruhuna_energy`).

### Step 3: Initialize Database Collections
Seed the 6 initial campus buildings (idempotent, safe to run repeatedly without creating duplicates):
```powershell
npm run init-db
```

### Step 4: Provision User Accounts
Run the interactive account creation utility:
```powershell
npm run create-user
```
Alternatively, default administrator (`admin@eng.ruh.ac.lk` / `Admin@1234`) and viewer (`viewer@eng.ruh.ac.lk` / `Viewer@1234`) accounts are automatically verified and created when the web server boots.

### Step 5: Start the Telemetry Simulator
In a separate terminal, start the three-phase simulation process:
```powershell
npm run simulator
```

### Step 6: Start the Express Application Server
In the primary terminal, start the server:
```powershell
npm start
```
The application will listen on `http://localhost:5000`. Navigate to this address in any modern web browser to access the system.

---

## 7. Automated Testing & Verification

The system includes **8 comprehensive test suites** implemented with Node's native test runner (`node:test` and `node:assert`). All tests run against an isolated test database (`ruhuna_energy_test`) to verify complete end-to-end functionality without affecting production data.

### Execute Master Test Suite:
```powershell
npm test
```

### Test Suite Breakdown:

| Suite | Focus Area | Assertions & Scenarios Tested | Count | Status |
|---|---|---|:---:|:---:|
| `phase2.test.js` | Data Modeling & Seeding | Mongoose schemas, unique indexes, validation constraints, idempotent seeding | 16 | Passed |
| `phase3a.test.js` | Authentication & Security | Bcrypt hashing, sessions, RBAC restrictions, CSRF tokens, zero secret leakage | 16 | Passed |
| `phase3b.test.js` | Building CRUD Safeguards | Name/code validation, rated capacity boundaries, dependency-checked deletion | 31 | Passed |
| `phase4.test.js` | Simulator & Alert Engine | Three-phase AC math, diurnal factors, 8 anomaly types, auto-clearing logic | 23 | Passed |
| `phase5.test.js` | Analytics & Tariff Math | Riemann integration, coincident peak demand, tariff pro-rating, 16 audit KPIs | 26 | Passed |
| `phase6a.test.js` | Routing & Templates | Server-side rendering, Nunjucks filters, unauthenticated route protection | 25 | Passed |
| `phase6b.test.js` | Dashboard & UI Controls | Live data API contract, role-specific DOM controls, safe deletion dialogs | 25 | Passed |
| `phase6c.test.js` | Frontend Integration | Analytics filters, alert acknowledgments, report generation, CSV streaming | 25 | Passed |
| **Total** | **Full System Verification** | **187 Automated Tests Across All Modules** | **187** | **100% Pass** |

---

## 8. REST API Reference

### Authentication & Session Management
- `POST /login` — Authenticate credentials and establish an encrypted session cookie + CSRF token.
- `POST /logout` — Invalidate user session and clear authentication cookies.
- `GET /api/auth/me` — Return the authenticated user profile and current CSRF token.

### Building Management
- `GET /api/buildings` — Retrieve all registered university facilities.
- `POST /api/buildings/create` — Register a new facility (Administrator + CSRF).
- `POST /api/buildings/:id/update` — Modify facility properties (Administrator + CSRF).
- `GET /api/buildings/:id/delete-preview` — Inspect associated telemetry dependencies before deletion (Administrator).
- `POST /api/buildings/:id/delete` — Delete facility only if no historical dependencies exist (Administrator + CSRF).
- `POST /api/buildings/:id/delete-simulated` — Purge demonstration facility alongside its simulated readings (Administrator + CSRF).

### Telemetry & Real-Time Monitoring
- `GET /api/energy/live` — Retrieve live three-phase telemetry payload for all active facilities.
- `GET /api/energy/history` — Query time-series telemetry filtered by building and date range.
- `GET /api/energy/analytics` — Calculate facility and university-wide operational KPIs and load factors.
- `GET /api/energy/cost` — Compute two-part institutional tariff breakdowns and after-hours energy.
- `GET /api/energy/efficiency` — Return power factor compliance and efficiency metrics.
- `GET /api/energy/efficiency/comparison` — Return comparative efficiency rankings across campus complexes.

### Safety Alerts & Anomaly Supervision
- `GET /api/alerts` — Retrieve active and historical electrical alert records.
- `POST /api/alerts/:id/acknowledge` — Formally acknowledge an active safety alert (Administrator + CSRF).

### Audit Reports & Data Export
- `GET /api/reports/summary` — Generate 16-parameter institutional energy audit summary.
- `GET /api/reports/export/csv` — Stream raw telemetry readings formatted as an RFC 4180 CSV export.
- `POST /api/config/tariff` — Update demonstration institutional tariff parameters (Administrator + CSRF).
- `GET /reports/printable` — Render print-optimized university audit report layout.

---

## 9. Academic Transparency & Hardware Migration

This project was developed for academic evaluation in **EE4207: Web Application Development** at the **Faculty of Engineering, University of Ruhuna**.

### Simulation Disclosure
To enable complete testing without access to physical substation switchgear, all telemetry readings are generated via mathematical simulation models. Every user interface view, API response, CSV download, and printed audit document explicitly displays the label **`SIMULATED`** to preserve scientific integrity.

### Hardware Migration Roadmap
The software architecture decouples telemetry ingestion from persistence and presentation. In an operational physical deployment, `node_backend/simulator.js` can be replaced with an edge gateway service (e.g., Raspberry Pi or industrial edge controller) communicating with multi-function power meters (such as Schneider Electric PM5350 or Eastron SDM630) via Modbus-RTU over RS-485 or Modbus-TCP. The backend database schemas, analytical calculations, REST APIs, and frontend dashboards require no architectural redesign to transition to physical instrumentation.

---

## 10. License

Academic and Educational Use — Faculty of Engineering, University of Ruhuna, Sri Lanka.
Developed for EE4207 (Web Application Development).
