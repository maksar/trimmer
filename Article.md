# Размер имеет значение

Возможно я в этом не одинок, но меня сильно разочаровывают размеры современных приложений. Сейчас считается нормой создавать сайты, скачивающие десятки мегабайт при первой загрузке, иметь приложения для телефонов размером в несколько сотен мегабайт, базовые образы `docker` контейнеров занимают гигабайты... Ситуация парадоксальная – "дешевле" не заботиться о размере, так как усилия, потраченные в этом направлении будут стоить компании-разработчику дороже. Возможно это действительно так, проверить достаточно сложно. А может быть все как раз наоборот, просто не представляется возможным точно подсчитать сколько на **самом деле** средств тратится на трафик и "ожидания" с "простоями". А уж как большой размер скачиваемых артефактов влияет на customer и/или developer satisfaction и говорить нечего. "Современный подход" во мне пробуждает неоднозначные чувства и заставляет, в меру сил, *бороться* с ситуацией.

## Минутка истории

Компилируемые в бинарный код языки существовали еще до [начала времен](https://ru.wikipedia.org/wiki/Unix-время). В эпоху контейнеризации преимущества "бинарников" раскрылись с новой, неожиданной стороны. Для чего вообще нужна контейнеризация? Изоляция, воспроизводимость, удобство развертывания и безопасность. Программа, представляющая собой один единственный исполняемый файл – что может быть удобнее для развертывания? Изоляцию, в какой-то мере, может предоставить операционная система. А воспроизводимость (с точки зрения сборки) и вовсе решается без использования контейнеров. Что с безопасностью? Необходимо отметить, тут есть "проблемы". Возможно вы не застали времена [CGI](https://ru.wikipedia.org/wiki/CGI) скриптов, но в начале развития интернета, серверные приложения были обычными бинарниками, которые запускались web серверами через CGI интерфейс. И если в такой программе была ошибка/уязвимость – атакующий мог получить доступ ко всему серверу – ведь бинарник исполнялся, как правило, от пользователя под которым работал web сервер. А с учетом того, что сервера раньше виртуальными были редко – на одном и том же хосте располагались данные (почта, файлы и т.д.) многих пользователей – компрометации подвергалось все.

Сейчас CGI используется крайне редко, все чаще программы сами предоставляют `http` интерфейс для взаимодействия с собой, а web сервер выступает в роли `proxy`. Да и компилируемые в бинарный код языки для web используются все реже. Засилье виртуальных машин да интерпретаторов. Почему это не очень хорошо с точки зрения безопасности? Исполняемый файл можно назначить "точкой входа" `docker` контейнера, убрав из файловой системы все лишнее (кроме необходимых для работы приложения библиотек). В этом случае, даже если злоумышленник и обнаружит [shell injection](https://www.exploit-db.com/docs/english/42593-command-injection---shell-injection.pdf) в программе, ничего страшного не случится – никакого командного интерпретатора внутри контейнера нет, "внедрять" вредоносный код попросту некуда.

## Ruby

Если задуматься, такой трюк можно провернуть не только с системами, представляющими собой бинарный файл, но и со скриптовыми языками. Это несколько сложнее, но все-же возможно. Давайте попробуем разобраться на примере приложения, написанного на `ruby`.

В начале все более-менее стандартно – берем за основу официальный образ для `ruby` и устанавливаем зависимости из `Gemfile.lock` при помощи `bundler`-а. Библиотеки в `ruby` поставляются в виде исходников, складываем их в `/app/vendor/bundle` папку, рядом с самим приложением.

```Dockerfile
FROM ruby:2.7.0 as ruby

WORKDIR /app
COPY Gemfile* /app/
RUN bundle config --local deployment 'true'
RUN bundle config --local frozen 'true'
RUN bundle config --local no-cache 'true'
RUN bundle config --local clean 'true'
RUN bundle config --local without 'development'
RUN bundle config --local path 'vendor/bundle'
RUN bundle install
RUN mkdir .bundle && cp /usr/local/bundle/config .bundle/config
RUN rm -rf vendor/bundle/ruby/2.7.0/cache vendor/bundle/ruby/2.7.0/bin
```

Далее, в этом же много-`stage`-евом `Dockerfile` берем за основу `distroless` образ (без командного интерпретатора) и копируем из предыдущего шага библиотеки, необходимые для работы интерпретатора `ruby`. Как понять какие именно библиотеки нужны? Спрашивать у `ldd` (или `otool -L` в случае `llvm`) особого смысла нет – интерпретатор все равно кое-что загружает динамически. При помощи серии экспериментов, удается выявить, что для работы нашей программы, достаточно `libz`, `libyaml` и `libgmp`. Копируем библиотеки и сам интерпретатор в `distroless` образ.

```Dockerfile
FROM gcr.io/distroless/base-debian10 as distroless

COPY --from=ruby /lib/x86_64-linux-gnu/libz.so.* /lib/x86_64-linux-gnu/
COPY --from=ruby /usr/lib/x86_64-linux-gnu/libyaml* /usr/lib/x86_64-linux-gnu/
COPY --from=ruby /usr/lib/x86_64-linux-gnu/libgmp* /usr/lib/x86_64-linux-gnu/
COPY --from=ruby /usr/local/lib /usr/local/lib
COPY --from=ruby /usr/local/bin/ruby /usr/local/bin/ruby
COPY --from=ruby /usr/local/bin/bundle /usr/local/bin/bundle
```

Цель достигнута, образ не содержит командного интерпретатора и другой шелухи (`man` страниц, файлов настроек операционной системы и т.д.). Но мы на этом на остановимся и следующим шагом соберем образ буквально `FROM scratch`. `scratch` – это образ "без ничего", он пуст. Так что мы смеем надеяться, что ничего лишнего (не жизненно необходимого для работы приложения) в итоговом образе не будет. Кроме самого приложения (набора `*.rb` файлов) понадобиться еще файл с корневыми сертификатами, без которого не обойтись при общении с внешними сервисами по `https`.

```Dockerfile
FROM scratch

COPY --from=ruby /app /app

COPY --from=distroless /lib /lib
COPY --from=distroless /lib64 /lib64
COPY --from=distroless /usr/local /usr/local
COPY --from=distroless /usr/lib/ssl /usr/lib/ssl
COPY --from=distroless /usr/lib/x86_64-linux-gnu/lib* /usr/lib/x86_64-linux-gnu/
COPY --from=distroless /etc/ssl /etc/ssl
COPY --from=distroless /home /home

WORKDIR /app
COPY dialogs /app/dialogs/
COPY services /app/services/
COPY *.rb /app/

ENV SSL_CERT_FILE /etc/ssl/certs/ca-certificates.crt
ENV RUBYOPT -W:no-deprecated -W:no-experimental

CMD ["bundle", "exec", "ruby", "server.rb"]
```

Итоговый размер образа – **61 мегабайт**. Уверен, можно было бы еще десяток сбросить при помощи утилиты [dive](https://github.com/wagoodman/dive) (крайне рекомендую к использованию), удалив неиспользуемые части стандартной библиотеки языка и зависимостей `ruby` программы. Но вот эту часть, уже можно считать экономически нецелесообразной...

Если бы мы ставили перед собой цель максимально уменьшить размер приложения, то, скорее всего, воспользовались бы [alpine linux](https://alpinelinux.org) образом, который славится малым начальным размером а так же схлопнули бы все слои `docker` образа в один (чтобы избавиться от удаленных файлов в нижних слоях). В этом случае, размер получившего образа мог быть даже меньше, однако преимуществ безопасности мы бы не достигли.

Кроме преимуществ, у такого подхода есть и недостатки. К примеру, больше нельзя подключиться к работающему контейнеру и "посмотреть" логи, их просто нечем выводить, да и некуда – ни `bash` ни `cat` в образе нет. Вот он, микро-сервис во всей красе – пишет логи в `stdout`.

## Kotlin

Буквально на днях познакомился с [GraalVM](https://www.graalvm.org) и он меня покорил. Одной из функций `GraalVM` является сборка `native` бинарников из `jar` файлов. Да, именно так: вы можете взять свое приложение, собрать его в обычный `fat jar` (с зависимостями), а затем "скомпилировать" в исполняемый бинарь.

У меня есть маленькая поделка для "причесывания" названий ресурсных и проектных карт. Дело в том. что в процессе создания, иногда в начале или в конце `title`-а оставляют пробелы, что мешает потом эффективно работать с такими картами. Очень давно я написал программу, чтобы автоматизировать процесс `trim`-а. Целью было, конечно, не это, а исследование возможностей библиотеки `("com.github.rcarz", "jira-client", "master")` для доступа к `JIRA` через *приятный* `DSL`.

```kotlin
const val RESOURCE_CARDS = "RESCARD"
const val PROJECT_CARDS = "PROJCARD"

const val PAGINATION_SIZE = 999

val dotenv = DotEnv.load()
val jira = JiraClient(dotenv["JIRA_URL"], BasicCredentials(dotenv["JIRA_USERNAME"], dotenv["JIRA_PASSWORD"]))

fun makeQuery(block: JqlQueryBuilder.() -> Unit) : String =
        JqlStringSupportImpl(DefaultJqlQueryParser()).generateJqlString(newBuilder().also { block(it) }.buildQuery())

fun trim(project : String) {
    println("Searching for issues in ${project}.")
    jira.searchIssues(makeQuery {
        where().project(project)
        orderBy().createdDate(ASC)
    }, SUMMARY, PAGINATION_SIZE).iterator().asSequence().toList().filter {
        it.summary.trim() != it.summary
    }.also {
        if (it.count() == 0) {
            println("No issues in ${project}, that needs to be trimmed was found.")
        } else {
            println("Found ${it.count()} issues in ${project}, that needs to be trimmed.")
        }
    }.forEach {
        println("Trimming ${it.key} with summary '${it.summary}'.")
        it.update().field(SUMMARY, it.summary.trim()).execute()
    }
}

fun main(args: Array<String>) {
    MockComponentWorker().init()
    listOf(RESOURCE_CARDS, PROJECT_CARDS).forEach(::trim)
}
```

Для построения `JQL` запроса (не строкой, а при помощи `DSL`) к `JIRA` я использовал библиотеки самого `Atlassian`-а (библиотека для тестов необходима для инициализации `core`, в тестовом режиме, в противном случае `core` остается очень недоволен тем, что запущен вне контекста `JIRA`):

```kotlin
  implementation("com.atlassian.jira", "jira-core", "8.8.1")
  implementation("com.atlassian.jira", "jira-tests", "8.8.1")
```

Собрав `fat jar` с этими и еще некоторыми прямыми (сам `kotlin`, библиотека для работы с переменными окружения, etc.) и косвенными зависимостями (только представьте сколько зависимостей за собой "тянет" `jira-core`) получаем `trimmer-1.0-all.jar` размером в **112 мегабайт** – такова цена за `code-reuse`. Настало время для `GraalVM` – попробуем преобразовать `jar` файл в обычный исполняемый файл, в надежде избавиться от главной зависимости – виртуальной `java` машины.

```bash
native-image -cp ./build/libs/trimmer-1.0-all.jar -H:Name=trimer-exe -H:Class=TrimmerKt -H:+ReportUnsupportedElementsAtRuntime --allow-incomplete-classpath
```

Попытка "в лоб" заканчивается неудачей, логи полны сообщений вида:

```log
Error: Classes that should be initialized at run time got initialized during image building:
org.apache.log4j.spi.LoggingEvent was unintentionally initialized at build time.
org.apache.http.HttpEntity was unintentionally initialized at build time.
...
Error: Image build request failed with exit status 1
```

Не отчаиваемся и просим `GraalVM` пытаться инициализировать это все на этапе сборки образа:

```bash
native-image --no-server --enable-https --allow-incomplete-classpath -cp ./build/libs/trimmer-1.0-all.jar -H:Name=trimer-exe -H:Class=TrimmerKt --initialize-at-build-time=org.apache.http,org.slf4j,org.apache.log4j,org.apache.commons.codec,org.apache.commons.logging
```

Успех, на выходе имеем `trimer-exe` файл, размером всего в **6.5 мегабайт**. Упакуем его дополнительно замечательной утилитой [upx](https://upx.github.io), которая знакома всем еще со времен `DOS` и недостатка места на диске. Результат изумителен – **1.8 мегабайт**! Да только вот нас немного обманули... `GraalVM`, по умолчанию, строит образы, которые хоть и являются исполняемыми, но они не в состоянии работать без установленной на компьютере `java` виртуальной машины. При попытке построить "настоящий" независимый образ (опция `--no-fallback`), сталкиваемся с рядом сложностей.

Во-первых – `Warning: Aborting stand-alone image build. Detected a FileDescriptor in the image heap` появляющийся из-за статической инициализации поля `org.apache.log4j.LogManager.repositorySelector`. Дело в том, что в глубинах зависимостей нашего приложения есть части, инициализирующиеся на этапе загрузки классов – а именно – это код в блоках `static` и статические члены классов в `java`. В основном – это `logging framework`-и (их по дереву зависимостей наберется несколько штук), которые требуют указания `class`-а для создания logger объекта. Они обладают возможностью ленивой инициализации при первом использовании, при помощи ``reflection`` загружая подходящую реализацию, от чего `GraalVM` становится дурно (действительно, сохранить открытый `FileDescriptor` в дампе памяти – невыполнимая задача), он отчаянно требует помощи. Попробуем заглушить инициализацию `log4j`, мы ведь им и не пользуемся даже: добавляем в начале `main` строку `LogManager.setRepositorySelector(DefaultRepositorySelector(NOPLoggerRepository()), null)`, а в момент сборки образа добавляем опцию `-Dlog4j.defaultInitOverride=true`. Как до этого "дойти"? Исключительно чтением исходных текстов библиотеки. Сложно недооценить количество знаний и понимания внутреннего устройства систем, получаемых таким образом – не бойтесь заглядывать под капот используемым библиотекам!

К слову, еще до использования `GraalVM` я замечал, что при запуске приложения создается папка `target` (хоть я и использую `gradle`, который все *кладет* в папку `build`) с пустым файлом `unit-tests.log` в ней. Подозрения пали на `com.atlassian.jira:jira-tests` зависимость, в недрах которой обнаружился `log4j.properties` файл с незатейливым содержимым:

```properties
log4j.appender.console=org.apache.log4j.FileAppender
log4j.appender.console.File=target/unit-tests.log
```

Разработчики из `Atlassian` подумали, что это отличная идея – перенаправить все что должно выводиться на консоль – в файл. Хорошая это идея или кошмарная – каждый решает за себя, но вот делать это "втихую", просто из-за наличия зависимости – верх эгоизма.

За одно отключим еще один `logging framework` – `slf4j`. Для этого добавим в начало `main` ~~грязный хак~~строку:

```kotlin
LoggerFactory::class.java.getDeclaredField("INITIALIZATION_STATE").also { it.isAccessible = true }.set(LoggerFactory::class, LoggerFactory::class.java.getDeclaredField("NOP_FALLBACK_INITIALIZATION").also { it.isAccessible = true }.get(LoggerFactory::class))
```

Она заставит `slf4j` пропустить инициализацию и не заниматься `reflection`-ом во время старта приложения. А статическую инициализацию одного из наших полей, сделаем отложенной (чтобы "трюк" из `main` успел выполниться вовремя):

```kotlin
val jira = lazy { JiraClient(dotenv["JIRA_URL"], BasicCredentials(dotenv["JIRA_USERNAME"], dotenv["JIRA_PASSWORD"])) }
```

Кстати, именно из-за статической инициализации приложения на `java` так медленно стартуют, а при старте иногда можно видеть в консоли строки:

```log
log4j:WARN No appenders could be found for logger (org.apache.http.impl.conn.PoolingClientConnectionManager).
log4j:WARN Please initialize the log4j system properly.
log4j:WARN See http://logging.apache.org/log4j/1.2/faq.html#noconfig for more info.
```

`GraalVM` как раз и славится тем, что позволяет сократить время запуска приложений, так как вся статическая инициализация происходит на этапе "сборки", в готовый исполняемый файл, вместе со встраиваемой виртуальной машиной, попадают "замороженные" версии классов, с уже выполненным шагом статической инициализации.

```bash
native-image --no-fallback --allow-incomplete-classpath --enable-https --no-server -cp ./build/libs/trimmer-1.0-all.jar -H:Name=trimer-exe -H:Class=TrimmerKt --initialize-at-build-time=org.apache.http,org.apache.log4j,org.slf4j,org.apache.commons.logging,org.apache.commons.collections.map,net.sf.json,net.sf.ezmorph,org.apache.oro.text.regex,org.apache.commons.codec -Dlog4j.defaultInitOverride=true
```

Следующая беда "вылазит" уже не на этапе сборки, а после запуска собранного приложения:

```log
Exception in thread "main" java.lang.NoClassDefFoundError: java.lang.Class
  at net.sf.json.AbstractJSON.class$(AbstractJSON.java:53)
  ...
  at net.rcarz.jiraclient.RestClient.request(RestClient.java:165)
  ...
```

Общение с `JIRA` происходит при помощи `JSON`-а, а его разбор в `java` большинством библиотек происходит через `reflection`, возможности которого в `GraalVM` несколько ограничены. `GraalVM` понимает, что необходимо "встроить" нужные вызовы к `reflection` `API` в итоговый образ, если они происходят на этапе статической инициализации, но вот к вызовам `reflection` во время исполнения его "никто не готовил". Создадим файл `config.json` с таким содержимым:

```json
[
  {
    "name" : "java.lang.String"
  },
  {
    "name" : "java.lang.Class"
  }
]
```

Заставим `GraalVM` обратить на него внимание:

```bash
native-image --no-fallback --allow-incomplete-classpath --enable-https --no-server -cp ./build/libs/trimmer-1.0-all.jar -H:Name=trimer-exe -H:Class=TrimmerKt --initialize-at-build-time=org.apache.http,org.apache.log4j,org.slf4j,org.apache.commons.logging,org.apache.commons.collections.map,net.sf.json,net.sf.ezmorph,org.apache.oro.text.regex,org.apache.commons.codec -Dlog4j.defaultInitOverride=true -H:ReflectionConfigurationFiles=./config.json
```

В итоге, собранная программа работает как положено. Итоговый размер – **28 мегабайт**, а будучи упакованным при помощи `upx` – **7.1 мегабайт**. Не удивительно, ведь `GraalVM` пришлось включить в исполняемый файл `Substrate VM` виртуальную машину для того, чтобы бинарный файл стал независим от системного `JRE`. Обещания, которые давал `GraalVM` он выполнил – один исполняемый файл, независимость от системного `JRE`. К слову, время старта приложения значительно сократилось – разница заметна даже невооруженным взглядом:

```bash
➜ time java -jar build/libs/trimmer-1.0-all.jar --dry-run
0.30s user 0.05s system 181% cpu 0.195 total
➜ time ./trimer-exe --dry-run
0.00s user 0.00s system 70% cpu 0.010 total
```

## Haskell

Напоследок, попробуем получить преимущества от статической линковки программы на `Haskell`, бота [Group Manager](https://itransition.workplace.com/Group-Manager-105678357661487). Сборка программ на `Haskell` внутри `docker`-а происходит примерно так же как и на `golang`. В первом `stage`-е устанавливаются все необходимые зависимости, собирается бинарный исполняемый файл. Затем он из этого `stage`-а копируется в "чистовой" контейнер, не содержащий компилятора и других `development` зависимостей.

Самая первая версия бота так и собиралась, итоговый бинарный файл имел размер **26 мегабайт**, а `docker` образ (на основе того же `distroless`) – **46 мегабайт**.

```Dockerfile
FROM haskell:8.6.5 as haskell

RUN mkdir /app
WORKDIR /app

ADD stack.yaml .
ADD stack.yaml.lock .
ADD package.yaml .

RUN mkdir src
RUN mkdir app
RUN mkdir test

RUN stack setup
RUN stack build || true

ADD . .

RUN stack install

FROM gcr.io/distroless/base
COPY --from=haskell /lib/x86_64-linux-gnu/libz* /lib/x86_64-linux-gnu/
COPY --from=haskell /usr/lib/x86_64-linux-gnu/libgmp* /usr/lib/x86_64-linux-gnu/

COPY --from=haskell /root/.local/bin/ldabot-exe /app

ENTRYPOINT ["/app"]
```

В принципе, не так и плохо, но можно лучше! Если добавить опции для статической сборки и использовать `scratch` в качестве базового образа (никакие библиотеки ведь теперь не нужны), получается исполняемый файл размером **28 мегабайт** и такого же размера `docker` образ (состоит он, по сути, из одного единственного файла).

```Dockerfile
FROM haskell:8.6.5 as haskell

RUN mkdir /app
WORKDIR /app

ADD stack.yaml .
ADD stack.yaml.lock .
ADD package.yaml .

RUN mkdir src
RUN mkdir app
RUN mkdir test

RUN stack setup
RUN stack build || true

ADD . .

RUN sed -i "s/    ghc-options:/    cc-options: -static\n    ld-options: -static -pthread\n    ghc-options:\n    - -O2\n    - -static/g" package.yaml

RUN stack install --executable-stripping
RUN strip /root/.local/bin/ldabot-exe

FROM scratch

COPY --from=haskell /root/.local/bin/ldabot-exe /app

ENTRYPOINT ["/app"]
```

Стоит ли останавливаться на достигнутом? Конечно же нет! Существует такая штука как `musl` – альтернативная реализация `libc` библиотеки, которая славится малым размером (кроме других своих достоинств). Именно благодаря ей `alpine linux` имеет такой скромный размер. Мир полон добрых людей, существуют сборка компилятора `GHC 8.6.5` "под" `musl` – `utdemir/ghc-musl:v4-libgmp-ghc865`, ей мы и воспользуемся.

```Dockerfile
FROM utdemir/ghc-musl:v4-libgmp-ghc865 as haskell

RUN mkdir /app
WORKDIR /app

RUN cabal update
ADD ldabot.cabal .
RUN cabal build || true

ADD . .
RUN cabal new-install
RUN strip --strip-all /root/.cabal/bin/ldabot-prod

FROM alpine as upx

RUN apk add -u upx

COPY --from=haskell /root/.cabal/bin/ldabot-prod /app
RUN upx --best /app

FROM scratch

COPY --from=gcr.io/distroless/base /etc/ssl /etc/ssl
COPY --from=upx /app /app

ENTRYPOINT ["/app"]
```

Благодаря `musl` (и, конечно, `upx`) удалось добиться бинарника размером **5.9 мегабайт**. `docker` образ, при этом, стал чуть больше – **6.1 мегабайт**, так как дополнительно пришлось копировать `SSL` сертификаты для работы (исходный код к этому времени стал обращаться к внешним сервисам по `https`).

Текущая версия бота собирается чуть иначе. Причина этому – использование более новой версии компилятора `GHC 8.8.3`. Того требует одна из зависимостей `polysemy` – is a library for writing high-power, low-boilerplate, zero-cost, domain specific languages, о которой я постараюсь вскоре рассказать. Для `GHC 8.8.3`, на момент создания бота, поддержки `musl` еще "на завезли". Сборка работает при помощи `stack` (это как `gradle` для `java`), который "из коробки" умеет исполнять команды сборки внутри контейнера. Необходимо только указать базовый образ и запустить сборку при помощи команды `stack build --docker`

```yaml
docker:
  image: "fpco/stack-build-small:latest"
```

`Dockerfile` при этом выглядит необычно – внутри не происходит никакой сборки, только сжатие `upx`-ом и копирование библиотек.

```Dockerfile
FROM alpine as upx

COPY .stack-work/docker/_home/.local/bin/ldabot-prod /app
RUN apk add -u upx
RUN upx --best --ultra-brute /app

FROM scratch

COPY --from=gcr.io/distroless/base /etc/ssl /etc/ssl
COPY --from=upx /app /app
COPY --from=fpco/stack-build:lts-14.25 /lib/x86_64-linux-gnu/ld-linux* /lib/x86_64-linux-gnu/libc.* /lib/x86_64-linux-gnu/libnss_dns.* /lib/x86_64-linux-gnu/libresolv.* /lib/

ENTRYPOINT ["/app"]
```

Постойте, какие библиотеки, речь же шла о статической линковке... Дело в том, что `libc`, в отличие от `musl` не может быть полностью "влинкован" в приложение. Причин несколько, но для обывателя их можно сформулировать как "так получилось". Обратите внимание на то, какие именно библиотеки мы копируем – `libnss_dns` и `libresolv` (ну и еще `ld-linux` для возможности динамической загрузки последних). Это библиотеки для работы с `DNS`, а инфраструктура [NSS](https://en.wikipedia.org/wiki/Name_Service_Switch) предоставляет много backend-ов для работы с `DNS` (вплоть до чтения из файла). Так как нет возможности на этапе сборки указать какой именно backend использовать, `libc` всегда загружает их динамически, заставляя "тянуть" еще и себя, кроме необходимых `NSS` плагинов. С таким положением дел все до сих пор мирятся (убеждая окружающих, что статическая линковка "не нужна", ведь все равно придется "тянуть" с собой `libc`), периодически "сбегая" в лагерь `musl`, если нужна "действительно" статическая линковка.

В итоге, вышел компромиссный вариант (из-за невозможности использовать `musl`) – статическая линковка (размер исполняемого файла **4.6 мегатайта**), вместе с `libc` и библиотеками для `DNS`, сделали размер образа не таким большим – всего **7.2 мегабайта**. Цель по уменьшению размера итогово образа и обеспечению дополнительной безопасности можно считать достигнутой. Особенно греет душу мысль о том, что бот в состоянии покоя занимает в оперативной памяти всего **812 килобайт**!

```dive
Cmp   Size  Command
4.6 MB  ├── app
246 kB  ├── etc
246 kB  │   └── ssl
235 kB  │       ├── certs
235 kB  │       │   └── ca-certificates.crt
 11 kB  │       └── openssl.cnf
2.3 MB  └── lib
171 kB      ├── ld-linux-x86-64.so.2
2.0 MB      ├── libc.so.6
 27 kB      ├── libnss_dns.so.2
101 kB      └── libresolv.so.2

Total Image size: 7.2 MB
Potential wasted space: 0 B
Image efficiency score: 100 %
```
