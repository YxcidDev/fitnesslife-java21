(() => {
    'use strict';

    const report = document.getElementById('biReport');
    const frame = document.getElementById('biFrame');
    const loader = document.getElementById('biLoader');
    const fullscreenBtn = document.getElementById('biFullscreenBtn');
    const fullscreenIcon = document.getElementById('biFullscreenIcon');

    if (!report || !frame || !loader) {
        return;
    }

    const hideLoader = () => loader.classList.add('is-hidden');

    frame.addEventListener('load', hideLoader);

    setTimeout(hideLoader, 15000);

    if (!fullscreenBtn || !report.requestFullscreen) {
        fullscreenBtn?.remove();
        return;
    }

    fullscreenBtn.addEventListener('click', () => {
        if (document.fullscreenElement) {
            document.exitFullscreen();
        } else {
            report.requestFullscreen();
        }
    });

    document.addEventListener('fullscreenchange', () => {
        const active = document.fullscreenElement === report;

        fullscreenIcon.classList.toggle('bi-fullscreen', !active);
        fullscreenIcon.classList.toggle('bi-fullscreen-exit', active);
        fullscreenBtn.title = active ? 'Salir de pantalla completa' : 'Pantalla completa';
        fullscreenBtn.setAttribute('aria-label', fullscreenBtn.title);
    });
})();