Демонстрация проблемы при переключении AudioTrack между конфигурациями 2.0 и 5.1
---

Данное приложение демонстрирует проблему при использовании AudioTrack.

Проблема заключается в том, что при последовательной инициализации AudioTrack с конфигурациями
2.0 -> 5.1 -> 2.0 -> 5.1 (и т. д.), внутренние ресурсы AudioTrack для дорожек 2.0 не очищаются
(если точнее - очищаются не сразу, а спустя длительное время), даже учитывая, что перед
очередной инициализацией на предыдущей дорожке был вызван метод AudioTrack.release().
В результате, при попытке очередного переключения инициализация AudioTrack завершается ошибкой:

```
  E/AudioFlinger: no more track names available
  E/AudioFlinger: createTrack_l() initCheck failed -12; no control block?
  E/AudioTrack: AudioFlinger could not create track, status: -12
  E/AudioTrack-JNI: Error -12 initializing AudioTrack
  E/android.media.AudioTrack: Error code -20 when initializing AudioTrack.
```

При этом, если переключать только 2.0, либо только 5.1 дорожки, то проблема
не воспроизводится.

Алгоритм работы приложения:

  1. Создаётся AudioTrack для 2.0 дорожки
  2. AudioTrack воспроизводит случайный набор данных в течение 3 секунд
  3. Производится очистка AudioTrack (`AudioTrack.release()`)
  4. Создаётся AudioTrack для 5.1 дорожки
  5. AudioTrack воспроизводит случайный набор данных в течение 3 секунд
  6. Производится очистка AudioTrack
  7. Переходим к пункту 1.
  8. В определённый момент при создании AudioTrack произойдёт ошибка

Методика воспроизведения проблемы:

  1. Сборка: `./gradlew assembleDebug`
  2. Установка: `adb install ./app/build/outputs/apk/debug/app-debug.apk`
  3. Запуск `adb shell am start -n tc.planeta.audiotracktest/.MainActivity` (осторожно, будет воспроизводиться белый шум, поэтому звук можно заранее убавить)
  4. Периодическая проверка количества активных дорожек: `adb shell dumpsys media.audio_flinger`

В выводе данной команды нас интересует секция MIXER. Пример:

```
  Output thread 0xeea837c0 type 0 (MIXER):
  ...
  Channel count: 2
  Channel mask: 0x00000003 (front-left, front-right)
  ...
  10 Tracks of which 10 are active
    Name Active Client Type      Fmt Chn mask Session fCount S F SRate  L dB  R dB    Server Main buf  Aux Buf Flags UndFrmCnt
       2    yes   6100    3 00000001 00000003     817   4104 T 3 48000     0     0  00022800 0xeef89000 0x0 0x200      1026
       7    yes   6100    3 00000001 00000003     737   4104 T 3 48000     0     0  00022800 0xeef89000 0x0 0x200         0
       0    yes   6100    3 00000001 00000003     689   4104 T 3 48000     0     0  00022800 0xeef89000 0x0 0x200         0
       6    yes   6100    3 00000001 00000003     705   4104 T 3 48000     0     0  00022800 0xeef89000 0x0 0x201      1026
       5    yes   6100    3 00000001 00000003     673   4104 T 3 48000     0     0  00022800 0xeef89000 0x0 0x201      1026
       1    yes   6100    3 00000001 00000003     753   4104 T 3 48000     0     0  00022800 0xeef89000 0x0 0x200      1026
       8    yes   6100    3 00000001 00000003     769   4104 T 3 48000     0     0  00022800 0xeef89000 0x0 0x201      1026
       4    yes   6100    3 00000001 00000003     785   4104 T 3 48000     0     0  00022800 0xeef89000 0x0 0x200         0
       3    yes   6100    3 00000001 00000003     721   4104 T 3 48000     0     0  00022800 0xeef89000 0x0 0x201      1026
       9    yes   6100    3 00000001 00000003     801   4104 T 3 48000     0     0  00022800 0xeef89000 0x0 0x200      1026
```

Таблица в конце показывает количество активных дорожек. Когда их количество дойдёт до 16, произойдёт
ошибка инициализации.

Проблему также можно легко воспроизвести в сторонних приложениях. Например, в демо приложении ExoPlayer.
Достаточно скормить ему ссылку на источник с 2.0 и 5.1 дорожками и часто их переключать.

Важный момент: если мы переключили 2.0 на 5.1, а потом начали запускать другие источники (либо
переключаться между 2.0 дорожками текущего источника), то проблема так же остаётся - количество
активных дорожек прирастает при каждом переключении.

Можно предположить, что причина кроется в первом переключении с 2.0 на 5.1, когда операционная
система не высвободила ресурсы должным образом.