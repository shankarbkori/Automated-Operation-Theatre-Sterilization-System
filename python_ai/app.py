import os
import sqlite3
import pandas as pd
import numpy as np
from flask import Flask, jsonify
from flask_cors import CORS
from sklearn.ensemble import IsolationForest
from sklearn.linear_model import LinearRegression

app = Flask(__name__)
CORS(app)

import pathlib

# Use workspace-relative database path when available
BASE_DIR = pathlib.Path(__file__).resolve().parents[1]
DB_PATH = str(BASE_DIR.joinpath('database', 'ot.db'))

anomaly_model = None
time_model = None

# Keep a small per-phase durations map to match frontend expectations (seconds)
STEP_DURATIONS_SECONDS = {
    1: 20,   # Room Empty
    2: 15,   # Door Closed
    3: 120,  # UV-C Lamp
    4: 180,  # Fogging
    5: 90,   # Air Purification / Exhaust
    6: 30,   # Air Quality Check
    7: 10    # Room Ready
}
# FAST_MODE forces short-cycle timings for demo/submission purposes.
# Set True to make predictions and plans complete within ~2 minutes.
FAST_MODE = False

@app.route('/api/ai/train', methods=['GET', 'POST'])
def train_models():
    global anomaly_model, time_model
    try:
        # Prefer dataset in workspace root
        csv_path = str(BASE_DIR.joinpath('OT_Sterilization_Dataset (1).csv'))

        if not os.path.exists(csv_path):
            # fallback to original literal path if present on D: drive
            csv_path_fallback = r"d:\final year project\Automated-OT-Sterilization-System-Clone\OT_Sterilization_Dataset (1).csv"
            if os.path.exists(csv_path_fallback):
                csv_path = csv_path_fallback
            else:
                return jsonify({"success": False, "message": "Kaggle Dataset CSV not found in workspace for training."})

        # Load historical dataset, not the live SQLite DB
        df = pd.read_csv(csv_path)

        if len(df) < 5:
            return jsonify({"success": False, "message": "Not enough data in Kaggle CSV."})

        # Feature preparation mapping to 'temperature', 'humidity', 'pm25', 'pm10', 'co2'
        # The columns in CSV: Temperature_C, Humidity_pct, PM2_5_ugm3, PM10_ugm3, CO2_ppm
        df_features = df[['Temperature_C', 'Humidity_pct', 'PM2_5_ugm3', 'PM10_ugm3', 'CO2_ppm']].fillna(0)

        # 1. Anomaly Model (Isolation Forest)
        anomaly_model = IsolationForest(contamination=0.1, random_state=42)
        anomaly_model.fit(df_features)

        # 2. Time estimation Model (Linear Regression) predicting individual stage durations
        target = df[['Estimated_UV_Time_sec', 'Estimated_Fog_Time_sec', 'Estimated_Fan_Time_sec']]
        
        time_model = LinearRegression()
        time_model.fit(df_features, target)

        return jsonify({"success": True, "message": f"Successfully trained AI models on {len(df)} datapoints from Kaggle Dataset."})
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500


@app.route('/api/ai/predict', methods=['GET'])
def predict_status():
    global anomaly_model, time_model
    try:
        if anomaly_model is None or time_model is None:
            # Auto trigger train if models are empty
            train_res = train_models()
        
        conn = sqlite3.connect(DB_PATH)
        # Get latest element
        df_latest = pd.read_sql_query("SELECT * FROM sensor_data ORDER BY id DESC LIMIT 1", conn)
        conn.close()

        if df_latest.empty:
            return jsonify({"success": False, "message": "No data available in system."})
        
        # Rename columns to match Kaggle Dataset features trained locally
        df_latest_renamed = df_latest.rename(columns={
            'temperature': 'Temperature_C',
            'humidity': 'Humidity_pct',
            'pm25': 'PM2_5_ugm3',
            'pm10': 'PM10_ugm3',
            'co2': 'CO2_ppm'
        })
        
        latest_features = df_latest_renamed[['Temperature_C', 'Humidity_pct', 'PM2_5_ugm3', 'PM10_ugm3', 'CO2_ppm']].fillna(0)
        
        co2_val = df_latest['co2'].iloc[0]
        pm_val = df_latest['pm25'].iloc[0]
        # Thresholds aligned with NAQI AQI scale (0-500), not raw ppm
        unsafe_air = True if co2_val > 100 or pm_val > 100 else False

        if anomaly_model is None or time_model is None:
            # Models couldn't initialize
            return jsonify({
                "success": True,
                "data": {
                    "estimatedSterilizationTimeMinutes": 0,
                    "unsafeAirQuality": unsafe_air,
                    "abnormalSensorBehavior": False,
                    "anomalyScore": 1
                }
            })

        # Predict anomaly (-1 is anomalous, 1 is normal)
        anomaly_pred = anomaly_model.predict(latest_features)[0]
        is_anomalous = True if anomaly_pred == -1 else False

        # Predict estimated time — model returns [uv_sec, fog_sec, fan_sec]
        predictions = time_model.predict(latest_features)[0]
        total_seconds = float(np.sum(predictions))
        if total_seconds < 0: total_seconds = 0.0
        est_time = total_seconds / 60.0  # Convert seconds → minutes

        # Compute conservative minimum using frontend-aligned phase durations
        min_total_seconds = (STEP_DURATIONS_SECONDS[3] + STEP_DURATIONS_SECONDS[4] + STEP_DURATIONS_SECONDS[5])
        DEFAULT_MIN_TOTAL_MINUTES = min_total_seconds / 60.0
        if est_time < DEFAULT_MIN_TOTAL_MINUTES:
            est_time = DEFAULT_MIN_TOTAL_MINUTES

        # FAST_MODE demo override: compress estimate to <= 2 minutes so UI matches demo timings
        if FAST_MODE:
            est_time = min(est_time, 2.0)

        return jsonify({
            "success": True,
            "data": {
                "estimatedSterilizationTimeMinutes": round(est_time, 1),
                "unsafeAirQuality": unsafe_air,
                "abnormalSensorBehavior": is_anomalous,
                "anomalyScore": int(anomaly_pred) # 1 or -1
            }
        })
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500


