# Northstar Money

Aplicação de finanças pessoais para Android, desenvolvida em Kotlin e Jetpack Compose. Permite gerir contas, movimentos, orçamentos e objetivos com os dados guardados no dispositivo.

**Estado:** alpha · **Versão:** 0.1.0 · **Android:** 8.0 ou superior (API 26+) · **Idiomas:** português e inglês.

Este é um projeto pessoal de aprendizagem, desenvolvido com assistência de ferramentas de IA. As funcionalidades implementadas e os testes existentes não equivalem a uma auditoria profissional ou à validação completa para distribuição comercial.

## Índice

- [Funcionalidades](#funcionalidades)
- [Começar a utilizar](#começar-a-utilizar)
- [Regras financeiras e câmbio](#regras-financeiras-e-câmbio)
- [Privacidade e segurança](#privacidade-e-segurança)
- [Backups e recuperação](#backups-e-recuperação)
- [Importação e exportação](#importação-e-exportação)
- [Instalação e compilação](#instalação-e-compilação)
- [Arquitetura e tecnologias](#arquitetura-e-tecnologias)
- [Base de dados e migrações](#base-de-dados-e-migrações)
- [Testes e integração contínua](#testes-e-integração-contínua)
- [Limitações e âmbito](#limitações-e-âmbito)
- [Documentação e contribuição](#documentação-e-contribuição)
- [Licença](#licença)

## Funcionalidades

### Contas e movimentos

- Contas de dinheiro, conta corrente, poupança e crédito, com moeda e saldo inicial próprios.
- Criação e edição de receitas, despesas e transferências.
- Transferências entre moedas com valores enviados e recebidos separados.
- Pesquisa e filtros de movimentos, com carregamento de 200 registos por página.
- Marcação de movimentos como conferidos e reconciliação com extratos.
- Arquivo e recuperação de contas e recuperação de movimentos eliminados recentemente.
- Categorias de receitas e despesas, com renomeação, arquivo, restauro e fusões reversíveis.
- Anexos de recibos, seleção de imagens, fotografia e OCR no dispositivo para extrair comerciante, data e montante.

### Planeamento e relatórios

- Orçamentos mensais por categoria, com moeda de alocação explícita e transição do saldo do mês anterior.
- Objetivos de poupança e histórico de contribuições editável.
- Perfis de dívida com taxa de juro, pagamento mínimo e dia de vencimento.
- Movimentos recorrentes com pausa, retoma e lançamento automático das ocorrências vencidas.
- Resumo de saldos, receitas, despesas e saldo líquido mensal.
- Gráficos e alternativas de leitura acessíveis.
- Previsão a 30 dias, com indicação de câmbios estimados ou em falta.
- Exportação CSV, resumo mensal PDF e livro Excel com movimentos, contas, orçamentos, resumo e taxas de câmbio.

### Utilização e acessibilidade

- Configuração inicial guiada.
- Navegação adaptada a telemóveis, tablets e dispositivos dobráveis.
- Interface em português e inglês, com descrições semânticas e suporte para texto maior.
- Ocultação persistente dos valores monetários.
- Bloqueio opcional por biometria forte ou credencial do dispositivo.
- Widget Android com saldo e movimentos recentes, respeitando as opções de privacidade.
- Lembrete diário opcional de revisão financeira.

## Começar a utilizar

1. Instalar uma compilação debug num dispositivo ou emulador compatível.
2. Concluir a configuração inicial, escolhendo a moeda de apresentação e criando a primeira conta.
3. Confirmar as categorias e registar receitas, despesas ou transferências.
4. Definir orçamentos, objetivos e recorrências conforme necessário.
5. Nas opções da aplicação, configurar a privacidade e criar um backup completo protegido por palavra-passe.

O registo manual e a consulta dos dados locais funcionam sem ligação à Internet. A obtenção de novas taxas de câmbio precisa de rede. As imagens de recibos podem fornecer sugestões por OCR; os dados extraídos devem ser revistos antes de guardar.

## Regras financeiras e câmbio

Os montantes são guardados como inteiros de 64 bits em **unidades mínimas da moeda**. Por exemplo, `12345` representa 123,45 EUR; `12345` JPY representa 12 345 ienes. Os movimentos de despesa têm sinal negativo e os de receita têm sinal positivo. Os totais de despesas apresentam a magnitude positiva.

Cada conta mantém a sua moeda. A moeda de apresentação serve para consolidar os relatórios, recorrendo a conversões de movimentos e saldos iniciais estrangeiros. A aplicação consulta o serviço Frankfurter por HTTPS, enviando o par de moedas e a data solicitada. Os montantes, nomes de comerciantes e imagens dos recibos não fazem parte desses pedidos.

- As taxas obtidas ficam guardadas localmente e podem ser reutilizadas quando a data e as moedas correspondem.
- Novas conversões são processadas em segundo plano através de WorkManager, com concorrência e cache limitadas.
- Uma mudança de data ou de moeda de apresentação exige uma taxa correspondente.
- Totais com conversões em falta são assinalados como incompletos.
- Os orçamentos mantêm os valores e a moeda de alocação originais quando a moeda de apresentação muda.
- As previsões usam taxas disponíveis como estimativas; não garantem o câmbio futuro.

Os cálculos mensais respeitam os limites do mês local. As recorrências preservam o dia e mês de referência para lidar com meses curtos e anos bissextos. O trabalho de lançamento é agendado no arranque e periodicamente a cada seis horas; a execução efetiva depende do agendamento do Android.

## Privacidade e segurança

A aplicação não inclui registo de utilizadores, sincronização na nuvem, ligação bancária, publicidade ou analytics. Os registos ficam no armazenamento privado da aplicação. A configuração Android desativa o backup automático da aplicação (`allowBackup=false`); os backups completos são criados pelo utilizador.

| Proteção | Comportamento |
|---|---|
| Bloqueio da aplicação | Biometria forte ou credencial do dispositivo, com nova autenticação depois de sair da atividade |
| Ocultação de valores | Preferência persistente para mascarar montantes na interface |
| Captura de ecrã | Protegida enquanto o bloqueio ou a ocultação de valores estiverem ativos |
| Widget | Oculta montantes e detalhes de comerciantes nas mesmas condições |
| OCR | Reconhecimento de texto através de ML Kit no dispositivo |
| Backups completos | Cifra autenticada AES-256-GCM com chave derivada da palavra-passe |
| Importações | Limites de tamanho e validação antes da escrita ou substituição dos dados |

**A base de dados Room não usa uma camada adicional de cifra como SQLCipher.** O bloqueio da interface e a cifra dos backups têm funções diferentes: o bloqueio não cifra a base de dados local.

A permissão de Internet serve para obter taxas de câmbio. A permissão de notificações permite os lembretes, quando autorizada. A câmara é opcional; a aplicação usa os mecanismos Android de seleção e captura de documentos/imagens.

Ficheiros CSV, Excel e PDF exportados não são cifrados pela aplicação. O destino escolhido no seletor de ficheiros pode pertencer a outro serviço, incluindo um fornecedor de armazenamento na nuvem.

## Backups e recuperação

O backup completo contém o snapshot da base de dados: contas, categorias, movimentos e respetivas entradas, reconciliações, orçamentos, objetivos e contribuições, recorrências, dívidas, recibos e taxas de câmbio. Inclui também metadados de versão e moeda de apresentação. Não é uma cópia integral das preferências Android da aplicação.

O formato portátil atual utiliza AES-256-GCM, salt e IV aleatórios e PBKDF2-HMAC-SHA256 com **600 000 iterações**. A palavra-passe deve ter entre **12 e 128 caracteres**. A exportação de JSON cifrado é feita por streaming.

Ao restaurar, a aplicação:

1. Decifra o ficheiro e verifica a autenticidade do conteúdo.
2. Verifica a compatibilidade da versão e valida referências, moedas, sinais, cardinalidade dos movimentos e conversões.
3. Cria uma cópia local cifrada do estado anterior dentro da transação de substituição.
4. Substitui os registos de forma atómica e disponibiliza a recuperação do estado anterior.

A opção de desfazer o restauro usa a cópia local e a palavra-passe utilizada na recuperação. Essa cópia fica no dispositivo e não substitui um backup externo.

Backups antigos sem metadados da moeda de apresentação usam a moeda atualmente selecionada para interpretar as antigas alocações de orçamento. **Selecionar a moeda original antes de restaurar esses ficheiros.** Backups que exigem uma versão de base de dados superior à suportada são rejeitados.

Não existe um serviço de recuperação da palavra-passe do backup portátil. Guardar a palavra-passe e uma cópia do ficheiro num local adequado; desinstalar a aplicação ou apagar os seus dados elimina o armazenamento local.

## Importação e exportação

### CSV

O formato utiliza UTF-8, vírgulas como separador e este cabeçalho, pela ordem indicada:

```csv
date,type,payee,category,account,amount,currency
2026-10-12,EXPENSE,Supermercado,Alimentação,Conta principal,-2599,EUR
2026-10-12,INCOME,Salário,Salário,Conta principal,150000,EUR
```

As datas seguem `AAAA-MM-DD`. O campo `amount` contém um inteiro em unidades mínimas, sem separador decimal. A moeda deve corresponder à moeda da conta.

A importação aceita `INCOME` e `EXPENSE`. A conta e a categoria têm de existir e ser identificadas sem ambiguidade pelo nome; a categoria deve corresponder ao tipo de movimento. Transferências podem aparecer na exportação, mas **não são importadas pelo formato CSV atual**. O CSV também não contém todos os dados necessários para reconstruir a base de dados, pelo que não substitui o backup completo.

O importador valida os registos, deteta duplicados e faz a escrita de forma atómica. Se houver erros de validação, os movimentos não são importados. Os campos de texto exportados recebem proteção contra interpretação como fórmulas em folhas de cálculo; os valores numéricos mantêm o sinal.

### Excel e PDF

O Excel é escrito como `.xlsx`, com folhas para movimentos, contas, orçamentos, resumo e câmbios. O PDF contém o resumo mensal e pagina as linhas de orçamento. As exportações de movimentos em CSV e Excel incluem o conjunto completo, independentemente da página visível na interface.

### Limites de ficheiros e restauro

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

## Instalação e compilação

### Requisitos

- Android Studio compatível com a configuração Gradle do projeto, ou ferramentas Android de linha de comandos.
- Android SDK 36 e ferramentas de compilação correspondentes.
- JDK 21 para o daemon Gradle; o bytecode da aplicação tem como alvo Java 17.
- Dispositivo ou emulador Android API 26+ para executar a aplicação.
- Ligação à Internet para descarregar dependências na primeira compilação.

O wrapper fornece **Gradle 9.5.0**. Os critérios do daemon em `gradle/gradle-daemon-jvm.properties` selecionam **JetBrains JDK 21**; a primeira execução pode descarregar essa ferramenta. As versões de dependências estão definidas em `gradle/libs.versions.toml`.

### Obter o projeto

```bash
git clone https://github.com/moreirass/northstar-money.git
cd northstar-money
```

Abrir a pasta no Android Studio e permitir a sincronização Gradle. Configurar o SDK pelo IDE, pela variável `ANDROID_HOME` ou por `local.properties`, que não deve ser versionado. Exemplos:

```properties
# Windows: adaptar ao caminho instalado; as barras / evitam escapes.
sdk.dir=C:/Users/UTILIZADOR/AppData/Local/Android/Sdk
```

```properties
# Linux: adaptar ao caminho instalado.
sdk.dir=/home/UTILIZADOR/Android/Sdk
```

### Compilar e instalar

| Ação | PowerShell / Windows | Linux / macOS |
|---|---|---|
| APK debug | `.\gradlew.bat assembleDebug` | `bash ./gradlew assembleDebug` |
| Instalar debug num dispositivo ligado | `.\gradlew.bat installDebug` | `bash ./gradlew installDebug` |
| APK release | `.\gradlew.bat assembleRelease` | `bash ./gradlew assembleRelease` |

Artefactos gerados:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

O APK debug usa a assinatura de desenvolvimento. O build release ativa R8 e redução de recursos, mas **não tem assinatura de distribuição configurada**. O APK release sem assinatura precisa de ser assinado antes da instalação ou distribuição.

Se o SDK não for encontrado, confirmar o caminho em `local.properties` ou `ANDROID_HOME`. Se o Gradle não arrancar, verificar a instalação Java e os critérios do daemon. Os testes instrumentados exigem um dispositivo visível em `adb devices`.

## Arquitetura e tecnologias

O projeto contém um módulo Android, `app`, organizado em camadas:

```text
Compose UI → FinanceViewModel → FinanceRepository → OfflineFinanceRepository
                                                     ├─ Room / SQLite
                                                     ├─ adaptadores de importação e exportação
                                                     ├─ backups e OCR
                                                     └─ cliente de câmbio e workers
DataStore → preferências de utilização e privacidade
```

| Área | Tecnologia / configuração do repositório |
|---|---|
| Linguagem | Kotlin 2.3.20 |
| Build | Android Gradle Plugin 9.3.1 · Gradle 9.5.0 |
| Interface | Jetpack Compose BOM 2026.06.00 · Material 3 · Navigation Compose |
| Persistência | Room 2.8.4 · SQLite · esquema versão 10 |
| Preferências | DataStore 1.2.1 |
| Trabalho em segundo plano | WorkManager 2.11.2 |
| Serialização | Kotlin Serialization 1.8.1 |
| Recibos | ML Kit Text Recognition 16.0.1 |
| Autenticação | AndroidX Biometric e credencial do dispositivo |
| SDK | mínimo 26 · compilação e alvo 36 |

As consultas agregadas de categorias e orçamentos são executadas em SQL. A lista de movimentos usa paginação; os observadores de recibos carregam metadados sem imagens ou texto OCR completo. Os observadores do widget só ficam ativos quando existe um widget instalado.

### Estrutura do repositório

```text
app/
├─ src/main/java/com/northstar/money/
│  ├─ core/       # Base de dados, preferências, segurança, navegação e interface
│  ├─ data/       # Repositório, câmbio, backups, importação/exportação, OCR e workers
│  ├─ domain/     # Modelos monetários, cálculos e contrato do repositório
│  ├─ feature/    # Estado financeiro e ViewModel
│  └─ widget/     # Widget Android
├─ src/main/res/  # Recursos, traduções e configuração Android
├─ src/test/      # Testes JVM
├─ src/androidTest/ # Testes de Room, migrações e Compose
└─ schemas/       # Histórico dos esquemas Room

gradle/           # Wrapper, catálogo de versões e critérios JVM
docs/             # Requisitos, UX, arquitetura, modelo de dados e verificações
.github/workflows/ # Integração contínua
```

## Base de dados e migrações

A versão atual do esquema Room é **10**. O projeto mantém esquemas exportados e migrações explícitas, incluindo a migração de 9 para 10.

Esta migração acrescenta a moeda das alocações de orçamento, os dias/meses de referência das recorrências e as conversões dos saldos iniciais. As alocações antigas recebem a moeda de apresentação da instalação, mantendo os montantes. As fusões antigas de categorias são normalizadas para o destino final e as taxas incompatíveis com a data do movimento são removidas para posterior atualização.

Quando uma referência de calendário já se perdeu numa versão antiga, a migração utiliza a próxima data guardada; não consegue reconstruir a intenção original. Alterações ao esquema devem incluir migração, esquema exportado e cobertura de preservação de dados.

## Testes e integração contínua

### Comandos locais

```powershell
# Testes unitários JVM
.\gradlew.bat testDebugUnitTest

# Análise Android lint
.\gradlew.bat lintDebug

# Compilar os testes instrumentados sem dispositivo
.\gradlew.bat compileDebugAndroidTestKotlin

# Executar testes instrumentados num dispositivo/emulador ligado
.\gradlew.bat connectedDebugAndroidTest

# Compilar ambas as variantes e executar verificações sem dispositivo
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleRelease
```

Em Linux/macOS, substituir `.\gradlew.bat` por `bash ./gradlew`.

| Resultado | Localização habitual |
|---|---|
| Relatório JVM | `app/build/reports/tests/testDebugUnitTest/` |
| Resultados JVM em XML | `app/build/test-results/testDebugUnitTest/` |
| Relatório lint | `app/build/reports/lint-results-debug.html` |
| Relatórios instrumentados | `app/build/reports/androidTests/` |

A suíte JVM cobre cálculos com despesas negativas, moedas, recorrências, previsões, limites de importação, segurança CSV, cifra e validação de backups e parsing OCR. Os testes instrumentados cobrem migrações Room, integridade dos dados e interações Compose.

### Estado da verificação local

Na verificação de **12 de outubro de 2026**, passaram **74 testes JVM sem falhas**. Foram gerados APKs debug e release sem assinatura. Uma execução local de lint terminou com **0 erros e 48 avisos**. Os testes instrumentados das alterações foram compilados, mas não executados localmente por não existir um dispositivo ou emulador ligado.

Estes resultados descrevem essa revisão; não garantem verificações de alterações posteriores. A autenticação nos diferentes dispositivos e as migrações da revisão atual ainda necessitam de execução dos testes instrumentados.

### GitHub Actions

O workflow `.github/workflows/blank.yml` executa:

- Build debug e release, testes JVM e lint.
- Testes instrumentados em emuladores API 29 e 35.
- Publicação dos relatórios como artefactos do workflow.

É acionado em pushes e pull requests para `main`, ou manualmente. A existência da configuração não significa que uma execução remota tenha passado. O workflow não configura assinatura nem publicação na loja.

## Limitações e âmbito

O projeto continua em fase alpha. A assinatura de produção, a publicação na loja, os materiais legais e a validação completa para distribuição comercial estão por concluir. Manter backups regulares de dados importantes.

As taxas dependem da disponibilidade e cobertura do serviço externo. O agendamento de recorrências e lembretes depende do Android e não garante execução numa hora exata. O OCR pode produzir resultados incorretos. Os limites de ficheiros e snapshots também limitam os volumes que podem ser importados ou restaurados.

Sincronização entre dispositivos, serviços na nuvem, ligações bancárias/Open Banking, contas partilhadas, categorização por aprendizagem automática e deteção avançada de subscrições ou anomalias estão fora do âmbito atual. A sua inclusão requer uma decisão explícita de âmbito.

## Documentação e contribuição

| Documento | Conteúdo |
|---|---|
| [Requisitos funcionais](docs/02-functional-requirements.md) | Funcionalidades e regras previstas |
| [Especificação UX/UI](docs/03-ux-ui-specification.md) | Organização e comportamento da interface |
| [Arquitetura e stack](docs/04-architecture-and-stack.md) | Decisões técnicas |
| [Modelo de dados](docs/05-database-design.md) | Estrutura e relações da base de dados |
| [Estrutura e roadmap](docs/06-project-structure-and-roadmap.md) | Organização e plano de desenvolvimento |
| [Correções e verificação](docs/07-hardening-and-verification.md) | Integridade financeira, segurança, desempenho e limites |

Alguns documentos descrevem o plano original e podem anteceder a implementação atual. O código e a configuração do repositório determinam o comportamento efetivo.

Para propor alterações, descrever o problema e o comportamento esperado, executar os testes relevantes e atualizar a documentação afetada. Alterações à base de dados devem preservar os registos existentes. Não incluir dados financeiros reais, palavras-passe, chaves de assinatura ou ficheiros de configuração local em commits ou relatórios de erros.

## Licença

O repositório não contém uma licença de utilização ou distribuição. Nenhuma licença open source foi atribuída ao projeto; os direitos de terceiros sobre as dependências mantêm-se.
