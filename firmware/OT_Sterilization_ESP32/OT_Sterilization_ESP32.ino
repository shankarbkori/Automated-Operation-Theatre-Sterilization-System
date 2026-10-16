#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <DHT.h>

// WiFi credentials
const char* ssid = "shankar";
const char* password = "shankar1335";

// Server configuration (Update to your Spring Boot PC IP)
const char* serverIp = "10.220.149.164"; 
const int serverPort = 8081;

// Device definition matches the database backend
const String DEVICE_ID = "ESP32_OT_NODE";

// Hardware Sensor Pins
#define PIN_DHT11     21 
#define PIN_LDR       35 // Analog Input for Light Intensity
#define PIN_MQ135     34 // Analog Input
#define PIN_IR_SENSOR 13 // Active LOW (LOW = Motion Detected)
#define PIN_KEY_SW    14 // Low = Closed. High = Open.

#define DHTTYPE DHT22 
DHT dht(PIN_DHT11, DHTTYPE);

// Hardware Relay Pins (Actuators)
#define RELAY_MAIN    25 // Main Power
#define RELAY_UV      26
#define RELAY_MIST    27 // Fogger
#define RELAY_FAN     32
#define PIN_BUZZER    18 // Dedicated Emergency Buzzer Pin

// --- HARDWARE FEATURE FLAGS ---
const bool ENABLE_FOGGER = true; // Fogger is now driven by the same AI-predicted plan as UV/exhaust

// --- STATE MACHINE ---
enum SystemState {
    STATE_IDLE,
    STATE_UV,
    STATE_FOG,
    STATE_FAN,
    STATE_READY,
    STATE_EMERGENCY
};

SystemState currentState = STATE_IDLE;
unsigned long stateStartTime = 0;
bool cycleRequested = false;

// Timers (Scaling down minutes to seconds for demonstration purposes)
unsigned long UV_TIME = 15000;   // overwritten by AI plan on fetch (fallback default)
unsigned long FOG_TIME = 20000;  // overwritten by AI plan on fetch (fallback default)
unsigned long FAN_TIME = 20000;  // overwritten by AI plan on fetch (fallback default)
long lastAppliedCycleSequence = -1;

// Sensor readings globally accessible for the State Machine
float currentTemp = 0.0;
float currentHum = 0.0;
int currentGas = 0;
bool motionDetected = false;
bool doorClosed = false;

unsigned long lastPollTime = 0;
unsigned long lastHeartbeatTime = 0;

void setup() {
    Serial.begin(115200);

    // Initialize inputs
    pinMode(PIN_DHT11, INPUT);
    pinMode(PIN_MQ135, INPUT);
    pinMode(PIN_LDR, INPUT);
    pinMode(PIN_IR_SENSOR, INPUT_PULLUP); // Active-LOW PIR: HIGH = no motion, LOW = motion
    pinMode(PIN_KEY_SW, INPUT_PULLUP);      // LOW = door closed
    
    dht.begin();

    // Initialize outputs (Active-HIGH relays default to LOW/OFF)
    pinMode(RELAY_MAIN, OUTPUT);
    digitalWrite(RELAY_MAIN, LOW); 

    pinMode(RELAY_UV, OUTPUT);
    digitalWrite(RELAY_UV, LOW);

    pinMode(RELAY_MIST, OUTPUT);
    digitalWrite(RELAY_MIST, LOW);

    pinMode(RELAY_FAN, OUTPUT);
    digitalWrite(RELAY_FAN, LOW);

    pinMode(PIN_BUZZER, OUTPUT);
    digitalWrite(PIN_BUZZER, LOW);

    // Connect WiFi
    WiFi.begin(ssid, password);
    Serial.print("Connecting to WiFi");
    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }
    Serial.println("\nWiFi Connected. IP: " + WiFi.localIP().toString());
}

