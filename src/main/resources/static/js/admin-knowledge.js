(function () {
  "use strict";

  const page = document.getElementById("kbPage");
  if (!page || !window.bootstrap) {
    return;
  }

  const API = page.dataset.api || "/admin/knowledge";
  const MAX_BYTES = parseInt(page.dataset.maxBytes, 10) || 5 * 1024 * 1024;
  const ALLOWED_EXTENSIONS = ["pdf", "md", "txt", "html"];
  const POLL_MS = 5000;

  const AUDIENCE_LABELS = {
    ALL: "Todos",
    TRAINER: "Entrenadores",
    ADMIN: "Administradores",
  };
  const STATUS_LABELS = {
    INDEXED: "Indexado",
    PENDING: "Pendiente de indexar",
    FAILED: "Error al indexar",
    MISSING: "Archivo no encontrado",
  };
  const OUTCOME_LABELS = {
    INGESTED: "Indexado",
    SKIPPED_UNCHANGED: "Sin cambios",
    SKIPPED_IGNORED: "Omitido",
    FAILED: "Error",
    RATE_LIMITED: "Detenido por límite del proveedor",
    PENDING: "Pendiente",
    RETIRED: "Retirado",
  };

  const byId = function (id) {
    return document.getElementById(id);
  };
  const el = {
    statDocuments: byId("kbStatDocuments"),
    statChunks: byId("kbStatChunks"),
    statModel: byId("kbStatModel"),
    statIndex: byId("kbStatIndex"),
    statIndexNote: byId("kbStatIndexNote"),
    pending: byId("kbPending"),
    count: byId("kbCount"),
    ingest: byId("kbIngest"),
    reindex: byId("kbReindex"),
    openUpload: byId("kbOpenUpload"),
    tbody: byId("kbTableBody"),
    empty: byId("kbEmpty"),
    uploadModal: byId("uploadModal"),
    form: byId("kbUploadForm"),
    modalError: byId("kbModalError"),
    dropzone: byId("kbDropzone"),
    file: byId("kbFile"),
    fileLabel: byId("kbFileLabel"),
    audience: byId("kbAudience"),
    autoIndex: byId("kbAutoIndex"),
    upload: byId("kbUpload"),
    resultModal: byId("resultModal"),
    resultSummary: byId("kbResultSummary"),
    resultList: byId("kbResultList"),
  };

  const uploadModal = bootstrap.Modal.getOrCreateInstance(el.uploadModal);
  const resultModal = bootstrap.Modal.getOrCreateInstance(el.resultModal);
  const state = {
    uploading: false,
    ingesting: false,
    serverRunning: false,
    pollTimer: null,
  };

  class ApiError extends Error {
    constructor(message, status, code) {
      super(message);
      this.status = status;
      this.code = code;
    }
  }

  function csrfToken() {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : "";
  }

  async function request(path, options) {
    const settings = options || {};
    const method = settings.method || "GET";
    const headers = { Accept: "application/json" };
    if (method !== "GET") {
      headers["X-XSRF-TOKEN"] = csrfToken();
    }

    let response;
    try {
      response = await fetch(API + path, {
        method: method,
        headers: headers,
        body: settings.body,
        credentials: "same-origin",
      });
    } catch (networkError) {
      throw new ApiError(
        "No se pudo conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.",
        0,
      );
    }

    if (response.redirected) {
      throw new ApiError(
        "Tu sesión expiró. Recarga la página para continuar.",
        401,
      );
    }
    if (response.status === 204) {
      return null;
    }

    const isJson = (response.headers.get("Content-Type") || "").includes(
      "application/json",
    );
    const payload = isJson
      ? await response.json().catch(function () {
          return null;
        })
      : null;

    if (!response.ok) {
      let message = payload && payload.message ? payload.message : null;
      if (!message && response.status === 403) {
        message =
          "No se pudo validar tu sesión. Recarga la página e inténtalo de nuevo.";
      }
      throw new ApiError(
        message || "No se pudo completar la solicitud. Inténtalo de nuevo.",
        response.status,
        payload ? payload.code : null,
      );
    }
    return payload;
  }

  let progressToast = null;

  function closeProgress() {
    if (progressToast) {
      progressToast.close();
      progressToast = null;
    }
  }

  function showMessage(text, type) {
    closeProgress();
    if (type === "info") {
      progressToast = FLToast.info(text, { duration: 0 });
    } else if (type === "success") {
      FLToast.success(text);
    } else {
      FLToast.error(text);
    }
  }

  function clearMessage() {
    closeProgress();
  }

  function showModalError(text) {
    el.modalError.textContent = text;
    el.modalError.hidden = false;
  }

  function clearModalError() {
    el.modalError.hidden = true;
    el.modalError.textContent = "";
  }

  function formatSize(bytes) {
    if (bytes >= 1024 * 1024) {
      return (bytes / (1024 * 1024)).toFixed(1) + " MB";
    }
    return Math.max(1, Math.round(bytes / 1024)) + " KB";
  }

  function formatDate(iso) {
    if (!iso) {
      return "—";
    }
    return new Date(iso).toLocaleString("es-CO", {
      dateStyle: "medium",
      timeStyle: "short",
    });
  }

  function extensionOf(name) {
    const dot = name.lastIndexOf(".");
    return dot < 0 ? "" : name.slice(dot + 1).toLowerCase();
  }

  function isBusy() {
    return state.uploading || state.ingesting || state.serverRunning;
  }

  function applyBusy() {
    const busy = isBusy();
    el.ingest.disabled = busy;
    el.reindex.disabled = busy;
    el.openUpload.disabled = state.ingesting || state.serverRunning;
    el.upload.disabled = busy || !el.file.files.length;
    el.tbody.querySelectorAll(".btn-delete").forEach(function (button) {
      button.disabled = busy;
    });
  }

  function schedulePolling() {
    const needed = state.ingesting || state.serverRunning;
    if (needed && state.pollTimer === null) {
      state.pollTimer = setInterval(refresh, POLL_MS);
    } else if (!needed && state.pollTimer !== null) {
      clearInterval(state.pollTimer);
      state.pollTimer = null;
    }
  }

  async function loadStatus() {
    const status = await request("/status");
    state.serverRunning = status.ingestionRunning;
    el.statDocuments.textContent = status.documents;
    el.statChunks.textContent = status.chunks;
    el.statModel.textContent =
      status.embeddingModel + " · " + status.dimensions + " dim.";

    const index = status.vectorIndex;
    if (index.found && index.queryable) {
      el.statIndex.textContent = "Listo";
      el.statIndexNote.textContent = "";
    } else if (index.found) {
      el.statIndex.textContent = "No disponible aún";
      el.statIndexNote.textContent =
        "Estado en Atlas: " + (index.status || "desconocido");
    } else {
      el.statIndex.textContent = "No disponible";
      el.statIndexNote.textContent = index.note || "";
    }
  }

  function textCell(value) {
    const cell = document.createElement("td");
    cell.textContent = value;
    return cell;
  }

  function smallLine(text, extraClass) {
    const line = document.createElement("small");
    line.className =
      "text-muted d-block" + (extraClass ? " " + extraClass : "");
    line.textContent = text;
    return line;
  }

  function nameCell(doc) {
    const cell = document.createElement("td");
    const name = document.createElement("p");
    name.className = "mb-0 fw-bold user-name";
    name.textContent = doc.fileName;
    cell.appendChild(name);

    if (doc.title && doc.title !== doc.fileName) {
      cell.appendChild(smallLine(doc.title));
    }
    if (doc.lastError) {
      cell.appendChild(smallLine(doc.lastError, "kb-error-text"));
    }
    return cell;
  }

  function actionCell(doc) {
    const cell = document.createElement("td");
    cell.className = "text-center";
    const actions = document.createElement("div");
    actions.className =
      "table-actions d-flex align-items-center gap-2 justify-content-center";

    const button = document.createElement("button");
    button.type = "button";
    button.className = "btn btn-action btn-delete";
    button.title = "Eliminar";
    button.setAttribute("aria-label", "Eliminar " + doc.fileName);
    const icon = document.createElement("i");
    icon.className = "bi bi-trash-fill";
    icon.setAttribute("aria-hidden", "true");
    button.appendChild(icon);
    button.addEventListener("click", function () {
      removeDocument(doc);
    });

    actions.appendChild(button);
    cell.appendChild(actions);
    return cell;
  }

  function renderDocuments(list) {
    el.tbody.replaceChildren();
    let pending = 0;

    list.forEach(function (doc) {
      if (doc.status === "PENDING" || doc.status === "FAILED") {
        pending++;
      }
      const row = document.createElement("tr");
      row.append(
        nameCell(doc),
        textCell(AUDIENCE_LABELS[doc.audience] || doc.audience),
        textCell(doc.status === "MISSING" ? "—" : formatSize(doc.sizeBytes)),
        textCell(STATUS_LABELS[doc.status] || doc.status),
        textCell(String(doc.chunkCount)),
        textCell(formatDate(doc.indexedAt)),
        actionCell(doc),
      );
      el.tbody.appendChild(row);
    });

    el.empty.textContent =
      "Aún no hay documentos. Sube el primero para que el asistente pueda usarlo.";
    el.empty.hidden = list.length > 0;
    el.count.textContent = list.length;
    el.pending.textContent =
      list.length === 0
        ? "Sin documentos"
        : pending === 0
          ? "Todo está indexado"
          : pending +
            (pending === 1
              ? " documento pendiente de indexar"
              : " documentos pendientes de indexar");
    applyBusy();
  }

  async function refresh() {
    try {
      const results = await Promise.all([loadStatus(), request("/documents")]);
      renderDocuments(results[1]);
    } catch (error) {
      showMessage(error.message, "error");
    }
    applyBusy();
    schedulePolling();
  }

  function renderResult(result) {
    el.resultSummary.textContent =
      result.processed +
      " indexados · " +
      result.skipped +
      " omitidos · " +
      result.failed +
      " con error o pendientes · " +
      result.retired +
      " retirados";
    el.resultList.replaceChildren();

    result.items
      .filter(function (item) {
        return item.outcome !== "SKIPPED_UNCHANGED";
      })
      .forEach(function (item) {
        const line = document.createElement("li");
        const name = document.createElement("strong");
        name.textContent = item.sourceKey.replace(/^file:/, "");
        line.append(
          name,
          document.createTextNode(
            " — " +
              (OUTCOME_LABELS[item.outcome] || item.outcome) +
              (item.detail ? ": " + item.detail : ""),
          ),
        );
        el.resultList.appendChild(line);
      });
  }

  function updateSelection() {
    const file = el.file.files[0];
    if (file) {
      el.fileLabel.textContent = file.name + " (" + formatSize(file.size) + ")";
      el.fileLabel.hidden = false;
    } else {
      el.fileLabel.hidden = true;
    }
    applyBusy();
  }

  function resetUploadForm() {
    el.file.value = "";
    el.audience.value = "";
    el.autoIndex.checked = true;
    clearModalError();
    updateSelection();
  }

  function validateSelection() {
    const file = el.file.files[0];
    if (!file) {
      showModalError("Selecciona un archivo.");
      return false;
    }
    if (!ALLOWED_EXTENSIONS.includes(extensionOf(file.name))) {
      showModalError(
        "Formato no permitido. Usa PDF, Markdown (.md), texto (.txt) o HTML.",
      );
      return false;
    }
    if (file.size > MAX_BYTES) {
      showModalError(
        "El archivo supera el tamaño máximo permitido (" +
          Math.floor(MAX_BYTES / (1024 * 1024)) +
          " MB).",
      );
      return false;
    }
    if (!el.audience.value) {
      showModalError(
        "Elige quién podrá recibir la información de este documento.",
      );
      el.audience.focus();
      return false;
    }
    return true;
  }

  async function upload(overwrite) {
    state.uploading = true;
    applyBusy();

    const body = new FormData();
    body.append("file", el.file.files[0]);
    body.append("audience", el.audience.value);
    body.append("overwrite", String(overwrite));

    try {
      const saved = await request("/documents", { method: "POST", body: body });
      return { outcome: "OK", name: saved.fileName };
    } catch (error) {
      if (error.code === "KNOWLEDGE_DOCUMENT_EXISTS") {
        return { outcome: "EXISTS" };
      }
      showModalError(error.message);
      return { outcome: "ERROR" };
    } finally {
      state.uploading = false;
      applyBusy();
    }
  }

  async function onSubmit(event) {
    event.preventDefault();
    if (isBusy()) {
      return;
    }
    clearModalError();
    if (!validateSelection()) {
      return;
    }

    const indexAfter = el.autoIndex.checked;
    let result = await upload(false);
    if (
      result.outcome === "EXISTS" &&
      window.confirm(
        "Ya existe un documento con ese nombre en esa audiencia. ¿Quieres reemplazarlo?",
      )
    ) {
      result = await upload(true);
    }
    if (result.outcome !== "OK") {
      return;
    }

    uploadModal.hide();
    showMessage("«" + result.name + "» se guardó correctamente.", "success");
    await refresh();
    if (indexAfter) {
      await ingest(false);
    }
  }

  async function ingest(force) {
    if (state.ingesting) {
      return;
    }
    state.ingesting = true;
    applyBusy();
    schedulePolling();
    showMessage(
      "Indexando… Si el proveedor de embeddings limita las solicitudes puede tardar varios minutos. " +
        "Puedes seguir en esta página: la tabla se actualiza sola.",
      "info",
    );

    try {
      const result = await request(
        "/ingest?force=" + (force ? "true" : "false"),
        { method: "POST" },
      );
      const changed = result.items.some(function (item) {
        return item.outcome !== "SKIPPED_UNCHANGED";
      });

      if (!changed) {
        showMessage("No hay cambios que indexar: todo está al día.", "success");
      } else {
        renderResult(result);
        resultModal.show();
        if (result.failed > 0) {
          showMessage(
            "La indexación terminó con " +
              result.failed +
              " documento(s) con error o pendientes. Revisa el detalle y vuelve a indexar.",
            "error",
          );
        } else {
          showMessage("Indexación completada.", "success");
        }
      }
    } catch (error) {
      showMessage(error.message, "error");
    } finally {
      state.ingesting = false;
      await refresh();
    }
  }

  async function removeDocument(doc) {
    if (isBusy()) {
      return;
    }
    if (
      !window.confirm(
        "¿Eliminar «" +
          doc.fileName +
          "»? Se borrará el documento y el asistente " +
          "dejará de usarlo en sus respuestas.",
      )
    ) {
      return;
    }
    clearMessage();
    try {
      await request(
        "/documents?audience=" +
          encodeURIComponent(doc.audience) +
          "&fileName=" +
          encodeURIComponent(doc.fileName),
        { method: "DELETE" },
      );
      showMessage("«" + doc.fileName + "» se eliminó.", "success");
    } catch (error) {
      showMessage(error.message, "error");
    }
    await refresh();
  }

  el.form.addEventListener("submit", onSubmit);
  el.file.addEventListener("change", function () {
    clearModalError();
    updateSelection();
  });
  el.uploadModal.addEventListener("hidden.bs.modal", resetUploadForm);
  el.ingest.addEventListener("click", function () {
    clearMessage();
    ingest(false);
  });
  el.reindex.addEventListener("click", function () {
    if (
      window.confirm(
        "Se volverán a procesar todos los documentos, aunque no hayan cambiado. " +
          "Esto consume cuota del proveedor de embeddings. ¿Continuar?",
      )
    ) {
      clearMessage();
      ingest(true);
    }
  });

  ["dragenter", "dragover"].forEach(function (name) {
    el.dropzone.addEventListener(name, function (event) {
      event.preventDefault();
      el.dropzone.classList.add("is-dragging");
    });
  });
  ["dragleave", "drop"].forEach(function (name) {
    el.dropzone.addEventListener(name, function (event) {
      event.preventDefault();
      el.dropzone.classList.remove("is-dragging");
    });
  });
  el.dropzone.addEventListener("drop", function (event) {
    if (event.dataTransfer && event.dataTransfer.files.length > 0) {
      el.file.files = event.dataTransfer.files;
      clearModalError();
      updateSelection();
    }
  });

  refresh();
})();
