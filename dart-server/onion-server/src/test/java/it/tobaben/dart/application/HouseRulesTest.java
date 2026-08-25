package it.tobaben.dart.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HouseRulesTest {

    @Test
    void schnapszahlNeedsAtLeastTwoEqualDigits() {
        assertTrue(HouseRules.isSchnapszahl(11));
        assertTrue(HouseRules.isSchnapszahl(99));
        assertTrue(HouseRules.isSchnapszahl(111));
        assertTrue(HouseRules.isSchnapszahl(222));
        assertFalse(HouseRules.isSchnapszahl(5));
        assertFalse(HouseRules.isSchnapszahl(10));
        assertFalse(HouseRules.isSchnapszahl(121));
        assertFalse(HouseRules.isSchnapszahl(0));
    }
}
