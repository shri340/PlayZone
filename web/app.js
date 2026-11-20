const API = (window.PLAYZONE_API_BASE || "/api").replace(/\/+$/, "");
let equipment = [];
let draft = [];
let toastTimer;
const passedBondGroups = new Set();

const $ = (id) => document.getElementById(id);
const money = (n) => "\u20B9" + Number(n || 0).toFixed(2);

async function api(path, options = {}) {
  const response = await fetch(API + path, {
    headers: { "Content-Type": "application/json" },
    ...options
  });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || "Request failed.");
  return data;
}

function notify(message) {
  const el = $("toast");
  el.textContent = message;
  el.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove("show"), 2800);
}

function navigate(view) {
  document.querySelectorAll(".view").forEach(v => v.classList.toggle("active", v.id === view));
  document.querySelectorAll(".nav-link").forEach(b => b.classList.toggle("active", b.dataset.view === view));
  const titles = {dashboard:"Dashboard",inventory:"Equipment", "new-rental":"New rental",history:"Rental history",reports:"Reports",insights:"Smart insights","reality-rift":"Reality Rift",gamebond:"GameBond"};
  $("page-title").textContent = titles[view] || "Dashboard";
  if (view === "history") loadHistory();
  if (view === "reports") loadReport();
  if (view === "insights") loadInsights();
  if (view === "reality-rift") runRealityRift();
  if (view === "gamebond") loadGameBondLobbies();
  if (view === "dashboard" || view === "inventory") loadEquipment();
}

document.querySelectorAll("[data-view]").forEach(b => b.addEventListener("click", () => navigate(b.dataset.view)));
document.querySelectorAll("[data-go]").forEach(b => b.addEventListener("click", () => navigate(b.dataset.go)));

async function loadEquipment() {
  equipment = await api("/equipment");
  renderEquipment();
  updateStats();
}

function equipmentRow(item, showId = false) {
  return `<tr>${showId ? `<td>${item.id}</td>` : ""}<td>${item.name}</td><td>${item.category}</td><td>${money(item.rent)}</td><td>${item.units}</td><td><span class="status ${item.status.toLowerCase()}">${item.status}</span></td></tr>`;
}

function renderEquipment() {
  $("dashboard-equipment").innerHTML = equipment.map(e => equipmentRow(e)).join("");
  $("inventory-table").innerHTML = equipment.map(e => equipmentRow(e, true)).join("");
  $("equipment-select").innerHTML = equipment.map(e => `<option value="${e.id}" ${e.units===0?"disabled":""}>${e.name} · ${e.units} available · ${money(e.rent)}/day</option>`).join("");
}

async function updateStats() {
  const report = await api("/report");
  $("stat-rentals").textContent = report.totalRentals;
  $("stat-revenue").textContent = money(report.revenue);
  $("stat-equipment").textContent = equipment.length;
  $("stat-low").textContent = equipment.filter(e => e.units < 10).length;
}

function addDraftItem() {
  const id = Number($("equipment-select").value);
  const qty = Number($("equipment-qty").value);
  const item = equipment.find(e => e.id === id);
  if (!item) return notify("Choose an available equipment item.");
  if (!Number.isInteger(qty) || qty < 1) return notify("Quantity must be a positive whole number.");
  const existing = draft.find(d => d.id === id);
  const currentQty = existing ? existing.quantity : 0;
  if (currentQty + qty > item.units) return notify(`Only ${item.units - currentQty} more unit(s) available.`);
  if (existing) existing.quantity += qty;
  else draft.push({id, name:item.name, rent:item.rent, quantity:qty});
  $("equipment-qty").value = 1;
  renderDraft();
}

function renderDraft() {
  $("draft-list").innerHTML = draft.length ? draft.map((d,i) =>
    `<div class="draft-row"><div><strong>${d.name}</strong><small>${d.quantity} × ${money(d.rent)} per day</small></div><div><strong>${money(d.quantity*d.rent)}</strong> <button class="remove-item" data-remove="${i}" aria-label="Remove item">×</button></div></div>`
  ).join("") : `<div class="empty-state">No equipment added yet.</div>`;
  document.querySelectorAll("[data-remove]").forEach(b => b.addEventListener("click", () => {
    draft.splice(Number(b.dataset.remove),1); renderDraft();
  }));
  $("undo-item").disabled = draft.length === 0;
  updatePreview();
}

