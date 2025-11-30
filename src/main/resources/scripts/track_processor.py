import cv2
import numpy as np
import json
import sys

# --- CONFIGURATION: CENTRED ON THE MEADOWS ---
# Center: -3.192, 55.942

# Longitude: -3.192 +/- 0.025 (Wider range = Bigger horizontal track)
MIN_LNG, MAX_LNG = -3.21, -3.170

# Latitude:  55.942 +/- 0.015 (Higher range = Bigger vertical track)
MIN_LAT, MAX_LAT = 55.940, 55.945
# ---------------------------------------------

def process_track(image_path):
    img = cv2.imread(image_path)
    if img is None:
        print(json.dumps({"error": "Could not read image"}))
        return

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    _, binary = cv2.threshold(gray, 200, 255, cv2.THRESH_BINARY_INV)
    contours, _ = cv2.findContours(binary, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    if not contours:
        print(json.dumps({"coordinates": []}))
        return

    # Find the largest shape (the track)
    largest_contour = max(contours, key=cv2.contourArea)
    contour_points = largest_contour[:, 0, :].astype(float)

    # Get bounding box of the DRAWING in the image
    min_x, min_y = contour_points.min(axis=0)
    max_x, max_y = contour_points.max(axis=0)

    width = max((max_x - min_x), 1.0)
    height = max((max_y - min_y), 1.0)

    # Normalize drawing to 0.0 - 1.0 range
    normalized = (contour_points - [min_x, min_y]) / [width, height]

    # Scale normalized points to the new Edinburgh Geographic Bounds
    # This "Stretches" the 0-1 drawing to fill the Lat/Lng area defined above
    lngs = MIN_LNG + normalized[:, 0] * (MAX_LNG - MIN_LNG)
    lats = MAX_LAT - normalized[:, 1] * (MAX_LAT - MIN_LAT)

    coords = np.column_stack((lngs, lats)).tolist()

    geojson = {
        "type": "LineString",
        "coordinates": coords
    }

    print(json.dumps(geojson))

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No image path provided"}))
    else:
        process_track(sys.argv[1])