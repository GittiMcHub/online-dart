import RPi.GPIO as GPIO
import numpy as np
import time

GPIO.setmode(GPIO.BCM)

dartPinArray_outputs = [5,6,12,13,16,17,18,19]
dartPinArray_inputs = [20,21,22,23,24,25,26,27]

dartMatrix = np.zeros((len(dartPinArray_outputs),len(dartPinArray_inputs)))

werteMatrix = [
[111,112,113,114,115,116,117,118],
[221,222,223,224,225,226,227,228],
[331,332,333,334,335,336,337,338],
[441,442,443,444,445,446,447,448],
[551,552,553,554,555,556,557,558],
[661,662,663,664,665,666,667,668],
[771,772,773,774,775,776,777,778],
[881,882,993,884,995,886,887,888]
]


class Gpio_auslesen:

    def __init__(self):
        self.setup()
        print("starte Main")
        self.main()

    def main(self):
        while True:
            try:
                ergebnis = self.wurfAuswerten()
                if (ergebnis != 999):
                    print(str(ergebnis))
                    time.sleep(2)

            except KeyboardInterrupt:
                GPIO.setmode(GPIO.BCM)
                GPIO.cleanup()

    def setup(self):
        # Set Outputs
        for pin in dartPinArray_outputs:
            GPIO.setup(pin, GPIO.OUT)

        # Set Inputs
        for pin in dartPinArray_inputs:
            GPIO.setup(pin, GPIO.IN, pull_up_down=GPIO.PUD_UP)

    def wurfAuswerten(self):
        hitDetected = False

        for zeile in range(len(dartPinArray_outputs)):
            # setze alle Outputs auf HIGH bis auf einen
            for pin in dartPinArray_outputs:
                GPIO.output(pin, GPIO.HIGH)
            # Ziehe einen Ausgang auf LOW, damit bei einem Treffer ein Input auf LOW gezogen wird
            GPIO.output(dartPinArray_outputs[zeile], GPIO.LOW)

            for spalte in range(len(dartPinArray_inputs)):
                # Lese GPIO inputs ein und schreibe 1 oder 0 in dartMatrix
                dartMatrix[zeile][spalte] = GPIO.input(dartPinArray_inputs[spalte])

                # Wenn GPIO input auf LOW, dann wurde das  Feld getroffen
                if (dartMatrix[zeile][spalte] == 0):
                    hitDetected = True
                    print("Wurf detektiert")
                    ergebnis = werteMatrix[zeile][spalte]
                    return ergebnis
        return 999

if __name__ == '__main__':
    Gpio_auslesen()
