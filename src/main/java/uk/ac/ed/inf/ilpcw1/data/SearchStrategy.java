package uk.ac.ed.inf.ilpcw1.data;

import lombok.Getter;

@Getter
public enum SearchStrategy {
    ASTAR("Optimal Ace"),       // f = g + h
    GREEDY("Gambler"),          // f = h (ignores cost so far)
    DIJKSTRA("Bob"),    // f = g (ignores heuristic)
    WEIGHTED_ASTAR("The Speedster"); // New Strategy

    private final String personalityName;

    SearchStrategy(String personalityName) {
        this.personalityName = personalityName;
    }

}