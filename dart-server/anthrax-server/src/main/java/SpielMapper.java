public class SpielMapper {
    public static SpielDto map(Spiel spiel){
        SpielDto dto = new SpielDto();
        dto.anzahlSpiele = spiel.getAnzahlSpieleTurnier();
        dto.spielId = spiel.getId();
        dto.currentPlayer = spiel.getCurrentPlayer();
        dto.spielerPlatzierung = spiel.getSpielerPlatzierung();
        dto.spielerReihenfolge = spiel.getSpielerReihenfolge();
        dto.kostenStrafpunkte = spiel.getKostenStrafpunkt();
        dto.punkteSpielzug = spiel.getPunkteSpielzug();
        dto.gameState = spiel.getGameState();
        dto.letzterWurf = spiel.getLetzerWurf();
        return dto;
    }

    public static Spiel map(SpielDto dto){
        return null;
    }
}
