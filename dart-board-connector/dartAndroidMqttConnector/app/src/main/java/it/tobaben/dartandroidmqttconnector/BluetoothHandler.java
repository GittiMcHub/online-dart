package it.tobaben.dartandroidmqttconnector;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.widget.TextView;

import java.util.UUID;

public class BluetoothHandler implements Runnable{
    private final UUID characteristicUUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    private final UUID serviceUUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private final String deviceAddress;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothDevice device;
    private MqttHandler mqttHandler;
    private Context baseContext;
    private boolean running;
    private MainActivity mainActivity;
    private TextView prompt;

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                updatePrompt("BT-Device connected");
                gatt.discoverServices();
            }
        }
        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattService service = gatt.getService(serviceUUID);
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristics().get(0); // Hier wird angenommen, dass es nur eine Charakteristik gibt
                subscribeToNotifications(gatt, characteristic);
                gatt.readCharacteristic(characteristic);
            }
        }
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] data = characteristic.getValue();
                String value = new String(data);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            // Diese Methode wird aufgerufen, wenn neue Daten von der Charakteristik empfangen werden        }
            byte[] data = characteristic.getValue();
            // Hier können Sie die empfangenen Daten verarbeiten
            String value = DartboardMessageConverter.getMappedString(Byte.toUnsignedInt(data[0]));
            mqttHandler.publish(value);
        }
        @SuppressLint("MissingPermission")
        public void subscribeToNotifications(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            try {
                Thread.sleep(5000); // WTF man. Ohne dem funktioiniert es nicht. Kann das irgendwer erklären?
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            // Zuerst müssen Sie die Benachrichtigungen für diese Charakteristik aktivieren
            gatt.setCharacteristicNotification(characteristic, true);

            // Dann müssen Sie den Descriptoren für diese Charakteristik finden und konfigurieren
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")); // Standard-Client-Konfigurations-Descriptor für Benachrichtigungen
            if (descriptor != null) {
                // Setzen Sie den Wert des Descriptors auf ENABLE_NOTIFICATION_VALUE, um Benachrichtigungen zu aktivieren
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
                updatePrompt("BT-Device subscribed");
            }
        }
    };
    public BluetoothHandler(String mac, MqttHandler mqttHandler, Context basecontext, MainActivity activity, TextView textViewPrompt){
        this.deviceAddress = mac;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mqttHandler = mqttHandler;
        this.baseContext = basecontext;
        this.mainActivity = activity;
        this.prompt = textViewPrompt;
        this.running = true;
        connectToDevice();
    }

    @Override
    public void run() {
        while (this.running){
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    //
                }
        }
    }
    public void stop(){
        this.running=false;
        // Bluetooth disconnecten??
    }

    private void updatePrompt(final String msg){
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                prompt.append(msg);
                prompt.append(";");
            }
        });
    }
    @SuppressLint("MissingPermission")
    public void connectToDevice() {
        updatePrompt("BT-Device TryToConnect");
        this.device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        bluetoothGatt = device.connectGatt(baseContext, true, gattCallback);
    }
}
