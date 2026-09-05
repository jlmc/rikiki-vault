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
