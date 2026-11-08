package com.gravitycanvas.validation;

import com.gravitycanvas.service.UserValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production-grade JUnit 5 Unit Tests for UserValidationService following OWASP & industry standards.
 */
class UserValidationServiceTest {

    private UserValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new UserValidationService();
    }

    // =========================================================================
    // FULL NAME VALIDATION TESTS (Unicode, Hyphens, Apostrophes, 2-70 Chars)
    // =========================================================================

    @ParameterizedTest
    @DisplayName("Valid Full Names (Unicode, Hyphens, Apostrophes, International)")
    @ValueSource(strings = {
        "Sarah Jenkins",
        "Akshat Chandawar",
        "Jean-Luc O'Connor",
        "María José",
        "Renée d'Amboise",
        "Li Wei"
    })
    void testValidateFullName_Valid(String fullName) {
        assertDoesNotThrow(() -> validationService.validateFullName(fullName));
    }

    @ParameterizedTest
    @DisplayName("Invalid Full Names (Numbers, HTML Tags, Special Symbols, Length Out of Bounds)")
    @ValueSource(strings = {
        "Sarah123",
        "Akshat<script>",
        "Sarah@Jenkins",
        "A",
        "ThisIsAnExtremelyLongFullNameThatExceedsTheMaximumAllowedSeventyCharacterLimitForUserAccountsInTheSystem"
    })
    void testValidateFullName_Invalid(String fullName) {
        assertThrows(IllegalArgumentException.class, () -> validationService.validateFullName(fullName));
    }

    // =========================================================================
    // USERNAME VALIDATION TESTS (Alphanumeric, Reserved Words, Boundary Dots)
    // =========================================================================

    @ParameterizedTest
    @DisplayName("Valid Usernames (3-30 Chars, Alphanumeric, Single Internal Dot/Hyphen/Underscore)")
    @ValueSource(strings = {
        "sjenkins",
        "akshat_c",
        "alex.vance",
        "devon-smith",
        "user123"
    })
    void testValidateUsername_Valid(String username) {
        assertDoesNotThrow(() -> validationService.validateUsername(username));
    }

    @ParameterizedTest
    @DisplayName("Invalid Usernames (Reserved Words, Boundary Symbols, Consecutive Symbols, Length)")
    @ValueSource(strings = {
        "admin",
        "ADMIN",
        "root",
        "support",
        "null",
        ".username",
        "username.",
        "_username",
        "username_",
        "-username",
        "username--test",
        "user..name",
        "user__name",
        "ab",
        "thisusernameiswaytoolongto-beacceptablebythesystem"
    })
    void testValidateUsername_Invalid(String username) {
        assertThrows(IllegalArgumentException.class, () -> validationService.validateUsername(username));
    }

    // =========================================================================
    // PASSWORD VALIDATION TESTS (Min 8 Chars, 4 Character Classes)
    // =========================================================================

    @ParameterizedTest
    @DisplayName("Valid Passwords (Min 8 Chars, Uppercase, Lowercase, Number, Special Char)")
    @ValueSource(strings = {
        "Password123!",
        "P@ssw0rd2026",
        "Super$ecure1",
        "MyP@ssw0rd With Space"
    })
    void testValidatePassword_Valid(String password) {
        assertDoesNotThrow(() -> validationService.validatePassword(password));
    }

    @ParameterizedTest
    @DisplayName("Invalid Passwords (Short Length, Missing Character Classes)")
    @ValueSource(strings = {
        "Pass1!",           // Too short (< 8)
        "password123!",     // Missing Uppercase
        "PASSWORD123!",     // Missing Lowercase
        "Password!",        // Missing Number
        "Password123"       // Missing Special Character
    })
    void testValidatePassword_Invalid(String password) {
        assertThrows(IllegalArgumentException.class, () -> validationService.validatePassword(password));
    }

    // =========================================================================
    // EMPLOYEE NUMBER VALIDATION TESTS (Format: EMP-XXXX)
    // =========================================================================

    @Test
    @DisplayName("Valid Employee Numbers (EMP- Prefix with Digits)")
    void testValidateEmployeeNo_Valid() {
        assertDoesNotThrow(() -> validationService.validateEmployeeNo("EMP-101"));
        assertDoesNotThrow(() -> validationService.validateEmployeeNo("EMP-301"));
        assertDoesNotThrow(() -> validationService.validateEmployeeNo("EMP-999999"));
    }

    @ParameterizedTest
    @DisplayName("Invalid Employee Numbers (Wrong Prefix or Non-Numeric)")
    @ValueSource(strings = {
        "101",
        "EMP-",
        "EMP-ABC",
        "EMP 101",
        "WORKER-101"
    })
    void testValidateEmployeeNo_Invalid(String employeeNo) {
        assertThrows(IllegalArgumentException.class, () -> validationService.validateEmployeeNo(employeeNo));
    }
}
