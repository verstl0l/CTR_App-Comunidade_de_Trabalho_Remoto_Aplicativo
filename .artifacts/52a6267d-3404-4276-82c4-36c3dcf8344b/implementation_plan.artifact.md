# Implementação de Fluxo de Equipe e Gerenciamento de Trabalhos

Este plano descreve a criação de uma funcionalidade completa para criar equipes e gerenciar trabalhos (CRUD), integrando com o banco de dados Room existente.

## User Review Required

> [!IMPORTANT]
> A estrutura do banco de dados será alterada para incluir a entidade `Equipe`. Como o `AppDatabase` usa `fallbackToDestructiveMigration()`, os dados locais atuais serão apagados na próxima execução para aplicar o novo esquema.

## Proposed Changes

### Banco de Dados (Room)

#### [NEW] [Equipe.kt](file:///C:/Users/Desenvolvimento.MININT-KKPKIJ9/AndroidStudioProjects/CTR_App-Comunidade_de_Trabalho_Remoto_Aplicativo/app/src/main/java/com/example/plataformaremota/data/entity/Equipe.kt)
Criação da entidade `Equipe` com campos: `id`, `nome`, `descricao` e `criadorEmail`.

#### [NEW] [EquipeDao.kt](file:///C:/Users/Desenvolvimento.MININT-KKPKIJ9/AndroidStudioProjects/CTR_App-Comunidade_de_Trabalho_Remoto_Aplicativo/app/src/main/java/com/example/plataformaremota/data/dao/EquipeDao.kt)
Interface DAO para operações de Inserção, Atualização e Busca de equipes.

#### [MODIFY] [Trabalho.kt](file:///C:/Users/Desenvolvimento.MININT-KKPKIJ9/AndroidStudioProjects/CTR_App-Comunidade_de_Trabalho_Remoto_Aplicativo/app/src/main/java/com/example/plataformaremota/data/entity/Trabalho.kt)
Adição do campo `equipeId` para vincular trabalhos a uma equipe específica.

#### [MODIFY] [TrabalhoDao.kt](file:///C:/Users/Desenvolvimento.MININT-KKPKIJ9/AndroidStudioProjects/CTR_App-Comunidade_de_Trabalho_Remoto_Aplicativo/app/src/main/java/com/example/plataformaremota/data/dao/TrabalhoDao.kt)
Adição de métodos para listar trabalhos por equipe e excluir trabalhos.

#### [MODIFY] [AppDatabase.kt](file:///C:/Users/Desenvolvimento.MININT-KKPKIJ9/AndroidStudioProjects/CTR_App-Comunidade_de_Trabalho_Remoto_Aplicativo/app/src/main/java/com/example/plataformaremota/data/database/AppDatabase.kt)
Inclusão da nova entidade `Equipe` e do DAO correspondente.

---

### UI - Telas de Fluxo

#### [NEW] [activity_criar_equipe.xml](file:///C:/Users/Desenvolvimento.MININT-KKPKIJ9/AndroidStudioProjects/CTR_App-Comunidade_de_Trabalho_Remoto_Aplicativo/app/src/main/res/layout/activity_criar_equipe.xml)
Layout para criação de equipe com campos de texto (Nome e Descrição) seguindo o estilo visual escuro do app.

#### [NEW] [CriarEquipeActivity.kt](file:///C:/Users/Desenvolvimento.MININT-KKPKIJ9/AndroidStudioProjects/CTR_App-Comunidade_de_Trabalho_Remoto_Aplicativo/app/src/main/java/com/example/plataformaremota/CriarEquipeActivity.kt)
Lógica para salvar a nova equipe e atualizar o estado do usuário no `SharedPreferences`.

#### [NEW] [activity_gerenciar_equipe.xml](file:///C:/Users/Desenvolvimento.MININT-KKPKIJ9/AndroidStudioProjects/CTR_App-Comunidade_de_Trabalho_Remoto_Aplicativo/app/src/main/res/layout/activity_gerenciar_equipe.xml)
Layout baseado na imagem enviada:
- Cabeçalho com Nome/Descrição da equipe (editáveis).
- Formulário para "Publicar Trabalho" (Título, Descrição, Categoria, Prazo).
- Lista de trabalhos existentes com botões de Deletar/Editar.

#### [NEW] [GerenciarEquipeActivity.kt](file:///C:/Users/Desenvolvimento.MININT-KKPKIJ9/AndroidStudioProjects/CTR_App-Comunidade_de_Trabalho_Remoto_Aplicativo/app/src/main/java/com/example/plataformaremota/GerenciarEquipeActivity.kt)
Lógica completa de CRUD para trabalhos e edição dos dados da equipe.

---

### Integração e Navegação

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Desenvolvimento.MININT-KKPKIJ9/AndroidStudioProjects/CTR_App-Comunidade_de_Trabalho_Remoto_Aplicativo/app/src/main/java/com/example/plataformaremota/MainActivity.kt)
Ajuste no botão "Criar Equipe" para abrir a nova tela e verificação se o usuário já possui equipe para redirecionar para "Gerenciar".

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/Desenvolvimento.MININT-KKPKIJ9/AndroidStudioProjects/CTR_App-Comunidade_de_Trabalho_Remoto_Aplicativo/app/src/main/AndroidManifest.xml)
Registro das novas Activities.

## Verification Plan

### Manual Verification
1. Fazer login no app.
2. Na Home, clicar em "Criar Equipe".
3. Preencher Nome e Descrição e salvar.
4. Ser redirecionado para a tela de Gerenciamento.
5. Adicionar um trabalho preenchendo os 4 campos.
6. Verificar se o trabalho aparece na lista abaixo.
7. Excluir um trabalho e verificar se ele some.
8. Editar o nome da equipe e salvar.
