# Roteiro de Segurança e Plataforma

*(Read in English: [security-roadmap.md](security-roadmap.md))*

## Como ler este documento

Este é um backlog vivo de ideias de reforço de segurança e direções de plataforma para o Rikiki
Vault, não um roteiro comprometido — os itens ficam aqui depois de discutidos e passam a milestone
dedicada quando forem priorizados. Cada item explica porque importa e, para os de prioridade alta,
tem detalhe técnico suficiente para arrancar a implementação sem redesenhar do zero.

## Prioridade: Alta

### 1. Cifrar a chave privada em repouso com passphrase — ✅ Implementado

Entregue: `KeyStorePort.isPassphraseProtected()`/`load(char[])`/`changePassphrase(...)`, o envelope
"RVPK" (`PrivateKeyEnvelopeCodec`, PBKDF2-HMAC-SHA256 + AES-GCM), os comandos CLI `set-passphrase`/
`remove-passphrase`, e o ecrã de desbloqueio + Configurações → Segurança na app desktop. Com cache
de sessão via `PassphraseCachingKeyStorePort` (um pedido por invocação da CLI / por sessão da GUI
aberta), em vez do desenho original "nunca fazer cache" esboçado abaixo — ver
`docs/faq/07-private-key-is-not-password-protected.pt.md` para a justificação. O resto desta secção
fica como registo do desenho original.

**Porquê:** o `LocalKeyStoreAdapter`
(`core/src/main/java/io/github/jlmc/rikikivault/core/adapters/keystore/LocalKeyStoreAdapter.java`)
grava `private.key` como bytes PKCS8 DER em claro, protegidos só por permissões de ficheiro
(`writeFileSecurely`/`restrictToOwnerBestEffort`). Quem conseguir ler esse ficheiro — outra conta
do sistema operativo, uma sessão já destrancada, um disco retirado da máquina — usa-o diretamente;
não há nenhum segredo além das permissões do ficheiro entre essa pessoa e tudo o que este vault
alguma vez publicou. É a lacuna mais explorável de todo o desenho, já assinalada como limitação
conhecida em `docs/faq/07-private-key-is-not-password-protected.pt.md`.

**Estado atual (confirmado por leitura direta do código):**
- `LocalKeyStoreAdapter`: `PRIVATE_KEY_FILE="private.key"`, `PUBLIC_KEY_FILE="public.key"`,
  `KEY_ALGORITHM="X25519"`. `save(MachineIdentity)` recusa sobrescrever uma chave já existente
  (`exists()` → `MachineIdentityAlreadyExistsException`), grava bytes PKCS8/X.509 DER em claro.
  `load()` lança `PrivateKeyNotFoundException` quando o ficheiro não existe. Implementa
  `KeyStorePort { void save(MachineIdentity); MachineIdentity load(); boolean exists(); }`.
- `MachineIdentity` é um record simples `(KeyFingerprint id, PublicKey publicKey, PrivateKey
  privateKey, String keyAlgorithm)` — um valor em memória, sem noção de estado "protegido".
- Só dois sítios de composição constroem um `LocalKeyStoreAdapter`: o `VaultContext.at()` privado
  do `cli/.../Main.java`, e o `VaultContext.at(Path)` do `gui-javafx` — os únicos dois pontos que
  precisariam de saber obter uma passphrase.
- `VaultConfig` (`identityDirectory`, `encryptionSettings`), carregado/gravado por
  `YamlConfigFileAdapter`, é o sítio natural para verificar "esta identidade precisa de
  passphrase", sem precisar de um campo novo — a resposta pode vir do próprio formato do ficheiro
  da chave em disco (ver abaixo).
- Não existe nenhum KDF (PBKDF2/Argon2/scrypt) em lado nenhum do código ainda. A convenção
  criptográfica já existente a seguir está em
  `JceHybridEncryptionAdapter`/`X25519HkdfAesGcmKeyWrapStrategy`:
  `Cipher.getInstance("AES/GCM/NoPadding")`, nonce aleatório de 12 bytes via `SecureRandom`,
  `GCMParameterSpec(128, nonce)`, e um `wipe(byte[])` que zera material de chave num `finally`.

