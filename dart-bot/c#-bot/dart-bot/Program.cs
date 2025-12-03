using dartBot;
using MQTTnet;
using MQTTnet.Protocol;
using System;
using System.Text;
using System.Text.Json;
class Program
{
    static async Task Main()
    {
        Console.WriteLine("Hello, World!");
        string broker = "10.0.1.1";
        int port = 1883;
        string clientId = "Bot XYZ";
        string topic = "status/gameUpdate";
        string username = "dartboard";
        string password = "smartness";
        string filePath = "./botwurf.json";
        string dartboardTopic = "dartboard/2";

        byte[]? fileRead = await File.ReadAllBytesAsync(filePath);
        Bot? botWuerfe = JsonSerializer.Deserialize<Bot>(fileRead);

        // Create a MQTT client factory
        var factory = new MqttClientFactory();

        // Create a MQTT client instance
        var mqttClient = factory.CreateMqttClient();

        // Create MQTT client options
        var options = new MqttClientOptionsBuilder()
            .WithTcpServer(broker, port) // MQTT broker address and port
            .WithCredentials(username, password) // Set username and password
            .WithClientId(clientId)
            .WithCleanSession()
            .Build();

        // Connect to the broker
        var connectResult = await mqttClient.ConnectAsync(options);
        Console.WriteLine("Connected to MQTT broker!");

        if (connectResult.ResultCode == MqttClientConnectResultCode.Success)
        {
            Console.WriteLine("Connected to MQTT broker successfully.");

            // Subscribe to a topic
            await mqttClient.SubscribeAsync(topic);

            // Callback function when a message is received
            mqttClient.ApplicationMessageReceivedAsync += e =>
            {
                HandleMessage(Encoding.UTF8.GetString(e.ApplicationMessage.Payload), botWuerfe, dartboardTopic, mqttClient);

                return Task.CompletedTask;
            };


            // Anwendung am Leben halten
            while (true)
            {
                Task.Delay(1000).Wait(); // wartet 1 Sekunde
            }

            // Sauber trennen beim Beenden
            await mqttClient.DisconnectAsync();
        }
        else
        {
            Console.WriteLine($"Failed to connect to MQTT broker. Result code: {connectResult.ResultCode}");
        }
    }


    static void HandleMessage(string message, Bot bot, string pubTopic, IMqttClient client)
    {

        string json = message;
        // JSON in GameMessage deserialisieren
        GameMessage? game = JsonSerializer.Deserialize<GameMessage>(json);

        if (game != null && game.currentPlayer.name.Equals(bot.name) && game.currentPlayer.freieWuerfe == 3)
        {
            if(bot.spielIdMerker != game.spielId)
            {
                bot.zeigerSpielzug = 0;
                bot.spielIdMerker = game.spielId;
            }
            Console.WriteLine($"Botname: {game.currentPlayer.name} Spielzug gestartet.");

            // Alle 3 Würfe senden
            for (int wurf = 0; wurf < 3; wurf++)
            {
                int spiel = game.spielId - 1;
                int spielzug = bot.zeigerSpielzug;
                Console.WriteLine($"Bot Spielzug Nr.: {bot.zeigerSpielzug + 1}, WurfNr: {wurf + 1}, Wert: {bot.spiele[spiel][spielzug][wurf]}");
                var pubMessage = new MqttApplicationMessageBuilder()
                    .WithTopic(pubTopic)
                    .WithPayload(bot.spiele[game.spielId - 1][bot.zeigerSpielzug][wurf])
                    .WithQualityOfServiceLevel(MqttQualityOfServiceLevel.AtLeastOnce)
                    .Build();
                client.PublishAsync(pubMessage);

                Task.Delay(1000).Wait();
            }
            bot.zeigerSpielzug++;

            // Spielzug beenden
            Console.WriteLine($"Botname: {game.currentPlayer.name} Spielzug beendet.");
            var pubMessageSpielzugEnde = new MqttApplicationMessageBuilder()
                   .WithTopic(pubTopic)
                   .WithPayload("999")
                   .WithQualityOfServiceLevel(MqttQualityOfServiceLevel.AtLeastOnce)
                   .Build();
            client.PublishAsync(pubMessageSpielzugEnde);
        }
    }
}
