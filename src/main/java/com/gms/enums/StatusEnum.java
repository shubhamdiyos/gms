package com.gms.enums;

/**
 * Status enums for various entities in the GMS system
 */
public class StatusEnum {

    /**
     * School status enum
     */
    public enum SchoolStatus {
        ACTIVE("1", "School is active and operational"),
        INACTIVE("INACTIVE", "School is temporarily inactive"),
        SUSPENDED("SUSPENDED", "School is suspended due to violations"),
        PENDING_APPROVAL("PENDING_APPROVAL", "School registration is pending approval");

        private final String code;
        private final String description;

        SchoolStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() { return code; }
        public String getDescription() { return description; }

        public static SchoolStatus fromCode(String code) {
            for (SchoolStatus status : values()) {
                if (status.getCode().equals(code)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown school status code: " + code);
        }
    }

    /**
     * Employee status enum
     */
    public enum EmployeeStatus {
        ACTIVE("1", "Employee is actively working"),
        INACTIVE("INACTIVE", "Employee is temporarily inactive"),
        TERMINATED("TERMINATED", "Employee has been terminated"),
        ON_LEAVE("ON_LEAVE", "Employee is on extended leave"),
        PROBATION("PROBATION", "Employee is on probation period");

        private final String code;
        private final String description;

        EmployeeStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() { return code; }
        public String getDescription() { return description; }

        public static EmployeeStatus fromCode(String code) {
            for (EmployeeStatus status : values()) {
                if (status.getCode().equals(code)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown employee status code: " + code);
        }
    }

    /**
     * User account status enum
     */
    public enum UserStatus {
        ACTIVE("1", "User account is active"),
        LOCKED("LOCKED", "User account is locked"),
        PASSWORD_EXPIRED("PASSWORD_EXPIRED", "User password has expired"),
        DISABLED("DISABLED", "User account is disabled"),
        PENDING_VERIFICATION("PENDING_VERIFICATION", "User account is pending email/phone verification");

        private final String code;
        private final String description;

        UserStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() { return code; }
        public String getDescription() { return description; }

        public static UserStatus fromCode(String code) {
            for (UserStatus status : values()) {
                if (status.getCode().equals(code)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown user status code: " + code);
        }
    }

    /**
     * Board affiliation enum for schools
     */
    public enum BoardAffiliation {
        CBSE("CBSE", "Central Board of Secondary Education"),
        ICSE("ICSE", "Indian Certificate of Secondary Education"),
        STATE_BOARD("STATE_BOARD", "State Education Board"),
        IGCSE("IGCSE", "International General Certificate of Secondary Education"),
        IB("IB", "International Baccalaureate"),
        OTHER("OTHER", "Other board affiliation");

        private final String code;
        private final String description;

        BoardAffiliation(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() { return code; }
        public String getDescription() { return description; }

        public static BoardAffiliation fromCode(String code) {
            for (BoardAffiliation board : values()) {
                if (board.getCode().equals(code)) {
                    return board;
                }
            }
            throw new IllegalArgumentException("Unknown board affiliation code: " + code);
        }
    }

    /**
     * Department enum for designations
     */
    public enum Department {
        ACADEMIC("ACADEMIC", "Academic Department"),
        ADMINISTRATIVE("ADMINISTRATIVE", "Administrative Department"),
        SUPPORT("SUPPORT", "Support Services Department"),
        FINANCE("FINANCE", "Finance Department"),
        MAINTENANCE("MAINTENANCE", "Maintenance Department"),
        LIBRARY("LIBRARY", "Library Department"),
        SPORTS("SPORTS", "Sports Department");

        private final String code;
        private final String description;

        Department(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() { return code; }
        public String getDescription() { return description; }

        public static Department fromCode(String code) {
            for (Department dept : values()) {
                if (dept.getCode().equals(code)) {
                    return dept;
                }
            }
            throw new IllegalArgumentException("Unknown department code: " + code);
        }
    }
}
