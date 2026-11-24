package com.civicfix.ai;

import com.civicfix.ai.dto.AiTriageResult;
import com.civicfix.ai.dto.DuplicateCandidate;
import com.civicfix.ai.dto.SuggestCategoryResponse;
import com.civicfix.ai.duplicate.DuplicateDetectionEngine;
import com.civicfix.ai.summary.ComplaintSummaryResponse;
import com.civicfix.ai.summary.ComplaintSummaryService;
import com.civicfix.ai.triage.DeterministicTriageEngine;
import com.civicfix.complaint.Complaint;
import com.civicfix.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * High-level AI triage service coordinating deterministic triage rules,
 * proximity-based duplicate detection, and structured complaint summarization.
 * 
 * Safety Mandate: All evaluations produced by this service are advisory.
 * Official complaint category, priority, and status are never mutated without
 * explicit authorized officer action.
 */
@Service
public class AiTriageService {

    private static final Logger log = LoggerFactory.getLogger(AiTriageService.class);

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");
    private static final int MAX_SUGGESTION_TEXT_LENGTH = 1000;

    private final DeterministicTriageEngine triageEngine;
    private final DuplicateDetectionEngine duplicateEngine;
    private final ComplaintSummaryService summaryService;

    public AiTriageService(DeterministicTriageEngine triageEngine, DuplicateDetectionEngine duplicateEngine) {
        this(triageEngine, duplicateEngine, new ComplaintSummaryService());
    }

    @Autowired
    public AiTriageService(
            DeterministicTriageEngine triageEngine,
            DuplicateDetectionEngine duplicateEngine,
            @Autowired(required = false) ComplaintSummaryService summaryService) {
        this.triageEngine = triageEngine;
        this.duplicateEngine = duplicateEngine;
        this.summaryService = summaryService != null ? summaryService : new ComplaintSummaryService();
    }

    /**
     * Evaluate a complaint entity and populate its advisory triage fields.
     * Guaranteed non-blocking and safe against unexpected exceptions.
     */
    public AiTriageResult triageComplaint(Complaint complaint) {
        if (complaint == null) {
            return AiTriageResult.fallback("GENERAL", null, "Null complaint provided.");
        }

        try {
            // 1. Evaluate category and hazard rules
            AiTriageResult result = triageEngine.evaluate(
                    complaint.getTitle(),
                    complaint.getDescription(),
                    complaint.getCategory(),
                    complaint.getPriority()
            );

            // 2. Identify advisory duplicate candidates (non-destructive)
            List<DuplicateCandidate> duplicates = duplicateEngine.findCandidates(
                    complaint.getId(),
                    complaint.getLatitude(),
                    complaint.getLongitude(),
                    complaint.getWard(),
                    complaint.getCategory(),
                    complaint.getTitle(),
                    complaint.getDescription()
            );
            result.setDuplicateCandidates(duplicates);

            // 3. Populate advisory entity fields without altering official priority/category
            complaint.setAiDetectedCategory(result.getDetectedCategory());
            complaint.setAiRecommendedPriority(result.getRecommendedPriority());
            complaint.setAiConfidence(result.getConfidence());
            complaint.setAiHazardScore(result.getHazardScore());
            complaint.setAiExplanation(result.getExplanation());
            complaint.setAiTriagedAt(result.getTriagedAt() != null ? result.getTriagedAt() : OffsetDateTime.now());
            if (complaint.getAiAdjudicationStatus() == null || complaint.getAiAdjudicationStatus().isBlank()) {
                complaint.setAiAdjudicationStatus("PENDING");
            }

            return result;
        } catch (Exception e) {
            log.error("Triage evaluation encountered unexpected error for complaint: {}", complaint.getReferenceId(), e);
            AiTriageResult fallback = AiTriageResult.fallback(
                    complaint.getCategory(),
                    complaint.getPriority(),
                    "Triage execution bypassed due to internal processing error: " + e.getMessage()
            );
            complaint.setAiDetectedCategory(fallback.getDetectedCategory());
            complaint.setAiRecommendedPriority(fallback.getRecommendedPriority());
            complaint.setAiConfidence(fallback.getConfidence());
            complaint.setAiHazardScore(fallback.getHazardScore());
            complaint.setAiExplanation(fallback.getExplanation());
            complaint.setAiTriagedAt(OffsetDateTime.now());
            complaint.setAiAdjudicationStatus("PENDING");
            return fallback;
        }
    }

    /**
     * Retrieve ranked duplicate candidate recommendations for a given complaint.
     */
    public List<DuplicateCandidate> findDuplicateCandidates(Complaint complaint) {
        if (complaint == null) {
            return Collections.emptyList();
        }
        return duplicateEngine.findCandidates(
                complaint.getId(),
                complaint.getLatitude(),
                complaint.getLongitude(),
                complaint.getWard(),
                complaint.getCategory(),
                complaint.getTitle(),
                complaint.getDescription()
        );
    }

    /**
     * Fast live category suggestion based on title/description text input.
     * Non-destructive and purely advisory for the citizen report intake form.
     * Sanitizes inputs, clamps input length to 1000 characters, and computes alternatives.
     */
    public SuggestCategoryResponse suggestCategory(String text, String language) {
        if (text == null || text.isBlank()) {
            return new SuggestCategoryResponse("ROADS", 50.0, "No text provided for evaluation.", List.of(), Collections.emptyList(), false);
        }

        // 1. Input sanitization & clamping
        String sanitized = CONTROL_CHARS.matcher(text.trim()).replaceAll("");
        if (sanitized.length() > MAX_SUGGESTION_TEXT_LENGTH) {
            sanitized = sanitized.substring(0, MAX_SUGGESTION_TEXT_LENGTH);
        }

        if (sanitized.isBlank()) {
            return new SuggestCategoryResponse("ROADS", 50.0, "Input contained only whitespace or control characters.", List.of(), Collections.emptyList(), false);
        }

        AiTriageResult result = triageEngine.evaluate(sanitized, "", null, null);
        double conf = result.getConfidence() != null ? result.getConfidence().doubleValue() : 50.0;
        boolean isConfident = conf >= 65.0 && result.getRuleMatches() != null && !result.getRuleMatches().isEmpty();

        List<String> alternatives = new ArrayList<>();
        // Extract alternate categories if present in rule matches
        if (result.getRuleMatches() != null) {
            for (String match : result.getRuleMatches()) {
                if (match != null && match.startsWith("Category:") && !match.contains(result.getDetectedCategory())) {
                    String[] parts = match.split(":");
                    if (parts.length > 1 && !alternatives.contains(parts[1].trim())) {
                        alternatives.add(parts[1].trim());
                    }
                }
            }
        }

        return new SuggestCategoryResponse(
                result.getDetectedCategory(),
                conf,
                result.getExplanation(),
                result.getRuleMatches(),
                alternatives,
                isConfident
        );
    }

    /**
     * Generate a structured executive summary for municipal staff.
     */
    public ComplaintSummaryResponse generateSummary(Complaint complaint, UserPrincipal principal) {
        return summaryService.generateSummary(complaint, principal);
    }
}
