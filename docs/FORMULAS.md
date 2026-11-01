# University of Ruhuna — Faculty of Engineering
## Electrical Engineering Mathematical Formulations (`FORMULAS.md`)
**Course:** EE4207 — Web Application Development  
**Author:** K.A.W.R.K.B. Kohona (EG/2023/5685)  

This document details the mathematical models, electrical formulas, derivations, and units implemented across the **Smart Energy Monitoring and Analytics System** (Hapugala, Galle).

---

## 1. Apparent Power ($S$)

### Definition
Apparent power represents the total vector power flowing in an alternating current (AC) circuit, comprising both energy-consuming active power and stored reactive power. In a balanced three-phase AC power system, apparent power is determined from the root-mean-square (RMS) line-to-line voltage ($V_L$) and line current ($I_L$).

### Formula
$$S = \sqrt{3} \times V_L \times I_L \quad [\text{VA}]$$

To express in kilovolt-amperes ($\text{kVA}$):
$$S_{\text{kVA}} = \frac{\sqrt{3} \times V_L \times I_L}{1000} \quad [\text{kVA}]$$

### Parameters & Units
| Symbol | Parameter | Nominal System Value | Unit |
|---|---|---|---|
| $V_L$ | RMS Line-to-Line Voltage | $400.0\text{ V}$ | Volts ($\text{V}$) |
| $I_L$ | RMS Line Current | Facility-dependent ($10.0\text{ A} - 125.0\text{ A}$) | Amperes ($\text{A}$) |
| $\sqrt{3}$ | Three-phase geometric constant | $\approx 1.7320508$ | Dimensionless |
| $S$ | Apparent Power | Monitored | Kilovolt-Amperes ($\text{kVA}$) |

### Implementation Reference
- `node_backend/simulator.js`: `calculateElectricalParameters()`
- `node_backend/tests/phase4.test.js`: Three-phase AC formula tests

---

## 2. Active Power ($P$)

### Definition
Active power (also termed real power or true power) is the actual electrical power converted into useful work (such as mechanical torque, thermal energy, computing power, or lighting) in an AC circuit.

### Formula
$$P = S \times \cos(\theta) = \sqrt{3} \times V_L \times I_L \times \text{PF} \quad [\text{W}]$$

To express in kilowatts ($\text{kW}$):
$$P_{\text{kW}} = \frac{\sqrt{3} \times V_L \times I_L \times \text{PF}}{1000} = S_{\text{kVA}} \times \text{PF} \quad [\text{kW}]$$

### Parameters & Units
| Symbol | Parameter | Typical Range | Unit |
|---|---|---|---|
| $P$ | Active (Real) Power | $10.0 - 90.0$ | Kilowatts ($\text{kW}$) |
| $\text{PF}$ | Displacement Power Factor ($\cos\theta$) | $0.80 - 0.95$ | Dimensionless |
| $\theta$ | Phase angle between voltage and current | $18^\circ - 36^\circ$ | Degrees ($^\circ$) |

### Implementation Reference
- `node_backend/simulator.js`: Line calculations
- `node_backend/services/analyticsService.js`: `calculateBuildingAnalytics()`, `getEnergyAnalytics()`

---

## 3. Reactive Power ($Q$)

### Definition
Reactive power represents the electrical power oscillating between the source and inductive or capacitive magnetic/electric fields. In university engineering facilities, reactive power is primarily drawn by inductive motor loads (pumps, compressors, machining tools, ventilation fans) and transformer windings.

### Formula
Using the trigonometric identity $\sin(\theta) = \sqrt{1 - \cos^2(\theta)} = \sqrt{1 - \text{PF}^2}$:

$$Q = S \times \sin(\theta) = S \times \sqrt{1 - \text{PF}^2} \quad [\text{VAr}]$$

