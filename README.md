# Начальная конфигурация PICO 4 Pro SEKO

Продукт `aosp_pico4pro-userdebug` описывает проверенную аппаратную основу для
`android-10.0.0_r47`. Это начальный device tree; VR-компоненты пока не включены
в сборку. Образ с работающим VR ещё не создан.

Конфигурация использует ARM64/ARM32, SDK/VNDK 29, KONA и ext4. Размер system
5 704 732 672 байт взят из текущей карты `/dev/block/mapper/system` и подтверждён
LP-метаданными. `super` имеет размер 8 589 934 592 байта; группа
`qti_dynamic_partitions` — 8 585 740 288 байт. Все checksum геометрии и четырёх
копий LP-метаданных проверены. Копия слота 0 совпадает с сохранённой 5.13.7,
слот 1 содержит другие размеры. Это не подтверждение стандартного A/B boot.

`BOARD_BUILD_SYSTEM_ROOT_IMAGE=false` соответствует правилам Android 10:
system.img всё равно содержит root и system, а boot сохраняет first-stage
ramdisk. Для первого прототипа оставляем заводские boot/kernel/DTB/DTBO и
vendor/product/odm 5.13.7. Файлы `stock/*.img` подключаются локальными ссылками
на проверенные образы ext4; они не включаются в Windows device tree.

Пока готовятся только конфигурация и проверка её загрузки системой сборки.
Сборка super, изменение слотов и установка на шлем не входят в этот этап.
Нужно реализовать упаковку VR-служб и их конфигураций, совместимость
framework/JNI/графики, classpath, разрешения, SELinux и подписи системных APK.
Граф наблюдаемых зависимостей — `reports/vr-dependencies/factory-graph.json`.

Первичный источник правил system-as-root:
https://source.android.com/docs/core/architecture/partitions/system-as-root
