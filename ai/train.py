import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest
import joblib
import os

# Create directories if they do not exist
os.makedirs("ai", exist_ok=True)

# Generate normal dataset
# Features: temperature, humidity, pm25, pm10, co2, tvoc, co, light, motionDetected, doorClosed
np.random.seed(42)
n_normal = 1000

temp = np.random.uniform(18.0, 22.0, n_normal)
humidity = np.random.uniform(45.0, 55.0, n_normal)
pm25 = np.random.uniform(5.0, 15.0, n_normal)
pm10 = np.random.uniform(10.0, 30.0, n_normal)
co2 = np.random.uniform(400.0, 600.0, n_normal)
tvoc = np.random.uniform(50.0, 150.0, n_normal)
co = np.random.uniform(0.1, 0.9, n_normal)
light = np.random.uniform(200.0, 500.0, n_normal)
motion = np.zeros(n_normal)
door = np.ones(n_normal)

# Generate 50 anomalous records representing standard violations
n_anom = 50
temp_anom = np.random.uniform(23.0, 28.0, n_anom)
humidity_anom = np.random.uniform(30.0, 70.0, n_anom)
pm25_anom = np.random.uniform(25.0, 60.0, n_anom)
pm10_anom = np.random.uniform(40.0, 100.0, n_anom)
co2_anom = np.random.uniform(800.0, 1500.0, n_anom)
tvoc_anom = np.random.uniform(200.0, 600.0, n_anom)
co_anom = np.random.uniform(1.2, 5.0, n_anom)
light_anom = np.random.uniform(10.0, 800.0, n_anom)
motion_anom = np.random.choice([0, 1], n_anom)
door_anom = np.random.choice([0, 1], n_anom)

# Concatenate normal and anomalous
X_temp = np.concatenate([temp, temp_anom])
X_humidity = np.concatenate([humidity, humidity_anom])
X_pm25 = np.concatenate([pm25, pm25_anom])
X_pm10 = np.concatenate([pm10, pm10_anom])
X_co2 = np.concatenate([co2, co2_anom])
X_tvoc = np.concatenate([tvoc, tvoc_anom])
X_co = np.concatenate([co, co_anom])
X_light = np.concatenate([light, light_anom])
X_motion = np.concatenate([motion, motion_anom])
X_door = np.concatenate([door, door_anom])

data = pd.DataFrame({
    'temperature': X_temp,
    'humidity': X_humidity,
    'pm25': X_pm25,
    'pm10': X_pm10,
    'co2': X_co2,
    'tvoc': X_tvoc,
    'co': X_co,
    'light': X_light,
    'motionDetected': X_motion,
    'doorClosed': X_door
})

# Fit Isolation Forest
model = IsolationForest(n_estimators=100, contamination=0.05, random_state=42)
model.fit(data)

# Save
joblib.dump(model, "ai/anomaly_model.joblib")
print("Model trained and saved completely to ai/anomaly_model.joblib")