function updatePreview() {
  const subtotal = draft.reduce((sum,d) => sum + d.quantity*d.rent, 0);
  const rate = $("borrower-type").value === "Student" ? .10 : .15;
  const discount = subtotal * rate;
  const taxable = subtotal - discount;
  const gst = taxable * .18;
  $("preview-lines").innerHTML = draft.length ? draft.map(d =>
    `<div class="preview-item"><span>${d.name} <small>× ${d.quantity}</small></span><strong>${money(d.quantity*d.rent)}</strong></div>`
  ).join("") : `<p class="muted">Your selected items will appear here.</p>`;
  $("discount-label").textContent = ` ${$("borrower-type").value} discount (${rate*100}%)`;
  $("preview-subtotal").textContent = money(subtotal);
  $("preview-discount").textContent = "−" + money(discount);
  $("preview-gst").textContent = money(gst);
  $("preview-total").textContent = money(taxable+gst);
}

async function submitRental() {
  const name = $("borrower-name").value.trim();
  const type = $("borrower-type").value;
  if (!name) return notify("Enter the borrower's name.");
  if (!draft.length) return notify("Add at least one equipment item.");
  try {
    const bill = await api("/rentals", {
      method:"POST",
      body:JSON.stringify({name,type,items:draft.map(d=>({id:d.id,quantity:d.quantity}))})
    });
    draft = [];
    $("borrower-name").value = "";
    renderDraft();
    await loadEquipment();
    showBill(bill);
    notify("Rental created successfully.");
  } catch (error) {
    notify(error.message);
  }
}

function billMarkup(b) {
  return `<div class="bill-head"><div><p class="eyebrow">PLAYZONE · RENTAL RECEIPT</p><h3>${b.id}</h3><p>${escapeHtml(b.name)} · ${b.type}</p></div><div class="bill-total">${money(b.total)}</div></div>
    <div class="bill-meta">Token #${b.token} · Collect equipment from <strong>${b.room}</strong></div>
    ${b.items.map(i=>`<div class="bill-line"><span>${escapeHtml(i.name)} × ${i.quantity}</span><strong>${money(i.amount)}</strong></div>`).join("")}
    <div class="bill-footer"><div class="summary-line"><span>Subtotal</span><strong>${money(b.subtotal)}</strong></div><div class="summary-line"><span>Discount (${b.discountRate}%)</span><strong>−${money(b.discount)}</strong></div><div class="summary-line"><span>GST (18%)</span><strong>${money(b.gst)}</strong></div><div class="summary-total"><span>Final total</span><strong>${money(b.total)}</strong></div></div>`;
}

function showBill(bill) {
  $("modal-content").innerHTML = billMarkup(bill);
  $("bill-modal").classList.remove("hidden");
}

async function loadHistory() {
  const rows = await api("/rentals");
  $("history-table").innerHTML = rows.length ? rows.map(r =>
    `<tr><td>${r.id}</td><td>${escapeHtml(r.name)}</td><td>${r.type}</td><td>${money(r.total)}</td><td><button class="text-btn" data-bill="${r.id}">View bill →</button></td></tr>`
  ).join("") : `<tr><td colspan="5">No rentals recorded yet.</td></tr>`;
  document.querySelectorAll("[data-bill]").forEach(b => b.addEventListener("click", () => lookupBill(b.dataset.bill)));
}

async function lookupBill(id) {
  try {
    const bill = await api("/rentals/" + encodeURIComponent(id.trim()));
    $("bill-result").innerHTML = `<div class="bill-card">${billMarkup(bill)}</div>`;
  } catch (e) {
    $("bill-result").innerHTML = `<div class="info-banner">${escapeHtml(e.message)}</div>`;
  }
}

async function loadReport() {
  const r = await api("/report");
  $("report-rentals").textContent = r.totalRentals;
  $("report-revenue").textContent = money(r.revenue);
  $("report-popular").textContent = r.popular;
}


