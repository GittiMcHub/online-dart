using System;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Collections.Generic;

namespace dartBot
{
    public class Statistik
    {
        public int anzWuerfeSpiel { get; set; }
        public int anzWuerfeTurnier { get; set; }
        public int summeSpiel { get; set; }
        public int summeTurnier { get; set; }
        public int anzMinWuerfeBisSpielende { get; set; }
        public int maxPunkteProSpielzug { get; set; }
        public double avgSpiel { get; set; }
        public double avgTurnier { get; set; }
        public int anzStrafpunkte { get; set; }
        public int anzRandTreffer { get; set; }
        public int anzWandTreffer { get; set; }
        public int anzBull { get; set; }
        public int anzBullseye { get; set; }
        public int anzEinerFeld { get; set; }
        public int anzDoubleFeld { get; set; }
        public int anzTripleFeld { get; set; }
        public int anzUeberworfen { get; set; }
        public int highestFinish { get; set; }
        public List<List<int>> getroffeneFelder { get; set; }
    }

    public class Spieler
    {
        public int id { get; set; }
        public int dartboardId { get; set; }
        public string name { get; set; }
        public int punktestand { get; set; }
        public int freieWuerfe { get; set; }
        public Statistik statistik { get; set; }
    }

    public class GameMessage
    {
        public List<Spieler> spielerPlatzierung { get; set; }
        public List<Spieler> spielerReihenfolge { get; set; }
        public Spieler currentPlayer { get; set; }
        public int kostenStrafpunkte { get; set; }
        public int anzahlSpiele { get; set; }
        public int spielId { get; set; }
        public int punkteSpielzug { get; set; }
        public int letzterWurf { get; set; }
        public string gameState { get; set; }
    }
}