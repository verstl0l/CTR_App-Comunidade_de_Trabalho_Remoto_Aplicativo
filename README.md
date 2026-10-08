# CTR — Comunidade de Trabalho Remoto

![Android CI](https://github.com/verstl0l/CTR_App-Comunidade_de_Trabalho_Remoto_Aplicativo/actions/workflows/android.yml/badge.svg)

Aplicativo Android para **organização, comunicação e acompanhamento de tarefas** em equipes remotas. Combina gestão de equipes, chat em tempo real, envio de mídia, notificações e métricas de produtividade em um único app.

---

## ✨ Funcionalidades

### Autenticação e perfil
- Cadastro e login (Firebase Auth — email/senha)
- Perfil com avatar (upload via Cloudinary)
- Links sociais (LinkedIn, GitHub, portfólio)
- Edição de nome e profissão

### Equipes
- Criar, editar e excluir equipes
- Equipes públicas ou privadas
- Pedidos de entrada com motivos e especialidades
- Aprovação/recusa de pedidos
- Gerenciamento de membros (promover a admin, rebaixar, remover)
- Exclusão em cascata (trabalhos, chats, membros, convites)

### Trabalhos (tarefas)
- Criar, editar e excluir trabalhos
- Atribuição a membros da equipe
- Status: pendente, em progresso, concluído
- Prazos com data e hora
- Comentários por trabalho
- Anexos por trabalho (foto, vídeo, arquivo)
- Convite de usuários específicos para um trabalho

### Chat em tempo real
- **Conversas 1-a-1 (PV)**
- **Chat de equipe** (com hub: geral, grupos, individuais)
- **Grupos dentro da equipe**
- Mensagens de texto, foto, vídeo e arquivo
- Responder mensagens (citação)
- Swipe to reply
- Indicador "digitando..."
- Badge de mensagens não lidas em tempo real
- Favoritar mensagens
- Apagar mensagens (para todos)
- Galeria de mídia com swipe

### Notificações
- Notificações in-app (Firestore)
- Push notifications (OneSignal)
- Badge no ícone de chat e notificações

### Produtividade
- Gráfico de pizza por status dos trabalhos
- Gráfico de barras por membro
- Taxa de conclusão
- Filtros por status

### Edição de mídia
- Crop de imagem (uCrop)
- Trim de vídeo (Android Video Trimmer)
- Preview antes de enviar
- Legendas em fotos, vídeos e arquivos

### Outros
- Busca inteligente de usuários (rolo de resultados com foto)
- Bloqueio de usuários
- Cache local + sincronização em background
- Denormalização de dados (otimização de leitura no Firestore)

---

## 🛠 Tecnologias

| Camada | Tecnologia |
|--------|-----------|
| Linguagem | Kotlin |
| IDE | Android Studio |
| Auth | Firebase Authentication |
| Banco | Cloud Firestore (NoSQL) |
| Mídia | Cloudinary |
| Push | OneSignal |
| Imagens | Glide, PhotoView |
| Gráficos | MPAndroidChart |
| Crop | uCrop |
| Trim de vídeo | Android Video Trimmer |
| Assincronismo | Coroutines + Tasks (await) |
| Cache | SharedPreferences |
| Build | Gradle (KTS) |
| CI/CD | GitHub Actions |

---

## 🗂 Estrutura do Firestore

### Coleções raiz
- `usuarios` — dados do usuário + `chatsResumo` (denormalizado)
- `equipes` — dados da equipe
- `membros_equipe` — relação user↔equipe
- `convites_equipe` — convites para entrar em equipe
- `pedidos_entrada` — pedidos de entrada em equipe
- `trabalhos` — tarefas
- `convites_trabalho` — convites para trabalhos específicos
- `chats` — conversas PV
- `chats_equipe` — chat geral da equipe
- `grupos` — grupos dentro de uma equipe
- `notificacoes` — notificações in-app
- `favoritos` — mensagens favoritadas
- `bloqueios` — bloqueios entre usuários

### Subcoleções
- `trabalhos/{id}/comentarios`
- `trabalhos/{id}/anexos`
- `chats/{id}/mensagens`
- `chats_equipe/{id}/mensagens`
- `grupos/{id}/mensagens`

---

## 🚀 Como Rodar

### 1. Clone o repositório

git clone https://github.com/verstl0l/CTR_App-Comunidade_de_Trabalho_Remoto_Aplicativo.git
2. Configure o Firebase
Veja FIREBASE_SETUP.md.

Resumo:

Baixe google-services.json no Firebase Console

Coloque em app/google-services.json

Sincronize o Gradle

3. Configure o Cloudinary
Edita app/build.gradle.kts com suas credenciais Cloudinary (CLOUDINARY_CLOUD_NAME).

4. Configure o OneSignal
Edita app/build.gradle.kts com seu ONESIGNAL_APP_ID.

5. Rode
Execute em um device/emulador Android 7.0+.


```
📁 Estrutura do Projeto

app/src/main/java/com/example/plataformaremota/
├── Activities/
│   ├── MainActivity, LoginActivity, CadastroActivity, Splash
│   ├── ListaConversasActivity, ChatActivity, ChatGrupoActivity, ChatEquipeActivity
│   ├── MinhasEquipesActivity, GerenciarEquipeActivity, InfoEquipeActivity
│   ├── ChatEquipeHubActivity, GerenciarMembrosGrupoActivity
│   ├── CriarTrabalhoActivity, EntregarTrabalhoActivity, MeusTrabalhosActivity
│   ├── ComentariosTrabalhoActivity, AnexosTrabalhoActivity, ConvidarTrabalhoActivity
│   ├── NotificacoesActivity, MensagensFavoritasActivity
│   ├── PerfilUsuarioActivity, perfil
│   ├── ProdutividadeActivity, GruposActivity
│   └── AceitarConviteActivity, PedirEntradaActivity, PedidosPendentesActivity
│
├── Helpers/
│   ├── ChatResumoHelper, ChatPaginacaoHelper, TypingIndicatorHelper
│   ├── AnexoHelper, ComentarioHelper, NotificacaoHelper
│   ├── BadgeHelper, SessionHelper, SwipeToReplyHelper
│   ├── SeletorUsuarioHelper, CloudinaryConfig
│   └── NetworkUtils
│
└── Models/
    ├── Mensagem, ItemChat, MensagemAdapter
    └── ConversaItem
```

📸 Screenshots
<!-- Adicione prints aqui -->
Em breve

🌐 Versão Web
Acesse: CTR_Web-Comunidade_de_Trabalho_Remoto_Web

📋 Status
✅ Autenticação, cadastro e perfil

✅ Equipes (criar, editar, excluir, convidar, gerenciar)

✅ Trabalhos (CRUD, comentários, anexos, produtividade)

✅ Chat completo (PV, grupo, equipe) com tempo real

✅ Notificações in-app e push

✅ Edição de mídia

⏳ Testes finais e refinamento de UI

📄 Licença
Projeto acadêmico — sem licença de uso comercial definida.

Desenvolvido como parte de uma atividade acadêmica para a prática de desenvolvimento mobile e integração com Firebase.
