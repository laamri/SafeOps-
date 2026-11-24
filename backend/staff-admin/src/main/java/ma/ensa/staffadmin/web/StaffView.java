package ma.ensa.staffadmin.web;

public record StaffView(String id, String username, String email, String firstName, String lastName,
                        boolean enabled) {
}
