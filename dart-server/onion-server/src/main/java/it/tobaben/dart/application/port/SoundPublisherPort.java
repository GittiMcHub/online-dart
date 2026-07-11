package it.tobaben.dart.application.port;

import it.tobaben.dart.application.Sound;

/**
 * Outbound port: asks the clients to play a sound
 * (infrastructure maps it to status/playSound JSON).
 */
public interface SoundPublisherPort {
    void play(Sound sound);
}
