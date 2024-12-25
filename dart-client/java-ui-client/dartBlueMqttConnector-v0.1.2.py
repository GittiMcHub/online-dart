import paho.mqtt.client as mqtt
import asyncio
from bleak import BleakClient
import time
import threading
import argparse

MQTT_CONFIG_FILE = "./config/mqttbroker.conf"
DARTBOARD_CONFIG_FILE = "./config/dartboard.conf"
TOPIC_DARTBOARD = "dartboard/"

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

class DartBlueMqttConnector():
    def __init__(self, mqtt_ip=None, mqtt_port=None, mqtt_user=None, mqtt_pw=None, mqtt_qos=None, dartboard_mac=None, dartboard_uuid=None, dartboard_id=None):
        # Wenn Skript mit Argumenten aufgerufen wurde, dann die Werte aus den Argumenten setzen
        if (mqtt_ip and mqtt_port and mqtt_user and mqtt_pw and mqtt_qos and dartboard_mac and dartboard_uuid and dartboard_id):
            self.MQTT_BROKER_IP = mqtt_ip
            self.MQTT_BROKER_PORT = mqtt_port
            self.USERNAME = mqtt_user
            self.PASSWORT = mqtt_pw
            self.QOS = mqtt_qos
            self.DARTBOARD_MAC = dartboard_mac
            self.DARTBOARD_UUID = dartboard_uuid
            self.DARTBOARD_ID = dartboard_id
        else:
            self.read_mqtt_config()
            self.read_dartboard_config()
            
        self.publishTopic = TOPIC_DARTBOARD + str(self.DARTBOARD_ID)
        self.mqttc = mqtt.Client(protocol=mqtt.MQTTv311)
        self.mqttc.username_pw_set(self.USERNAME, self.PASSWORT)
        self.mqttc.on_connect = self.on_connect

    def on_connect(self, client, userdata, flags, reason_code, properties=None):
        print(f"Connected with result code {reason_code}")

    # Funktion zum Lesen der MQTT-Broker-Informationen aus der Datei
    def read_mqtt_config(self):
        with open(MQTT_CONFIG_FILE, "r") as file:
            lines = file.readlines()
            self.MQTT_BROKER_IP = lines[0].split(":")[1].strip()
            self.MQTT_BROKER_PORT = int(lines[1].split(":")[1].strip())
            self.USERNAME = lines[2].split(":")[1].strip()
            self.PASSWORT = lines[3].split(":")[1].strip()
            self.QOS = lines[4].split(":")[1].strip()

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
        self.mqttc.publish(self.publishTopic, value, qos=self.QOS)
        print("Value: "+ value +" published.")

    async def connect_and_subscribe(self):
        async with BleakClient(self.DARTBOARD_MAC) as dartboard:
            print(f"Connected to {self.DARTBOARD_MAC}")
            # Dienste des Geräts abrufen
            services = await dartboard.get_services()
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
            await asyncio.sleep(3600)  # Hier kannst du die Laufzeit in Sekunden anpassen oder durch ein Event ersetzen
    
    def reconnect_mqtt(self):
        #self.mqttc.loop_forever()
        while True:
            if not self.mqttc.is_connected():
                print("Reconnecting to MQTT broker...")
                self.mqttc.connect(self.MQTT_BROKER_IP, self.MQTT_BROKER_PORT, 60)
                self.mqttc.loop_forever()
            time.sleep(10)

    def reconnect_bt(self):
        while True:
            print("Reconnecting to Dartboard...")
            loop.run_until_complete(connector.connect_and_subscribe())
            time.sleep(5)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="MQTT and Dartboard Connector")
    parser.add_argument("--mqttbrokerip", type=str, required=False, help="MQTT broker hostname or IP address")
    parser.add_argument("--mqttbrokerport", type=int, required=False, help="MQTT broker port")
    parser.add_argument("--mqttuser", type=str, required=False, help="MQTT broker username")
    parser.add_argument("--mqttpassword", type=str, required=False, help="MQTT broker password")
    parser.add_argument("--mqttqos", type=str, required=False, help="MQTT broker Quality of Service")

    parser.add_argument("--dartboard_mac", type=str, required=False, help="Dartboard MAC-Adresse")
    #uuid should be 0000ffe1-0000-1000-8000-00805f9b34fb
    parser.add_argument("--dartboard_uuid", type=str, required=False, help="Dartboard Bluetooth UUID")
    parser.add_argument("--dartboard_id", type=str, required=False, help="Dartboard id")

    args = parser.parse_args()

    if(args.mqttbrokerip and args.mqttbrokerport and args.mqttuser and args.mqttpassword and args.mqttqos and args.dartboard_mac and args.dartboard_uuid and args.dartboard_id):
        connector = DartBlueMqttConnector(args.mqttbrokerip, args.mqttbrokerport, args.mqttuser, args.mqttpassword, args.mqttqos, args.dartboard_mac, args.dartboard_uuid, args.dartboard_id)
    else:
        connector = DartBlueMqttConnector()
    
    loop = asyncio.get_event_loop()
    connector = DartBlueMqttConnector()
    # Starten Sie den Reconnect-Mechanismus in einem separaten Thread
    threading.Thread(target=connector.reconnect_mqtt, daemon=True).start()
    threading.Thread(target=connector.reconnect_bt, daemon=True).start()

    while True:
        print("Main Thread läuft noch")
        time.sleep(60)
    