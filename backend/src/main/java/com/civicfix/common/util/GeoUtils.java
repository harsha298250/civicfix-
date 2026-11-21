package com.civicfix.common.util;

import java.math.BigDecimal;

/**
 * Spatial and Geographic Utilities.
 * Pure static methods for Great-Circle geodesic distance calculations.
 */
public final class GeoUtils {

    public static final double EARTH_RADIUS_METERS = 6371000.0;

    private GeoUtils() {}

    /**
     * Compute Great-Circle Haversine distance between two coordinates in meters.
     *
     * @param lat1 Latitude of point 1 in degrees (-90 to 90)
     * @param lon1 Longitude of point 1 in degrees (-180 to 180)
     * @param lat2 Latitude of point 2 in degrees (-90 to 90)
     * @param lon2 Longitude of point 2 in degrees (-180 to 180)
     * @return Distance in meters
     */
    public static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Compute Great-Circle Haversine distance from BigDecimal coordinates.
     */
    public static double haversineDistance(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            throw new IllegalArgumentException("Coordinates must not be null");
        }
        return haversineDistance(lat1.doubleValue(), lon1.doubleValue(), lat2.doubleValue(), lon2.doubleValue());
    }

    /**
     * Validate latitude (-90 to 90) and longitude (-180 to 180).
     * Rejects NaN, infinite, out of bounds, and the exact null island (0, 0).
     */
    public static boolean isValidCoordinatePair(BigDecimal lat, BigDecimal lon) {
        if (lat == null || lon == null) {
            return false;
        }
        double dLat = lat.doubleValue();
        double dLon = lon.doubleValue();

        if (Double.isNaN(dLat) || Double.isInfinite(dLat) || Double.isNaN(dLon) || Double.isInfinite(dLon)) {
            return false;
        }
        if (dLat < -90.0 || dLat > 90.0 || dLon < -180.0 || dLon > 180.0) {
            return false;
        }
        // Exact null island (0.0, 0.0) rejected per requirement
        if (Double.compare(dLat, 0.0) == 0 && Double.compare(dLon, 0.0) == 0) {
            return false;
        }
        return true;
    }

    /**
     * Standard Ray-Casting Point-in-Polygon (PIP) containment algorithm.
     * Evaluates whether geographic point (lat, lng) lies strictly within the exterior ring.
     * GeoJSON coordinates format: [longitude, latitude].
     *
     * @param lat Query point latitude in degrees
     * @param lng Query point longitude in degrees
     * @param ring List of coordinate pairs [[lon, lat], [lon, lat], ...]
     * @return true if point is inside polygon ring, false otherwise
     */
    public static boolean isPointInPolygon(double lat, double lng, java.util.List<java.util.List<Double>> ring) {
        if (ring == null || ring.size() < 3) {
            return false;
        }
        boolean inside = false;
        int n = ring.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            java.util.List<Double> p1 = ring.get(i);
            java.util.List<Double> p2 = ring.get(j);
            if (p1 == null || p1.size() < 2 || p2 == null || p2.size() < 2) {
                continue;
            }
            double xi = p1.get(0); // lon
            double yi = p1.get(1); // lat
            double xj = p2.get(0); // lon
            double yj = p2.get(1); // lat

            boolean intersect = ((yi > lat) != (yj > lat))
                    && (lng < (xj - xi) * (lat - yi) / (yj - yi) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * Overload for double array coordinates [[lon, lat], ...].
     */
    public static boolean isPointInPolygon(double lat, double lng, double[][] ring) {
        if (ring == null || ring.length < 3) {
            return false;
        }
        boolean inside = false;
        int n = ring.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = ring[i][0]; // lon
            double yi = ring[i][1]; // lat
            double xj = ring[j][0]; // lon
            double yj = ring[j][1]; // lat

            boolean intersect = ((yi > lat) != (yj > lat))
                    && (lng < (xj - xi) * (lat - yi) / (yj - yi) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * Compute geodesic distance in meters from point (lat, lng) to the nearest segment
     * of a polygon exterior ring.
     */
    public static double distanceToPolygonBoundaryMeters(double lat, double lng, java.util.List<java.util.List<Double>> ring) {
        if (ring == null || ring.size() < 2) {
            return 0.0;
        }
        double minDistance = Double.MAX_VALUE;
        int n = ring.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            java.util.List<Double> p1 = ring.get(i);
            java.util.List<Double> p2 = ring.get(j);
            if (p1 == null || p1.size() < 2 || p2 == null || p2.size() < 2) {
                continue;
            }
            double segDist = distanceToSegmentMeters(lat, lng, p1.get(1), p1.get(0), p2.get(1), p2.get(0));
            if (segDist < minDistance) {
                minDistance = segDist;
            }
        }
        return minDistance == Double.MAX_VALUE ? 0.0 : minDistance;
    }

    /**
     * Compute geodesic distance in meters from point P to line segment AB.
     */
    public static double distanceToSegmentMeters(double pLat, double pLon, double aLat, double aLon, double bLat, double bLon) {
        double meanLat = Math.toRadians((aLat + bLat) / 2.0);
        double cosLat = Math.cos(meanLat);

        double ax = aLon * cosLat;
        double ay = aLat;
        double bx = bLon * cosLat;
        double by = bLat;
        double px = pLon * cosLat;
        double py = pLat;

        double dx = bx - ax;
        double dy = by - ay;
        double lenSq = dx * dx + dy * dy;

        double t = 0.0;
        if (lenSq > 0.0) {
            t = ((px - ax) * dx + (py - ay) * dy) / lenSq;
            if (t < 0.0) {
                t = 0.0;
            } else if (t > 1.0) {
                t = 1.0;
            }
        }

        double projLat = aLat + t * (bLat - aLat);
        double projLon = aLon + t * (bLon - aLon);
        return haversineDistance(pLat, pLon, projLat, projLon);
    }
}

