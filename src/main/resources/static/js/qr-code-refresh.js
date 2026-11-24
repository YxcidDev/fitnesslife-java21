function getCsrfToken() {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : null;
}

function refreshQR() {
    const qrImage = document.getElementById('qrCodeImage');
    const refreshBtn = document.querySelector('.refresh-qr-btn');

    refreshBtn.disabled = true;
    refreshBtn.innerHTML = '<i class="bi bi-arrow-clockwise spinner-border spinner-border-sm"></i> Actualizando...';

    fetch('/api/qr/refresh', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': getCsrfToken()
        }
    })
        .then(response => {
            if (response.ok) {
                qrImage.src = '/api/qr/image?t=' + new Date().getTime();
                showAlert('Código QR actualizado correctamente', 'success');
            } else {
                showAlert('Error al actualizar el código QR', 'danger');
            }
        })
        .catch(error => {
            console.error('Error:', error);
            showAlert('Error de conexión al actualizar el código QR', 'danger');
        })
        .finally(() => {
            refreshBtn.disabled = false;
            refreshBtn.innerHTML = '<i class="bi bi-arrow-clockwise"></i> Actualizar Código';
        });
}

function showAlert(message, type) {
    FLToast.show({ type: type === 'danger' ? 'error' : type, message: message });
}