'use strict';

// ── State ───────────────────────────────────────────────────────
let map;
let selectedId = null;
let progressPollTimer = null;
let lastGeocodedCount = -1;
const BERLIN = [13.405, 52.52];
const STALE_HOURS = 6;

let dateRangeMin = 0;
let dateRangeMax = 0;

const impactColor = {
    LOW: '#43a047',
    MEDIUM: '#ffb300',
    HIGH: '#f57c00',
    VERY_HIGH: '#b71c1c'
};

// ── Init ────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
    initMap();
    await loadDistricts();
    await initDateRange();
    await loadAll();
    bindFilters();
    pollImportProgress();
});

function bindFilters() {
    document.getElementById('btn-refresh').addEventListener('click', loadAll);
    document.getElementById('btn-export-csv').addEventListener('click', exportCsv);
    ['filter-district', 'filter-impact', 'filter-category'].forEach(id => {
        document.getElementById(id).addEventListener('change', loadAll);
    });
    let debounce;
    ['range-from', 'range-to'].forEach(id => {
        document.getElementById(id).addEventListener('input', () => {
            updateRangeUI();
            clearTimeout(debounce);
            debounce = setTimeout(loadAll, 300);
        });
    });
    bindDisplayMode();
}

function bindDisplayMode() {
    document.getElementById('display-mode').addEventListener('change', function () {
        applyDisplayMode(this.value);
    });
}

function applyDisplayMode(mode) {
    const isPoints  = mode === 'points';
    const isHeatmap = mode === 'heatmap';
    const isPlz     = mode === 'plz';
    map.setLayoutProperty('demo-points',        'visibility', isPoints  ? 'visible' : 'none');
    map.setLayoutProperty('demo-heatmap',       'visibility', isHeatmap ? 'visible' : 'none');
    map.setLayoutProperty('impact-zones-fill',    'visibility', isPoints  ? 'visible' : 'none');
    map.setLayoutProperty('impact-zones-outline', 'visibility', isPoints  ? 'visible' : 'none');
    map.setLayoutProperty('plz-choropleth',     'visibility', isPlz     ? 'visible' : 'none');
    if (isPlz) loadPlzLayer();
}

async function initDateRange() {
    try {
        const data = await fetchJson('/api/date-range');
        if (!data.from || !data.to) return;
        dateRangeMin = localDateToEpochDay(data.from);
        dateRangeMax = localDateToEpochDay(data.to);
        const from = document.getElementById('range-from');
        const to   = document.getElementById('range-to');
        from.min = to.min = dateRangeMin;
        from.max = to.max = dateRangeMax;
        from.value = dateRangeMin;
        to.value   = dateRangeMax;
        updateRangeUI();
    } catch (_) {}
}

function localDateToEpochDay(str) {
    const [y, m, d] = str.split('-').map(Number);
    return Math.floor(Date.UTC(y, m - 1, d) / 86400000);
}

function epochDayToIso(day) {
    const d = new Date(day * 86400000);
    return d.toISOString().slice(0, 10);
}

