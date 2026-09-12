# Tenho um vault antigo (de antes dos paths serem cifrados) — como o migro?

*[Read in English](11-migrating-to-encrypted-paths.md)*

Versões desta app anteriores ao formato RV02 guardavam o `manifest.json` como JSON simples - com o
path real de cada ficheiro em claro - e davam a cada ficheiro cifrado em `documents/` o nome do seu
path real (`documents/<path-real>.enc`), estrutura de pastas incluída. Quem tivesse acesso de
leitura ao remoto conseguia ver todos os nomes de ficheiros e pastas, mesmo sem conseguir decifrar
nenhum conteúdo. Vê a [FAQ 05](05-filenames-and-metadata-are-not-encrypted.pt.md) para a história
completa do que isso expunha.

A partir do formato RV02, o próprio `manifest.json` passa a ser cifrado (da mesma forma que
qualquer ficheiro seguido já era), e cada ficheiro em `documents/` recebe um id aleatório e sem
significado em vez do seu nome real. `migrate-format` é o comando único e definitivo que converte
um vault existente da forma antiga para a nova.

## Antes de correr

- **Todas as máquinas que usam este vault têm de estar atualizadas para uma versão da app que
  suporte RV02 primeiro.** Uma versão antiga da app não consegue sequer abrir um vault RV02 - não
  sabe decifrar o manifest, e falha com um erro claro "este vault não está no formato atual" se
  tentares o inverso (uma app atualizada a abrir um vault ainda não migrado dá o mesmo tipo de erro
  claro, a dizer para migrar).
- **Garante que todas as máquinas estão totalmente atualizadas (pull feito) e sem nada por
  publicar**, e escolhe uma máquina para correr mesmo a migração. Isto não é uma operação
  distribuída - reescreve o vault uma vez, a partir de qualquer que seja a máquina que a corre, e
  publica o resultado. Qualquer outra máquina que publique a partir de um checkout antigo,
  pré-migração, depois disso, vai entrar em conflito feio.
- **Faz backup da pasta do vault primeiro** (um `cp -r` simples, ou só anota o hash do commit atual
  para teres `git reset`/`git checkout` disponíveis). A migração é definitiva a partir daí, mas nada
  é destruído do histórico do próprio Git - o commit pré-migração continua lá - por isso isto é uma
  rede de segurança extra barata, não um requisito estrito.

## A correr

Na máquina que escolheste, com o CLI:

```bash
# Ver o que aconteceria, sem mudar nada:
rikiki-vault -C /caminho/para/o/teu/vault migrate-format --dry-run

# Quando estiveres pronto:
rikiki-vault -C /caminho/para/o/teu/vault migrate-format --yes
```

Isto decifra cada ficheiro existente com a tua identidade atual, recifra-o sob um id novo e
aleatório (descartando o antigo ficheiro `.enc` com nome em claro), reescreve o `manifest.json`
como um blob RV02 cifrado, e publica o resultado num único commit. Se algo falhar a meio, nada é
apagado e nada é publicado até todos os ficheiros migrarem com sucesso - o teu vault fica
exatamente tão legível como estava antes de correres o comando.

Qualquer ficheiro que esta identidade não esteja atualmente autorizada a decifrar é reportado e
deixado na forma antiga, tal como o `restore` já faz para uma entrada não autorizada - não bloqueia
o resto da migração.

## Depois de migrar

A próxima ação de cada outra máquina sobre este vault deve ser um `pull` (ou um `clone` de novo),
não um `publish` - é assim que apanha o formato novo em vez de tentar continuar a partir de um
manifest antigo, pré-migração, que ainda consegue (por agora) ler localmente.

Ainda não há ação na GUI para isto - `migrate-format` é só CLI por agora, precisamente por causa do
requisito "coordenar todas as máquinas primeiro" acima, que não encaixa bem num único botão na app
desktop sem mais suporte de interface do que existe hoje.
