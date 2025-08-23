package com.gms.enums;

/**
 * Enum for system roles in the GMS application.
 * Defines the role hierarchy and access levels.
 */
public enum RoleEnum {
    SUPERADMIN("SUPERADMIN", "Super Administrator - System-wide access"),
    ADMIN("ADMIN", "School Administrator - School-level access"),
    TEACHER("TEACHER", "Teacher - Academic operations within assigned classes"),
    STUDENT("STUDENT", "Student - View personal academic information"),
    PARENT("PARENT", "Parent - View child's academic information");

    private final String code;
    private final String description;

    RoleEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get role by code
     */
    public static RoleEnum fromCode(String code) {
        for (RoleEnum role : values()) {
            if (role.getCode().equals(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role code: " + code);
    }

    /**
     * Check if role has system-level access (SUPERADMIN)
     */
    public boolean isSystemLevel() {
        return this == SUPERADMIN;
    }

    /**
     * Check if role has school-level access (ADMIN)
     */
    public boolean isSchoolLevel() {
        return this == ADMIN;
    }

    /**
     * Check if role has academic access (TEACHER, STUDENT, PARENT)
     */
    public boolean isAcademicLevel() {
        return this == TEACHER || this == STUDENT || this == PARENT;
    }
}
