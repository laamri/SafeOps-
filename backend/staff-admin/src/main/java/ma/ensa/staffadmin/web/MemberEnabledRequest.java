package ma.ensa.staffadmin.web;

import jakarta.validation.constraints.NotNull;

public record MemberEnabledRequest(@NotNull Boolean enabled) {
}
