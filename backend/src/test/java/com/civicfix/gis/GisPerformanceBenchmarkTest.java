package com.civicfix.gis;

import com.civicfix.common.util.GeoUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GIS Spatial Performance Empirical Benchmark")
class GisPerformanceBenchmarkTest {

    @Test
    void measureRayCastingAndDistancePerformance() {
        // GCC Zone polygon with 7 vertices (Anna Nagar)
        List<List<Double>> polygon = List.of(
                List.of(80.190, 13.070),
                List.of(80.235, 13.070),
                List.of(80.240, 13.095),
                List.of(80.220, 13.110),
                List.of(80.185, 13.105),
                List.of(80.180, 13.085),
                List.of(80.190, 13.070)
        );

        // State polygon with 41 vertices
        List<List<Double>> statePolygon = List.of(
                List.of(80.29, 13.48), List.of(80.33, 13.25), List.of(80.30, 13.08), List.of(80.25, 12.80),
                List.of(80.15, 12.50), List.of(79.88, 12.00), List.of(79.83, 11.75), List.of(79.77, 11.50),
                List.of(79.85, 11.15), List.of(79.85, 10.75), List.of(79.85, 10.30), List.of(79.35, 10.15),
                List.of(79.15, 9.85), List.of(79.05, 9.45), List.of(79.32, 9.28), List.of(79.15, 9.15),
                List.of(78.85, 9.10), List.of(78.50, 8.80), List.of(78.15, 8.50), List.of(77.75, 8.18),
                List.of(77.55, 8.08), List.of(77.30, 8.28), List.of(77.18, 8.50), List.of(77.25, 8.85),
                List.of(77.15, 9.25), List.of(77.10, 9.60), List.of(77.05, 10.05), List.of(76.85, 10.50),
                List.of(76.70, 10.85), List.of(76.50, 11.15), List.of(76.45, 11.55), List.of(76.65, 11.65),
                List.of(77.05, 11.85), List.of(77.25, 12.15), List.of(77.75, 12.45), List.of(78.25, 12.75),
                List.of(78.80, 12.85), List.of(79.15, 13.15), List.of(79.50, 13.35), List.of(79.95, 13.45),
                List.of(80.29, 13.48)
        );

        // Warm-up JIT
        for (int i = 0; i < 20000; i++) {
            GeoUtils.isPointInPolygon(13.085, 80.210, polygon);
            GeoUtils.isPointInPolygon(13.085, 80.210, statePolygon);
            GeoUtils.distanceToPolygonBoundaryMeters(13.085, 80.210, polygon);
        }

        // Benchmark 50,000 runs
        int iterations = 50000;
        long startZonePip = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            GeoUtils.isPointInPolygon(13.085, 80.210, polygon);
        }
        long durZonePipNanos = System.nanoTime() - startZonePip;
        double avgZonePipMicros = (durZonePipNanos / (double) iterations) / 1000.0;

        long startStatePip = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            GeoUtils.isPointInPolygon(13.085, 80.210, statePolygon);
        }
        long durStatePipNanos = System.nanoTime() - startStatePip;
        double avgStatePipMicros = (durStatePipNanos / (double) iterations) / 1000.0;

        long startDist = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            GeoUtils.distanceToPolygonBoundaryMeters(13.085, 80.210, polygon);
        }
        long durDistNanos = System.nanoTime() - startDist;
        double avgDistMicros = (durDistNanos / (double) iterations) / 1000.0;

        System.out.println("==================================================");
        System.out.println("EMPIRICAL BENCHMARK RESULTS (50,000 iterations):");
        System.out.printf("1. Zone PIP (7 vertices):  %.3f microseconds (%.5f ms)%n", avgZonePipMicros, avgZonePipMicros / 1000.0);
        System.out.printf("2. State PIP (41 vertices): %.3f microseconds (%.5f ms)%n", avgStatePipMicros, avgStatePipMicros / 1000.0);
        System.out.printf("3. Segment Geodesic Distance: %.3f microseconds (%.5f ms)%n", avgDistMicros, avgDistMicros / 1000.0);
        System.out.println("==================================================");

        assertTrue(avgZonePipMicros < 100.0, "Zone PIP should be fast");
    }
}