@app.route('/api/ai/sterilization-plan', methods=['POST'])
def sterilization_plan():
    """
    Called by SterilizationStateMachineImpl.java during AI_ANALYSIS state.
    Accepts live sensor data and returns per-phase durations in seconds.
    
    Request body (JSON):
      { temperature, humidity, pm25, pm10, co2 }
    Response:
      { uvSeconds, foggingSeconds, exhaustSeconds, notes }
    """
    global anomaly_model, time_model
    from flask import request
    try:
        body = request.get_json(force=True) or {}
        temp   = float(body.get('temperature', 25))
        hum    = float(body.get('humidity', 50))
        pm25   = float(body.get('pm25', 0))
        pm10   = float(body.get('pm10', 0))
        co2    = float(body.get('co2', 400))

        # Rule-based baseline (seconds) - align with frontend STEP_DURATIONS
        uv_seconds      = STEP_DURATIONS_SECONDS.get(3, 120)
        fogging_seconds = STEP_DURATIONS_SECONDS.get(4, 180)
        exhaust_seconds = STEP_DURATIONS_SECONDS.get(5, 90)
        notes = "Baseline fallback using frontend-aligned phase durations"

        # If FAST_MODE is enabled (submission/demo), scale all phase durations
        # down so the total sterilization completes ≲ 2 minutes.
        if FAST_MODE:
            total_default = uv_seconds + fogging_seconds + exhaust_seconds
            if total_default > 0:
                scale = (120.0 / total_default)
                uv_seconds = max(10, int(uv_seconds * scale))
                fogging_seconds = max(5, int(fogging_seconds * scale))
                exhaust_seconds = max(5, int(exhaust_seconds * scale))

        if anomaly_model is None or time_model is None:
            # Try to auto-train before giving up
            train_models()

        if anomaly_model is not None and time_model is not None:
            import pandas as pd
            features = pd.DataFrame([{
                'Temperature_C': temp,
                'Humidity_pct':  hum,
                'PM2_5_ugm3':    pm25,
                'PM10_ugm3':     pm10,
                'CO2_ppm':       co2
            }])

            # Predict individual stage durations directly
            predictions = time_model.predict(features)[0]
            uv_seconds = int(predictions[0])
            fogging_seconds = int(predictions[1])
            exhaust_seconds = int(predictions[2])

            # Enforce reasonable minimums to prevent invalid predictions
            uv_seconds = max(10, uv_seconds)
            fogging_seconds = max(10, fogging_seconds)
            exhaust_seconds = max(10, exhaust_seconds)

            # Calculate total time naturally from the individual predicted stages
            total_seconds = uv_seconds + fogging_seconds + exhaust_seconds

            # Optional rule-based extensions for heavily polluted rooms
            if co2 > 100 or pm25 > 100:  # NAQI AQI scale thresholds
                uv_seconds      = int(uv_seconds      * 1.25)
                fogging_seconds = int(fogging_seconds * 1.25)
                notes = f"Extended durations due to elevated CO2={co2:.0f}ppm / PM2.5={pm25:.0f}"
            elif temp > 35:
                uv_seconds = int(uv_seconds * 1.10)
                notes = f"UV slightly extended due to high temperature ({temp:.1f}C)"
            else:
                notes = f"AI-predicted stages: UV={uv_seconds}s, Fog={fogging_seconds}s, Fan={exhaust_seconds}s (Total: {total_seconds}s)"

            # FAST_MODE override: compress the predicted plan to finish within ~2 minutes
            if FAST_MODE:
                total_sec = uv_seconds + fogging_seconds + exhaust_seconds
                if total_sec > 0:
                    s = 120.0 / total_sec
                    uv_seconds = max(8, int(uv_seconds * s))
                    fogging_seconds = max(4, int(fogging_seconds * s))
                    exhaust_seconds = max(4, int(exhaust_seconds * s))

        return jsonify({
            "success": True,
            "uvSeconds":      uv_seconds,
            "foggingSeconds": fogging_seconds,
            "exhaustSeconds": exhaust_seconds,
            "totalSeconds":   uv_seconds + fogging_seconds + exhaust_seconds,
            "aiAvailable":    True,
            "notes":          notes
        })
    except Exception as e:
        # Always return fallback values so the state machine never blocks
        # Use SHORT durations matching Java FALLBACK_* constants
        import traceback
        print(f"[STERILIZATION-PLAN ERROR] {str(e)}")
        traceback.print_exc()
        return jsonify({
            "success": False,
            "uvSeconds":      45,
            "foggingSeconds": 30,
            "exhaustSeconds": 25,
            "totalSeconds":   100,
            "aiAvailable":    False,
            "notes":          f"Error: {str(e)} — using safe fallback values"
        }), 200   # 200 so Java client doesn't throw


if __name__ == '__main__':
    # Force train on startup
    print("Initializing AOTS Machine Learning Node...")
    app.run(host='0.0.0.0', port=5000, debug=False)
