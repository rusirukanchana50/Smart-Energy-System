# MongoDB Database Schema Documentation

## Faculty of Engineering, University of Ruhuna
### Smart Energy Monitoring and Analytics System (`ruhuna_energy`)

---

## 1. Document Entity Relationships

```
+--------------------------------------------------------------------+
|                       Collection: buildings                        |
|--------------------------------------------------------------------|
| _id             ObjectId (Primary Key)                             |
| name            String (Unique, Required, Trimmed)                 |
| code            String (Unique, Uppercase, Required, Indexed)      |
| description     String (Trimmed)                                   |
| rated_power_kw  Number (Min: 0.1, Max: 10000, Default: 50.0)       |
| is_core         Boolean (Default: false, true for 6 Core Campus)   |
| created_at      Date (Default: Date.now)                           |
+--------------------------------------------------------------------+
       │                                   │
       │ 1:N (building_id)                 │ 1:N (building_id)
       ▼                                   ▼
+-------------------------------+   +------------------------------------+
| Collection: energy_readings   |   |        Collection: alerts          |
|-------------------------------|   |------------------------------------|
| _id                 ObjectId  |   | _id               ObjectId         |
| building_id         ObjectId  |   | building_id       ObjectId (Ref)   |
| timestamp           Date      |   | timestamp         Date (Indexed)   |
| voltage             Number    |   | alert_type        String (Indexed) |
| current             Number    |   | severity          String ('INFO',  |
| power_factor        Number    |   |                          'WARNING',|
| frequency           Number    |   |                          'CRITICAL')
| apparent_power_kva  Number    |   | measured_value    Number           |
| active_power_kw     Number    |   | threshold_value   Number           |
| reactive_power_kvar Number    |   | message           String           |
| energy_kwh          Number    |   | acknowledged      Boolean (Def: F) |
| is_simulated        Boolean   |   | acknowledged_by   String (Def: null|
+-------------------------------+   | acknowledged_at   Date (Def: null) |
                                    | is_active         Boolean (Def: T) |
                                    | cleared_at        Date (Def: null) |
                                    +------------------------------------+

+--------------------------------------------------------------------+
|                        Collection: users                           |
|--------------------------------------------------------------------|
| _id            ObjectId (Primary Key)                              |
| email          String (Unique, Lowercase, Required, Trimmed)       |
| password_hash  String (Required, Bcrypt hashed, 12 rounds)         |
| full_name      String (Required, Trimmed)                          |
| role           String (Enum: ['Administrator', 'Viewer'])          |
| created_at     Date (Default: Date.now)                            |
+--------------------------------------------------------------------+

+--------------------------------------------------------------------+
|                       Collection: tariffs                          |
|--------------------------------------------------------------------|
| _id                      ObjectId (Primary Key)                    |
| name                     String (Default: 'Demonstration Tariff')  |
| energy_rate_lkr_per_kwh  Number (Default: 40.0)                    |
| demand_rate_lkr_per_kva  Number (Default: 1200.0)                  |
| fixed_charge_lkr         Number (Default: 3000.0)                  |
| updated_at               Date (Default: Date.now)                  |
+--------------------------------------------------------------------+

+--------------------------------------------------------------------+
|                   Collection: faculty_settings                     |
|--------------------------------------------------------------------|
| _id                 ObjectId (Primary Key)                         |
| key                 String (Unique, Required)                      |
| value               String (Required)                              |
| description         String                                         |
| updated_at          Date (Default: Date.now)                       |
+--------------------------------------------------------------------+

+--------------------------------------------------------------------+
|                       Collection: sessions                         |
|--------------------------------------------------------------------|
| _id             String (Session ID)                                |
| session         String (Serialized encrypted session JSON)         |
| expires         Date (Auto-managed TTL index by connect-mongo)     |
+--------------------------------------------------------------------+
```

---

## 2. Collections and Field Specifications

### 2.1 `buildings` Collection
Stores metadata and electrical rated capacity for campus facilities.

| Field | Type | Required | Constraints / Defaults | Description |
|---|---|:---:|---|---|
| `_id` | `ObjectId` | Yes | Auto-generated PK | Unique identifier |
| `name` | `String` | Yes | Unique, Trimmed | Full departmental name |
| `code` | `String` | Yes | Unique, Uppercase, Trimmed | Department code (e.g., `CEE`, `EIE`) |
| `description` | `String` | No | Trimmed | Facility function description |
| `rated_power_kw` | `Number` | Yes | `min: 0.1`, `max: 10000.0`, default: `50.0` | Feeder transformer rated capacity in kW |
| `is_core` | `Boolean` | Yes | Default: `false` | Protected campus building flag |
| `created_at` | `Date` | Yes | Default: `Date.now` | Registration timestamp |

**Indexes**:
- `code_1` (Unique): Ensures no duplicate uppercase codes can exist.
- `name_1` (Unique): Ensures department names remain unique across the campus.

