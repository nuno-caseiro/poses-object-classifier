package pt.ipleiria.estg.meicm.ssc.poses;

import org.eclipse.paho.android.service.MqttAndroidClient;
public class AppData {
    // static variable single_instance of type Singleton
    private static AppData instance = null;

    // variable of type String
    public String lastPose = "";
    public String actualPose = "";
    public MqttAndroidClient mqttClient = null;
    public int alarmLed = 1029;
    public int alarmBuzz = 1030;

    public int led1 = 1031;
    public int led2 = 1032;
    public int led3 = 1033;
    public int led4 = 1034;
    public int led5 = 1035;

    // private constructor restricted to this class itself
    private AppData()
    {

    }

    // static method to create instance of Singleton class
    public static AppData getInstance()
    {
        if (instance == null)
            instance = new AppData();

        return instance;
    }
}