function epochDayToDisplay(day) {
    const d = new Date(day * 86400000);
    return d.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

function updateRangeUI() {
    const from = document.getElementById('range-from');
    const to   = document.getElementById('range-to');
    let vFrom = parseInt(from.value);
    let vTo   = parseInt(to.value);
    if (vFrom > vTo) {
        if (document.activeElement === from) { from.value = vTo; vFrom = vTo; }
        else { to.value = vFrom; vTo = vFrom; }
    }
    const span   = dateRangeMax - dateRangeMin || 1;
    const left   = (vFrom - dateRangeMin) / span * 100;
    const right  = (vTo   - dateRangeMin) / span * 100;
    const fill   = document.getElementById('range-fill');
    fill.style.left  = left + '%';
    fill.style.width = (right - left) + '%';
    const label = document.getElementById('date-range-display');
    if (vFrom === dateRangeMin && vTo === dateRangeMax) {
        label.textContent = 'All dates';
    } else if (vFrom === vTo) {
        label.textContent = epochDayToDisplay(vFrom);
    } else {
        label.textContent = epochDayToDisplay(vFrom) + ' — ' + epochDayToDisplay(vTo);
    }
}

// ── Import progress banner ───────────────────────────────────────
async function pollImportProgress() {
    clearTimeout(progressPollTimer);
    try {
        const s = await fetchJson('/api/snapshot/status');
        if (s.loaded && s.normalizedRows > 0 && !s.geocodingComplete) {
            showProgressBanner(s.geocodedRows, s.normalizedRows);
            if (s.geocodedRows !== lastGeocodedCount) {
                lastGeocodedCount = s.geocodedRows;
                loadAll();
            }
            progressPollTimer = setTimeout(pollImportProgress, 3000);
        } else {
            hideProgressBanner();
            if (s.loaded) loadAll();
        }
    } catch (_) {}
}

function showProgressBanner(done, total) {
    let banner = document.getElementById('import-progress');
    if (!banner) {
        banner = document.createElement('div');
        banner.id = 'import-progress';
        document.getElementById('map-container').prepend(banner);
    }
    const pct = Math.round(done / total * 100);
    banner.innerHTML = `
        <div class="progress-text">Geocoding ${done} / ${total} events (${pct}%)</div>
        <div class="progress-bar-wrap"><div class="progress-bar-fill" style="width:${pct}%"></div></div>
    `;
    banner.classList.remove('hidden');
}

function hideProgressBanner() {
    const b = document.getElementById('import-progress');
    if (b) b.classList.add('hidden');
}

// ── Map ─────────────────────────────────────────────────────────
function initMap() {
    map = new maplibregl.Map({
        container: 'map',
        style: {
            version: 8,
            sources: {
                osm: {
                    type: 'raster',
                    tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
                    tileSize: 256,
                    attribution: '© OpenStreetMap contributors'
                }
            },
            layers: [{ id: 'osm', type: 'raster', source: 'osm' }]
        },
        center: BERLIN,
        zoom: 11
    });

    map.on('load', () => {
        addMapSources();
        addMapLayers();
        bindMapClicks();
    });
}

function addMapSources() {
    map.addSource('demos', { type: 'geojson', data: emptyCollection() });
    map.addSource('zones', { type: 'geojson', data: emptyCollection() });
    map.addSource('plz', { type: 'geojson', data: '/berlin-plz-centroids.geojson' });
    map.addSource('plz-polygons', { type: 'geojson', data: emptyCollection() });
}

function addMapLayers() {
    map.addLayer({
        id: 'impact-zones-fill',
        type: 'fill',
        source: 'zones',
        paint: {
            'fill-color': ['match', ['get', 'impactLevel'],
                'LOW', '#43a047', 'MEDIUM', '#ffb300',
                'HIGH', '#f57c00', 'VERY_HIGH', '#b71c1c', '#ccc'],
            'fill-opacity': 0.15
        }
    });
    map.addLayer({
        id: 'impact-zones-outline',
        type: 'line',
        source: 'zones',
        paint: {
            'line-color': ['match', ['get', 'impactLevel'],
                'LOW', '#43a047', 'MEDIUM', '#ffb300',
                'HIGH', '#f57c00', 'VERY_HIGH', '#b71c1c', '#ccc'],
            'line-width': 1.5
        }
    });
    map.addLayer({
        id: 'plz-labels',
        type: 'symbol',
        source: 'plz',
        minzoom: 11,
        layout: {
            'text-field': ['get', 'plz'],
            'text-size': 11,
            'text-font': ['Open Sans Regular', 'Arial Unicode MS Regular'],
            'text-anchor': 'center'
        },
        paint: {
            'text-color': '#444',
            'text-halo-color': '#fff',
            'text-halo-width': 1.5
        }
    });
    map.addLayer({
        id: 'plz-choropleth',
        type: 'fill',
        source: 'plz-polygons',
        layout: { visibility: 'none' },
        paint: {
            'fill-color': [
                'interpolate', ['linear'],
                ['coalesce', ['get', 'eventCount'], 0],
                0,  'rgba(255,245,245,0)',
                1,  'rgba(255,180,180,0.45)',
                5,  'rgba(255,100,100,0.65)',
                10, 'rgba(220,30,30,0.75)',
                20, 'rgba(160,0,0,0.9)'
            ],
            'fill-outline-color': 'rgba(120,0,0,0.3)'
        }
    });
    map.addLayer({
        id: 'demo-heatmap',
        type: 'heatmap',
        source: 'demos',
        filter: ['==', ['geometry-type'], 'Point'],
        layout: { visibility: 'none' },
        paint: {
            'heatmap-weight': 1,
            'heatmap-intensity': ['interpolate', ['linear'], ['zoom'], 8, 1.0, 14, 3.0],
            'heatmap-radius':    ['interpolate', ['linear'], ['zoom'], 8, 30,  14, 80],
            'heatmap-color': [
                'interpolate', ['linear'], ['heatmap-density'],
                0,   'rgba(0,0,0,0)',
                0.1, 'rgba(0,180,0,0.5)',
                0.3, 'rgba(120,220,0,0.7)',
                0.5, 'rgba(255,220,0,0.8)',
                0.7, 'rgba(255,120,0,0.85)',
                1.0, 'rgba(200,0,0,0.95)'
            ],
            'heatmap-opacity': ['interpolate', ['linear'], ['zoom'], 13, 0.9, 16, 0]
        }
    });
    map.addLayer({
        id: 'demo-points',
        type: 'circle',
        source: 'demos',
        filter: ['==', ['geometry-type'], 'Point'],
        paint: {
            'circle-color': ['match', ['get', 'impactLevel'],
                'LOW', '#43a047', 'MEDIUM', '#ffb300',
                'HIGH', '#f57c00', 'VERY_HIGH', '#b71c1c', '#888'],
            'circle-radius': ['case', ['==', ['get', 'id'], selectedId || ''], 10, 7],
            'circle-stroke-color': '#fff',
            'circle-stroke-width': 2
        }
    });
}

function bindMapClicks() {
    ['demo-points'].forEach(layerId => {
        map.on('click', layerId, e => {
            const id = e.features[0].properties.id;
            selectEvent(id, 'map');
        });
        map.on('mouseenter', layerId, () => { map.getCanvas().style.cursor = 'pointer'; });
        map.on('mouseleave', layerId, () => { map.getCanvas().style.cursor = ''; });
    });
}

function emptyCollection() {
    return { type: 'FeatureCollection', features: [] };
}

async function loadPlzLayer() {
    try {
        const data = await fetchJson('/api/demonstrations.plz-heatmap.geojson' + buildParams());
        map.getSource('plz-polygons')?.setData(data);
    } catch (err) {
        console.error('PLZ layer load failed:', err);
    }
}

// ── Data loading ────────────────────────────────────────────────
async function loadAll() {
    const params = buildParams();
    showLoading(true);

    try {
        await checkStatus();
        const mode = document.getElementById('display-mode')?.value ?? 'points';
        const requests = [
            fetchJson('/api/timeline' + params),
            fetchJson('/api/demonstrations.geojson' + params),
            fetchJson('/api/demonstrations.impact-zones.geojson' + params)
        ];
        if (mode === 'plz') requests.push(fetchJson('/api/demonstrations.plz-heatmap.geojson' + params));
        const results = await Promise.all(requests);

        renderTimeline(results[0]);
        updateMapData(results[1], results[2]);
        if (mode === 'plz' && results[3]) map.getSource('plz-polygons')?.setData(results[3]);
    } catch (err) {
        console.error('Load failed:', err);
    } finally {
        showLoading(false);
    }
}

async function checkStatus() {
    try {
        const status = await fetchJson('/api/snapshot/status');
        if (!status.loaded) return;

        const fetched = new Date(status.fetchedAt);
        const hoursSince = (Date.now() - fetched.getTime()) / 3600000;
        const staleEl = document.getElementById('stale-warning');
        const fetchedEl = document.getElementById('fetched-at');
        if (hoursSince > STALE_HOURS) {
            fetchedEl.textContent = fetched.toLocaleString();
            staleEl.classList.remove('hidden');
        } else {
            staleEl.classList.add('hidden');
        }
    } catch (_) {}
}

async function loadDistricts() {
    try {
        const districts = await fetchJson('/api/districts');
        const sel = document.getElementById('filter-district');
        districts.forEach(d => {
            const opt = document.createElement('option');
            opt.value = d;
            opt.textContent = d;
            sel.appendChild(opt);
        });
    } catch (_) {}
}

function buildParams() {
    const district = document.getElementById('filter-district').value;
    const impact   = document.getElementById('filter-impact').value;
    const cat      = document.getElementById('filter-category').value;
    const vFrom    = parseInt(document.getElementById('range-from').value || dateRangeMin);
    const vTo      = parseInt(document.getElementById('range-to').value   || dateRangeMax);
    const parts = [];
    if (dateRangeMin !== dateRangeMax) {
        if (vFrom > dateRangeMin) parts.push('dateFrom=' + epochDayToIso(vFrom));
        if (vTo   < dateRangeMax) parts.push('dateTo='   + epochDayToIso(vTo));
    }
    if (district) parts.push('district=' + encodeURIComponent(district));
    if (impact)   parts.push('impactLevel=' + impact);
    if (cat)      parts.push('category=' + cat);
    return parts.length ? '?' + parts.join('&') : '';
}

function exportCsv() {
    const params = buildParams();
    window.location.href = '/api/demonstrations.csv' + params;
}

async function fetchJson(url) {
    const r = await fetch(url);
    if (!r.ok) throw new Error(url + ' -> ' + r.status);
    return r.json();
}

// ── Timeline ─────────────────────────────────────────────────────
function renderTimeline(items) {
    const list    = document.getElementById('timeline-list');
    const loading = document.getElementById('timeline-loading');
    const empty   = document.getElementById('timeline-empty');

    list.innerHTML = '';
    loading.classList.add('hidden');

    if (!items.length) {
        empty.classList.remove('hidden');
        return;
    }
    empty.classList.add('hidden');

    items.forEach(item => {
        const card = document.createElement('div');
        card.className = `timeline-card impact-${item.impactLevel}`;
        card.dataset.id = item.id;
        const geoBadge = item.geocoded
            ? `<span class="geo-badge geo-yes">&#10003; Geocoded</span>`
            : `<span class="geo-badge geo-no">&#8212; Not geocoded</span>`;
        card.innerHTML = `
            <div class="card-title">${esc(item.title)}</div>
            <div class="card-datetime">${esc(item.date)}${item.timeText ? ' · ' + esc(item.timeText) : ''}</div>
            ${item.locationText ? '<div class="card-location">' + esc(item.locationText) + '</div>' : ''}
            <div class="card-meta">
                ${item.district ? '<span>' + esc(item.district) + '</span>' : ''}
                <span class="impact-badge badge-${item.impactLevel}">${item.impactLevel.replace('_', ' ')} ${item.impactScore}</span>
                ${geoBadge}
            </div>
            ${item.reasons.length ? '<div class="card-reasons">' + item.reasons.map(r => '• ' + esc(r)).join('<br>') + '</div>' : ''}
        `;
        card.addEventListener('click', () => selectEvent(item.id, 'timeline'));
        list.appendChild(card);
    });
}

// ── Map data ─────────────────────────────────────────────────────
function updateMapData(geojson, zones) {
    if (!map.isStyleLoaded()) {
        map.once('idle', () => updateMapData(geojson, zones));
        return;
    }
    map.getSource('demos').setData(geojson);
    map.getSource('zones').setData(zones);

    const mapEmpty  = document.getElementById('map-empty');
    const hasFeatures = geojson.features.length > 0;
    mapEmpty.classList.toggle('hidden', hasFeatures);
}

// ── Selection sync ───────────────────────────────────────────────
function selectEvent(id, source) {
    selectedId = id;

    document.querySelectorAll('.timeline-card').forEach(c => {
        c.classList.toggle('selected', c.dataset.id === id);
    });

    if (source !== 'timeline') {
        const card = document.querySelector(`.timeline-card[data-id="${id}"]`);
        if (card) card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    if (source !== 'map' && map.isStyleLoaded()) {
        if (map.getLayer('demo-points')) {
            map.setPaintProperty('demo-points', 'circle-radius',
                ['case', ['==', ['get', 'id'], id], 10, 7]);
        }

        const feature = findMapFeature(id);
        if (feature) flyToFeature(feature);
    }
}

function findMapFeature(id) {
    const src = map.getSource('demos');
    if (!src) return null;
    const data = src._data;
    return data?.features?.find(f => f.properties?.id === id) || null;
}

function flyToFeature(feature) {
    const geom = feature.geometry;
    if (geom.type === 'Point') {
        map.flyTo({ center: geom.coordinates, zoom: 14 });
    }
}

// ── Helpers ──────────────────────────────────────────────────────
function showLoading(on) {
    document.getElementById('timeline-loading').classList.toggle('hidden', !on);
    document.getElementById('map-loading').classList.toggle('hidden', !on);
}

function esc(str) {
    return (str || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
