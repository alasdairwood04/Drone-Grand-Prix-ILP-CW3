// --- MAP INITIALIZATION ---
const map = L.map('map', { zoomControl: false }).setView([55.944, -3.188], 15);
L.control.zoom({ position: 'bottomright' }).addTo(map);
L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
    attribution: '&copy; OpenStreetMap &copy; CARTO',
    maxZoom: 19
}).addTo(map);

const drawnItems = new L.FeatureGroup();
map.addLayer(drawnItems);

const drawControl = new L.Control.Draw({
    draw: {
        polygon: { shapeOptions: { color: '#37352F', weight: 2, fillOpacity: 0.1 } },
        marker: true, polyline: false, circle: false, rectangle: false, circlemarker: false
    },
    edit: { featureGroup: drawnItems }
});
map.addControl(drawControl);

// --- STATE VARIABLES ---
let trackPolygon = null;
let startPoint = null;
let endPoint = null;
let raceInterval = null;
let startTime = 0;
let activeDrones = 0;
let finishedDrones = [];
let pathLayers = {}; // Store references to drawn path layers

// --- NEW: Track Preset Logic ---
async function toggleTrackModal() {
    const modal = document.getElementById('track-modal');
    if (modal.style.display === 'flex') {
        modal.style.display = 'none';
        return;
    }

    modal.style.display = 'flex';
    const list = document.getElementById('track-list-content');
    list.innerHTML = '<div style="padding:10px; color:#999;">Fetching tracks...</div>';

    try {
        const response = await fetch('http://localhost:8080/api/v1/track/presets');
        const tracks = await response.json();

        list.innerHTML = '';
        if (Object.keys(tracks).length === 0) {
            list.innerHTML = '<div style="padding:10px;">No tracks found.</div>';
            return;
        }

        for (const [name, geoJson] of Object.entries(tracks)) {
            const item = document.createElement('div');
            item.className = 'track-item';
            item.innerHTML = `
                <div style="display:flex; align-items:center;">
                    <div class="track-icon">🏁</div>
                    <strong>${name}</strong>
                </div>
                <span style="color:#999;">Select →</span>
            `;
            item.onclick = () => loadPresetTrack(name, geoJson);
            list.appendChild(item);
        }
    } catch (e) {
        list.innerHTML = '<div style="color:red; padding:10px;">Error fetching tracks.</div>';
    }
}

function loadPresetTrack(name, geoJson) {
    if (trackPolygon) drawnItems.removeLayer(trackPolygon);

    // Fix: Handle the nested structure of GeoJSON Polygons.
    // We map over each "ring" (usually just one), and then map over each point in that ring.
    const latLngs = geoJson.coordinates.map(ring =>
        ring.map(coord => [coord[1], coord[0]]) // Swap [lng, lat] to [lat, lng]
    );

    // Pass the corrected array of rings to Leaflet
    trackPolygon = L.polygon(latLngs, { color: '#37352F', weight: 2, fillOpacity: 0.1 }).addTo(drawnItems);

    map.fitBounds(trackPolygon.getBounds());

    document.getElementById('track-modal').style.display = 'none';
    logCommentary(`Loaded track: ${name}`, true);
    logCommentary("Please place Start/End markers manually.");
}

// --- LEAFLET EVENTS ---
map.on(L.Draw.Event.CREATED, function (e) {
    const layer = e.layer;
    drawnItems.addLayer(layer);
    if (e.layerType === 'polygon') {
        trackPolygon = layer;
        logCommentary("Track boundary defined.");
    } else if (e.layerType === 'marker') {
        if (!startPoint) {
            startPoint = layer.getLatLng();
            logCommentary("Start line set.");
        } else if (!endPoint) {
            endPoint = layer.getLatLng();
            logCommentary("Finish line set.");
        }
    }
});

