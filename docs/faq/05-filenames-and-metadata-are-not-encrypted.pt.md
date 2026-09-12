# O GitHub (ou quem hospeda o remoto) vê os nomes dos meus ficheiros/pastas?

*[Read in English](05-filenames-and-metadata-are-not-encrypted.md)*

Não, desde o formato RV02 (vê a [FAQ 11](11-migrating-to-encrypted-paths.pt.md) se tiveres um vault
mais antigo ainda no formato anterior) - os paths e nomes de ficheiros reais são cifrados ao lado do
conteúdo, não são metadados visíveis à parte.

## O manifest é cifrado por inteiro

O `manifest.json` - o ficheiro que mapeia cada entrada seguida para o seu path real - é ele próprio
selado com a mesma cifra híbrida (X25519 + AES-GCM, envolvida por máquina autorizada) já usada para
o conteúdo dos ficheiros. Em disco e no histórico do git, é um blob binário opaco; não há nenhum
path em claro lá dentro, a não ser que sejas uma máquina autorizada capaz de o decifrar mesmo. É
também por isso que revogar o acesso de uma máquina recifra o manifest, não só o conteúdo dos
ficheiros - vê a [FAQ 08](08-revoking-a-stolen-machine.pt.md).

## Os nomes em `documents/` são aleatórios, não os paths reais

Cada ficheiro publicado fica em `documents/<id-opaco>.enc` - um identificador aleatório gerado uma
vez por ficheiro, sem relação com o seu nome ou localização reais, e nunca dentro de pastas que
espelhem a tua estrutura de diretórios real (o nome de uma pasta pode ser tão revelador como o de
um ficheiro). Se guardares `impostos/2025/declaracao.pdf` em `local/`, quem tiver acesso de leitura
ao remoto Git vê um ficheiro com um nome tipo
`documents/f47ac10b-58cc-4372-a567-0e02b2c3d479.enc` - nada sobre "impostos" ou "declaracao.pdf"
sobrevive a nada visível sem decifrar.

O próprio formato `.enc` (`RV02`) também deixou de trazer qualquer campo de nome de ficheiro no seu
cabeçalho - o formato anterior trazia, em claro, o que era em si um leak à parte; o manifest (depois
de decifrado) é agora a única fonte da verdade sobre o que um dado id realmente é.

## O que mais fica visível

Esta parte não é afetada por nada do que foi dito acima - nunca foi sobre nomes de ficheiros:

- **Mensagens e datas de commit** - o que quer que passes a `publish -m "..."` (ou escrevas no
  ecrã de revisão da app desktop) é uma mensagem de commit Git simples, não cifrada.
- **Tamanho aproximado do ficheiro** - o texto cifrado AES-GCM é só ligeiramente maior do que o
  original (um overhead fixo por ficheiro, sem padding), por isso os tamanhos ficam visíveis com
  precisão razoável.
- **Quando publicaste, e com que frequência** - datas e frequência de commits são histórico Git
  normal.

## O que fica realmente protegido

Conteúdo, paths reais e nomes de ficheiros reais - tudo cifrado da mesma forma, tudo invisível para
quem não tiver a chave privada de uma máquina autorizada. Conclusão prática: não ponhas nada
sensível numa mensagem de commit, e se o simples facto de certos ficheiros existirem (independente
do nome) já for sensível, o modelo desta ferramenta não esconde a contagem ou o tamanho aproximado
dos ficheiros do teu serviço de Git nem de mais ninguém que consiga ler o repositório.