void loop() {
    readSensors();
    handleStateMachine();

    // Poll the backend every 1 seconds for Commands and push Telemetry
    if (millis() - lastPollTime >= 1000) {
        if (WiFi.status() == WL_CONNECTED) {
            postSensorData();
            fetchDeviceCommands();
        }
        lastPollTime = millis();
    }

    // Send heartbeat every 5 seconds
    if (millis() - lastHeartbeatTime >= 5000) {
        if (WiFi.status() == WL_CONNECTED) {
            sendHeartbeat();
        }
        lastHeartbeatTime = millis();
    }
    
    delay(10); // Yield for WiFi stack stability
}

// ---------------------------------------------------------
// Heartbeat — tells the backend the ESP32 is alive.
// Called independently of sensor telemetry so the backend
// can detect disconnection even if sensor reads fail.
// ---------------------------------------------------------
void sendHeartbeat() {
    if (WiFi.status() != WL_CONNECTED) return;
    HTTPClient http;
    String url = "http://" + String(serverIp) + ":" + String(serverPort) + "/api/device/heartbeat";
    http.begin(url);
    http.addHeader("Content-Type", "application/json");
    String body = "{\"deviceId\":\"ESP32_OT_NODE\",\"ip\":\"" + WiFi.localIP().toString() + "\"}";
    int code = http.POST(body);
    http.end();
    Serial.print("[HB] "); Serial.println(code);
}


void readSensors() {
    float t = dht.readTemperature(); 
    float h = dht.readHumidity(); 
    
    if(!isnan(t) && !isnan(h)) {
        currentTemp = t;
        currentHum = h;
    }
    // No fallback: if DHT22 is not connected, keep values at 0.0
    // so the dashboard correctly shows DHT22 as Inactive

    currentGas = analogRead(PIN_MQ135);
    motionDetected = digitalRead(PIN_IR_SENSOR) == LOW; 
    doorClosed = digitalRead(PIN_KEY_SW) == LOW; 
}

void handleEmergency() {
    // Immediate Critical Hardware Sever
    digitalWrite(RELAY_UV, LOW);
    digitalWrite(RELAY_MIST, LOW);
    digitalWrite(RELAY_FAN, LOW);
    digitalWrite(PIN_BUZZER, HIGH);
    
    cycleRequested = false;
    
    if(currentState != STATE_EMERGENCY) {
        Serial.println("!!! CRITICAL EMERGENCY: BREACH DETECTED. SYSTEM ABORTED. !!!");
        // In a subsequent update, we can push an immediate alert payload to the backend here.
    }
    currentState = STATE_EMERGENCY;
}

