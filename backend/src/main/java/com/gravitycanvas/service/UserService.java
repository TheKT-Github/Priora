package com.gravitycanvas.service;

import com.gravitycanvas.dto.ChangePasswordRequest;
import com.gravitycanvas.dto.CreateUserRequest;
import com.gravitycanvas.dto.UserDto;
import com.gravitycanvas.entity.UserAccount;
import com.gravitycanvas.exception.ResourceNotFoundException;
import com.gravitycanvas.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service managing System User Accounts, Admin Account Creation, and OWASP Production-Grade Double-Validation.
 */
@Service
public class UserService {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserValidationService validationService;

    /**
     * Permitted roles for privileged user-creation and role-change operations.
     * Public registration always assigns ROLE_TEAM_MEMBER and never consults this set.
     */
    public static final Set<String> ALLOWED_ROLES = Set.of(
        "ROLE_PROJECT_MANAGER",
        "ROLE_TEAM_LEAD",
        "ROLE_TEAM_MEMBER",
        "ROLE_TESTER"
    );

    public UserService(UserAccountRepository userRepository, PasswordEncoder passwordEncoder, UserValidationService validationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.validationService = validationService;
    }

    /**
     * Retrieves all system users formatted as UserDto.
     */
    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    /**
     * Gets user profile by username.
     */
    @Transactional(readOnly = true)
    public UserDto getUserByUsername(String username) {
        UserAccount user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
        return convertToDto(user);
    }

    /**
     * Public registration: creates a new account and ALWAYS assigns ROLE_TEAM_MEMBER,
     * regardless of any role field present in the request.
     * This is the only code path for POST /api/users/register.
     */
    @Transactional
    public UserDto registerUser(CreateUserRequest req) {
        return createUserInternal(req, "ROLE_TEAM_MEMBER");
    }

    /**
     * Privileged user creation (Project Manager only via POST /api/users).
     * Validates the requested role against ALLOWED_ROLES before creating the account.
     * Throws IllegalArgumentException (-> HTTP 400) for unsupported role strings.
     */
    @Transactional
    public UserDto createUser(CreateUserRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("User creation request cannot be null.");
        }

        String rawRole = req.getRole() != null ? req.getRole().trim() : "";
        if (rawRole.isEmpty()) {
            throw new IllegalArgumentException("Role is required.");
        }

        // Normalize role format
        String formattedRole = rawRole.toUpperCase();
        if (!formattedRole.startsWith("ROLE_")) {
            formattedRole = "ROLE_" + formattedRole.replaceAll("[^A-Z0-9]", "_");
        }

        // Validate against whitelist -- reject unsupported roles with HTTP 400
        if (!ALLOWED_ROLES.contains(formattedRole)) {
            throw new IllegalArgumentException(
                "Role '" + rawRole + "' is not a supported system role. "
                + "Allowed roles: ROLE_PROJECT_MANAGER, ROLE_TEAM_LEAD, ROLE_TEAM_MEMBER, ROLE_TESTER."
            );
        }

        return createUserInternal(req, formattedRole);
    }

    /**
     * Shared user-creation logic. The resolvedRole parameter is the already-validated,
     * normalized role string to persist. The raw role in req is ignored here.
     */
    private UserDto createUserInternal(CreateUserRequest req, String resolvedRole) {
        if (req == null) {
            throw new IllegalArgumentException("User creation request cannot be null.");
        }

        // 1. Full Name OWASP Validation
        validationService.validateFullName(req.getFullName());

        // 2. Username OWASP Validation & Case-Insensitive Uniqueness Check
        String username = req.getUsername() != null ? req.getUsername().trim() : "";
        validationService.validateUsername(username);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken. Please enter a unique username.");
        }

        // 3. Password OWASP Validation
        validationService.validatePassword(req.getPassword());

        // 4. Employee Number Validation & Uniqueness Check
        String employeeNo = req.getEmployeeNo() != null ? req.getEmployeeNo().trim() : "";
        validationService.validateEmployeeNo(employeeNo);
        if (userRepository.existsByEmployeeNo(employeeNo)) {
            throw new IllegalArgumentException("Employee Number '" + employeeNo + "' already exists. Please enter a unique Employee Number.");
        }

        // 5. Department Validation
        String department = req.getDepartment() != null ? req.getDepartment().trim() : "";
        if (department.isEmpty()) {
            throw new IllegalArgumentException("Department is required.");
        }

        UserAccount newUser = new UserAccount();
        newUser.setUsername(username);
        // Password is BCrypt-hashed here. The raw password is never stored or returned.
        newUser.setPassword(passwordEncoder.encode(req.getPassword().trim()));
        newUser.setFullName(req.getFullName().trim());
        newUser.setRole(resolvedRole);  // resolvedRole -- never the caller's raw role field
        newUser.setEmployeeNo(employeeNo);
        newUser.setDepartment(department);

        UserAccount saved = userRepository.save(newUser);
        return convertToDto(saved);
    }

    /**
     * Changes password for authenticated user after verifying current password.
     */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest req) {
        UserAccount user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Incorrect current password.");
        }

        String newPassword = req.getNewPassword() != null ? req.getNewPassword().trim() : "";
        validationService.validatePassword(newPassword);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public UserDto convertToDto(UserAccount user) {
        return new UserDto(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.getRole(),
            user.getEmployeeNo(),
            user.getDepartment()
        );
    }
}
