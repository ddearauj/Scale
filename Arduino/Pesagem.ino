/* The library used for arduino  https://github.com/bogde/HX711<br>// LCD can also be used instead of serial */
#include "HX711.h"
//HX711.DOUT  - pin 10
//HX711.PD_SCK - pin 11
HX711 scale(A0, A1); /* parameter "gain" is ommited; the default value 128 is used by the  library  library*/
void setup() 
{
  Serial.begin(9600);  
// print the average of 5 readings from the ADC minus tare weight (not set) divided by the SCALE parameter (not set yet)  
  scale.set_scale(2280.f);  
 // this value is obtained by calibrating the scale with known weights; see the README for details
  scale.tare();   // reset the scale to 0

}

void loop() 
{
  //Serial.println("Weight :");
    float leitura = scale.get_units()*0.1;
    float peso = -(leitura-0.1127)*(7.880221);
    String weight = "0" + String(peso);
    Serial.print(weight);
  scale.power_down();             // put the ADC in sleep mode
  delay(1000);
  scale.power_up();
}
