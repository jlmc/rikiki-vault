# A minha chave privada em disco está protegida por password?

*[Read in English](07-private-key-is-not-password-protected.md)*

Não. `~/.rikiki-vault/identity/private.key` é um ficheiro PKCS8 DER simples, protegido só por
permissões do sistema de ficheiros - nunca por uma passphrase, e nunca guardado no
keychain/gestor de credenciais do sistema operativo.

## O que está lá de facto

O `LocalKeyStoreAdapter` grava a chave privada em disco exatamente como foi gerada, com permissões
só para o dono (`rw-------` no macOS/Linux - ver a secção "Hardening notes" do README principal).
Isso é proteção real contra *outras contas de utilizador* numa máquina partilhada e em
funcionamento, mas não é cifra da própria chave: sem password mestra, sem entrada no Keychain do
macOS, sem entrada no Credential Manager do Windows, nada a pedir-te para a desbloquear. Quem
conseguir ler ficheiros como se fosses tu - o root, uma sessão comprometida enquanto estás com
sessão iniciada, ou alguém que retire o disco e o monte noutra máquina sem cifra de disco - lê a
chave diretamente, sem precisar de nenhuma password.

## Porque é que está desenhado assim

A app precisa de usar esta chave sem interação em cada `pull`/`publish`/`clone`, incluindo
operações em segundo plano na app desktop - uma chave protegida por passphrase significaria
escrever uma password (ou desbloquear algum prompt de keychain do sistema) em cada sincronização.
Essa troca empurra a proteção real um nível abaixo: para o que quer que esteja a proteger a tua
conta e o teu disco, antes de mais nada.

## De onde tem mesmo de vir a proteção

**Cifra de disco completo** - FileVault (macOS), BitLocker (Windows), LUKS (Linux). Com isto
ativado, o ficheiro da chave só é legível depois de o disco ser desbloqueado com a tua password de
login; sem isso, quem tiver acesso físico ao disco lê a chave tão facilmente como qualquer outro
ficheiro. Isto não é opcional se te importares mesmo com as garantias que esta app tenta dar - um
vault cifrado ponta-a-ponta é só tão forte quanto o elo mais fraco, e um disco sem cifra com a
chave privada em claro é esse elo fraco.

É também exatamente por isto que a [primeira pergunta da FAQ](01-disk-failure-and-backups.pt.md)
trata o `private.key` como algo que precisa de um plano de backup: é um ficheiro pequeno, sensível
e sem cifra própria - vale a pena protegê-lo deliberadamente nas duas direções, contra perda e
contra exposição.
