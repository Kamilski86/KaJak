package com.example.epcis.api;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

@Getter
@Setter
public class EventSearchCriteria {
    private String type;
    private String action;
    private String bizStep;
    private String disposition;
    private String readPoint;
    private String bizLocation;
    private String parentId;
    private String epc;
    private String gln;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime eventTimeFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime eventTimeTo;
}
