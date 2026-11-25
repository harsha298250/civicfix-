package com.civicfix.security;

import com.civicfix.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String fullName;
    private final String email;
    private final String phone;
    private final String password;
    private final String accountStatus;
    private final String department;
    private final String ward;
    private final boolean crossWardAccess;
    private final OffsetDateTime crossWardExpiresAt;
    private final boolean crossDepartmentAccess;
    private final boolean emailVerified;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.fullName = user.getFullName();
        this.email = user.getEmail();
        this.phone = user.getPhone();
        this.password = user.getPasswordHash();
        this.accountStatus = user.getAccountStatus();
        this.department = user.getDepartment();
        this.ward = user.getWard();
        this.crossWardAccess = user.isCrossWardAccess();
        this.crossWardExpiresAt = user.getCrossWardExpiresAt();
        this.crossDepartmentAccess = user.isCrossDepartmentAccess();
        this.emailVerified = user.isEmailVerified();
        this.authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().toSecurityRole()))
                .collect(Collectors.toSet());
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getDepartment() {
        return department;
    }

    public String getWard() {
        return ward;
    }

    public boolean isCrossWardAccess() {
        return crossWardAccess;
    }

    public OffsetDateTime getCrossWardExpiresAt() {
        return crossWardExpiresAt;
    }

    public boolean hasActiveCrossWardAccess() {
        if (!crossWardAccess) {
            return false;
        }
        if (crossWardExpiresAt != null && crossWardExpiresAt.isBefore(OffsetDateTime.now())) {
            return false;
        }
        return true;
    }

    public boolean isCrossDepartmentAccess() {
        return crossDepartmentAccess;
    }

    public boolean hasActiveCrossDepartmentAccess() {
        return crossDepartmentAccess;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email != null ? email : phone;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !"SUSPENDED".equalsIgnoreCase(accountStatus);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equalsIgnoreCase(accountStatus);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserPrincipal that = (UserPrincipal) o;
        return java.util.Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hashCode(id);
    }
}