---

### 2.2 `energy_readings` Collection
Stores high-resolution three-phase telemetry records.

| Field | Type | Required | Description |
|---|---|:---:|---|
| `_id` | `ObjectId` | Yes | Unique reading identifier |
| `building_id` | `ObjectId` | Yes | Reference to `buildings._id` |
| `timestamp` | `Date` | Yes | Telemetry sample timestamp |
| `voltage` | `Number` | Yes | Balanced line-to-line RMS voltage ($V_L$) in Volts |
| `current` | `Number` | Yes | Balanced RMS line current ($I_L$) in Amperes |
| `power_factor` | `Number` | Yes | System power factor ($\cos\theta$, $0.0 - 1.0$) |
| `frequency` | `Number` | Yes | Grid line frequency in Hertz |
| `apparent_power_kva` | `Number` | Yes | Apparent power $S = (\sqrt{3} V_L I_L) / 1000$ |
| `active_power_kw` | `Number` | Yes | Active power $P = S \times \text{PF}$ |
| `reactive_power_kvar` | `Number` | Yes | Reactive power $Q = \sqrt{S^2 - P^2}$ |
| `energy_kwh` | `Number` | Yes | Cumulative energy in kWh (Riemann integrated) |
| `is_simulated` | `Boolean` | Yes | Explicit academic label (Default: `true`) |

**Indexes**:
- `{ building_id: 1, timestamp: -1 }`: Compound index for rapid chronological retrieval.
- `{ timestamp: -1 }`: Time-series indexing for campus-wide aggregations.

---

### 2.3 `alerts` Collection
Tracks abnormal electrical events through an auto-clearing state machine.

| Field | Type | Constraints | Description |
|---|---|---|---|
| `_id` | `ObjectId` | Auto PK | Unique alert identifier |
| `building_id` | `ObjectId` | Reference | Affected facility |
| `timestamp` | `Date` | Required | Trigger timestamp |
| `alert_type` | `String` | Enum | `LOW_POWER_FACTOR`, `UNDERVOLTAGE`, `OVERVOLTAGE`, `FREQUENCY_EXCURSION`, `HIGH_LOAD`, `OVERLOAD`, `SENSOR_OFFLINE` |
| `severity` | `String` | Enum | `CRITICAL`, `WARNING`, `INFO` |
| `measured_value` | `Number` | Required | Value that triggered threshold |
| `threshold_value`| `Number` | Required | System limit breached |
| `message` | `String` | Required | Diagnostic description |
| `acknowledged` | `Boolean` | Def: `false` | Has administrator acknowledged |
| `acknowledged_by`| `String` | Def: `null` | Administrator email |
| `acknowledged_at`| `Date` | Def: `null` | Acknowledgment timestamp |
| `is_active` | `Boolean` | Def: `true` | Active state-machine flag |
| `cleared_at` | `Date` | Def: `null` | Timestamp condition normalized |

**Indexes**:
- `{ is_active: 1, severity: 1 }`: For rapid active alerts dashboard counts.
- `{ building_id: 1, is_active: 1 }`: For building-specific anomaly suppression.

---

### 2.4 `users` Collection
Stores user authentication profiles with salted bcrypt hashes.

| Field | Type | Constraints | Description |
|---|---|---|---|
| `_id` | `ObjectId` | Auto PK | User record identifier |
| `email` | `String` | Unique, Lowercase | User email address |
| `password_hash` | `String` | Hidden in JSON | Salted bcrypt hash |
| `full_name` | `String` | Required | User's full name |
| `role` | `String` | `Administrator` \| `Viewer` | Role-Based Access Control |
| `created_at` | `Date` | Default: `Date.now` | Account creation timestamp |

---

### 2.5 `tariffs` Collection
Stores configurable institutional commercial / industrial electricity tariffs.

| Field | Type | Default | Description |
|---|---|---|---|
| `energy_rate_lkr_per_kwh` | `Number` | `40.0` | Charge per kWh consumed (LKR) |
| `demand_rate_lkr_per_kva` | `Number` | `1200.0` | Monthly peak demand charge per kVA (LKR) |
| `fixed_charge_lkr` | `Number` | `3000.0` | Fixed monthly supply charge (LKR) |
| `updated_at` | `Date` | `Date.now` | Last rate adjustment timestamp |

---

## 3. Database Integrity & Constraints

1. **Foreign Key Integrity**:
   - `Building.delete` safeguards check for dependent records in `energy_readings` and `alerts` before deletion.
   - Core campus buildings (`is_core: true`) cannot be deleted under any circumstances.
2. **Missing Sensor Gap Protection**:
   - When the time gap between consecutive readings exceeds $60\text{ seconds}$, energy integration assumes sensor downtime and assigns zero incremental energy.
3. **Audit Trail**:
   - Every acknowledged alert permanently records the administrator's email and timestamp.
