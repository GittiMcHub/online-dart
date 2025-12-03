# Version v0.1.2
import asyncio
from bleak import BleakClient
import time
import threading
import argparse
import json

DARTBOARD_CONFIG_FILE = "./config/dartboard.conf"
SAVE_FILE = "dart_games.json"

hex_mapping = {
    # Felder Außen
    "01": "101",
    "02": "102",
    "03": "103",
    "04": "104",
    "05": "105",
    "06": "106",
    "07": "107",
    "08": "108",
    "09": "109",
    "0a": "110",
    "0b": "111",
    "0c": "112",
    "0d": "113",
    "0e": "114",
    "0f": "115",
    "10": "116",
    "11": "117",
    "12": "118",
    "13": "119",
    "14": "120",
    # Felder innen
    "15": "101",
    "16": "102",
    "17": "103",
    "18": "104",
    "19": "105",
    "1a": "106",
    "1b": "107",
    "1c": "108",
    "1d": "109",
    "1e": "110",
    "1f": "111",
    "20": "112",
    "21": "113",
    "22": "114",
    "23": "115",
    "24": "116",
    "25": "117",
    "26": "118",
    "27": "119",
    "28": "120",
    # Felder double
    "29": "201",
    "2a": "202",
    "2b": "203",
    "2c": "204",
    "2d": "205",
    "2e": "206",
    "2f": "207",
    "30": "208",
    "31": "209",
    "32": "210",
    "33": "211",
    "34": "212",
    "35": "213",
    "36": "214",
    "37": "215",
    "38": "216",
    "39": "217",
    "3a": "218",
    "3b": "219",
    "3c": "220",
    # Felder triple
    "3d": "301",
    "3e": "302",
    "3f": "303",
    "40": "304",
    "41": "305",
    "42": "306",
    "43": "307",
    "44": "308",
    "45": "309",
    "46": "310",
    "47": "311",
    "48": "312",
    "49": "313",
    "4a": "314",
    "4b": "315",
    "4c": "316",
    "4d": "317",
    "4e": "318",
    "4f": "319",
    "50": "320",

    "51": "125", # Bull
    "52": "225", # Bullseye
    "65": "999" # Next Player
}

class DartGame:
    def __init__(self, player_name, num_games=1):
        self.player_name = player_name
        self.num_games = num_games
        self.games = []  # Liste aller Spiele
        self.current_game = []
        self.current_turn = []
        self.current_score = 301
        print(f"Current Score:  {self.current_score}")

    def add_throw(self, value):
        if len(self.games) == self.num_games:
            print("Programm wird nach spätestens 60 Sekunden automatisch geschlossen")
        """Einen Wurf hinzufügen"""
        if value == '999':
            # Zug beenden, egal wie viele Würfe
            while len(self.current_turn) < 3:
                self.current_turn.append("998")  # nicht geworfene Darts auffüllen
            self.current_game.append(self.current_turn)
            self.current_turn = []
            print(f"Spielzug abgeschlossen. Aktueller Punktestand: {self.current_score}")

            # Falls Score auf 0 → Spielende
            if self.current_score == 0:
                print("Spiel beendet!")
                self.finish_game()
                
            return

        # Normaler Wurf
        if len(self.current_turn) < 3:
            self.current_turn.append(value)

            # Punkte abziehen
            score_val = self.translate_value(value)
            if self.current_score - score_val >= 0:
                self.current_score -= score_val
                if len(self.current_turn) >= 3:
                    print("Spielzug beendet. Roten Knopf drücken.")
            else:
                # TODO Punkte auf Spielzug vorher resetten...
                print("Überworfen! Punkte bleiben gleich.")

        else:
            print("3 Würfe schon gemacht – warte auf roten Knopf (999).")

        print(f"New Score: + {self.current_score}")

    def translate_value(self, value):
        """Wert in Punkte umrechnen"""
        if value == "998" or value == "999":
            return 0
        mult = int(str(value)[0])
        num = int(str(value)[1:])
        return mult * num

    def finish_game(self):
        """Spiel speichern und zurücksetzen"""
        print("Spiel speichern und neue Runde starten.")
        self.games.append(self.current_game)
        self.current_game = []
        self.current_turn = []
        self.current_score = 301
        if(len(self.games) == self.num_games):
            self.save_to_file()


    def save_to_file(self):
        """JSON speichern"""
        data = {
            "name": self.player_name,
            "spiele": self.games
        }
        with open(SAVE_FILE, "w") as f:
            json.dump(data, f, indent=2)
        print(f"Spielstand in {SAVE_FILE} gespeichert.")


