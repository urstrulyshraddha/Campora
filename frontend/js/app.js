const API_BASE = "http://localhost:8080/api";
const MANAGEMENT_PASSCODE = "campora@2026";

let currentFilter = "all";
let cachedReports = [];

// DOM Elements
const navStudentBtn = document.getElementById("navStudentBtn");
const navManagementBtn = document.getElementById("navManagementBtn");
const studentView = document.getElementById("studentView");
const managementView = document.getElementById("managementView");

const passcodeModal = document.getElementById("passcodeModal");
const passcodeForm = document.getElementById("passcodeForm");
const passcodeInput = document.getElementById("passcodeInput");
const passcodeError = document.getElementById("passcodeError");
const cancelPasscodeBtn = document.getElementById("cancelPasscodeBtn");
const lockManagementBtn = document.getElementById("lockManagementBtn");

const submitModal = document.getElementById("submitModal");
const openSubmitModalBtn = document.getElementById("openSubmitModalBtn");
const closeSubmitModalBtn = document.getElementById("closeSubmitModalBtn");
const anonCheckbox = document.getElementById("anonCheckbox");
const publicIdentityFields = document.getElementById("publicIdentityFields");
const newReportForm = document.getElementById("newReportForm");
const reportDomain = document.getElementById("reportDomain");
const categoryLabel = document.getElementById("categoryLabel");

const resolveModal = document.getElementById("resolveModal");
const closeResolveModalBtn = document.getElementById("closeResolveModalBtn");
const resolveForm = document.getElementById("resolveForm");
const resolveTicketId = document.getElementById("resolveTicketId");
const resolveTicketTitle = document.getElementById("resolveTicketTitle");
const newStatusSelect = document.getElementById("newStatusSelect");
const resolutionNoteInput = document.getElementById("resolutionNoteInput");

// ==========================================
// INIT & NAVIGATION
// ==========================================
document.addEventListener("DOMContentLoaded", () => {
  fetchReports();
  fetchStats();
  setupFilterPills();
  refreshIcons();
});

function refreshIcons() {
  if (window.lucide) {
    lucide.createIcons();
  }
}

navStudentBtn.addEventListener("click", () => {
  studentView.classList.remove("hidden");
  managementView.classList.add("hidden");

  navStudentBtn.className = "px-4 py-2 rounded-lg bg-pastel-surface text-purple-950 border border-pastel-border shadow-xs transition-all flex items-center gap-2";
  navManagementBtn.className = "px-4 py-2 rounded-lg text-neutral-500 hover:text-purple-900 transition-all flex items-center gap-2";
  fetchReports();
});

navManagementBtn.addEventListener("click", () => {
  const isAuth = sessionStorage.getItem("campora_mgmt_auth");
  if (isAuth === "true") {
    showManagementDashboard();
  } else {
    passcodeModal.classList.remove("hidden");
    passcodeInput.value = "";
    passcodeError.classList.add("hidden");
    passcodeInput.focus();
    refreshIcons();
  }
});

cancelPasscodeBtn.addEventListener("click", () => {
  passcodeModal.classList.add("hidden");
});

passcodeForm.addEventListener("submit", (e) => {
  e.preventDefault();
  if (passcodeInput.value.trim() === MANAGEMENT_PASSCODE) {
    sessionStorage.setItem("campora_mgmt_auth", "true");
    passcodeModal.classList.add("hidden");
    showManagementDashboard();
  } else {
    passcodeError.classList.remove("hidden");
    refreshIcons();
  }
});

lockManagementBtn.addEventListener("click", () => {
  sessionStorage.removeItem("campora_mgmt_auth");
  navStudentBtn.click();
});

function showManagementDashboard() {
  studentView.classList.add("hidden");
  managementView.classList.remove("hidden");

  navManagementBtn.className = "px-4 py-2 rounded-lg bg-pastel-surface text-purple-950 border border-pastel-border shadow-xs transition-all flex items-center gap-2";
  navStudentBtn.className = "px-4 py-2 rounded-lg text-neutral-500 hover:text-purple-900 transition-all flex items-center gap-2";
  renderManagementTable();
}

