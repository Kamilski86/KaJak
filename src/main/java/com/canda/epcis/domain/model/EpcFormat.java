package com.canda.epcis.domain.model;

/**
 * Erlaubte und verbotene EPC Pure Identity URI Formate.
 *
 * REQ101.1: epcList, parentId, childEPCs dürfen nur SGTIN und SSCC enthalten.
 * REQ216:   GID, USDoD, ADI, BIC sind explizit verboten und müssen gefiltert werden.
 *
 * Filterlogik:
 * - isForbidden()          → immer entfernen + loggen (REQ216)
 * - isAllowedInEpcList()   → SGTIN und SSCC erlaubt
 * - isValidParentId()      → nur SSCC erlaubt (REQ216 Punkt 2)
 * - isValidSsccStructure() → SSCC SerialReference muss rein numerisch sein, 17 Stellen gesamt
 *                            (GS1 EPC TDS 2.0, Abschnitt 6.3.2)
 */
public enum EpcFormat {
    SGTIN("urn:epc:id:sgtin:"),
    SSCC("urn:epc:id:sscc:"),
    SGLN("urn:epc:id:sgln:"),
    GRAI("urn:epc:id:grai:"),
    GIAI("urn:epc:id:giai:");

    private final String prefix;

    EpcFormat(String prefix) { this.prefix = prefix; }

    public String getPrefix() { return prefix; }

    public static boolean isAllowedInEpcList(String epc) {
        // REQ101.1: nur SGTIN und SSCC
        if (epc == null || epc.isBlank()) return false;
        return epc.startsWith("urn:epc:id:sgtin:")
            || epc.startsWith("urn:epc:id:sscc:");
    }

    public static boolean isValidParentId(String epc) {
        // REQ216 Punkt 2: parentID muss SSCC sein
        if (epc == null || epc.isBlank()) return false;
        return epc.startsWith("urn:epc:id:sscc:");
    }

    public static boolean isForbidden(String epc) {
        // REQ216 Punkt 1: GID, USDoD, ADI, BIC explizit verboten
        if (epc == null) return false;
        return epc.startsWith("urn:epc:id:gid:")
            || epc.startsWith("urn:epc:id:usdod:")
            || epc.startsWith("urn:epc:id:adi:")
            || epc.startsWith("urn:epc:id:bic:");
    }

    /**
     * Validiert die Struktur eines SSCC nach GS1 EPC TDS 2.0, Abschnitt 6.3.2.
     *
     * Regeln:
     * - Format: urn:epc:id:sscc:{CompanyPrefix}.{SerialReference}
     * - CompanyPrefix und SerialReference müssen ausschließlich Ziffern [0-9] enthalten.
     * - CompanyPrefix.length() + SerialReference.length() muss exakt 17 ergeben.
     *
     * Gibt false zurück wenn der EPC kein SSCC-Präfix hat.
     */
    public static boolean isValidSsccStructure(String epc) {
        if (epc == null || !epc.startsWith("urn:epc:id:sscc:")) return false;
        String body = epc.substring("urn:epc:id:sscc:".length());
        String[] parts = body.split("\\.", -1);
        if (parts.length != 2) return false;
        String company = parts[0];
        String serialRef = parts[1];
        if (!company.matches("[0-9]+") || !serialRef.matches("[0-9]+")) return false;
        return company.length() + serialRef.length() == 17;
    }

    public static String detectFormat(String epc) {
        if (epc == null) return "NULL";
        for (EpcFormat f : values()) {
            if (epc.startsWith(f.prefix)) return f.name();
        }
        if (isForbidden(epc)) return "FORBIDDEN";
        return "UNKNOWN";
    }
}
