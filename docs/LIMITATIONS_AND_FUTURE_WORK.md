# University of Ruhuna — Faculty of Engineering
## System Limitations and Future Work Roadmap (`LIMITATIONS_AND_FUTURE_WORK.md`)
**Course:** EE4207 — Web Application Development  
**Author:** K.A.W.R.K.B. Kohona (EG/2023/5685)  

This document evaluates the technical boundaries of the **University of Ruhuna Smart Energy Monitoring and Analytics System** prototype and outlines the engineering roadmap for transitioning the software into a physical, hardware-integrated campus smart grid infrastructure.

---

## 1. Prototype Limitations

While the software architecture implements three-phase power mathematics, real-time telemetry streaming, anomaly detection state machines, and institutional tariff calculations, several boundaries exist in the prototype:

### 1.1 Simulated Electrical Telemetry
- **Status**: All electrical measurements (Line Voltage $V_L$, Line Current $I_L$, Power Factor $\text{PF}$, Active Power $P$, Reactive Power $Q$, and Frequency $f$) are generated in software by `node_backend/simulator.js`.
- **Constraint**: The readings follow mathematical equations with realistic diurnal academic patterns, but do not capture real-world electrical transients, noise, voltage sags, switching surges, or real phase imbalance.
- **Academic Disclosure**: Every page, API endpoint, CSV telemetry export, and printable audit report explicitly labels the data as **`SIMULATED`** to preserve scientific and academic integrity.

### 1.2 No Physical Smart Meters on University Buildings
- **Status**: The prototype does not yet interface with physical digital energy meters installed in the substation switchrooms of the 6 faculty complexes (`CEE`, `EIE`, `MME`, `MENA`, `ADMIN`, `AUDI`).
- **Constraint**: Communication drivers for physical hardware protocols (e.g., Modbus-RTU over RS-485, Modbus-TCP, DLMS/COSEM, or DNP3) have not yet been deployed in physical substations.

### 1.3 Balanced Three-Phase Assumption (No Per-Phase Measurements)
- **Status**: The current simulator assumes balanced three-phase operation across phases $R$, $Y$, and $B$ (where $V_{RN} = V_{YN} = V_{BN}$ and $I_R = I_Y = I_B$).
- **Constraint**: Real academic facilities experience phase imbalance due to uneven distribution of single-phase office loads, split air conditioners, and lighting circuits across the three phases. Neutral current is not currently modeled or measured.

### 1.4 Database Scaling for Sub-Second Multi-Node Ingestion
- **Status**: Telemetry persistence uses MongoDB Atlas with compound B-tree indexes on `(building_id, timestamp)`.
- **Constraint**: While MongoDB Atlas handles concurrent demonstration loads effectively, multi-year high-frequency sub-second telemetry ingestion across dozens of physical substations would benefit from native time-series collection partitioning or downsampling continuous aggregates.

### 1.5 Demonstration Tariff Model is Not Official University Bill
- **Status**: Cost estimates use a configurable two-part demonstration institutional tariff model (Energy Rate: LKR 40.00/kWh, Demand Rate: LKR 1,200.00/kW, Fixed Charge: LKR 3,000.00/month).
- **Constraint**: University of Ruhuna's actual monthly electricity billing is managed centrally by the Ceylon Electricity Board (CEB) under bulk supply tariffs, which may include time-of-use (TOU) peak/off-peak/day tiers, fuel adjustment charges (FAC), and contractual maximum demand limits that differ from the educational demonstration rates.

### 1.6 Supervisory Monitoring Without Autonomous Load Control
- **Status**: The system is an analytics and supervisory monitoring platform.
- **Constraint**: When an overload ($> 100\%$ feeder capacity) or low power factor ($< 0.850$) is detected, the platform triggers visual alerts and records threshold events, but does not autonomously trip shunt circuit breakers, shed non-critical HVAC loads, or switch capacitor banks.

---

## 2. Future Work & Engineering Expansion Roadmap

To transition this software prototype into an operational campus energy management system, the following enhancements are planned:

