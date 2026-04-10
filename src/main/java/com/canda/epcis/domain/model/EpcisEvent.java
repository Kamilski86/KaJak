package com.canda.epcis.domain.model;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Base class for all canonical EPCIS event types.
 * Covers the EPCIS dimensions: When, Where, Why, How.
 * What (EPCs / quantities) is event-type specific and defined in subclasses.
 */
@Getter
@SuperBuilder
public abstract class EpcisEvent {

    public abstract String getEventTypeName();

    /** Business-assigned event identifier. Never generated synthetically. */
    private String eventId;

    /** Mandatory: timestamp of the event (EPCIS dimension: When). */
    private OffsetDateTime eventTime;

    /** Timezone offset string, e.g. "+02:00". */
    private String eventTimeZoneOffset;

    /** Optional: timestamp when the event was recorded by the repository. */
    private OffsetDateTime recordTime;

    /** Business step URI from CBV, e.g. "urn:epcglobal:cbv:bizstep:shipping". */
    private String bizStep;

    /** Disposition URI from CBV, e.g. "urn:epcglobal:cbv:disp:in_transit". */
    private String disposition;

    /** Read point location URI (EPCIS dimension: Where). */
    private String readPoint;

    /** Business location URI (EPCIS dimension: Where). */
    private String bizLocation;

    /** Business transactions associated with this event (EPCIS dimension: Why). */
    private List<BusinessTransaction> bizTransactionList;

    /** Source parties or locations (EPCIS dimension: Why). */
    private List<Source> sourceList;

    /** Destination parties or locations (EPCIS dimension: Why). */
    private List<Destination> destinationList;

    /** Present when this event corrects a previously reported erroneous event. */
    private ErrorDeclaration errorDeclaration;

    /** Custom extension elements attached to this event body. */
    private ExtensionPayload extension;

    private Action action;
}
