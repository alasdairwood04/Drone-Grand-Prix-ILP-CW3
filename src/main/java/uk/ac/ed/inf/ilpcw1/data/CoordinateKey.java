package uk.ac.ed.inf.ilpcw1.data;

/**
 * CoordinateKey class to represent a coordinate key with longitude and latitude.
 * The longitude and latitude are stored as long integers after scaling to preserve precision.
 */

public record CoordinateKey(long lng, long lat) {
    private static final double PRECISION_SCALE = 10000.0; // 1e4

    public static CoordinateKey fromLngLat(LngLat pos) {
        return new CoordinateKey(
                Math.round(pos.getLongitude() * PRECISION_SCALE),
                Math.round(pos.getLatitude() * PRECISION_SCALE)
        );
    }
}