package com.canda.epcis.api.capture;

import com.canda.epcis.application.capture.CaptureEventUseCase;
import com.canda.epcis.domain.model.CaptureResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CaptureControllerTest {

    @Mock
    CaptureEventUseCase captureEventUseCase;

    MockMvc mockMvc;

    private static final String DUMMY_XML = "<root/>";

    @BeforeEach
    void setUp() {
        CaptureController controller = new CaptureController(captureEventUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void capture_allEventsAccepted_returns201() throws Exception {
        when(captureEventUseCase.capture(any(), any())).thenReturn(
                CaptureResult.builder()
                        .sessionId("s1")
                        .totalReceived(1).totalAccepted(1)
                        .captureIds(List.of("urn:uuid:abc"))
                        .errors(List.of())
                        .build());

        mockMvc.perform(post("/epcis/capture/events")
                        .contentType(MediaType.APPLICATION_XML)
                        .header("X-EPCIS-Source-ID", "STORE-DE-001")
                        .content(DUMMY_XML))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAccepted").value(1));
    }

    @Test
    void capture_allEventsRejected_returns422() throws Exception {
        when(captureEventUseCase.capture(any(), any())).thenReturn(
                CaptureResult.builder()
                        .sessionId("s2")
                        .totalReceived(1).totalAccepted(0)
                        .captureIds(List.of())
                        .errors(List.of(CaptureResult.CaptureError.builder()
                                .eventId(null)
                                .errorCode("CBV_VIOLATION")
                                .message("Invalid bizStep")
                                .build()))
                        .build());

        mockMvc.perform(post("/epcis/capture/events")
                        .contentType(MediaType.APPLICATION_XML)
                        .header("X-EPCIS-Source-ID", "STORE-DE-001")
                        .content(DUMMY_XML))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.totalAccepted").value(0))
                .andExpect(jsonPath("$.errors[0].errorCode").value("CBV_VIOLATION"));
    }

    @Test
    void capture_partialSuccess_returns207() throws Exception {
        when(captureEventUseCase.capture(any(), any())).thenReturn(
                CaptureResult.builder()
                        .sessionId("s3")
                        .totalReceived(2).totalAccepted(1)
                        .captureIds(List.of("urn:uuid:good"))
                        .errors(List.of(CaptureResult.CaptureError.builder()
                                .eventId("urn:uuid:bad")
                                .errorCode("CBV_VIOLATION")
                                .message("Invalid disposition")
                                .build()))
                        .build());

        mockMvc.perform(post("/epcis/capture/events")
                        .contentType(MediaType.APPLICATION_XML)
                        .header("X-EPCIS-Source-ID", "STORE-DE-001")
                        .content(DUMMY_XML))
                .andExpect(status().is(207))
                .andExpect(jsonPath("$.totalAccepted").value(1))
                .andExpect(jsonPath("$.totalReceived").value(2));
    }
}
