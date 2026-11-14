import json
import math
import struct
import time

import bluetooth
import network
import espnow
import uasyncio as asyncio
from machine import I2C, Pin

# =====================================================================
#  MPU6050 / SWING CONTROLLER (ESP-NOW)
# =====================================================================
# ---- Wiring (ESP32 default I2C pins) ----
# MPU6050 VCC -> 3V3, GND -> GND, SDA -> GPIO21, SCL -> GPIO22
# AD0 -> GND gives address 0x68 (AD0 -> 3V3 gives 0x69)

MPU_ADDR = 0x68
REG_PWR_MGMT_1 = 0x6B
REG_ACCEL_CONFIG = 0x1C
REG_GYRO_CONFIG = 0x1B
REG_ACCEL_XOUT_H = 0x3B
REG_WHO_AM_I = 0x75

ACCEL_SCALE = 16384.0  # LSB/g for +/-2g
GYRO_SCALE = 65.5      # LSB/(deg/s) for +/-500 deg/s (swings saturate +/-250)

# ---- Swing detection tuning ----
SWING_THRESHOLD = 300.0   # deg/s: rotation speed that counts as a swing
SWING_RESET = 100.0       # deg/s: must drop below this before the next swing
SWING_COOLDOWN_MS = 600   # minimum time between counted swings

SAMPLE_MS = 10            # sensor polling (fast, so swing peaks aren't missed)
SEND_INTERVAL_MS = 100    # periodic ESP-NOW update rate
BROADCAST = b"\xff\xff\xff\xff\xff\xff"


class MPU6050:
    def __init__(self, i2c, addr=MPU_ADDR):
        self.i2c = i2c
        self.addr = addr
        self.gyro_offset = (0.0, 0.0, 0.0)

        if self.addr not in self.i2c.scan():
            raise OSError("MPU6050 not found at 0x%02X - check wiring" % self.addr)

        # Wake the sensor (it starts in sleep mode)
        self.i2c.writeto_mem(self.addr, REG_PWR_MGMT_1, b'\x00')
        time.sleep_ms(100)
        # +/-2g accelerometer, +/-500 deg/s gyroscope
        self.i2c.writeto_mem(self.addr, REG_ACCEL_CONFIG, b'\x00')
        self.i2c.writeto_mem(self.addr, REG_GYRO_CONFIG, b'\x08')

    def who_am_i(self):
        return self.i2c.readfrom_mem(self.addr, REG_WHO_AM_I, 1)[0]

    def read_raw(self):
        # 14 bytes: accel XYZ, temp, gyro XYZ (big-endian signed 16-bit)
        data = self.i2c.readfrom_mem(self.addr, REG_ACCEL_XOUT_H, 14)
        return struct.unpack(">hhhhhhh", data)

    def read(self):
        ax, ay, az, t, gx, gy, gz = self.read_raw()
        accel = (ax / ACCEL_SCALE, ay / ACCEL_SCALE, az / ACCEL_SCALE)
        gyro = (
            gx / GYRO_SCALE - self.gyro_offset[0],
            gy / GYRO_SCALE - self.gyro_offset[1],
            gz / GYRO_SCALE - self.gyro_offset[2],
        )
        temp = t / 340.0 + 36.53
        return accel, gyro, temp

    def calibrate_gyro(self, samples=200):
        """Keep the sensor still while this runs."""
        sx = sy = sz = 0.0
        for _ in range(samples):
            *_, gx, gy, gz = self.read_raw()
            sx += gx / GYRO_SCALE
            sy += gy / GYRO_SCALE
            sz += gz / GYRO_SCALE
            time.sleep_ms(5)
        self.gyro_offset = (sx / samples, sy / samples, sz / samples)


