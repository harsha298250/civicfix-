package com.civicfix.supervisor.dto;

import com.civicfix.user.User;

import java.util.UUID;

/**
 * Sanitized response for worker directory access by supervisors.
 * Exposes strictly the operational fields needed for field work reassignment:
 * ID, full name, department, ward, and account status.
 * Strictly excludes personal phone numbers, personal email addresses, trust scores,
 * and user administrative timestamps to prevent employee data harvesting.
 */
public class SupervisorWorkerResponse {

    private UUID id;
    private String fullName;
    private String department;
    private String ward;
    private String accountStatus;

    public SupervisorWorkerResponse() {}

    public SupervisorWorkerResponse(UUID id, String fullName, String department, String ward, String accountStatus) {
        this.id = id;
        this.fullName = fullName;
        this.department = department;
        this.ward = ward;
        this.accountStatus = accountStatus;
    }

    public static SupervisorWorkerResponse fromEntity(User u) {
        if (u == null) return null;
        return new SupervisorWorkerResponse(
                u.getId(),
                u.getFullName(),
                u.getDepartment(),
                u.getWard(),
                u.getAccountStatus()
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getWard() {
        return ward;
    }

    public void setWard(String ward) {
        this.ward = ward;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }
}
