function getCsrfToken() {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : null;
}

document.addEventListener("DOMContentLoaded", () => {
    const modalEl      = document.getElementById("modalPasswordReset");
    const bsModal      = new bootstrap.Modal(modalEl);

    const stepEmail    = document.getElementById("stepEmail");
    const stepOtp      = document.getElementById("stepOtp");
    const stepDone     = document.getElementById("stepDone");

    const inputEmail   = document.getElementById("resetEmail");
    const btnSendOtp   = document.getElementById("btnSendOtp");

    const inputOtp     = document.getElementById("resetOtp");
    const inputNewPass = document.getElementById("resetNewPass");
    const inputConfirm = document.getElementById("resetConfirmPass");
    const btnReset     = document.getElementById("btnReset");
    const btnBackEmail = document.getElementById("btnBackEmail");

    let savedEmail = "";

    document.getElementById("btnForgotPassword").addEventListener("click", (e) => {
        e.preventDefault();
        showStep("email");
        bsModal.show();
    });

    btnSendOtp.addEventListener("click", async () => {
        const email = inputEmail.value.trim();

        if (!isValidEmail(email)) {
            FLToast.error("Ingresa un email válido.");
            return;
        }

        setLoading(btnSendOtp, true, "Enviando...");

        try {
            const { data } = await post("/api/password/send-otp", `email=${encodeURIComponent(email)}`);

            if (data && data.ok) {
                savedEmail = email;
                showStep("otp");
                FLToast.success("Código enviado. Revisa tu correo.");
            } else {
                FLToast.warning(messageOf(data, "No se pudo enviar el código."));
            }
        } catch (err) {
            FLToast.error("Error de conexión. Intenta de nuevo.");
        } finally {
            setLoading(btnSendOtp, false, "Enviar código");
        }
    });

    btnReset.addEventListener("click", async () => {
        const otp         = inputOtp.value.trim();
        const newPass     = inputNewPass.value;
        const confirmPass = inputConfirm.value;

        if (otp.length !== 6 || isNaN(otp)) {
            FLToast.error("El código debe ser de 6 dígitos.");
            return;
        }
        if (newPass.length < 6) {
            FLToast.error("La contraseña debe tener al menos 6 caracteres.");
            return;
        }
        if (newPass !== confirmPass) {
            FLToast.error("Las contraseñas no coinciden.");
            return;
        }

        setLoading(btnReset, true, "Verificando...");

        try {
            const { data } = await post("/api/password/reset",
                `email=${encodeURIComponent(savedEmail)}`
                + `&otp=${encodeURIComponent(otp)}`
                + `&newPassword=${encodeURIComponent(newPass)}`);

            if (data && data.ok) {
                showStep("done");
            } else {
                FLToast.error(messageOf(data, "Error al actualizar la contraseña."));
            }
        } catch (err) {
            FLToast.error("Error de conexión. Intenta de nuevo.");
        } finally {
            setLoading(btnReset, false, "Cambiar contraseña");
        }
    });

    btnBackEmail.addEventListener("click", () => showStep("email"));

    modalEl.addEventListener("hidden.bs.modal", resetModal);

    async function post(url, body) {
        const res = await fetch(url, {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
                "X-XSRF-TOKEN": getCsrfToken()
            },
            body: body
        });
        const data = await res.json().catch(() => null);
        return { res, data };
    }

    function messageOf(data, fallback) {
        return (data && (data.msg || data.message)) || fallback;
    }

    function showStep(step) {
        stepEmail.classList.add("d-none");
        stepOtp.classList.add("d-none");
        stepDone.classList.add("d-none");

        if (step === "email") stepEmail.classList.remove("d-none");
        if (step === "otp")   stepOtp.classList.remove("d-none");
        if (step === "done")  stepDone.classList.remove("d-none");
    }

    function setLoading(btn, loading, text) {
        btn.disabled    = loading;
        btn.textContent = loading ? text : btn.dataset.label || text;
        if (!loading) btn.dataset.label = text;
    }

    function isValidEmail(email) {
        return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
    }

    function resetModal() {
        inputEmail.value   = "";
        inputOtp.value     = "";
        inputNewPass.value = "";
        inputConfirm.value = "";
        savedEmail         = "";
        showStep("email");
    }
});