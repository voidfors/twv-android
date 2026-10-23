# Twitch Viewers (Android) — порт расширения twitch-viewers-extension

Открыть в Android Studio (Gradle wrapper создастся сам, gradle-8.7). minSdk 26, Kotlin + Compose.

## Сделано (волна 1 + ядро волны 2)
- Contracts.kt / Constants.kt — модели и интерфейсы, константы из расширения
- data/GqlClient.kt — CommunityTab и GetChannelMeta (как в background.js), тесты на MockWebServer
- data/IrcParser.kt, IrcClientImpl.kt — анонимный IRC-чат, реконнект, watchdog
- logic/ViewerSorter.kt — 7 сортировок, поиск, фильтры месяца/стримера, избранные, данные для графика
- logic/MetaQueue.kt — очередь метаданных (пачки по 20, приоритет, кэш 30 мин)
- logic/ViewersEngine.kt — сбор зрителей, чат, коллаб-переходы, эхо общего чата

## Проверено
Логика (Contracts, ViewerSorter, MetaQueue, ViewersEngine, IrcParser) скомпилирована kotlinc 2.0.20 и прогнана
на 20 сценариях. GqlClient и IrcClientImpl (OkHttp) и юнит-тесты на JUnit/MockWebServer здесь не собирались —
запустить: `./gradlew :app:testDebugUnitTest`.

## Добавлено (хранилище + экран зрителей), не собиралось
- data/MetaDb.kt — Room-кэш метаданных (RoomMetaCache), data/SettingsStore.kt — DataStore: избранные зрители, чат вкл/выкл, недавние каналы
- logic/CollabSession.kt — группа каналов вручную (E4): по движку на канал, стримеры группы исключаются, суммарный total
- ui/ViewersViewModel.kt, ViewersScreen.kt (поиск, 7 сортировок, чипы стримеров G4, звёздочка избранного, диалог «+ Канал»)
- ui/HomeScreen.kt — ввод логина и недавние каналы (запасной путь до B2), AppContainer.kt, TwvApp, навигация в MainActivity
- Зависимости: Room 2.6.1 + KSP 2.0.20-1.0.25, DataStore 1.1.1, Coil 2.7.0
- Тест: CollabSessionTest

## Добавлено: B2 (список стримов и поиск), не собиралось
- data/StreamsApi.kt — GqlStreamsApi: streams(sort: VIEWER_COUNT) с курсором и поиск по логину; все непроверенные запросы только здесь
- ui/StreamsViewModel.kt, StreamsScreen.kt — список, поиск с задержкой, «Ещё», ошибка с текстом + «Повторить» и «Ввести логин»; стартовый экран
- Тест: StreamsApiTest (MockWebServer, формы ответов выведены из знания схемы, не из живого ответа)

## Добавлено: график регистраций и фильтр по месяцу (G3), не собиралось
- logic/ViewerPipeline.kt — список с фильтрами + данные графика (график считается без фильтра месяца)
- ViewersScreen: кнопка «График», тап по столбцу фильтрует по месяцу, чип «Месяц: … ✕» сбрасывает
- Тест: ViewerPipelineTest

## Добавлено: настройки и уведомления об избранных, не собиралось
- ui/SettingsScreen.kt — чат, уведомления (запрос POST_NOTIFICATIONS на Android 13+), очистка избранных; кнопка ⚙ на экране стримов
- logic/FavoriteAlerts.kt — только избранные, только если включено, один раз за сессию (тест FavoriteAlertsTest)
- data/FavoriteNotifier.kt — системное уведомление. Фонового сервиса нет: уведомления приходят, пока открыт экран канала

## Добавлено: тёмная тема, избранные каналы, редизайн, не собиралось
- ui/Theme.kt — единая тёмная тема (цвета заданы явно, от системной темы не зависят), res/values/themes.xml — тёмное окно
- ui/Components.kt — Avatar (круглый, с буквой-заглушкой), SearchField, formatCount
- Список стримов: вместо превью — аватары, точка «в эфире», число зрителей, звезда избранного
- Избранные каналы: SettingsStore.favoriteChannels, GqlStreamsApi.fetchLiveChannels (один запрос с алиасами), блок «Избранные · в эфире» сверху; обновляются при открытии экрана и по кнопке ⟳
- Тест: FavoriteChannelsApiTest

## Дальше
Сборка в Android Studio и исправление ошибок компиляции, проверка запросов списка стримов на устройстве; по желанию J (автоопределение коллаба) и фоновый сервис для уведомлений.

## Сборка APK
- Android Studio: открыть папку, дождаться синхронизации, Build > Build APK(s). Файл: app/build/outputs/apk/debug/app-debug.apk
- Без Android Studio: залить проект в репозиторий GitHub и запустить workflow «Build debug APK» (.github/workflows/build-apk.yml), APK будет в артефактах запуска
