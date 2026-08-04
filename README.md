# Rotina 📱✅

App Android de rotina e hábitos, altamente personalizável, com notificações push
nos horários que você definir — várias por dia, por hábito.

## Funcionalidades

- ✅ Hábitos ilimitados, cada um com nome, cor e dias da semana próprios
- ⏰ Vários horários de lembrete por dia para cada hábito
- 🔔 Notificações pontuais com botões **"Feito ✓"** e **"Adiar"** direto na notificação
- 📅 Calendário com o histórico dos hábitos em mapa de calor
- 🗓️ Lembretes avulsos com data, horário opcional e repetição
  (diária, semanal, mensal por dia do mês, mensal por semana, anual)
- 🔥 Sequências (streaks), estatísticas e gráfico dos últimos 7 dias
- 💾 Backup e restauração dos dados em arquivo
- 🌗 Tema claro/escuro automático (segue o celular) e idioma português/inglês
- 🔒 100% offline — seus dados ficam só no seu celular

## Como instalar no celular

1. Abra este repositório no navegador do celular e vá em **Releases** (ou acesse
   `https://github.com/jpm100/Rotina/releases/latest`).
2. Baixe o arquivo **`Rotina.apk`**.
3. Toque no arquivo baixado. O Android vai pedir permissão para
   "instalar apps desconhecidos" — autorize (só na primeira vez).
4. Pronto! Abra o app **Rotina**, permita as notificações e crie seu primeiro hábito.

> 💡 Dica: em **Ajustes → Alarmes exatos**, confirme que a permissão está ativa
> para os lembretes chegarem exatamente na hora.

## Como o APK é gerado

A cada alteração no código, o GitHub Actions compila o app automaticamente e
publica o APK atualizado na release **latest**. Não é preciso instalar nada no
computador.

## Tecnologia

Kotlin • Jetpack Compose (Material 3) • Room • AlarmManager (alarmes exatos) •
GitHub Actions para build