**Desenho:**
- Novo formato de envelope em disco para `private.key` quando protegido por passphrase,
  auto-descritivo via um prefixo de magic bytes, para o `LocalKeyStoreAdapter` detetar sozinho
  claro vs. cifrado sem precisar de nenhum campo novo em `VaultConfig`: `"RVPK" (4 bytes) | versão
  (1 byte) | id do KDF (1 byte, 0 = PBKDF2WithHmacSHA256) | iterações (int) | comprimento+salt |
  nonce (12 bytes) | texto cifrado AES-GCM dos bytes PKCS8 (com tag incluída)`.
- Usar `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` (já vem no JDK — sem dependência
  nova) com um número alto de iterações (≥600.000, recomendação atual da OWASP para
  PBKDF2-SHA256) para derivar uma KEK de 256 bits a partir da passphrase + salt aleatório; cifrar
  os bytes PKCS8 com `Cipher`/`GCMParameterSpec`, seguindo exatamente o estilo já usado em
  `JceHybridEncryptionAdapter`. O ficheiro da chave pública nunca é cifrado (não é segredo).
- `KeyStorePort` ganha operações cientes de passphrase, mantendo o `save`/`load` sem argumentos
  exatamente como hoje (compatível com o passado, só opt-in):
  ```java
  MachineIdentity load(); // comportamento inalterado; lança PassphraseRequiredException se protegida
  MachineIdentity load(char[] passphrase);
  void save(MachineIdentity identity); // inalterado: grava uma chave sem cifra, omissão de hoje
  void save(MachineIdentity identity, char[] passphrase);
  void changePassphrase(char[] atualOuNull, char[] novaOuNull); // adicionar / mudar / remover
  boolean isPassphraseProtected();
  ```
  Novas `PassphraseRequiredException`/`InvalidPassphraseException extends RikikiVaultException`.
- CLI: novos comandos `set-passphrase`/`remove-passphrase`; lê a passphrase via
  `System.console().readPassword(...)` quando há uma consola real (sem eco no terminal), com um
  *fallback* documentado para ambientes sem consola. `Main.VaultContext.at()` apanha
  `PassphraseRequiredException` e pede a passphrase uma vez por invocação.
- GUI: `App.java`'s `openVault(...)` verifica `keyStorePort.isPassphraseProtected()` antes de
  construir o `VaultContext`, e se for verdadeiro mostra um novo `PassphrasePromptController`/
  `passphrase-prompt-view.fxml` pequeno, reutilizando o padrão de `PasswordField` mascarado + olho
  já construído em `GitAuthSettingsPanel.wireReveal(...)`. Uma nova secção "Segurança" em
  Configurações permite definir/mudar/remover a passphrase via `changePassphrase(...)`.
- A passphrase em si só existe como `char[]`, apagada logo a seguir a derivar a KEK; a
  `MachineIdentity` já carregada continua a viver em memória durante a sessão exatamente como hoje
  (sem cache nova da própria passphrase).
- Adoção estritamente opt-in — uma `private.key` já existente em claro continua a carregar
  exatamente como hoje; nada quebra para quem não ativar isto.

**Testes:** `LocalKeyStoreAdapterTest` — round-trip com passphrase; passphrase errada →
`InvalidPassphraseException`; carregar uma chave protegida sem passphrase →
`PassphraseRequiredException`; todos os testes existentes do formato em claro continuam a passar
sem alteração (prova a compatibilidade); `changePassphrase` a cobrir adicionar/mudar/remover.

**Perguntas em aberto antes de implementar:** número de iterações vs. latência percebida em
máquinas mais antigas; se a GUI deve alguma vez guardar a passphrase em memória durante o resto de
uma sessão (recomendação: não, voltar a pedir sempre que a identidade precisar de ser carregada do
disco, para a propriedade de segurança continuar a fazer sentido) — interage diretamente com a
proposta #3 (bloqueio automático) abaixo.

---

### 2. Detetar destinatários novos inesperados durante o `pull`

