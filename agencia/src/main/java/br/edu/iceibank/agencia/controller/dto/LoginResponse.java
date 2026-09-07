package br.edu.iceibank.agencia.controller.dto;

import java.util.Set;

public record LoginResponse(String token, String login, long expiraEmSegundos, Set<Integer> contas) {}