def setup_swing_controller():
    """Init sensor + ESP-NOW. Returns (mpu, espnow_instance)."""
    # --- Sensor setup ---
    i2c = I2C(0, scl=Pin(22), sda=Pin(21), freq=400000)
    print("I2C devices:", [hex(a) for a in i2c.scan()])
    mpu = MPU6050(i2c)
    print("WHO_AM_I: 0x%02X (expect 0x68)" % mpu.who_am_i())

    print("Calibrating gyro - keep sensor still...")
    mpu.calibrate_gyro()
    print("Done.\n")

    # --- ESP-NOW setup ---
    sta = network.WLAN(network.STA_IF)
    sta.active(True)

    e = espnow.ESPNow()
    e.active(True)
    e.add_peer(BROADCAST)
    return mpu, e


async def swing_task(mpu, e):
    # Packet: counter (uint32) + ax ay az gx gy gz temp (7 floats) = 32 bytes
    PACKET_FMT = "<I7f"

    def send(counter, accel, gyro, temp):
        payload = struct.pack(PACKET_FMT, counter, *accel, *gyro, temp)
        try:
            e.send(BROADCAST, payload)
        except OSError as err:
            print("Send failed:", err)

    counter = 0
    e.send(BROADCAST, counter.to_bytes(4, "little"))
    armed = True                      # ready to detect a new swing
    last_swing = time.ticks_ms()
    last_send = time.ticks_ms()

    print("Swing the controller!")

    while True:
        accel, gyro, temp = mpu.read()
        speed = math.sqrt(gyro[0] ** 2 + gyro[1] ** 2 + gyro[2] ** 2)
        now = time.ticks_ms()

        swung = False
        if armed:
            if (speed > SWING_THRESHOLD
                    and time.ticks_diff(now, last_swing) > SWING_COOLDOWN_MS):
                counter += 1
                swung = True
                armed = False
                last_swing = now
                print("SWING! count =", counter, "(peak %.0f deg/s)" % speed)
        elif speed < SWING_RESET:
            armed = True              # motion settled, allow next swing

        # Send immediately on a swing, otherwise at the regular interval
        if swung or time.ticks_diff(now, last_send) >= SEND_INTERVAL_MS:
            e.send(BROADCAST, counter.to_bytes(4, "little"))
            last_send = now
        if counter > 3:
            counter = 0

        await asyncio.sleep_ms(SAMPLE_MS)


# =====================================================================
#  BLE SERVER (NFC payload receiver)
# =====================================================================
BLE_DEVICE_NAME = "ESP32-1"

