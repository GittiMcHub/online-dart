package it.tobaben.dartandroidmqttconnector;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 123;
    private Button buttonBT,buttonMQTT;
    private TextView textViewPrompt;
    private MqttHandler mqttHandler;
    private BluetoothHandler bluetoothHandler;
    private MainActivity mainActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Überprüfen, ob die Berechtigungen vorhanden sind
        if (!checkPermissions()) {
            // Berechtigungen anfordern
            requestPermissions();
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        mainActivity = this;
        textViewPrompt = (TextView)findViewById(R.id.textViewPrompt);
        buttonBT = (Button)findViewById(R.id.buttonBt);
        buttonMQTT = (Button)findViewById(R.id.buttonMqtt);
        buttonMQTT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText urlText = (EditText)findViewById(R.id.editTextMqttIp);
                String url = urlText.getText().toString();
                EditText userText = (EditText)findViewById(R.id.editTextMqttName);
                String user = userText.getText().toString();
                EditText pwText = (EditText)findViewById(R.id.editTextMqttPassword);
                String pw = pwText.getText().toString();
                EditText dartboardIdText = (EditText)findViewById(R.id.editTextDartId);
                String dartboardId = dartboardIdText.getText().toString();
                mqttHandler = new MqttHandler(url,user,pw, dartboardId,mainActivity,textViewPrompt);
                Thread mqttThread = new Thread(mqttHandler);
                mqttThread.start();
                buttonBT.setVisibility(View.VISIBLE);
            }
        });

        buttonBT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText macText = (EditText)findViewById(R.id.editTextMac);
                String macAdresse = macText.getText().toString();

                bluetoothHandler = new BluetoothHandler(macAdresse,mqttHandler,getBaseContext(),mainActivity,textViewPrompt);
                Thread btThread = new Thread(bluetoothHandler);
                btThread.start();
                buttonMQTT.setVisibility(View.INVISIBLE);
                buttonBT.setVisibility(View.INVISIBLE);
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        System.out.println("onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        bluetoothHandler.stop();
        // MQTT-Verbindung trennen, wenn nicht mehr benötigt
        mqttHandler.stop();

    }
    private void updatePrompt(final String msg){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textViewPrompt.append(msg);
                textViewPrompt.append(";");
            }
        });
    }
    @SuppressLint("ObsoleteSdkInt")
    private boolean checkPermissions() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this,
                            android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this,
                            android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this,
                            android.Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this,
                            android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED;
        }
    }
    @SuppressLint("ObsoleteSdkInt")
    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this,
                    new String[]{ android.Manifest.permission.BLUETOOTH,
                            android.Manifest.permission.BLUETOOTH_ADMIN,
                            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                            Manifest.permission.INTERNET,
                            Manifest.permission.ACCESS_NETWORK_STATE},
                    PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{ android.Manifest.permission.BLUETOOTH,
                            android.Manifest.permission.BLUETOOTH_ADMIN},
                    PERMISSION_REQUEST_CODE);
        }
    }
}