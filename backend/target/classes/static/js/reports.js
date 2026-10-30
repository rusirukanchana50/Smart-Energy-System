/**
 * reports.js
 * ----------
 * Client-Side Controller for Phase 8: Reports, Export and System Integration
 * Faculty of Engineering, University of Ruhuna
 *
 * Handles:
 * - Reporting preset periods: Today, Last 24 Hours, Last 7 Days, This Month
 * - Fetching full 16-parameter summary report via /api/reports/summary
 * - Displaying comprehensive KPI cards, engineering observations, and campus breakdown table
 * - Triggering printable PDF audit report popup via /reports/printable
 * - Downloading raw CSV telemetry files via /api/reports/export/csv
 * - Administrator demonstration tariff updates via /api/config/tariff
 */

document.addEventListener("DOMContentLoaded", async () => {
  await initReportsPage();
  setupReportPresets();
  setReportPreset("24h");
  triggerGenerateReport();
});

/**
 * Initialize dynamic user profile, facilities dropdown, and active tariff model
 */
async function initReportsPage() {
  // 1. Fetch authenticated user profile
  try {
    const res = await fetch("/api/auth/me", {
      headers: { "Accept": "application/json" },
      credentials: "same-origin"
    });

    if (res.ok) {
      const data = await res.json();
      if (data.status === "success" && data.user) {
        const user = data.user;
        const name = user.full_name || "User";
        const initial = name.trim().length > 0 ? name.trim()[0].toUpperCase() : "U";
        const role = user.role || "Viewer";

        const avatarEl = document.getElementById("userAvatar");
        if (avatarEl) { avatarEl.textContent = initial; avatarEl.title = name; }
        const nameEl = document.getElementById("userName");
        if (nameEl) nameEl.textContent = name;
        const roleBadge = document.getElementById("userRoleBadge");
        if (roleBadge) {
          roleBadge.textContent = role;
          roleBadge.setAttribute("data-role", role);
          roleBadge.className = "user-role-tag " + (role === "Administrator" ? "role-admin" : "role-viewer");
        }

        // Show Administrator-only demonstration tariff management panel
        const adminSection = document.getElementById("adminTariffSection");
        if (adminSection) {
          if (role === "Administrator") {
            adminSection.style.display = "block";
          } else {
            adminSection.style.display = "none";
          }
        }

        if (data.csrf_token) {
          const metaCsrf = document.querySelector('meta[name="csrf-token"]');
          if (metaCsrf) metaCsrf.setAttribute("content", data.csrf_token);
          sessionStorage.setItem("csrf_token", data.csrf_token);
          const logoutCsrf = document.getElementById("logoutCsrf");
          if (logoutCsrf) logoutCsrf.value = data.csrf_token;
        }
      }
    } else if (res.status === 401) {
      window.location.href = "/login";
      return;
    }
  } catch (e) {
    console.warn("Could not load user profile:", e);
  }

  // 2. Fetch registered facilities from /api/buildings
  try {
    const bRes = await fetch("/api/buildings", {
      headers: { "Accept": "application/json" },
      credentials: "same-origin"
    });
    if (bRes.ok) {
      const bData = await bRes.json();
      if (bData.status === "success" && Array.isArray(bData.data)) {
        const sel = document.getElementById("reportBuilding");
        if (sel) {
          sel.innerHTML = '<option value="all">Campus Facilities Summary (All Registered Buildings)</option>';
          const urlParams = new URLSearchParams(window.location.search);
          const queryBldg = urlParams.get("building_id");

          bData.data.forEach(b => {
            const opt = document.createElement("option");
            opt.value = b.id || b.code;
            opt.textContent = `[${b.code}] ${b.name}${b.rated_power_kw ? ` (Rated: ${b.rated_power_kw} kW)` : ""}`;
            if (queryBldg && (queryBldg === b.id || queryBldg === b.code)) {
              opt.selected = true;
            }
            sel.appendChild(opt);
          });
        }
      }
    }
  } catch (e) {
    console.warn("Could not load facilities:", e);
  }

  // 3. Fetch active tariff configuration from /api/config
  try {
    const cRes = await fetch("/api/config", {
      headers: { "Accept": "application/json" },
      credentials: "same-origin"
    });
    if (cRes.ok) {
      const cData = await cRes.json();
      if (cData.status === "success" && cData.data && cData.data.tariff) {
        const t = cData.data.tariff;
        const bEnergy = document.getElementById("bannerEnergyRate");
        if (bEnergy && t.energy_rate_lkr_per_kwh != null) {
          bEnergy.textContent = Number(t.energy_rate_lkr_per_kwh).toFixed(2);
        }
        const bDemand = document.getElementById("bannerDemandRate");
        if (bDemand && t.demand_rate_lkr_per_kw != null) {
          bDemand.textContent = Number(t.demand_rate_lkr_per_kw).toFixed(2);
        }

        const cfgId = document.getElementById("cfgTariffId");
        if (cfgId && t.id != null) cfgId.value = t.id;
        const cfgName = document.getElementById("cfgTariffName");
        if (cfgName && t.name) cfgName.value = t.name;
        const cfgEnergy = document.getElementById("cfgEnergyRate");
        if (cfgEnergy && t.energy_rate_lkr_per_kwh != null) cfgEnergy.value = t.energy_rate_lkr_per_kwh;
        const cfgDemand = document.getElementById("cfgDemandRate");
        if (cfgDemand && t.demand_rate_lkr_per_kw != null) cfgDemand.value = t.demand_rate_lkr_per_kw;
        const cfgFixed = document.getElementById("cfgFixedCharge");
        if (cfgFixed && t.fixed_charge_lkr != null) cfgFixed.value = t.fixed_charge_lkr;
      }
    }
  } catch (e) {
    console.warn("Could not load tariff configuration:", e);
  }
}

