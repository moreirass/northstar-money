# Migração incremental para Java

Branch: `migrate-to-java`. Início: 23 de novembro de 2026.

**Estado: em curso; a aplicação ainda não está integralmente migrada para Java.**
Compose, Room com entidades/DAOs Kotlin, DataStore, coroutines e serialização Kotlin
continuam necessários durante esta transição.

## Contratos preservados

- Esquema Room 10 e SQL das migrações 1→10 inalterados; sem migração destrutiva.
- Dinheiro em `long`, conversão com `BigDecimal` e arredondamento HALF_EVEN.
- Envelope NSMB v2: PBKDF2-HMAC-SHA256, 600 000 iterações, AES-256-GCM,
  salt de 16 bytes, IV de 12 bytes, cabeçalho de 12 bytes autenticado como AAD.
- Mesmos cabeçalhos CSV, sinais, neutralização de fórmulas e limites de entrada.
- JSON, chaves de preferências, permissões, widget, traduções e FileProvider mantidos.
- O codec Java usa Gson 2.11.0 para ler e escrever JSON por streaming. Os campos
  são tratados explicitamente para preservar a ordem, os valores nulos e a precisão
  dos inteiros de 64 bits, incluindo em builds minificadas.

## Sequência e critérios de passagem

| Fase | Trabalho | Critério |
|---|---|---|
| 1 | Higiene, ícones e contratos de compatibilidade | Workflow fixado por SHA; testes diferenciais antes da substituição |
| 2 | Dinheiro, calendário, CSV, streams e cifra Java | Testes JVM antigos e novos aprovados; leitura cruzada Kotlin/Java |
| 3 | Arranque e leitura da moeda para migração | Construção de Room sem espera na main thread; teste com DataStore JPY e falha de leitura |
| 4 | Modelos, DAOs, migrações e JSON em Java | Mesmos esquemas exportados; testes 1→10 e fixtures JSON byte a byte |
| 5 | Repositórios por domínio e executores | Mesmas transações atómicas; nenhuma operação de disco/rede na main thread |
| 6 | Fragments, Material Views, ViewBinding e ViewModels por feature | Paridade de todos os formulários, privacidade, navegação, acessibilidade e estados |
| 7 | Workers, widget e remoção do stack Kotlin | annotationProcessor; remoção de kapt e flags temporárias; release minificada verificada |

A fase 4 está em curso. O codec de backup e o SQL das nove migrações passaram
para Java. A comparação com as implementações Kotlin abrangeu as 13 tabelas do
backup e as 42 instruções SQL. Após a integração, passaram os 90 testes JVM
e a compilação dos testes instrumentados de debug.

`AccountEntity` é a primeira entidade Room em Java. O teste do backup usa
`app/src/test/resources/backup/full-v3-kotlin.json`, produzido pelo serializador
Kotlin antes da substituição da entidade. A amostra contém as 13 tabelas e permite
comparar os bytes originais mesmo depois de migrar as restantes entidades.
O Room regenerou o esquema v10 completo com a entidade Java e o conteúdo coincidiu
com a versão em Git. Passaram os 91 testes JVM e a compilação dos testes instrumentados de debug.

`CategoryEntity` também passou para Java. A verificação em SQLite com o SQL
exportado pelo Room confirmou que uma fusão pode referenciar uma categoria
inserida mais tarde na mesma transação, que o par tipo/nome é único e que apagar
o destino da fusão limpa a referência na categoria de origem.

`TransactionEntity` e `TransactionEntryEntity` passaram juntas para Java. O
teste de backup de uma transferência editada compara os dois lançamentos após a
leitura, incluindo contas, moedas, sinais, montantes de 64 bits e conciliação.
No SQLite, o esquema exportado preservou os valores da transferência EUR→USD,
bloqueou a eliminação de uma conta com lançamentos e eliminou os lançamentos
quando o movimento foi apagado.

`TransactionExchangeRateEntity` guarda em Java a taxa histórica de cada lançamento.
O teste de backup cobre uma cotação pendente, com taxa e montante nulos, e uma
cotação disponível de 1 000 001 milionésimos. A validação recalcula um montante
convertido superior, em valor absoluto, a 2⁵³ unidades mínimas.
As restantes oito entidades e os DAOs continuam em Kotlin.
As fases 5–7 estão pendentes. Não remover kapt ou os plugins Compose/serialization
enquanto o código que os exige ainda estiver em produção. Em particular,
`android.builtInKotlin=false` e `android.newDsl=false` só podem ser removidas com a
substituição do processamento atual de entidades Kotlin.

## Verificação de equivalência

As implementações Kotlin de referência foram conservadas em `src/test/.../reference`.
Antes da substituição em produção, os testes compararam:

