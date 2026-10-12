# Northstar Money

**Finanças pessoais para Android, com os dados guardados no dispositivo.**

[![CI](https://github.com/moreirass/northstar-money/actions/workflows/blank.yml/badge.svg)](https://github.com/moreirass/northstar-money/actions/workflows/blank.yml)
![Estado](https://img.shields.io/badge/estado-alpha-orange)
![Versão](https://img.shields.io/badge/versão-0.1.0-blue)
![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF)

Gere contas, movimentos, orçamentos, objetivos e dívidas em várias moedas, sem registo de utilizador, sem nuvem e sem analytics. Desenvolvida em Kotlin e Jetpack Compose. Disponível em português e inglês.

<!-- Adicionar aqui 2 a 4 capturas de ecrã (ex.: docs/images/). -->

> [!WARNING]
> **Projeto alpha.** É um projeto pessoal de aprendizagem, desenvolvido com assistência de ferramentas de IA. Os testes existentes não equivalem a uma auditoria profissional nem à validação para distribuição comercial. Manter backups regulares de dados importantes.

## Destaques

- **Local-first:** o registo e a consulta funcionam sem Internet. A rede só é usada para obter taxas de câmbio.
- **Multimoeda:** cada conta tem a sua moeda; os relatórios consolidam na moeda de apresentação.
- **Privacidade:** bloqueio biométrico, ocultação de valores, captura de ecrã protegida e OCR de recibos no dispositivo.
- **Backups cifrados:** AES-256-GCM, com restauro validado e recuperação do estado anterior.
- **Exportação:** CSV, PDF (resumo mensal) e Excel.

## Índice

- [Funcionalidades](#funcionalidades)
- [Início rápido](#início-rápido)
- [Utilização](#utilização)
- [Regras financeiras e câmbio](#regras-financeiras-e-câmbio)
- [Privacidade e segurança](#privacidade-e-segurança)
- [Backups e recuperação](#backups-e-recuperação)
- [Importação e exportação](#importação-e-exportação)
- [Compilação](#compilação)
- [Arquitetura](#arquitetura)
- [Base de dados e migrações](#base-de-dados-e-migrações)
- [Testes e CI](#testes-e-ci)
- [Limitações](#limitações)
- [Documentação e contribuição](#documentação-e-contribuição)
- [Licença](#licença)

## Funcionalidades

**Contas e movimentos**

- Contas de dinheiro, conta corrente, poupança e crédito, com moeda e saldo inicial próprios.
- Receitas, despesas e transferências (incluindo entre moedas, com valores enviado e recebido separados).
- Pesquisa e filtros, com carregamento de 200 registos por página.
- Movimentos conferidos e reconciliação com extratos.
- Arquivo e recuperação de contas; recuperação de movimentos eliminados recentemente.
- Categorias com renomeação, arquivo, restauro e fusões reversíveis.
- Recibos: seleção de imagem, fotografia e OCR no dispositivo para sugerir comerciante, data e montante.

**Planeamento e relatórios**

- Orçamentos mensais por categoria, com moeda de alocação explícita e transição do saldo do mês anterior.
- Objetivos de poupança com histórico de contribuições editável.
- Perfis de dívida: taxa de juro, pagamento mínimo e dia de vencimento.
- Movimentos recorrentes com pausa, retoma e lançamento automático das ocorrências vencidas.
- Resumo mensal de saldos, receitas, despesas e saldo líquido.
- Gráficos com alternativas de leitura acessíveis.
- Previsão a 30 dias, com indicação de câmbios estimados ou em falta.

**Utilização e acessibilidade**

- Configuração inicial guiada.
- Navegação adaptada a telemóveis, tablets e dispositivos dobráveis.
- Descrições semânticas e suporte para texto maior.
- Bloqueio opcional por biometria forte ou credencial do dispositivo.
- Widget com saldo e movimentos recentes, respeitando as opções de privacidade.
- Lembrete diário opcional de revisão financeira.

## Início rápido

Requisitos: Android Studio (ou ferramentas de linha de comandos), Android SDK 36 e JDK 21. Detalhes em [Compilação](#compilação).

```bash
git clone https://github.com/moreirass/northstar-money.git
cd northstar-money
./gradlew installDebug   # Windows (PowerShell): .\gradlew.bat installDebug
```

O comando instala a compilação debug num dispositivo ou emulador ligado (API 26+).

## Utilização

1. Concluir a configuração inicial: moeda de apresentação e primeira conta.
2. Confirmar as categorias e registar movimentos.
3. Definir orçamentos, objetivos e recorrências conforme necessário.
4. Nas opções, configurar a privacidade e **criar um backup completo protegido por palavra-passe**.

Os dados extraídos por OCR são apenas sugestões e devem ser revistos antes de guardar.

## Regras financeiras e câmbio

Os montantes são guardados como inteiros de 64 bits em **unidades mínimas da moeda**: `12345` EUR representa 123,45 €; `12345` JPY representa 12 345 ienes. Despesas têm sinal negativo e receitas positivo; os totais de despesas apresentam a magnitude positiva.

Cada conta mantém a sua moeda. A moeda de apresentação serve para consolidar relatórios, convertendo movimentos e saldos iniciais estrangeiros. As taxas são obtidas do serviço Frankfurter por HTTPS; cada pedido envia apenas o par de moedas e a data. Montantes, comerciantes e imagens de recibos **não** são enviados.

- As taxas obtidas ficam guardadas localmente e são reutilizadas quando a data e as moedas coincidem.
- Novas conversões correm em segundo plano (WorkManager), com concorrência e cache limitadas.
- Mudar a data ou a moeda de apresentação exige uma taxa correspondente.
- Totais com conversões em falta são assinalados como incompletos.
- Os orçamentos mantêm o valor e a moeda de alocação originais quando a moeda de apresentação muda.
- As previsões usam as taxas disponíveis como estimativa; não garantem o câmbio futuro.

Os cálculos mensais respeitam os limites do mês local. As recorrências preservam o dia e o mês de referência (meses curtos e anos bissextos). O lançamento é agendado no arranque e a cada seis horas; a execução efetiva depende do agendamento do Android.

## Privacidade e segurança

Não existem registo de utilizadores, sincronização na nuvem, ligação bancária, publicidade ou analytics. Os dados ficam no armazenamento privado da aplicação e o backup automático do Android está desativado (`allowBackup=false`). Os backups completos são criados pelo utilizador.

| Proteção | Comportamento |
|---|---|
| Bloqueio da aplicação | Biometria forte ou credencial do dispositivo; nova autenticação ao sair da atividade |
| Ocultação de valores | Preferência persistente que mascara montantes |
| Captura de ecrã | Bloqueada enquanto o bloqueio ou a ocultação estiverem ativos |
| Widget | Oculta montantes e comerciantes nas mesmas condições |
| OCR | ML Kit, no dispositivo |
| Backups completos | AES-256-GCM, com chave derivada da palavra-passe |
| Importações | Limites de tamanho e validação antes de escrever ou substituir dados |

> [!IMPORTANT]
> A base de dados Room **não** usa uma camada adicional de cifra (como SQLCipher). O bloqueio da interface não cifra a base de dados local; a cifra aplica-se apenas aos backups.

**Permissões:** Internet (taxas de câmbio), notificações (lembretes, se autorizada) e câmara (opcional; a aplicação usa os mecanismos Android de seleção e captura).

**Exportações:** os ficheiros CSV, Excel e PDF não são cifrados. O destino escolhido no seletor de ficheiros pode pertencer a outro serviço, incluindo armazenamento na nuvem.

## Backups e recuperação

O backup completo contém o snapshot da base de dados (contas, categorias, movimentos e entradas, reconciliações, orçamentos, objetivos e contribuições, recorrências, dívidas, recibos e taxas de câmbio), além de metadados de versão e da moeda de apresentação. **Não** inclui todas as preferências Android da aplicação.

- **Formato:** AES-256-GCM com salt e IV aleatórios; PBKDF2-HMAC-SHA256 com **600 000 iterações**; exportação em streaming.
- **Palavra-passe:** entre **12 e 128 caracteres**.

**Ao restaurar, a aplicação:**

1. Decifra o ficheiro e verifica a autenticidade do conteúdo.
2. Verifica a versão e valida referências, moedas, sinais, cardinalidade dos movimentos e conversões.
3. Cria uma cópia local cifrada do estado anterior, dentro da transação de substituição.
4. Substitui os registos de forma atómica e disponibiliza a recuperação do estado anterior.

Desfazer o restauro usa essa cópia local e a palavra-passe usada na recuperação. A cópia fica no dispositivo e não substitui um backup externo.

> [!CAUTION]
> - Não existe recuperação da palavra-passe. Guardar a palavra-passe e uma cópia do ficheiro num local seguro.
> - Desinstalar a aplicação ou apagar os seus dados elimina o armazenamento local.
> - Backups antigos sem metadados da moeda de apresentação usam a moeda atualmente selecionada para interpretar as alocações de orçamento. **Selecionar a moeda original antes de restaurar.**
> - Backups que exigem uma versão de base de dados superior à suportada são rejeitados.

## Importação e exportação

### CSV

UTF-8, separador vírgula, com este cabeçalho pela ordem indicada:

```csv
date,type,payee,category,account,amount,currency
2026-10-12,EXPENSE,Supermercado,Alimentação,Conta principal,-2599,EUR
2026-10-12,INCOME,Salário,Salário,Conta principal,150000,EUR
```

- Datas em `AAAA-MM-DD`; `amount` é um inteiro em unidades mínimas, sem separador decimal.
- A moeda tem de corresponder à da conta.
- A importação aceita `INCOME` e `EXPENSE`. Conta e categoria têm de existir, ser identificadas sem ambiguidade pelo nome, e a categoria tem de corresponder ao tipo.
- Transferências podem aparecer na exportação, mas **não são importadas** pelo formato atual.
- O CSV não contém dados suficientes para reconstruir a base de dados; **não substitui o backup completo**.
- O importador valida os registos, deteta duplicados e escreve de forma atómica. Se houver erros de validação, nada é importado.
- Os campos de texto exportados têm proteção contra interpretação como fórmulas em folhas de cálculo.

### Excel e PDF

O Excel (`.xlsx`) inclui folhas de movimentos, contas, orçamentos, resumo e câmbios. O PDF contém o resumo mensal e pagina as linhas de orçamento. As exportações de movimentos (CSV e Excel) incluem o conjunto completo, independentemente da página visível na interface.

<details>
<summary><strong>Limites de ficheiros e restauro</strong></summary>

| Conteúdo | Limite |
|---|---|
| Imagem de recibo selecionada | 8 MiB |
| Imagem de recibo guardada | 1 000 000 bytes |
| Ficheiro CSV | 16 MiB |
| Linhas de dados CSV | 100 000 |
| Campo CSV | 4 096 caracteres |
| Backup cifrado | 64 MiB |
| Conteúdo total de recibos num restauro | 32 MiB |
| Total de registos num snapshot restaurado | 500 000 |

</details>

## Compilação

### Requisitos

- Android Studio compatível com a configuração Gradle, ou ferramentas Android de linha de comandos.
- Android SDK 36 e ferramentas de compilação correspondentes.
- JDK 21 para o daemon Gradle (o bytecode da aplicação tem como alvo Java 17).
- Dispositivo ou emulador Android API 26+.
- Internet na primeira compilação, para descarregar dependências.

O wrapper fornece **Gradle 9.5.0**. `gradle/gradle-daemon-jvm.properties` seleciona **JetBrains JDK 21** (pode ser descarregado na primeira execução) e `gradle/libs.versions.toml` define as versões das dependências.

### Configurar o SDK

Abrir a pasta no Android Studio e aguardar a sincronização Gradle. Caso necessário, definir o SDK pela variável `ANDROID_HOME` ou em `local.properties` (não versionar):

```properties
# Windows (usar / evita escapes)
sdk.dir=C:/Users/UTILIZADOR/AppData/Local/Android/Sdk

# Linux
sdk.dir=/home/UTILIZADOR/Android/Sdk
```

### Comandos

| Ação | Windows (PowerShell) | Linux / macOS |
|---|---|---|
| APK debug | `.\gradlew.bat assembleDebug` | `./gradlew assembleDebug` |
| Instalar debug | `.\gradlew.bat installDebug` | `./gradlew installDebug` |
| APK release | `.\gradlew.bat assembleRelease` | `./gradlew assembleRelease` |

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

O APK debug usa a assinatura de desenvolvimento. O build release ativa R8 e redução de recursos, mas **não tem assinatura de distribuição configurada**: tem de ser assinado antes de instalar ou distribuir.

**Resolução de problemas:** se o SDK não for encontrado, confirmar `local.properties` ou `ANDROID_HOME`. Se o Gradle não arrancar, verificar a instalação Java e os critérios do daemon. Os testes instrumentados exigem um dispositivo visível em `adb devices`.

## Arquitetura

Um único módulo Android (`app`), organizado em camadas:

```text
Compose UI → FinanceViewModel → FinanceRepository → OfflineFinanceRepository
                                                     ├─ Room / SQLite
                                                     ├─ adaptadores de importação e exportação
                                                     ├─ backups e OCR
                                                     └─ cliente de câmbio e workers
DataStore → preferências de utilização e privacidade
```

As consultas agregadas de categorias e orçamentos correm em SQL. A lista de movimentos usa paginação; os observadores de recibos carregam metadados sem imagens nem texto OCR completo; os observadores do widget só ficam ativos quando existe um widget instalado.

<details>
<summary><strong>Tecnologias e versões</strong></summary>

| Área | Tecnologia |
|---|---|
| Linguagem | Kotlin 2.3.20 |
| Build | Android Gradle Plugin 9.3.1 · Gradle 9.5.0 |
| Interface | Jetpack Compose BOM 2026.06.00 · Material 3 · Navigation Compose |
| Persistência | Room 2.8.4 · SQLite · esquema versão 10 |
| Preferências | DataStore 1.2.1 |
| Segundo plano | WorkManager 2.11.2 |
| Serialização | Kotlin Serialization 1.8.1 |
| Recibos | ML Kit Text Recognition 16.0.1 |
| Autenticação | AndroidX Biometric e credencial do dispositivo |
| SDK | mínimo 26 · compilação e alvo 36 |

</details>

<details>
<summary><strong>Estrutura do repositório</strong></summary>

```text
app/
├─ src/main/java/com/northstar/money/
│  ├─ core/         # Base de dados, preferências, segurança, navegação e interface
│  ├─ data/         # Repositório, câmbio, backups, importação/exportação, OCR e workers
│  ├─ domain/       # Modelos monetários, cálculos e contrato do repositório
│  ├─ feature/      # Estado financeiro e ViewModel
│  └─ widget/       # Widget Android
├─ src/main/res/    # Recursos, traduções e configuração Android
├─ src/test/        # Testes JVM
├─ src/androidTest/ # Testes de Room, migrações e Compose
└─ schemas/         # Histórico dos esquemas Room

gradle/             # Wrapper, catálogo de versões e critérios JVM
docs/               # Requisitos, UX, arquitetura, modelo de dados e verificações
.github/workflows/  # Integração contínua
```

</details>

## Base de dados e migrações

O esquema Room está na versão **10**, com esquemas exportados e migrações explícitas (incluindo 9 → 10).

A migração 9 → 10 acrescenta a moeda das alocações de orçamento, os dias/meses de referência das recorrências e as conversões dos saldos iniciais:

- As alocações antigas recebem a moeda de apresentação da instalação, mantendo os montantes.
- As fusões antigas de categorias são normalizadas para o destino final.
- As taxas incompatíveis com a data do movimento são removidas, para posterior atualização.
- Se uma referência de calendário já se perdeu numa versão antiga, usa-se a próxima data guardada; a intenção original não é reconstruível.

Qualquer alteração ao esquema deve incluir migração, esquema exportado e cobertura de preservação de dados.

## Testes e CI

```powershell
.\gradlew.bat testDebugUnitTest               # testes unitários JVM
.\gradlew.bat lintDebug                       # Android lint
.\gradlew.bat compileDebugAndroidTestKotlin   # compilar testes instrumentados (sem dispositivo)
.\gradlew.bat connectedDebugAndroidTest       # testes instrumentados (dispositivo/emulador)
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleRelease   # verificação completa sem dispositivo
```

Em Linux/macOS, usar `./gradlew`.

| Resultado | Localização |
|---|---|
| Relatório JVM | `app/build/reports/tests/testDebugUnitTest/` |
| Resultados JVM (XML) | `app/build/test-results/testDebugUnitTest/` |
| Relatório lint | `app/build/reports/lint-results-debug.html` |
| Relatórios instrumentados | `app/build/reports/androidTests/` |

**Cobertura.** Os testes JVM cobrem cálculos com despesas negativas, moedas, recorrências, previsões, limites de importação, segurança CSV, cifra e validação de backups, e parsing OCR. Os instrumentados cobrem migrações Room, integridade dos dados e interações Compose.

**Última verificação local (12 de outubro de 2026):** 74 testes JVM sem falhas; APKs debug e release (sem assinatura) gerados; lint com 0 erros e 48 avisos. Os testes instrumentados foram compilados mas **não executados** (sem dispositivo ou emulador). A autenticação em diferentes dispositivos e as migrações da revisão atual ainda precisam desses testes. Estes resultados descrevem apenas essa revisão.

**GitHub Actions** (`.github/workflows/blank.yml`), em pushes e pull requests para `main` ou manualmente:

- Build debug e release, testes JVM e lint.
- Testes instrumentados em emuladores API 29 e 35.
- Publicação dos relatórios como artefactos.

O workflow não configura assinatura nem publicação na loja.

## Limitações

- Fase alpha: assinatura de produção, publicação na loja, materiais legais e validação para distribuição comercial por concluir.
- As taxas de câmbio dependem da disponibilidade e cobertura do serviço externo.
- O agendamento de recorrências e lembretes depende do Android e não garante hora exata.
- O OCR pode produzir resultados incorretos.
- Os limites de ficheiros e snapshots restringem os volumes importáveis ou restauráveis.
- **Fora do âmbito atual:** sincronização entre dispositivos, nuvem, Open Banking, contas partilhadas, categorização por aprendizagem automática e deteção avançada de subscrições ou anomalias. A sua inclusão exige decisão explícita de âmbito.

## Documentação e contribuição

| Documento | Conteúdo |
|---|---|
| [Requisitos funcionais](docs/02-functional-requirements.md) | Funcionalidades e regras previstas |
| [Especificação UX/UI](docs/03-ux-ui-specification.md) | Organização e comportamento da interface |
| [Arquitetura e stack](docs/04-architecture-and-stack.md) | Decisões técnicas |
| [Modelo de dados](docs/05-database-design.md) | Estrutura e relações da base de dados |
| [Estrutura e roadmap](docs/06-project-structure-and-roadmap.md) | Organização e plano de desenvolvimento |
| [Correções e verificação](docs/07-hardening-and-verification.md) | Integridade financeira, segurança, desempenho e limites |

Alguns documentos descrevem o plano original e podem anteceder a implementação; o código e a configuração do repositório prevalecem.

**Para contribuir:**

1. Descrever o problema e o comportamento esperado.
2. Executar os testes relevantes.
3. Atualizar a documentação afetada.
4. Garantir que alterações à base de dados preservam os registos existentes.

Não incluir dados financeiros reais, palavras-passe, chaves de assinatura nem ficheiros de configuração local em commits ou relatórios de erros.

## Licença

O repositório **não contém licença** de utilização ou distribuição: nenhuma licença open source foi atribuída. Os direitos de terceiros sobre as dependências mantêm-se.
