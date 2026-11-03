const API_BASE = window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1"
  ? "http://localhost:8080/api"
  : "https://campora-h0sy.onrender.com/api";
const PASSCODE = "campora2026";

let currentStatusFilter = "all";
let cachedTickets = [];

document.addEventListener("DOMContentLoaded", () => {
  if (sessionStorage.getItem("campora_mgmt_auth") === "true") {
    document.getElementById("authGate").classList.add("hidden");
    fetchManagementTickets();
  }
  setupAuth();
  setupResolveModal();
  setupStatusTabs();
});

function refreshIcons() {
  if (window.lucide) lucide.createIcons();
}

function setupAuth() {
  const form = document.getElementById("passcodeForm");
  const input = document.getElementById("passcodeInput");
  const err = document.getElementById("passcodeError");
  const gate = document.getElementById("authGate");
  const lockBtn = document.getElementById("lockBtn");

  form.addEventListener("submit", (e) => {
    e.preventDefault();
    if (input.value.trim() === PASSCODE) {
      sessionStorage.setItem("campora_mgmt_auth", "true");
      gate.classList.add("hidden");
      fetchManagementTickets();
    } else {
      err.classList.remove("hidden");
    }
  });

  lockBtn.addEventListener("click", () => {
    sessionStorage.removeItem("campora_mgmt_auth");
    window.location.href = "../index.html";
  });
}

function setupStatusTabs() {
  const tabs = document.querySelectorAll(".status-tab");
  tabs.forEach(tab => {
    tab.addEventListener("click", () => {
      tabs.forEach(t => {
        t.className = "status-tab px-3.5 py-1.5 rounded-xl border border-pastel-border bg-white text-neutral-600 hover:border-purple-300 text-xs font-medium transition-all";
      });
      tab.className = "status-tab px-3.5 py-1.5 rounded-xl border border-purple-300 bg-purple-100/70 text-purple-900 font-semibold text-xs shadow-xs transition-all";

      currentStatusFilter = tab.getAttribute("data-status");
      renderTickets();
    });
  });
}

async function fetchManagementTickets() {
  try {
    const res = await fetch(`${API_BASE}/reports`);
    cachedTickets = await res.json();
    renderTickets();
  } catch (err) {
    console.error("Fetch tickets error:", err);
  }
}

function renderTickets() {
  const container = document.getElementById("ticketsContainer");
  const countBadge = document.getElementById("ticketCountBadge");

  const filtered = currentStatusFilter === "all"
    ? cachedTickets
    : cachedTickets.filter(r => r.status.toLowerCase() === currentStatusFilter.toLowerCase());

  if (countBadge) {
    countBadge.textContent = `${filtered.length} ticket(s)`;
  }

  if (!filtered || filtered.length === 0) {
    container.innerHTML = `
      <div class="text-center py-12 border border-dashed border-pastel-border rounded-2xl">
        <i data-lucide="inbox" class="w-8 h-8 mx-auto text-purple-300 mb-2"></i>
        <p class="text-xs text-neutral-400">No tickets matching status "${currentStatusFilter.toUpperCase()}".</p>
      </div>`;
    refreshIcons();
    return;
  }

  container.innerHTML = filtered.map(r => `
    <div class="bg-pastel-subtle border border-pastel-border rounded-2xl p-4 flex flex-col md:flex-row md:items-center justify-between gap-4">
      <div class="space-y-1">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="text-[10px] font-mono px-2 py-0.5 rounded bg-purple-100 text-purple-800 font-semibold uppercase">${escapeHtml(r.domain)}</span>
          ${getStatusBadge(r.status)}
          <span class="text-xs font-mono text-purple-700 bg-purple-50 px-2 py-0.5 rounded border border-purple-200">Urgency: ${r.urgency}</span>
          <span class="text-xs font-mono text-neutral-400">#${r.id}</span>
          <span class="text-xs font-mono text-neutral-400">• ${r.supportCount} Upvotes</span>
        </div>
        <h4 class="text-sm font-semibold text-pastel-text mt-1">${escapeHtml(r.title)}</h4>
        <p class="text-xs text-neutral-500">📍 ${escapeHtml(r.location)} | Reporter: ${r.anonymous ? "Anonymous" : escapeHtml(r.reporterIdentity)}</p>
      </div>

      <button onclick="openResolveModal('${r.id}', '${escapeHtml(r.title)}', '${r.status}')" class="px-3.5 py-2 bg-white hover:bg-purple-50 border border-purple-200 rounded-xl text-xs font-semibold text-purple-900 transition flex items-center gap-1.5 shrink-0">
        <i data-lucide="edit-3" class="w-3.5 h-3.5 text-purple-600"></i> Manage Ticket
      </button>
    </div>
  `).join("");

  refreshIcons();
}

function getStatusBadge(status) {
  switch (status.toUpperCase()) {
    case "RESOLVED":
      return `<span class="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 font-semibold border border-emerald-200">RESOLVED</span>`;
    case "IN_PROGRESS":
      return `<span class="text-[10px] font-mono px-2 py-0.5 rounded bg-amber-50 text-amber-700 font-semibold border border-amber-200">IN_PROGRESS</span>`;
    case "REJECTED":
      return `<span class="text-[10px] font-mono px-2 py-0.5 rounded bg-neutral-100 text-neutral-600 font-semibold border border-neutral-300">REJECTED</span>`;
    default:
      return `<span class="text-[10px] font-mono px-2 py-0.5 rounded bg-purple-50 text-purple-700 font-semibold border border-purple-200">PENDING</span>`;
  }
}

function setupResolveModal() {
  const modal = document.getElementById("resolveModal");
  const form = document.getElementById("resolveForm");
  const closeBtn = document.getElementById("closeResolveModalBtn");

  closeBtn.addEventListener("click", () => modal.classList.add("hidden"));

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const id = document.getElementById("resolveTicketId").value.trim();
    const status = document.getElementById("newStatusSelect").value.trim();
    const note = document.getElementById("resolutionNoteInput").value.trim();

    try {
      const res = await fetch(`${API_BASE}/resolve`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id, status, note })
      });
      if (res.ok) {
        modal.classList.add("hidden");
        form.reset();
        await fetchManagementTickets();
      } else {
        const errorData = await res.json().catch(() => ({}));
        alert("Update failed: " + (errorData.error || "Server returned status " + res.status));
      }
    } catch (err) {
      alert("Resolve request failed");
    }
  });
}

window.openResolveModal = function(id, title, status) {
  document.getElementById("resolveTicketId").value = id;
  document.getElementById("resolveTicketTitle").textContent = title;
  document.getElementById("newStatusSelect").value = status === "PENDING" ? "IN_PROGRESS" : status;
  document.getElementById("resolutionNoteInput").value = "";
  document.getElementById("resolveModal").classList.remove("hidden");
  refreshIcons();
};

function escapeHtml(str) {
  if (!str) return "";
  return str.replace(/[&<>"']/g, m => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[m]);
}
