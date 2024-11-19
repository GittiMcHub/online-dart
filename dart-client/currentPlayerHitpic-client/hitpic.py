import tkinter as tk
from tkinter import ttk
import numpy as np
import matplotlib.pyplot as plt
from matplotlib.backends.backend_tkagg import FigureCanvasTkAgg
from paho.mqtt import client as mqtt_client
import json


# MQTT-Broker Einstellungen
broker = '10.0.14.68'  # Ändere das entsprechend deinem MQTT-Broker
port = 1883
user = 'dartboard'
password = 'smartness'
topic = "status/gameUpdate"

def plot_dartboard(hit_distribution):
    # Farben (von Weiß bis Dunkelblau)
    cmap = plt.cm.Blues
    norm = plt.Normalize(vmin=0, vmax=np.max(hit_distribution))
    
    fig, ax = plt.subplots(figsize=(6, 6))
    ax.set_xlim(-1.2, 1.2)
    ax.set_ylim(-1.2, 1.2)
    ax.axis('off')  # Keine Achsen

    # Radien der Ringe
    outer_radius = 1.0  # Äußerer Radius der Dartscheibe
    double_ring_width = 0.05  # Double-Feld Breite
    single_outer_width = 0.25  # Äußeres Single-Feld
    triple_ring_width = 0.05  # Triple-Feld Breite
    single_inner_width = 0.25  # Inneres Single-Feld
    bull_radius = 0.1  # Äußeres Bullseye
    inner_bull_radius = 0.05  # Inneres Bullseye

    # Double Ring
    double_outer_radius = outer_radius
    double_inner_radius = double_outer_radius - double_ring_width

    # Single Outer Ring
    single_outer_outer_radius = double_inner_radius
    single_outer_inner_radius = single_outer_outer_radius - single_outer_width

    # Triple Ring
    triple_outer_radius = single_outer_inner_radius
    triple_inner_radius = triple_outer_radius - triple_ring_width

    # Single Inner Ring (reicht bis zum Bullseye)
    single_inner_outer_radius = triple_inner_radius
    single_inner_inner_radius = bull_radius  # Direkter Anschluss ans Bullseye

    # Ringe und Segmente zeichnen
    radii = [
        (double_outer_radius, double_inner_radius),
        (single_outer_outer_radius, single_outer_inner_radius),
        (triple_outer_radius, triple_inner_radius),
        (single_inner_outer_radius, single_inner_inner_radius),
    ]

    # Dartscheibennummern (typische Reihenfolge)
    dart_numbers = [20, 5, 12, 9, 14, 11, 8, 16, 7, 19, 3, 17, 2, 15, 10, 6, 13, 4, 18, 1]
    dart_index = [20, 5, 12, 9, 14, 11, 8, 16, 7, 19, 3, 17, 2, 15, 10, 6, 13, 4, 18, 1,0]

    for i, (radius_outer, radius_inner) in enumerate(radii):
        for j in range(20):  # 20 Segmente
            # Rotationskorrektur: Segmente um 9° drehen und zusätzlich um 90° verschieben
            theta1 = j * 18 - 9 + 90
            theta2 = (j + 1) * 18 - 9 + 90
            indexInArray = dart_index[j]-1  # Finde den richtigen Index für die Dartscheibe
            
            # Behandlung des inneren und äußeren Single-Felds
            if i == 1:  # Single-Felder, außen und innen gleich behandeln
                var = int(hit_distribution[0,indexInArray])
                color = cmap(norm(hit_distribution[0, indexInArray]))  # Single-Felder in einem Array
            elif i == 0:  # Double-Felder
                var = int(hit_distribution[1,indexInArray])
                color = cmap(norm(hit_distribution[1, indexInArray]))  # Double-Felder
            elif i == 3: # single Inner Felder
                color = cmap(norm(hit_distribution[0, indexInArray]))
            else:  # Triple-Felder
                var = int(hit_distribution[2,indexInArray])
                color = cmap(norm(hit_distribution[2, indexInArray]))  # Triple-Felder

            wedge = plt.Polygon(
                [
                    (radius_inner * np.cos(np.radians(theta1)), radius_inner * np.sin(np.radians(theta1))),
                    (radius_outer * np.cos(np.radians(theta1)), radius_outer * np.sin(np.radians(theta1))),
                    (radius_outer * np.cos(np.radians(theta2)), radius_outer * np.sin(np.radians(theta2))),
                    (radius_inner * np.cos(np.radians(theta2)), radius_inner * np.sin(np.radians(theta2))),
                ],
                facecolor=color,
                edgecolor='black',
                linewidth=0.5,
            )
            ax.add_patch(wedge)

    # Äußeres Bullseye
    bull_color = cmap(norm(hit_distribution[0, 20]))  # Bullseye im innersten Bereich (Single)
    ax.add_patch(plt.Circle((0, 0), bull_radius, color=bull_color, ec='black'))

    # Inneres Bullseye
    inner_bull_color = cmap(norm(hit_distribution[1, 20]))  # Bullseye im innersten Bereich (Single)
    ax.add_patch(plt.Circle((0, 0), inner_bull_radius, color=inner_bull_color, ec='black'))

    # Zahlen rund um die Dartscheibe
    for j, num in enumerate(dart_numbers):
        angle = np.radians(j * 18 + 90)  # 9° Rotation und zusätzlich 90° verschieben
        x = 1.1 * np.cos(angle)
        y = 1.1 * np.sin(angle)
        ax.text(
            x, y, str(num),
            ha='center', va='center',
            fontsize=10, fontweight='bold'
        )

    return fig


def update_chart(hit_array):
    global canvas
    # Hart codiertes Array mit zufälligen Werten (21 Felder für Single, Double und Triple)
    # Array für Testzwecke
    hit_distribution = np.array([
        [1, 2, 3, 4, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 100],  # Single-Felder (1-20 und bull)
        [99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 50],  # Double-Felder (1-20 und bullseye)
        [99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 50],   # Triple-Felder (1-20)
    ])
    fig = plot_dartboard(hit_array)
    
    # Aktualisiere die Canvas
    if canvas:
        canvas.get_tk_widget().destroy()
    canvas = FigureCanvasTkAgg(fig, master=frame)
    canvas.draw()
    canvas.get_tk_widget().pack()

# MQTT Callback für den Empfang von Nachrichten
def on_message(client, userdata, msg):
    try:
        data = json.loads(msg.payload)
        if 'currentPlayer' in data and 'punktestand' in data['currentPlayer']:
            getroffene_felder = data["currentPlayer"]["statistik"]["getroffeneFelder"]
            hit_array = np.array(getroffene_felder)
            update_chart(hit_array)
    except json.JSONDecodeError:
        print("Fehler: Ungültiges JSON-Format")

# MQTT-Verbindung herstellen und abonnieren
def connect_mqtt():
    client = mqtt_client.Client()
    client.username_pw_set(user, password)
    client.on_connect = lambda client, userdata, flags, rc: client.subscribe(topic)
    client.on_message = on_message

    client.connect(broker, port)
    return client


# Haupt-GUI
root = tk.Tk()
root.title("Dartscheiben-Trefferverteilung")

frame = ttk.Frame(root, padding="10")
frame.grid(row=0, column=0, sticky="nsew")

canvas = None

# MQTT Client initialisieren und starten
client = connect_mqtt()
client.loop_start()

# Starte GUI
root.mainloop()

# Stoppe MQTT-Loop, wenn die GUI geschlossen wird
client.loop_stop()
client.disconnect()
