package ma.ensa.appointmentservice.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repairs appointments created before Slice 1 added the organisation column. Runs after
 * DemoData and is idempotent: every assignment is guarded by a null check.
 */
@Component
@Order(20)
public class TenantBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantBackfill.class);

    private final AppointmentRepository appointments;

    public TenantBackfill(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int updated = 0;
        for (Appointment appointment : appointments.findAll()) {
            if (appointment.getOrganizationId() == null) {
                // Slice 1: establishment and organisation share the same id.
                appointment.assignOrganizationId(appointment.getEstablishmentId());
                updated++;
            }
        }
        log.info("tenant backfill: appointments={}", updated);
    }
}
