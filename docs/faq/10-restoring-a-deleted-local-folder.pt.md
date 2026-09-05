# Apaguei sem querer a pasta `local/` nesta máquina — os ficheiros publicados ainda lá estão, como os recupero?

*[Read in English](10-restoring-a-deleted-local-folder.md)*

Corre `restore` - sem rede, sem precisares da chave de backup. Esta é uma situação diferente das
perguntas anteriores sobre "perdi o SSD": aqui a *máquina em si* está bem, `documents/`/`.git`/a
tua identidade continuam intactos, só a cópia de trabalho em claro é que foi apagada.

## Porque é que o `pull` sozinho não resolve isto

É tentador assumir que o `pull` voltaria simplesmente a descarregar tudo, mas não - o `pull` só
volta a decifrar uma entrada do manifesto cujo hash mudou desde o *último* pull. Se nada foi
publicado remotamente entretanto, todas as entradas continuam com o mesmo hash de sempre, por
isso o `pull` ignora-as todas - não tem nenhuma noção de "também verificar se o ficheiro ainda
está mesmo em `local/`". O `clone` também não serve de recurso, porque exige uma pasta de destino
vazia, e este vault já tem `.git`/`vault`/`documents` no sítio.

## O que o `restore` faz em vez disso

Ao contrário do `pull`, o `restore` não compara nada com um estado "antes" - percorre todas as
entradas do manifesto atual e decifra diretamente a partir de `documents/`, totalmente offline.
Por omissão é não-destrutivo: um ficheiro que já esteja em `local/` fica intocado, só é contado
como "já existia", por isso corrê-lo não arrisca destruir trabalho que ainda não publicaste.

```bash
java -jar cli/target/rikiki-vault.jar -C <vault> restore
```

Ou na app desktop: **"Mais ▾" → "Restaurar ficheiros em falta..."** na toolbar da janela
principal.

## Se um ficheiro precisar de ser forçado a voltar à versão publicada

Passa `--force` (CLI) ou aceita a pergunta seguinte que a app desktop mostra quando alguns
ficheiros foram ignorados - isto redecifra *todas* as entradas, substituindo também os ficheiros
que já existem em `local/`. Útil se um ficheiro ficou corrompido em vez de apagado, mas atenção:
qualquer edição local não publicada num ficheiro substituído desaparece, trocada pelo que foi
publicado pela última vez. Sem `--force`, nenhum desse risco existe - só consegue voltar a
acrescentar ficheiros, nunca remover ou substituir um.

```bash
java -jar cli/target/rikiki-vault.jar -C <vault> restore --force
```
