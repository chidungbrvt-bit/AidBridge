package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.*;
import com.aidbridge.casefile.service.EvidenceFixtureCatalog;
import com.aidbridge.shared.exception.BusinessException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class EvidenceFixtureCatalogTest {
    @Test void serverConfigurationBindsOrganizationUuidToOnlyItsAllowedKeys() {
        UUID org=UUID.randomUUID();var catalog=new EvidenceFixtureCatalog();
        var source=new MapConfigurationPropertySource(
                Map.of("aidbridge.evidence.organization-fixtures["+org+"]","household-note-v1,consent-note-v1"));
        new Binder(source).bind("aidbridge.evidence",Bindable.ofInstance(catalog));
        assertThat(catalog.list(org)).hasSize(2);
        assertThat(catalog.list(UUID.randomUUID())).isEmpty();
    }
    @Test void unknownOrganizationAndTraversalAreDeniedWithoutReadingCallerPaths() {
        var catalog=new EvidenceFixtureCatalog();UUID org=UUID.randomUUID();
        assertThat(catalog.list(org)).isEmpty();
        catalog.setOrganizationFixtures(Map.of(org,List.of("household-note-v1")));
        for(String key:List.of("../application.properties","https://example.com","household-note-v2"))
            assertThatThrownBy(()->catalog.resolve(org,key)).isInstanceOf(BusinessException.class);
    }
    @Test void hashAndSizeComeFromRealFixtureBytesAndVersionsRemainDistinct() {
        var catalog=new EvidenceFixtureCatalog();UUID org=UUID.randomUUID();
        catalog.setOrganizationFixtures(Map.of(org,List.of("household-note-v1","household-note-v2")));
        var first=catalog.resolve(org,"household-note-v1");var second=catalog.resolve(org,"household-note-v2");
        assertThat(first.hash()).isEqualTo(EvidenceFixtureCatalog.sha256(first.bytes())).isNotEqualTo(second.hash());
        assertThat(first.metadata().byteSize()).isEqualTo(first.bytes().length);
    }
    @Test void returnedByteArraysCannotMutateStoredFixtureHistory() {
        var catalog=new EvidenceFixtureCatalog();UUID org=UUID.randomUUID();
        catalog.setOrganizationFixtures(Map.of(org,List.of("household-note-v1")));
        var fixture=catalog.resolve(org,"household-note-v1");byte[] original=fixture.bytes();fixture.bytes()[0]=0;
        assertThat(fixture.bytes()).isEqualTo(original);
    }
}
