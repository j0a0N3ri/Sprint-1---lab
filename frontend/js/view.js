/*
 * VIEW - o "V" do MVC.
 *
 * So desenha e le a tela. Nao faz requisicao, nao decide regra de negocio, nao conhece
 * token nem endpoint. Recebe dados prontos e devolve o que a pessoa digitou.
 */
window.View = (function () {
  'use strict';

  function elemento(id) {
    return document.getElementById(id);
  }

  function moeda(valor) {
    return Number(valor).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  /* -------------------------------------------------------------- mensagens */

  /*
   * Toda mensagem de erro passa por aqui e vai para uma faixa fixa no topo da pagina.
   * O roteiro exige que o erro apareca para quem esta usando a tela, e nao so no console
   * do navegador - por isso nunca ha um console.error solitario neste projeto.
   */
  function mostrarMensagem(texto, tipo) {
    var faixa = elemento('mensagem');
    faixa.textContent = texto;
    faixa.className = 'mensagem ' + (tipo || 'aviso');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function limparMensagem() {
    var faixa = elemento('mensagem');
    faixa.className = 'mensagem oculto';
    faixa.textContent = '';
  }

  /* ----------------------------------------------------------------- telas */

  function mostrarTelaLogin() {
    elemento('telaLogin').classList.remove('oculto');
    elemento('telaApp').classList.add('oculto');
    elemento('sessao').classList.add('oculto');
    elemento('loginSenha').value = '';
  }

  function mostrarTelaApp(usuario, idAgencia) {
    elemento('telaLogin').classList.add('oculto');
    elemento('telaApp').classList.remove('oculto');
    elemento('sessao').classList.remove('oculto');
    elemento('usuarioLogado').textContent = usuario;
    elemento('agenciaAtual').textContent =
      'Agencia ' + idAgencia + ' - porta ' + (4000 + idAgencia);
  }

  /* ---------------------------------------------------------------- contas */

  function renderizarContas(contas) {
    var lista = elemento('listaContas');
    lista.innerHTML = '';

    if (!contas || contas.length === 0) {
      var vazio = document.createElement('p');
      vazio.className = 'vazio';
      vazio.textContent =
        'Nenhuma conta sua nesta agencia. Abra uma conta com um numero que pertenca a ela.';
      lista.appendChild(vazio);
      return;
    }

    contas.forEach(function (conta) {
      var caixa = document.createElement('div');
      caixa.className = 'conta';

      var numero = document.createElement('div');
      numero.className = 'numero';
      numero.textContent = 'Conta ' + conta.id;

      var titular = document.createElement('div');
      titular.className = 'titular';
      titular.textContent = conta.nomeAluno;

      var saldo = document.createElement('div');
      saldo.className = 'saldo';
      saldo.textContent = moeda(conta.saldo);

      caixa.appendChild(numero);
      caixa.appendChild(titular);
      caixa.appendChild(saldo);
      lista.appendChild(caixa);
    });
  }

  /* Funcionalidade adicional: mostra o teto por operacao antes da pessoa tentar. */
  function mostrarLimites(limites) {
    elemento('limiteSaque').textContent =
      'Limite por saque: ' + moeda(limites.limitePorSaque) + '. Deposito nao tem limite.';
    elemento('limiteTransferencia').textContent =
      'Limite por transferencia: ' + moeda(limites.limitePorTransferencia) + '.';
  }

  function mostrarResultadoConsulta(conta) {
    var caixa = elemento('resultadoConsulta');
    caixa.className = 'resultado';
    caixa.innerHTML = '';
    caixa.appendChild(document.createTextNode(
      'Conta ' + conta.id + ' - ' + conta.nomeAluno + ': ' + moeda(conta.saldo)));
  }

  function esconderResultadoConsulta() {
    elemento('resultadoConsulta').className = 'resultado oculto';
  }

  /* --------------------------------------------------------- transferencia */

  /*
   * O frontend nao decide se a transferencia e local ou entre agencias - isso e do
   * backend. Ele apenas EXIBE qual das duas aconteceu, lendo o campo "tipo" da resposta,
   * que e o que o roteiro pede que fique claro na tela.
   */
  function mostrarResultadoTransferencia(resposta) {
    var caixa = elemento('resultadoTransferencia');
    var entreAgencias = resposta.tipo === 'ENTRE_AGENCIAS';
    caixa.className = 'resultado ' + (entreAgencias ? 'entre-agencias' : 'local');
    caixa.innerHTML = '';

    var linhas = [];
    linhas.push(entreAgencias
      ? 'Transferencia ENTRE AGENCIAS concluida (destino: Agencia ' + resposta.agenciaDestino + ')'
      : 'Transferencia LOCAL concluida (mesma agencia)');
    linhas.push('Relogio de Lamport no momento da operacao: ' + resposta.timestampLamport);
    if (resposta.saldoOrigem !== undefined) {
      linhas.push('Saldo da origem: ' + moeda(resposta.saldoOrigem));
    }
    if (resposta.saldoDestino !== undefined) {
      linhas.push('Saldo do destino: ' + moeda(resposta.saldoDestino));
    }

    linhas.forEach(function (texto, i) {
      if (i > 0) { caixa.appendChild(document.createElement('br')); }
      caixa.appendChild(document.createTextNode(texto));
    });
  }

  function esconderResultadoTransferencia() {
    elemento('resultadoTransferencia').className = 'resultado oculto';
  }

  /* ------------------------------------------------------ leitura de campos */

  function lerNumero(id) {
    var valor = elemento(id).value.trim();
    return valor === '' ? null : Number(valor);
  }

  function lerTexto(id) {
    return elemento(id).value.trim();
  }

  function limparCampos(ids) {
    ids.forEach(function (id) { elemento(id).value = ''; });
  }

  return {
    elemento: elemento,
    moeda: moeda,
    mostrarMensagem: mostrarMensagem,
    limparMensagem: limparMensagem,
    mostrarTelaLogin: mostrarTelaLogin,
    mostrarTelaApp: mostrarTelaApp,
    renderizarContas: renderizarContas,
    mostrarLimites: mostrarLimites,
    mostrarResultadoConsulta: mostrarResultadoConsulta,
    esconderResultadoConsulta: esconderResultadoConsulta,
    mostrarResultadoTransferencia: mostrarResultadoTransferencia,
    esconderResultadoTransferencia: esconderResultadoTransferencia,
    lerNumero: lerNumero,
    lerTexto: lerTexto,
    limparCampos: limparCampos
  };
})();
