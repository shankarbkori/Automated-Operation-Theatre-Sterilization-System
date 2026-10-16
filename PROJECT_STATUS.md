# AI-Based Automated OT Sterilization System - Project Status

## Project Overview
This repository contains the software application for a smart, automated sterilization system for hospital operation theatres utilizing ESP32 IoT hardware, Spring Boot backend, and Python ML anomaly detection. Note: Raspberry Pi is strictly NOT used.

## Completed Modules
- Basic Spring Boot structure and database configuration initialized.
- Dashboard Thymeleaf wrapper (`dashboard/index.html`).

## Remaining Modules
- **Entities:** `DeviceStatus` missing relay tracks, `SterilizationSession` missing execution logic.
- **Frontend / Static Assets:** Blank JavaScript files (`api.js`, `relay.js`, etc.) need to be written to wire dashboard polling.
- **Controllers:** The extra HTML pages (`health/index.html`, etc.) are floating without view routes. Missing ESP32 command control endpoints.
- **ESP32 Firmware:** The entire ESP32 hardware client (`firmware/`) needs to be implemented via Arduino C++ format (simulated with ESP32 REST calls).
- **AI/ML predictions:** Isolation forest bridging needs to be re-connected properly for the ESP32.

## Architecture
- **Hardware:** ESP32 (Sensors: DHT22, MQ135, PIR, Reed Switch; Outputs: Relay, UV Lamp Demo, Mist Pump Demo, Exhaust Fan Demo)
- **Backend:** Spring Boot (Java, Maven, Spring Data JPA, SQLite, REST APIs)
- **Frontend:** HTML, CSS, JavaScript, Bootstrap, Thymeleaf, Chart.js

## Current Progress Percentage
- 10% (Base repository audited, debt identified)

## Known Issues (Audit Debt)
1. **Unfinished modules:** `api.js`, `relay.js` are blank.
2. **Missing APIs:** No API allows commanding the ESP32 to turn ON/OFF relays.
3. **Inconsistent Architecture:** `DeviceStatus` entity lacks tracking for the actual outputs (UV Lamp, Exhaust Fan, Humidifier, Relay).
4. **Routing Issues:** `health/index.html`, `reports/index.html`, and `sensors/live.html` have no backend mappings.
5. **ESP32 Integration:** `ESP32Simulator.java` is completely empty. No firmware exists.

## Upcoming Milestones
1. Fix Audit Debt: Update `DeviceStatus`, add missing API endpoints, fix routing.
2. Complete static Javascript plumbing for dashboard real-time fetches.
3. Connect simulation outputs / AI recommendations.
4. Provide ESP32 prototype code in `firmware/`.
