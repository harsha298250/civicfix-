package com.civicfix.security;

import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS, noRollbackFor = UsernameNotFoundException.class)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new UsernameNotFoundException("Identifier cannot be empty");
        }

        String trimmed = identifier.trim();

        // 1. Try finding by email
        User user = userRepository.findByEmailIgnoreCase(trimmed)
                // 2. Try finding by phone (exact)
                .or(() -> userRepository.findByPhone(trimmed))
                // 3. Try finding by normalized phone variants (10-digit, +91, and space-separated)
                .or(() -> {
                    String digits = trimmed.replaceAll("[^0-9]", "");
                    if (digits.length() == 10) {
                        return userRepository.findByPhone("+91" + digits)
                                .or(() -> userRepository.findByPhone("+91 " + digits.substring(0, 5) + " " + digits.substring(5)))
                                .or(() -> userRepository.findByPhone(digits));
                    } else if (digits.length() == 12 && digits.startsWith("91")) {
                        String ten = digits.substring(2);
                        return userRepository.findByPhone("+" + digits)
                                .or(() -> userRepository.findByPhone("+91 " + ten.substring(0, 5) + " " + ten.substring(5)))
                                .or(() -> userRepository.findByPhone(ten));
                    }
                    return userRepository.findByPhone(trimmed.replaceAll("\\s+", ""));
                })
                .orElseThrow(() -> new UsernameNotFoundException("User not found with identifier: " + identifier));

        return new UserPrincipal(user);
    }
}
