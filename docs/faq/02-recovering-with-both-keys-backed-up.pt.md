# Perdi o SSD, o remoto está atualizado, e fiz backup de `private.key` e `public.key` — como recupero?

*[Read in English](02-recovering-with-both-keys-backed-up.md)*

Este é o caso fácil, e é exatamente o que seguir a [resposta anterior](01-disk-failure-and-backups.pt.md)
te garante: restaura os ficheiros da identidade na máquina nova **antes** de clonar, e a app
reutiliza a tua identidade existente, já autorizada, em vez de gerar uma nova - ninguém precisa de
te voltar a autorizar.

## Porque é que isto funciona

A identidade de uma máquina vive em `~/.rikiki-vault/identity/private.key` +
`~/.rikiki-vault/identity/public.key` - global à máquina, não a nenhum vault em particular. Quando
fazes `clone` de um vault, a app verifica primeiro se já existe uma identidade nesse caminho: se
existir, é reutilizada tal como está; só gera um par de chaves novo se nenhum dos dois ficheiros lá
estiver (ver a [primeira resposta](01-disk-failure-and-backups.pt.md) para perceber porque é que
isto importa). Como o teu fingerprint (calculado a partir da chave pública) é o que a lista de
destinatários do vault já reconhece, restaurar exatamente o mesmo par de chaves significa que já és
um destinatário autorizado no momento em que clonas.

## Passos

Assume-se uma máquina de substituição mesmo em bruto (macOS com o
[Homebrew](https://brew.sh/) instalado) - salta o que já tiveres.

### 1. Instalar os pré-requisitos

```bash
brew install openjdk@25 maven git
```

O `openjdk@25` é *keg-only* (o Homebrew não o põe no `PATH` nem o regista no
`/usr/libexec/java_home` automaticamente), por isso liga-o:

```bash
sudo ln -sfn "$(brew --prefix openjdk@25)/libexec/openjdk.jdk" \
  /Library/Java/JavaVirtualMachines/openjdk-25.jdk
echo 'export PATH="'"$(brew --prefix openjdk@25)"'/bin:$PATH"' >> ~/.zshrc
export PATH="$(brew --prefix openjdk@25)/bin:$PATH"
java -version   # confirma que reporta 25
```

(No Linux, usa o gestor de pacotes da tua distribuição para um JDK 25 + Maven + Git em vez do
`brew`.)

### 2. Obter a própria aplicação Rikiki Vault

Este é o código-fonte da app em si - um repositório diferente do teu vault pessoal (o que tem os
teus ficheiros cifrados). Clona e compila:

```bash
git clone <this-app's-source-repository-url>
cd rikiki-vault
mvn -pl cli -am package -DskipTests
```

Isto produz `cli/target/rikiki-vault.jar` (+ a sua pasta `lib/`). Para a app desktop - ou para um
instalador nativo que nem precisa de Java instalado - ver o [README](../../README.pt.md) principal
("Building"/"Distribuição").

### 3. Restaurar os ficheiros da identidade

```bash
mkdir -p ~/.rikiki-vault/identity
cp /caminho/para/o/backup/private.key /caminho/para/o/backup/public.key ~/.rikiki-vault/identity/
chmod 700 ~/.rikiki-vault/identity
chmod 600 ~/.rikiki-vault/identity/private.key ~/.rikiki-vault/identity/public.key
```

Os nomes importam exatamente (`private.key`, `public.key`), e é um local global à máquina - não
dentro de nenhuma pasta de vault. A app aplica estas mesmas permissões sozinha quando gera uma
identidade de raiz; um ficheiro restaurado à mão precisa do mesmo tratamento.

### 4. Confirmar antes de clonar seja o que for

```bash
java -jar cli/target/rikiki-vault.jar whoami
```

Deve imprimir o teu fingerprint original de imediato, sem nenhuma mensagem de "a gerar uma
identidade nova". Se tiveres uma nota do teu fingerprint (ou ainda tiveres outra máquina que o
consiga mostrar), compara-os agora - apanhar uma discrepância aqui é muito mais fácil do que
depois de já teres clonado.

### 5. Clonar o teu vault pessoal

```bash
java -jar cli/target/rikiki-vault.jar clone <uri-do-remoto-do-teu-vault>
```

(ou, na app desktop: escolhe uma pasta vazia → "Entrar num vault já existente" → cola o URL do
remoto). Este é o *outro* remoto - os teus dados a sério, não o código-fonte da app clonado no
passo 2. Como a identidade restaurada já era um destinatário autorizado antes do disco se perder,
todos os ficheiros controlados decifram diretamente para `local/` - **sem precisar de nenhum
`authorize` por parte de ninguém**.

### 6. Continuar normalmente

`pull`/`publish` (ou a toolbar da app desktop) funcionam exatamente como na máquina antiga.

Se em vez disso o `whoami` mostrar um fingerprint *diferente* do esperado, algo correu mal na
restauração (ficheiros trocados, um backup desatualizado, pasta errada) - para antes de clonar e
revê o backup, em vez de avançar e teres `UnauthorizedMachineException` em todos os ficheiros.
