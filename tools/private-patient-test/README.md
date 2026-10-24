# Lokalna zasebna testna gradnja 1.2.796

Izhodišče je `v1.2.794` (`56ffd1c40e3f2a73c9198fe732bb319a9c304bd7`),
z dodatki shrambe iz `962a02a` in `52e51bf`. Fotografije in podatki o imenu
pacienta niso vključeni v ta imenik ali Git. Izvorne datoteke se izrecno izberejo
z argumenti lokalnega orodja `prepare_package.py`; orodje njihove vloge ne ugiba.

## Potek

1. Vizualno preveri izvorna PNG-ja za obstoječa ID-ja `please` in `thank_you`.
2. `prepare_package.py --please <PNG> --thank-you <PNG> --patient-name <IME>
   --output <NOVA_ZASEBNA_MAPA>` ustvari paket zunaj repozitorija. Originala se
   kopirata brez spremembe bajtov. Manifest vsebuje ID-je, SHA-256 in ime za
   potrditev skrbnika; ostane samo v zasebnem paketu.
3. `build.ps1 -PackageRoot <ZASEBNA_MAPA> -BaselineApk <PREVERJEN_APK_794>`
   uporabi obstoječo konfiguracijo podpisovanja in primerja javna prstna odtisa
   podpisov. Spremenljivke `ANDROID_KEYSTORE_*`, `ANDROID_KEY_ALIAS` in
   `ANDROID_KEY_PASSWORD` morajo biti že varno nastavljene. Skripta ne pridobiva,
   izpisuje ali shranjuje skrivnosti. `ANDROID_HOME` kaže na obstoječi SDK.
4. Izrecna možnost `-Unsigned` dovoli samo diagnostični NEPODPISANI izhod.
   Tak APK ni namenjen namestitvi in ne dokazuje združljive nadgradnje.
5. `verify_isolation.py` preveri, da zasebni APK vsebuje točna originala, običajni
   APK ne vsebuje njunih bajtov ali zasebne zagonske kode, Git pa ne sledi slikama.
   Njegovo poročilo mora biti zunaj repozitorija.

Zasebna različica uporablja `com.rehab2`, `versionCode 10984` (naslednji sodi
za 10982), `versionName 1.2.796-private-test` in posebno oznako aplikacije.
Običajna debug/release konfiguracija, njen podpis in oznake različic ostanejo
nespremenjeni. Za standardni `assembleDebug` se init skripta ne uporablja.
Gradnja privzeto uporablja lokalne odvisnosti; če manjkajo, jih je treba predhodno
pridobiti iz konfiguriranih Maven repozitorijev.

## Identiteta, ohranitev podatkov in ponovljivost

Ob prvem prikazu osnovne strani zasebna Application zahteva izrecno potrditev
skrbnika, da trenutni pacientov profil pripada osebi iz lokalnega paketa.
Samodejno sklepanje iz imena ni dovoljeno. Potrditev shrani povezavo
`packId → obstoječi pacientov UUID` v zasebno evidenco. Zavrnitev ne spremeni slik.
Že potrjenega paketa ni mogoče pripeti drugi identiteti. DOM/TRGOVINA ostaneta
komunikacijska konteksta, ne ustvarjata nove identitete ali kopij fotografij.

Uvoz teče v ozadju in uporablja PatientImageStore. Veljavna obstoječa osebna
slika ima prednost in ostane nespremenjena, vključno s starimi osebnimi povezavami
prvotnega pacienta. Nov original se najprej kopira v `.pending`, preverita se
SHA-256 in berljivost, nato se objavi pod determinističnim imenom. Originali se
ne prepisujejo. Prekinitev po objavi originala in pred povezavo omogoča ponovno
uporabo iste datoteke, brez dvojnika.

Pred spremembo povezave se uporabi obstoječe varnostno kopiranje: profil, AAC
podatki in trenutna zasebna evidenca slik. Stari formati se ne spreminjajo.
Zaključek paketa se zabeleži šele po uspešni obdelavi obeh ikon. Ob napaki zagon
aplikacije teče dalje; ohranjene slike oziroma sistemska rezerva ostanejo na voljo.
Glavna stran po uspehu osveži samo sliki, brez ponastavitve navigacije ali govora.
Ponovni zagon zaključenega paketa ne uvaža ponovno in ne razveljavi poznejših ročnih izbir.

## Ločitev od objav

Init skripta se ne uporablja v `app/build.gradle` ali GitHub workflowih.
Zahteva izrecne poti zunaj repozitorija, onemogoči build cache in zavrne CI.
Izhod je v zasebni mapi, ne v običajnih poteh za upload. Koda Application in njen
manifest sta samo v `src/privatePatientTest`; brez izrecne lokalne konfiguracije
ta različica ne obstaja. Običajna avtomatizacija še vedno gradi le debug/release.
Zasebnega APK ni dovoljeno dodati kot običajen javni release asset.

## Preverjanje in meje

`tests/patient-images/run.ps1`: 33 preverjanj obstoječe shrambe in 27 preverjanj
uvoza, vključno z napačno identiteto, prekinitvijo, obnovo po objavi originala,
obstoječo sliko, varnostno kopijo, kontrolno vsoto in ponovnim zagonom.

Namestitev na dejanski tablici ni del avtomatiziranih testov. Pred nadgradnjo
morajo ustrezati applicationId, obstoječi podpis in višji versionCode. Podpis
debug ne nadomesti podpisa izdaje 794. Nameščena različica na odklopljeni tablici
ni preverjena; morebitne novejše različice se ne obide z odstranitvijo aplikacije
ali spremembo updaterja. Zasebna shramba preživi običajno nadgradnjo iste aplikacije,
ne pa odstranitve aplikacije ali brisanja podatkov.
