VERSION_CODE=11012
VERSION_NAME=1.2.824
RELEASE_CHANNEL=normal

NovaRehab 1.2.824 — nova prva stran in preizkus na tablici

| 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|
| DA | NE | HVALA | RAZUMEM | NE RAZUMEM |
| TOALETA | BOLI ME | MRZLO | SLABO | UTRUJENA |
| PIJAČA | HRANA | VROČE | DOBRO | SPATI |
| DRUŽINA | PRIJATELJI | POČAKAJ | POČASNEJE | HITREJE |
| RABIM | ŽELIM | POPRAVI | GREMO | JEZNA |

Razpored velja za slovenščino in ukrajinščino. POMOČ ostane dosegljiva na naslednji strani in prek obstoječih povezav. Nova stran je prikaz nad obstoječimi podatki: shranjene postavitve, UUID, slike, posnetki in podikone se ne prepisujejo. Pred uporabo na obstoječem profilu se izdela zasebna varnostna kopija. Prejšnji prikaz je dosegljiv v Nastavitve → PREIZKUS, NOVA MREŽA IN GOVOR → OBNOVI PRIKAZ PREJŠNJE POSTAVITVE. Izbira velja za trenutnega pacienta.

Uporabljenih je 25 različnih sistemskih slik, šest novih. DA, NE in POČAKAJ ohranijo preproste simbole; ostali motivi so realistični. Osebne slike iz zasebne shrambe imajo prednost. Fotografij pacientke ali njenih bližnjih v novih datotekah ni. DRUŽINA in PRIJATELJI uporabljata sistemska motiva, dokler skrbnik ne izbere osebnih fotografij v urejevalniku.

Govor in odzivnost:
- Vključen je popravek slovenskih izgovornih navodil iz pripravljene 1.2.822. Slovenski sistemski posnetki se lahko ponovno ustvarijo; ločitev jezika, glasu, hitrosti in osebnih posnetkov ostaja.
- Shranjen zvok se poišče pred omrežno zahtevo. Priprava MediaPlayerja je asinhrona; kratki veljavni posnetki, tudi DA/NE, se ne zavržejo zaradi trajanja pod 700 ms.
- Ob razpoložljivi nadomestni poti je odziv na počasno generacijo omejen na 1,8 s; nova generacija se lahko dokonča v predpomnilnik. Če lokalni jezik/posnetek ni na voljo, se ohrani daljše čakanje na razumljiv generirani govor.
- PRIPRAVI GOVOR PRVE STRANI na izrecno zahtevo pripravi največ 50 fraz izbranega jezika z ločenima glasovoma. Brez samodejnega predvajanja, največ dve sočasni generaciji in združevanje enakih zahtev.
- Novi dotiki prekinejo stari govor; pozni dogodki predvajalnika, prevoda in preklicani časovniki ne smejo ponoviti prejšnje izbire.
- Nastavitve premorov ostanejo shranjene. Gumb NASTAVI ŠTIRI PREMORE NA 0 ms po potrditvi spremeni prvo vrstico, glavne ikone, podikone in vprašanja. Druge nastavitve sestavljanja stavkov ostanejo.

Živi preizkus:
- Vklopi se po jasni izbiri sodelovanja; na obeh komunikatorjih je viden širok gumb USTAVI / ЗУПИНИТИ, dosegljiv tudi z leve strani.
- Delujejo lokalni dogodki dotikov, prikaza, začetka/konca predvajanja in odzivnosti ter ločeni zapiski OPAŽANJE in ODGOVOR, POTRJEN S PACIENTKO.
- Podatki so le v pomnilniku, največ 2000 dogodkov in 24 ur, z možnostjo takojšnjega izbrisa. Ob zamenjavi pacienta se izbrišejo.
- Pravilo za popravke/nerazumevanje prikaže predlog za pregled; ne spreminja ikon ali pomenov. To ni analiza z AI.
- AI poslušanje, analiza kamere in mimike NISO vključeni. Manjkajo preverjen HTTPS-backend, avtentikacija naprave, model analize ter vmesnik za soglasje, hrambo in izbris. Mikrofon in kamera sta izključena; noben zvok/video se ne zajema ali pošilja. Obstoječi govorni API ni ta backend.

Tehnično preverjanje: Android debug gradnja; 186 preverjanj dejanskega kataloga/postavitve; 58 preverjanj ohranitve polj; 69 preverjanj zasebnih slik, uvoza in varnostnih kopij; 29 preverjanj življenjskega cikla TTS/predvajalnika; 18 preverjanj časovnikov in poznih odgovorov; 13 preverjanj predpomnilnika/generacije; 11 preverjanj lokalnega preizkusa. Statično preverjeni obstoječi graf, posebna mesta TOALETA in vseh 25 različnih slik. Podpisana gradnja in OTA se preverita pri objavi.

Na fizični tablici še NI potrjeno: dejanska SL/UK izgovorjava, tišina v posnetkih, akustični časi, velikost/dosegljivost prikaza, razumevanje slik, dotiki in nadgradnja pacientkine konkretne namestitve. Med pripravo ni bilo povezane ADB-naprave. Programske časovne oznake niso meritve prvega/zadnjega slišnega zvoka; posnetkov zato ne obrezujemo na slepo.

Za naslednji skupni pregled: POPRAVI trenutno pomeni »Prosim, popravi to.«, GREMO »Gremo, prosim.«; ne sprožita samodejne fizične akcije. RABIM in ŽELIM odpirata obstoječi izbiri, brez novega obsežnega drevesa. Pomen in podikone teh štirih pojmov uskladimo s pacientko/skrbnikom. Predloge dodatkov umestimo na drugo stran ali podikone.

Osnova vsebuje celotno 820 in popravek 822. PR #4 ostaja ločen osnutek starejše izvedbe personalizacije/transporta slik; prekriva MainActivity, komunikator, govor, katalog in zasebne slike. Ni združen, ker novejša osnova uporablja drugo shrambo in izboljšan življenjski cikel. Main in obstoječe oznake niso premaknjeni.
