# Naloga 003: zasebne osebne slike

Izhodišče: `v1.2.794`, commit `56ffd1c40e3f2a73c9198fe732bb319a9c304bd7`.

## Obseg

Dodana je ločena evidenca pacientovih slik. `PatientImageStore` ustvari stabilen UUID
za obstoječo namestitev ter omogoča ustvarjanje in izbiro drugih identitet prek API.
Komunikacijski profili DOM/TRGOVINA in njihove nastavitve se ne uporabljajo kot
identiteta pacienta. V tej omejeni nalogi ni dodan uporabniški vmesnik za upravljanje
več celotnih pacientovih profilov; aplikacija 794 še vedno uporablja svoj obstoječi
enojni profil. API za identitete slik ne preklaplja govora ali osebnih podatkov.

V urejevalniku ikone je nova možnost **OSEBNA SLIKA PACIENTA**. Uvozi JPG, PNG ali
WebP in poveže kopijo z obstoječim ID-jem AAC ikone. Možnost odstranitve povezave
vrne obstoječo sliko; originalne fotografije ne izbriše. Zaklenjenih ikon ni mogoče
spreminjati. Rezultat odprtega izbirnika se ne pripne drugi pacientovi identiteti.
Če Android med izbiranjem uniči aktivnost, se nepopolna izbira varno opusti.

## Shranjevanje in združljivost

Pot se pridobi iz `Context.noBackupFilesDir`, običajno:

```
/data/user/0/com.rehab2/no_backup/aac-patients/
  active.properties
  <patient-UUID>/
    images.properties
    originals/<image-UUID>.<končnica>
    previews/<image-UUID>.png
    backups/<backup-UUID>/0-patient_profile.json
    backups/<backup-UUID>/1-aac_items.json
```

Ni novih dovoljenj, omrežnih zahtev, generiranja slik, javnih poti ali osebnih
fotografij v APK. Zasebna shramba ni izpostavljena obstoječemu FileProviderju.
Originalne datoteke se ustvarijo z novim UUID in se nikoli ponovno ne uporabijo
kot cilj zapisovanja. Omejitev uvoza je 25 MiB. Predogled je največ 1024 pik po
daljši stranici in upošteva EXIF orientacijo, kadar jo sistemski dekoder prebere.

Pred vsakim novim osebnim uvozom se bajtno kopirata obstoječa `patient_profile.json`
in `aac_items.json`, če obstajata. Če kopiranje ne uspe, uvoz ne spremeni povezave.
Na sveži namestitvi manjkajoči datoteki nista napaka. Obstoječi format, neznana
polja, stare slike, napisi, govor in povezave se ne pretvarjajo ali prepisujejo.
Zapis novih evidenc uporablja začasno datoteko, sinhronizacijo in obnovitveno kopijo.
Neznana različica evidence se zavrne, namesto da bi bila ponastavljena.

Manjkajoča ali neberljiva osebna slika prepusti prikaz prejšnji sliki ikone oziroma
začetni sistemski sliki. Za novo pacientovo identiteto se stare skupne osebne slike
ne dedujejo. Prvotni pacient ohrani svoje stare povezave. Stare knjižnice in njihovi
uvozni postopki ostanejo zaradi združljivosti nespremenjeni; za nove zasebne
fotografije je treba uporabiti novo izrecno označeno možnost. To ni samodejna selitev
vseh starih osebnih datotek.

## Preverjanje

`tests/patient-images/run.ps1` z JDK 17 prevede in zažene samostojne JVM teste:
stabilnost in izolacija identitet, lastništvo starih slik, povezava na AAC ID,
ohranitev originalov, točne kopije JSON z neznanimi polji, neobstoječe datoteke,
prazni/neveljavni/prekinjeni/preveliki uvozi, neveljavne poti, odstranitev povezave,
obnova prekinjenega zapisa, zavrnitev neznane različice ter nespremenjeni stari podatki.
Testni podatki ostanejo v ločeni začasni mapi; testi ne dostopajo do tablice.

Android preverjanje: `gradlew.bat :app:assembleDebug --offline --console=plain`.
Ni sprememb različice, podpisovanja, OTA, rollbacka, govora ali navigacije.

Rezultati 24. 10. 2026: **33/33 JVM preverjanj uspešnih**; končna Android gradnja
**BUILD SUCCESSFUL** (36 opravil, 6 izvedenih in 30 že posodobljenih).
`git diff --check` je uspešen. Gradnja še vedno opozarja na neuporabljene parametre
v obstoječi kodi in zastarele Gradle funkcije. Testni APK ohrani obstoječo oznako
različice 1.2.794; ta naloga ne izdaja ali objavlja različice 1.2.796.

## Preostalo preverjanje na napravi

- Uvoz resničnih fotografij HVALA/PROSIM, obe AAC strani in urejevalnikov predogled.
- Vrtenje JPEG fotografij ter izbor iz različnih ponudnikov dokumentov.
- Vrnitev na rezervno sliko, ponovni zagon aplikacije in nadgradnja ob ohranitvi podatkov.
- Prekinitev izbirnika, zaklenjena ikona, pomanjkanje prostora.

Fotografiji nista bili priloženi in nista vključeni. Testni APK ni nameščen.
Ker je shramba izključena iz samodejnega Android varnostnega kopiranja, njene slike
in identitete ne preživijo odstranitve aplikacije ali brisanja njenih podatkov.
Ročni izvoz/obnova te nove zasebne shrambe ni del te naloge. Lokalne kopije JSON
so zaščita pred lokalnimi spremembami, ne kopija za obnovo po izgubi naprave.
