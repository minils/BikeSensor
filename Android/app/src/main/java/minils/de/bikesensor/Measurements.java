package minils.de.bikesensor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;

/**
 * Created by nils on 23.07.15.
 */
public class Measurements {

    MainActivity mainActivity;

    private int count = 0;
    private int[] temp = {0, 0};

    private static Measurements measurements = null;

    ArrayList<Data> temperature;
    ArrayList<Data> humidity;
    ArrayList<Data> pressure;

    public static Measurements getMeasurements(MainActivity mainActivity) {
        if (measurements == null) {
            return new Measurements(mainActivity);
        } else {
            return measurements;
        }
    }

    private Measurements(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
        temperature = new ArrayList<>();
        humidity = new ArrayList<>();
        pressure = new ArrayList<>();
    }

    public void interpretIncome(byte[] bytes) {
        temp[count] = HexAsciiHelper.byteArrayToInt(bytes);
        if (count == 1) {
            // we got timestamp and temperature
            long timestamp = (long) temp[0];
            int value = temp[1];
            Data data = new Data(timestamp, value);
            // TODO: check if data is temperature, humidity or pressure
            temperature.add(data);
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd.MM.yy");
            mainActivity.updateStatus(temp[0] + ": " + HexAsciiHelper.getTemperatureString(bytes));
            count = 0;
            mainActivity.incAmount();
        } else {
            count++;
        }
    }

    class Data {
        long timestamp;
        int value;

        long getTimestamp() {
            return timestamp;
        }

         int getValue() {
             return value;
         }

        Data(long timestamp, int value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }
}
