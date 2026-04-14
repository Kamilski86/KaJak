package com.canda.epcis.infrastructure.persistence.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SsccContentRepository extends JpaRepository<SsccContentEntity, Long> {

    /** Alle SGTINs die einer bestimmten SSCC zugeordnet sind. */
    List<SsccContentEntity> findBySsccUrn(String ssccUrn);

    /** Alle SSCCs denen eine bestimmte SGTIN zugeordnet ist (für REQ210: Eindeutigkeit). */
    List<SsccContentEntity> findByChildEpc(String childEpc);

    /** Entfernt bestimmte SGTINs aus einer SSCC (partielles DELETE). */
    void deleteBySsccUrnAndChildEpcIn(String ssccUrn, List<String> childEpcs);

    /** Entfernt alle SGTINs einer SSCC (REQ215: DELETE mit leeren childEPCs). */
    void deleteBySsccUrn(String ssccUrn);

    boolean existsBySsccUrnAndChildEpc(String ssccUrn, String childEpc);

    long countBySsccUrn(String ssccUrn);
}
