import json
import tkinter as tk
from paho.mqtt import client as mqtt_client

# MQTT-Broker Einstellungen
broker = '10.0.10.1'  # Ändere das entsprechend deinem MQTT-Broker
port = 1883
user = 'dartboard'
password = 'smartness'
topic = "status/gameUpdate"

# Punktestand-Variable
punktestand = '???'

# Funktion zur dynamischen Anpassung der Schriftgröße
def resize_font(event=None):
    screen_width = root.winfo_width()
    screen_height = root.winfo_height()

    # Berechnung der Schriftgröße basierend auf Bildschirmgröße
    font_size = min(screen_width, screen_height) // 2  # Passe den Divisor nach Bedarf an
    punktestand_label.config(font=("Helvetica", font_size))

# Funktion zur Aktualisierung des Punktestands in der GUI
def update_punktestand(new_score):
    global punktestand
    punktestand = new_score
    punktestand_label.config(text=punktestand)
    root.update_idletasks()
    resize_font() # Schriftgröße anpassen falls nötig

# MQTT Callback für den Empfang von Nachrichten
def on_message(client, userdata, msg):
    try:
        data = json.loads(msg.payload)
        if 'currentPlayer' in data and 'punktestand' in data['currentPlayer']:
            new_score = data['currentPlayer']['punktestand']
            update_punktestand(new_score)
    except json.JSONDecodeError:
        print("Fehler: Ungültiges JSON-Format")

# MQTT-Verbindung herstellen und abonnieren
def connect_mqtt():
    client = mqtt_client.Client()
    client.username_pw_set(user,password)
    client.on_connect = lambda client, userdata, flags, rc: client.subscribe(topic)
    client.on_message = on_message

    client.connect(broker, port)
    return client

# GUI mit tkinter
root = tk.Tk()
root.attributes('-fullscreen', True)  # Vollbildmodus
root.configure(bg="black")

punktestand_label = tk.Label(root, text=str(punktestand), font=("Helvetica", 200), fg="white", bg="black")
punktestand_label.pack(expand=True)

root.bind('<Configure>',resize_font)

# MQTT Client initialisieren und starten
client = connect_mqtt()
client.loop_start()

# tkinter-Loop starten
root.mainloop()

# Stoppe MQTT-Loop, wenn die GUI geschlossen wird
client.loop_stop()
client.disconnect()