SERVICE_UUID = bluetooth.UUID("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
CHAR_UUID = bluetooth.UUID("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
UUID_LE = bytes(reversed(bytes.fromhex("6E400001B5A3F393E0A9E50E24DCCA9E")))

_FLAG_WRITE = 0x0008
_FLAG_WRITE_NO_RESP = 0x0004

_IRQ_CENTRAL_CONNECT = 1
_IRQ_CENTRAL_DISCONNECT = 2
_IRQ_GATTS_WRITE = 3
_IRQ_MTU_EXCHANGED = 21

# LED on GPIO 2 (onboard LED on most ESP32 boards)
led = Pin(2, Pin.OUT)
led.value(0)


def handle_nfc_payload(source, d):
    led.value(1)  # data arrived -> LED on
    print("\n" + "=" * 50)
    print("[NFC RECEIVED] via", source)
    print("  Tag UID     :", d.get("tagUid", "Unknown"))
    print("  Payload Text:", d.get("payloadText", ""))
    print("  Records     :", d.get("records", []))
    print("  Tech List   :", d.get("techList", []))
    print("  Timestamp   :", d.get("timestamp", 0))
    print("=" * 50)


class BLEServer:
    def __init__(self):
        self._ble = bluetooth.BLE()
        self._ble.active(True)
        # Must be set BEFORE any connection so the phone can negotiate a big MTU.
        try:
            self._ble.config(mtu=512)
        except Exception as e:
            print("[BLE] mtu config failed:", e)
        try:
            self._ble.config(gap_name=BLE_DEVICE_NAME)
        except Exception as e:
            print("[BLE] gap_name config failed:", e)
        self._ble.irq(self._irq)

        # No pairing/bonding: the characteristic is plain-writable, so security only adds failure modes.
        services = ((SERVICE_UUID, ((CHAR_UUID, _FLAG_WRITE | _FLAG_WRITE_NO_RESP),)),)
        ((self._h,),) = self._ble.gatts_register_services(services)
        # allow long values (prepared/long writes) and accumulate chunks
        self._ble.gatts_set_buffer(self._h, 512, True)
        self._buf = b""
        self._conns = {}  # conn_handle -> time connected
        self._advertise()

    def _irq(self, event, data):
        # Exceptions inside an IRQ handler are swallowed silently - always print them.
        try:
            self._handle(event, data)
        except Exception as e:
            print("[BLE] IRQ handler error:", repr(e))

    def _handle(self, event, data):
        if event == _IRQ_CENTRAL_CONNECT:
            conn = data[0]
            self._conns[conn] = time.ticks_ms()
            print("[BLE] Connected, conn", conn)
        elif event == _IRQ_CENTRAL_DISCONNECT:
            print("[BLE] Disconnected")
            self._conns.pop(data[0], None)
            self._buf = b""
            self._advertise()
        elif event == _IRQ_MTU_EXCHANGED:
            print("[BLE] MTU:", data[1])
        elif event == _IRQ_GATTS_WRITE:
            conn, handle = data
            if handle != self._h:
                return
            chunk = bytes(self._ble.gatts_read(self._h))
            led.value(1)  # data arrived -> LED on
            print("[BLE] Received %d bytes" % len(chunk))
            self._buf += chunk
            self._conns[conn] = time.ticks_ms()

            # Plain text (not JSON): print it and clear the buffer
            if not self._buf.lstrip().startswith(b"{"):
                try:
                    print("[BLE] Text:", self._buf.decode().strip())
                except UnicodeError:
                    print("[BLE] Hex:", self._buf.hex())
                self._buf = b""
                return

            # JSON: wait until the full message has arrived
            try:
                d = json.loads(self._buf.decode())
            except ValueError:
                if len(self._buf) > 2048:
                    print("[BLE] Buffer overflow, dropping")
                    self._buf = b""
                return  # wait for more chunks
            self._buf = b""
            handle_nfc_payload("BLE", d)

    def _advertise(self):
        # Primary packet: flags + 128-bit service UUID (3 + 18 = 21 bytes) so scanners can
        # filter on the UUID. Scan response: the device name.
        name = BLE_DEVICE_NAME.encode()
        adv = b"\x02\x01\x06" + bytes([17, 0x07]) + UUID_LE
        resp = bytes([len(name) + 1, 0x09]) + name
        try:
            self._ble.gap_advertise(None)  # stop first so a restart always takes effect
        except Exception:
            pass
        self._ble.gap_advertise(100_000, adv_data=adv, resp_data=resp, connectable=True)
        print("[BLE] Advertising as", BLE_DEVICE_NAME)

    def watchdog(self):
        # A phone that vanished without a clean disconnect leaves the ESP32 'connected'
        # and NOT advertising. Drop connections that sit idle for 8 s, and re-arm advertising.
        now = time.ticks_ms()
        for conn, t in list(self._conns.items()):
            if time.ticks_diff(now, t) > 8000:
                print("[BLE] Dropping idle connection", conn)
                try:
                    self._ble.gap_disconnect(conn)
                except Exception as e:
                    print("[BLE] disconnect failed:", e)
                self._conns.pop(conn, None)
        if not self._conns:
            self._advertise()


async def ble_task(ble_server):
    while True:
        await asyncio.sleep(5)
        ble_server.watchdog()


# =====================================================================
#  MAIN
# =====================================================================
async def main():
    mpu, e = setup_swing_controller()   # sensor + ESP-NOW (Wi-Fi radio)
    ble_server = BLEServer()            # BLE advertising / GATT server
    await asyncio.gather(
        swing_task(mpu, e),
        ble_task(ble_server),
    )


try:
    asyncio.run(main())
except KeyboardInterrupt:
    print("Stopped")