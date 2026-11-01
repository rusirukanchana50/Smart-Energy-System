# University of Ruhuna — Smart Energy Monitoring & Analytics System
## Project Repository Structure (`PROJECT_STRUCTURE.md`)

This document details the architectural layout, directory organization, and specific role of every file and folder in the **Smart Energy Monitoring and Analytics System (Node.js + Express + MongoDB)** codebase for the Faculty of Engineering, University of Ruhuna.

---

## 1. Directory Tree Overview

```text
Ruhuna Smart Energy System NodeJS/
├── docs/                               # Comprehensive engineering documentation
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
│   ├── static/                         # Static assets served under /static
│   │   ├── css/
│   │   │   └── style.css               # Dark theme stylesheet & @media print rules
│   │   └── js/                         # Client-side JavaScript controllers
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

## 2. Detailed Component Descriptions

### 1. `node_backend/`
- **`server.js`**: Connects to MongoDB, initializes the Express server on port 5000, and attaches graceful shutdown handlers (`SIGINT`, `SIGTERM`).
- **`app.js`**: Assembles middleware (`cors`, `express.json`, `express.urlencoded`, `express-session` with `connect-mongo`, `csrfProtection`), configures Nunjucks template engine with Jinja2 compatibility hooks, and mounts REST & page routers.
- **`simulator.js`**: Generates balanced 400V/50Hz telemetry for all registered buildings using diurnal load curves and trapezoidal integration with missing data handling. Automatically triggers anomaly checks through `alertEngine`.
- **`init_db.js`**: Idempotently seeds the 6 core campus buildings into MongoDB without deleting existing data.
- **`create_user.js`**: CLI utility for creating or updating Administrator and Viewer accounts with salted bcrypt password hashing.

### 2. `node_backend/models/`
- **`Building.js`**: Stores facility code (unique, uppercase), name, description, rated capacity (kW), and core protection flag.
- **`EnergyReading.js`**: Stores timestamped balanced three-phase readings ($V_L$, $I_L$, $\text{PF}$, $f$, $S$, $P$, $Q$, $\text{kWh}$, $\text{is\_simulated}$).
- **`Alert.js`**: Stores active and historical safety violation alerts with auto-clearing and administrator acknowledgment timestamps.
- **`User.js`**: Stores user authentication credentials with salted bcrypt password hashing and RBAC roles.
- **`Tariff.js`**: Stores commercial/industrial demonstration tariff rates.
- **`FacultySetting.js`**: Stores faculty configuration parameters such as working hours.

### 3. `node_backend/services/`
- **`alertEngine.js`**: Evaluates live telemetry against electrical safety thresholds (voltage limits, low power factor, frequency excursions, high load, feeder overload, and sensor timeout). Handles duplicate suppression and automatic clearing.
- **`analyticsService.js`**: Implements three-phase AC power calculations, discrete trapezoidal Riemann energy integration, load factor computation, after-hours energy ratios, and 16-parameter audit summary generation.

### 4. `node_backend/tests/`
Contains 8 automated test suites built on Node's native test runner (`node:test`), covering 187 test assertions spanning models, sessions, RBAC, CRUD, math, telemetry, alert detection, and UI views.
