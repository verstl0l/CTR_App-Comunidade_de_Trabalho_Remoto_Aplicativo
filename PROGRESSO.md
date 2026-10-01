# CTR — Progresso de Desenvolvimento

Documento que registra o andamento do projeto CTR (Comunidade de Trabalho Remoto) — aplicativo Android para gestao de equipes remotas.

Ultima atualizacao: Outubro/2026
Versao atual: 1.3
Status: Em desenvolvimento

---

## Visao Geral

O CTR e um app Android desenvolvido em Kotlin, com backend Firebase (Firestore + Auth) e Cloudinary (upload de midia). O projeto esta organizado em ondas de desenvolvimento, cada uma resolvendo um conjunto de problemas ou adicionando features.

### Stack

| Camada | Tecnologia |
|--------|-----------|
| Linguagem | Kotlin |
| UI | XML + Material Design Components |
| Backend | Firebase Authentication |
| Banco | Cloud Firestore (NoSQL) |
| Storage | Cloudinary (unsigned upload) |
| Push | OneSignal |
| Graficos | MPAndroidChart |
| Imagens | Glide + PhotoView |
| Video | VideoView + android-video-trimmer |
| Crop | uCrop |

---

## Ondas Concluidas

### Onda 0 — Correcao critica de ID deterministico

Problema: membros_equipe usava ID aleatorio (.add()), o que quebrava a verificacao isMembroEquipe() nas Firestore Rules.

Solucao:
- Padrao adotado: ID = {email}_{equipeId}
- Migracao aplicada em 4 arquivos: CriarEquipeActivity, AceitarConviteActivity, PedirEntradaActivity, GerenciarEquipeActivity
- Script de migracao one-shot em MainActivity (flag membros_migrados_v2)

Impacto: Chat de equipe passou a funcionar para membros nao-donos.

---

### Onda 1 — Seguranca nas Firestore Rules

Problema: Rules estavam permissivas demais — qualquer logado podia ler/escrever em qualquer colecao.

Solucao:
- membros_equipe.read restrito a membros/dono
- notificacoes.create exige remetente igual a mim
- anexos.create/delete exige enviadoPor igual a mim
- usuarios.update com whitelist de campos
- comentarios.read e grupos.read restritos
- Helper ehDaEquipe(equipeId) centralizado

Impacto: App blindado contra scraping e escrita arbitraria.

---

### Onda 2 — UX do Chat (estilo WhatsApp)

O que foi adicionado:
1. Horario em cada mensagem (HH:mm)
2. Separador de data ("HOJE", "ONTEM", "12/10/2026")
3. Info da mensagem no long press (data, tipo, tamanho, resposta)

Implementacao:
- Nova sealed class ItemChat (MensagemItem | SeparadorData)
- MensagemAdapter reformulado para lidar com 5 tipos de view (texto, foto, video, arquivo, separador)
- ItemChat.deMensagens() injeta separadores onde o dia muda

Impacto: Chat visualmente profissional.

---

### Onda 4 — Convite Reverso

Problema: Usuario nao conseguia pedir entrada em equipe — so era convidado.

Solucao:
- Botao "PEDIR PARA ENTRAR" em BuscarEquipesActivity
- Tela PedirEntradaActivity com motivos + especialidades
- Bloqueio de pedido duplicado (verifica antes de enviar)
- Notificacao para o dono da equipe
- Tela PedidosPendentesActivity para o dono aceitar/recusar
- Fluxo completo: pedido -> notificacao -> aceitar -> membro criado -> solicitante notificado

Impacto: Fluxo bidirecional (dono convida + usuario pede).

---

### Onda 5 Fase 1 — Correcao de Bugs Criticos

Bugs corrigidos:

