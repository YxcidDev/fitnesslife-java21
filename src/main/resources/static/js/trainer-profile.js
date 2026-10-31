(function () {
    'use strict';

    const grids = document.querySelectorAll('[data-fill-grid]');
    if (grids.length === 0) {
        return;
    }

    const ICON_PATTERN = /^bi-[a-z0-9-]+$/;
    const DEFAULT_ICON = 'bi-plus-square';

    function columnCount(grid) {
        const template = window.getComputedStyle(grid).gridTemplateColumns;
        if (!template || template === 'none') {
            return 1;
        }
        return template.trim().split(/\s+/).length;
    }

    function createPlaceholder(grid) {
        const card = document.createElement('div');
        card.className = 'ph-card';
        card.setAttribute('aria-hidden', 'true');

        const inner = document.createElement('div');
        inner.className = 'placeholder-inner';

        const requestedIcon = grid.dataset.placeholderIcon || '';
        const icon = document.createElement('i');
        icon.className = 'bi ' + (ICON_PATTERN.test(requestedIcon) ? requestedIcon : DEFAULT_ICON)
            + ' placeholder-icon';
        inner.appendChild(icon);

        if (grid.dataset.placeholderTitle) {
            const title = document.createElement('p');
            title.className = 'placeholder-title';
            title.textContent = grid.dataset.placeholderTitle;
            inner.appendChild(title);
        }
        if (grid.dataset.placeholderText) {
            const text = document.createElement('p');
            text.className = 'placeholder-text';
            text.textContent = grid.dataset.placeholderText;
            inner.appendChild(text);
        }

        card.appendChild(inner);
        return card;
    }

    function sync(grid) {
        if (grid.clientWidth === 0) {
            return;
        }
        const placeholders = grid.querySelectorAll(':scope > .ph-card');
        const real = grid.children.length - placeholders.length;
        const columns = columnCount(grid);
        const wanted = real === 0 ? 0 : (columns - (real % columns)) % columns;

        if (placeholders.length === wanted) {
            return;
        }
        placeholders.forEach(function (node) {
            node.remove();
        });
        for (let i = 0; i < wanted; i++) {
            grid.appendChild(createPlaceholder(grid));
        }
    }

    function syncAll() {
        grids.forEach(sync);
    }

    if ('ResizeObserver' in window) {
        const observer = new ResizeObserver(syncAll);
        grids.forEach(function (grid) {
            observer.observe(grid);
        });
    } else {
        window.addEventListener('resize', syncAll);
    }
    syncAll();
}());