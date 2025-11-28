package uk.ac.ed.inf.ilpcw1.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.ed.inf.ilpcw1.data.GeoJsonLineString;
import uk.ac.ed.inf.ilpcw1.data.GeoJsonPolygon;
import uk.ac.ed.inf.ilpcw1.service.TrackGenerationService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/track")
public class TrackController {

    private final TrackGenerationService trackService;

    public TrackController(TrackGenerationService trackService) {
        this.trackService = trackService;
    }

    @PostMapping("/upload")
    public ResponseEntity<GeoJsonLineString> uploadTrackImage(@RequestParam("file") MultipartFile file) {
        try {
            // Check if file is empty
            if (file.isEmpty()) {
                throw new RuntimeException("File is empty");
            }

            // Calls the service that runs your Python script
            return ResponseEntity.ok(trackService.extractTrackFromImage(file));

        } catch (Exception e) {
            e.printStackTrace(); // Print error to console for debugging
            throw new RuntimeException("Failed to process image: " + e.getMessage());
        }
    }

    // --- NEW ENDPOINT ---
    @GetMapping("/presets")
    public ResponseEntity<Map<String, GeoJsonPolygon>> getTrackPresets() {
        return ResponseEntity.ok(trackService.getPreloadedTracks());
    }
}