async function loadInsights() {
  const data = await api("/insights");
  $("insight-low").textContent = data.lowStockCount;
  $("insight-rentals").textContent = data.totalRentals;
  const ranked = [...data.items].sort((a,b) => b.rented - a.rented);
  const leader = ranked.length && ranked[0].rented > 0 ? ranked[0].name : "No data yet";
  $("insight-leader").textContent = leader;
  const max = Math.max(1, ...data.items.map(i => i.rented));
  $("demand-bars").innerHTML = ranked.map(item => {
    const width = item.rented === 0 ? 0 : Math.max(4, item.rented / max * 100);
    return `<div class="demand-row"><span class="demand-name">${escapeHtml(item.name)}</span><div class="demand-track"><div class="demand-fill" style="width:${width}%"></div></div><span class="demand-value">${item.rented} rented</span></div>`;
  }).join("");
  $("restock-table").innerHTML = data.items.map(item => {
    const cls = item.risk.toLowerCase();
    const restock = item.suggestedRestock === 0 ? "No action" : `+${item.suggestedRestock} units`;
    return `<tr><td>${escapeHtml(item.name)}</td><td>${item.available}</td><td>${item.rented}</td><td><span class="risk-pill ${cls}">${item.risk}</span></td><td><strong>${restock}</strong></td></tr>`;
  }).join("");
}


// REALITY RIFT: compare current stock against three transparent future demand scenarios.
// Profiles represent illustrative item quantities for a 30-person event, not predictions.
const riftWorlds = [
  {
    id:"practice", name:"The Calm Campus", tag:"UNIVERSE 01 · ROUTINE DAY",
    description:"A normal afternoon practice session with balanced equipment demand.",
    profile:[1,2,3,1,1,1]
  },
  {
    id:"tournament", name:"The Tournament Surge", tag:"UNIVERSE 02 · CROWD EVENT",
    description:"Multiple teams arrive together for an inter-college sports event.",
    profile:[5,7,8,4,6,2]
  },
  {
    id:"monsoon", name:"The Monsoon Flip", tag:"UNIVERSE 03 · INDOOR SHIFT",
    description:"Outdoor play drops; indoor activities and racket sports spike.",
    profile:[0,3,5,1,0,6]
  }
];

function updateRiftControlLabels() {
  $("rift-participants-value").textContent = `${$("rift-participants").value} people`;
  $("rift-pressure-value").textContent = `${Number($("rift-pressure").value).toFixed(1)}×`;
}

async function runRealityRift() {
  const button = $("rift-run");
  button.disabled = true;
  button.textContent = "Opening realities…";
  try {
    // Pull current live stock from the Java server for every simulation run.
    const liveEquipment = await api("/equipment");
    const people = Number($("rift-participants").value);
    const pressure = Number($("rift-pressure").value);
    const includeDraft = $("rift-include-draft").checked;
    const draftById = {};
    if (includeDraft) draft.forEach(line => draftById[line.id] = (draftById[line.id] || 0) + line.quantity);

    const simulations = riftWorlds.map(world => {
      const lines = liveEquipment.map((item, index) => {
        const draftQty = draftById[item.id] || 0;
        const availableNow = Math.max(0, item.units - draftQty);
        const projected = Math.ceil(world.profile[index] * (people / 30) * pressure);
        const served = Math.min(availableNow, projected);
        const gap = Math.max(0, projected - availableNow);
        return {name:item.name,available:availableNow,projected,served,gap};
      });
      const demand = lines.reduce((sum,line)=>sum+line.projected,0);
      const supplied = lines.reduce((sum,line)=>sum+line.served,0);
      const gaps = lines.filter(line=>line.gap>0);
      const score = demand === 0 ? 100 : Math.round(supplied / demand * 100);
      return {...world,lines,demand,supplied,gaps,score};
    });

    const totalGaps = simulations.reduce((sum,world)=>sum+world.gaps.reduce((n,line)=>n+line.gap,0),0);
    $("rift-meta").textContent = `SIMULATION COMPLETE · ${people} PARTICIPANTS · ${pressure.toFixed(1)}× PRESSURE · ${includeDraft ? "UNSAVED DRAFT INCLUDED" : "DRAFT EXCLUDED"} · LIVE STOCK READ ${new Date().toLocaleTimeString()}`;
    $("rift-results").innerHTML = simulations.map(world => {
      const verdict = world.score >= 95 ? "REALITY STABLE" : world.score >= 75 ? "UNDER PRESSURE" : "STOCK BREACH";
      const rescue = world.gaps.length
        ? world.gaps.map(line=>`${escapeHtml(line.name)} +${line.gap}`).join(" · ")
        : "No shortages in this scenario. Your inventory can cover the projected demand.";
      return `<article class="rift-card">
        <div class="rift-card-top"><span class="rift-world">${world.tag}</span><span class="rift-verdict">${verdict}</span><h3>${world.name}</h3><p>${world.description}</p></div>
        <div class="rift-score-row"><strong class="rift-score">${world.score}%</strong><span class="rift-score-label">projected demand<br>covered by available stock</span></div>
        <div class="rift-item-list">${world.lines.map(line=>{
          const width = line.projected === 0 ? 0 : Math.round(line.served / line.projected * 100);
          return `<div class="rift-item"><div class="rift-item-head"><strong>${escapeHtml(line.name)}</strong><span>${line.served}/${line.projected} covered</span></div><div class="rift-item-track"><div class="rift-item-fill" style="width:${width}%"></div></div>${line.gap ? `<div class="rift-gap">Short by ${line.gap} unit(s) · ${line.available} currently available</div>` : ""}</div>`;
        }).join("")}</div>
        <div class="rift-rescue ${world.gaps.length ? "" : "rift-safe"}"><strong>${world.gaps.length ? `COUNTER-MOVE · ${world.gaps.reduce((n,l)=>n+l.gap,0)} UNIT(S) GAP` : "COUNTER-MOVE · NO ACTION"}</strong>${rescue}</div>
      </article>`;
    }).join("");

    if (totalGaps === 0) notify("All three simulated realities are stable.");
    else notify(`Reality Rift found ${totalGaps} projected stock gap(s) across the three futures.`);
  } catch (error) {
    notify("Reality Rift could not read live stock: " + error.message);
  } finally {
    button.disabled = false;
    button.textContent = "◈ Re-run the realities";
  }
}


