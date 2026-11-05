package com.gms.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SchoolResponse {
    private Integer id;
    private String schoolId; // SCH001
    private String schoolName;
    private String schoolCode;
    private String status;
    private boolean enabled;

    /**
     * The account the provisioning created, and the password it was issued.
     *
     * <p>Both are populated only on the creation response, because that is the one moment the
     * plaintext credential exists: the row itself stores a BCrypt hash, and the email that would
     * otherwise carry it can be undeliverable (SMTP unavailable, address mistyped). A platform
     * owner who cannot hand the administrator a working credential has locked the school out of
     * its own tenant with no recovery path, so the response the creator already receives — over
     * the TLS connection, to the one caller authorised to provision — is the delivery channel of
     * last resort. The account is still flagged {@code requirePasswordChange}, so the password
     * cannot be used past the first sign-in.</p>
     */
    private String adminUsername;

    /** See {@link #adminUsername}. Never populated on list or read responses. */
    private String temporaryPassword;

    public SchoolResponse(Integer id, String schoolId, String schoolName, String schoolCode,
                          String status, boolean enabled) {
        this.id = id;
        this.schoolId = schoolId;
        this.schoolName = schoolName;
        this.schoolCode = schoolCode;
        this.status = status;
        this.enabled = enabled;
    }
}