**Porquê:** `recipients.json` vive na mesma pasta `vault/` git-tracked que `manifest.json`, e viaja
exatamente pelo mesmo `git pull` que tudo o resto. Se um git host comprometido, um colaborador
malicioso, ou um MITM alguma vez adicionasse a chave pública de um atacante a esse ficheiro, o
próximo `publish` de qualquer máquina legítima reencriptaria silenciosamente tudo também para esse
atacante — e nada na app de hoje alguma vez o mencionaria. O `PullVaultService` já tem o sítio
perfeito para notar isto: já compara `manifest.json` antes/depois do pull para detetar conflitos; a
mesma técnica de snapshot antes/depois funciona exatamente igual para `recipients.json`.

**Estado atual (confirmado por leitura direta do código):** `PullVaultService.pull()`
(`core/src/main/java/io/github/jlmc/rikikivault/core/application/usecase/PullVaultService.java:76-128`)
captura `before = indexByPlaintextPath(manifestPort.load())` antes de chamar
`gitRepositoryPort.pull()`, depois `after = indexByPlaintextPath(manifestPort.load())` a seguir, e
compara por hash para construir `VaultConflict`s. `RecipientRegistryPort.load()/save()`
(`JsonRecipientRegistryFileAdapter`) não é tocado em lado nenhum deste método hoje. `PullResult`
tem hoje exatamente quatro campos: `updatedPaths`, `deletedPaths`, `conflicts`
(`List<VaultConflict>`), `uncommittedLocalChangesAtStart`. `Recipient` é `(String label,
KeyFingerprint fingerprint, PublicKey publicKey)`; `RecipientRegistry` é `(int version,
List<Recipient> recipients)`.

**Desenho:**
- `PullVaultService` ganha uma dependência de construtor `RecipientRegistryPort`.
- Em `pull()`, captura `recipientsBefore` (indexado por `fingerprint().hex()`) junto ao snapshot
  `before` já existente do manifesto, e `recipientsAfter` junto ao `after`. Diferença por conjunto
  de fingerprints: entradas só em `recipientsAfter` → `newRecipients`; só em `recipientsBefore` →
  `removedRecipients`.
- `PullResult` ganha dois campos novos: `List<Recipient> newRecipients`, `List<Recipient>
  removedRecipients` (vazios quando nada muda, mesma convenção `List.copyOf` de hoje).
- Isto é informativo/de aviso, não bloqueante: quando `pull()` devolve, o `git pull` já fundiu
  `recipients.json` — a porta só expõe um `pull()` do repositório inteiro, por isso não há forma
  limpa de "reter" só um ficheiro a meio do pull sem uma cirurgia muito maior no
  `GitRepositoryPort`. O valor de segurança está em tornar a alteração impossível de ignorar, não
  em impedi-la de chegar.
- GUI (`MainWindowController.onPull`, `PullResultController`/`pull-result-view.fxml`): nova secção
  de aviso visualmente distinta (mesmo peso da secção de conflitos já existente) lista
  `newRecipients` com label + fingerprint; uma secção informativa mais suave lista
  `removedRecipients`.
- CLI (`Main.runPull`): imprime um bloco claramente assinalado `"Novos destinatários detetados
  neste pull:"`, e um bloco mais suave `"Destinatários removidos:"`, usando o
  `RecipientRegistryPort` já disponível no `VaultContext` (só falta ligá-lo ao `PullVaultService`).
  Chaves i18n novas em `messages_{pt,en}.properties` e `CliMessages`.
- Sem alteração a `AuthorizeMachineService`/`RevokeMachineService` — continuam a ser a única forma
  legítima de mudar `recipients.json`; esta funcionalidade é sobre o lado do `pull` notar sempre
  quando esse ficheiro mudou, seja qual for o motivo.

**Testes:** `PullVaultServiceTest` (com o `FakeRecipientRegistryPort` já existente) —
destinatários inalterados → as duas listas vazias; um destinatário adicionado/removido entre
antes/depois → aparece corretamente; um pull com conflitos de manifesto e alterações de
destinatários ao mesmo tempo mostra os dois independentemente. Novo caso em
`EndToEndClonePullTest`: a máquina A autoriza a máquina C, depois a máquina B (uma terceira,
independente) faz pull e vê C em `newRecipients` — prova o caminho com adaptadores reais, não só
fakes.

