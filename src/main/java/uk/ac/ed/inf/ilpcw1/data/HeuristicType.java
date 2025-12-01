package uk.ac.ed.inf.ilpcw1.data;

public enum HeuristicType {
    EUCLIDEAN,  // Standard (Math.sqrt(dx^2 + dy^2))
    MANHATTAN,  //  (abs(dx) + abs(dy))
    CHEBYSHEV,   // (max(abs(dx), abs(dy)))
    OCTILE      // (max(abs(dx), abs(dy)) + (sqrt(2)-1)*min(abs(dx), abs(dy)))
}