// ==========================================
// DATA FETCHING & RENDERING
// ==========================================
async function fetchReports() {
  try {
    const res = await fetch(`${API_BASE}/reports`);
    cachedReports = await res.json();
    renderStudentFeed();
    if (!managementView.classList.contains("hidden")) {
      renderManagementTable();
    }
  } catch (err) {
    console.error("Backend offline:", err);
  }
}

async function fetchStats() {
  try {
    const res = await fetch(`${API_BASE}/stats`);
    const data = await res.json();
    document.getElementById("statTotal").textContent = data.total;
    document.getElementById("statSafety").textContent = data.safety;
    document.getElementById("statManagement").textContent = data.management;
    document.getElementById("statResolved").textContent = data.resolved;
  } catch (err) {
    console.error("Stats offline:", err);
  }
}

function renderStudentFeed() {
  const feed = document.getElementById("reportsFeed");
  const filtered = currentFilter === "all"
    ? cachedReports
    : cachedReports.filter(r => r.domain.toLowerCase() === currentFilter.toLowerCase());

  if (filtered.length === 0) {
    feed.innerHTML = `
      <div class="text-center py-12 bg-white border border-dashed border-pastel-border rounded-2xl">
        <i data-lucide="folder-search" class="w-8 h-8 mx-auto text-purple-300 mb-2"></i>
        <p class="text-neutral-500 text-xs">No reports found matching this track.</p>
      </div>`;
    refreshIcons();
    return;
  }

  feed.innerHTML = filtered.map(r => {
    const trackBadge = getTrackBadge(r.domain);
    const statusBadge = getStatusBadge(r.status);
    const reporterTag = r.anonymous
      ? `<span class="text-purple-800/80 text-xs font-mono flex items-center gap-1.5"><i data-lucide="shield-check" class="w-3.5 h-3.5 text-purple-500"></i> Anonymous Submission</span>`
      : `<span class="text-neutral-600 text-xs font-mono flex items-center gap-1.5"><i data-lucide="user" class="w-3.5 h-3.5 text-purple-500"></i> [${escapeHtml(r.reporterType)}] ${escapeHtml(r.reporterIdentity)}</span>`;

    const notesHtml = (r.notes && r.notes.length > 0)
      ? `<div class="mt-3 pt-3 border-t border-purple-50 space-y-1.5">
          <span class="text-[11px] font-semibold text-purple-900/70 uppercase tracking-wider block">Updates & Timeline</span>
          ${r.notes.map(n => `<p class="text-xs text-neutral-600 bg-pastel-subtle px-2.5 py-1.5 rounded-lg border border-purple-100/50">• ${escapeHtml(n)}</p>`).join("")}
         </div>`
      : "";

    return `
      <article class="bg-pastel-surface border border-pastel-border hover:border-purple-200 transition-all rounded-2xl p-5 shadow-xs hover:shadow-sm">
        <div class="flex items-start justify-between gap-4">
          <div class="space-y-1.5">
            <div class="flex items-center gap-2 flex-wrap">
              ${trackBadge}
              ${statusBadge}
              <span class="text-neutral-400 text-xs font-mono">#${r.id}</span>
              <span class="text-neutral-400 text-xs">• ${r.createdAt}</span>
            </div>
            <h3 class="text-base font-semibold text-pastel-text pt-0.5">${escapeHtml(r.title)}</h3>
            <p class="text-xs text-neutral-500 flex items-center gap-1.5">
              <i data-lucide="map-pin" class="w-3.5 h-3.5 text-purple-400"></i> ${escapeHtml(r.location)}
            </p>
          </div>

          <!-- Atomic Upvote Button -->
          <button onclick="endorseTicket('${r.id}')" class="flex flex-col items-center justify-center min-w-[54px] px-3 py-2 rounded-xl bg-purple-50 hover:bg-purple-100 border border-purple-200/60 text-purple-700 transition group">
            <i data-lucide="chevron-up" class="w-4 h-4 group-hover:-translate-y-0.5 transition"></i>
            <span class="text-xs font-mono font-bold mt-0.5">${r.supportCount}</span>
          </button>
        </div>

        <p class="text-xs text-neutral-600 mt-3 leading-relaxed">${escapeHtml(r.description)}</p>

        ${notesHtml}

        <div class="mt-4 pt-3 border-t border-pastel-border flex items-center justify-between text-xs">
          <div>${reporterTag}</div>
          <button onclick="promptAddNote('${r.id}')" class="text-purple-600 hover:text-purple-800 font-medium transition text-xs flex items-center gap-1">
            <i data-lucide="message-square-plus" class="w-3.5 h-3.5"></i> Add Observation
          </button>
        </div>
      </article>
    `;
  }).join("");

  refreshIcons();
}

