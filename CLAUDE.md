# Armine Order Module

Android uygulaması (`com.example.armineordermodule`), Kotlin + Gradle KTS.

## RFID

Hedef cihaz **Chainway C5**. RFID okumaları Chainway SDK ile değil, cihazdaki
Keyboard Emulator uygulamasının broadcast'leri dinlenerek alınır.
Action, extra'lar, EPC formatı ve kurallar:

@docs/chainway-rfid.md

`docs/RFID_SDK_ENTEGRASYON.md` başka bir cihazı (BLD N60) ve onun `.aar` SDK'sını anlatır.
Chainway C5 için geçerli değildir.
