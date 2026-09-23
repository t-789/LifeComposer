package org.example.lifecomposer.recommendation;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** A first-batch growth direction loaded from recommendation/directions.json. */
@Getter
@Setter
public class GrowthDirection {
    private String id;
    private String name;
    private String description;
    private List<String> targetTags = new ArrayList<>();
    private List<String> entryTags = new ArrayList<>();
    private int requiredHoursPerWeek;
    private String difficulty;
    private int preparationMonths;
    private List<String> keywords = new ArrayList<>();
    private List<String> resourceIds = new ArrayList<>();
}
