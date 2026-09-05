# O GitHub (ou quem hospeda o remoto) vê os nomes dos meus ficheiros/pastas?

*[Read in English](05-filenames-and-metadata-are-not-encrypted.md)*

Sim, em duas camadas. Só o *conteúdo* dos teus ficheiros é cifrado - tudo o resto sobre onde estão
e como se chamam continua visível para quem conseguir ler o repositório.

## O próprio caminho

Cada ficheiro publicado fica em `documents/<caminho-em-claro>.enc` - esse caminho espelha
exatamente `local/`, pasta a pasta, nome a nome, só com `.enc` acrescentado (ver a
[quarta pergunta da FAQ](04-decrypting-without-the-app.pt.md) para o formato completo). Se
guardares `impostos/2025/declaracao.pdf` em `local/`, quem tiver acesso de leitura ao remoto Git
vê um ficheiro em `documents/impostos/2025/declaracao.pdf.enc` - o nome da pasta "impostos" e do
ficheiro "declaracao.pdf" estão ali, mesmo que não consigam abrir o que está lá dentro.

## O nome do ficheiro também fica guardado outra vez, dentro do ficheiro

Menos óbvio: o formato RV01 escreve o nome de ficheiro original no próprio cabeçalho do `.enc`,
*antes* de qualquer cifra acontecer (`RvEncryptedFileFormatCodec` escreve-o como uma string UTF-8
simples, com o comprimento à frente). Por isso, mesmo que tivesses mudado o nome do ficheiro em
disco para algo genérico antes de sair de `local/`, o texto cifrado continua a trazer o nome que
tinha no momento da cifra, em claro. Isto é redundante com o caminho na prática, mas significa que
o nome do ficheiro não está mesmo nada protegido pela cifra - nunca esteve dentro da parte selada.

## O que mais fica visível

- **Mensagens e datas de commit** - o que quer que passes a `publish -m "..."` (ou escrevas no
  ecrã de revisão da app desktop) é uma mensagem de commit Git simples, não cifrada.
- **Tamanho aproximado do ficheiro** - o texto cifrado AES-GCM é só ligeiramente maior do que o
  original (um overhead fixo por ficheiro, sem padding), por isso os tamanhos ficam visíveis com
  precisão razoável.
- **Quando publicaste, e com que frequência** - datas e frequência de commits são histórico Git
  normal.

## O que fica realmente protegido

Só o conteúdo. É esse o desenho todo: um repositório Git privado (esta app nunca faz afirmações
sobre que serviço de repositório usas, nem sobre quão privado é - isso é contigo) com o
*conteúdo* cifrado ponta-a-ponta, não um sistema que esconde a existência ou a forma dos teus
ficheiros. Conclusão prática: não ponhas nada sensível num nome de ficheiro nem numa mensagem de
commit, e se o simples facto de certos ficheiros existirem já for sensível, o modelo desta
ferramenta não esconde isso do teu serviço de Git nem de mais ninguém que consiga ler o
repositório.
