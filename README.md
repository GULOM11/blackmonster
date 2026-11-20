# Black Monster, Minecraft Forge 1.20.1 boss mod

Bukchaygan, uzun qo'lli, moyli qora terili tubsizlik maxluqi. **Silliq OBJ model** (~55 000 uchburchak, 4 tomonlama rasm o'lchovlari asosida) va to'liq **protsedural Java animatsiyalar** (GeckoLib kerak emas).

## Imkoniyatlar
- **Model**: `assets/blackmonster/models/entity/black_monster.obj`, 23 suyakli rig: hips, torso, head, jaw, arm_upper/arm_lower/hand (l/r), tendril_a/tendril_b (l/r), thigh/shin/foot (l/r), cloth_f, cloth_b
- **Tashqi ko'rinish**: eng baland nuqtasi bo'lgan tuxumsimon, oldinga egilgan bosh (burunsiz), qalpoq ostidagi pastga qaragan og'iz va igna tishlar, iyak o'rtasida kalta tikan, bo'rtib chiqqan suyak-oq ko'zlar va nuqtadek qorachiqlar, bitta uzluksiz bukri va chuqur umurtqa ariqchasi, kurak va trapetsiya mushaklari, ingichka bel, oldinga-pastga cho'zilgan uzun bilaklar, 3 bo'g'imli ilmoqsimon 4 ta barmoq, iyakning ikki yonida 4 ta ayirsimon S-shaklli tentakl, kamonsimon oyoqlar, changallagan oyoq barmoqlari, belga qisilgan yirtiq latta (old tomoni boldir o'rtasigacha, orqasi kaltaroq)
- **Teksturalar**: moyli rezinasimon teri 1024px (mushak tolalari, tomirlar, ho'l yaltiroq dog'lar, #232A30), ko'z, tish, og'iz ichi, tirnoq, yirtiq latta (alfa bilan titilgan)
- **Animatsiyalar**: idle, walk (bukchaygan yurish), run (to'rt oyoqlab galop), fight (jang holati), attack, pounce, tendril lash, frenzy bite, ink eruption, roar, death
- **Spawn egg**: Creative > Spawn Eggs (`/give @s blackmonster:black_monster_spawn_egg`)
- **Boss**: 450 HP, 10 armor, 6 toughness, 100% knockback qarshiligi, olov/wither/zahar/darkness/blindness'ga chidamli, yiqilishdan zarar olmaydi, yo'qolmaydi, boss bar
- **Hujumlar**
  1. **Long Claw Sweep**: uzun qo'l bilan 5 blok oldingi yoy bo'ylab supurish, bir nechta nishon, Weakness
  2. **Vault Pounce**: chuqur cho'kkalab nishonga uchib tashlanadi, 16 zarar, yerga mixlaydi (Slowness V) + Darkness
  3. **Tendril Lash**: iyak tentakllari 9 barobar uzayib 13 blokdan nishonni o'ziga tortadi, siyoh izi
  4. **Frenzy Bite**: 5 marta tez tishlash, har biri Hunger beradi va maxluqni davolaydi
  5. **Ink Eruption**: qo'llarini yerga uradi, har bir o'yinchi ostida siyoh halqasi paydo bo'ladi, 1 soniyadan keyin fontan otiladi (qochish mumkin!) + Blindness
- **Tubsizlik terisi**: yorug'lik 7 dan past joyda har soniyada 1 yurak tiklanadi. Mash'alalar yoqing!
- **G'azab fazasi (HP < 50%)**: Abyssal Roar (24 blokda Darkness, itarib yuborish), +25% tezlik, +35% zarar, hujumlar 2x tez, binafsha boss bar
- **Effektlar**: iyak tentakllari va tirnoqlardan siyoh tomchilari, og'izdan qora so'lak, g'azabda ruh aurasi va tutun, yugurganda siyoh izlari
- **Kubok** va **Loot**: Trophy, Nether Star, 2-5 olmos, 8-16 siyoh xaltachasi, 1-3 echo shard
- Faqat vanilla ovozlar va zarrachalar

## JAR faylni GitHub orqali olish
1. GitHub'da yangi repository yarating.
2. ZIP ichidagi hamma narsani repository ildiziga yuklang (`.github` papkasi ham).
3. **Actions** bo'limida "Build JAR" tugashini kuting.
4. **blackmonster-jar** artifactini yuklab oling: `blackmonster-1.0.0.jar`.

## Kompyuterda yig'ish (ixtiyoriy)
JDK 17 + Gradle 8.8:
```
gradle wrapper --gradle-version 8.8
./gradlew build
./gradlew runClient
```

## O'rnatish
`blackmonster-1.0.0.jar` ni `mods` papkasiga qo'ying (Forge 1.20.1, 47.x). Boshqa mod kerak emas. Bipedal Stalker bilan birga ishlaydi.

## Modelni qayta yaratish
- `python tools/build_monster_model.py` model va teksturalarni, `python tools/render_preview.py` esa `previews/` ni qayta yaratadi.
- 1 birlik = 1 blok, Y yuqoriga, old tomon = -Z, oyoqlar y=0. Pivotlar: `MonsterModelRenderer.RIG`. 60 000 uchburchakdan oshirmang.

## Taxminlar
- Nomi "Black Monster" deb olindi (xabarda "black moster").
- Bo'yi ~3.4 blok (bukchaygan holatda), bo'kirganda ~3.8 blokgacha ko'tariladi.

- Oyoqda 3 ta kalta tirnoqli barmoq qilindi (rasmda 2-3 ta).
