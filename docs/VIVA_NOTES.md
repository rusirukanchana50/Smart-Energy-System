# University Viva Preparation Notes & Examiner Q&A Guide

## Smart Energy Monitoring and Analytics System
### Faculty of Engineering, University of Ruhuna (Hapugala, Galle)
**Course:** EE4207 — Web Application Development  
**Author:** K.A.W.R.K.B. Kohona (EG/2023/5685)  

---

## 1. Project Background & Engineering Rationale

### A. Problem Statement
University facilities consume significant electrical power across lecture halls, laboratory testbenches, machine shops, and server facilities. Traditional utility billing from the Ceylon Electricity Board (CEB) provides a single monthly aggregate invoice, offering:
- No departmental granularity
- No real-time power quality visibility
- No immediate notification of abnormal electrical conditions (undervoltage, overload, low power factor)

### B. Project Objective
To develop a centralized, full-stack Smart Energy Monitoring and Analytics platform engineered for the Faculty of Engineering, University of Ruhuna. The system delivers real-time three-phase telemetry, historical load-curve integration, automated electrical anomaly detection, two-part demonstration tariff modeling, after-hours usage tracking, and formal audit reporting.

### C. Institutional Utility for University of Ruhuna
1. **Departmental Accountability**: Identifies electrical consumption trends and coincident demand across individual faculties.
2. **Laboratory Equipment Protection**: Flags line voltage excursions ($< 380\text{ V}$ or $> 420\text{ V}$) to prevent damage to sensitive scientific instruments.
3. **CEB Tariff Optimization**: Highlights sub-statutory power factor ($\text{PF} < 0.85$) to avoid utility penalty surcharges and guide Automatic Power Factor Correction (APFC) capacitor bank sizing.
4. **Energy Conservation**: Quantifies energy consumed after hours ($17:00\text{ to }08:00$ and weekends) to identify idle baseload waste.

---

## 2. Technical Architecture & Data Flow

### D. System Architecture
The system uses a decoupled multi-tier architecture:
1. **Physical Simulation Layer** (`node_backend/simulator.js`): Generates three-phase electrical telemetry at $400\text{ V} / 50\text{ Hz}$ across all registered complexes.
2. **Persistence Layer** (MongoDB Atlas / Local MongoDB): Stores collections for buildings, telemetry readings, alerts, tariffs, faculty settings, and encrypted sessions (`connect-mongo`).
3. **Application & API Layer** (`node_backend/server.js`, `app.js`): Express.js backend handling session authentication, RBAC guards, double-submit cookie CSRF validation, analytics calculations, and REST APIs.
4. **Presentation Layer**: Dark-mode web interface (`HTML5`, `Vanilla CSS3`, `Nunjucks`, `Chart.js`, and vanilla `JavaScript`) with unified navigation across 6 views.

### E. Data Pipeline
$$\text{Simulator} \longrightarrow \text{MongoDB Collections} \longrightarrow \text{Express REST APIs} \longrightarrow \text{Browser Dashboard / Charts}$$
- Every 15 seconds, the simulator inserts new readings into MongoDB.
- The browser dashboard polls `/api/energy/live` every 5 seconds.
- Historical queries and reports pull data directly from MongoDB via Mongoose aggregation.

### F. Simulation Transparency
Deploying physical smart meters across university substations requires hardware procurement, Modbus cabling, Current Transformers (CTs), and planned power shutdowns. The mathematical simulator models realistic load profiles, grid noise, and electrical anomalies so the software, detection logic, and analytics can be developed and verified before hardware deployment. Every view explicitly displays the `SIMULATED` label to preserve scientific integrity.

### G. Physical Hardware Migration Path
Digital multi-function meters (e.g., Schneider PM5350 or Eastron SDM630) communicate via Modbus-RTU over RS-485 or Modbus-TCP. A lightweight edge gateway (e.g., Raspberry Pi or industrial edge controller) can query the meter registers and publish JSON telemetry to the existing Express REST API. The backend database schemas, analytical calculations, and frontend dashboards require no architectural redesign to transition to physical instrumentation.

---

## 3. Power Engineering Concepts

