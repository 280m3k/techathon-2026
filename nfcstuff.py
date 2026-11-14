import network
import espnow
from machine import Pin

sta = network.WLAN(network.STA_IF)
sta.active(True)

leds = [Pin(21, Pin.OUT), Pin(19, Pin.OUT), Pin(22, Pin.OUT)]
leds[0].value(0)
leds[1].value(0)
leds[2].value(0)

e = espnow.ESPNow()
e.active(True)

print("Waiting for data...")
previous_msg = ""

while True:
    host, msg = e.recv()# blocks until a message arrives (or timeout)
    if msg == previous_msg:
        value = int.from_bytes(msg, "little")
        leds[0].value(0)
        leds[1].value(0)
        leds[2].value(0)
        for x in range(value):
            leds[x].value(1)
        print("Received:", value)
    previous_msg = msg