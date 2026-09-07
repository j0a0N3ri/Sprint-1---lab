/*
 * MODEL - o "M" do MVC.
 *
 * Duas responsabilidades, e nenhuma delas envolve o DOM:
 *   1. guardar o estado da sessao (token, usuario, agencia escolhida);
 *   2. falar com a API das agencias.
 *
 * Este arquivo nao sabe que existe uma tela. Se o ICEIBank ganhasse outra interface,
 * ele seria reaproveitado inteiro.
 */
window.Model = (function () {
  'use strict';

  // Mesmo calculo do backend (AgenciaConfig): a particao e por resto da divisao.
  var NUMERO_AGENCIAS = 3;
  var PORTA_BASE = 4000;

  var CHAVE_TOKEN = 'iceibank.token';
  var CHAVE_USUARIO = 'iceibank.usuario';
  var CHAVE_AGENCIA = 'iceibank.agencia';

  var estado = {
    token: null,
    usuario: null,
    idAgencia: 0
  };

  /* ---------------------------------------------------------------- sessao */

  /*
   * O token fica no localStorage para sobreviver ao F5 - sem isso, recarregar a pagina
   * exigiria novo login. Vale registrar a limitacao: localStorage e legivel por qualquer
   * script da mesma origem, entao um XSS rouba o token. A alternativa mais segura seria
   * um cookie HttpOnly, que o JavaScript nao enxerga - mas ai o backend precisaria emitir
   * e ler cookie, e a API deixaria de ser puramente stateless via cabecalho.
   */
  function carregarSessaoSalva() {
    estado.token = localStorage.getItem(CHAVE_TOKEN);
    estado.usuario = localStorage.getItem(CHAVE_USUARIO);
    var agencia = localStorage.getItem(CHAVE_AGENCIA);
    estado.idAgencia = agencia === null ? 0 : parseInt(agencia, 10);
    return estado.token !== null;
  }

  function salvarSessao(token, usuario, idAgencia) {
    estado.token = token;
    estado.usuario = usuario;
    estado.idAgencia = idAgencia;
    localStorage.setItem(CHAVE_TOKEN, token);
    localStorage.setItem(CHAVE_USUARIO, usuario);
    localStorage.setItem(CHAVE_AGENCIA, String(idAgencia));
  }

  function encerrarSessao() {
    estado.token = null;
    estado.usuario = null;
    localStorage.removeItem(CHAVE_TOKEN);
    localStorage.removeItem(CHAVE_USUARIO);
  }

  function estaAutenticado() {
    return estado.token !== null;
  }

  function getEstado() {
    return estado;
  }

  function urlDaAgencia(idAgencia) {
    return 'http://localhost:' + (PORTA_BASE + idAgencia);
  }

  function agenciaResponsavel(idConta) {
    return idConta % NUMERO_AGENCIAS;
  }

  /* ------------------------------------------------------------------- api */

  /*
   * Toda chamada passa por aqui, e e aqui que o token e reanexado a cada requisicao -
   * a resposta da pergunta "como o frontend lembra de mandar o token": ele nao lembra
   * em cada tela, ele tem um unico ponto de saida que sempre anexa.
   */
  function requisitar(metodo, caminho, corpo) {
    var opcoes = {
      method: metodo,
      headers: { 'Content-Type': 'application/json' }
    };
    if (estado.token) {
      opcoes.headers['Authorization'] = 'Bearer ' + estado.token;
    }
    if (corpo !== undefined) {
      opcoes.body = JSON.stringify(corpo);
    }

    return fetch(urlDaAgencia(estado.idAgencia) + caminho, opcoes)
      .then(function (resposta) {
        return resposta.text().then(function (texto) {
          var dados = null;
          try { dados = texto ? JSON.parse(texto) : null; } catch (e) { dados = { erro: texto }; }

          if (resposta.ok) {
            return dados;
          }

          // Erro da API: vira uma excecao com o status junto, para o controller poder
          // tratar o 401 (sessao expirada) de forma diferente dos demais.
          var erro = new Error((dados && dados.erro) || ('Erro HTTP ' + resposta.status));
          erro.status = resposta.status;
          erro.corpo = dados;
          throw erro;
        });
      })
      .catch(function (e) {
        // fetch so rejeita quando a requisicao nem chegou a ser respondida: agencia fora
        // do ar, porta errada, CORS bloqueado. Sem esta traducao, a pessoa veria
        // "Failed to fetch", que nao diz nada a quem esta usando o sistema.
        if (e.status === undefined) {
          var falha = new Error(
            'Nao foi possivel falar com a Agencia ' + estado.idAgencia +
            ' (' + urlDaAgencia(estado.idAgencia) + '). Ela esta no ar?');
          falha.status = 0;
          throw falha;
        }
        throw e;
      });
  }

  function login(idAgencia, usuario, senha) {
    estado.idAgencia = idAgencia;
    estado.token = null; // o login e a unica rota que nao leva token
    return requisitar('POST', '/auth/login', { login: usuario, senha: senha })
      .then(function (dados) {
        salvarSessao(dados.token, dados.login, idAgencia);
        return dados;
      });
  }

  return {
    carregarSessaoSalva: carregarSessaoSalva,
    encerrarSessao: encerrarSessao,
    estaAutenticado: estaAutenticado,
    getEstado: getEstado,
    urlDaAgencia: urlDaAgencia,
    agenciaResponsavel: agenciaResponsavel,
    NUMERO_AGENCIAS: NUMERO_AGENCIAS,

    login: login,
    listarContas: function () { return requisitar('GET', '/contas'); },
    consultarLimites: function () { return requisitar('GET', '/limites'); },
    consultarConta: function (id) { return requisitar('GET', '/contas/' + id); },
    criarConta: function (id, nome, saldo) {
      return requisitar('POST', '/contas', { id: id, nomeAluno: nome, saldoInicial: saldo });
    },
    depositar: function (id, valor) {
      return requisitar('POST', '/contas/' + id + '/depositar', { valor: valor });
    },
    sacar: function (id, valor) {
      return requisitar('POST', '/contas/' + id + '/sacar', { valor: valor });
    },
    transferir: function (origem, destino, valor) {
      return requisitar('POST', '/transferencias', {
        idOrigem: origem, idDestino: destino, valor: valor
      });
    }
  };
})();
