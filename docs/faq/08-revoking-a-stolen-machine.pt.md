# Uma máquina foi roubada/comprometida — como garanto que deixa de conseguir ler ficheiros novos?

*[Read in English](08-revoking-a-stolen-machine.md)*

Corre `revoke <fingerprint>` (CLI) ou usa "Gerir Acesso" na app desktop - a partir de **outra**
máquina, ainda de confiança, já que a roubada obviamente não se pode auto-revogar. Isto protege
por completo tudo o que for publicado a partir daí, e até a maior parte do que já existia - mas há
uma nuance importante que vale a pena perceber antes de confiares cegamente nisto.

## O que o revoke faz de facto

O `RevokeMachineService` remove a máquina do registo de destinatários, e depois reencripta
**todos** os ficheiros já controlados para o conjunto de destinatários reduzido. Isto não é
reembrulhar a mesma chave de conteúdo - cada ficheiro recebe uma chave AES aleatória
completamente nova e um texto cifrado novo (confirmado em `JceHybridEncryptionAdapter.encrypt`:
gera-se sempre uma chave nova em cada chamada, nunca reutilizada). Por isso, depois de um revoke
completo e publicado, mesmo um ficheiro que existia desde o primeiro dia passa a existir no
repositório como um blob `.enc` totalmente novo, sem nenhuma entrada de chave embrulhada para o
fingerprint da máquina revogada. Isso é proteção real e eficaz - uma máquina que só tem o
ciphertext *novo* genuinamente não o consegue decifrar, seja como for.

## A nuance que não é óbvia

Se a máquina roubada já tinha feito `pull`/`clone` e descarregado os ficheiros `.enc` *antigos*
antes de a revogares, a chave privada dela continua a abrir exatamente essas cópias antigas -
revogar remove o acesso ao que for publicado *daí para a frente*, não invalida retroativamente uma
chave de decifra contra dados que essa máquina já tem no disco dela. Não há forma criptográfica de
"despartilhar" um ficheiro com uma chave que já teve oportunidade de o abrir. Isto não é uma falha
específica desta app - é verdade para qualquer controlo de acesso baseado em chaves: a revogação
olha para a frente, não apaga o passado.

## O que isto significa na prática

- Revoga assim que souberes que uma máquina está comprometida - quanto mais cedo, menor a janela
  em que consegue puxar coisas novas.
- Assume que a máquina roubada já tem uma cópia decifrada (ou decifrável) do que sincronizou pela
  última vez antes do roubo - trata esse conteúdo como exposto, tal como farias com qualquer outro
  dispositivo perdido com ficheiros locais nele.
- Ainda assim vale sempre a pena revogar de imediato: é isso que impede a fuga de crescer para
  incluir tudo o que for publicado depois.
