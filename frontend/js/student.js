const API_BASE = window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1"
  ? "http://localhost:8080/api"
  : "https://campora-h0sy.onrender.com/api";

let currentFilter = "all";
let cachedReports = [];
let chartInstance = null;

document.addEventListener("DOMContentLoaded", () => {
  fetchReports();
  fetchStats();
  setupFilterPills();
  setupModal();
});

function refreshIcons() {
  if (window.lucide) lucide.createIcons();
}

async function fetchReports() {
  try {
    const res = await fetch(`${API_BASE}/reports`);
    if (!res.ok) throw new Error("Server error " + res.status);
    cachedReports = await res.json();
    renderStudentFeed();
  } catch (err) {
    console.error("Fetch reports error:", err);
    document.getElementById("reportsFeed").innerHTML = `
      <div class="p-6 bg-rose-50 border border-rose-200 rounded-2xl text-rose-700 text-xs">
        Could not connect to Java backend on http://localhost:8080. Ensure Main.java is running!
      </div>`;
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

    document.getElementById("legendPending").textContent = data.pending || 0;
    document.getElementById("legendProgress").textContent = data.in_progress || 0;
    document.getElementById("legendResolved").textContent = data.resolved || 0;

    renderChart(data.pending || 0, data.in_progress || 0, data.resolved || 0);
  } catch (err) {
    console.error("Stats offline:", err);
  }
}

function renderChart(pending, inProgress, resolved) {
  const ctx = document.getElementById("resolutionChart").getContext("2d");
  if (chartInstance) chartInstance.destroy();

  chartInstance = new Chart(ctx, {
    type: "doughnut",
    data: {
      labels: ["Pending", "In Progress", "Resolved"],
      datasets: [{
        data: [pending, inProgress, resolved],
        backgroundColor: ["#f59e0b", "#8b5cf6", "#10b981"],
        borderWidth: 2,
        borderColor: "#ffffff"
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false }
      },
      cutout: "70%"
    }
  });
}

function renderStudentFeed() {
  const feed = document.getElementById("reportsFeed");
  const filtered = currentFilter === "all"
    ? cachedReports
    : cachedReports.filter(r => r.domain.toLowerCase() === currentFilter.toLowerCase());

  document.getElementById("feedCount").textContent = `${filtered.length} ticket(s) found`;

  if (filtered.length === 0) {
    feed.innerHTML = `
      <div class="text-center py-12 bg-white border border-dashed border-pastel-border rounded-3xl">
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
      ? `<span class="text-purple-800/80 text-xs font-mono flex items-center gap-1.5"><i data-lucide="shield-check" class="w-3.5 h-3.5 text-purple-500"></i> Anonymous</span>`
      : `<span class="text-neutral-600 text-xs font-mono flex items-center gap-1.5"><i data-lucide="user" class="w-3.5 h-3.5 text-purple-500"></i> [${escapeHtml(r.reporterType)}] ${escapeHtml(r.reporterIdentity)}</span>`;

    const notesHtml = (r.notes && r.notes.length > 0)
      ? `<div class="mt-3 pt-3 border-t border-purple-50 space-y-1.5">
          <span class="text-[11px] font-semibold text-purple-900/70 uppercase tracking-wider block">Updates & Timeline</span>
          ${r.notes.map(n => `<p class="text-xs text-neutral-600 bg-pastel-subtle px-2.5 py-1.5 rounded-lg border border-purple-100/50">• ${escapeHtml(n)}</p>`).join("")}
         </div>`
      : "";

    return `
      <article class="bg-white border border-pastel-border hover:border-purple-200 transition-all rounded-3xl p-6 shadow-xs hover:shadow-sm">
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

          <button onclick="endorseTicket('${r.id}')" class="flex flex-col items-center justify-center min-w-[54px] px-3 py-2 rounded-2xl bg-purple-50 hover:bg-purple-100 border border-purple-200/60 text-purple-700 transition group">
            <i data-lucide="chevron-up" class="w-4 h-4 group-hover:-translate-y-0.5 transition"></i>
            <span class="text-xs font-mono font-bold mt-0.5">${r.supportCount}</span>
          </button>
        </div>

        <p class="text-xs text-neutral-600 mt-3 leading-relaxed">${escapeHtml(r.description)}</p>
        ${notesHtml}

        <div class="mt-4 pt-3 border-t border-pastel-border flex items-center justify-between text-xs">
          <div>${reporterTag}</div>
          <button onclick="promptAddNote('${r.id}')" class="text-purple-600 hover:text-purple-800 font-medium transition text-xs flex items-center gap-1">
            <i data-lucide="message-square-plus" class="w-3.5 h-3.5"></i> Add Note
          </button>
        </div>
      </article>
    `;
  }).join("");

  refreshIcons();
}

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
  const note = prompt("Enter observation or witness note:");
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

function setupModal() {
  const submitModal = document.getElementById("submitModal");
  const openBtn = document.getElementById("openSubmitModalBtn");
  const closeBtn = document.getElementById("closeSubmitModalBtn");
  const form = document.getElementById("newReportForm");
  const anonCheckbox = document.getElementById("anonCheckbox");
  const publicFields = document.getElementById("publicIdentityFields");

  openBtn.addEventListener("click", () => {
    submitModal.classList.remove("hidden");
    refreshIcons();
  });
  closeBtn.addEventListener("click", () => submitModal.classList.add("hidden"));

  anonCheckbox.addEventListener("change", (e) => {
    publicFields.classList.toggle("hidden", e.target.checked);
  });

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const isAnon = anonCheckbox.checked;

    const payload = {
      title: document.getElementById("reportTitle").value.trim(),
      location: document.getElementById("reportLocation").value.trim(),
      category: document.getElementById("reportCategory").value.trim(),
      description: document.getElementById("reportDescription").value.trim(),
      domain: document.getElementById("reportDomain").value,
      anonymous: isAnon ? "true" : "false",
      reporterType: isAnon ? "ANONYMOUS" : document.getElementById("reporterType").value,
      reporter: isAnon ? "ANONYMOUS" : (document.getElementById("reporterIdentity").value.trim() || "Student")
    };

    try {
      const res = await fetch(`${API_BASE}/reports`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });

      if (res.ok) {
        form.reset();
        anonCheckbox.checked = true;
        publicFields.classList.add("hidden");
        submitModal.classList.add("hidden");
        await fetchReports();
        await fetchStats();
      } else {
        const errorData = await res.json();
        alert("Submission failed: " + (errorData.error || "Server error"));
      }
    } catch (err) {
      alert("Could not reach server on localhost:8080. Please ensure Java backend is running!");
    }
  });
}

function setupFilterPills() {
  const pills = document.querySelectorAll(".filter-pill");
  pills.forEach(p => {
    p.addEventListener("click", () => {
      pills.forEach(btn => {
        btn.className = "filter-pill px-4 py-2 rounded-xl border border-pastel-border bg-white text-neutral-600 hover:border-purple-300";
      });
      p.className = "filter-pill px-4 py-2 rounded-xl border border-purple-300 bg-purple-100/70 text-purple-900 font-semibold shadow-xs";
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