- bytes cifrados com salt e IV determinísticos, leitura em ambas as direções,
  exportação por streaming, alteração do AAD e parâmetros inválidos;
- backups legados com chave existente, chave ausente, truncagem, excesso de tamanho
  e etiqueta de autenticação inválida;
- parsing, formatação por locale, igualdade, hash, overflow e moedas com diferentes
  números de casas decimais;
- conversões positivas/negativas e âncoras de recorrências em meses curtos;
- bytes CSV com Unicode, aspas, campos vazios e fórmulas; limites dos streams.

Primeira passagem antes da integração: **86 testes JVM, zero falhas**.
`compileDebugAndroidTestKotlin` também passou. Estes resultados não representam
execução dos testes instrumentados.

Depois da integração das implementações Java em produção: **87 testes JVM, zero
falhas**, incluindo o encaminhamento do formato portátil/legado sem acesso à chave
legada no caso portátil. Os testes instrumentados voltaram a compilar.

Verificação conjunta concluída em **24 de novembro de 2026**:

- `assembleDebug`: aprovado.
- `testDebugUnitTest`: aprovado, 87 testes e zero falhas.
- `lintDebug`: aprovado, zero erros e 52 avisos.
- `assembleRelease`: aprovado, R8 e redução de recursos ativos, sem a flag
  `android.r8.strictFullModeForKeepRules=false`.
- `assembleRelease assembleReleaseAndroidTest -PreleaseSmoke=true`: aprovado;
  gerados o APK minificado para teste e o APK de instrumentação, ambos compilados
  com as regras R8 verificadas. **Não foram instalados nem executados.**
- `git diff main -- app/schemas`: sem alterações face a `7f54232`.

A release normal gerada não tem assinatura de distribuição. Estes resultados
validam esta fase incremental, não a migração completa nem os fluxos em runtime.

## Teste de fumo release

`scripts/release-smoke.ps1` seleciona um dispositivo autorizado e executa
`connectedReleaseAndroidTest -PreleaseSmoke=true` com um conjunto delimitado:
arranque real de `MainActivity`, onboarding, navegação, edição de movimentos,
importação CSV atómica, backup/restauro/desfazer e moeda da migração.

A propriedade apenas seleciona a release para instrumentação e aplica assinatura
debug para instalação. Mantém R8, redução de recursos e `debuggable=false`. Sem a
propriedade, a release mantém-se sem assinatura de distribuição. O perfil segue a
[configuração oficial de variantes de teste](https://developer.android.com/studio/test/advanced-test-setup).

O workflow executa esse conjunto depois dos testes debug nos emuladores API 29 e 35.
Esta configuração não é evidência de uma execução remota aprovada.

O APK de instrumentação usa uma única regra `dontwarn` para
`com.google.errorprone.annotations.MustBeClosed`, anotação de análise estática
referenciada pelo AndroidX Test mas ausente no classpath transitivo. A regra foi
indicada pelo relatório `missing_rules.txt` do R8 e está isolada em
`app/proguard-test-rules.pro`; não desativa verificações no APK de produção.

## Arranque e compatibilidade de moeda

A construção de Room recebe um `CompletableFuture` preenchido pela leitura
assíncrona de DataStore. `CurrencyAwareMigration` espera apenas quando Room executa
a migração no executor de abertura, rejeita execução na main thread e propaga falhas
de leitura. Nunca troca uma moeda desconhecida por EUR silenciosamente.

A leitura usa o mesmo DataStore e a mesma chave `base_currency_code`; não cria um
segundo armazenamento de preferências. A ponte com coroutines será removida na
fase de DataStore Java. A espera do futuro não chama `runBlocking` nem lê o ficheiro
de preferências diretamente.

## Limitações da verificação local

O ADB não encontrou dispositivos ligados nesta sessão. Os testes de migração,
Keystore Android, UI, widget, autenticação e release em execução precisam de um
dispositivo/emulador. Não considerar compilação de testes como execução aprovada.

A primeira verificação conjunta de debug/release/lint encontrou pressão extrema de
memória no anfitrião (cerca de 5 MiB livres) e falhou no compilador incremental Kotlin
com um ficheiro temporário em falta. A repetição usa um worker, compilação Kotlin no
processo Gradle e heap de 2 GiB. Em PowerShell, as propriedades com pontos precisam
de ser passadas entre aspas:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleRelease --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-Pkotlin.incremental=false' '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8' --console=plain
```

Estas opções são locais à verificação; não alteram os requisitos JDK 21/bytecode 17.

O roadmap local `08-local-improvements-roadmap.md` antecede esta tarefa e foi
preservado sem alterações nem inclusão nos commits.
