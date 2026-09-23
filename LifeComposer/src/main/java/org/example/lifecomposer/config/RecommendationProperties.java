package org.example.lifecomposer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * v0.1 M5: experimental scoring configuration. Weights and thresholds live here
 * (and in recommendation/directions.json) so the algorithm can be re-versioned
 * without rewriting the scoring flow.
 */
@Configuration
@ConfigurationProperties(prefix = "lifecomposer.recommendation")
@Getter
@Setter
public class RecommendationProperties {

    /** Configuration version recorded on every recommendation result. */
    private String scoringVersion = "experimental-v1";

    private double skillWeight = 0.40;
    private double timeWeight = 0.20;
    private double goalWeight = 0.20;
    private double difficultyWeight = 0.10;
    private double preparationWeight = 0.10;

    /** total >= suitableThreshold => SUITABLE. */
    private double suitableThreshold = 0.70;
    /** total >= partialThreshold => PARTIALLY_SUITABLE, otherwise NOT_RECOMMENDED. */
    private double partialThreshold = 0.45;
}
