import tkinter as tk
from spieler import Spieler
from spielfeld import Spielfeld
import threading
import pygame
import numpy as np
import time
import paho.mqtt.client as mqtt

class StartPage(tk.Frame):

    def __init__(self, master, *args, **kwargs):
        super().__init__(master, *args, **kwargs)
        self.master = master
        self.master.title("Spieleinstellungen")
        self.create_widgets()
        self.sound_start = pygame.mixer.Sound('./sounds/spielstart.wav')

    def create_widgets(self):
        # Widget zur Eingabe der Anzahl der Spiele
        num_games_label = tk.Label(self.master, text="Anzahl der Spiele:", font=('arial', 20, 'bold'))
        num_games_label.grid(row=0, column=0, padx=10, pady=10)

        self.num_games_entry = tk.Entry(self.master)
        self.num_games_entry.grid(row=0, column=1, padx=10, pady=10)

        # Widget zur Eingabe der Anzahl der Spieler
        num_players_label = tk.Label(self.master, text="Anzahl Mitspieler:", font=('arial', 20, 'bold'))
        num_players_label.grid(row=1, column=0, padx=10, pady=10)

        self.num_players_entry = tk.Entry(self.master)
        self.num_players_entry.grid(row=1, column=1, padx=10, pady=10)

        # Widget zur Eingabe der Spielernamen
        self.player_names = []
        for i in range(0, 8):
            player_name_label = tk.Label(self.master, text="Spieler " + str(i+1) + ":")
            player_name_label.grid(row=i+2, column=0, padx=10, pady=10)

            player_name_entry = tk.Entry(self.master)
            player_name_entry.grid(row=i+2, column=1, padx=10, pady=10)

            self.player_names.append(player_name_entry)

        # Widget zur Bestätigung der Eingaben
        start_button = tk.Button(self.master, text="Start", command=self.start_game, font=('arial', 15, 'bold'))
        start_button.grid(row=10, column=1, padx=10, pady=10)
    
    def start_game(self):        
        # Anzahl der Spiele und Spieler aus den Eingabefeldern auslesen
        num_games = int(self.num_games_entry.get())
        num_players = int(self.num_players_entry.get())

        # Spieler-Namen aus den Eingabefeldern auslesen
        player_names = []
        for i in range(num_players):
            name = self.player_names[i].get()
            if name == "":
                player_names.append("Spieler " + str(i+1))
            else:
                player_names.append(name)

        self.sound_start.play()
        GamePage(self.master, num_games, num_players, player_names)
        self.master.withdraw() #hide current window


