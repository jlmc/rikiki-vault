# Como garantir que não perco acesso aos dados mesmo se o meu SSD se perder?

*[Read in English](01-disk-failure-and-backups.md)*

Resposta curta: publica regularmente para um remoto, e garante que a **identidade da tua
máquina** (a chave privada) nunca é um ponto único de falha - ou autorizando uma segunda máquina,
ou fazendo backup do próprio ficheiro da chave. As duas metades do teu vault sobrevivem a um disco
perdido de formas muito diferentes.

## O que realmente sobrevive a um disco perdido

Um vault tem dois tipos de dados locais, e comportam-se de forma muito diferente:

- **`local/`** - a tua cópia de trabalho em claro. Nunca sai da máquina, e nunca faz parte do que
  o `publish` envia para fora. Se o disco se perder, tudo o que estiver só em `local/` e ainda não
  tiver sido publicado perde-se, exatamente como qualquer outro trabalho local não gravado.
- **`documents/`** - os ficheiros cifrados, controlados pelo repositório Git do vault. Cada
  `publish` cifra, faz commit, e (se houver um remoto configurado) faz push disto. Enquanto uma
  alteração tiver mesmo chegado ao remoto, sobrevive a um disco perdido - o GitHub (ou onde quer
  que o remoto viva) mantém a sua própria cópia.

Portanto, o primeiro passo de "estar sempre a salvo" é simplesmente: **publicar, e confirmar que
chegou ao remoto**, não só que gravou localmente. O `publish` da CLI imprime `(guardado localmente
- sem remoto configurado)` quando não há remoto, e o badge "Remoto: ..." da toolbar da app desktop
diz o mesmo de relance - se qualquer um disser "só local", essa alteração está exatamente tão
exposta a uma falha de disco como qualquer coisa em `local/`.

## O verdadeiro ponto único de falha: a tua chave privada

Esta é a parte fácil de esquecer. Cada máquina tem o seu próprio par de chaves X25519, guardado
em `~/.rikiki-vault/identity/` - esta identidade é **global à máquina**, partilhada por todos os
vaults que abras nela, e **nunca é escrita no repositório Git**. É precisamente isso que mantém a
cifra ponta-a-ponta: ninguém que só tenha acesso ao repositório (incluindo o próprio GitHub)
consegue decifrar nada sem uma das chaves privadas que foram usadas para cifrar.

A consequência: se o disco dessa máquina se perder e não houver backup de
`~/.rikiki-vault/identity/private.key`, e nenhuma *outra* máquina já tiver uma cópia autorizada de
uma chave privada, os ficheiros cifrados que estão em segurança no teu remoto Git ficam
**permanentemente indecifráveis**. Não existe nenhuma porta das traseiras de recuperação - é esse
o objetivo da cifra ponta-a-ponta, mas significa que é a chave privada que precisa mesmo de um
plano de recuperação de desastre, não os dados cifrados em si.

Um erro comum: assumir que basta fazer `clone` do vault outra vez numa máquina de substituição e
está tudo resolvido. Uma máquina sem identidade existente gera um par de chaves **novo** ao fazer
`clone` - e essa chave nova nunca foi autorizada, por isso decifrar seja o que for falha com "esta
máquina não está autorizada" até alguém que ainda tenha acesso correr `authorize` para ela.

## Duas formas de estar realmente a salvo

**1. Autorizar uma segunda máquina (recomendado).** Isto já vem embutido na app e não precisa de
nenhuma ferramenta extra. Assim que uma segunda máquina tiver a sua própria identidade autorizada,
perder qualquer uma das duas isoladamente não custa o acesso - a máquina sobrevivente continua a
conseguir decifrar tudo, e pode fazer `authorize` a uma substituta para a que se perdeu. Ver o
"full walkthrough" no [README](../../README.pt.md) principal para os passos exatos de
`export-key`/`authorize` (ou "Gerir Acesso" na app desktop).

**2. Fazer backup do próprio ficheiro da chave privada.** Útil se só usas uma máquina. Copia
`~/.rikiki-vault/identity/private.key` (e `public.key`, embora essa não seja sensível por si só)
para um local separado, duradouro e seguro - um gestor de passwords, um disco externo cifrado
guardado noutro sítio, algo do género. Nunca ponhas este backup dentro do próprio repositório Git
do vault, nem em cloud storage simples sem cifra - é literalmente a chave que decifra tudo.

Estas duas opções não se excluem mutuamente - fazer as duas é a configuração mais robusta.

**Uma terceira camada, por cima de qualquer uma das duas: proteger o `private.key` com uma
passphrase** (`rikiki-vault set-passphrase`, ou Configurações → Segurança na app desktop - ver a
[entrada da FAQ sobre proteção por password](07-private-key-is-not-password-protected.pt.md) para
o quadro completo). Isto importa especialmente para o próprio backup: guardar uma cópia *protegida*
nalgum sítio menos que totalmente confiável (um disco na cloud, um segundo dispositivo) é
significativamente mais seguro do que guardar a chave em claro, já que só conseguir ler o ficheiro
deixa de chegar sem a passphrase também. Não substitui fazer backup do ficheiro - uma chave
protegida da qual nunca fizeste backup continua perdida para sempre com o disco - e se alguma vez
esqueceres a passphrase, recuperar a identidade a partir desse backup precisa dos passos cientes de
passphrase da [FAQ 03](03-recovering-with-only-the-private-key.pt.md), não dos passos para PKCS8 em
claro de antes desta funcionalidade existir.

## Uma checklist curta

- Configura um remoto desde o início (`init --git --remote <url>`, ou adiciona um mais tarde com
  `git remote add origin <url>`) para o `publish` sair mesmo da máquina.
- Publica depois de fazeres alterações, e confirma o indicador de sincronização (`status` da CLI/o
  badge "Remoto: ...") em vez de assumir que chegou ao remoto.
- Autoriza pelo menos uma máquina adicional cedo - não como último recurso depois de algo já ter
  corrido mal.
- Se dependes de uma única máquina, mantém um backup seguro de
  `~/.rikiki-vault/identity/private.key`.
- De vez em quando, testa mesmo a recuperação: numa máquina sobresselente ou nova, faz `clone` do
  vault (ou restaura a chave em backup) e confirma que consegues ler os ficheiros. Um backup nunca
  testado não é uma rede de segurança a sério.

O que *não* está em risco por si só: `vault/manifest.json` e `vault/recipients.json` também estão
controlados pelo Git (hashes/caminhos de ficheiros e chaves públicas autorizadas - sem conteúdo em
claro), por isso voltam automaticamente com qualquer `clone`, em qualquer máquina.