class DartBotRecorder():
    def __init__(self, game: DartGame, dartboard_mac=None, dartboard_uuid=None, dartboard_id=None):
        self.game = game
        # Wenn Skript mit Argumenten aufgerufen wurde, dann die Werte aus den Argumenten setzen
        if (dartboard_mac and dartboard_uuid and dartboard_id):
            self.DARTBOARD_MAC = dartboard_mac
            self.DARTBOARD_UUID = dartboard_uuid
            self.DARTBOARD_ID = dartboard_id
        else:
            self.read_dartboard_config()
            
    def on_connect(self, client, userdata, flags, reason_code, properties=None):
        print(f"Connected with result code {reason_code}")

    # Funktion zum Lesen der Dartboard-Informationen aus der Datei
    def read_dartboard_config(self):
        with open(DARTBOARD_CONFIG_FILE, "r") as file:
            lines = file.readlines()
            self.DARTBOARD_MAC = lines[0].split(";")[1].strip()
            self.DARTBOARD_UUID = lines[1].split(";")[1].strip()
            self.DARTBOARD_ID = lines[2].split(";")[1].strip()

    # Nachricht vom Dartboard empfangen, übersetzen und per MQTT verschicken
    async def handle_notifications(self,sender: int, data: bytearray):
        print(f"Received data from handle {sender}: {data.hex()}")
        value = hex_mapping.get(data.hex(), "999")
        print("Value: "+ value +" empfangen.")
        # HIER WERT HINZUFÜGEN
        self.game.add_throw(value)
        

    async def connect_and_subscribe(self):
        async with BleakClient(self.DARTBOARD_MAC) as dartboard:
            print(f"Connected to {self.DARTBOARD_MAC}")
            # Dienste des Geräts abrufen
            services = dartboard.services
            # Characteristics für Handle Value Notifications finden
            for service in services:
                for char in service.characteristics:
                    if str(char.uuid) == self.DARTBOARD_UUID:
                        notification_char = char
                        break
                else:
                    continue
                break
            else:
                raise RuntimeError("Notification characteristic not found.")

            # Handle Value Notifications aktivieren
            await dartboard.start_notify(notification_char.handle, self.handle_notifications)

            print("Listening for Handle Value Notifications. Press Ctrl+C to exit.")
            await asyncio.sleep(36000)  # Hier kannst du die Laufzeit in Sekunden anpassen oder durch ein Event ersetzen

    def reconnect_bt(self):
        while True:
            print("Reconnecting to Dartboard...")
            loop.run_until_complete(connector.connect_and_subscribe())
            time.sleep(5)


if __name__ == "__main__":

     # Eingaben für Spielername und Anzahl Spiele
    player_name = input("Spielername: ")
    num_games = int(input("Anzahl Spiele: "))

    game = DartGame(player_name, num_games)


    parser = argparse.ArgumentParser(description="Dartboard Connector")
    parser.add_argument("--dartboard_mac", type=str, required=False, help="Dartboard MAC-Adresse")
    #uuid should be 0000ffe1-0000-1000-8000-00805f9b34fb
    parser.add_argument("--dartboard_uuid", type=str, required=False, help="Dartboard Bluetooth UUID")
    parser.add_argument("--dartboard_id", type=str, required=False, help="Dartboard id")

    args = parser.parse_args()

    if(args.dartboard_mac and args.dartboard_uuid and args.dartboard_id):
        connector = DartBotRecorder(args.dartboard_mac, args.dartboard_uuid, args.dartboard_id)
    else:
        connector = DartBotRecorder(game)
    
    loop = asyncio.get_event_loop()
    # Starten Sie den Reconnect-Mechanismus in einem separaten Thread
    threading.Thread(target=connector.reconnect_bt, daemon=True).start()

    run = True;
    while run:
        print("Main Thread läuft noch")
        time.sleep(60)
        if len(game.games) == game.num_games:
            run = False
            print("Programm schließt in 5 Sekunden...")
            time.sleep(5)
    