# Rikiki Vault

*[Read in English](README.md)*

Um cofre de ficheiros local-first, cifrado de ponta a ponta, sincronizado através de um
repositório Git privado. Mantém os ficheiros em claro numa cópia de trabalho `local/`; o
`publish` cifra-os (X25519 + AES-GCM) para `documents/` e publica o resultado. Qualquer máquina
autorizada pode fazer `clone`/`pull` do repositório e decifrar de volta para a sua própria cópia
`local/`. Nada chega ao Git em claro.

## Requisitos

- Java 25
- Maven

## Compilar

A partir da raiz do repositório:

```
mvn install
```

Isto compila os três módulos:

- **`core`** — modelo de domínio, ports & adapters, todos os casos de uso da aplicação. Sem UI,
  sem `main`.
- **`cli`** — uma aplicação de consola (`cli/target/rikiki-vault.jar`) que expõe cada caso de uso
  como um comando.
- **`gui-javafx`** — uma aplicação desktop JavaFX que usa os mesmos casos de uso.

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

### Ficheiros de configuração

Tudo o que existe fora de um vault propriamente dito vive em `~/.rikiki-vault/` — global à máquina,
partilhado por todos os vaults que abras e pela CLI e pela GUI:

| Ficheiro | Para que serve | Como se configura |
|---|---|---|
| `~/.rikiki-vault/identity/private.key` / `public.key` | O par de chaves X25519 desta máquina (permissões só do dono) — ver "Identidade da máquina" acima. | Gerado automaticamente na primeira vez que é preciso; não é editável à mão. `whoami`/`export-key` leem-no. |
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

## Aplicação desktop (JavaFX)

```
mvn -pl gui-javafx javafx:run
```

No arranque, escolhe uma pasta — um vault já existente, ou uma pasta vazia para
inicializar/clonar. A janela principal mostra a árvore de ficheiros do vault com um indicador de
estado por ficheiro (sincronizado/novo/modificado/removido), um painel de pré-visualização para
o ficheiro selecionado (texto, JSON, XML, Markdown, imagens, e a primeira página de PDFs) que
também pode passar a editor (Save/Encrypt/Revert/Diff), um indicador "Remoto: ..." do estado de
sincronização, e ações na toolbar para Pull, Publicar (com um passo de revisão e aprovação antes
de qualquer coisa ser cifrada, e uma pergunta à parte antes de publicar para o remoto), gerir o
acesso das máquinas (autorizar/revogar), e Configurações (autenticação Git + idioma).
