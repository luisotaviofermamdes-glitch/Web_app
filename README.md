# web_app V3 — Desktop Windows 10 no navegador

Desktop web inspirado no **Windows 10**, que funciona no celular (iOS e Android) e no computador.

## O que tem na versão web (V3)

- Interface completa de desktop Windows 10
- Menu Iniciar com pesquisa
- Barra de tarefas com relógio
- Aplicativos:
  - 🖥️ Explorador de arquivos
  - 🌐 Navegador
  - 📝 Bloco de Notas
  - 🔢 Calculadora
  - 🎨 Paint (desenho com touch)
  - ⌨️ Terminal
  - 💿 Gerenciador de ISO do Windows
  - ⚙️ Configurações (tema claro/escuro + papéis de parede)
- Suporte a **PWA** (pode instalar no iPhone e Android)
- Funciona offline depois da primeira visita
- Dados salvos localmente no navegador

## Como usar

1. Abra o arquivo `index.html` no navegador
2. Ou publique no GitHub Pages / Netlify / Vercel
3. No celular: abra no Safari (iOS) ou Chrome (Android) → "Adicionar à Tela de Início"

## Versão Android (APK)

A pasta `app/` contém o projeto Android que tenta rodar Windows real via QEMU.
A versão web funciona de forma independente e também pode ser embutida no APK.

## Estrutura

```
Web_app/
├── index.html          ← Versão web completa (V3)
├── manifest.json       ← PWA
├── sw.js               ← Service Worker (offline)
├── app/                ← Projeto Android
└── vm/                 ← Documentação do motor QEMU
```

## Licença

O código do web_app é livre para uso pessoal.
Windows é marca registrada da Microsoft. O usuário deve fornecer sua própria ISO e licença.
