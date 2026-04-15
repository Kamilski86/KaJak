package com.canda.epcis.domain.model.cbv;

import java.util.Optional;

/**
 * Die vier Standard-Vokabular-Typen des GS1 CBV 2.0.
 * Jeder Typ hat ein eigenes URI-Präfix und eine eigene Werteliste.
 *
 * Reference: GS1 Core Business Vocabulary Standard, Release 2.0, Jun 2022.
 */
public enum CbvVocabularyType {

    BIZ_STEP(
        "urn:epcglobal:cbv:bizstep:",
        "https://ref.gs1.org/cbv/BizStep-"
    ),
    DISPOSITION(
        "urn:epcglobal:cbv:disp:",
        "https://ref.gs1.org/cbv/Disp-"
    ),
    BIZ_TRANSACTION_TYPE(
        "urn:epcglobal:cbv:btt:",
        "https://ref.gs1.org/cbv/BTT-"
    ),
    SOURCE_DEST_TYPE(
        "urn:epcglobal:cbv:sdt:",
        "https://ref.gs1.org/cbv/SDT-"
    );

    private final String urnPrefix;
    private final String httpsPrefix;

    CbvVocabularyType(String urnPrefix, String httpsPrefix) {
        this.urnPrefix = urnPrefix;
        this.httpsPrefix = httpsPrefix;
    }

    public String getUrnPrefix() { return urnPrefix; }
    public String getHttpsPrefix() { return httpsPrefix; }

    /**
     * Extracts the payload value from a CBV URI.
     * Example: "urn:epcglobal:cbv:bizstep:shipping" → "shipping"
     * Example: "https://ref.gs1.org/cbv/BizStep-shipping" → "shipping"
     *
     * Returns empty if the URI does not match either prefix of this type.
     */
    public Optional<String> extractPayload(String uri) {
        if (uri == null) return Optional.empty();
        if (uri.startsWith(urnPrefix))
            return Optional.of(uri.substring(urnPrefix.length()));
        if (uri.startsWith(httpsPrefix))
            return Optional.of(uri.substring(httpsPrefix.length()));
        return Optional.empty();
    }

    /**
     * Returns true if the URI starts with either prefix of this vocabulary type.
     * Used to decide whether validation is applicable (non-CBV URIs are allowed).
     */
    public boolean isCbvUri(String uri) {
        if (uri == null) return false;
        return uri.startsWith(urnPrefix) || uri.startsWith(httpsPrefix);
    }
}
