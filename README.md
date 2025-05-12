# InOutStockerApp

InOutStockerApp is an Android inventory management app built entirely in Kotlin. It uses the device’s back camera to scan QR codes on goods and talks to PHP APIs on the backend. Every action (audit, inward, outward) only works after a successful scan.

---

## 📑 Table of Contents

- [InOutStockerApp](#inoutstockerapp)  
- [Authentication & QR Code Scanning](#-authentication--qr-code-scanning)  
- [Key Features](#-key-features)  
- [Highlights / Technologies](#-highlights--technologies)  
- [Tech Stack](#-tech-stack)  
- [Screenshots](#-screenshots)  
  - [App Logo](#app-logo)  
  - [Login Screen](#-login-screen)  
  - [Home Screen](#-home-screen)  
  - [Common Screen (Audit, Inward, Outward)](#-common-screenaudit-inward-outward)  
  - [Common Screen with sample data (Audit, Inward, Outward)](#-common-screen-with-sample-data-audit-inward-outward)  
- [Getting Started](#-getting-started)  
  - [Prerequisites](#prerequisites)  
  - [Build & Run](#build--run)  
- [Contact](#contact)  

---

## 🔐 Authentication & QR Code Scanning

- Users must log in before using the app.
- After login, the back camera opens to scan a QR code on the item.
- Scanning uses CameraX + ML Kit and is required before any feature (audit, inward, or outward) can proceed.
- Scanned data is sent to the PHP backend for processing.

---

## ✨ Key Features

- **Stock Audit**  
  Scan items and verify them against inventory records.

- **Inward Goods**  
  Record arrival of goods (boxes, bags, drums) by scanning PRN, DRS, or THC codes.

- **Outward Goods**  
  Send goods out via DRS (customer deliveries) or THC/PRN (depot transfers).

- **Secure Operations**  
  Every action requires a valid QR scan to ensure accuracy.

---

## 💡 Highlights / Technologies

- **Language:** Kotlin  
- **UI:** Jetpack Compose + Accompanist  
- **Camera & Scanning:** CameraX, ML Kit Barcode Scanning  
- **Networking:** OkHttp, Gson  
- **Animations:** Lottie  
- **Navigation:** Navigation Compose  
- **Build:** Gradle Kotlin DSL (.kts)  
- **Permissions:** INTERNET, CAMERA, FLASHLIGHT, POST_NOTIFICATIONS, BLUETOOTH*, LOCATION*  
  (from AndroidManifest.xml)

---

## 🛠 Tech Stack

| Component        | Technology                             |
|------------------|----------------------------------------|
| Language         | Kotlin                                 |
| UI Toolkit       | Jetpack Compose, Accompanist           |
| Navigation       | Navigation Compose                     |
| Camera/Scanning  | CameraX, ML Kit Barcode Scanning       |
| Networking       | OkHttp, Gson                           |
| Animations       | Lottie                                 |
| Build System     | Gradle (Kotlin DSL)                    |
| Backend API      | PHP                                    |

---

## 📸 Screenshots

### App Logo
![App Logo](app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp)

### 🔐 Login Screen
<img src="https://github.com/user-attachments/assets/4a4703e8-7178-4bc4-bf61-b9e3e3c718bd" alt="Login Screen" width="300"/>   

### 🏠 Home Screen
<img src="https://github.com/user-attachments/assets/a2fe5b65-67f4-41fa-8ed7-42a15cd21181" alt="Home Screen with small dashboard" width="300"/>   

### 📊 Common Screen(Audit, Inward, Outward)
| Audit | Inward | Outward |
|:-----:|:------:|:-------:|
| <img src="https://github.com/user-attachments/assets/b30ae3f2-def9-4736-b88c-fc3228962878" alt="Audit" width="150" /> | <img src="https://github.com/user-attachments/assets/ff11c461-4142-4892-adda-ff192a7aa083" alt="Inward" width="150" /> | <img src="https://github.com/user-attachments/assets/3c63ca93-7a85-4fbe-a0b2-c564d1329da4" alt="Outward" width="150" /> |

### 📊 Common Screen with sample data (Audit, Inward, Outward)
| Audit | Inward | Outward |
|:-----:|:------:|:-------:|
| <img src="https://github.com/user-attachments/assets/ea4e2bf7-ab44-402f-93a9-558ed18aee9a" alt="Audit with scanned data" width="150" /> | <img src="https://github.com/user-attachments/assets/6ffc6449-aa44-43fb-aa79-8b31eab6fce0" alt="Inward with scanned data" width="150" /> | <img src="https://github.com/user-attachments/assets/54418911-89b0-41dc-931c-87e98d8371cd" alt="Outward with scanned data" width="150" /> |

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Flamingo (2023.1.1) or later  
- JDK 17+  
- Gradle 8.x+

### Build & Run

1. Clone the repo:
   ```bash
   git clone https://github.com/manoj2000-crypto/InOutStockerApp.git
   ```
2. Open in Android Studio (File > Open).
3. Let Gradle sync and build.
4. Run on an emulator or device.
   - Grant required permissions (Camera, Internet, Bluetooth, Location).

---

### Contact

## 📬 Contact

**Maintainer:** Manoj kale  
**LinkedIn**: [LinkedIn Profile](https://www.linkedin.com/in/manojkalemk/)
