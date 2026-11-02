# Mogas Time Manager — como gerar o APK

A app já traz o horário LEE1TA pré-carregado e agenda 34 alarmes semanais
(10 min antes das aulas, na hora exacta nos blocos de estudo e rotina).

## Gerar o APK (sem Android Studio)
1. Cria uma conta em github.com e um repositório novo (privado serve).
2. Faz upload de TODO o conteúdo desta pasta (incluindo a pasta oculta .github).
   Dica: no site do GitHub, "Add file > Upload files" e arrasta tudo.
3. Abre o separador "Actions". O workflow "Compilar APK" arranca sozinho
   (ou carrega em "Run workflow"). Demora 5 a 10 minutos.
4. Quando terminar, abre a execução e descarrega "MogasTimeManager-APK" (ZIP com app-debug.apk).
5. Passa o app-debug.apk para o telemóvel e instala (permite "instalar apps desconhecidas").

## Primeiro arranque
- Aceita as notificações.
- Ajustes > "Testar alarme (10 s)", bloqueia o ecrã e fecha a app. Tem de tocar.
- Se não tocar: Definições > Aplicações > Mogas Time Manager > Bateria > "Sem restrições"
  e activa "Início automático" (Xiaomi, Tecno, Infinix, Oppo, Samsung).

## Alterar o horário
Dentro da app: toca num bloco para editar, ou no botão + para criar.
Para mudar o horário de origem, edita www/data.js.

## Limites desta versão
- O alarme toca como notificação de alta prioridade com som próprio (9 s).
  Ecrã de alarme a cobrir o bloqueio (como o despertador) fica para a próxima versão.
- A cópia de segurança é por texto (Ajustes > Gerar cópia).
