# UNIVERSITY OF RUHUNA — FACULTY OF ENGINEERING
## DEPARTMENT OF ELECTRICAL AND INFORMATION ENGINEERING
### EE4207: Web Application Development — Mini-Project Final Report

---

# Centralized Smart Energy Monitoring and Analytics System for the Faculty of Engineering, University of Ruhuna

**Student Name:** K.A.W.R.K.B. Kohona  
**Student Registration / Index Number:** EG/2023/5685  
**Course Code & Module:** EE4207 — Web Application Development  
**Academic Year / Semester:** Academic Year 2025/2026 — Semester 4  
**Department:** Department of Electrical and Information Engineering  
**Submission Date:** November 2026  
**GitHub Repository:** `https://github.com/rusirukanchana50/Smart-Energy-System.git`  
**Live Production URL:** `https://smart-energy-system-zuqh.onrender.com`  

---

## Executive Summary

This report presents the design, technical implementation, security architecture, and validation of the **Ruhuna Smart Energy System**, an enterprise-grade full-stack web platform engineered for the Faculty of Engineering, University of Ruhuna (Hapugala, Galle, Sri Lanka). The system delivers centralized supervision of three-phase electrical consumption ($400\text{ V Line-to-Line}$, $50.0\text{ Hz}$), automated safety anomaly detection across 8 electrical fault conditions, discrete trapezoidal energy integration, two-part institutional electricity tariff modeling, 16-parameter audit reporting, and comprehensive facility inventory management with dependency-safeguarded CRUD operations.

The software architecture is implemented on a decoupled **Node.js (v18+)** and **Express.js (v4.19+)** backend runtime integrated with **MongoDB Atlas** via **Mongoose (v5.13+)** Object-Document Modeling. The presentation layer utilizes server-side **Nunjucks (v3.2+)** templating, responsive vanilla CSS3, client-side ES6+ JavaScript, and **Chart.js (v4.4+)** for dynamic time-series visualizations. Application security is enforced using salted `bcrypt` password hashing, persistent server-side session management (`connect-mongo`), double-submit cookie Cross-Site Request Forgery (CSRF) defense, and role-based access control (RBAC). The platform was verified using 8 automated test suites comprising 187 test cases with a 100% pass rate. All simulated data is explicitly marked `SIMULATED` to maintain scientific and academic integrity.

---

## 1. Introduction & Problem Statement

### 1.1 Context & Background
The Faculty of Engineering at the University of Ruhuna encompasses several academic and administrative complexes distributed across its Hapugala campus:
- Civil and Environmental Engineering (`CEE`)
- Electrical and Information Engineering (`EIE`)
- Mechanical and Manufacturing Engineering (`MME`)
- Marine Engineering and Naval Architecture (`MENA`)
- Administration Building (`ADMIN`)
- Faculty Auditorium (`AUDI`)

Each facility exhibits distinct load characteristics, ranging from heavy inductive workshop machinery and hydraulic pumping rigs to computer laboratories, server rooms, and air-conditioning units.

### 1.2 Problem Statement
In conventional educational institutions in Sri Lanka, electricity monitoring relies on aggregate monthly utility bills issued by the Ceylon Electricity Board (CEB). This retrospective approach presents critical engineering limitations:
1. **Lack of Operational Visibility**: Facility managers have no instantaneous visibility into line voltages, line currents, active power, reactive power, or power factor fluctuations.
2. **Delayed Anomaly Identification**: Severe voltage fluctuations ($<380\text{ V}$ or $>420\text{ V}$), frequency deviations, and poor power factor ($\text{PF} < 0.85$) remain undetected until physical equipment sustains damage or utility surcharges are incurred.
3. **Unmonitored After-Hours Consumption**: A considerable proportion of energy is consumed outside standard operating hours (17:00–08:00 weekdays and weekends) due to unmonitored baseline lighting, idle research apparatus, and HVAC systems left running.
4. **Decentralized Facility Records**: There is no centralized, authenticated database to track building rated capacities, maintain audit histories, or evaluate load factors.

