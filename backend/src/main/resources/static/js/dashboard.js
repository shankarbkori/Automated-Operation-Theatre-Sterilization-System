/**
 * Universal Dashboard Javascript Engine
 * Contains background polling tasks syncing local DOM with Spring Backend Data
 * Now includes Chart.js Historical Data synchronization.
 */

// Global Chart Instances
let sparkTempChart, sparkHumChart, sparkAqiChart, sparkLuxChart;
let mainTempChart, mainHumChart, mainAqiChart;

const maxDataPoints = 20;

document.addEventListener("DOMContentLoaded", () => {
    console.log("AOTS Frontend initialized. Staring hardware sync...");
    initCharts();
    loadHistoricalData();
    startTelemetryPolling();
});

function initCharts() {
    if (typeof Chart === 'undefined') return;

    Chart.defaults.color = '#94a3b8';
    Chart.defaults.font.family = "'Inter', sans-serif";

    const sparkOptions = {
        responsive: true, maintainAspectRatio: false,
        animation: { duration: 0 },
        plugins: { legend: { display: false }, tooltip: { enabled: false } },
        scales: { x: { display: false }, y: { display: false } },
        elements: { point: { radius: 0 }, line: { tension: 0.4 } }
    };

    const mainOptions = {
        responsive: true, maintainAspectRatio: false,
        animation: { duration: 0 },
        plugins: { legend: { display: false } },
        scales: {
            x: { grid: { display: false, drawBorder: true }, ticks: { font: { size: 10 } } },
            y: { grid: { color: '#f1f5f9', borderDash: [5, 5] }, ticks: { font: { size: 10 } } }
        },
        elements: { point: { radius: 3, hoverRadius: 5 }, line: { tension: 0.4 } }
    };

    if (document.getElementById('sparkTemp')) sparkTempChart = new Chart(document.getElementById('sparkTemp'), { type: 'line', data: { labels: [], datasets: [{ data: [], borderColor: '#10b981', borderWidth: 1.5 }] }, options: sparkOptions });
    if (document.getElementById('sparkHum')) sparkHumChart = new Chart(document.getElementById('sparkHum'), { type: 'line', data: { labels: [], datasets: [{ data: [], borderColor: '#3b82f6', borderWidth: 1.5 }] }, options: sparkOptions });
    if (document.getElementById('sparkAqi')) sparkAqiChart = new Chart(document.getElementById('sparkAqi'), { type: 'line', data: { labels: [], datasets: [{ data: [], borderColor: '#f59e0b', borderWidth: 1.5 }] }, options: sparkOptions });
    if (document.getElementById('sparkLux')) sparkLuxChart = new Chart(document.getElementById('sparkLux'), { type: 'line', data: { labels: [], datasets: [{ data: [], borderColor: '#f59e0b', borderWidth: 1.5 }] }, options: sparkOptions });

    if (document.getElementById('mainTempChart')) mainTempChart = new Chart(document.getElementById('mainTempChart'), { type: 'line', data: { labels: [], datasets: [{ data: [], borderColor: '#10b981', backgroundColor: 'rgba(16, 185, 129, 0.1)', borderWidth: 2, fill: true }] }, options: mainOptions });
    if (document.getElementById('mainHumChart')) mainHumChart = new Chart(document.getElementById('mainHumChart'), { type: 'line', data: { labels: [], datasets: [{ data: [], borderColor: '#3b82f6', backgroundColor: 'rgba(59, 130, 246, 0.1)', borderWidth: 2, fill: true }] }, options: mainOptions });
    if (document.getElementById('mainAqiChart')) mainAqiChart = new Chart(document.getElementById('mainAqiChart'), { type: 'line', data: { labels: [], datasets: [{ data: [], borderColor: '#f59e0b', backgroundColor: 'rgba(245, 158, 11, 0.1)', borderWidth: 2, fill: true }] }, options: mainOptions });
}

async function loadHistoricalData() {
    try {
        const response = await fetch('/api/sensors');
        if (!response.ok) return;
        const result = await response.json();

        if (result.success && result.data && result.data.length > 0) {
            const historical = result.data.slice(-maxDataPoints); // Get last bounds

            historical.forEach(data => {
                const timeStr = formatTime(data.createdAt);
                pushDataToCharts(timeStr, data, false);
            });

            updateAllCharts();
        }
    } catch (error) {
        console.error("Historical Sync Failed:", error);
    }
}