To express in kilovolt-amperes reactive ($\text{kVAr}$):
$$Q_{\text{kVAr}} = S_{\text{kVA}} \times \sqrt{1 - \text{PF}^2} \quad [\text{kVAr}]$$

### Power Triangle Validation
The fundamental AC power triangle relationship is rigorously enforced:
$$S^2 = P^2 + Q^2 \implies S = \sqrt{P^2 + Q^2}$$

### Parameters & Units
| Symbol | Parameter | Meaning | Unit |
|---|---|---|---|
| $Q$ | Reactive Power | Magnetic field sustaining power | Kilovolt-Amperes Reactive ($\text{kVAr}$) |
| $S$ | Apparent Power | Vector sum of active and reactive power | $\text{kVA}$ |
| $P$ | Active Power | Useful working power | $\text{kW}$ |

### Implementation Reference
- `node_backend/simulator.js`: `calculateElectricalParameters()`
- `node_backend/tests/phase4.test.js`: Power triangle assertions

---

## 4. Power Factor ($\text{PF}$)

### Definition
The power factor is the ratio of real active power to apparent power. It is an index of how effectively electrical current is being converted into useful work output.

### Formula
$$\text{PF} = \frac{P}{S} = \cos(\theta) \quad [\text{Dimensionless, } 0.0 \le \text{PF} \le 1.0]$$

For a collection of $N$ discrete historical telemetry samples:
$$\text{PF}_{\text{avg}} = \frac{1}{N} \sum_{i=1}^{N} \text{PF}_i$$

### Utility Penalty & Engineering Significance
- **Optimal Range**: $\text{PF} \ge 0.90$
- **Regulatory Threshold**: The Ceylon Electricity Board (CEB) industrial tariff imposes surcharges when average power factor falls below **$0.850$**.
- When $\text{PF} < 0.850$, larger line currents are required to deliver the same active power, causing higher $I^2 R$ transmission losses, voltage drops, and transformer thermal stress.
- The system automatically triggers a `LOW_POWER_FACTOR` **WARNING** alert whenever $\text{PF} < 0.850$.

### Implementation Reference
- `node_backend/services/alertEngine.js`: `evaluateElectricalAlerts()`
- `node_backend/services/analyticsService.js`: `calculateBuildingAnalytics()`

---

## 5. Electrical Energy Consumption ($E$)

### Definition
Energy is the integral of active power over elapsed time. In the monitoring system, energy consumption is computed incrementally based on discrete timestamp intervals between consecutive telemetry readings.

### Continuous Time Integral
$$E = \int_{t_1}^{t_2} P(t) \, dt \quad [\text{kWh}]$$

### Discrete Trapezoidal Numerical Integration
For discrete readings $(t_{i-1}, P_{i-1})$ and $(t_i, P_i)$ separated by elapsed time $\Delta t_i = t_i - t_{i-1}$ seconds:

$$\Delta E_i = \left( \frac{P_{i-1} + P_i}{2} \right) \times \frac{\Delta t_i}{3600} \quad [\text{kWh}]$$

$$E_{\text{total}} = \sum_{i=1}^{N} \Delta E_i \quad [\text{kWh}]$$

### Discontinuous Telemetry & Missing Sensor Gap Protection
If sensor transmission is interrupted such that:
$$\Delta t_i > \Delta t_{\text{max}} \quad (\Delta t_{\text{max}} = 60.0\text{ seconds})$$

The system assumes telemetry communication was lost. In order to prevent false energy inflation across downtime:
$$\Delta E_i = P_i \times \frac{\Delta t_{\text{nominal}}}{3600} \quad (\Delta t_{\text{nominal}} = 15.0\text{ seconds})$$

### Implementation Reference
- `node_backend/simulator.js`: `calculateCumulativeEnergy()`
- `node_backend/services/analyticsService.js`: `calculateBuildingAnalytics()`
- `node_backend/tests/phase5.test.js`: Trapezoidal integration validation

---

## 6. Peak Demand ($P_{\text{peak}}$) & Campus Coincident Peak

