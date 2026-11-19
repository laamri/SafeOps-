# Known gaps

## Appointments can still be booked on a suspended establishment (until Slice 3)

`PUT /api/establishments/{id}/status {active:false}` stops new tickets and hides the
establishment from the client catalogue, but `POST /api/appointments` in appointment-service
does not consult queue-service, so a client can still book an appointment against the suspended
establishment. Closing this gap requires an authenticated service-to-service call from
appointment-service to queue-service; it is deferred to Slice 3 (service-to-service auth).
Until then this is a deliberate, documented gap: no unauthenticated appointment -> queue-service
call is added.

The expected behaviour is captured by the disabled test
`AppointmentServiceTest.rejectsAppointmentOnASuspendedEstablishmentUntilSlice3`.
