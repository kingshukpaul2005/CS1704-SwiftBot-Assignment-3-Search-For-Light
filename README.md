# 🔦 SwiftBot — Search For Light

An autonomous light-seeking robot program built in Java for the **SwiftBot platform**. The robot uses its onboard camera to analyse ambient light intensity, navigate toward the brightest (or darkest) area in its environment, and avoid obstacles in real time.

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [How It Works](#how-it-works)
- [Classes](#classes)
- [Getting Started](#getting-started)
- [Usage](#usage)
- [Configuration](#configuration)
- [OOP Design](#oop-design)
- [Session Logs](#session-logs)
- [Tech Stack](#tech-stack)

---

## Overview

This program was developed as part of the **CS1814 Software Implementation** module at Brunel University London. It transforms a SwiftBot robot into an autonomous light-seeking agent — capturing images each navigation cycle, computing directional light intensity, and making real-time movement decisions.

The program supports two operating modes:

| Mode | Trigger | Behaviour |
|---|---|---|
| Search for Light | Button A | Navigates toward the brightest area |
| Search for Dark | Button B | Navigates toward the darkest area |

---

## Features

**Core**
- Environmental calibration at startup — establishes a per-column ambient light baseline
- Image-based directional navigation — divides each 720×720 frame into left, centre, and right columns
- Ultrasound obstacle detection — checks for objects within 50cm before every movement
- Automatic obstacle avoidance — redirects to the second-best intensity direction
- Wandering mode — moves randomly when no light source is detected above threshold
- Safety termination — prompts the user after 5 obstacles within a 5-minute window
- Session logging — writes full movement history, intensity readings, and obstacle images to a timestamped folder

**Additional Features**
- **Search for Dark mode** — inverts the light-seeking logic via Button B
- **Adaptive Speed Control System** — two-factor speed system combining surface calibration and real-time image-based distance estimation:
  - Surface type (carpet/smooth) sets the base speed at startup (60 or 40)
  - Each cycle, the target column is split vertically — if the upper half is brighter, the light is estimated to be further away and speed increases by 20
  - Produces four possible speed values: 40, 60, 60, or 80
- **Session folder management** — all images and logs saved to a unique timestamped folder per run, previous sessions never overwritten
- **Button X exit** — clean manual shutdown at any point, with log written before exit

---

## How It Works

```
START
  │
  ├─ Standby screen (Button A = Light, Button B = Dark, Button X = Exit)
  │
  ├─ Environmental Calibration (captures baseline threshold per column)
  │
  ├─ Surface Type Selection (Carpet → baseSpeed=60, Smooth → baseSpeed=40)
  │
  └─ Core Navigation Loop:
        │
        ├─ Capture 720×720 image
        ├─ Calculate average luminance for Left / Centre / Right columns
        │
        ├─ Is any column beyond threshold? ──No──► Wander (random direction)
        │         │
        │        Yes
        │         │
        ├─ Obstacle within 50cm? ──Yes──► Blink red, save image, avoid (2nd best direction)
        │         │
        │        No
        │         │
        ├─ Move toward best section (brightest or darkest)
        ├─ Calculate speed (baseSpeed or baseSpeed+20 based on upper/lower brightness)
        │
        └─ Repeat until TERMINATE / Button X
```

---

## Classes

| Class | Responsibility |
|---|---|
| `SearchForLight` | Main class — program entry point, core loop, state management |
| `SearchMode` | Abstract class defining the search strategy interface |
| `LightMode` | Concrete strategy — seeks the brightest section |
| `DarkMode` | Concrete strategy — seeks the darkest section |
| `LightAnalyzer` | Image processing — luminance calculation, section analysis, speed calculation |
| `FileHandler` | File I/O — session folder creation, image saving, log writing |
| `SwiftBotActions` | Movement — go, wander, avoid, underlight control, surface calibration |
| `UI` | Console output — all formatted terminal display methods |

---

## Getting Started

### Prerequisites

- Java JDK 11 or higher
- SwiftBot API JAR (`SwiftBot-API-6.0.0.jar`)
- A SwiftBot robot with I2C enabled
- Eclipse IDE (recommended) or any Java IDE

### Setup

1. Clone the repository:
```bash
git clone https://github.com/yourusername/swiftbot-search-for-light.git
cd swiftbot-search-for-light
```

2. Add the SwiftBot API JAR to your build path. In Eclipse:
   - Right-click project → **Build Path** → **Add External Archives**
   - Select `SwiftBot-API-6.0.0.jar`

3. Ensure all classes are in the **default package** (no user-defined packages).

4. Deploy to the SwiftBot Raspberry Pi and compile:
```bash
javac -cp .:SwiftBot-API-6.0.0.jar SearchForLight.java
java -cp .:SwiftBot-API-6.0.0.jar SearchForLight
```

> **Note:** The program requires I2C to be enabled on the Raspberry Pi. If running without hardware, the program will print `I2C disabled!` and exit.

---

## Usage

### Startup

On launch, the standby screen is displayed:

```
  ___ ___   _   ___  ___ _  _   ___ ___  ___   _    ___ ___ _  _ _____
 / __| __| /_\ | _ \/ __| || | | __/ _ \| _ \ | |  |_ _/ __| || |_   _|
 \__ \ _| / _ \|   / (__| __ | | _| (_) |   / | |__ | | (_ | __ | | |
 |___/___/_/ \_\_|_\\___|_||_| |_| \___/|_|_\ |____|___\___|_||_| |_|

======================================================================
Status: STANDBY
Action: Please press Button 'A' on the SwiftBot to begin Search For Light

Additional Actions
Button 'B': Search for Dark
```

### Button Controls

| Button | Action |
|---|---|
| `A` | Start Search for Light mode |
| `B` | Start Search for Dark mode |
| `X` | Exit / Manual shutdown (works during standby and navigation) |

### Surface Prompt

After calibration, you will be asked:
```
Is the surface carpet? (True/False):
```
Enter `True` for carpet (base speed 60) or `False` for smooth surfaces (base speed 40).

### Termination

If 5 obstacles are detected within 5 minutes:
```
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  5 OBSTACLES DETECTED WITHIN 5 MINUTES
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

Enter TERMINATE to end the session.
Enter CONTINUE to reset the 5-minute window and continue.
Decision:
```
Both commands are **case-sensitive**.

---

## Configuration

Key constants in `SearchForLight.java`:

| Constant | Value | Description |
|---|---|---|
| `FORWARD_DISTANCE_CM` | `24.0` | Distance logged per forward movement |

Key values in `SwiftBotActions.java`:

| Variable | Carpet | Smooth | Description |
|---|---|---|---|
| `baseSpeed` | `60` | `40` | Base movement speed |

Obstacle detection threshold: **50cm** (hardcoded in `handleNormalMode` and `handleWandering`)

Speed boost distance threshold: upper half brightness > lower half brightness → `+20`

---

## OOP Design

This project demonstrates core OOP principles:

**Abstraction & Polymorphism** — `SearchMode` is an abstract class defining the navigation contract. `LightMode` and `DarkMode` implement it differently, so the core loop never needs to know which mode is active — it just calls `searchMode.getBestSection()` and gets the correct behaviour automatically.

```java
// Same call, different behaviour depending on active mode
direction = searchMode.getBestSection(sections, analyzer);
```

**Encapsulation & Access Control** — All fields in `SearchForLight` are `private`. `cycleCount` in `UI` is encapsulated behind `incrementCycle()`. `getLuminance()` in `LightAnalyzer` is `private` as an internal helper. `baseSpeed` in `SwiftBotActions` is `private` with a `getBaseSpeed()` getter.

**Cohesive Methods** — Each class has a single, well-defined responsibility. `LightAnalyzer` only handles image processing. `FileHandler` only handles I/O. `SwiftBotActions` only handles movement. `UI` only handles console output.

**Separation of Concerns** — `FileHandler.writeLog()` receives `modeName` and `peakIntensityLabel` as parameters rather than accessing `SearchForLight` internals directly, removing cross-class dependencies.

---

## Session Logs

Each run creates a timestamped folder at:
```
/data/home/pi/Session_YYYYMMDD_HHMMSS/
```

Contents:
```
Session_20260320_143022/
├── Logger_1.txt        ← Full session log
├── Image_1.png         ← Obstacle image (if detected)
├── Image_2.png
└── ...
```

The log file contains:
- Environment baseline threshold (per column)
- Peak intensity detected during session
- Execution duration
- Total distance travelled
- Full movement history with per-cycle intensity readings
- Obstacle count and image file paths

---

## Tech Stack

- **Language:** Java
- **Platform:** SwiftBot (Raspberry Pi 4)
- **API:** SwiftBot API 6.0.0
- **Libraries:** `java.awt`, `javax.imageio`, standard Java SDK only
- **IDE:** Eclipse

---

## Luminance Formula

Pixel luminance is calculated using the **ITU-R BT.601** standard:

```
Luminance = 0.299 × R + 0.587 × G + 0.114 × B
```

This weights green most heavily as the human eye is most sensitive to it, producing perceptually accurate brightness values for light-seeking decisions.

---

*Developed for CS1814 Software Implementation — Brunel University London, 2025/26*
