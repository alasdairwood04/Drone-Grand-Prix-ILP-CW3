package uk.ac.ed.inf.ilpcw1.data;

public enum HeuristicType {
    EUCLIDEAN,  // Standard "As the crow flies" (Math.sqrt(dx^2 + dy^2))
    MANHATTAN,  // "City Block" style (abs(dx) + abs(dy))
    CHEBYSHEV,   // "Chessboard King" style (max(abs(dx), abs(dy)))
    OCTILE      // "Diagonal Movement" style (max(abs(dx), abs(dy)) + (sqrt(2)-1)*min(abs(dx), abs(dy)))
}