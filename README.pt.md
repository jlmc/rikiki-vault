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
| `git-auth show\|set-ssh-key <caminho>\|clear-ssh-key\|set-token\|clear-token` | Configura autenticação Git explícita, substituindo a descoberta automática — ver "Autenticação Git explícita" abaixo. `set-token` lê o token do stdin, nunca de um argumento, para não ficar no histórico da shell. |

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
que o agente não está a oferecer, ou preferes não exportar uma variável de ambiente), configura-a
explicitamente — na app desktop através de "Definições de Git..." (no ecrã inicial e na toolbar da
janela principal), ou na CLI:

```
rikiki-vault git-auth set-ssh-key ~/.ssh/id_jc
echo "$O_TEU_TOKEN_GITHUB" | rikiki-vault git-auth set-token
rikiki-vault git-auth show
```

Nunca passes um token como argumento do `set-token` — ficaria no histórico da shell. As duas
definições ficam guardadas em `~/.rikiki-vault/git-auth/settings.json` com permissões só do dono
(`rw-------`), tal como a chave privada. O esquema do URL do remoto decide sozinho qual se aplica:
`git@`/`ssh://` usa a chave configurada (com fallback para a descoberta automática se nenhuma
estiver definida), `https://` usa o token configurado (com fallback para `RIKIKI_VAULT_GITHUB_TOKEN`).

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
o ficheiro selecionado (texto, JSON, XML, Markdown, imagens, e a primeira página de PDFs), e
ações na toolbar para Pull, Publicar (com um passo de revisão e aprovação antes de qualquer coisa
ser cifrada), e gerir o acesso das máquinas (autorizar/revogar).

Editar ficheiros e ver diffs de alterações na GUI ainda não estão implementados — por agora,
edita os ficheiros em `local/` com o teu próprio editor e usa o `publish` para rever e publicar o
resultado.
