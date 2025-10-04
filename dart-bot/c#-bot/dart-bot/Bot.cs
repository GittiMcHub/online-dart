using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace dartBot
{
    public class Bot
    {

        public string name { get; set; }
        public List<List<string[]>> spiele { get; set; }  // spiele -> list von spielen -> jedes spiel list von wurfen -> jeder wurf int[3]

        public int zeigerSpielzug { get; set; } = 0; // aktueller Spielzug in Spiel
        public int zeigerWurf { get; set; } = 0; // Anzahl freie Würfe pro Spielerzug 3 = der erste

        public int spielIdMerker { get; set; } = 1234; // Für reset des SpielzugZeigers

        public int dartboardId { get; set; } = 1234; // DartboardId für Bot


    }
}