// --- RACE LOGIC ---
async function prepareRace() {
    if (!trackPolygon || !startPoint || !endPoint) {
        alert("Setup incomplete: Track polygon + 2 Markers required.");
        return;
    }
    updateStatus("Calculating...", "#E0AC00");

    // CHANGE 1: Get the full GeoJSON geometry directly from Leaflet
    // This automatically handles Polygons, Holes, and Coordinate Swaps
    const geoJsonGeometry = trackPolygon.toGeoJSON().geometry;

    const payload = {
        startLocation: { lat: startPoint.lat, lng: startPoint.lng },
        endLocation: { lat: endPoint.lat, lng: endPoint.lng },
        LLMInput: geoJsonGeometry // Send the full Polygon object
    };

    try {
        const response = await fetch('http://localhost:8080/api/v1/race/start', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if(!response.ok) throw new Error("Server Error");
        const raceData = await response.json();
        startCountdown(raceData);
    } catch (error) {
        console.error(error);
        logCommentary("Calculation failed: " + error.message);
        updateStatus("Error", "#EB5757");
    }
}

function startCountdown(raceData) {
    const overlay = document.getElementById('countdown-overlay');
    overlay.style.display = 'flex';
    let count = 3;
    overlay.innerText = count;
    const interval = setInterval(() => {
        count--;
        if (count > 0) overlay.innerText = count;
        else if (count === 0) overlay.innerText = "GO";
        else {
            clearInterval(interval);
            overlay.style.display = 'none';
            runRaceSimulation(raceData);
        }
    }, 1000);
}

function runRaceSimulation(raceData) {
    updateStatus("Racing...", "#27AE60");
    logCommentary("Lights out and away we go!", true);
    document.getElementById('leaderboard-body').innerHTML = "";
    finishedDrones = [];
    startTime = Date.now();
    raceInterval = setInterval(updateTimerDisplay, 37);

    const results = raceData.droneResults;
    activeDrones = results.length;

    results.forEach(drone => {
        if(!drone.path || !drone.path.coordinates || drone.path.coordinates.length === 0) {
            activeDrones--; return;
        }

        const droneIcon = L.divIcon({
            className: 'drone-marker',
            html: `<div style="background:${drone.color}; width:10px; height:10px; border-radius:50%; border: 2px solid white; box-shadow: 0 1px 3px rgba(0,0,0,0.3);"></div>`
        });

        const marker = L.marker([raceData.startLocation.lat, raceData.startLocation.lng], {icon: droneIcon}).addTo(map);


        const pathCoords = drone.path.coordinates;

        // --- NEW CODE START ---
        // 1. Get the total time calculated by the backend (in seconds)
        // If travelTime is missing, default to 100 seconds to prevent errors
        const simTimeSeconds = drone.travelTime || 100;

        // 2. Define a "Speed Up" factor.
        // e.g., 10.0 means the race plays 10x faster than real-time.
        const SPEED_FACTOR = 20.0;

        // 3. Calculate how many milliseconds each step should take
        // Formula: (Total Sim Time / Speed Factor) / Number of Steps
        // We convert simTime to ms by multiplying by 1000
        let stepDelay = (simTimeSeconds * 1000 / SPEED_FACTOR) / pathCoords.length;

        // Optional: Clamp to a minimum of 10ms to ensure browser stability
        if (stepDelay < 10) stepDelay = 10;
        // --- NEW CODE END ---

        let step = 0;
        // Use the calculated stepDelay instead of the hardcoded 50
        const droneInterval = setInterval(() => {
            if (step >= pathCoords.length) {
                clearInterval(droneInterval);
                finishDrone(drone, Date.now() - startTime);
                return;
            }
            const [lng, lat] = pathCoords[step];
            marker.setLatLng([lat, lng]);
            step++;
        }, stepDelay); // <--- Updated here
    });
}
function finishDrone(drone, finalTimeMs) {
    activeDrones--;
    const timeStr = formatTime(finalTimeMs);
    finishedDrones.push({ ...drone, timeStr, rawTime: finalTimeMs });
    finishedDrones.sort((a,b) => a.rawTime - b.rawTime);
    renderLeaderboard();
    logCommentary(`${drone.algorithmName} finishes in ${timeStr}`);
    if (activeDrones <= 0) {
        clearInterval(raceInterval);
        updateStatus("Finished", "#37352F");
        logCommentary("Race complete.", true);

        enablePathAnalysis();
    }
}

function enablePathAnalysis() {
    const title = document.getElementById('analysis-title');
    const container = document.getElementById('path-controls');

    title.style.display = 'flex';
    container.style.display = 'flex';
    container.innerHTML = ''; // Clear previous

    finishedDrones.forEach((drone, index) => {
        const row = document.createElement('div');
        row.className = 'path-toggle-row';
        row.style.cssText = "display:flex; align-items:center; justify-content:space-between; font-size:13px; padding:4px 0;";

        // Create a unique ID for the checkbox
        const chkId = `chk-drone-${index}`;

        row.innerHTML = `
            <div style="display:flex; align-items:center;">
                <input type="checkbox" id="${chkId}" style="margin-right:8px; cursor:pointer;" onchange="togglePath(${index}, this.checked)">
                <label for="${chkId}" style="cursor:pointer; display:flex; align-items:center;">
                    <div style="width:8px; height:8px; background:${drone.color}; border-radius:50%; margin-right:6px;"></div>
                    ${drone.algorithmName}
                </label>
            </div>
            <span style="color:#999; font-size:11px;">${drone.timeStr}</span>
        `;
        container.appendChild(row);
    });
}

function togglePath(droneIndex, isVisible) {
    const drone = finishedDrones[droneIndex];

    if (isVisible) {
        // Drone coordinates are [lng, lat] (GeoJSON standard)
        // Leaflet Polyline expects [lat, lng]
        const latLngs = drone.path.coordinates.map(coord => [coord[1], coord[0]]);

        const line = L.polyline(latLngs, {
            color: drone.color,
            weight: 3,
            opacity: 0.8,
            lineJoin: 'round'
        }).addTo(map);

        // Add a small tooltip to the line
        line.bindTooltip(`${drone.algorithmName} (${drone.timeStr})`, { sticky: true });

        pathLayers[droneIndex] = line;
    } else {
        if (pathLayers[droneIndex]) {
            map.removeLayer(pathLayers[droneIndex]);
            delete pathLayers[droneIndex];
        }
    }
}

// Update clearMap to reset these new elements
function clearMap() {
    drawnItems.clearLayers();

    // Remove all analysis lines
    Object.values(pathLayers).forEach(layer => map.removeLayer(layer));
    pathLayers = {};

    // Hide Analysis UI
    document.getElementById('analysis-title').style.display = 'none';
    document.getElementById('path-controls').style.display = 'none';
    document.getElementById('path-controls').innerHTML = '';

    trackPolygon = null; startPoint = null; endPoint = null;
    document.getElementById('leaderboard-body').innerHTML = '<div style="padding:10px 0; color:#999; font-size:13px; text-align:center; font-style:italic;">Waiting...</div>';
    document.getElementById('race-timer').innerText = "00:00.00";
    updateStatus("Ready", "#ccc");
    logCommentary("Map cleared.");
}

function renderLeaderboard() {
    const container = document.getElementById('leaderboard-body');
    container.innerHTML = "";
    finishedDrones.forEach((d, index) => {
        const div = document.createElement('div');
        div.className = 'leaderboard-row';
        div.innerHTML = `<div class="rank">${index + 1}</div><div class="drone-info"><div class="drone-color" style="background:${d.color}"></div>${d.algorithmName}</div><div class="time">${d.timeStr}</div>`;
        container.appendChild(div);
    });
}

function updateTimerDisplay() {
    document.getElementById('race-timer').innerText = formatTime(Date.now() - startTime);
}

function formatTime(ms) {
    const min = Math.floor(ms / 60000);
    const sec = Math.floor((ms % 60000) / 1000);
    const centi = Math.floor((ms % 1000) / 10);
    return `${pad(min)}:${pad(sec)}.${pad(centi)}`;
}
function pad(num) { return num.toString().padStart(2, '0'); }

function logCommentary(text, highlight = false) {
    const box = document.getElementById('commentary-box');
    const entry = document.createElement('div');
    entry.className = 'comm-entry';
    const timeLabel = startTime > 0 ? formatTime(Date.now() - startTime) : "PRE";
    entry.innerHTML = `<span class="comm-time">${timeLabel}</span><span class="comm-text ${highlight ? 'comm-highlight' : ''}">${text}</span>`;
    box.prepend(entry);
}

function updateStatus(text, color) {
    document.getElementById('race-status-text').childNodes[1].textContent = " " + text;
    document.getElementById('status-dot').style.background = color;
}

function clearMap() {
    drawnItems.clearLayers();
    trackPolygon = null; startPoint = null; endPoint = null;
    document.getElementById('leaderboard-body').innerHTML = '<div style="padding:10px 0; color:#999; font-size:13px; text-align:center; font-style:italic;">Waiting...</div>';
    document.getElementById('race-timer').innerText = "00:00.00";
    updateStatus("Ready", "#ccc");
    logCommentary("Map cleared.");
}

async function uploadTrack(input) {
    if (!input.files || !input.files[0]) return;
    const formData = new FormData();
    formData.append('file', input.files[0]);
    logCommentary("Processing image...");
    try {
        const response = await fetch('http://localhost:8080/api/v1/track/upload', { method: 'POST', body: formData });
        if (!response.ok) throw new Error("Upload failed");
        const geoJson = await response.json();
        if (trackPolygon) drawnItems.removeLayer(trackPolygon);
        const latLngs = geoJson.coordinates.map(c => [c[1], c[0]]);
        trackPolygon = L.polygon(latLngs, { color: '#37352F', weight: 2 }).addTo(drawnItems);
        map.fitBounds(trackPolygon.getBounds());
        logCommentary("Track generated from image.", true);
        input.value = '';
    } catch (error) {
        console.error(error);
        logCommentary("Image failed.");
    }
}