package it.tobaben.dartandroidmqttconnector;
import java.util.HashMap;
public class DartboardMessageConverter {
    private static final HashMap<Integer, String> hexMapping = new HashMap<>();

    static {
        // Felder Außen
        hexMapping.put(1, "101");
        hexMapping.put(2, "102");
        hexMapping.put(3, "103");
        hexMapping.put(4, "104");
        hexMapping.put(5, "105");
        hexMapping.put(6, "106");
        hexMapping.put(7, "107");
        hexMapping.put(8, "108");
        hexMapping.put(9, "109");
        hexMapping.put(10, "110");
        hexMapping.put(11, "111");
        hexMapping.put(12, "112");
        hexMapping.put(13, "113");
        hexMapping.put(14, "114");
        hexMapping.put(15, "115");
        hexMapping.put(16, "116");
        hexMapping.put(17, "117");
        hexMapping.put(18, "118");
        hexMapping.put(19, "119");
        hexMapping.put(20, "120");
        hexMapping.put(21, "101");
        hexMapping.put(22, "102");
        hexMapping.put(23, "103");
        hexMapping.put(24, "104");
        hexMapping.put(25, "105");
        hexMapping.put(26, "106");
        hexMapping.put(27, "107");
        hexMapping.put(28, "108");
        hexMapping.put(29, "109");
        hexMapping.put(30, "110");
        hexMapping.put(31, "111");
        hexMapping.put(32, "112");
        hexMapping.put(33, "113");
        hexMapping.put(34, "114");
        hexMapping.put(35, "115");
        hexMapping.put(36, "116");
        hexMapping.put(37, "117");
        hexMapping.put(38, "118");
        hexMapping.put(39, "119");
        hexMapping.put(40, "120");
        hexMapping.put(41, "201");
        hexMapping.put(42, "202");
        hexMapping.put(43, "203");
        hexMapping.put(44, "204");
        hexMapping.put(45, "205");
        hexMapping.put(46, "206");
        hexMapping.put(47, "207");
        hexMapping.put(48, "208");
        hexMapping.put(49, "209");
        hexMapping.put(50, "210");
        hexMapping.put(51, "211");
        hexMapping.put(52, "212");
        hexMapping.put(53, "213");
        hexMapping.put(54, "214");
        hexMapping.put(55, "215");
        hexMapping.put(56, "216");
        hexMapping.put(57, "217");
        hexMapping.put(58, "218");
        hexMapping.put(59, "219");
        hexMapping.put(60, "220");
        hexMapping.put(61, "301");
        hexMapping.put(62, "302");
        hexMapping.put(63, "303");
        hexMapping.put(64, "304");
        hexMapping.put(65, "305");
        hexMapping.put(66, "306");
        hexMapping.put(67, "307");
        hexMapping.put(68, "308");
        hexMapping.put(69, "309");
        hexMapping.put(70, "310");
        hexMapping.put(71, "311");
        hexMapping.put(72, "312");
        hexMapping.put(73, "313");
        hexMapping.put(74, "314");
        hexMapping.put(75, "315");
        hexMapping.put(76, "316");
        hexMapping.put(77, "317");
        hexMapping.put(78, "318");
        hexMapping.put(79, "319");
        hexMapping.put(80, "320");

        hexMapping.put(81, "125"); // Bull
        hexMapping.put(82, "225"); // Bullseye
        hexMapping.put(101, "999"); // Next Player
    }


    public static String getMappedString(int value) {
        return hexMapping.getOrDefault(value, "Unknown");
    }

}

