/**
 * Hardware Action Controller & Sequence Macro
 * Executes process flows to Spring Boot Device REST Proxy
 *
 * NOTE: btnStart and btnPause are handled exclusively by dashboard.js
 * via the backend state machine. relay.js only handles raw hardware-level
 * emergency actions (stop, emergency stop, reset).
 */

const ESP32_DEVICE_ID = "ESP32_OT_NODE";

async function toggleDevice(relayType, state) {
    try {
        await fetch(`/api/device/${ESP32_DEVICE_ID}/command/${relayType}/${state}`, { method: 'POST' });
    } catch (e) {
        console.error("REST Proxy Communication Error:", e);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const btnStop = document.getElementById('btnStop');
    const btnEmergencyStop = document.getElementById('btnEmergencyStop');
    const btnResetSystem = document.getElementById('btnResetSystem');

    if (btnStop) {
        btnStop.addEventListener('click', async () => {
            await toggleDevice('uvlamp', false); // Hard stop overrides
            await toggleDevice('relay', false);

            fetch('/api/logs', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    level: 'ERROR',
                    message: 'Sterilization Cycle manually aborted by Operator'
                })
            }).catch(e => console.log("Log fail"));
        });
    }

    if (btnEmergencyStop) {
        btnEmergencyStop.addEventListener('click', async () => {
            await toggleDevice('uvlamp', false);
            await toggleDevice('relay', false);
            alert('EMERGENCY STOP ENGAGED! System halted and safe.');
            fetch('/api/logs', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    level: 'ERROR',
                    message: 'Emergency Stop Pressed By Operator'
                })
            }).catch(e => console.log("Log fail"));
        });
    }

    if (btnResetSystem) {
        btnResetSystem.addEventListener('click', async () => {
            await toggleDevice('relay', false);
            await toggleDevice('uvlamp', false);
            await toggleDevice('humidifier', false);
            await toggleDevice('exhaustfan', false);
            alert('Hardware Reset Engaged: Relays have been forcefully deactivated!');
            window.location.reload();
        });
    }
});
