package com.canda.epcis.domain.model;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * Canonical ObjectEvent — describes an action on one or more EPCs or quantities.
 * Covers EPCIS dimension: What (instance-level via epcList, class-level via quantityList).
 */
@Getter
@SuperBuilder
public class ObjectEvent extends EpcisEvent {

    @Override
    public String getEventTypeName() {
        return "ObjectEvent";
    }

    /** Instance-level EPC identifiers, e.g. "urn:epc:id:sgtin:0614141.107346.2017". */
    private List<String> epcList;

    /** Class-level quantity elements for products without a serial EPC. */
    private List<QuantityElement> quantityList;

    /**
     * Instance/Lot Master Data attached to this event.
     * Only ObjectEvent carries ILMD in EPCIS 1.2 and 2.0.
     */
    private IlmdPayload ilmd;
}