class GamePage(tk.Frame):

    button_size = 35
    font_size = 25

    def __init__(self, master_page, num_games, num_players, player_names, *args, **kwargs):
        super().__init__(master_page, *args, **kwargs)

        self.mqttc = mqtt.Client(protocol=mqtt.MQTTv311)
        # Callback-Funktionen dem Client zuweisen
        self.mqttc.on_connect = self.on_connect
        self.mqttc.on_message = self.on_message

        self.mqttc.username_pw_set("dartboard","smartness")
        self.mqttc.connect("10.0.10.1", 1883, 60)
        self.mqttc.loop_start()

        self.condition_quit = False
        self.condition_wandtreffer = False
        self.condition_keintreffer = False
        self.condition_wurfreset = False
        self.condition_treffer = False
        self.event_nextplayer = threading.Event()

        self.wurf_value = 0

        # Fenster erstellen
        self.num_games = num_games
        self.num_players = num_players
        self.master_page = master_page
        self.game_page = tk.Toplevel(self.master_page)
        self.game_page.title("Dart")

        # Fenster Quit Button
        self.quit_button = tk.Button(self.game_page, text="Quit", command=self.quit_game)
        self.quit_button.grid(row=1, column=0, padx=5, pady=5)

        # Widgets zur Anzeige Überschriften
        tk.Label(self.game_page, text="Name", font=('arial', 20, 'bold')).grid(row=2, column=1, padx=10, pady=10)
        tk.Label(self.game_page, text="Punkte", font=('arial', 20, 'bold')).grid(row=2, column=2, padx=10, pady=10)
        tk.Label(self.game_page, text="Spiel Avg", font=('arial', 20, 'bold')).grid(row=2, column=3, padx=10, pady=10)
        tk.Label(self.game_page, text="Turnier Avg", font=('arial', 20, 'bold')).grid(row=2, column=4, padx=10, pady=10)
        tk.Label(self.game_page, text="Geldstrafe", font=('arial', 20, 'bold')).grid(row=2, column=5, padx=10, pady=10)

        # Widgets zur Anzeige der Spielergebnisse
        self.player_labels = []
        self.player_points = []
        self.player_averages = []
        self.player_turnier_averages = []
        self.player_geldstrafen = []
        self.player_names = player_names
        self.players = []

        for i in range(0, self.num_players):

            player_label = tk.Label(self.game_page, text=player_names[i], font=('arial', 12, 'bold'))
            player_label.grid(row=i+3, column=1, padx=10, pady=10)
            self.player_labels.append(player_label)

            player_points = tk.Label(self.game_page, text="301", font=('arial', self.font_size))
            player_points.grid(row=i+3, column=2, padx=10, pady=10)
            self.player_points.append(player_points)

            player_average = tk.Label(self.game_page, text="0.00", font=('arial', self.font_size))
            player_average.grid(row=i+3, column=3, padx=10, pady=10)
            self.player_averages.append(player_average)

            player_turnier_average = tk.Label(self.game_page, text="0.00", font=('arial', self.font_size))
            player_turnier_average.grid(row=i+3, column=4, padx=10, pady=10)
            self.player_turnier_averages.append(player_turnier_average)
    
            player_geldstrafe = tk.Label(self.game_page, text="0.00 €", font=('arial', self.font_size))
            player_geldstrafe.grid(row=i+3, column=5, padx=10, pady=10)
            self.player_geldstrafen.append(player_geldstrafe)

            self.players.append(Spieler(self.player_names[i]))
                
        # Label aktuelles Spiel
        self.aktuelles_spiel = tk.Label(self.game_page, text="Spiel 1 von " + str(self.num_games), font=('arial', 10))
        self.aktuelles_spiel.grid(row=1, column=1, padx=10, pady=10)

        # Label Infoleiste
        self.info_label = tk.Label(self.game_page, text="Info")
        self.info_label.grid(row=1, column=3, columnspan=3, padx=10, pady=10, sticky='W')

        # Label hinweis Button next player
        self.info_next_label = tk.Label(self.game_page, text="")
        self.info_next_label.grid(row=self.num_players+5, column=0, padx=1, pady=1)

        # Spiel Buttons
        self.nextplayer_button = tk.Button(self.game_page, text="Next Player", command=self.next_player, font=('arial', self.button_size, 'bold'))
        self.nextplayer_button.grid(row=self.num_players+4, column=0, padx=5, pady=1)
        self.keintreffer_button = tk.Button(self.game_page, text="kein treffer", command=self.keintreffer, font=('arial', self.button_size, 'bold'))
        self.keintreffer_button.grid(row=self.num_players+4, column=4, padx=5, pady=5)
        self.wandtreffer_button = tk.Button(self.game_page, text="Wandtreffer", command=self.wandtreffer, font=('arial', self.button_size, 'bold'))
        self.wandtreffer_button.grid(row=self.num_players+4, column=5, padx=5, pady=5)
        self.wurfreset_button = tk.Button(self.game_page, text="Wurf reset", command=self.wurfreset, font=('arial', self.button_size, 'bold'))
        self.wurfreset_button.grid(row=self.num_players+4, column=2, padx=5, pady=5)

        # Starte das Spiel in seperaten Thread
        threading.Thread(target=self.start_turnier).start()

    # Callback-Funktion, die aufgerufen wird, wenn die Verbindung zum Broker hergestellt wird
    def on_connect(self, client, userdata, flags, rc):
        print("Verbunden mit MQTT-Broker mit dem Status: " + str(rc))
        # Hier das Abonnement für das gewünschte Topic durchführen
        self.mqttc.subscribe("dartboard/#")

    # Callback-Funktion, die aufgerufen wird, wenn eine Nachricht auf dem abonnierten Topic empfangen wird
    def on_message(self,client, userdata, msg):
        self.wurf_value = msg.payload.decode()
        self.condition_treffer = True

    def next_player(self):
        print("Button UI Next Player")
        self.play_sound('treffer')
        self.event_nextplayer.set()
        self.event_nextplayer.clear()

    def wandtreffer(self):
        print("Button Wandtreffer")
        self.play_sound('strafe')
        self.condition_wandtreffer = True

    def keintreffer(self):
        print("Button Kein Treffer")
        self.play_sound('reset')
        self.condition_keintreffer = True

    def wurfreset(self):
        print("Button Wurf reset")
        self.play_sound('reset')
        self.condition_wurfreset = True
        self.event_nextplayer.set()
        self.event_nextplayer.clear()


    def quit_game(self):
        self.condition_quit = True
        time.sleep(2)
        self.master_page.destroy()  # close the window and exit the app

    def start_turnier(self):
        for i in range(1, self.num_games+1):
            self.reset_spieler_spiel()
            self.update_ui()
            self.spiel(i)
        print("Finished")

    def spiel(self, spielnr):
        # Setze Label Spielnummer
        self.aktuelles_spiel.config(text="Spiel " + str(spielnr) + " von " + str(self.num_games))

        sieger = []
        platz = 1

        # Turnier = mehrere Spiele
        # Spiel = mehrere Spielrunden
        # Spielrunde = Alle Spieler einmal
        # Spielzug = 3 Würfe
        while self.condition_quit == False:
            # prüfen ob spiel zu ende
            if len(sieger) >= self.num_players -1:
                # Alle spieler beendet
                break 
            
            ############## Spieler Spielzug ##############
            for spielzug_aktueller_spieler in range(0,self.num_players):
                    # prüfen ob spiel zu ende
                if len(sieger) >= self.num_players -1:
                    # Alle spieler beendet
                    break

                # Prüfen ob Spieler schon gewonnen:
                if self.players[spielzug_aktueller_spieler] not in sieger:

                    # Wurfnummer merken
                    wurfnr = 1
                    # Falls jemand die Buttons gedrückt hat zur sicherheit
                    self.condition_wandtreffer = False
                    self.condition_keintreffer = False
                    self.condition_wurfreset = False

                    # UI clearen - Setze aktueller Spieler auf grau
                    for player_nr in range(0,self.num_players):
                        self.player_labels[player_nr].config(bg="#eaeaea")
                    # Setze aktuellen spieler auf grün
                    self.player_labels[spielzug_aktueller_spieler].config(bg="#D5E88F")

                    # Für den Fall dass er gleich überwirft
                    merker_spieler_punkte = self.players[spielzug_aktueller_spieler].get_punkte()

                    # Merker, wie viel er in diesem Spielzug geworfen hat
                    spielzug_punkte = 0
                    ############## Spielzug hat 3 Würfe ##############
                    while (wurfnr <= 3) & (self.condition_quit == False):
                        wurf = self.warte_auf_wurf()

                        # Wandtreffer
                        if wurf==-1:
                            self.condition_wandtreffer = False
                            self.players[spielzug_aktueller_spieler].wandtreffer_gerechnet()
                            self.update_ui()
                            self.info_update("Wandtreffer von " + str(self.player_names[spielzug_aktueller_spieler]))
                            print("Wandtreffer von " + str(self.player_names[spielzug_aktueller_spieler]))
                            wurfnr+=1

                        # Kein Treffer
                        if wurf==-2:
                            self.condition_keintreffer = False
                            print("war tatsächlich kein Treffer")
                            wurfnr+=1

                        if wurf==999:
                            # Next Player gedrückt
                            self.condition_treffer = False
                            wurfnr += 4 

                        # Wurf ergab Punkte
                        if wurf > 0 and wurf <= 800:
                            self.condition_treffer = False
                            # Wurf Sounds
                            wurf_sound = str(wurf)
                            if wurf_sound[0]=='3':
                                self.play_sound('triple')
                            if wurf_sound[0]=='2':
                                if wurf_sound=='225':
                                    self.play_sound('bullseye')
                                else:
                                    self.play_sound('double')
                            if wurf_sound[0]=='1':
                                self.play_sound('treffer')
                            
                            
                            wurf_auswertung = self.players[spielzug_aktueller_spieler].wurf_auswerten(str(wurf))
                            if (wurf_auswertung > 0):
                                # Wurf Sounds
                                wurf_sound = str(wurf)
                                if wurf_sound[0]=='3':
                                    self.play_sound('triple')
                                if wurf_sound[0]=='2':
                                    if wurf_sound=='225':
                                        self.play_sound('bullseye')
                                    else:
                                        self.play_sound('double')
                                if wurf_sound[0]=='1':
                                    self.play_sound('treffer')
                                    
                                spielzug_punkte += wurf_auswertung
                            if(wurf_auswertung == -1):
                                print("Gewonnen!") 
                                self.play_sound('winner')
                                wurfnr = 10 #  Einfach mehr als 3
                                sieger.append(self.players[spielzug_aktueller_spieler])
                                self.players[spielzug_aktueller_spieler].platzierung(platz)
                                platz +=1
                            if(wurf_auswertung == -2):
                                print("Überworfen!")
                                self.play_sound('ueberworfen')
                                self.info_update("Überworfen von " + str(self.player_names[spielzug_aktueller_spieler]))
                                self.players[spielzug_aktueller_spieler].set_punkte(merker_spieler_punkte)
                                wurfnr = 10 #  Einfach mehr als 3

                            # UI Updaten
                            self.update_ui()

                            wurfnr+=1
                            time.sleep(1)
                    ############## 3 Würfe zuende ##############

                    # average Berechnen und UI Updaten
                    # TODO Auswertung nicht beim letzetn Wurf zum Sieg
                    self.players[spielzug_aktueller_spieler].berechne_average(spielzug_punkte)
                    self.update_ui()

                    # Prüfen ob Spieler nun eine Schnappszahl hat
                    if(self.players[spielzug_aktueller_spieler].hat_schnappszahl() == True):
                        print("Spieler hat schnappszahl")
                        self.play_sound('strafe')
                        self.info_update("Schnapszahl von " + str(self.player_names[spielzug_aktueller_spieler]))
                        self.update_ui()

                    # Warten bis auf Next Player gedrückt wurde
                    self.player_labels[spielzug_aktueller_spieler].config(bg="yellow")
                    self.info_next_label.config(text=">plz klick<")              
                    self.event_nextplayer.wait() #  Nun Spielzug beendet

                    # ABFRAGE RESET
                    if self.condition_wurfreset == True:
                        print("Spielerpunkte zurücksetzen")
                        self.players[spielzug_aktueller_spieler].set_punkte(merker_spieler_punkte)
                        self.update_ui()
                        self.condition_wurfreset = False

                    self.info_next_label.config(text="")
            ############## Spieler Spielzug zuende ##############
        ############## Spiel zuende ##############
        #Geldstrafe für verlierer
        for player in range(0, self.num_players):
            if (self.players[player].get_platzierung() == 0):
                self.players[player].add_strafe(0.5 * self.num_players)
            else:
                self.players[player].add_strafe(0.5 * self.players[player].get_platzierung())
        
        self.update_ui()
        print("Spiel zu ende")
        self.info_update("Spiel " + str(spielnr) + " beendet")
        self.info_next_label.config(text=">plz klick<")
        self.event_nextplayer.wait()
        self.info_next_label.config(text="")

    def update_ui(self):
        for player in range(0,self.num_players):
            self.player_points[player].config(text=str(self.players[player].get_punkte()))
            self.player_averages[player].config(text="{:10.2f}".format(self.players[player].get_average_spiel()))
            self.player_turnier_averages[player].config(text="{:10.2f}".format(self.players[player].get_average_turnier()))
            self.player_geldstrafen[player].config(text="{:10.2f} €".format(self.players[player].get_geldstrafe()))

    def reset_spieler_spiel(self):
        for player in range(0,self.num_players):
            self.players[player].reset_spiel()

    def info_update(self, string):
        self.info_label.config(text="Info: " + string)

    def warte_auf_wurf(self):
        while self.condition_quit == False:
            
            if self.condition_wandtreffer == True:
                return -1
            if self.condition_keintreffer == True:
                return -2
            
            if self.condition_treffer == True:
                ergebnis = int(self.wurf_value)
                return ergebnis
    
        return 500
        #NUR FUER TEST
        #wurf = int(input("Bitte Wurf eingeben (Format: [1-3][1-9|25]): "))       
        #Wandtreffer
        #if wurf == -1:
        #    return -1
        #Kein Treffer
        #if wurf == -2:
        #    return -2        
        #return wurf

    def play_sound(self, str):
        start = pygame.mixer.Sound('./sounds/spielstart.wav')
        reset = pygame.mixer.Sound('./sounds/reset.wav')
        winner = pygame.mixer.Sound('./sounds/winner.wav')
        ueberworfen = pygame.mixer.Sound('./sounds/ueberworfen.wav')
        treffer = pygame.mixer.Sound('./sounds/treffer.wav')
        double = pygame.mixer.Sound('./sounds/double.wav')
        triple = pygame.mixer.Sound('./sounds/triple.wav')
        bullseye = pygame.mixer.Sound('./sounds/bullseye.wav')
        strafe = pygame.mixer.Sound('./sounds/strafe.wav')

        if str=='start':
            start.play()
        if str=='reset':
            reset.play()
        if str=='winner':
            winner.play()
        if str=='ueberworfen':
            ueberworfen.play()
        if str=='treffer':
            treffer.play()
        if str=='double':
            double.play()
        if str=='triple':
            triple.play()
        if str=='bullseye':
            bullseye.play()
        if str=='strafe':
            strafe.play()
            None


if __name__ == "__main__":
    pygame.mixer.init()
    root = tk.Tk()
    StartPage(root).grid(row=0, column=0)
    root.mainloop()