**Pergunta em aberto:** se vale a pena um registo persistente de "destinatários reconhecidos" para
uma revogação seguida de reautorização do mesmo fingerprint não voltar a ser assinalada —
recomendação: não fazer isto na v1 (assinalar sempre que houver alteração desde o último snapshot
local); eventos de reautorização são raros o suficiente para o ruído extra ser uma troca aceitável
e muito mais simples.

---

### 3. Bloqueio automático da GUI por inatividade

**Porquê:** mesmo com a chave protegida por passphrase (#1), uma sessão já destrancada deixada
aberta — uma máquina de onde te afastaste, um portátil a correr sem vigilância — expõe qualquer
preview/edição decifrada que esteja no ecrã e deixa quem estiver ao teclado continuar a trabalhar
como máquina autorizada. `docs/faq/07-private-key-is-not-password-protected.pt.md` já nomeia
exatamente este cenário como limitação sem solução ("proteção contra alguém a usar a tua própria
sessão já iniciada"). Um bloqueio automático por inatividade é a mitigação direta.

**Estado atual (confirmado por leitura direta do código):** `App.java` constrói uma única `Scene`
uma vez (`start(Stage)`) e só troca a sua raiz (`stage.getScene().setRoot(...)`) entre
Welcome/InitOrClone/JanelaPrincipal — não existe nenhum filtro de eventos nessa `Scene` hoje. O
`BorderPane` raiz do `MainWindowController` tem a toolbar em `top`, a rail de navegação em `left`,
e só o conteúdo de ficheiros/configurações/gerir-acesso dentro do `StackPane` `center`
(`centerContainer`) — por isso um overlay confinado a `centerContainer` não cobriria a toolbar nem
a rail; um bloqueio real de janela inteira tem de ficar acima de todo o `BorderPane`. O
`startAutoRefresh()` mantém hoje o seu `Timeline` de 3 segundos como variável **local**, não campo
— nunca é pausado hoje. `GitAuthSettingsPanel.wireReveal(...)` já tem um padrão funcional de
`PasswordField` mascarado + olho, reutilizável. `Dialogs` só oferece modais bloqueantes baseados em
`Alert` — não servem para um ecrã de bloqueio embutido.

**Desenho:**
- Um único filtro de eventos ao nível da `Scene` para `MouseEvent.ANY`/`KeyEvent.ANY`, instalado
  uma vez em `App.start()` logo a seguir a `stage.setScene(scene)`, que reinicia um temporizador de
  inatividade a cada interação.
- `lock()`/`unlock()` vivem em `App.java` (não no `MainWindowController`), usando exatamente o
  mesmo mecanismo `stage.getScene().setRoot(...)` já usado em todas as outras transições de ecrã:
  guarda a raiz atual antes de trocar para uma nova raiz de ecrã de bloqueio, restaura-a
  (`stage.getScene().setRoot(raizAnterior)`) ao destrancar com sucesso. Reutiliza o padrão de
  navegação já existente em vez de inventar um conceito de overlay novo.
- Ao bloquear: o `Timeline` de auto-refresh do `MainWindowController` tem de ser pausado — isto
  exige primeiro promovê-lo de variável local a campo. O conteúdo decifrado visível
  (editor/preview) é limpo do ecrã; o `VaultContext`/`MachineIdentity` subjacentes continuam
  residentes em memória durante a sessão (limpar bytes decifrados já renderizados no ecrã é o
  objetivo realista — a JVM não dá nenhuma garantia limpa de apagar memória heap arbitrária).
- Destrancar reutiliza o padrão `PasswordField`+olho de `GitAuthSettingsPanel`. Dois níveis,
  assinalados explicitamente em vez de disfarçados:
  - Se a proposta #1 (identidade protegida por passphrase) estiver implementada: destrancar volta
    a verificar essa mesma passphrase, tentando rederivar a chave — controlo de acesso com
    significado real.
  - Caso contrário: destrancar só pode ser um gesto "clicar para dispensar" — um dissuasor de tipo
    écran de privacidade/"shoulder surfing", não controlo de acesso real. Esta limitação tem de
    ficar documentada explicitamente onde quer que a funcionalidade seja descrita, para nunca ser
    vendida a mais.
- Nova secção "Segurança" em Configurações acrescenta um seletor de duração (1/5/15/30 min /
  Nunca), persistido junto da preferência de idioma já existente em
  `~/.rikiki-vault/preferences/settings.json` (mesmo padrão de adaptador de
  `LocalLanguagePreferenceAdapter`).

**Testes:** sem automação de UI (política já estabelecida no projeto — sem TestFX/Monocle). A
lógica de deteção de inatividade em si (um cálculo "deve bloquear agora" a partir de um timestamp
da última interação + limiar) pode ser extraída para uma classe pura pequena e testada com JUnit
normal, no mesmo espírito de `FolderTreeBuilder` estar separado do seu controlador. Verificação
manual: deixar a app inativa para lá do limiar e confirmar que o ecrã de bloqueio cobre toolbar +
rail + conteúdo; destrancar com passphrase correta/incorreta; confirmar que o `Timeline` de
auto-refresh não continua a disparar enquanto bloqueado.

**Ordem de construção recomendada face à #1:** implementar a #1 primeiro — é o que torna o passo
de destrancar da #3 uma fronteira de segurança real, não só cosmética. A #2 é totalmente
independente e pode ser construída em qualquer ordem face às outras duas.

## Prioridade: Média

- Verificação de fingerprint fora de banda no fluxo de `authorize` (um passo de checklist para
  confirmar um fingerprint por um canal separado antes de confirmar) — complementa a #2 do lado de
  quem é adicionado, mas é sobretudo um incentivo de processo/UX, não criptografia nova.
- Assinatura de commits (SSH/GPG) para o histórico git — dá proveniência verificável, mas depende
  de o utilizador já ter uma chave de assinatura configurada, o que é fricção fora do controlo da
  app.
- Um ecrã de "Atividade" na GUI a listar o histórico de publish/authorize/revoke — os dados já
  existem no `git log`; isto é mais observabilidade/UX do que um controlo de segurança novo.

## Prioridade: Baixa / direção futura

- Assinar o manifesto/registo de destinatários com a chave da máquina que publica — sobrepõe-se ao
  objetivo da #2; só vale a complexidade extra de desenho se a abordagem mais simples de "avisar
  na diferença" da #2 se revelar insuficiente na prática.
- Armazenamento seguro nativo do SO (Keychain no macOS / DPAPI no Windows) como alternativa ao
  repositório de chaves baseado em ficheiro — esforço de engenharia maior e específico por SO,
  para um ganho marginal face à #1, que é portátil e muito mais simples.
- Apagamento seguro ("shred") para ficheiros locais removidos por "Limpar local" — valor duvidoso
  em SSDs modernos (o wear leveling anula um simples overwrite-antes-do-delete); talvez fique
  melhor servido por documentar a limitação do que por construir isto.
- Sinalizar ocorrências repetidas de `UnauthorizedMachineException` como possível indício de
  máquina comprometida — baixa probabilidade de alguma vez disparar num uso pessoal/pequena equipa
  sem já haver outro vetor de ataque em curso.
- Aviso explícito antes de force-push/divergência de histórico — mais uma funcionalidade de
  segurança de dados do que criptográfica; o fluxo de publish já mostra o estado ahead/behind.

## Direção de plataforma (não é um item de segurança)

**Uma app multiplataforma estilo React Native** (mobile + desktop) como alternativa ou
complemento à GUI JavaFX atual. Pergunta em aberto, deliberadamente ainda não investigada: como o
módulo `core` (Java — criptografia, Git via JGit) seria exposto a uma app JS/TS — um serviço
local/bridge REST, uma reimplementação de raiz da lógica de criptografia/git em JS/TS, ou outra
ponte. Registado aqui como direção a revisitar, não como plano comprometido.
