package org.example.lifecomposer.recommendation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads the first-batch direction catalog from a versioned classpath resource. */
@Service
public class GrowthDirectionCatalog {

    private static final String RESOURCE = "recommendation/directions.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, GrowthDirection> directions = new LinkedHashMap<>();

    @PostConstruct
    public void load() {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.exists()) {
            throw new IllegalStateException("Missing direction catalog: " + RESOURCE);
        }
        try (InputStream input = resource.getInputStream()) {
            List<GrowthDirection> parsed = MAPPER.readValue(input, new TypeReference<>() {
            });
            for (GrowthDirection direction : parsed) {
                if (direction.getId() == null || direction.getId().isBlank()) {
                    throw new IllegalStateException("Direction without id in " + RESOURCE);
                }
                directions.put(direction.getId(), direction);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load direction catalog: " + RESOURCE, e);
        }
        if (directions.isEmpty()) {
            throw new IllegalStateException("Direction catalog is empty: " + RESOURCE);
        }
    }

    public List<GrowthDirection> all() {
        return List.copyOf(directions.values());
    }

    public GrowthDirection byId(String id) {
        return directions.get(id);
    }
}
