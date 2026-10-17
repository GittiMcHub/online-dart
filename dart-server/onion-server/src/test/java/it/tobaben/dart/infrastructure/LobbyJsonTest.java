package it.tobaben.dart.infrastructure;

import it.tobaben.dart.application.DartboardInput;
import it.tobaben.dart.application.lobby.LobbyPlayer;
import it.tobaben.dart.application.lobby.LobbyService;
import it.tobaben.dart.application.port.GameUpdatePublisherPort;
import it.tobaben.dart.application.port.SoundPublisherPort;
import it.tobaben.dart.application.session.ServerSession;
import it.tobaben.dart.game.bot.BotProfile;
import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

class LobbyJsonTest {

    @Test
    void botsSurviveTheLobbyStateRoundTrip() {
        LobbyService lobby = new LobbyService();
        lobby.join("Alice", 1, LobbyPlayer.LOCAL_CLIENT_ID);
        lobby.addBot("Bot", new BotProfile(60));
        GameUpdatePublisherPort updates = update -> { };
        SoundPublisherPort sounds = sound -> { };
        ServerSession session = new ServerSession(new LinkedBlockingQueue<DartboardInput>(), lobby, updates, sounds);

        var view = LobbyJson.parseState(LobbyJson.stateJson(session, "Test", 1));

        assertFalse(view.players().get(0).isBot());
        assertEquals(new BotProfile(60), view.players().get(1).bot());
    }
}
