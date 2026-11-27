"""Explicit text review against the exported 840/842 baseline, not an audio review."""
import csv,json,re
from pathlib import Path
r=Path(__file__).resolve().parents[2]
p=r/'docs/speech-review-842.tsv'
rows=list(csv.DictReader(p.open(encoding='utf8'),delimiter='\t'))
speech={}
def group(ids,text):
 for id in ids.split():speech[id]=text
group('problem','У мене проблема.')
group('family_group','Родина.')
group('friends_group','Друзі.')
group('acquaintances_group','Знайомі.')
group('about_me','Виберіть інформацію про мене.')
group('speech_difficulty','Мені важко говорити.')
group('i_understand','Я вас розумію.')
group('my_name my_age my_birth_date my_home my_country my_language my_caregiver my_therapist call_my_caregiver call_my_therapist','Цю інформацію ще не внесено.')
group('home','Я хочу додому.')
group('activity_group','Виберіть заняття.')
group('music','Я хотіла б послухати музику.')
group('tv','Я хотіла б подивитися телевізор.')
group('environment_group','Виберіть, що потрібно змінити в кімнаті.')
group('walk','Я хотіла б піти на прогулянку із супроводом.')
group('visit','Я хотіла б, щоб мене відвідали.')
group('animals','Виберіть тварину.')
for id,animal in {'dog':'собаку','cat':'кота','bird':'птаха','fish':'рибу','horse':'коня','rabbit':'кролика'}.items():speech[id]=f'Я хотіла б побачити {animal}.'
group('place_group','Виберіть, куди я хочу піти.')
group('miss_little','Я трохи сумую за вами.')
group('miss_very','Я дуже сумую за вами.')
group('repeat_question','Будь ласка, повторіть запитання.')
group('repeat_last_sentence','Будь ласка, повторіть останнє речення.')
group('repeat_slower','Будь ласка, повторіть повільніше.')
group('slower_little','Будь ласка, говоріть трохи повільніше.')
group('slower_much','Будь ласка, говоріть набагато повільніше.')
group('help_drinking','Мені потрібна допомога, щоб попити.')
group('help_feeding','Мені потрібна допомога з годуванням.')
group('position body_position fix_me','Будь ласка, допоможіть мені змінити положення тіла.')
group('i_want','Я хотіла б дещо.')
group('smoking','Я хотіла б покурити.')
group('lighter','Мені потрібна запальничка.')
group('ashtray','Мені потрібна попільничка.')
group('rest','Я хотіла б відпочити.')
group('food','Я хотіла б поїсти.')
group('emergency','Виберіть, що потрібно терміново.')
group('urgent_help','Мені терміново потрібна допомога.')
group('feel_bad','Мені погано.')
group('cannot_breathe','Я не можу дихати.')
group('call_doctor doctor','Будь ласка, покличте лікаря.')
group('call_nurse wc_call_nurse nurse','Будь ласка, покличте медсестру.')
group('pain_area','Де тебе болить?')
for id,name in {'sister_zana':'сестру Жану','grandfather_sergej':'дідуся Сергія','julija':'Юлію','oksana':'Оксану','inna':'Інну','franc':'Франца','dusan':'Душана','daughter':'доньку','son':'сина','therapist':'терапевта'}.items():speech[id]=f'Будь ласка, покличте {name}.'
group('miss_you pogresam_te','Я сумую за тобою.')
group('love_you rada_te_imam','Я тебе люблю.')
group('turn_me','Будь ласка, поверніть мене.')
group('turn_left','Будь ласка, поверніть мене ліворуч.')
group('turn_right','Будь ласка, поверніть мене праворуч.')
group('turn_back','Будь ласка, переверніть мене на спину.')
group('turn_side','Будь ласка, переверніть мене на бік.')
for id,verb in {'cannot_speak':'говорити','cannot_stand':'встати','cannot_drink':'пити','cannot_eat':'їсти','cannot_move':'повернутися або пересунутися','cannot_understand':'зрозуміти'}.items():speech[id]=f'Я не можу {verb}.'
group('contact_message','Я хочу надіслати повідомлення.')
group('contact_call','Будь ласка, подзвоніть цій людині.')
group('cancel_call','Скасуйте.')
group('contact_help','Будь ласка, допоможіть мені.')
for id,name in {'dusan':'Душан','zana':'Жана','sergej':'Сергій','julija':'Юлія','oksana':'Оксана','inna':'Інна','franc':'Франц','other':'Інша людина'}.items():speech['person_'+id]=name+'.'
group('person_see','Я хотіла б побачити цю людину.')
group('person_come','Я хотіла б, щоб ця людина прийшла.')
group('person_where_is','Де ця людина?')
group('person_tell','Скажіть цій людині.')
group('friend_1 friend_2 friend_3','Друг.')
group('not_thirsty','Я не хочу пити.')
group('not_hungry','Я не голодна.')
group('hungry_pleskavica','Будь ласка, я хочу плескавицю.')
group('diaper','Будь ласка, замініть мені підгузок.')
group('wc_now','Мені негайно потрібно в туалет.')
group('wc_soon','Мені скоро потрібно буде в туалет.')
group('wc_help','Мені потрібна допомога в туалеті.')
group('dressing_help','Мені потрібна допомога з переодяганням.')
group('pillow','Мені потрібна подушка.')
group('blanket','Мені потрібна ковдра.')
group('clothing','Виберіть одяг.')
for id,text in {'shirt':'потрібна футболка','pants':'потрібні штани','socks':'потрібні шкарпетки','shoes':'потрібне взуття','jacket':'потрібна куртка','pajamas':'потрібна піжама','underwear':'потрібна нижня білизна','hat':'потрібна шапка','scarf':'потрібен шарф','gloves':'потрібні рукавички'}.items():speech[id]='Мені '+text+'.'
group('bed','Будь ласка, допоможіть мені лягти в ліжко.')
group('change_position','Будь ласка, допоможіть мені змінити положення тіла.')
group('sit_up','Будь ласка, підніміть мене.')
group('lie_down','Будь ласка, покладіть мене.')
group('pain_when_standing','Болить, коли встаю.')
group('dont_know_problem','Я не знаю.')
group('peace','Я хотіла б тиші.')
group('time_group','Виберіть час.')
for id,text in {'today':'Сьогодні','tomorrow':'Завтра','yesterday':'Учора','now':'Зараз','morning':'Вранці','afternoon':'Після обіду','evening':'Увечері','night':'Вночі'}.items():speech[id]=text+'.'
group('medicine','Будь ласка, дайте мені ліки.')
group('therapy','Я хотіла б піти на терапію.')
group('wheelchair','Будь ласка, допоможіть мені сісти у візок.')
group('crutch','Будь ласка, допоможіть мені з милицею.')
group('not_safe','Я не почуваюся в безпеці.')
group('need_rest','Мені потрібен відпочинок.')
group('help_to_wc','Будь ласка, допоможіть мені дістатися до туалету.')
group('vending_drinks','Будь ласка, допоможіть мені біля автомата з напоями.')
group('vending_coffee_tea','Будь ласка, допоможіть мені біля автомата з кавою та чаєм.')
group('shop','Будь ласка, допоможіть мені в магазині.')
group('restaurant','Будь ласка, допоможіть мені в ресторані.')
group('transport','Будь ласка, допоможіть мені з транспортом.')
group('vending_photo_step_1','Подивіться на зображення першого кроку.')
group('vending_photo_step_2','Подивіться на зображення другого кроку.')
group('move_me','Будь ласка, пересуньте мене.')
labels={'acquaintances_group':'ЗНАЙОМІ','place_group':'КУДИ Я ХОЧУ','contact_call':'ПОДЗВОНИТИ','cancel_call':'СКАСУВАТИ','change_position':'ЗМІНИТИ ПОЛОЖЕННЯ','medicine':'ЛІКИ','mineral_water':'МІНЕРАЛЬНА ВОДА','hungry_lamb':'ЯГНЯТИНА','person_see':'ПОБАЧИТИ','person_come':'ЩОБ ПРИЙШЛА ЛЮДИНА'}
corrections=[]
for row in rows:
 id,lang=row['id'],row['language'];changes=[]
 for kind in ('label','speech','question'):
  old=row[kind];new=old
  if lang=='uk':
   if kind=='speech':new=speech.get(id,re.sub(r'\?\?$', '?',old))
   elif kind=='label':new=labels.get(id,old)
   elif id.startswith('person_') and old=='' and id not in {'person_see','person_come','person_where_is','person_tell'}:new='Подзвонити чи скасувати?'
   elif id in {'doctor','nurse','therapist'} and old=='':new='Подзвонити чи скасувати?'
   elif id=='acquaintances_group' and old=='':new='Кого зі знайомих?'
   elif id=='friends_group':new='Кого з друзів?'
  if new!=old:
   corrections.append((id,lang,kind,old,new));changes.append(kind)
 row['text_review']='besedilno pregledano'
 row['corrected']='da: '+','.join(changes) if changes else 'ne'
 row['note']='Pregled sistemskega besedila; brez poslušanja in brez osebnih podatkov s tablice.'
 if changes:row['note']+=' Ciljni popravek v AacTextCorrections842; ta stolpec ohranja izvorno besedilo.'
 if id in {'no','dont_understand'}:row['note']+=' V4: NE je tih; NE RAZUMEM nima podikon.'
 if id=='wc_poop' and lang=='sl':row['note']+=' V4 napis BLATO; govor ostane nespremenjen.'
 if id=='wc_diaper_change' and lang=='sl':row['note']+=' V4 napis MENJAVA; govor ostane nespremenjen.'
