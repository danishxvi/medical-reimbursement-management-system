package com.mrms.identity.internal;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTests {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsStrongPassword() {
        assertThat(policy.check("Blue#River2026", "EMP1001")).isEmpty();
    }

    @Test
    void rejectsWeakPasswords() {
        assertThat(policy.check("short1!A", "EMP1001")).isNotEmpty();
        assertThat(policy.check("alllowercase123!", "EMP1001")).contains("Add an upper case letter");
        assertThat(policy.check("NoDigitsHere!!", "EMP1001")).contains("Add a digit");
        assertThat(policy.check("NoSymbols12345", "EMP1001")).contains("Add a symbol such as @ # $ %");
        assertThat(policy.check("Emp1001@Secure", "EMP1001")).contains("Do not include your Employee ID");
        assertThat(policy.check("Password@123", "X")).isNotEmpty();
    }

    @RepeatedTest(20)
    void generatedTemporaryPasswordsSatisfyThePolicy() {
        assertThat(policy.check(policy.generateTemporary(), "EMP1001")).isEmpty();
    }
}