void handleStateMachine() {

    // 1. CRITICAL SAFETY CHECK: Override everything if sensors flag anomalies during an active cycle!
    if (cycleRequested || currentState != STATE_IDLE) {
        if (motionDetected || !doorClosed) {
            handleEmergency();
            return; 
        }
    }

    // 1.5. CHECK FOR BACKEND ABORT / COMPLETION
    // If backend clears the cycle, force hardware to stop any active phase
    if (!cycleRequested && (currentState == STATE_UV || currentState == STATE_FOG || currentState == STATE_FAN)) {
        Serial.println("Backend cleared cycle request. Shutting down active elements.");
        digitalWrite(RELAY_UV, LOW);
        digitalWrite(RELAY_MIST, LOW);
        digitalWrite(RELAY_FAN, LOW);
        currentState = STATE_READY;
    }

    // 2. STATE TRANSITIONS
    switch (currentState) {
        case STATE_IDLE:
            // Relax redundant LOW writes to allow manual overrides 
            // set by fetchDeviceCommands() to persist between API polls.
            
            // Wait for user to trigger cycle over REST API
            if (cycleRequested && doorClosed && !motionDetected) {
                stateStartTime = millis();
                if (UV_TIME > 0) {
                    Serial.println("Starting Cycle: Transitioning to UV_ACTIVE");
                    currentState = STATE_UV;
                    digitalWrite(RELAY_UV, HIGH);
                } else if (ENABLE_FOGGER && FOG_TIME > 0) {
                    Serial.println("Starting Cycle (UV skipped): Transitioning to FOG_ACTIVE");
                    currentState = STATE_FOG;
                    digitalWrite(RELAY_MIST, HIGH);
                } else if (FAN_TIME > 0) {
                    Serial.println("Starting Cycle (UV/Fog skipped): Transitioning to FAN_ACTIVE");
                    currentState = STATE_FAN;
                    digitalWrite(RELAY_FAN, HIGH);
                } else {
                    cycleRequested = false;
                }
            }
            break;

        case STATE_UV:
            if (millis() - stateStartTime >= UV_TIME) {
                digitalWrite(RELAY_UV, LOW);
                
                if (ENABLE_FOGGER && FOG_TIME > 0) {
                    Serial.println("UV Complete. Transitioning to FOG_ACTIVE");
                    currentState = STATE_FOG;
                    digitalWrite(RELAY_MIST, HIGH);
                } else {
                    Serial.println("UV Complete (Fogging Bypassed or 0s). Transitioning to FAN_ACTIVE");
                    currentState = STATE_FAN;
                    digitalWrite(RELAY_FAN, HIGH);
                }
                
                stateStartTime = millis();
            }
            break;

        case STATE_FOG:
            if (millis() - stateStartTime >= FOG_TIME) {
                Serial.println("Fogging Complete. Transitioning to FAN_ACTIVE");
                digitalWrite(RELAY_MIST, LOW);
                currentState = STATE_FAN;
                stateStartTime = millis();
                digitalWrite(RELAY_FAN, HIGH);
            }
            break;

        case STATE_FAN:
            // Fan turns off when air quality is confirmed safe OR the AI-predicted
            // duration elapses, whichever comes first — a fixed cap prevents the
            // fan from ever running forever if the gas threshold is never reached.
            if (currentGas < 1500 || (millis() - stateStartTime >= FAN_TIME)) {
                Serial.println("Air Quality Safe. Validating Temperature/Humidity...");
                
                // Read DHT11 bounds (e.g. Temp between 20-25C, Hum between 40-60%)
                if (currentTemp >= 18.0 && currentTemp <= 26.0 && currentHum >= 40.0 && currentHum <= 65.0) {
                    digitalWrite(RELAY_FAN, LOW);
                    currentState = STATE_READY;
                    Serial.println("Room Marked Ready.");
                } else if (millis() - stateStartTime >= FAN_TIME * 3) {
                    // Absolute safety cap: temp/humidity never settled into range.
                    // Force completion rather than run the fan indefinitely.
                    digitalWrite(RELAY_FAN, LOW);
                    currentState = STATE_READY;
                    Serial.println("Room marked Ready (safety timeout, verify manually).");
                } else {
                    // Environmental parameters unsafe, continue fanning/circulation
                }
            }
            break;

        case STATE_READY:
            // Holds state until manually reset back to IDLE via Backend toggle
            if (!cycleRequested) {
                currentState = STATE_IDLE;
            }
            break;
            
        case STATE_EMERGENCY:
            // Holds state until manually reset via Backend toggle
            if (!cycleRequested) {
                digitalWrite(PIN_BUZZER, LOW);
                currentState = STATE_IDLE;
            }
            break;
    }
}

