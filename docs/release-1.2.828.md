VERSION_CODE=11016
VERSION_NAME=1.2.828
RELEASE_CHANNEL=normal

NovaRehab 1.2.828 — mikrofon, kamera in spremljanje posameznega pacienta

Osnova je podpisana izdaja 826. Prva stran s 25 ikonami, osebne slike, govorne nastavitve, podikone, applicationId in pravila OTA/rollback ostajajo.

Začetek: Nastavitve → PREIZKUS, NOVA MREŽA IN GOVOR → MIKROFON, KAMERA IN INDIVIDUALNI PROFIL.

- Ločeni trajni nastavitvi MIKROFON OB ZAGONU in KAMERA OB ZAGONU, vezani na pacientov profil. Prva vključitev zahteva razlago, potrditev sodelovanja in ustrezno Androidovo dovoljenje. Zavrnitev ne blokira komunikatorja.
- Ročni USTAVI SPREMLJANJE velja do ročnega začetka ali novega zagona aplikacije. Vrnitev iz nastavitev in ozadja ga ne prekliče. Ozadje sprosti napravi; vrnitev nadaljuje aktivno sejo. Nov proces ali nov zagon z zaganjalnika po zaprtju zadnjega zaslona ponovno upošteva shranjene izbire. Seje zajema trajajo največ 30 minut.
- Zajem, pošiljanje AI in hramba surovih posnetkov so tri ločene izbire. Privzeto so izključene. Lokalni zajem ne potrebuje backenda, vendar brez njega ne ustvarja prepisa ali razlage slik.
- AudioRecord zajema 12-sekundne odseke. Segmenti, ki se prekrivajo z govorom aplikacije in sekundnim varnostnim premorom, se zavržejo. Ne ugibamo govorca. Kamera2 uporablja sprednjo kamero in tri majhne slike vsakih 20 sekund; hitri gibi se lahko spregledajo.
- Dejanska strežniška koda je v `backend/`. Zahteva HTTPS-gostovanje, strežniški OpenAI API-ključ in ločen omejeni ključ naprave. Gostovanje in račun nista bila zagotovljena, zato javna povezava AI še NI vzpostavljena. Modela se preverita ob povezavi; zajem se označi šele po dejanskem podatkovnem dogodku, odgovor AI šele po uspešni zahtevi.
- Prepis in vidni gibi so vedno nepotrjena opažanja. Ločen pregled omogoča popravek, ročno navedbo vira, DA / NE / NE VEM in potrjeni individualni znak samo za trenutnega pacienta. Profil podpira skrbnikove podatke o navadah pred poškodbo, sposobnostih, jeziku in preferencah ter popravke z virom.
- Največ trije predlogi povezujejo dogodek, iskanje, izbiro, potrditev in obstoječo pot. Trenutna pravila pokrivajo vodo, kavo in toaleto. Skrbnik jih pregleda in spremembe vnese v obstoječem urejevalniku; prva stran se ne prepisuje.
- Časovnica je v pomnilniku, omejena na 2000 dogodkov in 24 ur. Izbirna hramba poročil je 7 ali 30 dni; izvoz ne vsebuje posnetkov. Ločeno dovoljena lokalna hramba posnetkov je omejena na 20 MB/24 ur, čiščenje ob naslednjem zagonu in zajemu. Preklic sodelovanja izbriše lokalne seje in zahteva izbris strežniške seje. Strežnik surovih posnetkov ne hrani, ponudnikova pravila obdelave pa veljajo ločeno.

Programska preverjanja vključujejo Android debug gradnjo, dejansko kodo nastavitev in politike zagona, izločanje govora aplikacije, lokalno časovnico, ohranitev mreže in podatkov ter HTTP-protokol backenda s SIMULIRANIMI odgovori ponudnika. Podpisana gradnja, podpis in OTA se preverijo pri objavi.

NI potrjeno na fizični tablici: dovolilni dialogi konkretne različice Androida, resnični zvok/kamera, kakovost SL/UK prepisa, odkrivanje gibov, odmev, dostopnost in odzivnost, prehodi ozadje/vrnitev ter nadgradnja konkretnega pacientovega profila. Med pripravo ni bilo priključene ADB-naprave. Delujoča AI-povezava ni trditev te izdaje; natančna navodila in potrebne nastavitve so v `backend/README.md`.