function bondProfile() {
  return {
    nickname:$("bond-nickname").value.trim(),
    game:$("bond-game").value,
    skill:$("bond-skill").value,
    time:$("bond-time").value,
    budget:Number($("bond-budget").value)
  };
}
function bondCard(lobby) {
  const nickname = $("bond-nickname").value.trim().toLowerCase();
  const me = (lobby.players || []).some(name => name.toLowerCase() === nickname);
  const avatars=(lobby.players||[]).map(name=>`<span class="bond-avatar" title="${escapeHtml(name)}">${escapeHtml(name.slice(0,1).toUpperCase())}</span>`).join("");
  const score=lobby.compatibility ? `<span class="bond-score">${lobby.compatibility}% VIBE MATCH</span>` : "";
  const share = me ? lobby.currentShare : lobby.shareIfJoined;
  const action = me
    ? `<div class="bond-member-state">✓ YOU'RE IN THIS CREW</div>`
    : `<div class="bond-swipe-actions"><button class="bond-pass" data-bond-pass="${escapeHtml(lobby.id)}">Pass for now</button><button class="primary-btn" data-bond-join="${escapeHtml(lobby.id)}">♡ I'm in</button></div>`;
  return `<article class="bond-card" data-bond-card="${escapeHtml(lobby.id)}"><div class="bond-card-top"><div class="bond-game-icon">${({"Chess":"♟","Carrom":"◉","Badminton":"🏸","Football":"⚽","Basketball":"🏀","Table Tennis":"🏓","Volleyball":"🏐","Cricket":"🏏"})[lobby.game]||"🎮"}</div><div><h4>${escapeHtml(lobby.game)} crew</h4><p>${escapeHtml(lobby.skill)} · ${escapeHtml(lobby.time)}</p></div>${score}</div><div class="bond-players">${avatars}<small>${lobby.playerCount}/${lobby.maxPlayers} players · hosted by ${escapeHtml(lobby.host)}</small></div><div class="bond-cost"><span>${me ? "Your current estimated share" : "Estimated share if you join now"}</span><strong>${money(share)}</strong></div>${action}<small class="bond-id">GROUP ${escapeHtml(lobby.id)} · Total estimate ${money(lobby.totalCost)} · Share may change if more players join</small></article>`;
}
async function loadGameBondLobbies() {
  try {
    const [members,lobbies]=await Promise.all([
      api("/gamebond/members"), api("/gamebond/lobbies")
    ]);
    $("bond-member-total").textContent = `${members.length} DEMO MEMBERS`;
    const visible = lobbies.filter(lobby => !passedBondGroups.has(lobby.id));
    $("bond-match-count").textContent = `${visible.length} OPEN GROUP${visible.length===1?"":"S"}`;
    $("bond-results").innerHTML=visible.length?visible.map(bondCard).join(""):`<div class="bond-empty"><span>♡</span><h3>No open groups yet.</h3><p>Create the first group and invite others on the same PlayZone server to discover it.</p></div>`;
  } catch(e) { notify("Could not load GameBond groups: "+e.message); }
}
async function findBondMatches() {
  try {
    const profile=bondProfile();
    if(!profile.nickname) throw new Error("Enter your nickname first.");
    if(!Number.isFinite(profile.budget)||profile.budget<0) throw new Error("Enter a valid budget.");
    const matches=await api("/gamebond/recommend",{method:"POST",body:JSON.stringify(profile)});
    const visible = matches.filter(lobby => !passedBondGroups.has(lobby.id));
    $("bond-match-count").textContent = `${visible.length} VIBE MATCH${visible.length===1?"":"ES"}`;
    $("bond-results").innerHTML=visible.length?visible.map(bondCard).join(""):`<div class="bond-empty"><span>♡</span><h3>No compatible group found yet.</h3><p>Try another time, skill level or budget—or create your own group.</p></div>`;
    if(visible.length) notify(`${visible.length} compatible game group(s) found.`);
  } catch(e) { notify(e.message); }
}
async function createBondGroup() {
  try {
    const nickname=$("bond-nickname").value.trim();
    if(!nickname) throw new Error("Enter your nickname in Your Play Profile first.");
    const payload={nickname,game:$("bond-host-game").value,skill:$("bond-host-skill").value,
      time:$("bond-host-time").value,maxPlayers:Number($("bond-max").value),totalCost:Number($("bond-cost").value)};
    const created=await api("/gamebond/lobbies",{method:"POST",body:JSON.stringify(payload)});
    notify(`Group ${created.id} created. You're hosting — waiting for players.`);
    await loadGameBondLobbies();
  } catch(e) { notify(e.message); }
}
async function joinBondGroup(id) {
  try {
    const nickname=$("bond-nickname").value.trim();
    if(!nickname) throw new Error("Enter your nickname in Your Play Profile first.");
    const joined=await api("/gamebond/join",{method:"POST",body:JSON.stringify({id,nickname})});
    notify(`You're in! Your current estimated share: ${money(joined.currentShare)}.`);
    await loadGameBondLobbies();
  } catch(e) { notify(e.message); }
}
function passBondGroup(id) {
  passedBondGroups.add(id);
  const card = document.querySelector(`[data-bond-card="${CSS.escape(id)}"]`);
  if (card) { card.classList.add("bond-card-passed"); setTimeout(() => card.remove(), 180); }
  const count = document.querySelectorAll("[data-bond-card]").length - 1;
  $("bond-match-count").textContent = `${Math.max(0,count)} GROUP${count===1?"":"S"} LEFT TO EXPLORE`;
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
}

