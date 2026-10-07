# Web_app — motor de VM real

Este diretório documenta os componentes nativos necessários para o Web_app executar uma instalação real do Windows.

## Objetivo

O APK não deve simular o Windows. O fluxo esperado é:

1. Usuário abre o site oficial da Microsoft pelo Web_app.
2. Usuário escolhe e baixa a ISO do Windows 10/11 que quiser.
3. Usuário seleciona a ISO no Web_app.
4. O aplicativo cria um disco virtual persistente.
5. O motor QEMU inicia a ISO/disco.
6. O Windows instala e permanece no disco virtual para os próximos usos.

## Componentes

- QEMU nativo para Android arm64-v8a.
- Firmware UEFI/OVMF apropriado para o guest.
- Display nativo (SDL/Surface ou backend VNC integrado), não iframe apontando para uma porta VNC.
- Input touch -> USB tablet/mouse e teclado Android.
- Rede user-mode/SLIRP.
- Disco virtual persistente.
- Pasta compartilhada Android <-> guest.
- Configuração de UEFI/TPM quando necessária para Windows 11.

## Limitação importante

Uma ISO x86_64 do Windows em um telefone ARM64 precisa de emulação de CPU e pode ser lenta. Quando o usuário escolher uma ISO ARM64 compatível, o caminho pode ser muito mais eficiente. O app deve respeitar a arquitetura da ISO escolhida e não fingir compatibilidade quando o guest não puder ser inicializado.

## Distribuição

O binário QEMU é produzido pelo workflow de CI e copiado para o APK. Não colocar um executável binário falso ou vazio no repositório apenas para aumentar o tamanho do APK.

## Licenças

QEMU e seus componentes possuem licenças próprias. Firmware e bibliotecas adicionais também devem manter seus avisos/licenças correspondentes. Windows e Opera GX não são redistribuídos pelo Web_app: o usuário fornece sua própria ISO/licença e pode instalar o Opera GX dentro do Windows.
