import sys
import json
import joblib
import numpy as np
import os
import warnings

# Suppress sklearn UserWarning warnings
warnings.filterwarnings("ignore", category=UserWarning)

def predict():
    if len(sys.argv) < 11:
        print(json.dumps({"error": "Missing parameters. Required 10 sensor inputs."}))
        return

    try:
        # Args order: temp, humidity, pm25, pm10, co2, tvoc, co, light, motionDetected, doorClosed
        inputs = [float(arg) for arg in sys.argv[1:11]]
        
        # Load model relative to current file path
        script_dir = os.path.dirname(os.path.abspath(__file__))
        model_path = os.path.join(script_dir, "anomaly_model.joblib")
        model = joblib.load(model_path)
        
        # Features ordering must match training set
        features = np.array([inputs])
        
        # Predict: 1 = normal, -1 = anomaly
        pred = model.predict(features)[0]
        
        anomaly = bool(pred == -1)
        
        # Generate smart recommendations based on specific parameter excursions
        recommendations = []
        
        # Unpack values for condition testing
        t, h, pm25, pm10, co2, tvoc, co, light, motion, door = inputs
        
        if anomaly:
            if pm25 > 15.0 or pm10 > 30.0:
                recommendations.append("High PM particulate density. Examine HEPA filtration units and check physical seals.")
            if co2 > 650.0:
                recommendations.append("CO2 limits exceeded. Increase pure air purging circulation flowrates immediately.")
            if tvoc > 150.0:
                recommendations.append("Elevated volatile compounds. Scan for chemical spillages or gaseous sterilization residue.")
            if co > 0.9:
                recommendations.append("CRITICAL: Carbon Monoxide spike! Immediate evacuation or purge ventilation required.")
            if door == 0:  # Open door
                recommendations.append("OT gateway door is open. Close entry partitions to protect cleanroom sterility.")
            if motion == 1 and door == 0:  # Safety violation
                recommendations.append("Safety Alert: Motion detected in vicinity while partition door is open.")
            if t > 23.0:
                recommendations.append("Operating temperature exceeds aseptic limits. Adjust HVAC cooling settings.")
            
            if not recommendations:
                recommendations.append("Environmental boundary anomaly flagged. Inspect cleanroom monitors.")
        else:
            recommendations.append("System normal. Aseptic environment indicators are stable.")

        result = {
            "anomaly": anomaly,
            "recommendations": recommendations
        }
        print(json.dumps(result))

    except Exception as e:
        print(json.dumps({"error": str(e)}))

if __name__ == "__main__":
    predict()
