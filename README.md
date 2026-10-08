# Rotina Fitness

![Rotina Fitness](logo/rotina-fitness-logo.png)

App de rotina diária, dieta, treino, água e progresso, com lembretes para o calendário do celular.
Funciona no navegador, instala na tela inicial e abre sem internet. Os registros ficam guardados só no aparelho de quem usa.

Treino: musculação, natação, ciclismo e corrida, até 2 treinos por dia (por exemplo, academia de manhã e natação à noite) e treino em jejum, com o café da manhã reorganizado para depois do treino.
Caneta emagrecedora: cadastro do medicamento e da dose; o app reduz as porções, divide a comida em 6 refeições pequenas, mantém a proteína e lembra do dia da aplicação. Informação geral, não substitui orientação médica.

- `logo/`: logo e ícone do app
- `index.html`: o app (arquivo único)
- `manifest.webmanifest` e `icons/`: nome e ícones para instalar
- `sw.js`: funcionamento offline
- `android/`: projeto Android que embute o `index.html` num WebView e gera o `.apk`
- `.github/workflows/apk.yml`: compila o `.apk` no GitHub e publica na aba Releases (`apk-latest`)

## Baixar o .apk

Depois que a compilação termina (aba Actions), o arquivo fica em
`https://github.com/lucashenriquefernandestiburcio-glitch/rotina-app/releases/download/apk-latest/Rotina.apk`.
No Android é preciso permitir "instalar apps desconhecidos" para o navegador usado no download.

O `.apk` é assinado com a chave `android/keystore/rotina.jks`, que está neste repositório público de propósito:
serve só para manter a mesma assinatura entre versões (atualizar sem desinstalar). Não use este projeto em loja de apps sem trocar a chave.
