# System Architecture

## Faculty of Engineering, University of Ruhuna
### Smart Energy Monitoring and Analytics System (Node.js + Express + MongoDB)

---

## 1. High-Level Architecture Overview

The system is designed as a multi-tier, SCADA-inspired IoT telemetry and analytics platform for monitoring electrical energy consumption, power quality, and abnormal grid conditions across the campus facilities of the Faculty of Engineering, University of Ruhuna (Hapugala, Galle).

```
+-----------------------------------------------------------------------------------+
|                        PHYSICAL / SIMULATION LAYER                                |
|  - 3-Phase Electrical Physics Engine (node_backend/simulator.js)                  |
|  - 400 V Line-to-Line Balanced 50 Hz Grid Model with Gaussian Noise               |
|  - Time-of-Day Faculty Load Profiles (Baseline, Working Hours, Lunch, Night)      |
|  - Anomaly Injection Engine (Low PF, Overload, Voltage Excursions, Disconnection) |
+-----------------------------------------------------------------------------------+
                                       │
                                       │ (15-second Sampling / Mongoose Inserts)
                                       ▼
+-----------------------------------------------------------------------------------+
|                           STORAGE & PERSISTENCE LAYER                             |
|  - MongoDB Document Database (ruhuna_energy) via Mongoose ODM                    |
|  - Collections: buildings, users, energy_readings, alerts, tariffs, settings       |
|  - True Riemann Trapezoidal Integration & State-Machine Alert Tracking            |
|  - Server-Side Encrypted Session Storage via connect-mongo                        |
+-----------------------------------------------------------------------------------+
                                       │
                                       │ (Mongoose Documents & Aggregation)
                                       ▼
+-----------------------------------------------------------------------------------+
|                           APPLICATION & API LAYER                                 |
|  - Node.js Express REST Backend (node_backend/server.js, app.js)                  |
|  - Cryptographic Session Authentication (bcryptjs, express-session)               |
|  - Role-Based Access Control (RBAC): Administrator vs. Viewer                     |
|  - Double-Submit Cookie CSRF Defense (middleware/csrf.js)                         |
|  - Anomaly Detection Engine (Auto-Clearing State Machine: alertEngine.js)         |
|  - Demonstration Institutional Tariff & Cost Engine                               |
|  - Reporting Engine (16-Parameter Audit, CSV Telemetry Streaming, Print HTML)     |
+-----------------------------------------------------------------------------------+
                                       │
                                       │ (JSON REST Endpoints & Nunjucks Server Render)
                                       ▼
+-----------------------------------------------------------------------------------+
|                           PRESENTATION / UI LAYER                                 |
|  - University of Ruhuna Branded Dark Engineering Portal (Outfit & JetBrains Mono) |
|  - Standardized 6 Navigation Views:                                               |
|      1. Live Monitor (/dashboard)                                                 |
|      2. Historical Analytics (/analytics)                                         |
|      3. Electrical Alerts (/alerts)                                               |
|      4. Cost & Efficiency (/cost-efficiency)                                      |
|      5. Audit Reports (/reports)                                                  |
|      6. Printable Audit View (/reports/printable)                                 |
|  - Interactive Chart.js Visualizations (Sliding-window Real-time & Bar Charts)    |
|  - 5-Second Real-Time Asynchronous Polling & CSV Downloader                      |
+-----------------------------------------------------------------------------------+
```

---

## 2. Core Functional Layers

### 2.1 Simulation & Telemetry Layer
- **Balanced 400V Three-Phase AC Generator**: Calculates RMS line-to-line voltage, line current, power factor, line frequency, apparent power ($S$), active power ($P$), and reactive power ($Q$).
- **Realistic Diurnal Profiles**: Applies diurnal multiplier curves matching faculty operational hours (08:00–17:00 active academic load vs baseline standby night load).
- **Dynamic Facility Inclusion**: Refreshes building registry from MongoDB dynamically on every cycle.

### 2.2 Storage Layer (MongoDB)
- Schema definitions enforce validation rules, unique indexes, uppercase codes, and numeric ranges.
- Compounds indexes optimize time-series queries and dashboard retrieval.
- Session storage is offloaded to MongoDB using `connect-mongo`.

### 2.3 Web Application Layer (Express)
- Asynchronous routing pipeline with modular routers for authentication, buildings, telemetry, alerts, and reports.
- Comprehensive input sanitization and HTTP status code discipline.
- Double-submit CSRF cookie token protection on state-changing requests.

### 2.4 Presentation Layer (Vanilla CSS & JS + Chart.js)
- Responsive dark-mode dashboard with zero external CSS framework overhead.
- Interactive Chart.js graphs updated asynchronously every 5 seconds.
- Official university-branded printable audit report with `@media print` layout.
