# Configuração do Firebase

Este projeto usa Firebase (Authentication + Firestore). Para rodar localmente:

## 1. Obter o `google-services.json`

1. Acesse [console.firebase.google.com](https://console.firebase.google.com)
2. Selecione o projeto **CTR-App**
3. Vá em **Configurações do projeto** → aba **Geral**
4. Role até **Seus apps** → clique no app Android
5. Clique em **Baixar google-services.json**

## 2. Colocar o arquivo no lugar certo

Coloque o arquivo em:

app/google-services.json


## 3. Sincronizar o Gradle

No Android Studio: **File > Sync Project with Gradle Files**

## 4. Rodar o app

Pronto! O Firebase está configurado.

---

**Observação:** o arquivo `google-services.json.example` é apenas um modelo. Não use ele diretamente.
