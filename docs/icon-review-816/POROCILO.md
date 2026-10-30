# Celotni pregled ikon po 812 — pripravljena izvorna koda 816

Osnova: v1.2.812, commit 72ade87ebab8214ed4ba0d4f9b792744a1e4d56d.
Pripravljena različica: com.rehab2, 1.2.816 / 11004. To še ni zgrajen ali objavljen APK.
Paket ne vključuje ločenega popravka 814 za preverjanje povezave generatorja.

## Spremembe

- Vizualno pregledanih vseh 479 sistemskih vnosov in pripadajočih 650 povezav. IDs in graf ostajajo isti.
- 113 novih izvozov PNG, največ 512 px, s prosojnostjo. 74 privzetih poti je zamenjanih; dodatne nove slike so moške/ženske alternative.
- 98 vnosov ima dve različici pacienta. Spol se bere iz obstoječih nastavitev ob prikazu; zamenjava ne prepisuje pacientovega JSON ali originalnih fotografij.
- Lastna pacientova slika ostaja prva izbira. Ročne/zaklenjene ikone in drugačen izbrani vir so zaščiteni. Razširjena je prepoznava obstoječih zastavic ročnega urejanja tudi v nalagalniku.
- Ista izrecno imenovana oseba lahko uporabi svojo že uvoženo fotografijo tudi pri ustreznem vprašanju »Kje je …«. Lastna vezava konkretne ikone ima prednost; ročna ikona ne podeduje druge slike. Sorodstvene vloge se ne ugibajo.
- Čiste anatomske slike brez tehničnih napisov, jasnejša pomoč pri toaleti, cele steklenice, ločene slike za kosilo/večerjo, PROSIM/HVALA, ponavljanje in vprašanja. Popravljeno neskladje PEČEN KROMPIR.
- Enotna preslikava vseh 479 privzetih slik v začetnem katalogu in pri nadgradnji. Kurirana sistemska galerija skriva stare neuporabljene izreze in slike z enakimi bajti; stare datoteke ostanejo za obstoječe ročne izbire.
- 204 ukrajinska izgovorjena besedila so dopolnjena v smiselne povedi. Dopolnjene so znane moške slovnične oblike v SL/UK. Poslušanje še ni izvedeno.
- Fiksna vrstica DA / NE RAZUMEM / NE / HVALA / POMOČ in položaj TOALETA 6 ostajata enaka.
- Generatorjeva referenčna sistemska slika upošteva izbrani spol. Spletna storitev generatorja ostaja nenastavljena; ta paket je ne vzpostavlja.

## Izvedena preverjanja

- 136 preverjanj dejanskih Java razredov: osebna hramba 33, začetni uvoz 27, varnostna kopija 9, slovnica/vloge 31, spol/zaščita/isti kontakti 21, nadgradnja drevesa 15.
- 479 enoličnih ID-jev, 650 nespremenjenih povezav, brez ciklov; 13 komunikacijskih poti.
- 283 predhodnih preslikav in njihove kontrolne vsote ostajajo veljavne.
- 417 različnih referenc slik je dekodiranih; preverjeni SHA-256, 98 popolnih parov in točno 113 novih izvozov brez osirotelih novih datotek.
- 26 preverjanj anatomskih strani/višin in števila označevalnikov intenzivnosti za oba spola. Galerija nima podvojenih SHA-256.
- Vseh 113 novih izvozov je ponovno vizualno pregledanih v treh preglednih slikah.
- git diff --check brez napak.

Android/Kotlin gradnja, izvajanje novega runtime testa proti APK razredom, podpis, dejanski govor in prepoznavnost na tablici tukaj NISO potrjeni. Gradle/Android odvisnosti v tem okolju niso pripravljene. Podpisne poverilnice niso uporabljene. Ni bilo pusha, GitHub Actions ali objave.

## Kaj potrebuje lokalni vnos

22 vnosov za kontakte/ožje sorodstvo ima splošno nadomestno sliko; prave osebe zahteva lokalno fotografijo. Dve mesti za fotografije konkretnega avtomata zahtevata lokalni sliki. 11 profilnih vnosov zahteva podatke ali izbiro v profilu. Podrobnosti so označene v CSV in manifestu; teh mest ne predstavljamo kot osebno dokončana.

Sistemske moške/ženske slike so pripravljene ilustracije; niso generator osebne podobnosti iz pacientove fotografije. Spol ikone prikazuje pacienta, ne spreminja spola zdravnika, sestre ali svojca.
