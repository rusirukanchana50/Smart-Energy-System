# University of Ruhuna — Smart Energy Monitoring & Analytics System
## Viva Demonstration Guide (`DEMO_MODE.md`)
**Course:** EE4207 — Web Application Development  
**Author:** K.A.W.R.K.B. Kohona (EG/2023/5685)  

---

## 1. Evaluation Objectives

When demonstrating the system for evaluation in **EE4207: Web Application Development**, verify that the following engineering requirements are met:
1. **Three-Phase Electrical Calculations**: Compliance with standard AC power formulas ($S = \sqrt{3} V_L I_L$, $P = S \times \text{PF}$, $Q = \sqrt{S^2 - P^2}$).
2. **Asynchronous Architecture**: Dual-process architecture where `node_backend/simulator.js` writes telemetry and `node_backend/server.js` serves the portal without database contention.
3. **Role-Based Access Control (RBAC)**: Strict segregation between Administrator and Viewer roles.
4. **Energy Cost & Institutional Tariff Modeling**: Two-part industrial/commercial tariff calculations with after-hours consumption detection.
5. **Analytical Insights & Engineering Observations**: Automated evaluation of high demand peaks, low power factor, and safety threshold excursions.
6. **Academic Transparency**: Clear disclosure that telemetry is simulated and tariff rates represent an educational demonstration model.

---

## 2. Recommended 10-Minute Demonstration Timeline

```text
[00:00 - 01:30]  1. Terminal Setup & Server Boot
[01:30 - 02:45]  2. Authentication & Role-Based Access Control
[02:45 - 04:15]  3. Live Substation Monitoring Dashboard & 5s Telemetry Polling
[04:15 - 05:45]  4. Historical Analytics, Coincident Peak & Load Factor
[05:45 - 07:15]  5. Electrical Anomaly Detection & Anomaly Injection Testing
[07:15 - 08:30]  6. Energy Cost & Efficiency Rankings (Two-Part Tariff)
[08:30 - 09:30]  7. Comprehensive Audit Reports & Official PDF Print Layout
[09:30 - 10:00]  8. Automated Test Scorecard & Technical Q&A
```

---

## 3. Step-by-Step Execution Guide

### Step 1: Terminal Setup & Startup (2 Terminals)
Open **two terminals** in the project directory:

#### Terminal 1: Start Express Application Server
```powershell
npm start
```
- **Execution**: Express connects to MongoDB (`ruhuna_energy`), initializes session storage via `connect-mongo`, provisions default users, and starts listening on port 5000.

#### Terminal 2: Start Telemetry Simulator
```powershell
npm run simulator
```
- **Execution**: The simulator retrieves all registered facilities from MongoDB and emits balanced 3-phase readings every 15 seconds.

---

### Step 2: Authentication & RBAC Verification
1. Open web browser to:
   ```text
   http://localhost:5000
   ```
2. Log in using the **Viewer** account:
   - **Email**: `viewer@eng.ruh.ac.lk`
   - **Password**: `Viewer@1234`
3. Point out the **Viewer** role badge in the navigation bar.

---

### Step 3: Live Monitoring Dashboard (`/dashboard`)
1. Show live telemetry across the campus facilities:
   - Line Voltage ($V_L \approx 400.0\text{ V}$)
   - Line Current ($I_L$)
   - Active Power ($P\text{ kW}$)
   - Apparent Power ($S\text{ kVA}$)
   - Reactive Power ($Q\text{ kVAr}$)
   - Power Factor ($\text{PF}$)
   - Grid Frequency ($f \approx 50.0\text{ Hz}$)
2. Observe asynchronous 5-second polling updates without page reloads.

---

### Step 4: Historical Analytics & Load Factor (`/analytics`)
1. Select preset: **Last 24 Hours**.
2. Select individual facility or **University Facilities Comparison (All Registered Buildings)**.
3. Review KPIs: Total Energy ($kWh$), Peak Demand ($kW$), Average Demand ($kW$), Load Factor ($\text{LF} = P_{\text{avg}} / P_{\text{peak}}$).
4. Review coincident university peak derivation and comparative charts.

---

### Step 5: Controlled Anomaly Injection Testing (`/alerts`)
1. Navigate to `/alerts`.
2. In a separate terminal, inject controlled test anomalies:
   ```powershell
   # Inject Low Power Factor on Building EIE:
   node node_backend/simulator.js --once --anomaly LOW_PF --building-id EIE

   # Inject Severe Overload on Building CEE:
   node node_backend/simulator.js --once --anomaly OVERLOAD --building-id CEE
   ```
3. Verify alert indicators appear on the dashboard and alerts log.
4. Verify duplicate suppression by re-executing the command.
5. Restore normal readings:
   ```powershell
   node node_backend/simulator.js --once
   ```
6. Verify alerts automatically transition to `Cleared`.

---

### Step 6: Cost & Efficiency Analytics (`/cost-efficiency`)
1. Review the demonstration two-part tariff calculation.
2. Note the educational disclaimer banner.
3. Review after-hours consumption metrics and departmental rankings.

---

### Step 7: Reports, CSV Export & Printable Layout (`/reports`)
1. Generate audit report for **Last 24 Hours**.
2. Click **Print / Save PDF** to demonstrate print-optimized CSS layout.
3. Click **Export CSV** to demonstrate raw telemetry streaming.

---

### Step 8: Automated Test Verification
In a terminal, execute the master test suite:
```powershell
npm test
```
- **Expected Verification Results**:
  - Total Test Suites: **8**
  - Total Test Cases: **187**
  - Result: **187 Passed (100% Success)**
  - Execution Time: **~15 seconds**

---

## 4. Viva Technical Questions & Model Answers

### Q1: Are these readings originating from physical meters installed in the faculty?
> **Answer**: No. In this prototype, readings are generated by a three-phase software simulator (`node_backend/simulator.js`) using balanced $400\text{ V} / 50\text{ Hz}$ formulas and realistic diurnal university load curves. Every page, API endpoint, CSV export, and printed report explicitly discloses the `SIMULATED` label to maintain complete academic integrity.

### Q2: Why did you use MongoDB Atlas instead of SQLite or PostgreSQL?
> **Answer**: Electrical telemetry records contain nested parameters that map cleanly to document schemas. MongoDB Atlas provides managed high availability, cloud persistence, and flexible time-series compound indexing, while Mongoose enforces schema validation and relationship integrity.

### Q3: How is electricity cost calculated? Is this an official CEB bill?
> **Answer**: Cost estimates use a configurable two-part demonstration institutional tariff model (Energy Rate: LKR 40.00/kWh, Maximum Demand: LKR 1,200.00/kW, Fixed Charge: LKR 3,000.00/month). It serves as an engineering estimation tool to demonstrate the financial impact of peak demand and low power factor, clearly noted as a non-official estimate.

### Q4: How is Load Factor defined and what does it tell an electrical engineer?
> **Answer**: Load Factor is defined as the ratio of Average Active Power to Peak Active Power over a given interval:
> $$\text{Load Factor} = \frac{P_{\text{avg}}}{P_{\text{peak}}}$$
> A load factor close to 1.0 indicates steady and efficient utilization of substation transformer capacity. A low load factor means high peak demand with low average consumption, resulting in high utility maximum demand charges.
