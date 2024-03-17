from typing import Optional
from bleak import BleakClient
import asyncio


# Function to create a BleakClient and connect it to the address of the light's Bluetooth receiver
async def run(address):
    client = BleakClient(address)
    print("Connecting")
    await client.connect()
    print(f"Connected")
    services = await client.get_services()
    for service in services:
        # Iterate and subsequently print the characteristic UUID
        for characteristic in service.characteristics:
            print(f"Characteristic: {characteristic.uuid}")
    
    print("Please test these characteristics to identify the correct one")

if __name__ == "__main__":
    loop = asyncio.get_event_loop()
    address = "64:CF:D9:3A:E0:C1"
    loop.run_until_complete(run(address))