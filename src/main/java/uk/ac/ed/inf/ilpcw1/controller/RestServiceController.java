package uk.ac.ed.inf.ilpcw1.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.inf.ilpcw1.data.*;
import uk.ac.ed.inf.ilpcw1.service.RestService;
import uk.ac.ed.inf.ilpcw1.service.ValidationService;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/v1")
public class RestServiceController {

    private final RestService restService;
    private final ValidationService validationService;

    @Autowired
    public RestServiceController(RestService restService, ValidationService validationService) {
        this.restService = restService;
        this.validationService = validationService;
    }

    @GetMapping("/uid")
    public String getStudentId() {
        return "s2524182";
    }

    /**
     * Calculate the distance between two geographical positions.
     * @param request - The request body containing position1 and position2.
     * @return - The calculated distance as a Double.
     */
    @PostMapping("/distanceTo")
    public ResponseEntity<Double> calculateDistance(@RequestBody DistanceRequest request) {
        // Validate the entire request
        validationService.validateDistanceRequest(request);

        Double distance = restService.calculateDistance(request.getPosition1(), request.getPosition2());
        return ResponseEntity.ok(distance);
    }

    /**
     * Check if two geographical positions are within a close distance.
     * @param request - The request body containing position1 and position2.
     * @return - True if the positions are close, false otherwise.
     */
    @PostMapping("/isCloseTo")
    public ResponseEntity<Boolean> isCloseTo(@RequestBody CloseToRequest request) {
        // Validate the entire request
        validationService.validateCloseTo(request);

        boolean isClose = restService.isCloseTo(request.getPosition1(), request.getPosition2());
        return ResponseEntity.ok(isClose);
    }


    /**
     * Calculate the next geographical position based on a starting position and an angle.
     * @param request - The request body containing the start position and angle.
     * @return - The new geographical position as LngLat.
     */
    @PostMapping("/nextPosition")
    public ResponseEntity<LngLat> nextPosition(@RequestBody NextPositionRequest request) {
        // Validate the entire request
        validationService.validateNextPositionRequest(request);

        LngLat position = restService.nextPosition(request.getStart(), request.getAngle());
        return ResponseEntity.ok(position);
    }


    /**
     * Check if a geographical position is inside a specified region.
     * @param request - The request body containing the position and region.
     * @return - True if the position is inside the region, false otherwise.
     */
    @PostMapping("/isInRegion")
    public ResponseEntity<Boolean> isInRegion(@RequestBody RegionRequest request) {
        // Validate the entire request
        validationService.validateIsInRegionRequest(request);

        boolean insideRegion = restService.isInRegion(request.getPosition(), request.getRegion());
        return ResponseEntity.ok(insideRegion);
    }

//    ============================= CW2 ENDPOINTS ========================================
//
//    /**
//     * Get drones which support or don't support cooling
//     * @param state true for drones with cooling - false for drones that don't
//     * @return List of drone ID's
//     */
//    @GetMapping("/dronesWithCooling/{state}")
//    public ResponseEntity<List<String>> getDronesWithCooling(@PathVariable boolean state) {
//        validationService.validateCoolingState(state);
//        List<String> droneIds = droneQueryService.filterByCooling(state);
//        return ResponseEntity.ok(droneIds);
//    }
//
//    /**
//     * Get details of single specific drone
//     * @param id the DroneID
//     * @return The drone object
//     * @throws uk.ac.ed.inf.ilpcw1.exception.DroneNotFoundException if dront not found (404)
//     */
//    @GetMapping("/droneDetails/{id}")
//    public ResponseEntity<Drone> getDroneDetails(@PathVariable String id) {
//        validationService.validateDroneId(id);
//        Drone drone = droneQueryService.getByDroneId(id);
//        return ResponseEntity.ok(drone);
//    }
//
//    /**
//     * 3 Query drones by a single attribute via GET path variables.
//     * @param attributeName The attribute to check (e.g., "id", "capacity").
//     * @param attributeValue The value to match (e.g., "4", "8").
//     * @return A list of matching drone IDs.
//     */
//    @GetMapping("/queryAsPath/{attribute-name}/{attribute-value}")
//    public ResponseEntity<List<String>> queryDronesByPath(
//            @PathVariable("attribute-name") String attributeName,
//            @PathVariable("attribute-value") String attributeValue) {
//
//        List<String> droneIds = droneQueryService.queryByAttribute(attributeName, attributeValue);
//        return ResponseEntity.ok(droneIds);
//    }
//
//
//    /**
//     * 4 Query drones by multiple attributes via POST request body.
//     * @param queries - The list of query requests containing attribute names and values.
//     * @return - A list of matching drone IDs.
//     */
//    @PostMapping("/query")
//    public ResponseEntity<List<String>> queryDrones(@RequestBody List<DroneQueryRequest> queries) {
//
//        List<String> droneIds = droneQueryService.queryDrones(queries);
//        return ResponseEntity.ok(droneIds);
//    }
//
//    /**
//     * 4 Query available drones for a list of medical dispatch records.
//     * @param medDispatchRec - The list of medical dispatch records.
//     * @return - The list of available drone IDs.
//     */
//    @PostMapping("/queryAvailableDrones")
//    public ResponseEntity<List<String>> queryAvailableDrones(@RequestBody List<MedDispatchRec> medDispatchRec) {
//
//        List<String> availableDroneIds = droneQueryService.queryAvailableDrones(medDispatchRec);
//        return ResponseEntity.ok(availableDroneIds);
//    }
}