/**
 * Configure preset period buttons (today, 24h, 7d, month)
 */
function setupReportPresets() {
  const presetBtns = document.querySelectorAll("#reportPresets .preset-btn");
  presetBtns.forEach(btn => {
    btn.addEventListener("click", () => {
      presetBtns.forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      setReportPreset(btn.dataset.preset);
      triggerGenerateReport();
    });
  });

  const bldgSelect = document.getElementById("reportBuilding");
  if (bldgSelect) {
    bldgSelect.addEventListener("change", () => {
      triggerGenerateReport();
    });
  }

  // Deactivate preset highlight if custom date filters are edited manually
  const startInput = document.getElementById("reportStart");
  const endInput = document.getElementById("reportEnd");
  [startInput, endInput].forEach(inp => {
    if (inp) {
      inp.addEventListener("input", () => {
        presetBtns.forEach(b => b.classList.remove("active"));
      });
    }
  });
}

/**
 * Calculate and set datetime-local input values for presets
 */
function setReportPreset(preset) {
  const now = new Date();
  let start = new Date();

  if (preset === "today") {
    start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0);
  } else if (preset === "24h") {
    start = new Date(now.getTime() - (24 * 60 * 60 * 1000));
  } else if (preset === "7d") {
    start = new Date(now.getTime() - (7 * 24 * 60 * 60 * 1000));
  } else if (preset === "month") {
    start = new Date(now.getFullYear(), now.getMonth(), 1, 0, 0, 0);
  }

  const formatForInput = (d) => {
    const pad = (n) => String(n).padStart(2, "0");
    const year = d.getFullYear();
    const month = pad(d.getMonth() + 1);
    const day = pad(d.getDate());
    const hours = pad(d.getHours());
    const minutes = pad(d.getMinutes());
    return `${year}-${month}-${day}T${hours}:${minutes}`;
  };

  const startInput = document.getElementById("reportStart");
  const endInput = document.getElementById("reportEnd");

  if (startInput) startInput.value = formatForInput(start);
  if (endInput) endInput.value = formatForInput(now);
}

