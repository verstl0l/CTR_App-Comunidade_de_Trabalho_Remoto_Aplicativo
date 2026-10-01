# CTR — Documentacao da API

Documento tecnico descrevendo a estrutura do Firestore, os helpers do projeto e os fluxos principais.

Ultima atualizacao: Outubro/2026

---

## Indice

1. [Estrutura do Firestore](#estrutura-do-firestore)
2. [Autenticacao](#autenticacao)
3. [Helpers](#helpers)
4. [Fluxos principais](#fluxos-principais)
5. [Regras de seguranca](#regras-de-seguranca)

---

## Estrutura do Firestore

O projeto usa 14 colecoes. Cada uma tem uma responsabilidade clara.

### usuarios

Dados do usuario (perfil).

* **Documento ID**: email do usuario (lowercase) -> `usuarios/{email}`
* **Campos**:
    * `email`: string
    * `nome`: string
    * `profissao`: string
    * `fotoUrl`: string (opcional)
    * `links`: array<{ tipo: string, url: string }>
    * `chatsIds`: array<string>
    * `gruposIds`: array<string>
    * `fcmToken`: string (OneSignal)
    * `criadoEm`: long (timestamp)

**Queries comuns**:
* `get(usuarios/{email})` — perfil
* `update(usuarios/{email}, {fotoUrl})` — atualiza foto

---

### equipes

Equipes de trabalho.

* **Documento ID**: auto-gerado -> `equipes/{equipeId}`
* **Campos**:
    * `nome`: string
    * `descricao`: string
    * `criadorEmail`: string
    * `privada`: boolean
    * `criadaEm`: long

**Queries comuns**:
* `whereEqualTo("criadorEmail", email)` — equipes que sou dono
* `whereEqualTo("privada", false)` — equipes publicas

---

### membros_equipe

Membros de cada equipe.

* **Documento ID**: `{email}_{equipeId}` (deterministico) -> `membros_equipe/{email}_{equipeId}`
* **Campos**:
    * `equipeId`: string
    * `email`: string
    * `nome`: string
    * `funcao`: string ("membro" | "administrador")
    * `entrouEm`: long

**Por que ID deterministico?**
* Permite verificacao `exists()` nas rules sem query.
* Evita duplicatas.

**Queries comuns**:
* `whereEqualTo("equipeId", equipeId)` — membros de uma equipe
* `whereEqualTo("email", email)` — equipes que pertenco

---

### convites_equipe

Convites enviados pelo dono/admin.

* **Documento ID**: auto-gerado -> `convites_equipe/{conviteId}`
* **Campos**:
    * `equipeId`: string
    * `nomeEquipe`: string
    * `descricaoEquipe`: string
    * `emailConvidado`: string
    * `emailRemetente`: string
    * `nomeRemetente`: string
    * `status`: string ("pendente" | "aceito" | "recusado")
    * `criadoEm`: long

**Queries comuns**:
* `whereEqualTo("emailConvidado", email).whereEqualTo("status", "pendente")` — meus convites pendentes

---

### pedidos_entrada

Pedidos do usuario que quer entrar numa equipe.

* **Documento ID**: auto-gerado -> `pedidos_entrada/{pedidoId}`
* **Campos**:
    * `equipeId`: string
    * `nomeEquipe`: string
    * `emailSolicitante`: string
    * `nomeSolicitante`: string
    * `profissaoSolicitante`: string
    * `motivos`: string
    * `especialidades`: string
    * `status`: string ("pendente" | "aceito" | "recusado")
    * `criadoEm`: long

**Queries comuns**:
* `whereEqualTo("equipeId", X).whereEqualTo("status", "pendente")` — pedidos pendentes da equipe

---

### trabalhos

Tarefas da equipe, com subcoleções de comentários e anexos.

* **Documento ID**: auto-gerado -> `trabalhos/{trabalhoId}`
* **Campos**:
    * `titulo`: string
    * `descricao`: string
    * `categoria`: string
    * `prazo`: string
    * `equipeId`: string
    * `criadorEmail`: string
    * `responsavelEmail`: string
    * `status`: string ("pendente" | "em_progresso" | "concluido")
    * `criadoEm`: long

* **Subcoleção**: `trabalhos/{trabalhoId}/comentarios/{comentarioId}`
    * `autorEmail`, `autorNome`, `autorFotoUrl`, `texto`, `criadoEm`, `editadoEm` (opcional)
* **Subcoleção**: `trabalhos/{trabalhoId}/anexos/{anexoId}`
    * `nome`, `url` (Cloudinary), `tipo` ("imagem" | "video" | "pdf" | ...), `mimeType`, `tamanho`, `enviadoPor`, `nomeEnviadoPor`, `enviadoEm`

**Queries comuns**:
* `whereEqualTo("equipeId", X)` — trabalhos da equipe
* `whereEqualTo("status", "concluido")` — trabalhos concluidos

---

### chats

Chats privados (PV).

* **Documento ID**: auto-gerado -> `chats/{chatId}`
* **Campos**:
    * `participantes`: array<string> (2 emails)
    * `ultimaMensagem`: string
    * `ultimaMensagemPreview`: map
    * `atualizadoEm`: long
    * `tipo`: string ("individual")
    * `digitando`: map<string, long>
    * `naoLidas`: map<string, int>

* **Subcoleção**: `chats/{chatId}/mensagens/{mensagemId}`
    * `remetente`, `texto`, `tipo` ("texto" | "foto" | "video" | "arquivo"), `fotoUrl`, `videoUrl`, `arquivoUrl`, `nomeArquivo`, `tamanhoArquivo`, `mimeType`, `timestamp`, `lida`, `apagada`, `respostaPara` (opcional)

**Queries comuns**:
* `whereArrayContains("participantes", email)` — meus chats

---

### chats_equipe

Chat coletivo da equipe.

* **Documento ID**: ID da equipe -> `chats_equipe/{equipeId}`
* **Campos**:
    * `ultimaMensagem`: string
    * `ultimaMensagemPreview`: map
    * `atualizadoEm`: long
    * `digitando`: map<string, long>
    * `naoLidas`: map<string, int>

* **Subcoleção**: `chats_equipe/{equipeId}/mensagens/{mensagemId}`
    * `remetente`, `nomeRemetente`, `texto`, `tipo`, `timestamp`, `lida`, `apagada`, `respostaPara` (opcional)

**Observacao**: O chat de equipe so existe se alguem ja enviou mensagem. Por isso o codigo usa `set(..., merge())` em vez de `update()`.

---

### grupos

Grupos dentro de uma equipe (criados pelo dono).

* **Documento ID**: auto-gerado -> `grupos/{grupoId}`
* **Campos**:
    * `equipeId`: string
    * `nomeGrupo`: string
    * `criadorEmail`: string
    * `membros`: array<string>
    * `criadoEm`: long
    * `ultimaMensagem`: string
    * `atualizadoEm`: long

* **Subcoleção**: `grupos/{grupoId}/mensagens/{mensagemId}`
    * `remetente`, `nomeRemetente`, `texto`, `tipo`, `timestamp`, `lida`, `apagada`

---

### notificacoes

Notificacoes in-app.

* **Documento ID**: auto-gerado -> `notificacoes/{notificacaoId}`
* **Campos**:
    * `destinatario`: string
    * `tipo`: string ("convite_equipe" | "novo_trabalho" | "pedido_entrada" | ...)
    * `titulo`: string
    * `mensagem`: string
    * `referenciaId`: string
    * `referenciaTipo`: string
    * `remetente`: string
    * `nomeRemetente`: string
    * `lida`: boolean
    * `criadoEm`: long

**Queries comuns**:
* `whereEqualTo("destinatario", email).orderBy("criadoEm", DESC)` — minhas notificacoes

---

### favoritos

Mensagens favoritadas pelo usuario.

* **Documento ID**: auto-gerado -> `favoritos/{favoritoId}`
* **Campos**:
    * `usuarioEmail`: string
    * `mensagemId`: string
    * `chatId`: string
    * `tipoChat`: string ("pv" | "equipe" | "grupo")
    * `texto`: string
    * `remetente`: string
    * `nomeRemetente`: string
    * `tipoMidia`: string
    * `criadoEm`: long

---

### bloqueios

Bloqueios entre usuarios.

* **Documento ID**: auto-gerado -> `bloqueios/{bloqueioId}`
* **Campos**:
    * `bloqueadorEmail`: string
    * `bloqueadoEmail`: string
    * `criadoEm`: long

---

## Autenticacao

Firebase Authentication com email/senha.

* **Session persistente**:
    * `SharedPreferences` com `CTR_PREFS`
    * Flags: `logado`, `emailUsuario`, `nomeUsuario`, `profissaoUsuario`
* **Verificacao**: `MainActivity` verifica login no `onCreate` e redireciona pra `LoginActivity` se nao logado.

---

## Helpers

### AnexoHelper
Gerencia upload de anexos.
* `uploadAnexo(context, trabalhoId, uri, email, nome, onProgress, onSucesso, onErro)`
* `removerAnexo(trabalhoId, anexoId): Boolean`
* `formatarTamanho(bytes): String`

### NotificacaoHelper
Cria notificacoes in-app.
* `criar(destinatario, tipo, titulo, mensagem, ...)`
* `notificarConviteEquipe(...)`
* `notificarPedidoEntrada(...)`
* `notificarPedidoAceito(...)`
* `notificarNovoTrabalhoParaMembros(...)`
* `marcarComoLida(id)`, `marcarTodasComoLidas(email)`, `contarNaoLidas(email): Int`

### ComentarioHelper
Gerencia comentarios de tarefas.
* `criar(...)`, `editar(...)`, `remover(...)`, `contar(...)`, `notificarParticipantes(...)`

### ChatPaginacaoHelper
Paginacao + listener em tempo real de mensagens (ultimas 50 mensagens, suporte a rolagem e atualizacoes).

### TypingIndicatorHelper
Indicador "esta digitando..." com debounce de 1s e timeout de 3s.

### BadgeHelper
Gerencia badges do `BottomNavigationView`.

### NetworkUtils
Verifica conexao (`isOnline(context): Boolean`).

---

## Fluxos principais

1. **Criar equipe**: Preenche dados -> Cria documento em `equipes` -> Cria registro em `membros_equipe` com função `"administrador"`.
2. **Convidar para equipe**: Valida se usuário existe/já foi convidado -> Cria em `convites_equipe` -> Dispara `NotificacaoHelper`.
3. **Pedir entrada**: Usuário solicita -> Cria em `pedidos_entrada` -> Notifica o dono.
4. **Aceitar pedido**: Dono aceita -> Atualiza `pedidos_entrada` para `"aceito"` -> Cria em `membros_equipe` como `"membro"`.
5. **Enviar mensagem**: Executa Batch Firestore (`set` mensagem + `set` chat com incremento de nao lidas) -> Listener atualiza UI.
6. **Apagar mensagem**: Atualiza campo `apagada = true` e substitui o texto -> UI renderiza "Mensagem apagada".
7. **Excluir equipe**: Deleta em cascata (subcoleções, trabalhos, membros, convites, pedidos, chats) e remove o documento principal da equipe.

---

## Regras de seguranca

As Firestore Rules estao em `firestore.rules`.
* **Autenticacao obrigatoria**: `request.auth != null`
* **Ownership**: usuario so edita o proprio doc.
* **Pertencimento**: membros de equipe podem ler/escrever nas colecoes da equipe.
* **Fallback**: tudo que nao esta explicitamente permitido e bloqueado.

---

## Recursos

* **Testes unitarios**: `app/src/test/java/com/example/plataformaremota/`
* **Workflow CI**: `.github/workflows/android.yml`
* **Relatorio de progresso**: `PROGRESSO.md`

*Documento tecnico vivo. Atualizado conforme o projeto evolui.*