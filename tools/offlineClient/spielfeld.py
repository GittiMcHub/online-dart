from spieler import Spieler

class Spielfeld:
    def __init__(self, num_players):
        self.spieler = []
        for i in range(num_players):
            self.spieler.append(Spieler("Spieler " + str(i+1)))
    
    def wurf(self, spieler_idx, wurf):
        self.spieler[spieler_idx].wurf(wurf)