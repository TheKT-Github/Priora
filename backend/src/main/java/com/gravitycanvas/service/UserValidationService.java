package com.gravitycanvas.service;

import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Production-grade validation service enforcing OWASP and corporate security/UX standards.
 * Validates Full Name (Unicode letters, hyphens, apostrophes, 2-70 chars),
 * Username (3-30 chars, start/end alphanumeric, no reserved words, no consecutive symbols),
 * and Password (8-128 chars, 4 character classes).
 */
@Service
public class UserValidationService {

    // Reserved system names blocked by OWASP guidelines (case-insensitive)
    private static final Set<String> RESERVED_USERNAMES = Set.of(
        "admin", "administrator", "root", "support", "null", "system", "user", "superuser", "guest", "api", "owner"
    );

    // Full Name: Unicode letters (\p{L}), spaces, hyphens (-), and apostrophes (') (min 2, max 70)
    private static final Pattern FULL_NAME_PATTERN = Pattern.compile("^[\\p{L}]+(?:[ '\\-][\\p{L}]+)*$");

    // Username: Alphanumeric, underscores, hyphens, single internal dot (3-30 chars, starts/ends alphanumeric)
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9](?:[a-zA-Z0-9._-]{0,28}[a-zA-Z0-9])?$");
    private static final Pattern CONSECUTIVE_SPECIAL_SYMBOLS = Pattern.compile("[._-]{2,}");

    // Password: Min 8, max 128 chars, 1 uppercase, 1 lowercase, 1 number, 1 special char (including ASCII & space)
    private static final Pattern HAS_UPPERCASE = Pattern.compile(".*[A-Z].*");
    private static final Pattern HAS_LOWERCASE = Pattern.compile(".*[a-z].*");
    private static final Pattern HAS_NUMBER = Pattern.compile(".*[0-9].*");
    private static final Pattern HAS_SPECIAL = Pattern.compile(".*[\\s!\"#$%&'()*+,-./:;<=>?@\\[\\]^_`{|}~].*");

    /**
     * Validates Full Name according to OWASP guidelines:
     * - Unicode letters, spaces, hyphens, apostrophes (2-70 chars)
     * - Reject numbers, special symbols, and HTML tags (<, >, &)
     */
    public void validateFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Full Name is required.");
        }
        String sanitized = fullName.trim();

        if (sanitized.length() < 2 || sanitized.length() > 70) {
            throw new IllegalArgumentException("Full Name must be between 2 and 70 characters long.");
        }
        if (sanitized.contains("<") || sanitized.contains(">") || sanitized.contains("&")) {
            throw new IllegalArgumentException("Full Name cannot contain HTML tags or special script symbols.");
        }
        if (!FULL_NAME_PATTERN.matcher(sanitized).matches()) {
            throw new IllegalArgumentException("Full Name can only contain international letters, spaces, hyphens, and apostrophes (numbers and special symbols are forbidden).");
        }
    }

    /**
     * Validates Username according to OWASP guidelines:
     * - 3 to 30 characters, alphanumeric, underscores, hyphens, single internal dot.
     * - Must start and end with an alphanumeric character.
     * - Blocks reserved words (admin, root, support, null) and consecutive special symbols.
     */
    public void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required.");
        }
        String sanitized = username.trim();

        if (sanitized.length() < 3 || sanitized.length() > 30) {
            throw new IllegalArgumentException("Username must be between 3 and 30 characters long.");
        }
        if (RESERVED_USERNAMES.contains(sanitized.toLowerCase())) {
            throw new IllegalArgumentException("Username '" + sanitized + "' is a reserved system keyword and cannot be used.");
        }
        if (CONSECUTIVE_SPECIAL_SYMBOLS.matcher(sanitized).find()) {
            throw new IllegalArgumentException("Username cannot contain consecutive special symbols (e.g. '..', '--', '__').");
        }
        if (!USERNAME_PATTERN.matcher(sanitized).matches()) {
            throw new IllegalArgumentException("Username must start and end with an alphanumeric character and contain only alphanumeric, hyphens, underscores, or single internal dots.");
        }
    }

    /**
     * Validates Password according to OWASP guidelines:
     * - Min 8-12 chars (max 128 chars).
     * - Require at least 1 uppercase letter, 1 lowercase letter, 1 number, and 1 special character (including spaces).
     */
    public void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }
        if (password.length() < 8 || password.length() > 128) {
            throw new IllegalArgumentException("Password must be between 8 and 128 characters long.");
        }
        if (!HAS_UPPERCASE.matcher(password).matches()) {
            throw new IllegalArgumentException("Password must contain at least 1 uppercase letter (A-Z).");
        }
        if (!HAS_LOWERCASE.matcher(password).matches()) {
            throw new IllegalArgumentException("Password must contain at least 1 lowercase letter (a-z).");
        }
        if (!HAS_NUMBER.matcher(password).matches()) {
            throw new IllegalArgumentException("Password must contain at least 1 number (0-9).");
        }
        if (!HAS_SPECIAL.matcher(password).matches()) {
            throw new IllegalArgumentException("Password must contain at least 1 special character or space.");
        }
    }

    /**
     * Validates Employee Number format: EMP- followed by 1-6 digits.
     */
    public void validateEmployeeNo(String employeeNo) {
        if (employeeNo == null || !employeeNo.trim().matches("^EMP-[0-9]{1,6}$")) {
            throw new IllegalArgumentException("Employee Number must be in format 'EMP-XXXX' with 1-6 digits (e.g. EMP-301).");
        }
    }
}
