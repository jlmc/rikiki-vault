# Perdi o SSD, o remoto está atualizado, mas só fiz backup do `private.key`, não do `public.key` — como recupero?

*[Read in English](03-recovering-with-only-the-private-key.md)*

Mais difícil do que [o caso anterior](02-recovering-with-both-keys-backed-up.pt.md), mas não
necessariamente perdido - tens dois caminhos. Se alguma outra máquina (ou pessoa) ainda tiver
acesso ao vault, segue o **Caminho A**: é simples e funciona sempre. Se esta era a tua única
máquina, o **Caminho B** ainda consegue recuperar a tua identidade original exata, mas depende de
ferramentas externas e não é garantido.

## Porque é que isto é mais difícil

A app trata `private.key` e `public.key` como um par inseparável em disco - o `LocalKeyStoreAdapter`
exige que *ambos* os ficheiros existam antes de sequer carregar uma identidade. Um `private.key`
sozinho não é algo que a app consiga usar: coloca-o sozinho em `~/.rikiki-vault/identity/` e a
identidade continua a não ser reconhecida como existente. Não há nenhum comando embutido para
"regenerar a minha chave pública".

## Pré-requisitos (ambos os caminhos)

Numa máquina de substituição mesmo em bruto (macOS com o [Homebrew](https://brew.sh/) instalado):

```bash
brew install openjdk@25 maven git
sudo ln -sfn "$(brew --prefix openjdk@25)/libexec/openjdk.jdk" \
  /Library/Java/JavaVirtualMachines/openjdk-25.jdk
export PATH="$(brew --prefix openjdk@25)/bin:$PATH"
java -version   # confirma que reporta 25

git clone <this-app's-source-repository-url>
cd rikiki-vault
mvn -pl cli -am package -DskipTests   # produz cli/target/rikiki-vault.jar
```

Esse último `git clone` é o **código-fonte da própria aplicação** Rikiki Vault - um repositório
diferente do teu vault pessoal (o que tem os teus ficheiros cifrados), que vais clonar à parte nos
passos abaixo. Ver o [README](../../README.pt.md) principal ("Building"/"Distribuição") para
compilar a app desktop em vez disto, ou um instalador nativo que nem precisa de Java. (No Linux,
usa o gestor de pacotes da tua distribuição para JDK 25 + Maven + Git em vez do `brew`.)

## Caminho A — identidade nova + reautorização (usa este se puderes)

A opção mais segura quando ainda há alguém com acesso: nem tentes ressuscitar a identidade antiga.
Deixa a máquina nova gerar uma identidade de raiz, e autoriza-a como se fosse qualquer máquina
nova.

1. Deixa `~/.rikiki-vault/identity/` vazio na máquina nova (não ponhas lá o `private.key` sozinho -
   não ajuda e pode causar confusão mais tarde).
2. Clona o teu vault pessoal normalmente:
   ```bash
   java -jar cli/target/rikiki-vault.jar clone <uri-do-remoto-do-teu-vault>
   ```
   Sem nenhuma identidade existente, a app gera um par de chaves novo para esta máquina. O clone
   em si tem sucesso, mas nada decifra ainda - esta chave nova nunca foi autorizada.
3. Exporta a chave pública nova:
   ```bash
   java -jar cli/target/rikiki-vault.jar export-key minha-maquina-nova.pub
   ```
4. Envia esse ficheiro a quem ainda tiver acesso, e pede para autorizarem (a partir de qualquer
   máquina que ainda seja um destinatário autorizado):
   ```bash
   java -jar cli/target/rikiki-vault.jar authorize <label> minha-maquina-nova.pub
   ```
   (ou "Gerir Acesso" na app desktop). Isto reencripta todos os ficheiros controlados para o
   conjunto de destinatários atualizado, incluindo esta máquina nova.
5. Faz `pull` outra vez - os ficheiros decifram normalmente.
6. Opcionalmente, assim que tiveres a certeza de que a identidade antiga está mesmo perdida,
   `revoke` o fingerprint antigo se ainda estiver listado como destinatário, para deixar de
   conseguir decifrar qualquer coisa publicada de novo.

Este caminho precisa de pelo menos uma outra máquina ou pessoa autorizada disponível. Se eras o
único com acesso, segue para o Caminho B.

## Caminho B — derivar a chave pública a partir do backup da chave privada (funciona sozinho)

Ao contrário de outros tipos de chave, uma chave pública X25519 é uma função determinística da sua
chave privada - pode ser regenerada por quem tiver a chave privada, com ferramentas normais, já que
não é nenhuma derivação secreta. A app guarda as chaves em PKCS8/X.509 DER simples (sem cabeçalhos
PEM).

O macOS traz `openssl` como LibreSSL por omissão, que não lida com X25519 da mesma forma de forma
fiável - o [`scripts/derive-public-key.sh`](scripts/derive-public-key.sh) (nesta mesma pasta
`docs/faq/`, já disponível no código-fonte da app que clonaste nos pré-requisitos acima) encontra
sozinho o OpenSSL a sério do Homebrew, e diz-te para correres `brew install openssl@3` primeiro se
não o encontrar:

```bash
chmod +x docs/faq/scripts/derive-public-key.sh   # não é preciso se mantiver as permissões vindas do git
docs/faq/scripts/derive-public-key.sh /caminho/para/o/backup/private.key public.key
```

Os dois argumentos são **caminhos** — o primeiro é onde está o teu ficheiro `private.key` em
backup, o segundo é onde escrever o ficheiro `public.key` derivado (não o conteúdo de nenhum dos
dois, só as suas localizações). Também imprime o fingerprint resultante, para confirmares logo
contra o `vault/recipients.json`
(ver o passo 2 abaixo) sem precisares de um `whoami` à parte. Equivalente a correr diretamente, se
preferires não usar o script:

```bash
openssl pkey -in private.key -inform DER -pubout -outform DER -out public.key
```

(No Linux, o pacote `openssl` da tua distribuição é quase sempre OpenSSL a sério já - sem precisar
do passo do Homebrew.)

Depois:

1. **Coloca os dois ficheiros no sítio certo** - o `private.key` recuperado (o teu backup) e o
   `public.key` que acabaste de derivar - e restringe as permissões, tal como na [resposta
   anterior](02-recovering-with-both-keys-backed-up.pt.md#3-restaurar-os-ficheiros-da-identidade):
   ```bash
   mkdir -p ~/.rikiki-vault/identity
   cp /caminho/para/o/backup/private.key public.key ~/.rikiki-vault/identity/
   chmod 700 ~/.rikiki-vault/identity
   chmod 600 ~/.rikiki-vault/identity/private.key ~/.rikiki-vault/identity/public.key
   ```
2. **Corre `whoami` e confere o fingerprint impresso**:
   ```bash
   java -jar cli/target/rikiki-vault.jar whoami
   ```
   Se tiveres uma cópia do `vault/recipients.json` do vault (de um clone ainda existente, ou de
   alguém com acesso), procura lá um fingerprint correspondente sob o label antigo da tua máquina:
   ```bash
   cat /caminho/para/um/clone/existente/vault/recipients.json
   ```
   Uma correspondência confirma que a derivação reconstruiu exatamente a identidade original.
3. **Se corresponder**, faz `clone`/`pull` normalmente - sem precisar de reautorização, já que é
   criptograficamente a mesma identidade que o vault já reconhece:
   ```bash
   java -jar cli/target/rikiki-vault.jar clone <uri-do-remoto-do-teu-vault>
   ```
4. **Se *não* corresponder** (versão errada do OpenSSL, um formato de chave subtilmente diferente,
   ou um backup corrompido), não continues a tentar - sem mais comandos aqui, volta ao **Caminho A**
   descrito acima. Experimentar mais ferramentas/flags nesta altura arrisca mais confusão do que
   vale a pena.

## Conclusão

Este cenário é exatamente a razão pela qual [a primeira resposta](01-disk-failure-and-backups.pt.md)
recomenda fazer backup do `private.key` **e** do `public.key` juntos: é só mais um ficheiro
pequeno, e elimina qualquer dependência de conseguires derivar corretamente uma chave com a
ferramenta certa, ou de haver mais alguém disponível para te `authorize`.
