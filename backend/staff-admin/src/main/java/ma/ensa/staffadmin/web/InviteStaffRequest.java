package ma.ensa.staffadmin.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InviteStaffRequest(
        @NotBlank
        @Pattern(regexp = "[a-z0-9][a-z0-9._-]{2,63}",
                message = "username must be lowercase letters, digits, dot, dash or underscore")
        String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName) {
}
