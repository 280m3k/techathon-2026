# Project Plan

Phone scans an NFC tag and sends information to an ESP32.

## Project Brief

# Project Brief: NFC to ESP32 Data Transmitter

## Project Overview
An Android application that scans NFC tags and wirelessly transmits the tag payload to an ESP32 microcontroller.

## Features
1. **NFC Tag Reader**: Scans and reads NDEF messages or tag UIDs using the device's native NFC adapter.
2. **ESP32 Connectivity**: Discovers and connects to the ESP32 device via Wi-Fi (HTTP/REST API) or Bluetooth Low Energy (BLE).
3. **Data Transmission**: Formats and sends scanned tag information to the ESP32 upon successful tag read.
4. **Status & Diagnostic Dashboard**: Provides real-time visual feedback on NFC availability, target ESP32 connection status, and transmission history/outcomes.

## High-Level Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material Design 3
- **Navigation**: Jetpack Navigation 3 (State-driven navigation)
- **Adaptive Strategy**: Compose Material Adaptive library (`androidx.compose.material3.adaptive`) for responsive layouts across different device screen sizes.
- **Asynchronous & State Management**: Kotlin Coroutines & `StateFlow`
- **Connectivity Hardware**:
  - Android NFC API (`android.nfc`)
  - OkHttp / Ktor Client (for HTTP communication with ESP32 over Wi-Fi) or Android BLE APIs

## Implementation Steps
**Total Duration:** 19m 2s

### Task_1_NfcReaderAndDataModel: Implement NFC scanning logic using Android NfcAdapter (reading NDEF payloads and tag UIDs) and core data models for NFC tag payloads and transmission logs.
- **Status:** COMPLETED
- **Updates:** Added NFC permissions to AndroidManifest.xml. Defined NfcTagData and TransmissionLog data models. Implemented NfcManager for NDEF and UID parsing, reader mode support, and added unit tests in NfcManagerTest. Verified with `./gradlew assembleDebug` and `:app:testDebugUnitTest`.
- **Acceptance Criteria:**
  - Manifest updated with NFC permissions and feature requirements
  - NFC reader helper handles NDEF message parsing and Tag UID extraction
  - Data models defined for NFC tag data and transmission log items
- **Duration:** 7m 48s

### Task_2_Esp32CommunicationService: Implement ESP32 network communication service using HTTP/REST client with Kotlin Coroutines to transmit scanned NFC tag data to specified ESP32 device endpoint.
- **Status:** COMPLETED
- **Updates:** Added cleartext traffic permission to AndroidManifest.xml. Implemented Esp32ApiService, Esp32ApiServiceImpl using OkHttp and Coroutines, supporting POST/GET requests to ESP32 IP endpoints. Implemented Esp32Repository with StateFlow state and history tracking. Added unit tests Esp32ApiServiceTest and Esp32RepositoryTest. Verified with assembleDebug and testDebugUnitTest.
- **Acceptance Criteria:**
  - Network client implemented to send NFC tag payloads via HTTP POST/GET to target ESP32 IP
  - Error handling, timeout management, and state reporting (Idle, Transmitting, Success, Failed) implemented
- **Duration:** 5m 34s

### Task_3_Esp32BleCommunicationService: Implement Bluetooth Low Energy (BLE) peripheral scanning, connection, GATT service discovery, and characteristic write to transmit NFC tag data to ESP32 via BLE.
- **Status:** COMPLETED
- **Updates:** Added BLE permissions to AndroidManifest.xml. Implemented Esp32BleManager and Esp32BleManagerImpl for BLE scanning, GATT connection, service discovery, and characteristic writing. Integrated TransportMode (Wi-Fi vs BLE) into settings and repository. Verified with assembleDebug and testDebugUnitTest.
- **Acceptance Criteria:**
  - Add BLE permissions (BLUETOOTH, BLUETOOTH_ADMIN, BLUETOOTH_SCAN, BLUETOOTH_CONNECT) to AndroidManifest.xml
  - Implement BLE scanner and BluetoothGatt connection manager for ESP32 communication
  - Implement BLE characteristic write to transmit NFC payload data
- **Duration:** 2m 29s

### Task_4_ComposeUiDashboardAndSettings: Build Jetpack Compose UI with Material Design 3 featuring NFC status display, Wi-Fi vs BLE transport selection & endpoint configuration, tag reader controls, and transmission history dashboard.
- **Status:** COMPLETED
- **Updates:** Updated Compose UI (Esp32SettingsCard, DashboardScreen, TransmissionHistoryCard) with TransportMode toggle (Wi-Fi vs BLE), BLE settings (MAC address, UUIDs, scan/connect actions), and transport badges in history. Integrated MainViewModel with Esp32BleManager and Esp32ApiService. Verified build and 26 unit tests successfully.
- **Acceptance Criteria:**
  - Compose UI displays NFC hardware status, Wi-Fi / BLE transport toggle, ESP32 configuration, and transmission log
  - Settings section enables configuring target ESP32 IP endpoint or BLE device address/UUID
  - ViewModel and StateFlow integration connecting UI with NFC reader, Wi-Fi service, and BLE service
- **Duration:** 1m 40s

### Task_5_RunAndVerify: Refined run and verify step: Run and verify application build, ensure application stability with no crashes, confirm core NFC tag scanning to ESP32 Wi-Fi & BLE transmission functionality, and report critical UI issues.
- **Status:** COMPLETED
- **Updates:** Critic agent successfully deployed, tested, and verified app stability, build success, unit tests, and Wi-Fi vs BLE dashboard and adaptive layout on device/emulator with zero crashes.
- **Acceptance Criteria:**
  - make sure all existing tests pass
  - build pass
  - app does not crash
  - instruct critic_agent to verify application stability (no crashes), confirm alignment with user requirements, and report critical UI issues
- **Duration:** 1m 31s

