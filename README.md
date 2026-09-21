# CTR_App-Comunidade_de_Trabalho_Remoto_Aplicativo

Aplicativo mobile Android da **Plataforma Colaborativa de Trabalho Remoto (CTR)**, desenvolvido para apoiar a organização, distribuição e acompanhamento de tarefas em equipes que trabalham remotamente.

---

## Descrição do Projeto

A Plataforma Colaborativa de Trabalho Remoto (CTR) é um aplicativo mobile focado em apoiar a organização, distribuição e acompanhamento de tarefas para equipes que operam em regime de trabalho remoto. O projeto centraliza informações de fluxo de trabalho, facilitando a gestão de equipes, convites, tarefas e o monitoramento de atividades.

O sistema foi desenvolvido para a plataforma Android, utilizando Kotlin no Android Studio, com autenticação e banco de dados em nuvem através do Firebase (Authentication + Cloud Firestore).

---

## Objetivo

Oferecer uma solução digital que reduza a dispersão de informações, organizando equipes, tarefas, prazos e prioridades, além de proporcionar a aplicação prática de conhecimentos em desenvolvimento mobile e integração com serviços em nuvem.

---

## Funcionalidades Implementadas

- RF01 — Cadastro e autenticação de usuários (Firebase Authentication)
- RF02 — Criação e gerenciamento de equipes (nome, descrição, equipe privada)
- RF03 — Criação, consulta e exclusão de trabalhos (tarefas)
- RF04 — Convites por email para membros da equipe
- RF05 — Acompanhamento do status das tarefas
- RF06 — Registro de prazos
- RF07 — Visualização das atividades de cada equipe
- RF08 — Atualização dos dados da equipe
- RF09 — Consulta do histórico de convites e membros
- RF10 — Persistência em nuvem via Cloud Firestore

### Funcionalidades extras

- Pedidos de entrada em equipes (com motivos e especialidades)
- Gerenciamento de membros (promover a administrador / remover)
- Tela de perfil com dados do usuário e equipe atual
- Notificações de convites pendentes
- Exclusão de equipe em cascata (trabalhos, membros, convites, pedidos)
- Equipes privadas (não recebem pedidos de entrada)

---

## Tecnologias Utilizadas

- Linguagem: Kotlin
- IDE: Android Studio
- Autenticação: Firebase Authentication (email/senha)
- Banco de dados: Cloud Firestore (NoSQL, em nuvem)
- Interface: Material Design Components
- Assincronismo: Coroutines + Tasks (await())
- Cache local: SharedPreferences
- Arquitetura: MVC simplificado (Activity por tela)

---

## Estrutura do Banco (Firestore)

O projeto utiliza as seguintes coleções no Cloud Firestore:

- usuarios — dados do usuário (email, nome, profissão)
- equipes — dados da equipe (nome, descrição, criador, privada)
- membros_equipe — membros de cada equipe (email, nome, função)
- convites_equipe — convites enviados (email convidado, remetente, status)
- trabalhos — tarefas publicadas (título, descrição, categoria, prazo)
- pedidos_entrada — pedidos de entrada em equipes (motivos, especialidades)

---

## Configuração do Firebase

Este projeto usa Firebase. Para rodar localmente, siga as instruções em [FIREBASE_SETUP.md](FIREBASE_SETUP.md).

Resumo:
1. Baixe o `google-services.json` no Firebase Console (projeto CTR-App).
2. Coloque o arquivo em `app/google-services.json`.
3. Sincronize o Gradle no Android Studio.
4. Rode o app.

O arquivo `app/google-services.json.example` é apenas um modelo.

---

## Como Rodar o Projeto

1. Clone o repositório:
   git clone https://github.com/verstl0l/CTR_App-Comunidade_de_Trabalho_Remoto_Aplicativo.git

2. Abra no Android Studio.

3. Configure o Firebase (veja [FIREBASE_SETUP.md](FIREBASE_SETUP.md)).

4. Sincronize o Gradle.

5. Execute o app em um dispositivo Android (Android 7.0+) ou emulador.

---

## Versão Web

Procurando a versão para Web? Acesse o repositório [CTR_Web-Comunidade_de_Trabalho_Remoto_Web](https://github.com/verstl0l/CTR_Web-Comunidade_de_Trabalho_Remoto_Web.git).

---

## Status do Projeto

- Em desenvolvimento (versão parcial funcional — 3º bimestre)
- Próximas etapas: pedidos de entrada, melhorias de UI, testes finais

---

*Este projeto foi desenvolvido como parte de uma atividade acadêmica para a prática de desenvolvimento de software.*
