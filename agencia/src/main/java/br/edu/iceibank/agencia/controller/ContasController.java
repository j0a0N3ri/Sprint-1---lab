package br.edu.iceibank.agencia.controller;

import br.edu.iceibank.agencia.controller.dto.CriarContaRequest;
import br.edu.iceibank.agencia.controller.dto.OperacaoRequest;
import br.edu.iceibank.agencia.exception.ErroDeNegocio;
import br.edu.iceibank.agencia.model.Conta;
import br.edu.iceibank.agencia.service.BancoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Camada de controle (o "C" do MVC): traduz HTTP em chamada de servico e devolve o resultado.
 * Nenhuma regra de negocio mora aqui - saldo, particao e relogio de Lamport sao do BancoService.
 */
@RestController
@RequestMapping("/contas")
public class ContasController {

    private final BancoService banco;

    public ContasController(BancoService banco) {
        this.banco = banco;
    }

    @PostMapping
    public ResponseEntity<Conta> criar(@RequestBody CriarContaRequest req) {
        if (req.id() == null) {
            throw ErroDeNegocio.requisicaoInvalida("O campo id e obrigatorio.");
        }
        Conta conta = banco.criarConta(req.id(), req.nomeAluno(), req.saldoInicial());
        return ResponseEntity.status(HttpStatus.CREATED).body(conta);
    }

    @GetMapping
    public List<Conta> listar() {
        return banco.listarContas();
    }

    @GetMapping("/{id}")
    public Conta consultarSaldo(@PathVariable int id) {
        return banco.buscarConta(id);
    }

    @PostMapping("/{id}/depositar")
    public Conta depositar(@PathVariable int id, @RequestBody OperacaoRequest req) {
        return banco.depositar(id, req.valor());
    }

    @PostMapping("/{id}/sacar")
    public Conta sacar(@PathVariable int id, @RequestBody OperacaoRequest req) {
        return banco.sacar(id, req.valor());
    }
}
