import json
import time
import bluetooth
import uasyncio as asyncio
from machine import Pin

# ---------------- CONFIG ----------------
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


async def main():
    ble_server = BLEServer()
    while True:
        await asyncio.sleep(5)
        ble_server.watchdog()


try:
    asyncio.run(main())
except KeyboardInterrupt:
    print("Stopped")
