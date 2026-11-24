package ma.ensa.staffadmin.web;

/**
 * The temporary password is returned exactly once, to the authenticated administrator who
 * created the account; Keycloak forces the agent to replace it at first login. It is never
 * logged and never stored by this service.
 */
public record InvitationView(String id, String username, String temporaryPassword) {
}
