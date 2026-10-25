let isUninstallMode = false;
let installPath = "";

async function callApi(cmd, payload = {}) {
  const res = await fetch("/api", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ cmd, payload })
  });
  const data = await res.json();
  if (data.error) throw new Error(data.error);
  return data.result;
}

document.addEventListener("DOMContentLoaded", async () => {
  try {
    const info = await callApi("getInfo");
    isUninstallMode = info.isUninstall || false;
    installPath = info.installDir || "";

    const pathDisplay = document.getElementById("pathDisplay");
    if (pathDisplay) pathDisplay.textContent = installPath;

    if (isUninstallMode) {
      document.getElementById("wizardTitle").innerHTML = 'Not Defterim Kaldırma Sihirbazı <span class="badge">v1.0.0</span>';
      document.getElementById("wizardSubtitle").textContent = 'Uygulamayı sisteminizden kaldırma işlemi';
      document.getElementById("stepOptions").style.display = "none";
      document.getElementById("stepUninstall").style.display = "block";
      const btnAction = document.getElementById("btnAction");
      btnAction.textContent = "Kaldır";
      btnAction.className = "btn btn-danger";
    }
  } catch (e) {
    console.error("Bilgiler alınamadı:", e);
  }

  document.getElementById("btnCancel").addEventListener("click", () => {
    callApi("exit");
  });

  document.getElementById("btnAction").addEventListener("click", async () => {
    if (isUninstallMode) {
      runUninstall();
    } else {
      runInstall();
    }
  });
});

async function runInstall() {
  const stepOptions = document.getElementById("stepOptions");
  const stepProgress = document.getElementById("stepProgress");
  const stepDone = document.getElementById("stepDone");
  const btnAction = document.getElementById("btnAction");
  const btnCancel = document.getElementById("btnCancel");
  const progressText = document.getElementById("progressText");
  const progressBar = document.getElementById("progressBar");

  stepOptions.style.display = "none";
  stepProgress.style.display = "flex";
  btnAction.style.display = "none";
  btnCancel.style.display = "none";

  const desktop = document.getElementById("chkDesktop").checked;
  const startMenu = document.getElementById("chkStartMenu").checked;
  const launch = document.getElementById("chkLaunch").checked;

  try {
    progressText.textContent = "Kurulum dizini hazırlanıyor...";
    progressBar.style.width = "25%";
    await new Promise(r => setTimeout(r, 200));

    progressText.textContent = "Not Defterim dosyaları kopyalanıyor...";
    progressBar.style.width = "60%";

    await callApi("install", {
      desktopShortcut: desktop,
      startMenuShortcut: startMenu,
      launchAfter: launch
    });

    progressText.textContent = "Kısayollar ve sistem kayıtları tamamlanıyor...";
    progressBar.style.width = "90%";
    await new Promise(r => setTimeout(r, 200));

    progressBar.style.width = "100%";
    await new Promise(r => setTimeout(r, 200));

    stepProgress.style.display = "none";
    stepDone.style.display = "flex";
    btnAction.style.display = "inline-block";
    btnAction.textContent = launch ? "Kapat ve Not Defterim'i Aç" : "Kapat";
    btnAction.className = "btn btn-primary";

    btnAction.onclick = () => {
      callApi("exit");
    };
  } catch (err) {
    progressText.textContent = "Kurulum Hatası: " + err.message;
    progressText.style.color = "#EF4444";
    btnCancel.style.display = "inline-block";
    btnCancel.textContent = "Kapat";
  }
}

async function runUninstall() {
  const stepUninstall = document.getElementById("stepUninstall");
  const stepProgress = document.getElementById("stepProgress");
  const stepDone = document.getElementById("stepDone");
  const btnAction = document.getElementById("btnAction");
  const btnCancel = document.getElementById("btnCancel");
  const progressText = document.getElementById("progressText");
  const progressBar = document.getElementById("progressBar");

  stepUninstall.style.display = "none";
  stepProgress.style.display = "flex";
  btnAction.style.display = "none";
  btnCancel.style.display = "none";

  try {
    progressText.textContent = "Kısayollar ve kayıtlar temizleniyor...";
    progressBar.style.width = "50%";

    await callApi("uninstall");

    progressBar.style.width = "100%";
    await new Promise(r => setTimeout(r, 300));

    stepProgress.style.display = "none";
    stepDone.style.display = "flex";

    document.getElementById("doneIcon").textContent = "🗑️";
    document.getElementById("doneTitle").textContent = "Kaldırma İşlemi Tamamlandı";
    document.getElementById("doneDesc").textContent = "Not Defterim başarıyla kaldırıldı. Not verileriniz güvende tutuldu.";

    btnAction.style.display = "inline-block";
    btnAction.textContent = "Kapat";
    btnAction.className = "btn btn-secondary";

    btnAction.onclick = () => {
      callApi("exit");
    };
  } catch (err) {
    progressText.textContent = "Kaldırma Hatası: " + err.message;
    progressText.style.color = "#EF4444";
    btnCancel.style.display = "inline-block";
    btnCancel.textContent = "Kapat";
  }
}