### 1.3 Project Objectives
The objective of this project is to develop and validate a secure web application addressing these requirements:
- **Objective 1 (Facility CRUD Operations)**: Implement authenticated, validated management to Create, Read, Update, and Safely Delete building profiles with relational integrity safeguards.
- **Objective 2 (Three-Phase AC Modeling)**: Accurately model three-phase AC power quantities ($S, P, Q, \text{PF}$) and discrete trapezoidal energy integration with missing-sensor gap protection.
- **Objective 3 (Automated Safety Alerting)**: Construct an automated state-machine alert engine evaluating 8 electrical conditions with duplicate suppression and auto-clearing logic.
- **Objective 4 (Tariff & Efficiency Analytics)**: Implement a commercial two-part demonstration tariff model, university load factor derivations, and after-hours energy tracking.
- **Objective 5 (Audit Reporting & Export)**: Generate 16-parameter audit summaries, stream raw telemetry in RFC 4180 CSV format, and render an official print-optimized PDF view.
- **Objective 6 (Security & RBAC Enforcement)**: Restrict administrative capabilities using session authentication, CSRF validation, and cryptographic password hashing.

---

## 2. System Architecture & Technologies

### 2.1 Multi-Tier Decoupled Architecture
The system employs an asynchronous client-server architecture separating telemetry generation, document persistence, business logic, and presentation:

```text
 ┌────────────────────────────────────────────────────────────────────────┐
 │                    THREE-PHASE TELEMETRY SIMULATOR                     │
 │                      (node_backend/simulator.js)                       │
 │  • 400 V / 50 Hz Nominal AC Model   • Diurnal Working Hours Curves     │
 │  • S (kVA), P (kW), Q (kVAr) Math   • Controlled Anomaly Injection     │
 └───────────────────────────────────┬────────────────────────────────────┘
                                     │ Mongoose Document Inserts (Every 15s)
                                     ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │                      MONGODB ATLAS DATABASE                            │
 │                        (Database: ruhuna_energy)                       │
 │  • buildings (Campus Complexes)     • users (RBAC Hashed Credentials)  │
 │  • energy_readings (Telemetry Data) • alerts (Active / Cleared State)  │
 │  • tariffs (Demonstration Model)    • faculty_settings (Schedule Rules)│
 │  • sessions (connect-mongo store)                                      │
 └───────────────────────────────────▲────────────────────────────────────┘
                                     │ Queries, Aggregations & Mutations
                                     ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │                      NODE.JS / EXPRESS BACKEND                         │
 │                    (server.js, app.js, routes/)                        │
 │  • connect-mongo Session Engine     • Role Guards (requireAuth,        │
 │  • Double-Submit Cookie CSRF Defense   requireAdmin)                   │
 │  • RESTful API Endpoints (/api/...) • Analytical Computation Engine    │
 └───────────────────────────────────┬────────────────────────────────────┘
                                     │ HTTP Responses (JSON / Nunjucks HTML)
                                     ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │                       WEB BROWSER FRONTEND (UI)                        │
 │  • Semantic HTML5 / Nunjucks Layout • Dark Engineering Theme (CSS3)    │
 │  • Vanilla JavaScript Controllers   • Interactive Chart.js Graphs      │
 │  • Asynchronous 5s fetch() Polling  • Print Stylesheet (@media print)  │
 └────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Technology Stack Justification
- **Node.js (v18+) & Express.js (v4.19+)**: Provides an asynchronous, non-blocking I/O event loop suitable for concurrent telemetry processing and high-frequency polling.
- **MongoDB Atlas & Mongoose (v5.13+)**: Document-oriented schema definitions with type validation, compound indexes for time-series optimization, and flexible object modeling.
- **Nunjucks (v3.2+)**: High-performance templating engine with template inheritance, macros, and server-side filtering.
- **Security Middleware (`bcryptjs`, `connect-mongo`, `csrf.js`)**: Salted password hashing, persistent server-side session stores, and double-submit cookie verification.
- **Frontend (Vanilla HTML5, CSS3, ES6+ JavaScript)**: Lightweight client execution without heavy third-party framework overhead.
- **Chart.js (v4.4+)**: Canvas rendering for live sliding-window electrical telemetry and comparative bar charts.
- **Deployment Infrastructure**:
  - **Render**: Production Node.js web service running the application server and background telemetry simulation.
  - **Vercel**: Edge reverse proxy configured via `vercel.json` rewrites.

---

## 3. Database Design & Document Integrity

### 3.1 Document Collections & Schema Structure
The database is structured into 6 primary collections:

```text
 ┌────────────────────────┐                   ┌────────────────────────┐
 │       buildings        │1                 *│    energy_readings     │
 ├────────────────────────┤───────────────────├────────────────────────┤
 │ _id (ObjectId)         │                   │ _id (ObjectId)         │
 │ name (String, Unique)  │                   │ building_id (ObjectId) │
 │ code (String, Unique)  │                   │ timestamp (Date)       │
 │ description (String)   │                   │ voltage (Number)       │
 │ rated_power_kw (Number)│                   │ current (Number)       │
 │ is_core (Boolean)      │                   │ power_factor (Number)  │
 └───────────┬────────────┘                   │ frequency (Number)     │
             │1                               │ active_power_kw        │
             │                                │ apparent_power_kva     │
             │                                │ reactive_power_kvar    │
             │*                               │ energy_kwh (Number)    │
 ┌───────────┴────────────┐                   │ is_simulated (Boolean) │
 │         alerts         │                   └────────────────────────┘
 ├────────────────────────┤
 │ _id (ObjectId)         │                   ┌────────────────────────┐
 │ building_id (ObjectId) │                   │         users          │
 │ timestamp (Date)       │                   ├────────────────────────┤
 │ alert_type (String)    │                   │ _id (ObjectId)         │
 │ severity (String)      │                   │ email (String, Unique) │
 │ measured_value (Number)│                   │ password (Bcrypt Hash) │
 │ threshold_value(Number)│                   │ full_name (String)     │
 │ message (String)       │                   │ role (Admin / Viewer)  │
 │ acknowledged (Boolean) │                   │ created_at (Date)      │
 │ acknowledged_by(String)│                   └────────────────────────┘
 │ acknowledged_at (Date) │
 │ is_active (Boolean)    │                   ┌────────────────────────┐
 │ cleared_at (Date)      │                   │        tariffs         │
 └────────────────────────┘                   ├────────────────────────┤
                                              │ _id (ObjectId)         │
 ┌────────────────────────┐                   │ name (String)          │
 │    faculty_settings    │                   │ energy_rate_lkr_per_kwh│
 ├────────────────────────┤                   │ demand_rate_lkr_per_kva│
 │ _id (ObjectId)         │                   │ fixed_charge_lkr       │
 │ operating_hours (Obj)  │                   │ updated_at (Date)      │
 │ schedule_rules (Obj)   │                   └────────────────────────┘
 └────────────────────────┘
