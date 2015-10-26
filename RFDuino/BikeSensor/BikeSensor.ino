/*

Author: Nils Schwabe
Last change: 19.08.2015

BikeSensor
----------

Code to read sensor data from BME280 and send it via BLE
to the corresponding Android app.

Some parts of the code were adapted from the examples of
the BME280 MOD-1022 demo from www.embeddedadventures.com

Android app availible on Github: <missing link>
Board layout availible on Github: <missing link>
*/


#include <RFduinoBLE.h>
#include <BME280_MOD-1022.h>
#include <Wire.h>

#define MAX 100
#define DELAY 3

int data[MAX];
int n = 0;

boolean isConnected = false;

void setup() {
  // setup i2c
  Wire.begin();

  // setup sensor
  // need to read the NVM compensation parameters
  BME280.readCompensationParams();
  
  // Need to turn on 1x oversampling, default is os_skipped, which means it doesn't measure anything
  BME280.writeOversamplingPressure(os1x);  // 1x over sampling (ie, just one sample)
  BME280.writeOversamplingTemperature(os1x);
  BME280.writeOversamplingHumidity(os1x);

  // setup BLE
  RFduinoBLE.advertisementData = "m";
  RFduinoBLE.deviceName = "Bike Sensor";
  RFduinoBLE.advertisementInterval = 100;  // ms
  RFduinoBLE.txPowerLevel = -8;  // power  -20, -16, -8, -4, 0, +4
  RFduinoBLE.begin();

  Serial.begin(9600);
  Serial.println("Welcome to BikeSensor");
  Serial.println("Initialization done.");
}

void loop() {
  if (isConnected) {
    Serial.println("isConnected.. sendData()");
    sendData();
  }
  performMeasurements();
  //RFduino_ULPDelay(INFINITE);  // Stay in ultra low power mode until interrupt from the BLE or pinWake()
  delay(DELAY * 1000);
}

void RFduinoBLE_onConnect(){
  Serial.println("Connected");
  isConnected = true;
}

void RFduinoBLE_onDisconnect() {
  Serial.println("Disconnected");
  isConnected = false;
}

void sendData() {
  for (int i = 0; i < n; i++) {
    int waiting = 0;
    while (!RFduinoBLE.sendInt(data[i]) && waiting < 10000) {
      waiting++;
    }
  }
  Serial.print("DONE sending ");
  Serial.print(n);
  Serial.println(" chunks");
  n = 0;
}

void performMeasurements() {
  Serial.println("Measuring...");
  // example of a forced sample.  After taking the measurement the chip goes back to sleep
  BME280.writeMode(smForced);
  while (BME280.isMeasuring());
  BME280.readMeasurements();
  if (n + 1 < MAX) {
    data[n] = millis();
    data[n + 1] = BME280.getTemperatureMostAccurate() * 10.0;
    Serial.print(data[n]/1000);
    Serial.print("s - ");
    Serial.print("Temp: ");
    Serial.println(data[n+1]);
    n += 2;
  } else {
    n = 0;
    // buffer is full... TODO
  }
}