function formatTime(isoStr) {
    if (!isoStr) return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    return new Date(isoStr).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function pushDataToCharts(label, data, shouldUpdate = true) {
    const pushTo = (chart, val) => {
        if (!chart) return;
        chart.data.labels.push(label);
        chart.data.datasets[0].data.push(val);
        if (chart.data.labels.length > maxDataPoints) {
            chart.data.labels.shift();
            chart.data.datasets[0].data.shift();
        }
        if (shouldUpdate) {
            // Update without transition animation for seamless polling
            chart.update('none');
        }
    };

    pushTo(sparkTempChart, data.temperature);
    pushTo(sparkHumChart, data.humidity);
    pushTo(sparkAqiChart, data.co2);
    pushTo(sparkLuxChart, data.light);

    pushTo(mainTempChart, data.temperature);
    pushTo(mainHumChart, data.humidity);
    pushTo(mainAqiChart, data.co2);
}

function updateAllCharts() {
    if (sparkTempChart) sparkTempChart.update();
    if (sparkHumChart) sparkHumChart.update();
    if (sparkAqiChart) sparkAqiChart.update();
    if (sparkLuxChart) sparkLuxChart.update();
    if (mainTempChart) mainTempChart.update();
    if (mainHumChart) mainHumChart.update();
    if (mainAqiChart) mainAqiChart.update();
}

async function fetchLatestSensorData() {
    try {
        const response = await fetch('/api/sensors/latest');
        if (!response.ok) return;
        const result = await response.json();

        if (result.success && result.data) {
            const data = result.data;
            const timeStr = formatTime(data.createdAt);

            // Server-authoritative offline detection: backend checks lastSeenAt < 10s
            if (!data.deviceOnline) {
                updateDOMValue('dash-temp', '--');
                updateDOMValue('dash-hum', '--');
                updateDOMValue('dash-aqi', '--');
                updateDOMValue('dash-lux', '--');

                if (document.getElementById('dash-door-status')) {
                    document.getElementById('dash-door-status').innerText = '--';
                    document.getElementById('dash-door-status').className = 'fw-bold fs-5 text-muted';
                }
                if (document.getElementById('dash-ot-status')) {
                    document.getElementById('dash-ot-status').innerText = '--';
                    document.getElementById('dash-ot-status').className = 'fw-bold fs-5 text-muted';
                }

                const progText = document.getElementById('prog-text');
                const progCircle = document.getElementById('prog-circle');
                if (progText) {
                    progText.innerText = "OFFLINE";
                    progText.className = "fs-4 fw-bold text-danger";
                }
                if (progCircle) {
                    progCircle.style.background = `conic-gradient(var(--danger) 100%, var(--border-color) 0%)`;
                }
                return;
            }

            // Push values into Chart.js
            pushDataToCharts(timeStr, data, true);

            // Dashboard metric overwrites
            updateDOMValue('dash-temp', data.temperature.toFixed(1));
            updateDOMValue('dash-hum', data.humidity.toFixed(1));
            updateDOMValue('dash-aqi', data.co2.toFixed(0));
            updateDOMValue('dash-lux', data.light.toFixed(0));

            // Dynamic ESP32 Process State mapping for Sterilization Page
            const progText = document.getElementById('prog-text');
            const progCircle = document.getElementById('prog-circle');
            if (progText && progCircle && data.processState) {
                let progress = 0;
                switch (data.processState) {
                    case "IDLE": progress = 0; break;
                    case "UV_ACTIVE": progress = 25; break;
                    case "FOG_ACTIVE": progress = 50; break;
                    case "FAN_ACTIVE": progress = 75; break;
                    case "READY": progress = 100; break;
                    case "EMERGENCY": progress = 0; break;
                }
                progText.innerText = progress + "%";
                progCircle.style.background = `conic-gradient(var(--success) ${progress}%, var(--border-color) ${progress}%)`;

                if (data.processState === "EMERGENCY") progText.className = "fs-1 fw-bold text-danger";
                else if (progress === 100) progText.className = "fs-1 fw-bold text-success";
                else if (progress > 0) progText.className = "fs-1 fw-bold text-warning";
                else progText.className = "fs-1 fw-bold text-muted";
            }

            // Dashboard "Sterilization Control Panel" icons — reflect the REAL
            // firmware-reported phase instead of static, non-functional markup.
            const iconMap = { 'icon-uv': 'UV_ACTIVE', 'icon-fog': 'FOG_ACTIVE', 'icon-fan': 'FAN_ACTIVE', 'icon-verify': 'READY' };
            const order = ['UV_ACTIVE', 'FOG_ACTIVE', 'FAN_ACTIVE', 'READY'];
            const curIdx = order.indexOf(data.processState);
            Object.entries(iconMap).forEach(([id, phase]) => {
                const el = document.getElementById(id);
                if (!el) return;
                const phaseIdx = order.indexOf(phase);
                el.className = el.className.replace(/text-\S+/, '').trim();
                if (data.processState === phase) el.className += ' text-primary';
                else if (curIdx > phaseIdx) el.className += ' text-success';
                else el.className += ' text-muted';
            });

            if (document.getElementById('dash-door-status')) {
                const el = document.getElementById('dash-door-status');
                el.innerText = data.doorClosed ? 'CLOSED' : 'OPEN';
                el.className = data.doorClosed ? 'fw-bold fs-5 text-success' : 'fw-bold fs-5 text-danger';
            }
            if (document.getElementById('dash-ot-status')) {
                const el = document.getElementById('dash-ot-status');
                el.innerText = !data.motionDetected ? 'ROOM EMPTY' : 'MOTION DETECTED';
                el.className = !data.motionDetected ? 'fw-bold fs-5 text-success' : 'fw-bold fs-5 text-warning';
            }
            if (document.getElementById('dash-aqi-status')) {
                const aqi = data.co2 || data.pm25 || 0;
                const el = document.getElementById('dash-aqi-status');
                let aqiLabel, aqiClass;
                if (aqi <= 50) { aqiLabel = 'GOOD'; aqiClass = 'fw-bold fs-5 text-success'; }
                else if (aqi <= 100) { aqiLabel = 'SATISFACTORY'; aqiClass = 'fw-bold fs-5 text-success'; }
                else if (aqi <= 200) { aqiLabel = 'MODERATE'; aqiClass = 'fw-bold fs-5 text-warning'; }
                else if (aqi <= 300) { aqiLabel = 'POOR'; aqiClass = 'fw-bold fs-5 text-warning'; }
                else if (aqi <= 400) { aqiLabel = 'VERY POOR'; aqiClass = 'fw-bold fs-5 text-danger'; }
                else { aqiLabel = 'SEVERE'; aqiClass = 'fw-bold fs-5 text-danger'; }
                el.innerText = aqiLabel;
                el.className = aqiClass;
            }

            // Live Monitoring overrides
            updateDOMValue('live-temp', data.temperature.toFixed(1));
            updateDOMValue('live-hum', data.humidity.toFixed(1));
            updateDOMValue('live-aqi', data.co2.toFixed(0));

            if (document.getElementById('live-pir')) {
                const el = document.getElementById('live-pir');
                el.innerText = !data.motionDetected ? 'ROOM EMPTY' : 'MOTION DETECTED';
                el.className = !data.motionDetected ? 'fs-5 fw-bold text-success' : 'fs-5 fw-bold text-warning';
            }
            if (document.getElementById('live-door')) {
                const el = document.getElementById('live-door');
                el.innerText = data.doorClosed ? 'CLOSED' : 'OPEN';
                el.className = data.doorClosed ? 'fs-5 fw-bold text-success' : 'fs-5 fw-bold text-danger';
            }
            if (document.getElementById('live-uv')) {
                const isUVOn = data.light > 1000;
                document.getElementById('live-uv').innerText = isUVOn ? 'ON' : 'OFF';
                document.getElementById('live-uv').className = isUVOn ? 'fs-5 fw-bold text-primary' : 'fs-5 fw-bold text-muted';
            }
        }
    } catch (error) {
        console.error("Hardware Telemetry Sync Failed:", error);
    }
}

function updateDOMValue(id, value) {
    const el = document.getElementById(id);
    if (el) { el.innerText = value; }
}

function startTelemetryPolling() {
    setInterval(fetchLatestSensorData, 1000);
}

// AI Engine Integration has been moved to ai-insights.html natively

// Dashboard "Sterilization Control Panel" buttons — previously non-functional
// static markup with no click handlers at all.
document.addEventListener('DOMContentLoaded', () => {
    const btnStart = document.getElementById('btnDashStart');
    const btnReset = document.getElementById('btnResetSystem');
    if (btnStart) {
        btnStart.addEventListener('click', async () => {
            btnStart.disabled = true;
            try {
                const res = await fetch('/api/sterilization/start', { method: 'POST' });
                if (!res.ok) {
                    const msg = await res.text();
                    alert('Could not start sterilization: ' + msg);
                }
            } catch (e) {
                alert('Could not reach the backend to start sterilization.');
            } finally {
                setTimeout(() => { btnStart.disabled = false; }, 2000);
            }
        });
    }
    if (btnReset) {
        btnReset.addEventListener('click', async () => {
            if (!confirm('Abort the current cycle and reset the system?')) return;
            try {
                await fetch('/api/sterilization/abort', { method: 'POST' });
            } catch (e) { /* device may be offline */ }
        });
    }
});