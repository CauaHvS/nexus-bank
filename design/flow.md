# Nexus Bank — Fluxo de Navegacao e Jornadas do Usuário

## Mapa de telas

```
design/
├── design-system.css         Tokens e utilitarios globais
├── design-system.html        Showcase de componentes
├── flow.md                   Este arquivo
└── screens/
    ├── login.html            Autenticacao
    ├── cadastro.html         Registro de novo usuario
    ├── dashboard.html        Home (saldo, grafico, ultimas transacoes)
    ├── carteira.html         Dados da conta + chaves Pix
    ├── contas.html           Lista de contas + modal abertura
    ├── transferencia.html    Fluxo PIX/TED em 3 passos
    ├── extrato.html          Historico paginado com filtros
    ├── notificacoes.html     Central de notificacoes
    └── perfil.html           Dados pessoais, seguranca, preferencias
```

## Fluxo principal

```
[login.html]
  |
  |-- credenciais validas ---------> [dashboard.html]
  |-- falha de autenticacao -------> [login.html com alerta de erro]
  |-- "Criar conta" ---------------> [cadastro.html]
                                          |
                                          |-- sucesso ------> [dashboard.html]
                                          |-- erro de form --> [cadastro.html com erros]
```

## Navegacao interna (sidebar / bottom nav)

```
[dashboard.html]
  |-- "Carteira"         --> [carteira.html]
  |-- "Transferencias"   --> [transferencia.html]
  |-- "Extrato"          --> [extrato.html]
  |-- "Contas"           --> [contas.html]
  |-- "Notificacoes"     --> [notificacoes.html]
  |-- "Perfil"           --> [perfil.html]
```

## Jornada critica: Transferencia / Pix

```
[dashboard.html]
  -- botao "Pix" ou "Transferir" -->

[transferencia.html] Passo 1 — Destinatario
  |-- chave Pix / dados TED informados
  |-- destinatario encontrado -----> Passo 2
  |-- destinatario NAO encontrado -> exibe erro inline (campo + alert) | retry

[transferencia.html] Passo 2 — Valor
  |-- valor informado
  |-- saldo suficiente ------------> Passo 3
  |-- saldo insuficiente ----------> exibe erro inline (campo + alert)

[transferencia.html] Passo 3 — Revisao
  |-- dados exibidos em resumo
  |-- "Confirmar transferencia" ---> [processando]
      |-- sucesso -----------------> [tela de comprovante]
          |-- "Voltar ao inicio" --> [dashboard.html]
          |-- "Baixar comprovante"-> download PDF (fora de escopo no mockup)
      |-- 409 duplicada -----------> [exibe alerta de conflito]
          |-- "Nova transferencia"-> Passo 1
          |-- "Ver extrato" -------> [extrato.html]
  |-- "Cancelar" -----------------> [dashboard.html]
```

## Jornada de abertura de conta

```
[contas.html]
  -- botao "Abrir nova conta" -->

[Modal: Tipo de conta]
  |-- escolhe Corrente ou Poupanca
  |-- "Confirmar abertura" --------> conta criada
      |-- modal fecha
      |-- lista atualizada com nova conta
  |-- "Cancelar" -----------------> fecha modal
```

## Jornada de saida (logout)

```
[perfil.html]
  -- botao "Sair da conta" -->

[Modal de confirmacao]
  |-- "Sair da conta" (destrutivo) --> [login.html]
  |-- "Cancelar" ----------------> fecha modal, permanece em [perfil.html]
```

## Jornada de notificacoes

```
[qualquer tela interna]
  -- icone de sino com badge (3) -->

[notificacoes.html]
  |-- lista com nao lidas destacadas (fundo azul claro + ponto azul)
  |-- "Marcar todas como lidas" --> remove destaques + badge some
  |-- clicar em notificacao ------> marca como lida individualmente
```

## Estados por tela

| Tela             | Vazio | Loading (skeleton) | Erro | Sucesso |
|------------------|-------|--------------------|------|---------|
| login            | --    | botao loading      | alerta + campos | redirect |
| cadastro         | --    | botao loading      | campos invalidos | redirect |
| dashboard        | sem transacoes | skeleton cards + lista | banner de erro | estado normal |
| carteira         | sem chaves Pix | -- | -- | estado normal |
| contas           | sem contas | skeleton lista | -- | lista de contas |
| transferencia    | -- | botao loading | destinatario nao encontrado / saldo insuficiente / 409 | tela de comprovante |
| extrato          | sem movimentacoes | skeleton lista | -- | lista paginada |
| notificacoes     | sem notificacoes | skeleton lista | -- | lista com badges |
| perfil           | -- | -- | -- | dados do usuario |

## Navegacao entre arquivos HTML

Todos os links internos usam caminhos relativos dentro de `design/screens/`:
- `login.html` linka para `cadastro.html`
- `cadastro.html` linka para `login.html`
- Todas as telas internas linkam umas para as outras via sidebar/bottom nav
- `perfil.html` modal de saida linka para `login.html`

## Componentes reutilizados

- **Sidebar** (desktop): dashboard, carteira, contas, transferencia, extrato, notificacoes, perfil
- **Bottom nav** (mobile): idem acima com 5 itens principais
- **Topbar**: titulo da pagina + acoes contextuais
- **Dark mode toggle**: botao fixo no canto superior direito de cada tela

## Design tokens

Todos importam `../design-system.css` como base de tokens.
Cada tela pode adicionar estilos locais em `<style>` proprio.
O dark mode e ativado adicionando `class="dark"` no `<html>` via JS inline.
