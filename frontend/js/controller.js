/*
 * CONTROLLER - o "C" do MVC.
 *
 * Liga o que a pessoa faz na tela (View) ao que o sistema sabe fazer (Model), e manda a
 * View redesenhar com o resultado. Nao monta HTML e nao chama fetch: quando precisa de
 * dado, pede ao Model; quando precisa mostrar, manda a View.
 */
(function () {
  'use strict';

  /*
   * Ponto unico de tratamento de erro. Toda operacao termina aqui quando falha, o que
   * garante que nenhum erro morra em silencio no console.
   *
   * O 401 recebe tratamento proprio: significa que o token venceu no meio do uso. Em vez
   * de mostrar "nao autorizado" e deixar a pessoa clicando em botoes que nunca mais vao
   * funcionar, a sessao e encerrada e a tela volta ao login, com o motivo explicado.
   */
  function tratarErro(erro) {
    if (erro.status === 401) {
      Model.encerrarSessao();
      View.mostrarTelaLogin();
      View.mostrarMensagem(
        'Sua sessao expirou (o token JWT tem validade limitada). Entre novamente.', 'aviso');
      return;
    }
    if (erro.status === 403) {
      View.mostrarMensagem(erro.message, 'erro');
      return;
    }
    if (erro.status === 502) {
      // A falha conhecida da Parte D: o debito saiu e o credito nao chegou.
      View.mostrarMensagem(erro.message, 'erro');
      atualizarContas();
      return;
    }
    View.mostrarMensagem(erro.message, 'erro');
  }

  function atualizarContas() {
    return Model.listarContas()
      .then(View.renderizarContas)
      .catch(tratarErro);
  }

  function carregarLimites() {
    return Model.consultarLimites()
      .then(View.mostrarLimites)
      .catch(tratarErro);
  }

  /* ----------------------------------------------------------------- login */

  function entrar() {
    var idAgencia = Number(View.elemento('loginAgencia').value);
    var usuario = View.lerTexto('loginUsuario');
    var senha = View.elemento('loginSenha').value;

    if (!usuario || !senha) {
      View.mostrarMensagem('Informe usuario e senha.', 'erro');
      return;
    }

    Model.login(idAgencia, usuario, senha)
      .then(function (dados) {
        View.limparMensagem();
        View.mostrarTelaApp(dados.login, idAgencia);
        View.mostrarMensagem(
          'Bem-vindo, ' + dados.login + '. Token valido por ' + dados.expiraEmSegundos + ' segundos.',
          'sucesso');
        carregarLimites();
        return atualizarContas();
      })
      .catch(tratarErro);
  }

  function sair() {
    Model.encerrarSessao();
    View.mostrarTelaLogin();
    View.mostrarMensagem('Sessao encerrada.', 'aviso');
  }

  /* ---------------------------------------------------------------- contas */

  function criarConta() {
    var id = View.lerNumero('novaContaId');
    var nome = View.lerTexto('novaContaNome');
    var saldo = View.lerNumero('novaContaSaldo');

    if (id === null || !nome) {
      View.mostrarMensagem('Informe o numero da conta e o nome do titular.', 'erro');
      return;
    }

    // Aviso antes de gastar uma requisicao: o mesmo calculo de particao do backend.
    var responsavel = Model.agenciaResponsavel(id);
    if (responsavel !== Model.getEstado().idAgencia) {
      View.mostrarMensagem(
        'A conta ' + id + ' pertence a Agencia ' + responsavel + ' (' + id + ' % ' +
        Model.NUMERO_AGENCIAS + ' = ' + responsavel + '). Entre por aquela agencia para abri-la.',
        'erro');
      return;
    }

    Model.criarConta(id, nome, saldo === null ? 0 : saldo)
      .then(function (conta) {
        View.mostrarMensagem(
          'Conta ' + conta.id + ' aberta para ' + conta.nomeAluno +
          ' com saldo de ' + View.moeda(conta.saldo) + '.', 'sucesso');
        View.limparCampos(['novaContaId', 'novaContaNome', 'novaContaSaldo']);
        return atualizarContas();
      })
      .catch(tratarErro);
  }

  function consultar() {
    var id = View.lerNumero('consultaId');
    if (id === null) {
      View.mostrarMensagem('Informe o numero da conta.', 'erro');
      return;
    }

    Model.consultarConta(id)
      .then(function (conta) {
        View.limparMensagem();
        View.mostrarResultadoConsulta(conta);
      })
      .catch(function (erro) {
        View.esconderResultadoConsulta();
        tratarErro(erro);
      });
  }

  function operar(tipo) {
    var id = View.lerNumero('opId');
    var valor = View.lerNumero('opValor');

    if (id === null || valor === null) {
      View.mostrarMensagem('Informe a conta e o valor.', 'erro');
      return;
    }

    var operacao = tipo === 'deposito' ? Model.depositar(id, valor) : Model.sacar(id, valor);

    operacao
      .then(function (conta) {
        View.mostrarMensagem(
          (tipo === 'deposito' ? 'Deposito' : 'Saque') + ' de ' + View.moeda(valor) +
          ' na conta ' + conta.id + '. Novo saldo: ' + View.moeda(conta.saldo) + '.', 'sucesso');
        View.limparCampos(['opValor']);
        return atualizarContas();
      })
      .catch(tratarErro);
  }

  /* --------------------------------------------------------- transferencia */

  function transferir() {
    var origem = View.lerNumero('transfOrigem');
    var destino = View.lerNumero('transfDestino');
    var valor = View.lerNumero('transfValor');

    if (origem === null || destino === null || valor === null) {
      View.mostrarMensagem('Informe origem, destino e valor.', 'erro');
      return;
    }

    Model.transferir(origem, destino, valor)
      .then(function (resposta) {
        View.limparMensagem();
        View.mostrarResultadoTransferencia(resposta);
        View.limparCampos(['transfValor']);
        return atualizarContas();
      })
      .catch(function (erro) {
        View.esconderResultadoTransferencia();
        tratarErro(erro);
      });
  }

  /* ------------------------------------------------------------- ligacoes */

  function ligarEventos() {
    View.elemento('btnEntrar').addEventListener('click', entrar);
    View.elemento('btnSair').addEventListener('click', sair);
    View.elemento('btnAtualizar').addEventListener('click', atualizarContas);
    View.elemento('btnCriarConta').addEventListener('click', criarConta);
    View.elemento('btnConsultar').addEventListener('click', consultar);
    View.elemento('btnDepositar').addEventListener('click', function () { operar('deposito'); });
    View.elemento('btnSacar').addEventListener('click', function () { operar('saque'); });
    View.elemento('btnTransferir').addEventListener('click', transferir);

    View.elemento('loginSenha').addEventListener('keydown', function (e) {
      if (e.key === 'Enter') { entrar(); }
    });
  }

  function iniciar() {
    ligarEventos();

    // Sessao salva no localStorage: recarregar a pagina nao obriga a logar de novo.
    // Se o token ja tiver vencido, a primeira chamada devolve 401 e o tratarErro
    // devolve a pessoa ao login com a explicacao.
    if (Model.carregarSessaoSalva() && Model.estaAutenticado()) {
      var estado = Model.getEstado();
      View.mostrarTelaApp(estado.usuario, estado.idAgencia);
      carregarLimites();
      atualizarContas();
    } else {
      View.mostrarTelaLogin();
    }
  }

  document.addEventListener('DOMContentLoaded', iniciar);
})();
