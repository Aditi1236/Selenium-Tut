package org.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Placeholder test class for the SeleniumTut project.
 * Real browser-based Selenium tests require a running browser
 * and should be tagged/excluded from the default CI build.
 */
public class SeleniumTutTest {

    @Test
    void smokeTest() {
        // Verify the main class exists and is loadable
        assertNotNull(SeleniumTut.class.getName());
    }
}
