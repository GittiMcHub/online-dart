class Spieler:
    def __init__(self, name):
        self.name = name
        self.punkte = 301
        self.wurf_anzahl_spiel = 0
        self.average_spiel = 0.0
        self.wurf_anzahl_turnier = 0
        self.average_turnier = 0.0
        self.geldstrafe = 0
        self.gewonnen = False
        self.platz = 0

    def wurf_auswerten(self, wurf):
        multiplikator = int(wurf[0])
        wurfpunkte = int(wurf[1:])

        wurfpunkte *= multiplikator

        if (self.punkte - wurfpunkte) == 0:
            # gewonnen
            self.punkte -= wurfpunkte
            self.gewonnen = True
            return -1

        if (self.punkte - wurfpunkte) < 0:
            # Überwurfen
            return -2
        
        if (self.punkte - wurfpunkte) > 0:
            # regulärer Wurf
            self.punkte -= wurfpunkte

            return wurfpunkte
            


    def wandtreffer_gerechnet(self):
        self.geldstrafe += 0.5

    def add_strafe(self,strafe):
        self.geldstrafe += strafe

    def hat_schnappszahl(self):
        # Konvertieren der Zahl in einen String, um auf die einzelnen Ziffern zugreifen zu können
        s = str(self.punkte)
        # Überprüfen, ob die Länge des Strings zwischen 2 und 3 liegt
        if 2 <= len(s) <= 3:
            # Überprüfen, ob alle Ziffern im String gleich sind
            if all(c == s[0] for c in s):
                self.geldstrafe += 0.5
                return True
        return False

    
    def get_geldstrafe(self):
        return self.geldstrafe

    def get_punkte(self):
        return self.punkte
    def set_punkte(self, punkte):
        self.punkte = punkte
    
    def get_average_spiel(self):
        return self.average_spiel
    def get_average_turnier(self):
        return self.average_turnier
    
    def berechne_average(self, spielzug_punkte):
        self.wurf_anzahl_spiel += 1
        self.wurf_anzahl_turnier += 1
        self.average_spiel = (self.average_spiel * (self.wurf_anzahl_spiel - 1) + spielzug_punkte) / self.wurf_anzahl_spiel
        self.average_turnier = (self.average_turnier * (self.wurf_anzahl_turnier - 1) + spielzug_punkte) / self.wurf_anzahl_turnier


    def platzierung(self, platz):
        self.platz = platz
    
    def get_platzierung(self):
        return self.platz
    
    def reset_spiel(self):
        self.punkte = 301
        self.wurf_anzahl_spiel = 0
        self.average_spiel = 0
        self.gewonnen = False
        self.platz = 0


    

