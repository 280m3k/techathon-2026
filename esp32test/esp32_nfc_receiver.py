import network
import time
import json
import bluetooth
import uasyncio as asyncio

WIFI_SSID = "ESP32-1"
WIFI_PASSWORD = "ESP32"
BLE_DEVICE_NAME = "ESP32-1"

SERVICE_UUID = bluetooth.UUID("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
CHAR_UUID = bluetooth.UUID("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
UUID_LE = bytes(reversed(bytes.fromhex("6E400001B5A3F393E0A9E50E24DCCA9E")))

_FLAG_WRITE = 0x0008
_FLAG_WRITE_NO_RESP = 0x0004


def handle_nfc_payload(source, d):
    print("\n" + "=" * 50)
    print("[NFC RECEIVED] via", source)
    print("  Tag UID     :", d.get("tagUid", "Unknown"))
    print("  Payload Text:", d.get("payloadText", ""))
    print("  Records     :", d.get("records", []))
    print("  Tech List   :", d.get("techList", []))
    print("  Timestamp   :", d.get("timestamp", 0))
    print("=" * 50)


# ---------------- Wi-Fi + HTTP ----------------
def connect_wifi():
    wlan = network.WLAN(network.STA_IF)
    wlan.active(True)
    if not wlan.isconnected():
        print("[Wi-Fi] Connecting to", WIFI_SSID)
        wlan.connect(WIFI_SSID, WIFI_PASSWORD)
        timeout = 20
        while not wlan.isconnected() and timeout > 0:
            time.sleep(1)
            timeout -= 1
            print(".", end="")
        print()
    if wlan.isconnected():
        print("[Wi-Fi] Connected:", wlan.ifconfig())
        return True
    print("[Wi-Fi] Failed to connect")
    return False


async def handle_http_client(reader, writer):
    try:
        request_line = await reader.readline()
        if not request_line:
            return
        content_length = 0
        while True:
            line = await reader.readline()
            if not line or line in (b"\r\n", b"\n"):
                break
            h = line.decode().lower()
            if h.startswith("content-length:"):
                content_length = int(h.split(":")[1].strip())

        if b"POST" in request_line and content_length > 0:
            body = await reader.read(content_length)
            try:
                handle_nfc_payload("Wi-Fi HTTP POST", json.loads(body.decode()))
            except Exception as e:
                print("[HTTP] JSON error:", e)

        resp = json.dumps({"status": "success"})
        writer.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
             "Content-Length: %d\r\nConnection: close\r\n\r\n%s" % (len(resp), resp)).encode()
        )
        await writer.drain()
    except Exception as e:
        print("[HTTP] Error:", e)
    finally:
        writer.close()
        await writer.wait_closed()


# ---------------- BLE ----------------
class BLEServer:
    def __init__(self):
        self._ble = bluetooth.BLE()
        self._ble.active(True)
        self._ble.config(gap_name=BLE_DEVICE_NAME, mtu=512)
        self._ble.irq(self._irq)
        services = ((SERVICE_UUID, ((CHAR_UUID, _FLAG_WRITE | _FLAG_WRITE_NO_RESP),)),)
        ((self._h,),) = self._ble.gatts_register_services(services)
        # allow long values and accumulate chunks
        self._ble.gatts_set_buffer(self._h, 512, True)
        self._buf = b""
        self._advertise()

    def _irq(self, event, data):
        if event == 1:
            print("[BLE] Connected")
        elif event == 2:
            print("[BLE] Disconnected")
            self._buf = b""
            self._advertise()
        elif event == 21:
            print("[BLE] MTU:", data[1])
        elif event == 3:
            conn, handle = data
            if handle == self._h:
                self._buf += bytes(self._ble.gatts_read(self._h))
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
        name = BLE_DEVICE_NAME.encode()
        adv = b"\x02\x01\x06" + bytes([len(name) + 1, 0x09]) + name
        resp = bytes([17, 0x07]) + UUID_LE
        self._ble.gap_advertise(100_000, adv_data=adv, resp_data=resp)
        print("[BLE] Advertising as", BLE_DEVICE_NAME)


async def main():
    ble_server = BLEServer()
    if connect_wifi():
        server = await asyncio.start_server(handle_http_client, "0.0.0.0", 80)
        print("[HTTP] Listening on port 80")
        await server.wait_closed()
    else:
        print("[Warning] BLE only mode")
        while True:
            await asyncio.sleep(3600)


try:
    asyncio.run(main())
except KeyboardInterrupt:
    print("Stopped")
