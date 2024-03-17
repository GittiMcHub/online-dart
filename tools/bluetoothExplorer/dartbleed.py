import asyncio
from bleak import BleakClient

# Bluetooth-Adresse der Dartscheibe
dartboard_mac = "64:CF:D9:3A:E0:C1"

# UUID für die Handle Value Notification-Charakteristik
notification_uuid = "0000ffe1-0000-1000-8000-00805f9b34fb"

async def handle_notifications(sender: int, data: bytearray):
    print(f"Received data from handle {sender}: {data.hex()}")

async def connect_and_subscribe():
    async with BleakClient(dartboard_mac) as dartboard:
        print(f"Connected to {dartboard_mac}")

        # Dienste des Geräts abrufen
        services = await dartboard.get_services()

        # Characteristics für Handle Value Notifications finden
        for service in services:
            for char in service.characteristics:
                if str(char.uuid) == notification_uuid:
                    notification_char = char
                    break
            else:
                continue
            break
        else:
            raise RuntimeError("Notification characteristic not found.")

        # Handle Value Notifications aktivieren
        await dartboard.start_notify(notification_char.handle, handle_notifications)

        print("Listening for Handle Value Notifications. Press Ctrl+C to exit.")
        await asyncio.sleep(3600)  # Hier kannst du die Laufzeit in Sekunden anpassen oder durch ein Event ersetzen

if __name__ == "__main__":
    loop = asyncio.get_event_loop()
    loop.run_until_complete(connect_and_subscribe())