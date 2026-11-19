package ma.ensa.queueservice.web;

import ma.ensa.queueservice.domain.QueueService;
import ma.ensa.queueservice.domain.TicketStatus;
import ma.ensa.queueservice.domain.TicketTransitionException;
import ma.ensa.queueservice.security.CurrentUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QueueApiExceptionHandlerTest {

    private QueueService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(QueueService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new QueueController(service))
                .setCustomArgumentResolvers(new CurrentUserResolver())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void illegalTransitionIsAConflictWithAStableErrorBody() throws Exception {
        UUID ticketId = UUID.fromString("0f8e0e9c-4d1d-4f8e-9e4a-6f4b2c1d7a10");
        when(service.serve(any(), any())).thenThrow(new TicketTransitionException("A-006",
                TicketStatus.WAITING, TicketStatus.SERVED));

        mockMvc.perform(post("/api/tickets/{id}/served", ticketId)
                        .header("X-Auth-Subject", "agent-1")
                        .header("X-Auth-Roles", "agent"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                // same rendering as the appointment-service ApiError
                .andExpect(jsonPath("$.error").value("409 CONFLICT"))
                .andExpect(jsonPath("$.message").value("ticket A-006 cannot move from WAITING to SERVED"))
                .andExpect(jsonPath("$.path").value("/api/tickets/" + ticketId + "/served"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
