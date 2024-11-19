import java.io.File;
import java.io.ObjectInputFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ConfigReader {
    private final String broker;
    private String brokerIp;
    private String brokerPort;
    private String user;
    private String password;
    private String qos;
    private final String clientID = "server";
    private final String topic = "dartboard/#";

    private final String filePath = System.getProperty("user.dir") + File.separator +"mqttbroker.conf";

    public ConfigReader(){
        try{
            Properties properties = readPropertiesFromFile(this.filePath);
            this.brokerIp = properties.getProperty("mqtt_broker_ip");
            this.brokerPort = properties.getProperty("mqtt_broker_port");
            this.user = properties.getProperty("mqtt_username");
            this.password = properties.getProperty("mqtt_password");
            this.qos = properties.getProperty("mqtt_qos");
        }catch (Exception ex){
            ex.printStackTrace();
        }
        this.broker = "tcp://" + brokerIp + ":" + brokerPort;
    }


    private Properties readPropertiesFromFile(String filePath) throws Exception {
        Path path = Paths.get(filePath);
        Properties properties = new Properties();

        if (Files.exists(path)) {
            properties.load(Files.newBufferedReader(path));
        } else {
            throw new RuntimeException("Config file not found at: " + filePath);
        }
        return properties;
    }


    public String getBroker() {
        return broker;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public String getClientID() {
        return clientID;
    }

    public String getTopic() {
        return topic;
    }
    public int getQos(){
        return Integer.parseInt(this.qos);
    }
}