void postSensorData() {
    HTTPClient http;
    String url = String("http://") + serverIp + ":" + serverPort + "/api/sensors";
    http.begin(url);
    http.setTimeout(10000); // 10s — gives Spring Boot + SQLite enough time to respond
    http.addHeader("Content-Type", "application/json");

    int lightAnalog = analogRead(PIN_LDR);

    StaticJsonDocument<512> doc;
    doc["temperature"] = currentTemp;
    doc["humidity"] = currentHum;
    // Map raw MQ-135 ADC (0–4095) directly to NAQI 0–500 scale
    // Factor 0.12 gives: ADC 400→48(Good), ADC 830→100(Satisfactory), ADC 2500→300(Poor), ADC 4095→491(Severe)
    float aqiValue = (currentGas > 10) ? min(currentGas * 0.12f, 500.0f) : 0.0f;
    doc["pm25"] = aqiValue;
    doc["pm10"] = aqiValue * 1.5;
    doc["co2"] = aqiValue;
    doc["tvoc"] = (currentGas > 10) ? currentGas * 0.05 : 0.0;
    doc["co"] = 0.5;
    doc["light"] = lightAnalog; 
    doc["motionDetected"] = motionDetected;
    doc["doorClosed"] = doorClosed;

    const char* stateNames[] = {"IDLE", "UV_ACTIVE", "FOG_ACTIVE", "FAN_ACTIVE", "READY", "EMERGENCY"};
    doc["processState"] = stateNames[currentState];

    // Send string payload
    String jsonString;
    serializeJson(doc, jsonString);

    int httpCode = http.POST(jsonString);
    if (httpCode > 0) {
        // Serial.println("Sensors Posted successfully.");
    } else {
        Serial.println("POST failed: " + http.errorToString(httpCode));
    }
    http.end();
}

void fetchDeviceCommands() {
    HTTPClient http;
    String url = String("http://") + serverIp + ":" + serverPort + "/api/device/" + DEVICE_ID + "/plan";
    http.begin(url);

    int httpCode = http.GET();
    if (httpCode == 200) {
        String payload = http.getString();
        StaticJsonDocument<512> doc;
        DeserializationError error = deserializeJson(doc, payload);

        if (!error) {
            bool relayFlag = doc["relayActive"];
            long cycleSequence = doc["cycleSequence"] | -1L;
            long plannedUv = doc["plannedUvSeconds"] | 0L;
            long plannedFog = doc["plannedFoggingSeconds"] | 0L;
            long plannedExh = doc["plannedExhaustSeconds"] | 0L;

            // Adopt the AI-predicted durations only when a genuinely new plan
            // arrives (sequence changed), so an in-progress phase timer is
            // never reset mid-way by a repeated poll of the same plan.
            if (cycleSequence != lastAppliedCycleSequence) {
                UV_TIME  = (unsigned long)plannedUv * 1000UL;
                FOG_TIME = (unsigned long)plannedFog * 1000UL;
                FAN_TIME = (unsigned long)plannedExh * 1000UL;
                lastAppliedCycleSequence = cycleSequence;
                Serial.printf("[AI PLAN] seq=%ld uv=%lus fog=%lus exh=%lus\n",
                              cycleSequence, UV_TIME/1000, FOG_TIME/1000, FAN_TIME/1000);
            }

            if (relayFlag && !cycleRequested && currentState == STATE_IDLE) {
                cycleRequested = true;
                digitalWrite(RELAY_MAIN, HIGH);
            }
            else if (!relayFlag && cycleRequested) {
                cycleRequested = false;
                digitalWrite(RELAY_MAIN, LOW);
                currentState = STATE_IDLE;
                digitalWrite(PIN_BUZZER, LOW);
            }

            // Manual relay override only permitted when no automated cycle is running
            if (currentState == STATE_IDLE && !cycleRequested) {
                bool uv = doc["uvLampActive"] | false;
                bool mist = doc["humidifierActive"] | false;
                bool fan = doc["exhaustFanActive"] | false;
                digitalWrite(RELAY_UV, uv ? HIGH : LOW);
                digitalWrite(RELAY_MIST, mist ? HIGH : LOW);
                digitalWrite(RELAY_FAN, fan ? HIGH : LOW);

                // Enable main power contactor if any manual override is active
                if (uv || mist || fan) {
                    digitalWrite(RELAY_MAIN, HIGH);
                } else {
                    digitalWrite(RELAY_MAIN, LOW);
                }
            }
        }
    }
    http.end();
}
