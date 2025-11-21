package uk.ac.ed.inf.ilpcw1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ed.inf.ilpcw1.data.*;
import uk.ac.ed.inf.ilpcw1.data.DroneRaceResult;
import uk.ac.ed.inf.ilpcw1.service.RacerService;
import uk.ac.ed.inf.ilpcw1.service.TrackGenerationService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RaceController.class)
@DisplayName("RaceController Integration Tests")
class RaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TrackGenerationService trackGenerationService;

    @MockitoBean
    private RacerService racerService;

    @Test
    @DisplayName("Should run a race with a user-defined (mocked) track")
    void testStartRace_WithMockedTrack() throws Exception {
        // 1. Setup Input Data (User "Clicks" Start on the Map)
        LngLat start = new LngLat(-3.188, 55.944); // George Square
        LngLat end = new LngLat(-3.192, 55.942);   // The Meadows
        RaceDataRequest requestBody = new RaceDataRequest(start, end);

        // 2. Define the "User Created" Track
        // Instead of random generation, we force the service to return this specific wall
        RestrictedArea userDefinedWall = RestrictedArea.builder()
                .name("User Test Wall")
                .vertices(List.of(
                        new LngLat(-3.190, 55.943),
                        new LngLat(-3.189, 55.943),
                        new LngLat(-3.189, 55.944),
                        new LngLat(-3.190, 55.944),
                        new LngLat(-3.190, 55.943)
                ))
                .build();
        List<RestrictedArea> mockTrack = List.of(userDefinedWall);

        // 3. Mock the Services
        // When controller asks for a track, give it our "User Track"
        when(trackGenerationService.generateTrack(any(LngLat.class), any(LngLat.class)))
                .thenReturn(mockTrack);

        // When controller asks racers to run, return a dummy result (we trust RacerService has its own tests)
        DroneRaceResult dummyResult = DroneRaceResult.builder()
                .algorithmName("Test Algo")
                .moveCount(10)
                .computationTimeMs(5)
                .color("#FFFFFF")
                .build();

        when(racerService.runRacer(any(), any(), anyList(), any(), anyString()))
                .thenReturn(dummyResult);

        // 4. Execute Request
        mockMvc.perform(post("/api/v1/race/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                // Verify the response structure contains the track we "created"
                .andExpect(jsonPath("$.raceId").exists())
                .andExpect(jsonPath("$.trackObstacles[0].name").value("User Test Wall"))
                .andExpect(jsonPath("$.droneResults").isArray())
                .andExpect(jsonPath("$.droneResults.length()").value(3)); // Should have 3 racers

        // 5. Verify Interactions
        // Ensure the controller actually passed our specific track to the racers
        verify(trackGenerationService).generateTrack(any(), any());
        verify(racerService, times(3)).runRacer(
                eq(start),
                eq(end),
                eq(mockTrack), // Critical: Must have passed our mock track!
                any(SearchStrategy.class),
                anyString()
        );
    }
}