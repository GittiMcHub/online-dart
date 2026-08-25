package it.tobaben.dart.infrastructure.ble;

import it.tobaben.dart.application.DartboardInput;
import it.tobaben.dart.infrastructure.SegmentCodec;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the 1:1 port of the Python connector's hex_mapping and — as the
 * contract coupling test — that SegmentCodec accepts every emitted code.
 */
class BoardHexCodecTest {

    private static String code(int value) {
        return BoardHexCodec.toWireCode(new byte[]{(byte) value});
    }

    @Test
    void outerSinglesMapTo101Through120() {
        for (int hex = 0x01; hex <= 0x14; hex++) {
            assertEquals(String.valueOf(100 + hex), code(hex));
        }
    }

    @Test
    void innerSinglesMapTo101Through120() {
        for (int hex = 0x15; hex <= 0x28; hex++) {
            assertEquals(String.valueOf(100 + hex - 0x14), code(hex));
        }
    }

    @Test
    void doublesMapTo201Through220() {
        for (int hex = 0x29; hex <= 0x3c; hex++) {
            assertEquals(String.valueOf(200 + hex - 0x28), code(hex));
        }
    }

    @Test
    void triplesMapTo301Through320() {
        for (int hex = 0x3d; hex <= 0x50; hex++) {
            assertEquals(String.valueOf(300 + hex - 0x3c), code(hex));
        }
    }

    @Test
    void specialFields() {
        assertEquals("125", code(0x51)); // Bull
        assertEquals("225", code(0x52)); // Bullseye
        assertEquals("999", code(0x65)); // Next-Player-Taste
    }

    @Test
    void unknownBytesFallBackToNextLikePython() {
        assertEquals("999", code(0x00));
        assertEquals("999", code(0x53));
        assertEquals("999", code(0xff));
        assertEquals("999", BoardHexCodec.toWireCode(new byte[0]));
        assertEquals("999", BoardHexCodec.toWireCode(null));
        assertEquals("999", BoardHexCodec.toWireCode(new byte[]{0x01, 0x02}));
    }

    @Test
    void everyEmittedCodeIsAcceptedBySegmentCodec() {
        for (int hex = 0x01; hex <= 0x52; hex++) {
            String wireCode = code(hex);
            Optional<DartboardInput> decoded = SegmentCodec.decode(1, wireCode);
            assertTrue(decoded.isPresent(),
                    "SegmentCodec lehnt Code " + wireCode + " (hex " + Integer.toHexString(hex) + ") ab");
        }
        assertTrue(SegmentCodec.decode(1, code(0x65)).isPresent());
    }
}
