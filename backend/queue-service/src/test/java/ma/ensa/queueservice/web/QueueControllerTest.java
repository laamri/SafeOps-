package ma.ensa.queueservice.web;

import ma.ensa.queueservice.domain.QueueService;
import ma.ensa.queueservice.security.CurrentUserResolver;
import ma.ensa.queueservice.web.Views.CounterView;
import ma.ensa.queueservice.web.Views.QueueView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QueueControllerTest {

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
    void staffCloseQueueThroughStatusEndpoint() throws Exception {
        when(service.setOpen(any(), eq(1L), eq(false))).thenReturn(new CounterView(
                new QueueView(1L, "Dépôt de dossier", "A", "Guichet 3", 0, 0, false), null, List.of()));

        mockMvc.perform(put("/api/queues/1/status")
                        .header("X-Auth-Subject", "agent-1")
                        .header("X-Auth-Roles", "agent")
                        .contentType("application/json")
                        .content("{\"open\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queue.open").value(false));

        verify(service).setOpen(any(), eq(1L), eq(false));
    }

    @Test
    void statusRequestRequiresTheOpenFlag() throws Exception {
        mockMvc.perform(put("/api/queues/1/status")
                        .header("X-Auth-Subject", "agent-1")
                        .header("X-Auth-Roles", "agent")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
