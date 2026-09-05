# Apaguei um ficheiro do vault — desapareceu mesmo?

*[Read in English](06-deleting-a-file-is-not-permanent.md)*

Não do histórico do Git. Publicar uma remoção tira o ficheiro dali para a frente, mas todos os
commits anteriores onde ele ainda existia - conteúdo cifrado incluído - continuam no repositório.

## O que acontece de facto ao apagar

Quando apagas um ficheiro e publicas, o `PublishVaultService` remove a entrada dele do manifesto e
apaga o blob `.enc` da árvore *atual*, e depois faz commit dessa alteração. Esse commit novo é
real: `git log`/`clone`/`pull` a partir daí já não mostram o ficheiro nenhures. Mas o Git não
reescreve o histórico - o commit *anterior*, onde `documents/<caminho>.enc` ainda existia,
continua ali mesmo no histórico do repositório.

Quem tiver acesso de leitura ao repositório (qualquer máquina já autorizada, ou literalmente
qualquer pessoa com acesso a onde quer que o remoto viva) consegue recuperar esse conteúdo antigo
sem nenhuma ferramenta especial:

```bash
git log --all --full-history -- documents/<caminho>.enc   # encontra o commit onde ainda existia
git show <esse-commit>:documents/<caminho>.enc > recuperado.enc   # tira o texto cifrado antigo
```

Se essa máquina era um destinatário autorizado na altura em que o ficheiro foi cifrado, consegue
decifrar `recuperado.enc` exatamente como qualquer outro ficheiro `.enc` (ver a
[pergunta anterior da FAQ](04-decrypting-without-the-app.pt.md) para como, se não quiseres usar a
própria app).

## Para o remover mesmo do histórico

Esta app não faz isto por ti - é uma operação mais pesada e arriscada do que qualquer coisa que o
`publish` faz. Precisarias de uma ferramenta de reescrita de histórico (`git filter-repo`, ou a
mais antiga BFG Repo-Cleaner), seguida de um force-push, e depois todos os outros clones existentes
ficam dessincronizados do histórico reescrito (precisam de clonar de novo, não só de fazer `pull`).
Isso é uma disrupção real para um vault multi-máquina, por isso é um passo deliberado e manual -
não é algo que um `revoke` ou uma remoção fazem como efeito colateral.

## A parte que nenhuma reescrita de histórico resolve

Mesmo depois de reescrever o histórico, quem já tinha feito pull do commit antigo antes de o
reescreveres já tem esse texto cifrado (e, se era um destinatário autorizado, já o pode decifrar).
Reescrever o histórico impede que *futuros* clones o consigam - não chega às máquinas que já o
descarregaram. Assim que um ficheiro é publicado para um remoto, trata esse momento como o ponto
em que o partilhaste com quem já tinha (ou vier a ter, antes de o limpares) acesso de leitura a
esse remoto.