| Numero | Bug | Solucao |
|--------|-----|---------|
| 1 | PERMISSION_DENIED ao aceitar pedido | Rules membros_equipe.create afrouxadas |
| 2 | PERMISSION_DENIED ao apagar conversa | Rules chats/mensagens.delete permitem qualquer participante |
| 3 | PERMISSION_DENIED ao alterar status de trabalho | Rules trabalhos.update permitem membros |
| 4 | PERMISSION_DENIED ao excluir equipe | Rules de subcolecoes permitem dono |
| 5 | Apagar mensagem nao atualizava na hora | Campo apagada=true + listener do ChatPaginacaoHelper reescrito |
| 6 | Botao "CRIAR CONTA" nao funcionava | IDs renomeados (button3 -> btnCadastrar) |
| 7 | Admin se removia da propria equipe | Validacao em GerenciarEquipeActivity |
| 8 | Barra de status sobrepunha conteudo | fitsSystemWindows=true + adjustResize |
| 9 | Navigation bar cobria campo de texto | windowSoftInputMode=adjustResize em todas as telas |
| 10 | Chat por email crashava | Validacao de outroEmail/chatId no onCreate |
| 11 | Foto de perfil nao fazia upload | Era VPN do usuario (nao era bug) |
| 12 | Caracteres limitados no chat | Adicionado inputType=textMultiLine no EditText |

Impacto: App estavel, sem crashes conhecidos, UI correta.

---

### Extras — Otimizacoes

| Numero | Onde | O que foi feito | Ganho |
|--------|------|-----------------|-------|
| 1 | NotificacoesActivity | addSnapshotListener em vez de get() | Tempo real, sem re-query |
| 2 | ListaConversasActivity | Listener no doc do usuario | Lista atualiza em tempo real |
| 3 | produtos | Filtro em memoria (sem nova query) | Trocar filtro = instantaneo |
| 4 | perfil + MensagemAdapter | Glide.signature() | Foto nao re-baixa |

Impacto: Menos leituras no Firestore, UI mais responsiva.

---

## Em Andamento

### Onda 5 Fase 2 — Testes Unitarios + CI/CD

Objetivo: Adicionar cobertura de testes e automacao de build.

Progresso:
- Concluido: AnexoHelperTest — 9 testes passando (formatacao de bytes)
- Em andamento: MensagemTest
- Pendente: ChatPaginacaoHelperTest
- Pendente: GitHub Actions (CI)
- Pendente: Badge no README

---

## Proximas Ondas

### Onda 3 — Chat Equipe Hub

Objetivo: Tela unica com chat geral + grupos + PVs com membros.
Status: Nao iniciada.

### Onda 7 — Refatoracao

Objetivo: BaseChatActivity (unifica 3 chats) + ViewBinding.
Status: Nao iniciada.

### Onda 8.4 — Desenho com Dedo

Objetivo: Canvas customizado para desenhar e enviar como imagem.
Status: Nao iniciada.

---

## Metricas

| Metrica | Valor |
|---------|-------|
| Linhas de codigo (Kotlin) | ~8.000 |
| Activities | 30 |
| Helpers | 8 |
| Colecoes Firestore | 14 |
| Testes unitarios | 9 (em crescimento) |

---

## Objetivos de Qualidade

- [x] Zero crashes conhecidos
- [x] Rules de seguranca consistentes
- [x] Real-time em telas criticas
- [ ] Cobertura de testes maior que 30%
- [ ] CI/CD funcionando
- [ ] Documentacao de API

---

## Documentacao Adicional

- README.md — visao geral do projeto
- FIREBASE_SETUP.md — como configurar o Firebase
- firestore.rules — regras de seguranca
- PROGRESSO.md — este documento

---

## Links

- Repositorio GitHub: https://github.com/verstl0l/CTR_App-Comunidade_de_Trabalho_Remoto_Aplicativo
- Versao Web: https://github.com/verstl0l/CTR_Web-Comunidade_de_Trabalho_Remoto_Web.git

---

Documento vivo. Atualizado a cada onda concluida.