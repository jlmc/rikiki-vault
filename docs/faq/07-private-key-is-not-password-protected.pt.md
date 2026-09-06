# A minha chave privada em disco está protegida por password?

*[Read in English](07-private-key-is-not-password-protected.md)*

Opcionalmente, sim. `~/.rikiki-vault/identity/private.key` pode ser protegida por passphrase - mas
é opt-in e desativado por omissão: uma identidade recém-gerada continua a ser um ficheiro PKCS8
DER simples, protegido só por permissões do sistema de ficheiros, exatamente como antes. Nunca é
guardada no keychain/gestor de credenciais do sistema operativo, com ou sem passphrase.

## O que está lá de facto

O `LocalKeyStoreAdapter` grava a chave privada em disco com permissões só para o dono
(`rw-------` no macOS/Linux - ver a secção "Hardening notes" do README principal), com ou sem
passphrase. Sem passphrase, essa é a *única* proteção: sem password mestra, nada a pedir-te para a
desbloquear. Quem conseguir ler ficheiros como se fosses tu - o root, uma sessão comprometida
enquanto estás com sessão iniciada, ou alguém que retire o disco e o monte noutra máquina sem
cifra de disco - lê a chave diretamente.

Com uma passphrase definida (`rikiki-vault set-passphrase`, ou Configurações → Segurança na app
desktop), o ficheiro passa a ser um envelope cifrado autodescritivo: um salt aleatório,
PBKDF2-HMAC-SHA256 (600.000 iterações) estica a passphrase numa chave de cifra, e os bytes da
chave privada em si ficam selados com AES-GCM sob essa chave. O `LocalKeyStoreAdapter` deteta qual
dos dois formatos está em disco a partir de um prefixo de magic bytes, por isso uma identidade já
existente sem proteção continua a carregar exatamente como antes até a protegeres explicitamente -
nada parte, nada migra automaticamente.

## Porque é opt-in, e como funciona o pedido da passphrase

A app continua a precisar de usar esta chave sem interação em operações em segundo plano - o
auto-refresh e o push em background da app desktop, em particular. Uma identidade protegida por
passphrase não interfere com isso: pedimos a passphrase uma vez, quando um vault com identidade
protegida é aberto (ou uma vez por invocação da CLI que precise dela), e a identidade desbloqueada
fica depois em cache em memória durante o resto dessa sessão - nunca é escrita de volta em disco,
nem guardada em nenhum sítio persistente. Ações em segundo plano durante a mesma sessão reutilizam
a identidade em cache em vez de voltar a pedir. Fechar a app (ou terminar o processo da CLI) limpa-a
- a próxima abertura volta a pedir.

Isto significa que uma sessão já desbloqueada, deixada aberta e sem vigilância, continua a expor o
que estiver no ecrã a quem conseguir usar o teu computador já destrancado - uma passphrase no
ficheiro da chave protege o ficheiro em repouso, não uma sessão que já está em execução. Isso é um
problema diferente (registado como possível funcionalidade futura de bloqueio automático), não algo
que uma passphrase no `private.key` alguma vez teve como objetivo resolver.

## Sem recuperação, por desenho

Mudar ou remover uma passphrase exige sempre a atual primeiro - não há backdoor nem reset.
Esquecê-la significa que a identidade (e tudo o que ela alguma vez decifrou) só é recuperável a
partir de uma cópia de segurança do `private.key` sem proteção, se existir alguma. Trata definir
uma passphrase como tratarias qualquer outra credencial: vale a pena anotá-la nalgum sítio seguro,
não vale a pena confiar que a vais recordar.

## De onde tem mesmo de vir a proteção real

**Cifra de disco completo** - FileVault (macOS), BitLocker (Windows), LUKS (Linux) - continua tão
necessária como antes, com ou sem passphrase. Um `private.key` protegido resiste a quem só consiga
o ficheiro em si; não faz nada por quem tenha acesso à tua máquina já destrancada e com sessão
iniciada. A cifra de disco é o que transforma "um atacante conseguiu o disco" e "um atacante
conseguiu uma sessão já iniciada" em dois cenários diferentes, defendidos separadamente, em vez de
um só.

É também exatamente por isto que a [primeira pergunta da FAQ](01-disk-failure-and-backups.pt.md)
trata o `private.key` como algo que precisa de um plano de backup: é um ficheiro pequeno e sensível
- vale a pena protegê-lo deliberadamente nas duas direções, contra perda e contra exposição, quer
tenhas definido uma passphrase quer não.