with p.open('w',encoding='utf8',newline='') as f:
 w=csv.DictWriter(f,fieldnames=rows[0].keys(),delimiter='\t');w.writeheader();w.writerows(rows)
q=lambda s:json.dumps(s,ensure_ascii=False).replace('$','\\$')
entries='\n'.join('        Correction('+', '.join(map(q,c))+'),' for c in corrections)
code='''package com.rehab2.aac

/** Exact-match corrections of reviewed shipped text; never overwrite personal/manual text. */
object AacTextCorrections842 {
    data class Correction(val id: String, val language: String, val kind: String, val old: String, val text: String)
    val corrections = listOf(
ENTRIES
    )
    fun resolve(item: AacItem, language: String, kind: String, original: String?): String? {
        if (item.locked || item.userEdited || item.translationManualOverride) return original
        return corrections.firstOrNull { it.id == item.id && it.language == language && it.kind == kind && it.old == original.orEmpty() }?.text ?: original
    }
    fun previousSpeech(text: String, language: String): List<String> = corrections.filter {
        it.kind == "speech" && it.language == language && it.text == text && it.old.isNotBlank()
    }.map { it.old }.distinct()
}
'''.replace('ENTRIES',entries)
(r/'app/src/main/java/com/rehab2/aac/AacTextCorrections842.kt').write_text(code,encoding='utf8')
with (r/'docs/text-corrections-842.tsv').open('w',encoding='utf8',newline='') as f:
 w=csv.writer(f,delimiter='\t');w.writerow(['id','language','kind','old','new']);w.writerows(corrections)
print(f'Reviewed {len(rows)} rows; {len(corrections)} exact-match corrections; 0 recordings listened')
