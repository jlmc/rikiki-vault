# FAQ

*[Read in English](README.md)*

Respostas práticas a perguntas que surgem ao usar um vault no dia a dia - ao contrário do
[`README.pt.md`](../../README.pt.md) (como compilar/correr/usar a app) ou do
[`docs/release-process.md`](../release-process.md) (como se faz um release, em inglês).

1. [Como garantir que não perco acesso aos dados mesmo se o meu SSD se perder?](01-disk-failure-and-backups.pt.md)
   — porque é que a chave privada, não os ficheiros cifrados, é o que precisas mesmo de proteger
   com backup, e as duas formas de o fazer.
2. [Perdi o SSD, o remoto está atualizado, e fiz backup de `private.key` e `public.key` — como recupero?](02-recovering-with-both-keys-backed-up.pt.md)
   — restaura os dois ficheiros antes de clonar e a tua identidade existente, já autorizada, é
   reutilizada, sem precisar de reautorização.
3. [Perdi o SSD, o remoto está atualizado, mas só fiz backup do `private.key`, não do `public.key` — como recupero?](03-recovering-with-only-the-private-key.pt.md)
   — dois caminhos: ser reautorizado por outra máquina (mais simples), ou derivar a chave pública a
   partir da privada (funciona sozinho, mas depende de ferramentas externas).
4. [Não quero mais usar a app. Tenho os dados e as duas chaves — como decifro tudo sem ela?](04-decrypting-without-the-app.pt.md)
   — o formato do ficheiro cifrado explicado, mais um script OpenSSL + shell pronto a correr que
   decifra o vault inteiro sem Java, Maven, nem o código-fonte da app.
5. [O GitHub (ou quem hospeda o remoto) vê os nomes dos meus ficheiros/pastas?](05-filenames-and-metadata-are-not-encrypted.pt.md)
   — não, desde o RV02: paths/nomes ficam cifrados como o conteúdo; mensagens de commit/tamanhos/
   timing continuam visíveis.
6. [Apaguei um ficheiro do vault — desapareceu mesmo?](06-deleting-a-file-is-not-permanent.pt.md)
   — não do histórico do Git; o que é preciso para o purgar mesmo, e o que a purga não desfaz.
7. [A minha chave privada em disco está protegida por password?](07-private-key-is-not-password-protected.pt.md)
   — não, só por permissões do sistema de ficheiros; porquê, e de onde tem de vir a proteção real.
8. [Uma máquina foi roubada/comprometida — como garanto que deixa de conseguir ler ficheiros novos?](08-revoking-a-stolen-machine.pt.md)
   — o `revoke` reencripta tudo com chaves novas, mas não chega a dados que a máquina já tinha
   descarregado antes de a revogares.
9. [O que acontece se duas máquinas editarem o mesmo ficheiro antes de sincronizar?](09-conflicting-edits.pt.md)
   — o `pull` reporta o conflito com o hash de cada lado e deixa os dois intocados; resolver é um
   passo manual.
10. [Apaguei sem querer a pasta `local/` nesta máquina — como recupero os ficheiros?](10-restoring-a-deleted-local-folder.pt.md)
    — o `restore` reconstrói `local/` a partir de `documents/`, offline, sem precisar da chave de backup.
11. [Tenho um vault antigo (de antes dos paths serem cifrados) — como o migro?](11-migrating-to-encrypted-paths.pt.md)
    — o `migrate-format` converte um vault para o formato RV02, em que paths e nomes de ficheiros
    também ficam cifrados, não só o conteúdo.
