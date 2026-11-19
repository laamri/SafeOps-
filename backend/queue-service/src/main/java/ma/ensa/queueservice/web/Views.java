package ma.ensa.queueservice.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import ma.ensa.queueservice.domain.TicketStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JSON shapes sent to the frontend. Field names follow what src/App.tsx already uses
 * (short, color, tint...) so the screens can switch from mock data with little change.
 */
public final class Views {

    private Views() {
    }

    public record EstablishmentView(
            String id,
            String name,
            String category,
            String city,
            String shortName,
            String color,
            String tint,
            String address,
            String opensAt,
            String closesAt,
            boolean active,
            long waiting,
            long estimatedWaitMinutes,
            List<QueueView> queues
    ) {
    }

    public record QueueView(
            Long id,
            String name,
            String prefix,
            String counter,
            long waiting,
            long estimatedWaitMinutes,
            boolean open
    ) {
    }

    public record TicketView(
            UUID id,
            String code,
            TicketStatus status,
            String establishmentId,
            String establishmentName,
            String establishmentShortName,
            String color,
            String tint,
            Long queueId,
            String queueName,
            String counter,
            String ownerName,
            // 1 = next to be called; 0 once the ticket is no longer waiting
            long position,
            long estimatedWaitMinutes,
            Instant createdAt,
            Instant calledAt
    ) {
    }

    /**
     * What an agent sees at a counter: who is being served and who is next.
     */
    public record CounterView(
            QueueView queue,
            TicketView current,
            List<TicketView> waiting
    ) {
    }

    public record TakeTicketRequest(@NotNull Long queueId) {
    }

    public record QueueStatusRequest(@NotNull Boolean open) {
    }

    /**
     * A site is created with the id of the Keycloak tenant group it belongs to: the caller can
     * only create the row for a site already in their token claims.
     */
    public record EstablishmentCreateRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,63}", message = "id must be a slug") String id,
            @NotBlank String name,
            @NotBlank String category,
            @NotBlank String city,
            @NotBlank String shortName,
            String color,
            String tint,
            String address,
            String opensAt,
            String closesAt
    ) {
    }

    public record EstablishmentUpdateRequest(
            @NotBlank String name,
            @NotBlank String category,
            @NotBlank String city,
            @NotBlank String shortName,
            String color,
            String tint,
            String address,
            String opensAt,
            String closesAt
    ) {
    }

    public record EstablishmentStatusRequest(@NotNull Boolean active) {
    }

    public record QueueRequest(
            @NotBlank String name,
            @NotBlank @Pattern(regexp = "[A-Z0-9]{1,4}", message = "prefix is 1 to 4 capital letters or digits") String prefix,
            @NotNull @Min(1) @Max(240) Integer avgServiceMinutes,
            @NotBlank String counter
    ) {
    }

    /**
     * Same shape as the appointment-service error body, so the frontend can read
     * {@code message} for every 4xx from the gateway.
     */
    public record ApiError(java.time.Instant timestamp, int status, String error, String message, String path) {
    }
}