### H. Three-Phase Power Triangle: Active, Apparent, and Reactive Power
1. **Apparent Power ($S$, kVA)**:
   $$S = \frac{\sqrt{3} \times V_L \times I_L}{1000}$$
   The total vector power supplied by the grid. Transformers and switchgear must be sized to handle $S$.
2. **Active (Real) Power ($P$, kW)**:
   $$P = S \times \text{PF} = \frac{\sqrt{3} \times V_L \times I_L \times \cos\theta}{1000}$$
   The actual working power converted into thermal energy, lighting, computing, and mechanical shaft rotation.
3. **Reactive Power ($Q$, kVAr)**:
   $$Q = S \times \sqrt{1 - \text{PF}^2} = \sqrt{S^2 - P^2}$$
   The magnetizing power exchanged between inductive components (motor windings, transformers) and the AC grid. It performs no net work but causes current to flow and generates $I^2 R$ heat losses.

### I. Power Factor ($\text{PF} = \cos\theta$)
$$\text{PF} = \frac{P}{S} = \frac{\text{Active Power (kW)}}{\text{Apparent Power (kVA)}}$$
- **Optimal Range**: $\text{PF} \ge 0.90$.
- **Regulatory Threshold**: The Ceylon Electricity Board (CEB) industrial tariff imposes surcharges when average power factor falls below **$0.85$**.
- When $\text{PF} < 0.85$, larger line currents are required to deliver the same active power, causing higher transmission losses and voltage drops.

### J. Energy ($kWh$) Integration
Energy is the time integral of active power:
$$E = \int P(t) \, dt \approx \sum_{i=1}^{n} \left(\frac{P_{i-1} + P_i}{2}\right) \times \left(\frac{\Delta t_i}{3600}\right) \quad [\text{kWh}]$$
- The system uses **Riemann trapezoidal numerical integration** across consecutive timestamps.
- If the time delta $\Delta t_i > 60\text{ s}$ (indicating sensor downtime or an outage), the system caps $\Delta t$ at 15 seconds to avoid inflating energy calculations during missing-data gaps.

### K. Peak Demand ($kW$) & Load Factor ($\text{LF}$)
- **Coincident Campus Peak**: The absolute maximum coincident active power demand recorded across all university facilities.
- **Load Factor**:
  $$\text{LF} = \frac{P_{\text{avg}}}{P_{\text{peak}}} \quad (0.0 \le \text{LF} \le 1.0)$$
  Measures asset capacity utilization. A load factor close to $1.0$ indicates uniform, steady loading. A low load factor ($< 0.40$) indicates intermittent peak demand spikes, resulting in high maximum demand charges.

### L. Alert Detection State Machine
The backend evaluates 8 electrical conditions upon reading insertion:
1. Low PF: $\text{PF} < 0.850$ (`WARNING`)
2. Low Voltage: $V_L < 380.0\text{ V}$ (`CRITICAL`)
3. High Voltage: $V_L > 420.0\text{ V}$ (`CRITICAL`)
4. Low Frequency: $f < 49.50\text{ Hz}$ (`WARNING`)
5. High Frequency: $f > 50.50\text{ Hz}$ (`WARNING`)
6. High Load: $P > 90\%$ of rated capacity (`WARNING`)
7. Overload: $P > 100\%$ of rated capacity (`CRITICAL`)
8. Sensor Offline: No reading for $> 60.0\text{ s}$ (`WARNING`)

- **Duplicate Suppression**: Checks for existing active alerts matching that building and alert type before creating a new entry.
- **Auto-Clearing**: When parameters return to safe operating thresholds, `is_active` updates to `false` and records `cleared_at`.

### M. Two-Part Demonstration Tariff Formulation
$$\text{Estimated Total Cost} = \text{Energy Charge} + \text{Demand Charge} + \text{Fixed Service Charge}$$
- $\text{Energy Charge} = E_{\text{consumed}} \times 40.00\text{ LKR/kWh}$
- $\text{Demand Charge} = P_{\text{peak}} \times 1,200.00\text{ LKR/kW} \times \left(\frac{\text{Days}}{30}\right)$
- $\text{Fixed Charge} = 3,000.00\text{ LKR} \times \left(\frac{\text{Days}}{30}\right)$
- Clearly flagged with the disclaimer: *"Cost estimates use a configurable demonstration tariff model and are not an official CEB bill."*

