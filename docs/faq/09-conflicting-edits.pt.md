# O que acontece se duas máquinas editarem o mesmo ficheiro antes de sincronizar?

*[Read in English](09-conflicting-edits.md)*

O `pull` deteta isso e reporta um conflito - com um hash SHA-256 de cada versão para as
distinguires - mas nunca sobrescreve nem funde nada automaticamente. Resolver é um passo manual.

## Como é detetado

O `PullVaultService` analisa as tuas alterações locais por publicar *antes* de fazer o pull, e
depois compara o manifesto antes e depois do pull para ver o que mudou remotamente. Se o mesmo
caminho aparecer dos dois lados - alterado localmente e alterado remotamente desde a tua última
sincronização - fica registado como um `VaultConflict` em vez de ser aplicado: o teu ficheiro
local fica exatamente como está, e o conflito é reportado com o tipo de alteração e o hash SHA-256
de cada lado (visível no diálogo de resultado do pull da app desktop, ou impresso pela CLI).

## O que *não* faz

Não há nenhuma ação de "usar a remota", "manter a local" ou comparação lado a lado embutida na
app - "manter a local" é simplesmente o que acontece por omissão, já que um caminho em conflito
nunca é tocado. Também não há nenhuma lógica de fusão (não é uma ferramenta de merge de texto). Se
quiseres a versão remota, tens de a ir buscar e decifrar tu mesmo - o ficheiro `.enc` em conflito
que o pull acabou de descarregar já corresponde à entrada do manifesto remoto, por isso já está em
`documents/`, decifrável da mesma forma descrita na
[quarta pergunta da FAQ](04-decrypting-without-the-app.pt.md).

## O que fazer de facto

1. Anota os dois hashes reportados para o caminho em conflito.
2. Decide qual versão queres (ou reconcilia as duas à mão - copia conteúdo entre elas, faz diff,
   o que o ficheiro pedir).
3. Sobrescreve `local/<caminho>` com o que decidires que deve ser o conteúdo final.
4. Publica outra vez - isto passa a ser só uma alteração local normal e segue o fluxo habitual de
   cifra e push.

Até fazeres isso, o próximo `status`/`pull` continua a reportar o mesmo caminho como alteração
local pendente (e o mesmo conflito, se voltares a fazer pull antes de publicar) - nada força uma
decisão num prazo próprio, mas também nada se resolve sozinho.