```

### 3.2 Relational Integrity & Safeguarded Deletion
To prevent historical data corruption, facility deletion implements strict dependency checking:
1. **Core Building Protection**: The 6 original engineering complexes carry the `is_core: true` attribute. Attempts to delete core facilities are rejected with `HTTP 403 Forbidden`.
2. **Dependency Checking (`GET /api/buildings/:id/delete-preview`)**: Queries associated `energy_readings` and `alerts`. If dependent records exist, direct deletion is blocked (`HTTP 409 Conflict`).
3. **Demonstration Cleanup Endpoint (`POST /api/buildings/:id/delete-simulated`)**: Requires an explicit confirmation code check to safely purge disposable test facilities alongside their simulated readings.

---

## 4. Authentication, Role-Based Access Control (RBAC) & Security

### 4.1 Authentication & Session Management
- **Password Security**: Passwords are encrypted using `bcryptjs` with salt rounds (cost factor 10). Plain-text passwords are never stored or logged.
- **Session Persistence**: Sessions are managed with `express-session` and stored in MongoDB using `connect-mongo`. Cookies are configured with `HttpOnly` and `SameSite: 'Lax'` attributes.
- **Startup Provisioning**: Default administrator (`admin@eng.ruh.ac.lk`) and viewer (`viewer@eng.ruh.ac.lk`) credentials are automatically verified and initialized at server startup.

### 4.2 Role-Based Access Control (RBAC)
- **Administrator**: Full privileges (Facility CRUD, Alert Acknowledgment, Demonstration Tariff Updates, CSV Export, Data Purging).
- **Viewer**: Read-only privileges (Substation Monitoring, Historical Analytics, Alert Supervision, Report Generation).
Protected endpoints utilize modular middleware guards (`requireAuth` and `requireAdmin`):
```javascript
router.post('/create', requireAuth, requireAdmin, async (req, res) => { ... });
```
Requests lacking required administrative authorization receive `HTTP 403 Forbidden`.

### 4.3 Double-Submit Cookie CSRF & Origin Validation
State-changing requests (`POST`, `PUT`, `DELETE`) are protected by a double-submit cookie pattern:
1. **Origin Verification**: Requests are validated against authorized host origins (`localhost`, `127.0.0.1`, `smart-energy-system-zuqh.onrender.com`, and `*.vercel.app`).
2. **CSRF Token Matching**: A cryptographically random token is stored in `req.session.csrfToken` and validated against the request header `x-csrf-token` or form body `_csrf`.

---

## 5. Facility Inventory Management (CRUD) Implementation

The Building Management module fulfills core requirements for server-side validation, database transactions, and error handling:

### 5.1 Create Facility (POST `/api/buildings/create`)
- Inputs are sanitized and validated:
  - `code`: Uppercase string matching `^[A-Z0-9_-]{2,10}$`.
  - `name`: Cleaned string of 2 to 100 characters.
  - `rated_power_kw`: Strict positive finite float ($0.1\text{ kW}$ to $10,000.0\text{ kW}$).
- Rejects duplicate codes or names with `HTTP 409 Conflict`.

### 5.2 Read Facilities (GET `/api/buildings` & Server Rendering)
- Returns JSON arrays for API consumers and passes building objects to the Nunjucks view engine for dashboard rendering.

### 5.3 Update Facility (POST `/api/buildings/:id/update`)
- Validates capacity ranges and verifies uniqueness against other facilities while allowing the building to maintain its current code.

### 5.4 Delete Facility (POST `/api/buildings/:id/delete`)
- Rejects requests for buildings with telemetry dependencies (`HTTP 409 Conflict`). Unused test facilities are cleanly removed (`HTTP 200 OK`).

---

## 6. Three-Phase Electrical Modeling & Telemetry Simulation

*All electrical measurements generated by `simulator.js` are explicitly designated as `SIMULATED`.*

### 6.1 Three-Phase Electrical Power Formulations
The mathematical engine models a balanced three-phase 4-wire distribution system ($400.0\text{ V Line-to-Line}$, $50.0\text{ Hz}$):

1. **Apparent Power ($S$)**:
   $$S = \frac{\sqrt{3} \times V_L \times I_L}{1000} \quad [\text{kVA}]$$
2. **Active Power ($P$)**:
   $$P = S \times \text{PF} = \frac{\sqrt{3} \times V_L \times I_L \times \text{PF}}{1000} \quad [\text{kW}]$$
3. **Reactive Power ($Q$)**:
   $$Q = S \times \sqrt{1 - \text{PF}^2} \quad [\text{kVAr}]$$
4. **Power Triangle Verification**: The relationship $S = \sqrt{P^2 + Q^2}$ is preserved across all calculations.

### 6.2 Energy Numerical Integration
- **Incremental Integration**: Discrete energy increments are integrated over consecutive timestamps:
  $$\Delta E_i = \left(\frac{P_{i-1} + P_i}{2}\right) \times \left(\frac{\Delta t_i}{3600}\right) \quad [\text{kWh}]$$
- **Sensor Gap Safeguard**: If elapsed time $\Delta t_i > 60.0\text{ seconds}$, the system treats the interval as communication downtime, crediting only a nominal $15.0\text{ s}$ interval to prevent false energy accumulation.
- **Duration Scaling**: Multi-day query windows scale integrated energy with duration hours ($P_{\text{avg}} \times \Delta t_{\text{hours}}$) when records do not span the entire requested period.

### 6.3 Faculty Load Profiles & Diurnal Modeling
`BUILDING_PROFILES` defines electrical baseline current, peak current, and power factor parameters for the 6 core complexes:
- **Mechanical (MME)**: Current $60.0–125.0\text{ A}$, $\text{PF } 0.82–0.89$ (heavy motor loads).
- **Electrical (EIE)**: Current $48.0–95.0\text{ A}$, $\text{PF } 0.88–0.94$ (computing labs, test benches).
- **Administration (ADMIN)**: Current $20.0–55.0\text{ A}$, $\text{PF } 0.90–0.95$ (HVAC and office equipment).
- **Diurnal Factor**: Current is modulated using a diurnal curve peaking during lecture/lab hours ($11:30–13:30$), tapering in the evening, and dropping to a $0.20$ baseline at night.

---

## 7. Electrical Alerts, Analytics & Tariff Cost Estimation

### 7.1 Alert Engine State Machine
`alertEngine.js` evaluates 8 electrical conditions:
1. `LOW_POWER_FACTOR`: $\text{PF} < 0.850$ (`WARNING`)
2. `LOW_VOLTAGE`: $V_L < 380.0\text{ V}$ (`CRITICAL`, $-5.0\%$ of nominal)
3. `HIGH_VOLTAGE`: $V_L > 420.0\text{ V}$ (`CRITICAL`, $+5.0\%$ of nominal)
4. `HIGH_FREQUENCY`: $f > 50.50\text{ Hz}$ (`WARNING`, $+1.0\%$ of nominal)
5. `LOW_FREQUENCY`: $f < 49.50\text{ Hz}$ (`WARNING`, $-1.0\%$ of nominal)
6. `HIGH_LOAD`: $P > 90\%$ of building rated capacity (`WARNING`)
7. `OVERLOAD`: $P > 100\%$ of building rated capacity (`CRITICAL`)
8. `SENSOR_OFFLINE`: Sensor silence $> 60.0\text{ s}$ (`WARNING`)

- **Duplicate Suppression**: Re-evaluations check for existing active alerts matching `(building_id, alert_type)`.
- **Auto-Clearing Logic**: When measured values return within safe bounds, `is_active` is toggled to `false` and stamped with `cleared_at`.

### 7.2 Historical Analytics & Load Factor
- **Coincident Campus Peak**: Constructed by standardizing telemetry into unified time buckets and computing synchronous total power:
  $$P_{\text{peak}} = \max\left(\text{campusPowerSeries}\right) \quad [\text{kW}]$$
- **University Average Demand**: Derived as the arithmetic mean of the synchronous series, with a strict boundary guard:
  $$P_{\text{avg}} = \min(\text{calculatedAverage}, P_{\text{peak}}) \quad [\text{kW}]$$
- **Load Factor ($\text{LF}$)**:
  $$\text{LF} = \frac{P_{\text{avg}}}{P_{\text{peak}}} \quad (0.0 \le \text{LF} \le 1.0)$$

### 7.3 Demonstration Two-Part Tariff Cost Model
$$\text{Total Cost} = C_{\text{energy}} + C_{\text{demand}} + C_{\text{fixed}} \quad [\text{LKR}]$$
Where:
- $C_{\text{energy}} = E_{\text{total}} \times 40.00\text{ LKR/kWh}$
- $C_{\text{demand}} = P_{\text{peak}} \times 1,200.00\text{ LKR/kW} \times \left(\frac{T_{\text{days}}}{30}\right)$
- $C_{\text{fixed}} = 3,000.00\text{ LKR} \times \left(\frac{T_{\text{days}}}{30}\right)$

---

## 8. Audit Reports, CSV Export & Formal Printable Layout

### 8.1 16-Parameter Engineering Audit
`analyticsService.js` calculates 16 engineering parameters across selected timeframes:
1. Total Energy ($\text{kWh}$)
2. Average Active Power ($\text{kW}$)
3. Peak Active Demand ($\text{kW}$)
4. Minimum Active Power ($\text{kW}$)
5. Average Apparent Power ($\text{kVA}$)
6. Average Reactive Power ($\text{kVAr}$)
7. Average Power Factor
8. Minimum Power Factor
9. Load Factor ($\%$)
10. Average Voltage ($\text{V}$)
11. Voltage Boundaries ($\text{Min / Max V}$)
12. Average Frequency ($\text{Hz}$)
13. After-Hours Energy ($\text{kWh}$ and $\%$)
14. Operating-Hours Energy ($\text{kWh}$)
15. Estimated Cost Breakdown ($\text{LKR}$)
16. Alert Breakdown (Warning and Critical counts)

### 8.2 Data Streaming & Print Stylesheets
- **CSV Telemetry Streaming (`GET /api/reports/export/csv`)**: Streams historical telemetry rows directly formatted to RFC 4180 specifications.
- **Printable Audit Report (`GET /reports/printable`)**: Renders `printable_report.html` with `@media print` CSS for browser PDF export.

---

## 9. Verification & Automated Testing Results

### 9.1 Verification Methodology
The system was verified across 8 automated test suites using Node's native test runner (`node:test` and `node:assert`):

```text
=====================================================================================
 MASTER AUTOMATED TEST EXECUTION SCORECARD (npm test)