function renderManagementTable() {
  const container = document.getElementById("managementTable");
  if (cachedReports.length === 0) {
    container.innerHTML = `<p class="text-xs text-neutral-400 py-6 text-center">No reports active in registry.</p>`;
    return;
  }

  container.innerHTML = cachedReports.map(r => {
    return `
      <div class="bg-pastel-surface border border-pastel-border rounded-2xl p-4 flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-xs">
        <div class="space-y-1">
          <div class="flex items-center gap-2">
            ${getTrackBadge(r.domain)}
            ${getStatusBadge(r.status)}
            <span class="text-xs font-mono text-neutral-400">#${r.id}</span>
            <span class="text-xs font-mono text-purple-800 bg-purple-50 px-2 py-0.5 rounded-md border border-purple-200">Urgency: ${r.urgency}</span>
            <span class="text-xs font-mono text-neutral-400">• ${r.supportCount} Upvotes</span>
          </div>
          <h4 class="text-sm font-semibold text-pastel-text mt-1">${escapeHtml(r.title)}</h4>
          <p class="text-xs text-neutral-500">📍 ${escapeHtml(r.location)} | Reporter: ${r.anonymous ? "Anonymous" : escapeHtml(r.reporterIdentity)}</p>
        </div>

        <div class="flex items-center gap-2 shrink-0">
          <button onclick="openResolveModal('${r.id}', '${escapeHtml(r.title)}', '${r.status}')" class="px-3.5 py-1.5 bg-purple-50 hover:bg-purple-100 border border-purple-200 rounded-xl text-xs font-semibold text-purple-900 transition flex items-center gap-1.5">
            <i data-lucide="edit-3" class="w-3.5 h-3.5 text-purple-600"></i> Manage
          </button>
        </div>
      </div>
    `;
  }).join("");

  refreshIcons();
}

// ==========================================
// ACTIONS (UPVOTE, NOTES, RESOLVE)
// ==========================================
window.endorseTicket = async function(id) {
  try {
    const res = await fetch(`${API_BASE}/endorse?id=${id}`, { method: "POST" });
    if (res.ok) {
      await fetchReports();
      await fetchStats();
    }
  } catch (err) {
    console.error("Endorse failed:", err);
  }
};