### Definition
Peak active demand is the maximum active electrical power recorded across the queried monitoring window.

### Coincident University Peak Formula
For a campus-wide time series across standardized time buckets:
$$P_{\text{peak, university}} = \max_{t} \left( \sum_{k=1}^{M} P_{k}(t) \right) \quad [\text{kW}]$$

### University Average Demand Safeguard
Average demand is the arithmetic mean across that same synchronous timeline:
$$P_{\text{avg, university}} = \frac{1}{T} \sum_{t=1}^{T} P_{\text{campus}}(t) \quad [\text{kW}]$$
Enforced with a strict mathematical safeguard:
$$P_{\text{avg, university}} = \min(P_{\text{avg, calculated}}, P_{\text{peak, university}})$$

### Implementation Reference
- `node_backend/services/analyticsService.js`: `getCampusSynchronizedMetrics()`, `getEnergyAnalytics()`

---

## 7. Load Factor ($\text{LF}$)

### Definition
Load factor is the dimensionless ratio of average active power consumption over a specific period to the peak demand occurring in that same period.

### Formula
$$\text{LF} = \frac{P_{\text{avg}}}{P_{\text{peak}}} \quad [0.0 \le \text{LF} \le 1.0]$$

Expressed as a percentage:
$$\text{LF}_{\%} = \left( \frac{P_{\text{avg}}}{P_{\text{peak}}} \right) \times 100\%$$

### Interpretation
| Load Factor | Characterization | Operational Implication |
|---|---|---|
| **$\text{LF} \ge 0.85$** | **Optimal** | Steady, consistent load utilization; high return on infrastructure capacity. |
| **$0.60 \le \text{LF} < 0.85$** | **Moderate** | Standard academic cycle with clear daytime lecture/lab peaks. |
| **$\text{LF} < 0.60$** | **Low / Peaky** | High peak demand spikes compared to average utilization; high demand charges relative to consumed kWh. |

### Implementation Reference
- `node_backend/services/analyticsService.js`: `calculateBuildingAnalytics()`
- `node_backend/tests/phase5.test.js`: Bounded $[0.0, 1.0]$ tests

---

## 8. Estimated Electricity Cost (Two-Part Institutional Tariff)

### Model Structure
$$\text{Total Cost} = C_{\text{energy}} + C_{\text{demand}} + C_{\text{fixed}} \quad [\text{LKR}]$$

### 1. Energy Charge ($C_{\text{energy}}$)
$$C_{\text{energy}} = E_{\text{total}} \times R_{\text{energy}} \quad [\text{LKR}]$$
- Default Rate: $R_{\text{energy}} = \text{Rs. } 40.00 \text{ per kWh}$

### 2. Maximum Demand Charge ($C_{\text{demand}}$)
Based on peak active power demand ($P_{\text{peak}}$), scaled proportionally to the queried window relative to a standard 30-day utility cycle ($T_{\text{norm}} = 30 \text{ days}$):
$$C_{\text{demand}} = P_{\text{peak}} \times R_{\text{demand}} \times \left( \frac{T_{\text{duration}}}{T_{\text{norm}}} \right) \quad [\text{LKR}]$$
- Default Rate: $R_{\text{demand}} = \text{Rs. } 1,200.00 \text{ per kW monthly peak}$

### 3. Customer Fixed Service Charge ($C_{\text{fixed}}$)
$$C_{\text{fixed}} = R_{\text{fixed}} \times \left( \frac{T_{\text{duration}}}{T_{\text{norm}}} \right) \quad [\text{LKR}]$$
- Default Fixed Rate: $R_{\text{fixed}} = \text{Rs. } 3,000.00 \text{ per month}$

### Implementation Reference
- `node_backend/services/analyticsService.js`: `calculateBuildingCost()`
- `node_backend/tests/phase5.test.js`: Pro-rata scaling tests
