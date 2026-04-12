package com.canda.epcis.domain.model;

/**
 * Erlaubte und verbotene EPC Pure Identity URI Formate.
 *
 * REQ101.1: epcList, parentId, childEPCs dürfen nur SGTIN und SSCC enthalten.
 * REQ216:   GID, USDoD, ADI, BIC sind explizit verboten und müssen gefiltert werden.
 *
 * Filterlogik:
 * - isForbidden()        → immer entfernen + loggen (REQ216)
 * - isAllowedInEpcList() → SGTIN und SSCC erlaubt
 * - isValidParentId()    → nur SSCC erlaubt (REQ216 Punkt 2)
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

    public static String detectFormat(String epc) {
        if (epc == null) return "NULL";
        for (EpcFormat f : values()) {
            if (epc.startsWith(f.prefix)) return f.name();
        }
        if (isForbidden(epc)) return "FORBIDDEN";
        return "UNKNOWN";
    }
}