$("add-item").addEventListener("click", addDraftItem);
$("undo-item").addEventListener("click", () => { if (draft.length) { draft.pop(); renderDraft(); } });
$("clear-items").addEventListener("click", () => { draft=[]; renderDraft(); });
$("submit-rental").addEventListener("click", submitRental);
$("borrower-type").addEventListener("change", updatePreview);
$("search-bill").addEventListener("click", () => lookupBill($("bill-search").value));
$("bill-search").addEventListener("keydown", e => { if(e.key==="Enter") lookupBill($("bill-search").value); });
$("refresh-report").addEventListener("click", loadReport);
$("refresh-insights").addEventListener("click", loadInsights);
$("rift-participants").addEventListener("input", updateRiftControlLabels);
$("rift-pressure").addEventListener("input", updateRiftControlLabels);
$("rift-run").addEventListener("click", runRealityRift);
$("bond-find").addEventListener("click", findBondMatches);
$("bond-create").addEventListener("click", createBondGroup);
$("bond-refresh").addEventListener("click", loadGameBondLobbies);
$("bond-results").addEventListener("click", e => {
  const join=e.target.closest("[data-bond-join]");
  const pass=e.target.closest("[data-bond-pass]");
  if(join) joinBondGroup(join.dataset.bondJoin);
  if(pass) passBondGroup(pass.dataset.bondPass);
});
document.querySelectorAll("[data-close]").forEach(b => b.addEventListener("click", () => $("bill-modal").classList.add("hidden")));
$("print-bill").addEventListener("click", () => window.print());

(async function init() {
  try {
    const savedBondNickname = localStorage.getItem("playzoneBondNickname");
    if (savedBondNickname) $("bond-nickname").value = savedBondNickname;
    $("bond-nickname").addEventListener("change", () => localStorage.setItem("playzoneBondNickname", $("bond-nickname").value.trim()));
    await loadEquipment();
    renderDraft();
    await loadReport();
    updateRiftControlLabels();
  } catch (e) {
    notify("Cannot connect to Java server. Start PlayZoneWebServer first.");
  }
})();
