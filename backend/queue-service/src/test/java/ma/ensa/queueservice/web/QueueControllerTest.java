package ma.ensa.queueservice.web;

import ma.ensa.queueservice.domain.QueueService;
import ma.ensa.queueservice.security.CurrentUserResolver;
import ma.ensa.queueservice.web.Views.CounterView;
import ma.ensa.queueservice.web.Views.EstablishmentView;
import ma.ensa.queueservice.web.Views.QueueView;
import ma.ensa.queueservice.web.Views.WeeklyHoursView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                new QueueView(1L, "Dépôt de dossier", "A", "Guichet 3", 4, 0, 0, false), null, List.of()));

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

    @Test
    void anAdminCreatesAnEstablishment() throws Exception {
        when(service.createEstablishment(any(), any())).thenReturn(new EstablishmentView("annex", "Annexe",
                "Clinique", "Casablanca", "AX", null, null, "Rue 2", "08:30", "16:30", true, 0, 0, List.of()));

        mockMvc.perform(post("/api/establishments")
                        .header("X-Auth-Subject", "admin-1")
                        .header("X-Auth-Roles", "admin")
                        .contentType("application/json")
                        .content("{\"id\":\"annex\",\"name\":\"Annexe\",\"category\":\"Clinique\","
                                + "\"city\":\"Casablanca\",\"shortName\":\"AX\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("annex"));

        verify(service).createEstablishment(any(), any());
    }

    @Test
    void aQueuePrefixMustBeShortAndCapitalised() throws Exception {
        mockMvc.perform(post("/api/establishments/clinic/queues")
                        .header("X-Auth-Subject", "admin-1")
                        .header("X-Auth-Roles", "admin")
                        .contentType("application/json")
                        .content("{\"name\":\"Retrait\",\"prefix\":\"abcd\",\"avgServiceMinutes\":4,\"counter\":\"G\"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).createQueue(any(), any(), any());
    }

    @Test
    void deletingAQueueAnswersNoContent() throws Exception {
        mockMvc.perform(delete("/api/queues/1")
                        .header("X-Auth-Subject", "admin-1")
                        .header("X-Auth-Roles", "admin"))
                .andExpect(status().isNoContent());

        verify(service).deleteQueue(any(), eq(1L));
    }

    @Test
    void anAdminReadsAndReplacesTheWeeklySchedule() throws Exception {
        when(service.schedule(any(), eq("clinic")))
                .thenReturn(List.of(new WeeklyHoursView(1, "08:30", "16:30")));
        when(service.replaceSchedule(any(), eq("clinic"), any()))
                .thenReturn(List.of(new WeeklyHoursView(1, "08:30", "16:30")));

        mockMvc.perform(get("/api/establishments/clinic/hours")
                        .header("X-Auth-Subject", "admin-1")
                        .header("X-Auth-Roles", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].opensAt").value("08:30"));

        mockMvc.perform(put("/api/establishments/clinic/hours")
                        .header("X-Auth-Subject", "admin-1")
                        .header("X-Auth-Roles", "admin")
                        .contentType("application/json")
                        .content("{\"intervals\":[{\"dayOfWeek\":1,\"opensAt\":\"08:30\",\"closesAt\":\"16:30\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].closesAt").value("16:30"));

        verify(service).replaceSchedule(any(), eq("clinic"), any());
    }

    @Test
    void anInvalidScheduleIsRejectedBeforeTheService() throws Exception {
        mockMvc.perform(put("/api/establishments/clinic/hours")
                        .header("X-Auth-Subject", "admin-1")
                        .header("X-Auth-Roles", "admin")
                        .contentType("application/json")
                        .content("{\"intervals\":[{\"dayOfWeek\":8,\"opensAt\":\"08:30\",\"closesAt\":\"16:30\"}]}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).replaceSchedule(any(), any(), any());
    }
}
