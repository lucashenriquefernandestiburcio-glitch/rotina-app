# Rotina Fitness

![Rotina Fitness](logo/rotina-fitness-logo.png)

App de rotina diária, dieta, treino, água e progresso, com lembretes para o calendário do celular.
Funciona no navegador, instala na tela inicial e abre sem internet. Os registros ficam guardados só no aparelho de quem usa.

Treino: musculação, natação, ciclismo e corrida, até 2 treinos por dia (por exemplo, academia de manhã e natação à noite) e treino em jejum, com o café da manhã reorganizado para depois do treino.
Plano alimentar com caneta (Mounjaro, Ozempic, Wegovy e parecidas): na aba Dieta › Meu plano, informe o medicamento e a dose; o app reduz as porções, divide a comida em 6 refeições pequenas (com mini-refeição proteica), mantém a proteína, ajusta as metas e a lista de compras e lembra do dia da aplicação. Informação geral, não substitui orientação médica.
Registro diário da caneta: apetite, enjoo e energia com um toque, com sugestões (por exemplo, ajustar o nível de apetite do cardápio) e alerta de proteína atrasada no fim do dia. Sinais de enjoo forte ou energia baixa repetidos mandam procurar o médico.
Perfil com foto: tirar foto ou escolher da galeria, recorte quadrado. A foto entra no arquivo de backup e volta ao importar.
Backup: no app Android há backup automático semanal na pasta Downloads (perfis com PIN ficam de fora).
Aparência: texto normal, grande ou muito grande, e o modo "só o essencial" (Hoje, Treino, Dieta e Ajustes), em Ajustes.
Alarmes nativos (só no app Android): lembretes de refeições, treinos, água e da caneta tocam com a tela fechada e voltam sozinhos depois de reiniciar o celular. Ligue em Ajustes › Lembretes no celular. No Android 13 ou mais novo, o app pede permissão para mostrar avisos.
Fontes (Barlow e Barlow Condensed) vão embutidas no arquivo, sem depender da internet; licença SIL OFL 1.1 em `licencas/`.

- `logo/`: logo e ícone do app
- `index.html`: o app (arquivo único)
- `manifest.webmanifest` e `icons/`: nome e ícones para instalar
- `sw.js`: funcionamento offline
- `android/`: projeto Android que embute o `index.html` num WebView e gera o `.apk`
- `licencas/`: licença das fontes embutidas
- `.github/workflows/apk.yml`: compila o `.apk` no GitHub e publica na aba Releases (`apk-latest`)

## Baixar o .apk

Depois que a compilação termina (aba Actions), o arquivo fica em
`https://github.com/lucashenriquefernandestiburcio-glitch/rotina-app/releases/download/apk-latest/Rotina.apk`.
No Android é preciso permitir "instalar apps desconhecidos" para o navegador usado no download.

O `.apk` é assinado com a chave `android/keystore/rotina.jks`, que está neste repositório público de propósito:
serve só para manter a mesma assinatura entre versões (atualizar sem desinstalar). Não use este projeto em loja de apps sem trocar a chave.
