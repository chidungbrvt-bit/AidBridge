package com.aidbridge.casefile.service;

import com.aidbridge.casefile.dto.response.EvidenceFixtureResponse;
import com.aidbridge.shared.exception.BusinessException;
import com.aidbridge.shared.util.Hashing;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aidbridge.evidence")
public class EvidenceFixtureCatalog {
    private Map<UUID, List<String>> organizationFixtures = new HashMap<>();
    private final Map<String, Fixture> fixtures;

    public EvidenceFixtureCatalog() {
        Map<String, Fixture> loaded = new LinkedHashMap<>();
        for (String key : List.of("household-note-v1", "household-note-v2", "consent-note-v1")) {
            try {
                byte[] bytes = new ClassPathResource("private-evidence/" + key + ".txt").getContentAsByteArray();
                loaded.put(key, new Fixture(key, "Simulated supporting document", bytes, sha256(bytes)));
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Private evidence fixture is unavailable");
            }
        }
        fixtures = Collections.unmodifiableMap(loaded);
    }
    public Map<UUID, List<String>> getOrganizationFixtures() { return organizationFixtures; }
    public void setOrganizationFixtures(Map<UUID, List<String>> grants) {
        if (grants.values().stream().flatMap(Collection::stream).anyMatch(k -> !fixtures.containsKey(k)))
            throw new IllegalArgumentException("Unknown server evidence fixture grant");
        Map<UUID,List<String>> copy = new HashMap<>();
        grants.forEach((org, keys) -> copy.put(org,List.copyOf(keys)));
        organizationFixtures = Map.copyOf(copy);
    }

    public List<EvidenceFixtureResponse> list(UUID organizationId) {
        return organizationFixtures.getOrDefault(organizationId, List.of()).stream().distinct()
                .filter(fixtures::containsKey).sorted().map(key -> resolve(organizationId, key).metadata()).toList();
    }
    public Fixture resolve(UUID organizationId, String key) {
        if (!organizationFixtures.getOrDefault(organizationId, List.of()).contains(key)
                || !fixtures.containsKey(key)) throw BusinessException.missing();
        return fixtures.get(key);
    }
    public static String sha256(byte[] bytes) {
        return Hashing.sha256(bytes);
    }
    public record Fixture(String key, String title, byte[] bytes, String hash) {
        public Fixture { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
        public EvidenceFixtureResponse metadata() {
            return new EvidenceFixtureResponse(key, title, "text/plain", bytes.length, hash);
        }
    }
}
