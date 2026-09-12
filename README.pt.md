# Rikiki Vault

*[Read in English](README.md)*

Um cofre de ficheiros local-first, cifrado de ponta a ponta, sincronizado através de um
repositório Git privado. Mantém os ficheiros em claro numa cópia de trabalho `local/`; o
`publish` cifra-os (X25519 + AES-GCM) para `documents/` e publica o resultado. Qualquer máquina
autorizada pode fazer `clone`/`pull` do repositório e decifrar de volta para a sua própria cópia
`local/`. Nada chega ao Git em claro.

## Índice

- [Requisitos](#requisitos)
- [Compilar](#compilar)
- [Identidade da máquina](#identidade-da-máquina)
- [CLI](#cli)
  - [Comandos](#comandos)
  - [Um exemplo completo](#um-exemplo-completo)
  - [Publicar num remoto a sério](#publicar-num-remoto-a-sério)
  - [Autenticação Git explícita](#autenticação-git-explícita)
  - [Proteger a chave privada com passphrase](#proteger-a-chave-privada-com-passphrase)
  - [Ficheiros de configuração](#ficheiros-de-configuração)
  - [Notas de robustez](#notas-de-robustez)
  - [Logging](#logging)
- [Aplicação desktop (JavaFX)](#aplicação-desktop-javafx)
- [Distribuição](#distribuição)
  - [Apps nativas (sem precisar de Java na máquina de destino)](#apps-nativas-sem-precisar-de-java-na-máquina-de-destino)
- [Licença](#licença)
- [FAQ](#faq)

## Requisitos

- Java 25
- Maven

## Compilar

A partir da raiz do repositório:

```
mvn install
```

Isto compila os três módulos:

- **`core`** (artifactId Maven `rikiki-vault-core`) — modelo de domínio, ports & adapters, todos os
  casos de uso da aplicação. Sem UI, sem `main`.
- **`cli`** (artifactId Maven `rikiki-vault-cli`) — uma aplicação de consola
  (`cli/target/rikiki-vault.jar`) que expõe cada caso de uso como um comando.
- **`gui-javafx`** (artifactId Maven `rikiki-vault-gui-javafx`) — uma aplicação desktop JavaFX que
  usa os mesmos casos de uso.

## Identidade da máquina

Cada máquina tem um único par de chaves X25519, guardado em `~/.rikiki-vault/identity` —
partilhado por todos os vaults que abrires nessa máquina, já que a identidade representa *a
máquina*, não um vault específico. Tanto o CLI como a GUI leem/escrevem no mesmo sítio.

## CLI

Compilar o jar uma vez:

```
mvn -pl cli package
```

Depois corre-o como `java -jar cli/target/rikiki-vault.jar <comando> [args]`. Cada comando opera
sobre a diretoria atual como raiz do vault, a não ser que passes `-C <path>` primeiro (à
semelhança do `git -C`):

```
java -jar cli/target/rikiki-vault.jar -C /caminho/para/o/vault status
```

Ou salta a compilação manual: `scripts/run-cli.sh -- -C /caminho/para/o/vault status` compila o
jar da primeira vez que falta e corre-o da mesma forma (ver "Logging" mais abaixo para a *flag*
`--log-level`).

### Comandos

| Comando | O que faz |
|---|---|
| `init [--git] [--remote <url>] <nome-da-máquina>` | Inicializa um vault novo na diretoria atual. Com `--git`, também corre `git init` localmente; `--remote` (exige `--git`) associa também esse URL como `origin`, para a primeira `publish` já publicar a sério. Semeia o registo de destinatários com esta máquina como único destinatário. |
| `whoami` | Mostra o fingerprint da identidade desta máquina, gerando uma primeiro se ainda não existir. |
| `export-key <ficheiro-saída>` | Escreve a chave pública desta máquina num ficheiro, para dar a quem gere o `authorize` noutra máquina. |
| `clone <remote-uri>` | Entra num vault **já existente** (usa `init` para arrancar um novo). |
| `status` | Lista as alterações locais pendentes (novas/modificadas/removidas) desde a última publicação. |
| `publish -m "<mensagem>"` | Cifra todas as alterações pendentes para todos os destinatários atualmente autorizados, faz commit e publica. |
| `pull` | Faz pull do estado cifrado mais recente e decifra o que mudou remotamente para `local/`. Nunca sobrescreve um ficheiro que também alteraste localmente — isso é reportado como conflito. |
| `authorize <label> <ficheiro-chave-pública>` | Concede acesso a outra máquina: adiciona-a ao registo de destinatários e reencripta todos os ficheiros já publicados para o novo conjunto. |
| `revoke <fingerprint-hex>` | Remove o acesso de uma máquina, reencriptando tudo para que a sua chave deixe de conseguir decifrar o que quer que seja de novo. |
| `git-auth show\|set-ssh-key <caminho>\|clear-ssh-key\|set-token\|clear-token\|set-http-basic <utilizador>\|clear-http-basic\|use ssh\|token\|http\|none` | Configura autenticação Git explícita, substituindo a descoberta automática — ver "Autenticação Git explícita" abaixo. `set-token`/`set-http-basic` leem o segredo do stdin, nunca de um argumento, para não ficar no histórico da shell. |
| `set-passphrase` | Protege (ou muda) a identidade desta máquina com uma passphrase — pede a atual primeiro se já estiver protegida, depois a nova (duas vezes, para confirmar). Ver "Proteger a chave privada com passphrase" abaixo. |
| `remove-passphrase` | Remove a proteção por passphrase, dada a passphrase atual. |
| `unwrap-key <ficheiro-entrada> <ficheiro-saída>` | Recuperação de desastre: desencripta um `private.key` protegido por passphrase (qualquer caminho de ficheiro, não só a identidade ativa) para PKCS8 em claro — ver "Proteger a chave privada com passphrase" abaixo. |
| `migrate-format [--dry-run] [--yes]` | Definitivo: converte um vault do formato antigo (`manifest.json` em claro, nomes reais em `documents/`) para o atual, em que paths e nomes de ficheiros também ficam cifrados. A partir de um checkout, `scripts/migrate-format.sh <pasta-do-vault> --dry-run\|--yes` é a forma pronta a correr — ver [FAQ 11](docs/faq/11-migrating-to-encrypted-paths.pt.md). |

### Um exemplo completo

```
mkdir o-meu-vault && cd o-meu-vault
java -jar rikiki-vault.jar init --git machine-a

mkdir local
echo "olá" > local/notas.txt

java -jar rikiki-vault.jar status
java -jar rikiki-vault.jar publish -m "primeira publicação"
```

Um remoto é opcional: `publish`/`authorize`/`revoke` fazem commit localmente e imprimem
`(guardado localmente - sem remoto configurado)` quando não há nenhum, em vez de falhar. Podes
registar um logo no `init` com `--remote` (a única operação do `GitRepositoryPort` relacionada com
remotos), ou associar um mais tarde com o `git` normal:

```
java -jar rikiki-vault.jar init --git --remote git@github.com:tu/o-meu-vault-cifrado.git machine-a
```

```
git remote add origin git@github.com:tu/o-meu-vault-cifrado.git
java -jar rikiki-vault.jar publish -m "sincronizar com o remoto"
```

Para deixar entrar uma segunda máquina:

```
# na máquina B, depois de ela ter a sua própria identidade (corre qualquer comando uma vez, ex. `whoami`):
java -jar rikiki-vault.jar export-key machine-b.pub
# envia machine-b.pub a quem gere a máquina A

# na máquina A:
java -jar rikiki-vault.jar authorize machine-b machine-b.pub

# na máquina B:
java -jar rikiki-vault.jar clone git@github.com:tu/o-meu-vault-cifrado.git
```

### Publicar num remoto a sério

Define `RIKIKI_VAULT_GITHUB_TOKEN` no teu ambiente antes de `publish`/`pull`/`clone` contra um
remoto HTTPS que precise de token. Remotos SSH usam o agente/chaves SSH do teu sistema — sem
configuração extra.

### Autenticação Git explícita

Se a descoberta automática não funcionar no teu ambiente (ex.: uma chave SSH com nome não-padrão
que o agente não está a oferecer, ou preferes não exportar uma variável de ambiente), configura um
de três métodos explícitos. Só um fica *ativo* de cada vez — podes ter os três configurados ao
mesmo tempo (trocar entre eles nunca apaga os outros), mas só o ativo é aplicado por
`clone`/`pull`/`push`, independentemente do esquema do URL do remoto:

- uma **chave SSH** (um ficheiro de chave privada específico)
- um **token do GitHub** (HTTPS)
- um **utilizador/password HTTP** (HTTPS, para remotos que não sejam GitHub)

App desktop: "Configurações..." (ecrã inicial ou toolbar da janela principal) → o separador **Git**
mostra qual o método ativo agora e permite trocar, com um botão "👁" para revelar o valor real dos
campos de token/password em vez de aparecerem sempre vazios. CLI:

```
rikiki-vault git-auth set-ssh-key ~/.ssh/id_jc                # configura a chave e torna-a ativa
echo "$O_TEU_TOKEN_GITHUB" | rikiki-vault git-auth set-token    # configura o token e torna-o ativo
rikiki-vault git-auth set-http-basic o-meu-utilizador           # a password é lida a seguir do stdin
rikiki-vault git-auth use ssh|token|http|none                   # troca o método ativo sem reconfigurar nada
rikiki-vault git-auth show
```

Nunca passes um token ou password como argumento do `set-token`/`set-http-basic` — o segredo é
sempre lido do stdin, para não ficar no histórico da shell. Ver "Ficheiros de configuração" abaixo
para saber onde isto fica guardado.

### Proteger a chave privada com passphrase

Por omissão, `private.key` não está protegida (ver a [entrada da FAQ sobre a password da chave privada](docs/faq/07-private-key-is-not-password-protected.pt.md)
para a troca completa). Para ativar a proteção:

```
rikiki-vault set-passphrase       # pede uma nova passphrase (duas vezes, para confirmar)
rikiki-vault remove-passphrase    # pede a passphrase atual e remove a proteção
```

Uma vez protegida, qualquer comando que precise da identidade (`whoami`, `export-key`, `init`,
`clone`, `pull`, `restore`) pede a passphrase uma vez por invocação — mascarada, através da
própria consola quando existe uma real, com um recurso a stdin visivelmente ecoado (anunciado)
quando não há (entrada por pipe, configurações de execução de IDE, CI). Logo a seguir a gerar uma
identidade nova (só em `init`/`clone`), também és convidado a protegê-la ali mesmo, quando corres
de forma interativa.

App desktop: Configurações → **Segurança** oferece o mesmo definir/mudar/remover, aplicado de
imediato. Abrir um vault cuja identidade esteja protegida mostra um ecrã de desbloqueio a ocupar a
janela toda antes de mais nada carregar; depois de desbloqueada, a identidade fica utilizável
durante o resto dessa sessão (pull/push em segundo plano, auto-refresh) sem voltar a pedir —
fechar a app limpa-a.

Não há forma de recuperar se a passphrase for esquecida — mudar ou remover uma exige sempre a
passphrase atual primeiro, por desenho.

**Recuperação de desastre com uma chave protegida:** `unwrap-key <ficheiro-entrada>
<ficheiro-saída>` desencripta um `private.key` protegido por passphrase para PKCS8 em claro —
funciona sobre qualquer caminho de ficheiro (não só o `~/.rikiki-vault/identity/` ativo), por isso
também lida com um backup sozinho e protegido de uma chave cujo `public.key` se perdeu. Ver a
[FAQ 03](docs/faq/03-recovering-with-only-the-private-key.pt.md) e a
[FAQ 04](docs/faq/04-decrypting-without-the-app.pt.md) para percursos completos de recuperação
passo-a-passo, incluindo uma alternativa em OpenSSL/bash puro
(`docs/faq/scripts/unwrap-private-key.sh`) para quando compilar a CLI desta app não é opção.

### Ficheiros de configuração

Tudo o que existe fora de um vault propriamente dito vive em `~/.rikiki-vault/` — global à máquina,
partilhado por todos os vaults que abras e pela CLI e pela GUI:

| Ficheiro | Para que serve | Como se configura |
|---|---|---|
| `~/.rikiki-vault/identity/private.key` / `public.key` | O par de chaves X25519 desta máquina (permissões só do dono) — ver "Identidade da máquina" acima. `private.key` pode opcionalmente estar protegida por passphrase — ver "Proteger a chave privada com passphrase" acima. | Gerado automaticamente na primeira vez que é preciso; não é editável à mão diretamente. `whoami`/`export-key` leem-no; `set-passphrase`/`remove-passphrase` (ou Configurações → Segurança) mudam a sua proteção. |
| `~/.rikiki-vault/config/config.yaml` | Configuração de arranque da mecânica do vault: onde fica a diretoria de identidade, e os parâmetros de encriptação (algoritmo, tamanhos de chave). Escrito uma vez com valores por omissão sensatos no primeiro arranque. | Não está exposto na UI/CLI — a maioria dos utilizadores nunca precisa de lhe mexer; edita o YAML diretamente só se souberes bem o que estás a mudar. |
| `~/.rikiki-vault/preferences/preferences.json` | Tudo o que o ecrã de Configurações / comando `git-auth` gerem: qual o método de autenticação Git ativo e os seus valores (caminho da chave SSH, token do GitHub, utilizador/password HTTP), mais o idioma da aplicação (`PT`/`EN` - tanto a GUI como a CLI leem-no, por isso trocar num sítio muda o que ambas mostram). Permissões só do dono (`rw-------`), já que pode conter segredos. | App desktop: "Configurações...", incluindo o idioma. CLI: `git-auth ...` para o lado do Git (ver acima); ainda não há um comando na CLI para *definir* o idioma, mas o próprio texto da CLI (uso, confirmações, erros) já segue o idioma que foi gravado pela última vez a partir das Configurações da app desktop. |

Um ficheiro de antes deste modelo existir (`~/.rikiki-vault/git-auth/settings.json`) é lido uma
vez, automaticamente, a primeira vez que o `preferences.json` ainda não existir — para uma chave
SSH ou token já configurados continuarem a funcionar sem reconfigurar nada. A app nunca o
reescreve nem apaga; só deixa de o consultar assim que gravares alguma coisa através das
Configurações/`git-auth`.

### Notas de robustez

Os ficheiros decifrados escritos em `local/` são criados com permissões só do dono (`rw-------`
em sistemas POSIX, tal como já acontecia com a chave privada da máquina) e a escrita é atómica —
uma interrupção ou falha a meio da escrita nunca deixa um ficheiro truncado no sítio. Quando o
`pull` reporta um conflito, mostra os hashes SHA-256 da versão local e da remota, para conseguires
distingui-las antes de reconciliar manualmente; continua sem ação de "manter remoto"/"comparar" —
a versão local é sempre a que fica automaticamente, e o resto resolve-se à mão.

### Logging

Tanto a CLI como a app desktop trazem um *binding* simples do [slf4j-simple](https://www.slf4j.org/),
e o próprio código da app (`core`/`gui-javafx`) regista as suas operações através dele - abrir um
vault, clone/pull/push, publish/authorize/revoke, e todos os diálogos de erro da GUI. Duas camadas,
cada uma com o seu nível por omissão: as bibliotecas de Git/SSH de terceiros (JGit, mina-sshd)
ficam silenciosas (só avisos/erros), enquanto os pacotes da própria app (`io.github.jlmc`) ficam a
`info` por omissão. Sem nenhum ficheiro de configuração extra a preparar para distribuição - já
vem tudo embutido no `simplelogger.properties` de cada módulo.

A forma mais simples de mudar o nível de log da própria app é `scripts/run.sh`, que envolve tanto
a CLI como a GUI:

```bash
scripts/run.sh cli -- -C <vault> pull                    # info por omissão
scripts/run.sh cli --log-level=debug -- -C <vault> pull
scripts/run.sh gui --log-level=debug
```

`scripts/run-cli.sh`/`scripts/run-gui.sh` são atalhos diretos para `scripts/run.sh cli`/`scripts/run.sh gui` - as mesmas flags, menos uma palavra a escrever.

Para controlo direto (ex. para também aumentar o detalhe das bibliotecas de Git/SSH), passa as
*flags* `-D` diretamente:

```bash
java -Dorg.slf4j.simpleLogger.defaultLogLevel=debug -jar cli/target/rikiki-vault.jar -C <vault> pull
```

ou só os pacotes de Git/SSH, para manter o resto silencioso:

```bash
java -Dorg.slf4j.simpleLogger.log.org.apache.sshd=debug -Dorg.slf4j.simpleLogger.log.org.eclipse.jgit=debug -jar cli/target/rikiki-vault.jar -C <vault> pull
```

As mesmas *flags* funcionam com `mvn -pl gui-javafx javafx:run -D...` para a app desktop - os logs
aparecem no terminal que correu esse comando, ao lado da janela aberta.

## Aplicação desktop (JavaFX)

```
mvn -pl gui-javafx javafx:run
```

Ou `scripts/run-gui.sh` - compila o jar empacotado da primeira vez que falta, depois lança-o com
`java -jar` puro (sem Maven em tempo de execução); ver "Logging" acima para a *flag* `--log-level`.

No arranque, escolhe uma pasta — um vault já existente, ou uma pasta vazia para
inicializar/clonar. A janela principal mostra a árvore de ficheiros do vault com um indicador de
estado por ficheiro (sincronizado/novo/modificado/removido), um painel de pré-visualização para
o ficheiro selecionado (texto, JSON, XML, Markdown, imagens, e a primeira página de PDFs) que
também pode passar a editor (Save/Encrypt/Revert/Diff), um indicador "Remoto: ..." do estado de
sincronização, e ações na toolbar para Pull, Publicar (com um passo de revisão e aprovação antes
de qualquer coisa ser cifrada, e uma pergunta à parte antes de publicar para o remoto), gerir o
acesso das máquinas (autorizar/revogar), e Configurações (autenticação Git + idioma).

## Distribuição

Nenhum dos módulos produz um jar gordo/*shaded*. `mvn package` produz um jar fino por módulo (só
as classes do próprio projeto) mais uma pasta `target/lib/` com cada dependência como jar
separado; o manifesto do jar já aponta para `lib/`, por isso corre-se exatamente como antes:

```bash
mvn package
java -jar cli/target/rikiki-vault.jar -C <vault> status
java -jar gui-javafx/target/rikiki-vault-gui.jar
```

Basta manter `target/lib/` ao lado do jar - copia a pasta `target/` inteira (ou `lib/` + o jar) se
o moveres para outro sítio. Continua a ser preciso ter Java 25 instalado nessa máquina.

### Apps nativas (sem precisar de Java na máquina de destino)

`scripts/package.sh` (macOS/Linux) e `scripts/package.ps1` (Windows) envolvem o `mvn package` e
depois correm o `jpackage` (incluído no JDK) para produzir uma app nativa autossuficiente - a JVM
vai lá dentro, por isso a máquina que a corre não precisa de Java instalado. O `jpackage` nunca
faz compilação cruzada: cada script só produz artefactos para o SO onde corre, por isso corre o
script correspondente manualmente em cada SO de destino - não há CI neste repositório para fazer
isso de forma centralizada.

```bash
scripts/package.sh              # macOS/Linux, app-image (rápido, para testar localmente)
scripts/package.sh installer    # macOS/Linux, o instalador a sério (.dmg / .deb)
```

```powershell
scripts\package.ps1              # Windows, app-image
scripts\package.ps1 -Mode installer  # Windows, .msi
```

A saída vai para `dist/` (já ignorada pelo git). Pré-requisitos por SO:

- **macOS**: Xcode Command Line Tools (`xcode-select --install`) - já exigido pelo próprio
  `jpackage`.
- **Windows**: o [WiX Toolset](https://wixtoolset.org/) instalado, para `-Mode installer`
  (`--type msi`).
- **Linux**: `dpkg-dev` e `fakeroot` instalados, para o modo `installer` (`--type deb`).

A app empacotada da CLI continua a ser uma ferramenta de consola - corre-se a partir de um
terminal (ex. `dist/rikiki-vault-cli.app/Contents/MacOS/rikiki-vault-cli` no macOS), só deixa de
precisar de uma instalação de Java à parte. O ícone da app vive em `branding/icon.svg` (fonte),
com os `branding/icon.icns`/`.ico`/`.png` específicos de cada SO derivados a partir dele.

## Licença

[PolyForm Strict License 1.0.0](https://polyformproject.org/licenses/strict/1.0.0) (texto integral
em [`LICENSE`](LICENSE)) - uma licença *source-available*, não é open source aprovada pela OSI.
Podes descarregar, ler e correr este código para qualquer fim não comercial (uso pessoal,
investigação, projetos de hobby, organizações sem fins lucrativos/educação/governo), mas não podes
modificá-lo, distribuir uma versão alterada, nem usá-lo comercialmente. Nada disto restringe o uso
do próprio titular dos direitos de autor - só afeta terceiros que obtenham o código sob esta
licença.

## FAQ

Perguntas práticas sobre o uso real de um vault, com resposta em [`docs/faq/`](docs/faq/README.pt.md):

1. [Como garantir que não perco acesso aos dados mesmo se o meu SSD se perder?](docs/faq/01-disk-failure-and-backups.pt.md)
2. [Perdi o SSD, o remoto está atualizado, e fiz backup de `private.key` e `public.key` — como recupero?](docs/faq/02-recovering-with-both-keys-backed-up.pt.md)
3. [Perdi o SSD, o remoto está atualizado, mas só fiz backup do `private.key`, não do `public.key` — como recupero?](docs/faq/03-recovering-with-only-the-private-key.pt.md)
4. [Não quero mais usar a app. Tenho os dados e as duas chaves — como decifro tudo sem ela?](docs/faq/04-decrypting-without-the-app.pt.md)
5. [O GitHub (ou quem hospeda o remoto) vê os nomes dos meus ficheiros/pastas?](docs/faq/05-filenames-and-metadata-are-not-encrypted.pt.md)
6. [Apaguei um ficheiro do vault — desapareceu mesmo?](docs/faq/06-deleting-a-file-is-not-permanent.pt.md)
7. [A minha chave privada em disco está protegida por password?](docs/faq/07-private-key-is-not-password-protected.pt.md)
8. [Uma máquina foi roubada/comprometida — como garanto que deixa de conseguir ler ficheiros novos?](docs/faq/08-revoking-a-stolen-machine.pt.md)
9. [O que acontece se duas máquinas editarem o mesmo ficheiro antes de sincronizar?](docs/faq/09-conflicting-edits.pt.md)
10. [Apaguei sem querer a pasta `local/` nesta máquina — como recupero os ficheiros?](docs/faq/10-restoring-a-deleted-local-folder.pt.md)