---

## 4. Frequently Asked Viva Questions & Model Answers

### Architecture & Engineering
1. **Why Node.js + Express instead of Flask or Django?**
   *Answer*: Node.js provides an asynchronous, non-blocking I/O event loop that efficiently handles continuous telemetry ingestion and concurrent 5-second polling requests without thread starvation. Express provides a minimalist, robust routing pipeline with custom middleware for RBAC and CSRF defense.
2. **Why MongoDB Atlas instead of SQLite or MySQL?**
   *Answer*: Telemetry data naturally aligns with document schemas containing nested electrical parameters. MongoDB Atlas offers managed cloud availability, replica sets, and flexible time-series indexing, while Mongoose provides strict schema validation and relationship integrity.
3. **Is this real University of Ruhuna energy data?**
   *Answer*: No, all measurements are generated by a three-phase mathematical simulation engine (`node_backend/simulator.js`) and clearly marked as `SIMULATED` across the UI, API responses, CSV exports, and reports to maintain academic integrity.
4. **How would physical meters connect to this system?**
   *Answer*: Digital multi-function meters (e.g., Schneider PM5350) communicate via Modbus-RTU over RS-485. An edge gateway (e.g., Raspberry Pi or ESP32) queries the registers and posts readings to our REST API endpoint. The database schema, calculation engine, and frontend dashboards require no modifications.
5. **Does the frontend generate any random numbers?**
   *Answer*: No. All electrical measurements, totals, averages, and alert states are computed server-side by Express from MongoDB database records. The frontend strictly handles display, formatting, and Chart.js rendering.

### Electrical Formulas & Analytics
6. **How is active power calculated in a balanced three-phase system?**
   *Answer*: $P = \sqrt{3} \times V_L \times I_L \times \text{PF} / 1000$ (in kW), where $V_L$ is line-to-line RMS voltage, $I_L$ is line current, and $\text{PF}$ is displacement power factor.
7. **What is the difference between kW, kVA, and kVAr?**
   *Answer*: kW is active power performing useful work; kVA is apparent power representing total vector capacity supplied by the grid; kVAr is reactive power sustaining magnetic fields in inductive loads (motors, transformers).
8. **Why are voltage limits set to 380 V and 420 V?**
   *Answer*: They represent a $\pm 5\%$ statutory tolerance around nominal $400\text{ V Line-to-Line}$. Voltages below 380 V risk motor overheating and excessive current; voltages above 420 V cause transformer saturation and insulation stress.
9. **How do you handle missing data gaps during energy integration?**
   *Answer*: If consecutive readings are separated by more than 60 seconds (due to communication loss or outage), $\Delta t$ is capped at a nominal 15 seconds to prevent falsely integrating energy across downtime.
10. **What is Load Factor and why is it important?**
    *Answer*: Load Factor is $\frac{P_{\text{avg}}}{P_{\text{peak}}}$. It measures load curve uniformity. A low load factor ($< 0.40$) indicates intermittent peak surges with low average consumption, leading to higher maximum demand charges relative to consumed kWh.

### Security, Roles & Testing
11. **How are passwords stored securely?**
    *Answer*: Passwords are encrypted using `bcryptjs` with salt rounds. Plain-text passwords are never stored or logged.
12. **What role-based access control (RBAC) levels exist?**
    *Answer*: Two roles: `Administrator` and `Viewer`. Administrators have full privileges (facility CRUD, alert acknowledgment, tariff updates); Viewers have read-only access.
13. **How does the system prevent Cross-Site Request Forgery (CSRF)?**
    *Answer*: A double-submit cookie pattern verifies incoming state-changing requests against trusted origin headers and compares the request `x-csrf-token` against the cryptographic token in the user's session.
14. **How many automated tests exist and what do they verify?**
    *Answer*: 187 automated tests across 8 test suites verifying Mongoose schemas, authentication, building CRUD safeguards, three-phase math, alert state machines, coincident peak demand, tariff scaling, and frontend integration. All 187 tests pass with 100% success.