/**
 * Format numbers with comma grouping and fixed decimals
 * Returns '--' if val is null, undefined, or NaN, ensuring missing metrics are not disguised as 0.00
 */
function formatNum(val, dec = 2, fallback = "--") {
  if (val === null || val === undefined || isNaN(val)) return fallback;
  return Number(val).toLocaleString(undefined, {
    minimumFractionDigits: dec,
    maximumFractionDigits: dec
  });
}

/**
 * Generate Summary Report API request
 */
async function triggerGenerateReport() {
  const bldgVal = document.getElementById("reportBuilding") ? document.getElementById("reportBuilding").value : "all";
  const startVal = document.getElementById("reportStart") ? document.getElementById("reportStart").value : "";
  const endVal = document.getElementById("reportEnd") ? document.getElementById("reportEnd").value : "";

  const alertBox = document.getElementById("reportAlert");
  const loadingState = document.getElementById("reportLoadingState");
  const contentArea = document.getElementById("reportContentArea");
  const btn = document.getElementById("btnGenerateReport");

  if (alertBox) alertBox.style.display = "none";
  if (loadingState) loadingState.style.display = "block";
  if (contentArea) contentArea.style.opacity = "0.5";
  if (btn) {
    btn.disabled = true;
    btn.innerHTML = `<span class="pulse-dot"></span> Generating...`;
  }

  try {
    const url = `/api/reports/summary?building_id=${encodeURIComponent(bldgVal)}&start=${encodeURIComponent(startVal)}&end=${encodeURIComponent(endVal)}`;
    const res = await fetch(url, { credentials: "same-origin" });

    if (!res.ok) {
      if (res.status === 401) {
        window.location.href = "/login";
        return;
      }
      const errData = await res.json().catch(() => ({}));
      throw new Error(errData.message || `Report generation failed: ${res.statusText}`);
    }

    const data = await res.json();
    const report = data.report || {};

    renderReportView(report);

  } catch (err) {
    console.error("Report generation error:", err);
    if (alertBox) {
      alertBox.textContent = `Error compiling energy report: ${err.message}`;
      alertBox.style.display = "block";
    }
  } finally {
    if (loadingState) loadingState.style.display = "none";
    if (contentArea) contentArea.style.opacity = "1";
    if (btn) {
      btn.disabled = false;
      btn.innerHTML = `
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="23 6 13.5 15.5 8.5 10.5 1 18"></polyline>
          <polyline points="17 6 23 6 23 12"></polyline>
        </svg>
        Generate
      `;
    }
  }
}

/**
 * Render the full 16-parameter energy summary report view
 */