```text
               CAMPUS SMART ENERGY SYSTEM: FUTURE ARCHITECTURE
               
+-------------------------------------------------------------------------+
|                        Physical Substation Layer                        |
|                                                                         |
|  [Substation CEE]     [Substation EIE]     [Substation MME]    ...      |
|  Digital Multifunction Meters (Schneider / ABB / Selec)                 |
|  Per-Phase V_R, V_Y, V_B | I_R, I_Y, I_B | I_Neutral | THD% | Harmonics |
+-------------------------------------------------------------------------+
                                    │ RS-485 Serial Bus (Modbus RTU)
                                    ▼
+-------------------------------------------------------------------------+
|                       IoT Substation Gateway Layer                      |
|                                                                         |
|  Raspberry Pi / ESP32 Industrial Gateway running MQTT / Modbus TCP      |
|  Local circular buffer for zero data loss during network outages        |
+-------------------------------------------------------------------------+
                                    │ TLS-Encrypted Wi-Fi / Ethernet
                                    ▼
+-------------------------------------------------------------------------+
|                        Cloud Ingestion & Storage                        |
|                                                                         |
|  MongoDB Time Series Collections / TimescaleDB Engine                   |
|  Automated data rollup retention (1-min, 1-hour, 1-day aggregates)       |
+-------------------------------------------------------------------------+
                                    │ REST / WebSockets
                                    ▼
+-------------------------------------------------------------------------+
|                  Analytics, AI & Automatic Control                      |
|                                                                         |
|  - LSTM / XGBoost Load Forecasting & Solar PV Yield Prediction          |
|  - Automatic Power Factor Correction (APFC) Capacitor Bank Switching   |
|  - Demand Response: Modbus relay shedding of non-critical HVAC          |
+-------------------------------------------------------------------------+
```

### 2.1 Physical Multifunction Energy Meters (MFM)
- Install DIN-rail multifunction digital energy meters (e.g., Schneider Electric PM5350 series, ABB M4M, or Selec MFM384) at the main low-voltage (400V) distribution switchboards of each department.
- Capture Class 0.5S or Class 0.2S revenue-grade active and reactive electrical energy.

### 2.2 Industrial IoT Gateway & Modbus/RS485 Integration
- Equip each substation with an industrial edge gateway (such as a DIN-rail Raspberry Pi CM4 or ESP32-based industrial controller).
- Implement Modbus-RTU over RS-485 serial communication daisy-chained across meters.
- Transmit readings to the central server via MQTT over campus fiber-optic LAN or Wi-Fi with TLS encryption.
- Implement edge caching to prevent data gaps during campus network disruptions.

### 2.3 Comprehensive Three-Phase Per-Phase Telemetry
- Expand telemetry schema to record individual phase metrics:
  - Phase voltages ($V_{RN}, V_{YN}, V_{BN}$) and line-to-line voltages ($V_{RY}, V_{YB}, V_{BR}$).
  - Phase currents ($I_R, I_Y, I_B$) and neutral current ($I_N$).
  - Per-phase active power ($P_R, P_Y, P_B$), reactive power, and phase angle displacement.
- Detect phase current unbalance percentage according to IEEE/IEC standards.

### 2.4 Power Quality & Harmonic Monitoring
- Measure Total Harmonic Distortion (THD) for voltage ($\text{THD}_V\%$) and current ($\text{THD}_I\%$) up to the 31st harmonic.
- Track harmonic pollution generated by non-linear loads (variable frequency drives, laboratory computers, uninterruptible power supplies).
- Monitor voltage sags, swells, and transient flickers.

### 2.5 Rooftop Solar PV Generation & Microgrid Integration
- Integrate telemetry from the Faculty of Engineering’s existing or planned rooftop solar photovoltaic (PV) inverters.
- Monitor bidirectional active power flow (import vs. export to grid).
- Track net energy consumption, solar self-consumption ratio, and avoided carbon dioxide emissions ($kg\text{ CO}_2\text{ equivalent}$).

### 2.6 Automated Demand Response & Load Control
- Integrate programmable relay output modules (e.g., Modbus relay boards or BACnet actuators) for automated load management:
  - **Automatic Power Factor Correction (APFC)**: Automatically switch capacitor bank steps when power factor falls below $0.90$.
  - **Peak Demand Limiting**: Automatically trim or duty-cycle central chiller plants and corridor lighting when campus active power approaches the utility feeder capacity ceiling.

### 2.7 Predictive Energy Forecasting
- Implement predictive machine learning models (e.g., Long Short-Term Memory [LSTM] neural networks or XGBoost regressors).
- Forecast building load demand 24 hours in advance using historical patterns, academic calendar events, and weather forecasts.
- Provide early warning before monthly maximum demand limits are breached.
