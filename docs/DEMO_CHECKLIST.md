# Live Demonstration Checklist for University Evaluation
## University of Ruhuna • Faculty of Engineering
### Smart Energy Monitoring and Analytics System
**Course:** EE4207 — Web Application Development  
**Author:** K.A.W.R.K.B. Kohona (EG/2023/5685)  

---

## 1. Pre-Demonstration Setup (Terminal 1 & Terminal 2)

### Terminal 1: Launch Web Application Server
```powershell
npm start
```
- Verify console output:
  - `[MongoDB] Successfully connected to: mongodb://127.0.0.1:27017/ruhuna_energy`
  - `[Express] Server running on http://localhost:5000`

### Terminal 2: Launch Real-Time Telemetry Simulator
```powershell
npm run simulator
```
- Verify the simulator begins logging 3-phase readings every 15 seconds for all campus facilities.
- Point out that readings are explicitly labeled `[SIMULATED]`.

---

## 2. Structured Demonstration Sequence

### Step 1: Authentication & Role-Based Access Control (RBAC)
1. Open a browser window to `http://localhost:5000/`.
2. Observe automatic redirection to `/login` with Faculty of Engineering branding.
3. Attempt unauthenticated access to `/dashboard` or `/api/energy/live`:
   - Point out that protected routes reject unauthorized requests (`HTTP 401 Unauthorized` for API clients, login redirect for browsers).
4. Log in using the **Viewer** test account:
   - Email: `viewer@eng.ruh.ac.lk`
   - Password: `Viewer@1234`
5. Note the **Viewer** role tag in the top navigation bar.

---

### Step 2: Live Substation Monitoring Dashboard (`/dashboard`)
1. Present the registered facility telemetry cards:
   - Civil and Environmental Engineering (`CEE`)
   - Electrical and Information Engineering (`EIE`)
   - Mechanical and Manufacturing Engineering (`MME`)
   - Marine Engineering and Naval Architecture (`MENA`)
   - Administration Building (`ADMIN`)
   - Auditorium (`AUDI`)
2. Explain the parameters on each card:
   - Line Voltage ($V_L \approx 400.0\text{ V}$)
   - Line Current ($I_L\text{ A}$)
   - Displacement Power Factor ($\text{PF}$)
   - Active Power ($P\text{ kW}$)
   - Apparent Power ($S\text{ kVA}$)
   - Reactive Power ($Q\text{ kVAr}$)
   - Grid Frequency ($f \approx 50.0\text{ Hz}$)
   - Cumulative Energy ($kWh$)
3. Demonstrate that every 5 seconds, values update asynchronously via REST API polling without full page refreshes.
4. Highlight Chart.js real-time sliding line graphs.

---

### Step 3: Historical Analytics & Load Factor Analysis (`/analytics`)
1. Navigate to the **Historical Analytics** tab.
2. Select a quick preset: **Last 24 Hours** or **Today**.
3. Select an individual building (e.g., `EIE` or `CEE`).
4. Review the operational KPI cards:
   - True Integrated Energy ($kWh$)
   - Peak Demand ($kW$)
   - Average Active Power ($kW$)
   - Load Factor ($\text{LF} = \frac{P_{\text{avg}}}{P_{\text{peak}}}$)
   - Average Power Factor
5. Select **University Facilities Comparison (All Registered Buildings)**:
   - Review university aggregated metrics (University Total Energy, Peak Demand, Average Demand, Load Factor).
   - Review the comparative bar charts and detailed facilities table.

---

### Step 4: Electrical Anomaly Detection & Testing (`/alerts`)
1. Navigate to the **Electrical Alerts** tab.
2. In a separate terminal, trigger controlled anomalies:
   ```powershell
   # Low Power Factor on EIE:
   node node_backend/simulator.js --once --anomaly LOW_PF --building-id EIE

   # Feeder Overload on CEE:
   node node_backend/simulator.js --once --anomaly OVERLOAD --building-id CEE

   # Undervoltage on MENA:
   node node_backend/simulator.js --once --anomaly LOW_VOLTAGE --building-id MENA
   ```
3. Return to the browser:
   - On `/dashboard`: Show pulsating alert indicators and status borders.
   - On the navigation bar: Show updated active alert counter.
   - On `/alerts`: Review recorded entries showing measured values, thresholds, and severity badges (`CRITICAL`, `WARNING`).
4. Demonstrate Duplicate Suppression:
   - Re-run the anomaly command: verify no redundant alert records are generated while the condition remains active.
5. Demonstrate Auto-Clearing:
   ```powershell
   # Normal conditions:
   node node_backend/simulator.js --once
   ```
   - Verify active alerts transition to `Cleared` with recorded `cleared_at` timestamps.
6. Demonstrate Role Security on Alert Acknowledgment:
   - Under `viewer@eng.ruh.ac.lk`, verify acknowledgment controls are disabled.
   - Log out and authenticate as `admin@eng.ruh.ac.lk` / `Admin@1234`.
   - Click **Acknowledge**: confirm the alert is updated with administrator identity and acknowledgment timestamp.

---

### Step 5: Cost & Efficiency Analytics (`/cost-efficiency`)
1. Navigate to the **Cost & Efficiency** tab.
2. Note the **Demonstration Tariff Disclaimer Banner**:
   - *"Cost estimates use a configurable demonstration tariff model and are not an official CEB bill."*
3. Review the Two-Part Institutional Tariff Breakdown:
   - $\text{Energy Charge} = \text{Energy (kWh)} \times 40.00\text{ LKR}$
   - $\text{Demand Charge} = \text{Peak Demand (kW)} \times 1,200.00\text{ LKR} \times \left(\frac{\text{Days}}{30}\right)$
   - $\text{Fixed Charge} = 3,000.00\text{ LKR} \times \left(\frac{\text{Days}}{30}\right)$
4. Explain After-Hours Energy:
   - Demonstrate how consumption outside standard faculty operating hours (08:00–17:00 weekdays and weekends) is isolated.
5. Review Departmental Efficiency Rankings and financial comparison bar charts.

---

### Step 6: Audit Reports, CSV Export & Printable PDF Layout (`/reports`)
1. Navigate to the **Reports** tab.
2. Select **Last 24 Hours** and click **Generate**:
   - Review the 16-parameter KPI summary grid.
   - Point out evidence-based engineering observations.
3. Click **Print / Save PDF**:
   - A dedicated view opens with official University of Ruhuna branding and print layout.
   - Open browser print preview (`Ctrl+P`) to demonstrate clean A4 formatting.
4. Click **Export CSV**:
   - Inspect downloaded `.csv` file in Excel or text editor.
   - Verify columns: timestamp, voltage, current, PF, frequency, powers, and `SIMULATED` markers.
5. Demonstrate Administrator Tariff Adjustments:
   - While authenticated as `admin@eng.ruh.ac.lk`, modify tariff rates and save.
   - Verify cost estimates update dynamically from MongoDB.

---

## 3. Concluding Remarks for Examiners
- All telemetry readings are stored and calculated server-side in MongoDB.
- Mathematical energy integrations use numerical trapezoidal integration across contiguous timestamps with missing-data gap protection.
- The decoupled architecture allows physical multi-function meters with Modbus-RTU / MQTT gateways to replace `simulator.js` without rewriting backend services or user portals.
