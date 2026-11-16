package ma.ensa.tenantservice.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

@Component
public class TenantConfigValidator {
    public static final int MAX_BYTES = 16 * 1024;
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    public void validate(String configJson) {
        if (configJson == null || configJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw invalid("configuration exceeds the 16 KiB limit");
        }
        try {
            var root = new com.google.gson.JsonParser().parse(configJson).getAsJsonObject();
            var branding = root.getAsJsonObject("branding");
            if (branding == null) {
                throw invalid("branding is required");
            }
            requireString(branding, "name");
            requireColor(branding, "primaryColor");
            requireColor(branding, "accentColor");
            if (branding.has("logoUrl") && !branding.get("logoUrl").isJsonNull()) {
                String logo = branding.get("logoUrl").getAsString();
                if (!logo.startsWith("/") || logo.startsWith("//")
                        || logo.contains("\\") || logo.contains("?") || logo.contains("#")) {
                    throw invalid("logoUrl must be a same-origin path without query or fragment");
                }
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalid("configuration must be a JSON object matching the tenant schema");
        }
    }

    private void requireString(com.google.gson.JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()
                || !object.getAsJsonPrimitive(field).isString()
                || object.get(field).getAsString().isBlank()) {
            throw invalid("branding." + field + " is required");
        }
    }

    private void requireColor(com.google.gson.JsonObject object, String field) {
        requireString(object, field);
        if (!COLOR.matcher(object.get(field).getAsString()).matches()) {
            throw invalid("branding." + field + " must match #RRGGBB");
        }
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