window.promptAddNote = async function(id) {
  const note = prompt("Enter observation, update, or witness note:");
  if (!note || note.trim() === "") return;

  try {
    const res = await fetch(`${API_BASE}/comment?id=${id}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ note: note.trim() })
    });
    if (res.ok) fetchReports();
  } catch (err) {
    console.error("Note failed:", err);
  }
};

window.openResolveModal = function(id, title, status) {
  resolveTicketId.value = id;
  resolveTicketTitle.textContent = title;
  newStatusSelect.value = (status === "PENDING" ? "IN_PROGRESS" : status);
  resolutionNoteInput.value = "";
  resolveModal.classList.remove("hidden");
  refreshIcons();
};

closeResolveModalBtn.addEventListener("click", () => resolveModal.classList.add("hidden"));

resolveForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const id = resolveTicketId.value;
  const status = newStatusSelect.value;
  const note = resolutionNoteInput.value.trim();

  try {
    const res = await fetch(`${API_BASE}/resolve`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ id, status, note })
    });
    if (res.ok) {
      resolveModal.classList.add("hidden");
      await fetchReports();
      await fetchStats();
    }
  } catch (err) {
    console.error("Resolve failed:", err);
  }
});

// ==========================================
// SUBMIT MODAL
// ==========================================
openSubmitModalBtn.addEventListener("click", () => {
  submitModal.classList.remove("hidden");
  refreshIcons();
});

closeSubmitModalBtn.addEventListener("click", () => submitModal.classList.add("hidden"));

anonCheckbox.addEventListener("change", (e) => {
  if (e.target.checked) {
    publicIdentityFields.classList.add("hidden");
  } else {
    publicIdentityFields.classList.remove("hidden");
  }
});

reportDomain.addEventListener("change", (e) => {
  const val = e.target.value;
  if (val === "safety") categoryLabel.textContent = "Hazard Type";
  else if (val === "suggestion") categoryLabel.textContent = "Initiative Scope";
  else categoryLabel.textContent = "Responsible Dept";
});

newReportForm.addEventListener("submit", async (e) => {
  e.preventDefault();

  const domain = reportDomain.value;
  const title = document.getElementById("reportTitle").value.trim();
  const location = document.getElementById("reportLocation").value.trim();
  const category = document.getElementById("reportCategory").value.trim();
  const description = document.getElementById("reportDescription").value.trim();
  const isAnon = anonCheckbox.checked;

  const reporterType = isAnon ? "ANONYMOUS" : document.getElementById("reporterType").value;
  const reporterIdentity = isAnon ? "ANONYMOUS" : (document.getElementById("reporterIdentity").value.trim() || reporterType);

  const payload = {
    title,
    location,
    category,
    description,
    domain,
    anonymous: isAnon ? "true" : "false",
    reporterType,
    reporter: reporterIdentity
  };

  try {
    const res = await fetch(`${API_BASE}/reports`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });

    if (res.ok) {
      newReportForm.reset();
      anonCheckbox.checked = true;
      publicIdentityFields.classList.add("hidden");
      submitModal.classList.add("hidden");
      await fetchReports();
      await fetchStats();
    }
  } catch (err) {
    console.error("Submit failed:", err);
  }
});

// ==========================================
// HELPERS
// ==========================================
function setupFilterPills() {
  const pills = document.querySelectorAll(".filter-pill");
  pills.forEach(p => {
    p.addEventListener("click", () => {
      pills.forEach(btn => {
        btn.className = "filter-pill px-3.5 py-1.5 rounded-lg border border-pastel-border bg-pastel-surface text-neutral-600 hover:border-purple-300";
      });
      p.className = "filter-pill px-3.5 py-1.5 rounded-lg border border-purple-300 bg-purple-100/70 text-purple-900 font-semibold shadow-xs";
      currentFilter = p.getAttribute("data-filter");
      renderStudentFeed();
    });
  });
}

function getTrackBadge(domain) {
  switch (domain.toLowerCase()) {
    case "safety":
      return `<span class="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-md bg-rose-50 border border-rose-200 text-rose-700">Safety Alert</span>`;
    case "suggestion":
      return `<span class="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-md bg-purple-50 border border-purple-200 text-purple-700">Suggestion</span>`;
    default:
      return `<span class="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-md bg-indigo-50 border border-indigo-200 text-indigo-700">Facilities</span>`;
  }
}

function getStatusBadge(status) {
  switch (status.toUpperCase()) {
    case "RESOLVED":
      return `<span class="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-md bg-emerald-50 border border-emerald-200 text-emerald-700">Resolved</span>`;
    case "IN_PROGRESS":
      return `<span class="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-md bg-amber-50 border border-amber-200 text-amber-700">In Progress</span>`;
    case "REJECTED":
      return `<span class="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-md bg-neutral-100 border border-neutral-300 text-neutral-600">Rejected</span>`;
    default:
      return `<span class="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-md bg-violet-50 border border-violet-200 text-violet-700">Pending</span>`;
  }
}

function escapeHtml(str) {
  if (!str) return "";
  return str.replace(/[&<>"']/g, m => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;"
  })[m]);
}