=====================================================================================
 Suite Name        Checks  Status  Duration  Focus Area
-------------------------------------------------------------------------------------
 phase2.test.js      16    PASSED  1.08 s    Mongoose Schemas, Unique Indexes & Seeder
 phase3a.test.js     16    PASSED  1.12 s    Auth, Sessions, RBAC & CSRF Protection
 phase3b.test.js     31    PASSED  1.42 s    Building CRUD & Deletion Safeguards
 phase4.test.js      23    PASSED  0.80 s    Three-Phase Math & 8-Anomaly Alert Engine
 phase5.test.js      26    PASSED  1.21 s    Coincident Peak, Energy Scaling & Audit
 phase6a.test.js     25    PASSED  0.94 s    Nunjucks Templates & Route Access Control
 phase6b.test.js     25    PASSED  0.81 s    Live Dashboard Contract & DOM Controls
 phase6c.test.js     25    PASSED  0.85 s    Analytics, Alerts, Reports & Tariff APIs
-------------------------------------------------------------------------------------
 Total Tests: 187 / 187 Passed (100% Success Rate) | Total Duration: ~15 seconds
=====================================================================================
```

---

## 10. EE4207 Evaluation Criteria Alignment

| Evaluation Criteria | Target Weight | Implemented System Features | Verified Code References |
| :--- | :---: | :--- | :--- |
| **1. System Architecture & Tech Stack** | 10% | Decoupled client-server design; Node.js/Express backend; MongoDB Atlas persistence; vanilla HTML5/CSS3/JS. | [`server.js`](node_backend/server.js), [`app.js`](node_backend/app.js) |
| **2. Database Design & Integrity** | 15% | 6 collections, unique uppercase compound indexes, protected core buildings, dependency safeguards. | [`models/`](node_backend/models/), [`init_db.js`](node_backend/init_db.js) |
| **3. Core Building CRUD Functionality** | 20% | Create, Read, Update, and Safe Delete implementation with modal UI and atomic transactions. | [`routes/buildings.js`](node_backend/routes/buildings.js) |
| **4. Input Validation & Error Handling** | 10% | Regex code validation (`^[A-Z0-9_-]{2,10}$`), numeric capacity bounds ($0.1–10,000\text{ kW}$), error states. | [`phase3b.test.js`](node_backend/tests/phase3b.test.js) |
| **5. Authentication & Role Security (RBAC)**| 10% | Salted `bcrypt` password hashing, `requireAdmin` guards, separate Administrator and Viewer interfaces. | [`middleware/auth.js`](node_backend/middleware/auth.js) |
| **6. Web Security & CSRF Defense** | 10% | Origin validation, double-submit cookie CSRF defense, secure session attributes (`HttpOnly`, `SameSite: Lax`). | [`middleware/csrf.js`](node_backend/middleware/csrf.js) |
| **7. Electrical Modeling & Analytics** | 15% | AC power triangle ($S, P, Q$), 8-condition alert engine, coincident peak aggregation, two-part tariff calculations. | [`services/analyticsService.js`](node_backend/services/analyticsService.js) |
| **8. Reporting, CSV & Print View** | 5% | 16-parameter summary, raw CSV streaming export, printable audit report with `@media print` CSS. | [`routes/reports.js`](node_backend/routes/reports.js) |
| **9. Testing & Quality Assurance** | 5% | 8 automated test suites, 187 assertions passing (100%), isolated test database runner. | [`tests/`](node_backend/tests/) |

---

## 11. Real-World Limitations & Hardware Migration

1. **Physical Meter Instrumentation**: The prototype utilizes simulated telemetry. In physical substations, multi-function meters (e.g., Schneider PM5350 or Selec MFM384) with Current Transformers (CTs) and an edge gateway (Raspberry Pi / MOXA via Modbus-RTU over RS-485) would interface directly with the existing API.
2. **Phase Imbalance & Harmonics**: The mathematical model currently assumes balanced three-phase operation. Future iterations will capture per-phase voltages ($V_R, V_Y, V_B$), neutral currents, and Total Harmonic Distortion ($\text{THD}\%$).
3. **Automated Notification Dispatch**: Expanding alert notifications via SMS (Twilio) or SMTP for critical overload events.

---

## 12. Conclusion

The **Ruhuna Smart Energy System** delivers a robust, secure, and academically transparent energy monitoring and analytics platform for the Faculty of Engineering, University of Ruhuna. By integrating real-time telemetry supervision, AC electrical power modeling, automated anomaly alerting, institutional tariff estimation, and safeguarded facility CRUD management, the project satisfies the requirements of **EE4207: Web Application Development**. Complete test coverage (187 passing automated tests) verifies the reliability, mathematical accuracy, and security of the platform.
