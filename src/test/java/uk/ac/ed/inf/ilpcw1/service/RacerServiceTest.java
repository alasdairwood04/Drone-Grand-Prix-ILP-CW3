package uk.ac.ed.inf.ilpcw1.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.ed.inf.ilpcw1.data.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RacerService Unit Tests")
class RacerServiceTest {

    @Mock
    private PathfindingService pathfindingService;

    @Mock
    private TrackGenerationService trackGenerationService;

    // We mock this even if unused in startRace to satisfy the constructor
    @Mock
    private ILPServiceClient ilpServiceClient;

    @InjectMocks
    private RacerService racerService;

    private LngLat start;
    private LngLat end;
    private List<RestrictedArea> obstacles;

    @BeforeEach
    void setUp() {
        start = new LngLat(-3.188, 55.944); // George Square
        end = new LngLat(-3.192, 55.942);   // The Meadows

        // Sample obstacles
        RestrictedArea wall1 = RestrictedArea.builder()
                .name("Wall 1")
                .vertices(List.of(
                        new LngLat(-3.190, 55.943),
                        new LngLat(-3.189, 55.943),
                        new LngLat(-3.189, 55.944),
                        new LngLat(-3.190, 55.944),
                        new LngLat(-3.190, 55.943)
                ))
                .build();

        obstacles = List.of(wall1);
    }

    @Test
    @DisplayName("startRace should coordinate track generation and run 3 racers")
    void testStartRace_HappyPath() {
        // 1. Arrange
        RaceDataRequest request = new RaceDataRequest(start, end, "obstacles");

        // 2. Act
        RaceDataResponse response = racerService.startRace(request);

        // 3. Assert
        assertNotNull(response);
        assertNotNull(response.getRaceId());
        assertEquals(start, response.getStartLocation());
        assertEquals(end, response.getEndLocation());
        assertEquals(obstacles, response.getTrackObstacles());

        // Verify 3 results were generated (A*, Greedy, Dijkstra)
        List<DroneRaceResult> results = response.getDroneResults();
        assertEquals(3, results.size(), "Should run A*, Greedy, and Dijkstra");

        // Check if specific strategies were called
        verify(pathfindingService).findPath(start, end, obstacles, SearchStrategy.ASTAR);
        verify(pathfindingService).findPath(start, end, obstacles, SearchStrategy.GREEDY);
        verify(pathfindingService).findPath(start, end, obstacles, SearchStrategy.DIJKSTRA);

        // Verify result contents for one of them
        DroneRaceResult firstResult = results.get(0);
        assertNotNull(firstResult.getAlgorithmName());
        assertNotNull(firstResult.getColor());
        assertEquals(3, firstResult.getMoveCount()); // Based on mockPath size
    }
}