/**
 * printable_report.js
 * -------------------
 * Client-Side Controller for Printable Energy Audit Report
 * Faculty of Engineering, University of Ruhuna
 *
 * Handles:
 * - Parsing building_id, start, and end query parameters from URL
 * - Fetching authenticated comprehensive energy audit report via /api/reports/summary
 * - Populating formal University of Ruhuna printable audit report view
 * - Graceful error and session handling
 * - Clean browser print triggering via window.print()
 */

document.addEventListener("DOMContentLoaded", () => {
  loadPrintableReport();
});

/**
 * Format numbers with comma grouping and fixed decimals
 * Returns fallback if val is null, undefined, or NaN
 */
function formatNum(val, dec = 2, fallback = "--") {
  if (val === null || val === undefined || isNaN(val)) return fallback;
  return Number(val).toLocaleString(undefined, {
    minimumFractionDigits: dec,
    maximumFractionDigits: dec
  });
}

/**
 * Fetches report summary and populates printable document elements
 */
async function loadPrintableReport() {
  const loadingEl = document.getElementById("printLoadingState");
  const errorEl = document.getElementById("printErrorState");
  const contentEl = document.getElementById("printableContent");

  const urlParams = new URLSearchParams(window.location.search);
  const buildingId = urlParams.get("building_id") || "all";
  const start = urlParams.get("start") || "";
  const end = urlParams.get("end") || "";
  const autoPrint = urlParams.get("autoprint") === "true";

  try {
    const apiUrl = `/api/reports/summary?building_id=${encodeURIComponent(buildingId)}&start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}`;
    const res = await fetch(apiUrl, { credentials: "same-origin" });

    if (!res.ok) {
      if (res.status === 401) {
        window.location.href = "/login";
        return;
      }
      const errData = await res.json().catch(() => ({}));
      throw new Error(errData.message || `Failed to fetch report telemetry (HTTP ${res.status})`);
    }

    const data = await res.json();
    const report = data.report || {};
    const m = report.metrics || {};

    // 1. Title & Metadata
    const titleText = report.report_title || "Electrical Energy Audit Report";
    const pageTitleEl = document.getElementById("pageTitle");
    if (pageTitleEl) pageTitleEl.textContent = `${titleText} | University of Ruhuna`;

    const repTitleEl = document.getElementById("repTitle");
    if (repTitleEl) repTitleEl.textContent = titleText;

    const bldgName = report.building_name || "Campus-Wide Facilities";
    const bldgCode = report.building_code || "ALL";
    const metaScopeEl = document.getElementById("metaScope");
    if (metaScopeEl) metaScopeEl.textContent = `${bldgName} (${bldgCode})`;

    const metaGenEl = document.getElementById("metaGenerated");
    if (metaGenEl) metaGenEl.textContent = report.generated_at || new Date().toLocaleString();

    const repPeriod = report.reporting_period || {};
    const metaPeriodEl = document.getElementById("metaPeriod");
    if (metaPeriodEl) metaPeriodEl.textContent = `${repPeriod.start || "--"} to ${repPeriod.end || "--"}`;

    const metaDurEl = document.getElementById("metaDuration");
    if (metaDurEl) metaDurEl.textContent = `${repPeriod.duration_days || 0} Day(s)`;

    // 2. Summary KPI Grid
    const setElem = (id, text) => {
      const el = document.getElementById(id);
      if (el) el.textContent = text;
    };

    const setHtml = (id, html) => {
      const el = document.getElementById(id);
      if (el) el.innerHTML = html;
    };

    setHtml("kpiEnergyVal", `${formatNum(m.total_energy_kwh, 2)}<span class="print-kpi-unit">kWh</span>`);
    setElem("kpiCostVal", `LKR ${formatNum(m.estimated_electricity_cost_lkr, 2)}`);
    setHtml("kpiPeakVal", `${formatNum(m.peak_demand_kw, 1)}<span class="print-kpi-unit">kW</span>`);
    setHtml("kpiAvgPowerVal", `${formatNum(m.avg_active_power_kw, 1)}<span class="print-kpi-unit">kW</span>`);

    setElem("kpiLoadFactorVal", formatNum(m.load_factor, 2));
    setElem("kpiLoadFactorPctVal", `(${formatNum(m.load_factor_pct, 1)}%)`);
    setElem("kpiAvgPf", formatNum(m.avg_power_factor, 3));
    setHtml("kpiAfterHoursVal", `${formatNum(m.after_hours_kwh, 2)}<span class="print-kpi-unit">kWh</span>`);
    setElem("kpiWarnAlerts", m.warning_alerts_count != null ? m.warning_alerts_count : 0);
    setElem("kpiCritAlerts", m.critical_alerts_count != null ? m.critical_alerts_count : 0);

    // 3. Detailed Three-Phase Operating Parameters Table
    if (m.avg_voltage != null) {
      const vMin = m.min_voltage != null ? formatNum(m.min_voltage, 1) : "--";
      const vMax = m.max_voltage != null ? formatNum(m.max_voltage, 1) : "--";
      setElem("valVoltage", `${formatNum(m.avg_voltage, 1)} V (Min: ${vMin} V, Max: ${vMax} V)`);
      if (m.min_voltage != null && m.min_voltage < 380) {
        setHtml("statusVoltage", `<span style="color: #ef4444; font-weight: 700;">Excursion Detected</span>`);
      } else {
        setHtml("statusVoltage", `<span style="color: #059669;">Normal Compliance</span>`);
      }
    } else {
      setElem("valVoltage", "--");
      setElem("statusVoltage", "--");
    }

    setElem("valFrequency", `${formatNum(m.avg_frequency, 2)} Hz`);
    setElem("statusFrequency", "Synchronous Grid Normal");

    if (m.avg_power_factor != null) {
      const minPf = m.min_power_factor != null ? ` (Min Recorded: ${formatNum(m.min_power_factor, 3)})` : "";
      setElem("valPf", `${formatNum(m.avg_power_factor, 3)}${minPf}`);
      if (m.avg_power_factor < 0.85) {
        setHtml("statusPf", `<span style="color: #ef4444; font-weight: 700;">Sub-optimal / APFC Required</span>`);
      } else {
        setHtml("statusPf", `<span style="color: #059669;">Compliant</span>`);
      }
    } else {
      setElem("valPf", "--");
      setElem("statusPf", "--");
    }

    setElem("valApparentPower", `${formatNum(m.avg_apparent_power_kva, 2)} kVA`);
    setElem("valReactivePower", `${formatNum(m.avg_reactive_power_kvar, 2)} kVAr`);

    if (m.load_factor != null) {
      setElem("valLoadFactor", `${formatNum(m.load_factor, 2)} (${formatNum(m.load_factor_pct, 1)}%)`);
      setElem("statusLoadFactor", m.load_factor < 0.40 ? "Low Asset Utilization" : "Adequate Load Profile");
    } else {
      setElem("valLoadFactor", "--");
      setElem("statusLoadFactor", "--");
    }

    if (m.after_hours_kwh != null) {
      const pct = m.after_hours_pct != null ? formatNum(m.after_hours_pct, 1) : "0.0";
      setElem("valAfterHours", `${formatNum(m.after_hours_kwh, 2)} kWh (${pct}% of total)`);
      if (m.after_hours_pct != null && m.after_hours_pct > 30.0) {
        setHtml("statusAfterHours", `<span style="color: #d97706; font-weight: 600;">High Off-Hours Consumption</span>`);
      } else {
        setElem("statusAfterHours", "Normal Off-Hours Baseline");
      }
    } else {
      setElem("valAfterHours", "--");
      setElem("statusAfterHours", "--");
    }

    // 4. Campus Multi-Building Breakdown Table
    const breakdownContainer = document.getElementById("campusBreakdownContainer");
    const breakdownBody = document.getElementById("campusBreakdownBody");
    if (breakdownContainer && breakdownBody) {
      if (report.building_breakdown && report.building_breakdown.length > 0) {
        breakdownContainer.style.display = "block";
        breakdownBody.innerHTML = "";
        report.building_breakdown.forEach(b => {
          const tr = document.createElement("tr");
          const pfColor = (b.avg_power_factor != null && b.avg_power_factor < 0.85) ? "#ef4444" : "inherit";
          const pfWeight = (b.avg_power_factor != null && b.avg_power_factor < 0.85) ? "700" : "normal";
          tr.innerHTML = `
            <td><strong>${b.building_code || ""}</strong></td>
            <td>${b.building_name || ""}</td>
            <td>${formatNum(b.total_kwh, 2)}</td>
            <td style="color: #059669; font-weight: 600;">LKR ${formatNum(b.estimated_cost_lkr, 2)}</td>
            <td>${formatNum(b.peak_demand_kw, 1)}</td>
            <td>${formatNum(b.load_factor, 2)} (${formatNum(b.load_factor_pct, 1)}%)</td>
            <td style="color: ${pfColor}; font-weight: ${pfWeight};">${formatNum(b.avg_power_factor, 3)}</td>
            <td>${formatNum(b.after_hours_kwh, 2)} (${formatNum(b.after_hours_pct, 1)}%)</td>
          `;
          breakdownBody.appendChild(tr);
        });
      } else {
        breakdownContainer.style.display = "none";
      }
    }

    // 5. Engineering Observations
    const obsList = document.getElementById("printObsList");
    if (obsList) {
      obsList.innerHTML = "";
      const obs = report.engineering_observations || [];
      if (obs.length === 0) {
        obsList.innerHTML = `<li>All monitored electrical parameters are within standard operating thresholds.</li>`;
      } else {
        obs.forEach(item => {
          const li = document.createElement("li");
          li.style.marginBottom = "0.35rem";
          li.textContent = item;
          obsList.appendChild(li);
        });
      }
    }

    // 6. Recent Electrical Alerts
    const alertsContainer = document.getElementById("printAlertsContainer");
    const alertsBody = document.getElementById("printAlertsBody");
    if (alertsContainer && alertsBody) {
      if (report.important_alerts && report.important_alerts.length > 0) {
        alertsContainer.style.display = "block";
        alertsBody.innerHTML = "";
        const topAlerts = report.important_alerts.slice(0, 5);
        topAlerts.forEach(a => {
          const tr = document.createElement("tr");
          const sevColor = (a.severity === "CRITICAL") ? "#ef4444" : "#d97706";
          tr.innerHTML = `
            <td>${a.timestamp || "--"}</td>
            <td><strong>${a.alert_type || "--"}</strong></td>
            <td style="color: ${sevColor}; font-weight: 700;">${a.severity || "--"}</td>
            <td>${a.measured_value != null ? a.measured_value : "--"}</td>
            <td>${a.threshold_value != null ? a.threshold_value : "--"}</td>
            <td>${a.message || "--"}</td>
          `;
          alertsBody.appendChild(tr);
        });
      } else {
        alertsContainer.style.display = "none";
      }
    }

    // 7. Tariff Disclaimer Rates
    if (report.tariff) {
      setElem("disclaimerEnergyRate", Number(report.tariff.energy_rate_lkr_per_kwh || 38.50).toFixed(2));
      setElem("disclaimerDemandRate", Number(report.tariff.demand_rate_lkr_per_kw || 1450.00).toFixed(2));
    }

    // Reveal printable report document
    if (loadingEl) loadingEl.style.display = "none";
    if (contentEl) contentEl.style.display = "block";

    // Auto-print if query param autoprint=true is set
    if (autoPrint) {
      setTimeout(() => {
        window.print();
      }, 300);
    }

  } catch (err) {
    console.error("Printable report render error:", err);
    if (loadingEl) loadingEl.style.display = "none";
    if (errorEl) {
      errorEl.textContent = `Unable to generate printable report: ${err.message}`;
      errorEl.style.display = "block";
    }
  }
}