function renderReportView(report) {
  const m = report.metrics || {};
  const isCampus = (report.mode === "campus_summary");

  // Metadata headers
  const bldgCountStr = (report.building_breakdown && report.building_breakdown.length)
    ? `${report.building_breakdown.length} BUILDINGS`
    : "ALL REGISTERED FACILITIES";
  const repScopeTag = document.getElementById("repScopeTag");
  if (repScopeTag) {
    repScopeTag.textContent = isCampus ? `CAMPUS SUMMARY (${bldgCountStr})` : `FACILITY: [${report.building_code || "SINGLE"}]`;
  }

  const repDisplayTitle = document.getElementById("repDisplayTitle");
  if (repDisplayTitle) {
    repDisplayTitle.textContent = report.report_title || "Electrical Energy Audit Report";
  }

  const repMetaPeriod = document.getElementById("repMetaPeriod");
  if (repMetaPeriod && report.reporting_period) {
    repMetaPeriod.textContent = `${report.reporting_period.start || "--"} to ${report.reporting_period.end || "--"}`;
  }

  const repMetaDuration = document.getElementById("repMetaDuration");
  if (repMetaDuration && report.reporting_period) {
    repMetaDuration.textContent = `${report.reporting_period.duration_days || 0} Day(s)`;
  }

  const repMetaGenerated = document.getElementById("repMetaGenerated");
  if (repMetaGenerated) {
    repMetaGenerated.textContent = report.generated_at || new Date().toLocaleString();
  }

  // 16 Primary Metrics
  const setElText = (id, text) => {
    const el = document.getElementById(id);
    if (el) el.textContent = text;
  };

  setElText("repTotalKwh", formatNum(m.total_energy_kwh, 2));
  setElText("repTotalCost", formatNum(m.estimated_electricity_cost_lkr, 2));
  setElText("repAvgPower", formatNum(m.avg_active_power_kw, 2));
  setElText("repPeakDemand", formatNum(m.peak_demand_kw, 1));

  setElText("repLoadFactor", formatNum(m.load_factor, 2));
  setElText("repLoadFactorPct", (m.load_factor_pct !== null && m.load_factor_pct !== undefined)
    ? `(${formatNum(m.load_factor_pct, 1)}%)`
    : "");

  const avgPfEl = document.getElementById("repAvgPf");
  const pfSubEl = document.getElementById("repPfComplianceSub");
  if (avgPfEl) {
    if (m.avg_power_factor !== null && m.avg_power_factor !== undefined) {
      avgPfEl.textContent = formatNum(m.avg_power_factor, 3);
      if (m.avg_power_factor < 0.85) {
        avgPfEl.style.color = "#ef4444";
        if (pfSubEl) pfSubEl.textContent = "Non-compliant (Below 0.85 threshold)";
      } else {
        avgPfEl.style.color = "var(--energy-teal)";
        if (pfSubEl) pfSubEl.textContent = "Compliant with CEB threshold (>= 0.85)";
      }
    } else {
      avgPfEl.textContent = "--";
      if (pfSubEl) pfSubEl.textContent = "No data recorded";
    }
  }

  const minPfVal = (m.min_power_factor !== null && m.min_power_factor !== undefined) 
    ? m.min_power_factor 
    : m.avg_power_factor;
  setElText("repMinPf", formatNum(minPfVal, 3));

  setElText("repAvgS", formatNum(m.avg_apparent_power_kva, 1));
  setElText("repAvgQ", formatNum(m.avg_reactive_power_kvar, 1));

  setElText("repAfterHours", formatNum(m.after_hours_kwh, 2));
  setElText("repAfterHoursPct", (m.after_hours_pct !== null && m.after_hours_pct !== undefined)
    ? `(${formatNum(m.after_hours_pct, 1)}% of total)`
    : "");

  const voltRangeEl = document.getElementById("repVoltRange");
  if (voltRangeEl) {
    if (m.min_voltage !== null && m.min_voltage !== undefined && m.max_voltage !== null && m.max_voltage !== undefined) {
      voltRangeEl.textContent = `${formatNum(m.min_voltage, 0)}V - ${formatNum(m.max_voltage, 0)}V`;
    } else {
      voltRangeEl.textContent = "--";
    }
  }

  const avgVoltEl = document.getElementById("repAvgVolt");
  if (avgVoltEl) {
    if (m.avg_voltage !== null && m.avg_voltage !== undefined) {
      avgVoltEl.textContent = `Avg: ${formatNum(m.avg_voltage, 1)} V`;
    } else {
      avgVoltEl.textContent = "Avg: --";
    }
  }

  setElText("repAvgFreq", formatNum(m.avg_frequency, 2));
  setElText("repWarnAlerts", (m.warning_alerts_count !== null && m.warning_alerts_count !== undefined) ? m.warning_alerts_count : 0);
  setElText("repCritAlerts", (m.critical_alerts_count !== null && m.critical_alerts_count !== undefined) ? m.critical_alerts_count : 0);

  // Observations
  const obsList = document.getElementById("repObservationsList");
  if (obsList) {
    obsList.innerHTML = "";
    const obs = report.engineering_observations || [];
    if (obs.length === 0) {
      obsList.innerHTML = `<div class="obs-item"><span class="obs-bullet">&bull;</span><span>All monitored electrical parameters are within standard operating thresholds.</span></div>`;
    } else {
      obs.forEach(item => {
        const row = document.createElement("div");
        row.className = "obs-item";
        row.innerHTML = `<span class="obs-bullet">&bull;</span><span>${item}</span>`;
        obsList.appendChild(row);
      });
    }
  }

  // Status Badge
  const statusBadge = document.getElementById("repStatusBadge");
  if (statusBadge) {
    const perfStatus = report.performance_status || (m.avg_power_factor >= 0.85 ? "OPTIMAL" : "NEEDS_ATTENTION");
    statusBadge.textContent = String(perfStatus).replace("_", " ");
    statusBadge.className = "obs-badge";
    if (perfStatus === "OPTIMAL") statusBadge.classList.add("status-optimal");
    else if (perfStatus === "ACCEPTABLE") statusBadge.classList.add("status-acceptable");
    else if (perfStatus === "NEEDS_ATTENTION") statusBadge.classList.add("status-attention");
    else statusBadge.classList.add("status-inefficient");
  }

  // Campus Breakdown Table
  const breakdownSec = document.getElementById("campusBreakdownSection");
  const breakdownBody = document.getElementById("campusBreakdownTableBody");
  if (breakdownSec && breakdownBody) {
    if (isCampus && report.building_breakdown && report.building_breakdown.length > 0) {
      breakdownSec.style.display = "block";
      breakdownBody.innerHTML = "";
      report.building_breakdown.forEach(b => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
          <td><strong>${b.building_code}</strong></td>
          <td>${b.building_name}</td>
          <td>${formatNum(b.total_kwh, 2)}</td>
          <td style="color: #10b981; font-weight: 600;">LKR ${formatNum(b.estimated_cost_lkr, 2)}</td>
          <td>${formatNum(b.peak_demand_kw, 1)}</td>
          <td>${formatNum(b.load_factor, 2)} <small style="color: var(--text-dim);">(${formatNum(b.load_factor_pct, 1)}%)</small></td>
          <td style="color: ${b.avg_power_factor < 0.85 ? '#ef4444' : 'inherit'}; font-weight: ${b.avg_power_factor < 0.85 ? '700' : 'normal'};">${formatNum(b.avg_power_factor, 3)}</td>
          <td>${formatNum(b.after_hours_kwh, 2)} <small style="color: var(--text-dim);">(${formatNum(b.after_hours_pct, 1)}%)</small></td>
        `;
        breakdownBody.appendChild(tr);
      });
    } else {
      breakdownSec.style.display = "none";
    }
  }
}

/**
 * Open the formal browser printable report in a popup window
 */
function openPrintableReport() {
  const bldgVal = document.getElementById("reportBuilding") ? document.getElementById("reportBuilding").value : "all";
  const startVal = document.getElementById("reportStart") ? document.getElementById("reportStart").value : "";
  const endVal = document.getElementById("reportEnd") ? document.getElementById("reportEnd").value : "";

  const url = `/reports/printable?building_id=${encodeURIComponent(bldgVal)}&start=${encodeURIComponent(startVal)}&end=${encodeURIComponent(endVal)}`;
  window.open(url, "_blank");
}

/**
 * Stream/Download CSV export file directly from telemetry records
 */
function downloadCsvExport() {
  const bldgVal = document.getElementById("reportBuilding") ? document.getElementById("reportBuilding").value : "all";
  const startVal = document.getElementById("reportStart") ? document.getElementById("reportStart").value : "";
  const endVal = document.getElementById("reportEnd") ? document.getElementById("reportEnd").value : "";

  const url = `/api/reports/export/csv?building_id=${encodeURIComponent(bldgVal)}&start=${encodeURIComponent(startVal)}&end=${encodeURIComponent(endVal)}`;
  window.location.href = url;
}

/**
 * Administrator Only: Update Demonstration Tariff Rates via API
 */
async function updateTariffConfig() {
  const tariffId = document.getElementById("cfgTariffId") ? document.getElementById("cfgTariffId").value : 1;
  const nameInput = document.getElementById("cfgTariffName");
  const energyInput = document.getElementById("cfgEnergyRate");
  const demandInput = document.getElementById("cfgDemandRate");
  const fixedInput = document.getElementById("cfgFixedCharge");

  const name = nameInput ? nameInput.value.trim() : "";
  const rawEnergy = energyInput ? energyInput.value.trim() : "";
  const rawDemand = demandInput ? demandInput.value.trim() : "";
  const rawFixed = fixedInput ? fixedInput.value.trim() : "";

  const energyRate = parseFloat(rawEnergy);
  const demandRate = parseFloat(rawDemand);
  const fixedCharge = parseFloat(rawFixed);

  const alertBox = document.getElementById("tariffConfigAlert");
  const saveBtn = document.getElementById("btnSaveTariff");

  // Client-Side Validation
  if (!name || name.length < 3 || name.length > 100) {
    if (alertBox) {
      alertBox.className = "alert alert-danger";
      alertBox.textContent = "Validation Error: Tariff name/description is required (3 to 100 characters).";
      alertBox.style.display = "block";
    }
    return;
  }

  if (!rawEnergy || isNaN(energyRate) || !isFinite(energyRate) || energyRate <= 0 || energyRate > 1000) {
    if (alertBox) {
      alertBox.className = "alert alert-danger";
      alertBox.textContent = "Validation Error: Energy rate must be a valid positive number greater than 0 LKR/kWh (max 1,000 LKR/kWh).";
      alertBox.style.display = "block";
    }
    return;
  }

  if (!rawDemand || isNaN(demandRate) || !isFinite(demandRate) || demandRate < 0 || demandRate > 50000) {
    if (alertBox) {
      alertBox.className = "alert alert-danger";
      alertBox.textContent = "Validation Error: Demand rate must be a valid non-negative number (>= 0 LKR/kW, max 50,000 LKR/kW).";
      alertBox.style.display = "block";
    }
    return;
  }

  if (!rawFixed || isNaN(fixedCharge) || !isFinite(fixedCharge) || fixedCharge < 0 || fixedCharge > 100000) {
    if (alertBox) {
      alertBox.className = "alert alert-danger";
      alertBox.textContent = "Validation Error: Monthly fixed charge must be a valid non-negative number (>= 0 LKR, max 100,000 LKR).";
      alertBox.style.display = "block";
    }
    return;
  }

  function getCsrfToken() {
    const el = document.querySelector('meta[name="csrf-token"]');
    return (el && el.getAttribute("content")) ? el.getAttribute("content") : (sessionStorage.getItem("csrf_token") || "");
  }

  try {
    if (saveBtn) saveBtn.disabled = true;

    const res = await fetch("/api/config/tariff", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-TOKEN": getCsrfToken()
      },
      credentials: "same-origin",
      body: JSON.stringify({
        tariff_id: tariffId,
        name: name,
        energy_rate_lkr_per_kwh: energyRate,
        demand_rate_lkr_per_kw: demandRate,
        fixed_charge_lkr: fixedCharge
      })
    });

    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.message || "Failed to update tariff configuration.");
    }

    if (alertBox) {
      alertBox.className = "alert alert-success";
      alertBox.textContent = `Tariff successfully updated! ${data.message || ""}`;
      alertBox.style.display = "block";
    }

    // Refresh banner text
    const bEnergy = document.getElementById("bannerEnergyRate");
    if (bEnergy) bEnergy.textContent = energyRate.toFixed(2);
    const bDemand = document.getElementById("bannerDemandRate");
    if (bDemand) bDemand.textContent = demandRate.toFixed(2);

    // Refresh current report with updated tariff rates
    setTimeout(() => {
      triggerGenerateReport();
    }, 1000);

  } catch (err) {
    if (alertBox) {
      alertBox.className = "alert alert-danger";
      alertBox.textContent = `Configuration Error: ${err.message}`;
      alertBox.style.display = "block";
    }
  } finally {
    if (saveBtn) saveBtn.disabled = false;
  }
}
